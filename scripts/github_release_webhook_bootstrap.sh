#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

AZURE_ENV_FILE="${ROOT_DIR}/.data/mappo-azure.env"
RUNTIME_ENV_FILE="${ROOT_DIR}/.data/mappo-runtime.env"
GITHUB_ENV_FILE="${ROOT_DIR}/.data/mappo-github.env"
RESOURCE_GROUP=""
BACKEND_APP_NAME=""
BACKEND_URL=""
REPOSITORY="${MAPPO_DEMO_GITHUB_REPOSITORY:-}"
EVENTS="push"
WEBHOOK_SECRET=""
GITHUB_TOKEN="${GITHUB_TOKEN:-}"
DRY_RUN="false"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Configure MAPPO's GitHub release-manifest webhook secret in the hosted backend and optionally
create the corresponding GitHub repository webhook.

Options:
  --azure-env-file <path>      Azure env file (default: .data/mappo-azure.env)
  --runtime-env-file <path>    Runtime env file (default: .data/mappo-runtime.env)
  --github-env-file <path>     GitHub env file (default: .data/mappo-github.env)
  --resource-group <name>      Backend Container App resource group
  --backend-app-name <name>    Backend Container App name
  --backend-url <url>          Backend public base URL
  --repository <owner/name>    GitHub repository (or MAPPO_DEMO_GITHUB_REPOSITORY)
  --events <csv>               GitHub webhook events (default: push)
  --webhook-secret <secret>    Explicit webhook secret (default: generate if missing)
  --github-token <token>       GitHub token used to create/update the repository webhook
  --dry-run                    Print actions only; do not mutate Azure or GitHub
  -h, --help                   Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --azure-env-file)
      AZURE_ENV_FILE="${2:-}"
      shift 2
      ;;
    --runtime-env-file)
      RUNTIME_ENV_FILE="${2:-}"
      shift 2
      ;;
    --github-env-file)
      GITHUB_ENV_FILE="${2:-}"
      shift 2
      ;;
    --resource-group)
      RESOURCE_GROUP="${2:-}"
      shift 2
      ;;
    --backend-app-name)
      BACKEND_APP_NAME="${2:-}"
      shift 2
      ;;
    --backend-url)
      BACKEND_URL="${2:-}"
      shift 2
      ;;
    --repository)
      REPOSITORY="${2:-}"
      shift 2
      ;;
    --events)
      EVENTS="${2:-}"
      shift 2
      ;;
    --webhook-secret)
      WEBHOOK_SECRET="${2:-}"
      shift 2
      ;;
    --github-token)
      GITHUB_TOKEN="${2:-}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "github-release-webhook-bootstrap: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -f "${AZURE_ENV_FILE}" ]]; then
  echo "github-release-webhook-bootstrap: missing Azure env file: ${AZURE_ENV_FILE}" >&2
  exit 1
fi
if [[ ! -f "${RUNTIME_ENV_FILE}" ]]; then
  echo "github-release-webhook-bootstrap: missing runtime env file: ${RUNTIME_ENV_FILE}" >&2
  exit 1
fi
if ! command -v az >/dev/null 2>&1; then
  echo "github-release-webhook-bootstrap: Azure CLI is required." >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "github-release-webhook-bootstrap: curl is required." >&2
  exit 1
fi

set -a
source "${AZURE_ENV_FILE}"
source "${RUNTIME_ENV_FILE}"
if [[ -f "${GITHUB_ENV_FILE}" ]]; then
  source "${GITHUB_ENV_FILE}"
fi
set +a

if [[ -z "${REPOSITORY}" ]]; then
  REPOSITORY="${MAPPO_DEMO_GITHUB_REPOSITORY:-}"
fi
if [[ -z "${REPOSITORY}" ]]; then
  echo "github-release-webhook-bootstrap: --repository or MAPPO_DEMO_GITHUB_REPOSITORY is required." >&2
  exit 2
fi

if [[ -z "${RESOURCE_GROUP}" ]]; then
  RESOURCE_GROUP="${MAPPO_RUNTIME_RESOURCE_GROUP:-}"
fi
if [[ -z "${BACKEND_APP_NAME}" ]]; then
  BACKEND_APP_NAME="${MAPPO_RUNTIME_BACKEND_APP:-}"
fi
if [[ -z "${BACKEND_URL}" ]]; then
  BACKEND_URL="${MAPPO_RUNTIME_BACKEND_URL:-}"
fi
if [[ -z "${RESOURCE_GROUP}" || -z "${BACKEND_APP_NAME}" || -z "${BACKEND_URL}" ]]; then
  echo "github-release-webhook-bootstrap: runtime env is missing backend app/resource group/url." >&2
  exit 1
fi

WEBHOOK_URL="${BACKEND_URL%/}/api/v1/admin/releases/webhooks/github"
if [[ -z "${WEBHOOK_SECRET}" ]]; then
  WEBHOOK_SECRET="${MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET:-}"
fi
if [[ -z "${WEBHOOK_SECRET}" ]]; then
  if ! command -v openssl >/dev/null 2>&1; then
    echo "github-release-webhook-bootstrap: openssl is required to generate a webhook secret." >&2
    exit 1
  fi
  WEBHOOK_SECRET="$(openssl rand -hex 32)"
fi

mkdir -p "$(dirname "${GITHUB_ENV_FILE}")"
cat > "${GITHUB_ENV_FILE}" <<EOF
export MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET=${WEBHOOK_SECRET}
EOF

echo "github-release-webhook-bootstrap: repository=${REPOSITORY}"
echo "github-release-webhook-bootstrap: webhook_url=${WEBHOOK_URL}"
echo "github-release-webhook-bootstrap: github_env_file=${GITHUB_ENV_FILE}"

if [[ "${DRY_RUN}" != "true" ]]; then
  az containerapp secret set \
    --name "${BACKEND_APP_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --secrets "managed-app-release-webhook-secret=${WEBHOOK_SECRET}" \
    --only-show-errors \
    >/dev/null

  az containerapp update \
    --name "${BACKEND_APP_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --set-env-vars "MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET=secretref:managed-app-release-webhook-secret" \
    --only-show-errors \
    >/dev/null

  echo "github-release-webhook-bootstrap: configured backend Container App secret/env."
else
  echo "github-release-webhook-bootstrap: dry-run; skipped backend Container App update."
fi

event_json="$(
  printf '%s' "${EVENTS}" \
    | tr ',' '\n' \
    | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' \
    | awk 'NF { printf("%s\"%s\"", sep, $0); sep="," }'
)"

payload="$(cat <<EOF
{"name":"web","active":true,"events":[${event_json}],"config":{"url":"${WEBHOOK_URL}","content_type":"json","secret":"${WEBHOOK_SECRET}","insecure_ssl":"0"}}
EOF
)"

if [[ -n "${GITHUB_TOKEN}" ]]; then
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "github-release-webhook-bootstrap: dry-run; skipped GitHub webhook create."
  else
    http_code="$(
      curl -sS -o /tmp/mappo-github-webhook-response.json -w '%{http_code}' \
        -X POST \
        -H "Accept: application/vnd.github+json" \
        -H "Authorization: Bearer ${GITHUB_TOKEN}" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "https://api.github.com/repos/${REPOSITORY}/hooks" \
        -d "${payload}"
    )"

    if [[ "${http_code}" =~ ^20[01]$ ]]; then
      echo "github-release-webhook-bootstrap: created GitHub webhook for ${REPOSITORY}."
    elif [[ "${http_code}" == "422" ]]; then
      echo "github-release-webhook-bootstrap: GitHub returned 422 (webhook may already exist)." >&2
      cat /tmp/mappo-github-webhook-response.json >&2
    else
      echo "github-release-webhook-bootstrap: GitHub webhook create failed with HTTP ${http_code}." >&2
      cat /tmp/mappo-github-webhook-response.json >&2
      exit 1
    fi
  fi
else
  echo "github-release-webhook-bootstrap: no GitHub token supplied; rerun with --github-token or GITHUB_TOKEN set to create the repository webhook."
fi

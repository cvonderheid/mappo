#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${MAPPO_API_BASE_URL:-}"
ENDPOINT_ID="ado-pipeline-default"
NAME="Azure DevOps Pipeline Default"
PIPELINE_ID=""
BRANCH="main"
SECRET_REF=""

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Patch MAPPO's Azure DevOps release source so only the release-readiness pipeline creates releases.

Options:
  --api-base-url <url>       MAPPO API base URL (default: MAPPO_API_BASE_URL)
  --endpoint-id <id>         Release source ID (default: ado-pipeline-default)
  --name <value>             Release source display name (default: Azure DevOps Pipeline Default)
  --pipeline-id <id>         ADO release-readiness pipeline ID
  --branch <name>            Branch filter (default: main)
  --secret-ref <ref>         Optional secret reference override
  -h, --help                 Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api-base-url)
      API_BASE_URL="${2:-}"
      shift 2
      ;;
    --endpoint-id)
      ENDPOINT_ID="${2:-}"
      shift 2
      ;;
    --name)
      NAME="${2:-}"
      shift 2
      ;;
    --pipeline-id)
      PIPELINE_ID="${2:-}"
      shift 2
      ;;
    --branch)
      BRANCH="${2:-}"
      shift 2
      ;;
    --secret-ref)
      SECRET_REF="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "release-source-configure-ado: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${API_BASE_URL}" ]]; then
  echo "release-source-configure-ado: --api-base-url (or MAPPO_API_BASE_URL) is required." >&2
  exit 2
fi
if [[ -z "${PIPELINE_ID}" ]]; then
  echo "release-source-configure-ado: --pipeline-id is required." >&2
  exit 2
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "release-source-configure-ado: jq is required." >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "release-source-configure-ado: curl is required." >&2
  exit 1
fi

PAYLOAD="$(jq -n \
  --arg name "${NAME}" \
  --arg pipelineId "${PIPELINE_ID}" \
  --arg branch "${BRANCH}" \
  --arg secretRef "${SECRET_REF}" \
  '{
    name: $name,
    provider: "azure_devops",
    enabled: true,
    pipelineIdFilter: $pipelineId,
    branchFilter: $branch
  }
  + (if ($secretRef|length) > 0 then {secretRef: $secretRef} else {} end)')"

ENDPOINT="${API_BASE_URL%/}/api/v1/release-ingest/endpoints/${ENDPOINT_ID}"
echo "release-source-configure-ado: patching ${ENDPOINT}"

RESPONSE="$(curl --fail-with-body --silent --show-error \
  -X PATCH "${ENDPOINT}" \
  -H "content-type: application/json" \
  --data "${PAYLOAD}")"

echo "${RESPONSE}" | jq .
echo "release-source-configure-ado: completed."

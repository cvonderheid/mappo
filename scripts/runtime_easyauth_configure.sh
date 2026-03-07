#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

STACK="${PULUMI_STACK:-demo}"
SUBSCRIPTION_ID=""
RESOURCE_GROUP=""
FRONTEND_APP_NAME=""
FRONTEND_URL=""
AZURE_ENV_FILE="${ROOT_DIR}/.data/mappo-azure.env"
RUNTIME_ENV_FILE="${ROOT_DIR}/.data/mappo-runtime.env"
OUTPUT_ENV_FILE="${ROOT_DIR}/.data/mappo-easyauth.env"
APP_DISPLAY_NAME=""
APP_CLIENT_ID="${MAPPO_EASYAUTH_CLIENT_ID:-}"
APP_CLIENT_SECRET="${MAPPO_EASYAUTH_CLIENT_SECRET:-}"
TENANT_ID="${MAPPO_EASYAUTH_TENANT_ID:-}"
SIGN_IN_AUDIENCE="${MAPPO_EASYAUTH_SIGN_IN_AUDIENCE:-AzureADMyOrg}"
EXTRA_REDIRECT_URIS="${MAPPO_EASYAUTH_EXTRA_REDIRECT_URIS:-}"
SECRET_VALID_YEARS="${MAPPO_EASYAUTH_SECRET_YEARS:-2}"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Configure EasyAuth for MAPPO frontend Container App and create/update the Entra app registration.

Options:
  --stack <name>                   Naming suffix seed (default: \$PULUMI_STACK or demo)
  --subscription-id <id>           Subscription containing the MAPPO runtime apps
  --resource-group <name>          Runtime resource group (default from runtime env)
  --frontend-app-name <name>       Frontend Container App name (default from runtime env)
  --frontend-url <url>             Frontend URL (default from runtime env or ACA lookup)
  --azure-env-file <path>          Azure env file (default: .data/mappo-azure.env)
  --runtime-env-file <path>        Runtime env file (default: .data/mappo-runtime.env)
  --output-env-file <path>         Output env file (default: .data/mappo-easyauth.env)
  --tenant-id <guid>               Entra tenant for EasyAuth app registration
  --app-display-name <name>        Entra app display name
  --client-id <guid>               Existing Entra app client ID to reuse
  --client-secret <value>          Existing Entra app client secret to reuse
  --sign-in-audience <value>       AzureADMyOrg or AzureADMultipleOrgs (default: AzureADMyOrg)
  --extra-redirect-uris <csv>      Extra redirect URIs (comma-separated)
  --secret-valid-years <int>       Secret validity years when generated (default: 2)
  -h, --help                       Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack)
      STACK="${2:-}"
      shift 2
      ;;
    --subscription-id)
      SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --resource-group)
      RESOURCE_GROUP="${2:-}"
      shift 2
      ;;
    --frontend-app-name)
      FRONTEND_APP_NAME="${2:-}"
      shift 2
      ;;
    --frontend-url)
      FRONTEND_URL="${2:-}"
      shift 2
      ;;
    --azure-env-file)
      AZURE_ENV_FILE="${2:-}"
      shift 2
      ;;
    --runtime-env-file)
      RUNTIME_ENV_FILE="${2:-}"
      shift 2
      ;;
    --output-env-file)
      OUTPUT_ENV_FILE="${2:-}"
      shift 2
      ;;
    --tenant-id)
      TENANT_ID="${2:-}"
      shift 2
      ;;
    --app-display-name)
      APP_DISPLAY_NAME="${2:-}"
      shift 2
      ;;
    --client-id)
      APP_CLIENT_ID="${2:-}"
      shift 2
      ;;
    --client-secret)
      APP_CLIENT_SECRET="${2:-}"
      shift 2
      ;;
    --sign-in-audience)
      SIGN_IN_AUDIENCE="${2:-}"
      shift 2
      ;;
    --extra-redirect-uris)
      EXTRA_REDIRECT_URIS="${2:-}"
      shift 2
      ;;
    --secret-valid-years)
      SECRET_VALID_YEARS="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "runtime-easyauth-configure: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v az >/dev/null 2>&1; then
  echo "runtime-easyauth-configure: Azure CLI is required." >&2
  exit 1
fi
if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "runtime-easyauth-configure: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

if [[ -f "${AZURE_ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${AZURE_ENV_FILE}"
  set +a
fi

if [[ -f "${RUNTIME_ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${RUNTIME_ENV_FILE}"
  set +a
fi

if [[ -n "${SUBSCRIPTION_ID}" ]]; then
  az account set --subscription "${SUBSCRIPTION_ID}"
elif [[ -n "${MAPPO_RUNTIME_SUBSCRIPTION_ID:-}" ]]; then
  SUBSCRIPTION_ID="${MAPPO_RUNTIME_SUBSCRIPTION_ID}"
  az account set --subscription "${SUBSCRIPTION_ID}"
else
  SUBSCRIPTION_ID="$(az account show --query id -o tsv --only-show-errors)"
fi

normalize_stack() {
  printf "%s" "$1" \
    | tr "[:upper:]" "[:lower:]" \
    | tr -cd "a-z0-9-" \
    | sed -E 's/^-+//; s/-+$//; s/-+/-/g'
}

stack_token="$(normalize_stack "${STACK}")"
if [[ -z "${stack_token}" ]]; then
  stack_token="demo"
fi

if [[ -z "${RESOURCE_GROUP}" ]]; then
  RESOURCE_GROUP="${MAPPO_RUNTIME_RESOURCE_GROUP:-}"
fi
if [[ -z "${FRONTEND_APP_NAME}" ]]; then
  FRONTEND_APP_NAME="${MAPPO_RUNTIME_FRONTEND_APP:-}"
fi
if [[ -z "${FRONTEND_URL}" ]]; then
  FRONTEND_URL="${MAPPO_RUNTIME_FRONTEND_URL:-}"
fi

if [[ -z "${RESOURCE_GROUP}" || -z "${FRONTEND_APP_NAME}" ]]; then
  echo "runtime-easyauth-configure: resource group and frontend app are required." >&2
  echo "Run runtime deploy first or pass --resource-group and --frontend-app-name." >&2
  exit 1
fi

if [[ -z "${TENANT_ID}" ]]; then
  TENANT_ID="${MAPPO_AZURE_TENANT_ID:-}"
fi
if [[ -z "${TENANT_ID}" ]]; then
  TENANT_ID="$(az account show --query tenantId -o tsv --only-show-errors)"
fi

if [[ -z "${APP_DISPLAY_NAME}" ]]; then
  APP_DISPLAY_NAME="mappo-ui-easyauth-${stack_token}"
fi

if [[ -z "${FRONTEND_URL}" ]]; then
  frontend_fqdn="$(az containerapp show \
    --name "${FRONTEND_APP_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --query properties.configuration.ingress.fqdn \
    -o tsv \
    --only-show-errors)"
  if [[ -z "${frontend_fqdn}" ]]; then
    echo "runtime-easyauth-configure: unable to resolve frontend URL." >&2
    exit 1
  fi
  FRONTEND_URL="https://${frontend_fqdn}"
fi
FRONTEND_URL="${FRONTEND_URL%/}"

callback_url="${FRONTEND_URL}/.auth/login/aad/callback"
logout_url="${FRONTEND_URL}/.auth/logout"

if [[ -z "${APP_CLIENT_ID}" ]]; then
  APP_CLIENT_ID="$(az ad app list --display-name "${APP_DISPLAY_NAME}" --query '[0].appId' -o tsv --only-show-errors)"
fi

if [[ -z "${APP_CLIENT_ID}" ]]; then
  app_row="$(az ad app create \
    --display-name "${APP_DISPLAY_NAME}" \
    --sign-in-audience "${SIGN_IN_AUDIENCE}" \
    --query '[appId,id]' \
    -o tsv \
    --only-show-errors)"
  APP_CLIENT_ID="$(printf "%s" "${app_row}" | awk '{print $1}')"
  APP_OBJECT_ID="$(printf "%s" "${app_row}" | awk '{print $2}')"
else
  APP_OBJECT_ID="$(az ad app show --id "${APP_CLIENT_ID}" --query id -o tsv --only-show-errors)"
fi

if [[ -z "${APP_CLIENT_ID}" || -z "${APP_OBJECT_ID}" ]]; then
  echo "runtime-easyauth-configure: failed to resolve EasyAuth app registration." >&2
  exit 1
fi

mapfile -t redirect_uris < <(
  "${ROOT_DIR}/scripts/run_tooling.sh" \
    azure-script-support easyauth-redirect-uris \
    --existing-json "$(az ad app show --id "${APP_CLIENT_ID}" --query 'web.redirectUris' -o json --only-show-errors)" \
    --callback-url "${callback_url}" \
    --extra-redirect-uris "${EXTRA_REDIRECT_URIS}"
)

update_args=(
  --id "${APP_CLIENT_ID}"
  --sign-in-audience "${SIGN_IN_AUDIENCE}"
  --enable-id-token-issuance true
  --web-home-page-url "${FRONTEND_URL}"
)
if [[ ${#redirect_uris[@]} -gt 0 ]]; then
  update_args+=(--web-redirect-uris "${redirect_uris[@]}")
fi

az ad app update "${update_args[@]}" --only-show-errors >/dev/null

if [[ -z "${APP_CLIENT_SECRET}" ]]; then
  APP_CLIENT_SECRET="$(az ad app credential reset \
    --id "${APP_CLIENT_ID}" \
    --append \
    --display-name "mappo-easyauth-$(date -u +"%Y%m%d%H%M%S")" \
    --years "${SECRET_VALID_YEARS}" \
    --query password \
    -o tsv \
    --only-show-errors)"
fi

if [[ -z "${APP_CLIENT_SECRET}" ]]; then
  echo "runtime-easyauth-configure: failed to resolve EasyAuth client secret." >&2
  exit 1
fi

if ! az extension show --name containerapp >/dev/null 2>&1; then
  az extension add --name containerapp --upgrade --only-show-errors >/dev/null
fi

echo "runtime-easyauth-configure: subscription=${SUBSCRIPTION_ID}"
echo "runtime-easyauth-configure: tenant_id=${TENANT_ID}"
echo "runtime-easyauth-configure: resource_group=${RESOURCE_GROUP}"
echo "runtime-easyauth-configure: frontend_app=${FRONTEND_APP_NAME}"
echo "runtime-easyauth-configure: app_display_name=${APP_DISPLAY_NAME}"
echo "runtime-easyauth-configure: app_client_id=${APP_CLIENT_ID}"

az containerapp auth update \
  --name "${FRONTEND_APP_NAME}" \
  --resource-group "${RESOURCE_GROUP}" \
  --enabled true \
  --require-https true \
  --unauthenticated-client-action RedirectToLoginPage \
  --redirect-provider AzureActiveDirectory \
  --only-show-errors \
  >/dev/null

az containerapp auth microsoft update \
  --name "${FRONTEND_APP_NAME}" \
  --resource-group "${RESOURCE_GROUP}" \
  --tenant-id "${TENANT_ID}" \
  --client-id "${APP_CLIENT_ID}" \
  --client-secret "${APP_CLIENT_SECRET}" \
  --allowed-audiences "${APP_CLIENT_ID}" \
  --yes \
  --only-show-errors \
  >/dev/null

mkdir -p "$(dirname "${OUTPUT_ENV_FILE}")"
cat > "${OUTPUT_ENV_FILE}" <<EOF
# Generated by scripts/runtime_easyauth_configure.sh on $(date -u +"%Y-%m-%dT%H:%M:%SZ")
export MAPPO_EASYAUTH_ENABLED='true'
export MAPPO_EASYAUTH_SUBSCRIPTION_ID='${SUBSCRIPTION_ID}'
export MAPPO_EASYAUTH_TENANT_ID='${TENANT_ID}'
export MAPPO_EASYAUTH_APP_OBJECT_ID='${APP_OBJECT_ID}'
export MAPPO_EASYAUTH_CLIENT_ID='${APP_CLIENT_ID}'
export MAPPO_EASYAUTH_CLIENT_SECRET='${APP_CLIENT_SECRET}'
export MAPPO_EASYAUTH_APP_DISPLAY_NAME='${APP_DISPLAY_NAME}'
export MAPPO_EASYAUTH_FRONTEND_URL='${FRONTEND_URL}'
export MAPPO_EASYAUTH_CALLBACK_URL='${callback_url}'
export MAPPO_EASYAUTH_LOGOUT_URL='${logout_url}'
EOF

echo "runtime-easyauth-configure: configured EasyAuth for frontend app ${FRONTEND_APP_NAME}."
echo "runtime-easyauth-configure: wrote ${OUTPUT_ENV_FILE}"

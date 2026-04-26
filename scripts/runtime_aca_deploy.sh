#!/usr/bin/env bash
set -euo pipefail

echo "$(basename "$0"): legacy runtime script; use infra/pulumi for MAPPO runtime infrastructure." >&2

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

STACK="${PULUMI_STACK:-demo}"
RESOURCE_GROUP=""
LOCATION="centralus"
SUBSCRIPTION_ID=""
CONTAINER_ENV_NAME=""
BACKEND_APP_NAME=""
FRONTEND_APP_NAME=""
ACR_NAME=""
IMAGE_TAG="$(date -u +"%Y%m%d%H%M%S")"
ENV_FILE="${ROOT_DIR}/.data/mappo.env"
AZURE_ENV_FILE="${ROOT_DIR}/.data/mappo.env"
DB_ENV_FILE="${ROOT_DIR}/.data/mappo.env"
PUBLISHER_ACR_ENV_FILE="${ROOT_DIR}/.data/mappo.env"
GITHUB_ENV_FILE="${ROOT_DIR}/.data/mappo.env"
ADO_ENV_FILE="${ROOT_DIR}/.data/mappo.env"
OUTPUT_ENV_FILE="${ROOT_DIR}/.data/mappo.env"
REDIS_CLUSTER_NAME=""
REDIS_SKU="Balanced_B0"
REDIS_PROVISION="true"
BACKEND_CPU="0.5"
BACKEND_MEMORY="1.0Gi"
FRONTEND_CPU="0.5"
FRONTEND_MEMORY="1.0Gi"
MIN_REPLICAS="1"
MAX_REPLICAS="2"
MIGRATION_JOB_NAME=""
MIGRATION_TIMEOUT_SECONDS="900"
RUN_MIGRATIONS="true"
SKIP_BUILD="false"
SKIP_APP_DEPLOY="false"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Deploy MAPPO runtime (backend + frontend) to Azure Container Apps in a dedicated runtime resource group.

Options:
  --stack <name>               Naming suffix seed (default: \$PULUMI_STACK or demo)
  --resource-group <name>      Runtime resource group (default: rg-mappo-runtime-<stack>)
  --location <region>          Azure region (default: centralus)
  --subscription-id <id>       Azure subscription for runtime resources
  --environment-name <name>    Container Apps environment name (default: cae-mappo-runtime-<stack>)
  --backend-app-name <name>    Backend Container App name (default: ca-mappo-api-<stack>)
  --frontend-app-name <name>   Frontend Container App name (default: ca-mappo-ui-<stack>)
  --acr-name <name>            ACR name (default: reuse first ACR in runtime RG; otherwise deterministic from stack+subscription)
  --image-tag <tag>            Image tag (default: UTC timestamp)
  --env-file <path>            Consolidated env file (default: .data/mappo.env)
  --azure-env-file <path>      Additional env file to source after --env-file (default: .data/mappo.env)
  --db-env-file <path>         Additional env file to source after --env-file (default: .data/mappo.env)
  --publisher-acr-env-file <path>
                               Consolidated env file (default: .data/mappo.env)
  --github-env-file <path>     Consolidated env file (default: .data/mappo.env)
  --ado-env-file <path>        Consolidated env file (default: .data/mappo.env)
  --output-env-file <path>     Output env file with deployed URLs (default: .data/mappo.env)
  --redis-name <name>          Azure Managed Redis cluster name (default: redis-mappo-<stack>)
  --redis-sku <sku>            Azure Managed Redis SKU (default: Balanced_B0)
  --skip-redis                 Do not provision or configure Azure Managed Redis
  --min-replicas <int>         Min replicas per app (default: 1)
  --max-replicas <int>         Max replicas per app (default: 2)
  --migration-job-name <name>  Migration Container App Job name (default: job-mappo-db-<stack>)
  --migration-timeout <sec>    Migration job timeout/replica-timeout seconds (default: 900)
  --skip-build                Reuse published images; do not build/push backend, frontend, or flyway images
  --skip-app-deploy           Prepare infra/job only; do not create or update backend/frontend apps
  --skip-migrations            Deploy apps but do not execute migration job
  -h, --help                   Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack)
      STACK="${2:-}"
      shift 2
      ;;
    --resource-group)
      RESOURCE_GROUP="${2:-}"
      shift 2
      ;;
    --location)
      LOCATION="${2:-}"
      shift 2
      ;;
    --subscription-id)
      SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --environment-name)
      CONTAINER_ENV_NAME="${2:-}"
      shift 2
      ;;
    --backend-app-name)
      BACKEND_APP_NAME="${2:-}"
      shift 2
      ;;
    --frontend-app-name)
      FRONTEND_APP_NAME="${2:-}"
      shift 2
      ;;
    --acr-name)
      ACR_NAME="${2:-}"
      shift 2
      ;;
    --image-tag)
      IMAGE_TAG="${2:-}"
      shift 2
      ;;
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    --azure-env-file)
      AZURE_ENV_FILE="${2:-}"
      shift 2
      ;;
    --db-env-file)
      DB_ENV_FILE="${2:-}"
      shift 2
      ;;
    --publisher-acr-env-file)
      PUBLISHER_ACR_ENV_FILE="${2:-}"
      shift 2
      ;;
    --github-env-file)
      GITHUB_ENV_FILE="${2:-}"
      shift 2
      ;;
    --ado-env-file)
      ADO_ENV_FILE="${2:-}"
      shift 2
      ;;
    --output-env-file)
      OUTPUT_ENV_FILE="${2:-}"
      shift 2
      ;;
    --redis-name)
      REDIS_CLUSTER_NAME="${2:-}"
      shift 2
      ;;
    --redis-sku)
      REDIS_SKU="${2:-}"
      shift 2
      ;;
    --skip-redis)
      REDIS_PROVISION="false"
      shift
      ;;
    --min-replicas)
      MIN_REPLICAS="${2:-}"
      shift 2
      ;;
    --max-replicas)
      MAX_REPLICAS="${2:-}"
      shift 2
      ;;
    --migration-job-name)
      MIGRATION_JOB_NAME="${2:-}"
      shift 2
      ;;
    --migration-timeout)
      MIGRATION_TIMEOUT_SECONDS="${2:-}"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD="true"
      shift
      ;;
    --skip-app-deploy)
      SKIP_APP_DEPLOY="true"
      shift
      ;;
    --skip-migrations)
      RUN_MIGRATIONS="false"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "runtime-aca-deploy: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

is_falsey() {
  [[ "$1" =~ ^(0|false|no|off)$ ]]
}

resolve_containerapp_public_host() {
  local app_name="$1"
  local fallback_fqdn="$2"
  local custom_host=""
  local bound_host=""

  custom_host="$(
    az containerapp hostname list \
      --name "${app_name}" \
      --resource-group "${RESOURCE_GROUP}" \
      --query "[?bindingType=='SniEnabled' && contains(name, 'azurecontainerapps.io') == \`false\`].name | [0]" \
      -o tsv \
      --only-show-errors \
      2>/dev/null || true
  )"
  if [[ -n "${custom_host}" && "${custom_host}" != "null" ]]; then
    printf '%s\n' "${custom_host}"
    return 0
  fi

  bound_host="$(
    az containerapp hostname list \
      --name "${app_name}" \
      --resource-group "${RESOURCE_GROUP}" \
      --query "[?bindingType=='SniEnabled'].name | [0]" \
      -o tsv \
      --only-show-errors \
      2>/dev/null || true
  )"
  if [[ -n "${bound_host}" && "${bound_host}" != "null" ]]; then
    printf '%s\n' "${bound_host}"
    return 0
  fi

  printf '%s\n' "${fallback_fqdn}"
}

upsert_env_var() {
  local file="$1"
  local key="$2"
  local value="$3"
  mkdir -p "$(dirname "${file}")"
  "${ROOT_DIR}/scripts/run_tooling.sh" \
    azure-script-support upsert-export-line \
    --env-file "${file}" \
    --key "${key}" \
    --value "${value}" \
    >/dev/null
  chmod 600 "${file}"
}

if ! command -v az >/dev/null 2>&1; then
  echo "runtime-aca-deploy: Azure CLI is required." >&2
  exit 1
fi
if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "runtime-aca-deploy: no active Azure login context. Run 'az login' first." >&2
  exit 1
fi

set -a
if [[ ! -f "${ENV_FILE}" ]]; then
  echo "runtime-aca-deploy: missing env file: ${ENV_FILE}" >&2
  exit 1
fi
source "${ENV_FILE}"
for extra_env_file in "${AZURE_ENV_FILE}" "${DB_ENV_FILE}" "${PUBLISHER_ACR_ENV_FILE}" "${GITHUB_ENV_FILE}" "${ADO_ENV_FILE}"; do
  if [[ "${extra_env_file}" != "${ENV_FILE}" && -f "${extra_env_file}" ]]; then
    source "${extra_env_file}"
  fi
done

if [[ -z "${MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN:-}" && -n "${AZURE_DEVOPS_EXT_PAT:-}" ]]; then
  MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN="${AZURE_DEVOPS_EXT_PAT}"
fi
set +a

required_vars=(
  MAPPO_DATABASE_URL
  MAPPO_DB_HOST
  MAPPO_DB_PORT
  MAPPO_DB_NAME
  MAPPO_DB_USER
  MAPPO_DB_PASSWORD
  MAPPO_AZURE_TENANT_ID
  MAPPO_AZURE_CLIENT_ID
  MAPPO_AZURE_CLIENT_SECRET
  MAPPO_AZURE_TENANT_BY_SUBSCRIPTION
  MAPPO_MARKETPLACE_INGEST_TOKEN
)
for key in "${required_vars[@]}"; do
  if [[ -z "${!key:-}" ]]; then
    echo "runtime-aca-deploy: required env var is missing: ${key}" >&2
    exit 1
  fi
done

if [[ -n "${SUBSCRIPTION_ID}" ]]; then
  az account set --subscription "${SUBSCRIPTION_ID}"
else
  SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
fi
sub_token="$(printf "%s" "${SUBSCRIPTION_ID}" | tr -d '-' | cut -c1-8)"

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
  RESOURCE_GROUP="rg-mappo-runtime-${stack_token}"
fi
if [[ -z "${CONTAINER_ENV_NAME}" ]]; then
  CONTAINER_ENV_NAME="cae-mappo-runtime-${stack_token}"
fi
if [[ -z "${BACKEND_APP_NAME}" ]]; then
  BACKEND_APP_NAME="ca-mappo-api-${stack_token}"
fi
if [[ -z "${FRONTEND_APP_NAME}" ]]; then
  FRONTEND_APP_NAME="ca-mappo-ui-${stack_token}"
fi
BACKEND_APP_NAME="$(printf "%s" "${BACKEND_APP_NAME}" | tr "[:upper:]" "[:lower:]" | cut -c1-32 | sed -E 's/-+$//')"
FRONTEND_APP_NAME="$(printf "%s" "${FRONTEND_APP_NAME}" | tr "[:upper:]" "[:lower:]" | cut -c1-32 | sed -E 's/-+$//')"
if [[ -z "${MIGRATION_JOB_NAME}" ]]; then
  MIGRATION_JOB_NAME="$(printf "job-mappo-db-%s" "${stack_token}" | cut -c1-32 | sed -E 's/-+$//')"
fi
if [[ -z "${REDIS_CLUSTER_NAME}" ]]; then
  REDIS_CLUSTER_NAME="$(printf "redis-mappo-%s" "${stack_token}" | cut -c1-60 | sed -E 's/-+$//')"
fi
if [[ -z "${MAPPO_AZURE_KEY_VAULT_NAME:-}" && -z "${MAPPO_AZURE_KEY_VAULT_URL:-}" ]]; then
  MAPPO_AZURE_KEY_VAULT_NAME="$(printf "kvmappo%s%s" "${stack_token//-/}" "${sub_token}" | cut -c1-24)"
fi
if [[ -z "${MAPPO_AZURE_KEY_VAULT_NAME:-}" && -n "${MAPPO_AZURE_KEY_VAULT_URL:-}" ]]; then
  MAPPO_AZURE_KEY_VAULT_NAME="$(printf "%s" "${MAPPO_AZURE_KEY_VAULT_URL}" | sed -E 's#^https://([^./]+).*#\1#')"
fi
MIGRATION_JOB_NAME="$(printf "%s" "${MIGRATION_JOB_NAME}" | tr "[:upper:]" "[:lower:]")"
REDIS_CLUSTER_NAME="$(printf "%s" "${REDIS_CLUSTER_NAME}" | tr "[:upper:]" "[:lower:]")"
MAPPO_AZURE_KEY_VAULT_NAME="$(printf "%s" "${MAPPO_AZURE_KEY_VAULT_NAME:-}" | tr "[:upper:]" "[:lower:]")"
if ! [[ "${MIGRATION_TIMEOUT_SECONDS}" =~ ^[0-9]+$ ]] || (( MIGRATION_TIMEOUT_SECONDS < 60 )); then
  echo "runtime-aca-deploy: --migration-timeout must be an integer >= 60." >&2
  exit 2
fi
if ! [[ "${MIGRATION_JOB_NAME}" =~ ^[a-z][a-z0-9-]{0,30}[a-z0-9]$ ]]; then
  echo "runtime-aca-deploy: invalid migration job name '${MIGRATION_JOB_NAME}' (must match ^[a-z][a-z0-9-]{0,30}[a-z0-9]$)." >&2
  exit 2
fi
if [[ -n "${ACR_NAME}" ]]; then
  ACR_NAME="$(printf "%s" "${ACR_NAME}" | tr "[:upper:]" "[:lower:]")"
fi

prefer_access_policy_key_vault() {
  local requested_name="$1"
  local preferred_name="${requested_name}"
  local enable_rbac=""
  local suffix=""
  local candidate_name=""
  local candidate_rbac=""

  if [[ -z "${requested_name}" ]]; then
    printf '%s\n' ""
    return 0
  fi

  if az keyvault show --name "${requested_name}" --resource-group "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
    enable_rbac="$(
      az keyvault show \
        --name "${requested_name}" \
        --resource-group "${RESOURCE_GROUP}" \
        --query properties.enableRbacAuthorization \
        -o tsv \
        --only-show-errors
    )"
    if [[ "${enable_rbac}" == "true" ]]; then
      for suffix in a b c d e; do
        candidate_name="${requested_name}${suffix}"
        if az keyvault show --name "${candidate_name}" --resource-group "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
          candidate_rbac="$(
            az keyvault show \
              --name "${candidate_name}" \
              --resource-group "${RESOURCE_GROUP}" \
              --query properties.enableRbacAuthorization \
              -o tsv \
              --only-show-errors
          )"
          if [[ "${candidate_rbac}" != "true" ]]; then
            echo "runtime-aca-deploy: preferring Azure Key Vault ${candidate_name} because ${requested_name} uses RBAC authorization." >&2
            preferred_name="${candidate_name}"
            break
          fi
        fi
      done
    fi
  else
    for suffix in a b c d e; do
      candidate_name="${requested_name}${suffix}"
      if az keyvault show --name "${candidate_name}" --resource-group "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
        echo "runtime-aca-deploy: preferring existing Azure Key Vault ${candidate_name} because ${requested_name} is unavailable." >&2
        preferred_name="${candidate_name}"
        break
      fi
    done
  fi

  printf '%s\n' "${preferred_name}"
}

MAPPO_AZURE_KEY_VAULT_NAME="$(prefer_access_policy_key_vault "${MAPPO_AZURE_KEY_VAULT_NAME:-}")"

echo "runtime-aca-deploy: subscription=${SUBSCRIPTION_ID}"
echo "runtime-aca-deploy: resource_group=${RESOURCE_GROUP}"
echo "runtime-aca-deploy: location=${LOCATION}"
echo "runtime-aca-deploy: environment=${CONTAINER_ENV_NAME}"
echo "runtime-aca-deploy: backend_app=${BACKEND_APP_NAME}"
echo "runtime-aca-deploy: frontend_app=${FRONTEND_APP_NAME}"
echo "runtime-aca-deploy: migration_job=${MIGRATION_JOB_NAME}"
if [[ -n "${ACR_NAME}" ]]; then
  echo "runtime-aca-deploy: acr=${ACR_NAME}"
else
  echo "runtime-aca-deploy: acr=<auto>"
fi
echo "runtime-aca-deploy: image_tag=${IMAGE_TAG}"
echo "runtime-aca-deploy: redis_cluster=${REDIS_CLUSTER_NAME}"
echo "runtime-aca-deploy: redis_sku=${REDIS_SKU}"
echo "runtime-aca-deploy: redis_provision=${REDIS_PROVISION}"
echo "runtime-aca-deploy: key_vault=${MAPPO_AZURE_KEY_VAULT_NAME:-<disabled>}"
echo "runtime-aca-deploy: skip_build=${SKIP_BUILD}"
echo "runtime-aca-deploy: skip_app_deploy=${SKIP_APP_DEPLOY}"
echo "runtime-aca-deploy: run_migrations=${RUN_MIGRATIONS}"

az provider register --namespace Microsoft.App --wait --only-show-errors >/dev/null
az provider register --namespace Microsoft.ContainerRegistry --wait --only-show-errors >/dev/null
az provider register --namespace Microsoft.OperationalInsights --wait --only-show-errors >/dev/null
az provider register --namespace Microsoft.Cache --wait --only-show-errors >/dev/null
az provider register --namespace Microsoft.KeyVault --wait --only-show-errors >/dev/null

existing_rg_location="$(az group show --name "${RESOURCE_GROUP}" --query location -o tsv --only-show-errors 2>/dev/null || true)"
if [[ -n "${existing_rg_location}" ]]; then
  if [[ "${existing_rg_location,,}" != "${LOCATION,,}" ]]; then
    echo "runtime-aca-deploy: resource group ${RESOURCE_GROUP} already exists in ${existing_rg_location}; overriding location." >&2
    LOCATION="${existing_rg_location}"
  fi
else
  az group create \
    --name "${RESOURCE_GROUP}" \
    --location "${LOCATION}" \
    --only-show-errors \
    >/dev/null
fi

if [[ -n "${MAPPO_AZURE_KEY_VAULT_NAME:-}" ]]; then
  if ! az keyvault show --name "${MAPPO_AZURE_KEY_VAULT_NAME}" --resource-group "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
    echo "runtime-aca-deploy: creating Azure Key Vault ${MAPPO_AZURE_KEY_VAULT_NAME}"
    az keyvault create \
      --name "${MAPPO_AZURE_KEY_VAULT_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --location "${LOCATION}" \
      --enable-rbac-authorization false \
      --only-show-errors \
      >/dev/null
  else
    echo "runtime-aca-deploy: reusing Azure Key Vault ${MAPPO_AZURE_KEY_VAULT_NAME}"
  fi
  MAPPO_AZURE_KEY_VAULT_URL="$(az keyvault show --name "${MAPPO_AZURE_KEY_VAULT_NAME}" --resource-group "${RESOURCE_GROUP}" --query properties.vaultUri -o tsv --only-show-errors)"
  if [[ "$(az keyvault show --name "${MAPPO_AZURE_KEY_VAULT_NAME}" --resource-group "${RESOURCE_GROUP}" --query properties.enableRbacAuthorization -o tsv --only-show-errors)" == "true" ]]; then
    echo "runtime-aca-deploy: existing Key Vault ${MAPPO_AZURE_KEY_VAULT_NAME} uses RBAC authorization. Recreate it with access policies or grant the MAPPO principal secret-read access manually." >&2
    exit 1
  fi
  if az ad sp show --id "${MAPPO_AZURE_CLIENT_ID}" --only-show-errors >/dev/null 2>&1; then
    az keyvault set-policy \
      --name "${MAPPO_AZURE_KEY_VAULT_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --spn "${MAPPO_AZURE_CLIENT_ID}" \
      --secret-permissions get list \
      --only-show-errors \
      >/dev/null
  fi
fi

if ! az extension show --name containerapp >/dev/null 2>&1; then
  az extension add --name containerapp --upgrade --only-show-errors >/dev/null
else
  az extension update --name containerapp --only-show-errors >/dev/null || true
fi

CONTAINER_ENV_ID=""
CONTAINER_ENV_RG="${RESOURCE_GROUP}"
CONTAINER_ENV_LOCATION="${LOCATION}"

if az containerapp env show --name "${CONTAINER_ENV_NAME}" --resource-group "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
  CONTAINER_ENV_ID="$(az containerapp env show --name "${CONTAINER_ENV_NAME}" --resource-group "${RESOURCE_GROUP}" --query id -o tsv)"
else
  env_create_log="$(mktemp)"
  if ! az containerapp env create \
    --name "${CONTAINER_ENV_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --location "${LOCATION}" \
    --only-show-errors \
    >"${env_create_log}" 2>&1; then
    if grep -Eq "MaxNumberOfRegionalEnvironmentsInSubExceeded|MaxNumberOfGlobalEnvironmentsInSubExceeded" "${env_create_log}"; then
      echo "runtime-aca-deploy: ACA environment quota prevents creating ${CONTAINER_ENV_NAME} in ${RESOURCE_GROUP}." >&2
      echo "runtime-aca-deploy: not reusing an environment from another resource group because handoff deployments must keep MAPPO runtime resources together." >&2
      cat "${env_create_log}" >&2
      rm -f "${env_create_log}"
      exit 1
    else
      cat "${env_create_log}" >&2
      rm -f "${env_create_log}"
      exit 1
    fi
  else
    rm -f "${env_create_log}"
    CONTAINER_ENV_ID="$(az containerapp env show --name "${CONTAINER_ENV_NAME}" --resource-group "${RESOURCE_GROUP}" --query id -o tsv)"
  fi
fi

if [[ -z "${CONTAINER_ENV_ID}" ]]; then
  CONTAINER_ENV_ID="$(az containerapp env show --name "${CONTAINER_ENV_NAME}" --resource-group "${CONTAINER_ENV_RG}" --query id -o tsv)"
fi

if [[ -z "${ACR_NAME}" ]]; then
  existing_acr_name="$(az acr list --resource-group "${RESOURCE_GROUP}" --query '[0].name' -o tsv --only-show-errors 2>/dev/null || true)"
  if [[ -n "${existing_acr_name}" ]]; then
    ACR_NAME="${existing_acr_name}"
  else
    sub_token="$(printf "%s" "${SUBSCRIPTION_ID}" | tr -d '-' | cut -c1-8)"
    ACR_NAME="$(printf "acrmappo%s%s" "${stack_token//-/}" "${sub_token}" | cut -c1-50)"
  fi
fi
ACR_NAME="$(printf "%s" "${ACR_NAME}" | tr "[:upper:]" "[:lower:]")"
echo "runtime-aca-deploy: resolved acr=${ACR_NAME}"

if ! az acr show --name "${ACR_NAME}" --resource-group "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
  acr_create_log="$(mktemp)"
  if ! az acr create \
    --name "${ACR_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --location "${LOCATION}" \
    --sku Basic \
    --admin-enabled true \
    --only-show-errors \
    >"${acr_create_log}" 2>&1; then
    if grep -q "AlreadyExists" "${acr_create_log}"; then
      random_suffix="$("${ROOT_DIR}/scripts/run_tooling.sh" \
        azure-script-support random-suffix \
        --length 6)"
      ACR_NAME="$(printf "acrmappo%s%s" "${stack_token//-/}" "${random_suffix}" | cut -c1-50)"
      echo "runtime-aca-deploy: deterministic ACR name already taken globally, using fallback ${ACR_NAME}."
      az acr create \
        --name "${ACR_NAME}" \
        --resource-group "${RESOURCE_GROUP}" \
        --location "${LOCATION}" \
        --sku Basic \
        --admin-enabled true \
        --only-show-errors \
        >/dev/null
    else
      cat "${acr_create_log}" >&2
      rm -f "${acr_create_log}"
      exit 1
    fi
  fi
  rm -f "${acr_create_log}"
else
  az acr update \
    --name "${ACR_NAME}" \
    --admin-enabled true \
    --only-show-errors \
    >/dev/null
fi

acr_login_server="$(az acr show --name "${ACR_NAME}" --resource-group "${RESOURCE_GROUP}" --query loginServer -o tsv)"
acr_username="$(az acr credential show --name "${ACR_NAME}" --resource-group "${RESOURCE_GROUP}" --query username -o tsv)"
acr_password="$(az acr credential show --name "${ACR_NAME}" --resource-group "${RESOURCE_GROUP}" --query 'passwords[0].value' -o tsv)"

backend_image="${acr_login_server}/mappo-backend:${IMAGE_TAG}"
frontend_image="${acr_login_server}/mappo-frontend:${IMAGE_TAG}"
flyway_image="${acr_login_server}/mappo-flyway:${IMAGE_TAG}"
redis_host=""
redis_port=""
redis_password=""
redis_ssl_enabled="true"

if is_falsey "${REDIS_PROVISION}"; then
  echo "runtime-aca-deploy: skipping Azure Managed Redis provisioning by request."
else
  if ! az redisenterprise show --name "${REDIS_CLUSTER_NAME}" --resource-group "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
    echo "runtime-aca-deploy: creating Azure Managed Redis cluster ${REDIS_CLUSTER_NAME}"
    az redisenterprise create \
      --name "${REDIS_CLUSTER_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --location "${LOCATION}" \
      --sku "${REDIS_SKU}" \
      --public-network-access Enabled \
      --access-keys-auth Enabled \
      --only-show-errors \
      >/dev/null
  else
    echo "runtime-aca-deploy: reusing Azure Managed Redis cluster ${REDIS_CLUSTER_NAME}"
    az redisenterprise database update \
      --cluster-name "${REDIS_CLUSTER_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --access-keys-auth Enabled \
      --client-protocol Encrypted \
      --only-show-errors \
      >/dev/null
  fi

  redis_host="$(az redisenterprise show --name "${REDIS_CLUSTER_NAME}" --resource-group "${RESOURCE_GROUP}" --query hostName -o tsv --only-show-errors)"
  redis_port="$(az redisenterprise database show --cluster-name "${REDIS_CLUSTER_NAME}" --resource-group "${RESOURCE_GROUP}" --query port -o tsv --only-show-errors)"
  redis_password="$(az redisenterprise database list-keys --cluster-name "${REDIS_CLUSTER_NAME}" --resource-group "${RESOURCE_GROUP}" --query primaryKey -o tsv --only-show-errors)"

  if [[ -z "${redis_host}" || -z "${redis_port}" || -z "${redis_password}" ]]; then
    echo "runtime-aca-deploy: failed to resolve Azure Managed Redis connection details for ${REDIS_CLUSTER_NAME}" >&2
    exit 1
  fi
fi

ensure_backend_artifact() {
  if [[ ! -f "${ROOT_DIR}/backend/target/backend-1.0.0-SNAPSHOT.jar" ]]; then
    echo "runtime-aca-deploy: backend jar missing; packaging backend first." >&2
  else
    echo "runtime-aca-deploy: packaging backend to refresh target/backend-1.0.0-SNAPSHOT.jar before image build." >&2
  fi
  (cd "${ROOT_DIR}" && ./mvnw -pl backend -am package -DskipTests --quiet)
}

build_image() {
  local image_repo="$1"
  local dockerfile_path="$2"
  local build_context="$3"
  shift 3
  local build_args=("$@")

  local task_log
  task_log="$(mktemp)"

  if az acr build \
    --registry "${ACR_NAME}" \
    --image "${image_repo}:${IMAGE_TAG}" \
    --file "${dockerfile_path}" \
    "${build_args[@]}" \
    "${build_context}" \
    --only-show-errors \
    >"${task_log}" 2>&1; then
    rm -f "${task_log}"
    return 0
  fi

  if grep -q "TasksOperationsNotAllowed" "${task_log}"; then
    if ! command -v docker >/dev/null 2>&1; then
      echo "runtime-aca-deploy: ACR Tasks unavailable and Docker is not installed for fallback build." >&2
      cat "${task_log}" >&2
      rm -f "${task_log}"
      exit 1
    fi
    echo "runtime-aca-deploy: ACR Tasks unavailable; falling back to local docker build/push for ${image_repo}:${IMAGE_TAG}." >&2
    az acr login --name "${ACR_NAME}" --only-show-errors >/dev/null
    if docker buildx version >/dev/null 2>&1; then
      docker buildx build \
        --platform linux/amd64 \
        -f "${dockerfile_path}" \
        -t "${acr_login_server}/${image_repo}:${IMAGE_TAG}" \
        "${build_args[@]}" \
        --push \
        "${build_context}"
    else
      echo "runtime-aca-deploy: docker buildx unavailable; falling back to docker build (platform compatibility not guaranteed)." >&2
      docker build -f "${dockerfile_path}" -t "${acr_login_server}/${image_repo}:${IMAGE_TAG}" "${build_args[@]}" "${build_context}"
      docker push "${acr_login_server}/${image_repo}:${IMAGE_TAG}"
    fi
    rm -f "${task_log}"
    return 0
  fi

  cat "${task_log}" >&2
  rm -f "${task_log}"
  exit 1
}

if is_falsey "${SKIP_BUILD}"; then
  ensure_backend_artifact

  echo "runtime-aca-deploy: building backend image ${backend_image}"
  build_image "mappo-backend" "${ROOT_DIR}/backend/Dockerfile" "${ROOT_DIR}/backend"

  echo "runtime-aca-deploy: building migration image ${flyway_image}"
  build_image "mappo-flyway" "${ROOT_DIR}/backend/flyway/Dockerfile" "${ROOT_DIR}/backend"
else
  echo "runtime-aca-deploy: reusing published backend image ${backend_image}"
  echo "runtime-aca-deploy: reusing published migration image ${flyway_image}"
fi

flyway_jdbc_url="jdbc:postgresql://${MAPPO_DB_HOST}:${MAPPO_DB_PORT}/${MAPPO_DB_NAME}"
if [[ -n "${MAPPO_DB_SSLMODE:-}" ]]; then
  flyway_jdbc_url="${flyway_jdbc_url}?sslmode=${MAPPO_DB_SSLMODE}"
fi

migration_env_vars=(
  "FLYWAY_URL=${flyway_jdbc_url}"
  "FLYWAY_USER=${MAPPO_DB_USER}"
  "FLYWAY_PASSWORD=secretref:flyway-password"
  "FLYWAY_CONNECT_RETRIES=10"
)
migration_secrets=(
  "flyway-password=${MAPPO_DB_PASSWORD}"
)

migration_job_exists="$(az containerapp job show --name "${MIGRATION_JOB_NAME}" --resource-group "${RESOURCE_GROUP}" --query name -o tsv --only-show-errors 2>/dev/null || true)"
if [[ -n "${migration_job_exists}" ]]; then
  echo "runtime-aca-deploy: updating migration job ${MIGRATION_JOB_NAME}"
  az containerapp job secret set \
    --name "${MIGRATION_JOB_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --secrets "${migration_secrets[@]}" \
    --only-show-errors \
    >/dev/null
  az containerapp job registry set \
    --name "${MIGRATION_JOB_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --server "${acr_login_server}" \
    --username "${acr_username}" \
    --password "${acr_password}" \
    --only-show-errors \
    >/dev/null
  az containerapp job update \
    --name "${MIGRATION_JOB_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --container-name flyway \
    --image "${flyway_image}" \
    --set-env-vars "${migration_env_vars[@]}" \
    --cpu "0.5" \
    --memory "1.0Gi" \
    --replica-timeout "${MIGRATION_TIMEOUT_SECONDS}" \
    --replica-retry-limit 0 \
    --parallelism 1 \
    --replica-completion-count 1 \
    --only-show-errors \
    >/dev/null
else
  echo "runtime-aca-deploy: creating migration job ${MIGRATION_JOB_NAME}"
  az containerapp job create \
    --name "${MIGRATION_JOB_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --environment "${CONTAINER_ENV_ID}" \
    --trigger-type Manual \
    --container-name flyway \
    --image "${flyway_image}" \
    --registry-server "${acr_login_server}" \
    --registry-username "${acr_username}" \
    --registry-password "${acr_password}" \
    --secrets "${migration_secrets[@]}" \
    --env-vars "${migration_env_vars[@]}" \
    --cpu "0.5" \
    --memory "1.0Gi" \
    --replica-timeout "${MIGRATION_TIMEOUT_SECONDS}" \
    --replica-retry-limit 0 \
    --parallelism 1 \
    --replica-completion-count 1 \
    --only-show-errors \
    >/dev/null
fi

if [[ ! "${RUN_MIGRATIONS}" =~ ^(0|false|no|off)$ ]]; then
  echo "runtime-aca-deploy: starting migration job execution for ${MIGRATION_JOB_NAME}"
  migration_start_json="$(
    az containerapp job start \
      --name "${MIGRATION_JOB_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --only-show-errors \
      -o json
  )"
  migration_execution_name="$("${ROOT_DIR}/scripts/run_tooling.sh" \
    azure-script-support job-execution-name \
    --json "${migration_start_json}")"
  if [[ -z "${migration_execution_name}" ]]; then
    migration_execution_name="$(az containerapp job execution list --name "${MIGRATION_JOB_NAME}" --resource-group "${RESOURCE_GROUP}" --query "sort_by(@, &properties.startTime)[-1].name" -o tsv --only-show-errors 2>/dev/null || true)"
  fi
  if [[ -z "${migration_execution_name}" ]]; then
    echo "runtime-aca-deploy: unable to resolve migration execution name for ${MIGRATION_JOB_NAME}" >&2
    exit 1
  fi

  migration_deadline_epoch="$(( $(date +%s) + MIGRATION_TIMEOUT_SECONDS ))"
  while true; do
    migration_status="$(az containerapp job execution show --name "${MIGRATION_JOB_NAME}" --resource-group "${RESOURCE_GROUP}" --job-execution-name "${migration_execution_name}" --query "properties.status" -o tsv --only-show-errors 2>/dev/null || true)"
    if [[ "${migration_status}" == "Succeeded" ]]; then
      echo "runtime-aca-deploy: migration job execution succeeded (${migration_execution_name})."
      break
    fi
    if [[ "${migration_status}" =~ ^(Failed|Canceled|Cancelled)$ ]]; then
      echo "runtime-aca-deploy: migration job execution failed (${migration_execution_name}) status=${migration_status}" >&2
      az containerapp job logs show \
        --name "${MIGRATION_JOB_NAME}" \
        --resource-group "${RESOURCE_GROUP}" \
        --execution "${migration_execution_name}" \
        --container flyway \
        --tail 100 \
        --format text \
        --only-show-errors \
        || true
      exit 1
    fi
    if (( $(date +%s) >= migration_deadline_epoch )); then
      echo "runtime-aca-deploy: migration job execution timed out after ${MIGRATION_TIMEOUT_SECONDS}s (${migration_execution_name})." >&2
      exit 1
    fi
    sleep 5
  done
else
  echo "runtime-aca-deploy: skipping migration job execution by request."
fi

backend_env_vars=(
  "MAPPO_DATABASE_URL=secretref:database-url"
  "MAPPO_BACKEND_PORT=8000"
  "MAPPO_DB_USER=${MAPPO_DB_USER}"
  "MAPPO_DB_PASSWORD=secretref:database-password"
  "MAPPO_EXECUTION_MODE=azure"
  "MAPPO_AZURE_TENANT_ID=${MAPPO_AZURE_TENANT_ID}"
  "MAPPO_AZURE_CLIENT_ID=${MAPPO_AZURE_CLIENT_ID}"
  "MAPPO_AZURE_CLIENT_SECRET=secretref:azure-client-secret"
  "MAPPO_AZURE_TENANT_BY_SUBSCRIPTION=${MAPPO_AZURE_TENANT_BY_SUBSCRIPTION}"
  "MAPPO_MARKETPLACE_INGEST_TOKEN=secretref:marketplace-ingest-token"
  "MAPPO_RUN_RETENTION_DAYS=90"
  "MAPPO_AUDIT_RETENTION_DAYS=90"
)
backend_secrets=(
  "database-url=${MAPPO_DATABASE_URL}"
  "database-password=${MAPPO_DB_PASSWORD}"
  "azure-client-secret=${MAPPO_AZURE_CLIENT_SECRET}"
  "marketplace-ingest-token=${MAPPO_MARKETPLACE_INGEST_TOKEN}"
)

if [[ -n "${redis_host}" && -n "${redis_port}" && -n "${redis_password}" ]]; then
  backend_env_vars+=(
    "MAPPO_REDIS_ENABLED=true"
    "MAPPO_REDIS_HOST=${redis_host}"
    "MAPPO_REDIS_PORT=${redis_port}"
    "MAPPO_REDIS_SSL_ENABLED=${redis_ssl_enabled}"
    "MAPPO_REDIS_PASSWORD=secretref:redis-password"
  )
  backend_secrets+=("redis-password=${redis_password}")
else
  backend_env_vars+=("MAPPO_REDIS_ENABLED=false")
fi

if [[ -n "${MAPPO_PUBLISHER_ACR_SERVER:-}" ]]; then
  backend_env_vars+=("MAPPO_PUBLISHER_ACR_SERVER=${MAPPO_PUBLISHER_ACR_SERVER}")
fi
if [[ -n "${MAPPO_AZURE_KEY_VAULT_URL:-}" ]]; then
  backend_env_vars+=("MAPPO_AZURE_KEY_VAULT_URL=${MAPPO_AZURE_KEY_VAULT_URL}")
fi
if [[ -n "${MAPPO_PUBLISHER_ACR_PULL_CLIENT_ID:-}" ]]; then
  backend_env_vars+=("MAPPO_PUBLISHER_ACR_PULL_CLIENT_ID=${MAPPO_PUBLISHER_ACR_PULL_CLIENT_ID}")
fi
if [[ -n "${MAPPO_PUBLISHER_ACR_PULL_SECRET_NAME:-}" ]]; then
  backend_env_vars+=("MAPPO_PUBLISHER_ACR_PULL_SECRET_NAME=${MAPPO_PUBLISHER_ACR_PULL_SECRET_NAME}")
fi
if [[ -n "${MAPPO_PUBLISHER_ACR_PULL_CLIENT_SECRET:-}" ]]; then
  backend_env_vars+=("MAPPO_PUBLISHER_ACR_PULL_CLIENT_SECRET=secretref:publisher-acr-pull-client-secret")
  backend_secrets+=("publisher-acr-pull-client-secret=${MAPPO_PUBLISHER_ACR_PULL_CLIENT_SECRET}")
fi
if [[ -n "${MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET:-}" ]]; then
  backend_env_vars+=("MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET=secretref:managed-app-release-webhook-secret")
  backend_secrets+=("managed-app-release-webhook-secret=${MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET}")
fi
if [[ -n "${MAPPO_MANAGED_APP_RELEASE_GITHUB_TOKEN:-}" ]]; then
  backend_env_vars+=("MAPPO_MANAGED_APP_RELEASE_GITHUB_TOKEN=secretref:managed-app-release-github-token")
  backend_secrets+=("managed-app-release-github-token=${MAPPO_MANAGED_APP_RELEASE_GITHUB_TOKEN}")
fi
if [[ -n "${MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN:-}" ]]; then
  backend_env_vars+=("MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN=secretref:azure-devops-personal-access-token")
  backend_secrets+=("azure-devops-personal-access-token=${MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN}")
fi
if [[ -n "${MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET:-}" ]]; then
  backend_env_vars+=("MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET=secretref:azure-devops-webhook-secret")
  backend_secrets+=("azure-devops-webhook-secret=${MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET}")
fi

backend_base_url=""
frontend_base_url=""
if is_falsey "${SKIP_APP_DEPLOY}"; then
  backend_state="$(az containerapp show --name "${BACKEND_APP_NAME}" --resource-group "${RESOURCE_GROUP}" --query properties.provisioningState -o tsv --only-show-errors 2>/dev/null || true)"
  if [[ "${backend_state}" == "Failed" ]]; then
    echo "runtime-aca-deploy: backend app ${BACKEND_APP_NAME} is in Failed provisioning state; deleting and recreating."
    az containerapp delete \
      --name "${BACKEND_APP_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --yes \
      --only-show-errors \
      >/dev/null
    backend_state=""
  fi

  if [[ -n "${backend_state}" ]]; then
    echo "runtime-aca-deploy: updating backend app ${BACKEND_APP_NAME}"
    az containerapp secret set \
      --name "${BACKEND_APP_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --secrets "${backend_secrets[@]}" \
      --only-show-errors \
      >/dev/null
    az containerapp registry set \
      --name "${BACKEND_APP_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --server "${acr_login_server}" \
      --username "${acr_username}" \
      --password "${acr_password}" \
      --only-show-errors \
      >/dev/null
    az containerapp update \
      --name "${BACKEND_APP_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --image "${backend_image}" \
      --set-env-vars "${backend_env_vars[@]}" \
      --cpu "${BACKEND_CPU}" \
      --memory "${BACKEND_MEMORY}" \
      --min-replicas "${MIN_REPLICAS}" \
      --max-replicas "${MAX_REPLICAS}" \
      --only-show-errors \
      >/dev/null
  else
    echo "runtime-aca-deploy: creating backend app ${BACKEND_APP_NAME}"
    az containerapp create \
      --name "${BACKEND_APP_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --environment "${CONTAINER_ENV_ID}" \
      --image "${backend_image}" \
      --ingress external \
      --target-port 8000 \
      --registry-server "${acr_login_server}" \
      --registry-username "${acr_username}" \
      --registry-password "${acr_password}" \
      --secrets "${backend_secrets[@]}" \
      --env-vars "${backend_env_vars[@]}" \
      --cpu "${BACKEND_CPU}" \
      --memory "${BACKEND_MEMORY}" \
      --min-replicas "${MIN_REPLICAS}" \
      --max-replicas "${MAX_REPLICAS}" \
      --only-show-errors \
      >/dev/null
  fi

  backend_fqdn="$(az containerapp show --name "${BACKEND_APP_NAME}" --resource-group "${RESOURCE_GROUP}" --query properties.configuration.ingress.fqdn -o tsv)"
  if [[ -z "${backend_fqdn}" ]]; then
    echo "runtime-aca-deploy: failed to resolve backend FQDN for ${BACKEND_APP_NAME}" >&2
    exit 1
  fi
  backend_public_host="$(resolve_containerapp_public_host "${BACKEND_APP_NAME}" "${backend_fqdn}")"
  backend_base_url="https://${backend_public_host}"

  if is_falsey "${SKIP_BUILD}"; then
    echo "runtime-aca-deploy: building frontend image ${frontend_image}"
    build_image "mappo-frontend" "${ROOT_DIR}/frontend/Dockerfile" "${ROOT_DIR}/frontend"
  else
    echo "runtime-aca-deploy: reusing published frontend image ${frontend_image}"
  fi

  frontend_state="$(az containerapp show --name "${FRONTEND_APP_NAME}" --resource-group "${RESOURCE_GROUP}" --query properties.provisioningState -o tsv --only-show-errors 2>/dev/null || true)"
  if [[ "${frontend_state}" == "Failed" ]]; then
    echo "runtime-aca-deploy: frontend app ${FRONTEND_APP_NAME} is in Failed provisioning state; deleting and recreating."
    az containerapp delete \
      --name "${FRONTEND_APP_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --yes \
      --only-show-errors \
      >/dev/null
    frontend_state=""
  fi

  if [[ -n "${frontend_state}" ]]; then
    echo "runtime-aca-deploy: updating frontend app ${FRONTEND_APP_NAME}"
    az containerapp registry set \
      --name "${FRONTEND_APP_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --server "${acr_login_server}" \
      --username "${acr_username}" \
      --password "${acr_password}" \
      --only-show-errors \
      >/dev/null
    az containerapp update \
      --name "${FRONTEND_APP_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --image "${frontend_image}" \
      --set-env-vars "MAPPO_API_BASE_URL=${backend_base_url}" \
      --cpu "${FRONTEND_CPU}" \
      --memory "${FRONTEND_MEMORY}" \
      --min-replicas "${MIN_REPLICAS}" \
      --max-replicas "${MAX_REPLICAS}" \
      --only-show-errors \
      >/dev/null
  else
    echo "runtime-aca-deploy: creating frontend app ${FRONTEND_APP_NAME}"
    az containerapp create \
      --name "${FRONTEND_APP_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --environment "${CONTAINER_ENV_ID}" \
      --image "${frontend_image}" \
      --ingress external \
      --target-port 80 \
      --registry-server "${acr_login_server}" \
      --registry-username "${acr_username}" \
      --registry-password "${acr_password}" \
      --env-vars "MAPPO_API_BASE_URL=${backend_base_url}" \
      --cpu "${FRONTEND_CPU}" \
      --memory "${FRONTEND_MEMORY}" \
      --min-replicas "${MIN_REPLICAS}" \
      --max-replicas "${MAX_REPLICAS}" \
      --only-show-errors \
      >/dev/null
  fi

  frontend_fqdn="$(az containerapp show --name "${FRONTEND_APP_NAME}" --resource-group "${RESOURCE_GROUP}" --query properties.configuration.ingress.fqdn -o tsv)"
  if [[ -z "${frontend_fqdn}" ]]; then
    echo "runtime-aca-deploy: failed to resolve frontend FQDN for ${FRONTEND_APP_NAME}" >&2
    exit 1
  fi
  frontend_public_host="$(resolve_containerapp_public_host "${FRONTEND_APP_NAME}" "${frontend_fqdn}")"
  frontend_base_url="https://${frontend_public_host}"

  backend_cors="http://localhost:5174,http://127.0.0.1:5174,https://${frontend_fqdn}"
  if [[ "${frontend_base_url}" != "https://${frontend_fqdn}" ]]; then
    backend_cors="${backend_cors},${frontend_base_url}"
  fi
  az containerapp update \
    --name "${BACKEND_APP_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --set-env-vars "MAPPO_CORS_ORIGINS=${backend_cors}" \
    --only-show-errors \
    >/dev/null
else
  echo "runtime-aca-deploy: skipping backend/frontend app deployment by request."
  backend_fqdn="$(az containerapp show --name "${BACKEND_APP_NAME}" --resource-group "${RESOURCE_GROUP}" --query properties.configuration.ingress.fqdn -o tsv --only-show-errors 2>/dev/null || true)"
  frontend_fqdn="$(az containerapp show --name "${FRONTEND_APP_NAME}" --resource-group "${RESOURCE_GROUP}" --query properties.configuration.ingress.fqdn -o tsv --only-show-errors 2>/dev/null || true)"
  if [[ -n "${backend_fqdn}" ]]; then
    backend_public_host="$(resolve_containerapp_public_host "${BACKEND_APP_NAME}" "${backend_fqdn}")"
    backend_base_url="https://${backend_public_host}"
  fi
  if [[ -n "${frontend_fqdn}" ]]; then
    frontend_public_host="$(resolve_containerapp_public_host "${FRONTEND_APP_NAME}" "${frontend_fqdn}")"
    frontend_base_url="https://${frontend_public_host}"
  fi
fi

upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_SUBSCRIPTION_ID "${SUBSCRIPTION_ID}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_RESOURCE_GROUP "${RESOURCE_GROUP}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_LOCATION "${LOCATION}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_ENVIRONMENT "${CONTAINER_ENV_NAME}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_ENVIRONMENT_RESOURCE_GROUP "${CONTAINER_ENV_RG}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_ENVIRONMENT_LOCATION "${CONTAINER_ENV_LOCATION}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_ENVIRONMENT_ID "${CONTAINER_ENV_ID}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_ACR_NAME "${ACR_NAME}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_IMAGE_TAG "${IMAGE_TAG}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_REDIS_NAME "${REDIS_CLUSTER_NAME}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_REDIS_HOST "${redis_host}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_REDIS_PORT "${redis_port}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_REDIS_SSL_ENABLED "${redis_ssl_enabled}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_BACKEND_APP "${BACKEND_APP_NAME}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_FRONTEND_APP "${FRONTEND_APP_NAME}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_MIGRATION_JOB "${MIGRATION_JOB_NAME}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_KEY_VAULT_NAME "${MAPPO_AZURE_KEY_VAULT_NAME:-}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_KEY_VAULT_URL "${MAPPO_AZURE_KEY_VAULT_URL:-}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_BACKEND_URL "${backend_base_url}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_RUNTIME_FRONTEND_URL "${frontend_base_url}"
upsert_env_var "${OUTPUT_ENV_FILE}" MAPPO_API_BASE_URL "${backend_base_url}"

echo "runtime-aca-deploy: deployment complete."
echo "runtime-aca-deploy: backend_url=${backend_base_url}"
echo "runtime-aca-deploy: frontend_url=${frontend_base_url}"
echo "runtime-aca-deploy: wrote ${OUTPUT_ENV_FILE}"

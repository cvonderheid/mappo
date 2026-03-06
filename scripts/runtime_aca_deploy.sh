#!/usr/bin/env bash
set -euo pipefail

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
AZURE_ENV_FILE="${ROOT_DIR}/.data/mappo-azure.env"
DB_ENV_FILE="${ROOT_DIR}/.data/mappo-db.env"
OUTPUT_ENV_FILE="${ROOT_DIR}/.data/mappo-runtime.env"
BACKEND_CPU="0.5"
BACKEND_MEMORY="1.0Gi"
FRONTEND_CPU="0.5"
FRONTEND_MEMORY="1.0Gi"
MIN_REPLICAS="1"
MAX_REPLICAS="2"
MIGRATION_JOB_NAME=""
MIGRATION_TIMEOUT_SECONDS="900"
RUN_MIGRATIONS="true"

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
  --azure-env-file <path>      Azure env file (default: .data/mappo-azure.env)
  --db-env-file <path>         DB env file (default: .data/mappo-db.env)
  --output-env-file <path>     Output env file with deployed URLs (default: .data/mappo-runtime.env)
  --min-replicas <int>         Min replicas per app (default: 1)
  --max-replicas <int>         Max replicas per app (default: 2)
  --migration-job-name <name>  Migration Container App Job name (default: job-mappo-db-<stack>)
  --migration-timeout <sec>    Migration job timeout/replica-timeout seconds (default: 900)
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
    --azure-env-file)
      AZURE_ENV_FILE="${2:-}"
      shift 2
      ;;
    --db-env-file)
      DB_ENV_FILE="${2:-}"
      shift 2
      ;;
    --output-env-file)
      OUTPUT_ENV_FILE="${2:-}"
      shift 2
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

if [[ ! -f "${AZURE_ENV_FILE}" ]]; then
  echo "runtime-aca-deploy: missing Azure env file: ${AZURE_ENV_FILE}" >&2
  exit 1
fi
if [[ ! -f "${DB_ENV_FILE}" ]]; then
  echo "runtime-aca-deploy: missing DB env file: ${DB_ENV_FILE}" >&2
  exit 1
fi
if ! command -v az >/dev/null 2>&1; then
  echo "runtime-aca-deploy: Azure CLI is required." >&2
  exit 1
fi
if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "runtime-aca-deploy: no active Azure login context. Run 'az login' first." >&2
  exit 1
fi

set -a
source "${AZURE_ENV_FILE}"
source "${DB_ENV_FILE}"
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
if [[ -z "${MIGRATION_JOB_NAME}" ]]; then
  MIGRATION_JOB_NAME="$(printf "job-mappo-db-%s" "${stack_token}" | cut -c1-32 | sed -E 's/-+$//')"
fi
MIGRATION_JOB_NAME="$(printf "%s" "${MIGRATION_JOB_NAME}" | tr "[:upper:]" "[:lower:]")"
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
echo "runtime-aca-deploy: run_migrations=${RUN_MIGRATIONS}"

az provider register --namespace Microsoft.App --wait --only-show-errors >/dev/null
az provider register --namespace Microsoft.ContainerRegistry --wait --only-show-errors >/dev/null
az provider register --namespace Microsoft.OperationalInsights --wait --only-show-errors >/dev/null

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
  fallback_env_row="$(
    python3 - <<'PY' "$(az containerapp env list --subscription "${SUBSCRIPTION_ID}" --query '[0]' -o json 2>/dev/null || echo 'null')"
import json
import sys

raw = (sys.argv[1] if len(sys.argv) > 1 else "").strip()
payload = None if raw in {"", "null"} else json.loads(raw)
if payload is None:
    print("")
    raise SystemExit(0)

name = str(payload.get("name") or "")
resource_group = str(payload.get("resourceGroup") or "")
location = str(payload.get("location") or "")
resource_id = str(payload.get("id") or "")
print("\t".join([name, resource_group, location, resource_id]))
PY
  )"
  IFS=$'\t' read -r fallback_env_name fallback_env_rg fallback_env_location fallback_env_id <<< "${fallback_env_row}"
  if [[ -n "${fallback_env_id}" ]]; then
    CONTAINER_ENV_NAME="${fallback_env_name}"
    CONTAINER_ENV_RG="${fallback_env_rg}"
    CONTAINER_ENV_LOCATION="${fallback_env_location}"
    CONTAINER_ENV_ID="${fallback_env_id}"
    echo "runtime-aca-deploy: reusing existing ACA environment ${CONTAINER_ENV_NAME} (${CONTAINER_ENV_RG}, ${CONTAINER_ENV_LOCATION})."
  else
  env_create_log="$(mktemp)"
  if ! az containerapp env create \
    --name "${CONTAINER_ENV_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --location "${LOCATION}" \
    --only-show-errors \
    >"${env_create_log}" 2>&1; then
    if grep -Eq "MaxNumberOfRegionalEnvironmentsInSubExceeded|MaxNumberOfGlobalEnvironmentsInSubExceeded" "${env_create_log}"; then
      fallback_env_row="$(
        python3 - <<'PY' "$(az containerapp env list --subscription "${SUBSCRIPTION_ID}" --query '[0]' -o json 2>/dev/null || echo 'null')"
import json
import sys

raw = (sys.argv[1] if len(sys.argv) > 1 else "").strip()
payload = None if raw in {"", "null"} else json.loads(raw)
if payload is None:
    print("")
    raise SystemExit(0)

name = str(payload.get("name") or "")
resource_group = str(payload.get("resourceGroup") or "")
location = str(payload.get("location") or "")
resource_id = str(payload.get("id") or "")
print("\t".join([name, resource_group, location, resource_id]))
PY
      )"
      IFS=$'\t' read -r fallback_env_name fallback_env_rg fallback_env_location fallback_env_id <<< "${fallback_env_row}"
      if [[ -n "${fallback_env_id}" ]]; then
        CONTAINER_ENV_NAME="${fallback_env_name}"
        CONTAINER_ENV_RG="${fallback_env_rg}"
        CONTAINER_ENV_LOCATION="${fallback_env_location}"
        CONTAINER_ENV_ID="${fallback_env_id}"
        echo "runtime-aca-deploy: quota prevents creating a new ACA environment; reusing ${CONTAINER_ENV_NAME} (${CONTAINER_ENV_RG}, ${CONTAINER_ENV_LOCATION})." >&2
        rm -f "${env_create_log}"
      else
        echo "runtime-aca-deploy: ACA environment quota exhausted and no existing environment found to reuse." >&2
        cat "${env_create_log}" >&2
        rm -f "${env_create_log}"
        exit 1
      fi
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
      random_suffix="$(python3 - <<'PY'
import random
import string
print("".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(6)))
PY
)"
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

build_image() {
  local image_repo="$1"
  local dockerfile_path="$2"
  shift 2
  local build_args=("$@")

  local task_log
  task_log="$(mktemp)"

  if az acr build \
    --registry "${ACR_NAME}" \
    --image "${image_repo}:${IMAGE_TAG}" \
    --file "${dockerfile_path}" \
    "${build_args[@]}" \
    "${ROOT_DIR}" \
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
        "${ROOT_DIR}"
    else
      echo "runtime-aca-deploy: docker buildx unavailable; falling back to docker build (platform compatibility not guaranteed)." >&2
      docker build -f "${dockerfile_path}" -t "${acr_login_server}/${image_repo}:${IMAGE_TAG}" "${build_args[@]}" "${ROOT_DIR}"
      docker push "${acr_login_server}/${image_repo}:${IMAGE_TAG}"
    fi
    rm -f "${task_log}"
    return 0
  fi

  cat "${task_log}" >&2
  rm -f "${task_log}"
  exit 1
}

echo "runtime-aca-deploy: building backend image ${backend_image}"
build_image "mappo-backend" "${ROOT_DIR}/backend-java/Dockerfile"

flyway_image="${acr_login_server}/mappo-flyway:${IMAGE_TAG}"
echo "runtime-aca-deploy: building migration image ${flyway_image}"
build_image "mappo-flyway" "${ROOT_DIR}/backend/flyway/Dockerfile"

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
  migration_execution_name="$(python3 - <<'PY' "${migration_start_json}"
import json
import sys

payload = json.loads(sys.argv[1]) if len(sys.argv) > 1 and sys.argv[1].strip() else {}
name = str(payload.get("name") or "").strip()
if name:
    print(name)
    raise SystemExit(0)
raw_id = str(payload.get("id") or "").strip().rstrip("/")
if raw_id:
    print(raw_id.split("/")[-1])
    raise SystemExit(0)
print("")
PY
)"
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
  "MAPPO_EXECUTION_MODE=azure"
  "MAPPO_AZURE_TENANT_ID=${MAPPO_AZURE_TENANT_ID}"
  "MAPPO_AZURE_CLIENT_ID=${MAPPO_AZURE_CLIENT_ID}"
  "MAPPO_AZURE_CLIENT_SECRET=secretref:azure-client-secret"
  "MAPPO_AZURE_TENANT_BY_SUBSCRIPTION=${MAPPO_AZURE_TENANT_BY_SUBSCRIPTION}"
  "MAPPO_MARKETPLACE_INGEST_TOKEN=secretref:marketplace-ingest-token"
  "MAPPO_RETENTION_DAYS=90"
)
backend_secrets=(
  "database-url=${MAPPO_DATABASE_URL}"
  "azure-client-secret=${MAPPO_AZURE_CLIENT_SECRET}"
  "marketplace-ingest-token=${MAPPO_MARKETPLACE_INGEST_TOKEN}"
)

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
backend_base_url="https://${backend_fqdn}"

echo "runtime-aca-deploy: building frontend image ${frontend_image}"
build_image "mappo-frontend" "${ROOT_DIR}/frontend/Dockerfile" \
  --build-arg "VITE_API_BASE_URL=${backend_base_url}"

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
frontend_base_url="https://${frontend_fqdn}"

backend_cors="http://localhost:5174,http://127.0.0.1:5174,${frontend_base_url}"
az containerapp update \
  --name "${BACKEND_APP_NAME}" \
  --resource-group "${RESOURCE_GROUP}" \
  --set-env-vars "MAPPO_CORS_ORIGINS=${backend_cors}" \
  --only-show-errors \
  >/dev/null

mkdir -p "$(dirname "${OUTPUT_ENV_FILE}")"
cat > "${OUTPUT_ENV_FILE}" <<EOF
# Generated by scripts/runtime_aca_deploy.sh on $(date -u +"%Y-%m-%dT%H:%M:%SZ")
export MAPPO_RUNTIME_SUBSCRIPTION_ID='${SUBSCRIPTION_ID}'
export MAPPO_RUNTIME_RESOURCE_GROUP='${RESOURCE_GROUP}'
export MAPPO_RUNTIME_LOCATION='${LOCATION}'
export MAPPO_RUNTIME_ENVIRONMENT='${CONTAINER_ENV_NAME}'
export MAPPO_RUNTIME_ENVIRONMENT_RESOURCE_GROUP='${CONTAINER_ENV_RG}'
export MAPPO_RUNTIME_ENVIRONMENT_LOCATION='${CONTAINER_ENV_LOCATION}'
export MAPPO_RUNTIME_ENVIRONMENT_ID='${CONTAINER_ENV_ID}'
export MAPPO_RUNTIME_ACR_NAME='${ACR_NAME}'
export MAPPO_RUNTIME_IMAGE_TAG='${IMAGE_TAG}'
export MAPPO_RUNTIME_BACKEND_APP='${BACKEND_APP_NAME}'
export MAPPO_RUNTIME_FRONTEND_APP='${FRONTEND_APP_NAME}'
export MAPPO_RUNTIME_MIGRATION_JOB='${MIGRATION_JOB_NAME}'
export MAPPO_RUNTIME_BACKEND_URL='${backend_base_url}'
export MAPPO_RUNTIME_FRONTEND_URL='${frontend_base_url}'
export MAPPO_API_BASE_URL='${backend_base_url}'
EOF

echo "runtime-aca-deploy: deployment complete."
echo "runtime-aca-deploy: backend_url=${backend_base_url}"
echo "runtime-aca-deploy: frontend_url=${frontend_base_url}"
echo "runtime-aca-deploy: wrote ${OUTPUT_ENV_FILE}"

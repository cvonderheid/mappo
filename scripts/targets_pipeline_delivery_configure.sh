#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IAC_DIR="${ROOT_DIR}/infra/demo/targets-pipeline-delivery"
STACK="targets-pipeline-delivery"
LOCATION="eastus"
FIRST_SUBSCRIPTION_ID=""
SECOND_SUBSCRIPTION_ID=""
FIRST_TENANT_ID=""
SECOND_TENANT_ID=""
FIRST_REGION=""
SECOND_REGION=""
SOFTWARE_VERSION=""
DATA_MODEL_VERSION=""

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Configure the Pipeline Delivery Demo Targets Pulumi stack for the azure-appservice-ado-pipeline project.

Options:
  --stack <name>                Pulumi stack name (default: targets-pipeline-delivery)
  --location <region>           Default Azure location (default: eastus)
  --first-subscription-id <id>  First target subscription ID (required)
  --second-subscription-id <id> Second target subscription ID (required)
  --first-tenant-id <id>        Override first tenant ID (optional; auto-resolved)
  --second-tenant-id <id>       Override second tenant ID (optional; auto-resolved)
  --first-region <region>       First target region (default: --location)
  --second-region <region>      Second target region (default: --location)
  --software-version <value>    Default APP_VERSION app setting
  --data-model-version <value>  Default DATA_MODEL_VERSION app setting
  --iac-dir <path>              Pulumi project dir (default: infra/demo/targets-pipeline-delivery)
  -h, --help                    Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack)
      STACK="${2:-}"
      shift 2
      ;;
    --location)
      LOCATION="${2:-}"
      shift 2
      ;;
    --first-subscription-id)
      FIRST_SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --second-subscription-id)
      SECOND_SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --first-tenant-id)
      FIRST_TENANT_ID="${2:-}"
      shift 2
      ;;
    --second-tenant-id)
      SECOND_TENANT_ID="${2:-}"
      shift 2
      ;;
    --first-region)
      FIRST_REGION="${2:-}"
      shift 2
      ;;
    --second-region)
      SECOND_REGION="${2:-}"
      shift 2
      ;;
    --software-version)
      SOFTWARE_VERSION="${2:-}"
      shift 2
      ;;
    --data-model-version)
      DATA_MODEL_VERSION="${2:-}"
      shift 2
      ;;
    --iac-dir)
      IAC_DIR="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "targets-pipeline-delivery-configure: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${FIRST_SUBSCRIPTION_ID}" || -z "${SECOND_SUBSCRIPTION_ID}" ]]; then
  echo "targets-pipeline-delivery-configure: first/second subscription IDs are required." >&2
  usage >&2
  exit 2
fi

if ! command -v az >/dev/null 2>&1; then
  echo "targets-pipeline-delivery-configure: Azure CLI is required." >&2
  exit 1
fi
if ! command -v pulumi >/dev/null 2>&1; then
  echo "targets-pipeline-delivery-configure: Pulumi CLI is required." >&2
  exit 1
fi
if [[ ! -d "${IAC_DIR}" ]]; then
  echo "targets-pipeline-delivery-configure: missing IaC directory: ${IAC_DIR}" >&2
  exit 1
fi
if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "targets-pipeline-delivery-configure: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

resolve_tenant_for_subscription() {
  local subscription_id="$1"
  local tenant_id=""
  if ! tenant_id="$(az account show --subscription "${subscription_id}" --query tenantId -o tsv --only-show-errors 2>/dev/null)"; then
    echo "targets-pipeline-delivery-configure: unable to resolve tenant for subscription ${subscription_id}" >&2
    exit 1
  fi
  echo "${tenant_id}"
}

if [[ -z "${FIRST_TENANT_ID}" ]]; then
  FIRST_TENANT_ID="$(resolve_tenant_for_subscription "${FIRST_SUBSCRIPTION_ID}")"
fi
if [[ -z "${SECOND_TENANT_ID}" ]]; then
  SECOND_TENANT_ID="$(resolve_tenant_for_subscription "${SECOND_SUBSCRIPTION_ID}")"
fi
if [[ -z "${FIRST_REGION}" ]]; then
  FIRST_REGION="${LOCATION}"
fi
if [[ -z "${SECOND_REGION}" ]]; then
  SECOND_REGION="${LOCATION}"
fi

echo "targets-pipeline-delivery-configure: stack=${STACK}"
echo "targets-pipeline-delivery-configure: first subscription=${FIRST_SUBSCRIPTION_ID} tenant=${FIRST_TENANT_ID}"
echo "targets-pipeline-delivery-configure: second subscription=${SECOND_SUBSCRIPTION_ID} tenant=${SECOND_TENANT_ID}"

pushd "${IAC_DIR}" >/dev/null
pulumi login --local >/dev/null
pulumi stack select "${STACK}" >/dev/null 2>&1 || pulumi stack init "${STACK}" >/dev/null

pulumi config set --stack "${STACK}" pipelineDeliveryTargets:defaultLocation "${LOCATION}"
if [[ -n "${SOFTWARE_VERSION}" ]]; then
  pulumi config set --stack "${STACK}" pipelineDeliveryTargets:defaultSoftwareVersion "${SOFTWARE_VERSION}"
fi
if [[ -n "${DATA_MODEL_VERSION}" ]]; then
  pulumi config set --stack "${STACK}" pipelineDeliveryTargets:defaultDataModelVersion "${DATA_MODEL_VERSION}"
fi

pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[0].id" "ado-target-01"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[0].tenantId" "${FIRST_TENANT_ID}"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[0].subscriptionId" "${FIRST_SUBSCRIPTION_ID}"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[0].targetGroup" "canary"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[0].region" "${FIRST_REGION}"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[0].environment" "prod"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[0].tier" "gold"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[0].customerName" "ADO Demo Customer A"

pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[1].id" "ado-target-02"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[1].tenantId" "${SECOND_TENANT_ID}"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[1].subscriptionId" "${SECOND_SUBSCRIPTION_ID}"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[1].targetGroup" "prod"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[1].region" "${SECOND_REGION}"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[1].environment" "prod"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[1].tier" "gold"
pulumi config set --stack "${STACK}" --path "pipelineDeliveryTargets:targets[1].customerName" "ADO Demo Customer B"
popd >/dev/null

echo "targets-pipeline-delivery-configure: stack configured."
echo "next:"
echo "  ./scripts/targets_pipeline_delivery_up.sh --stack ${STACK} --api-base-url <MAPPO_API_BASE_URL>"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IAC_DIR="${ROOT_DIR}/infra/demo-fleet"
STACK="demo-fleet"
LOCATION="eastus"
PROVIDER_SUBSCRIPTION_ID=""
CUSTOMER_SUBSCRIPTION_ID=""
PROVIDER_TENANT_ID=""
CUSTOMER_TENANT_ID=""
PROVIDER_REGION=""
CUSTOMER_REGION=""
PROVIDER_EXISTING_ENVIRONMENT_ID=""
CUSTOMER_EXISTING_ENVIRONMENT_ID=""

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Configure demo-fleet Pulumi stack with two cross-subscription targets.

Options:
  --stack <name>                     Pulumi stack name (default: demo-fleet)
  --location <region>                Azure location for shared resources (default: eastus)
  --provider-subscription-id <id>    First target subscription ID (required)
  --customer-subscription-id <id>    Second target subscription ID (required)
  --provider-tenant-id <id>          Override provider tenant ID (optional; auto-resolved)
  --customer-tenant-id <id>          Override customer tenant ID (optional; auto-resolved)
  --provider-region <region>         Override provider target region (default: --location)
  --customer-region <region>         Override customer target region (default: --location)
  --provider-existing-environment-id <id>
                                     Reuse an existing ACA environment in provider subscription
  --customer-existing-environment-id <id>
                                     Reuse an existing ACA environment in customer subscription
  --iac-dir <path>                   demo-fleet Pulumi directory (default: infra/demo-fleet)
  -h, --help                         Show help
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
    --provider-subscription-id)
      PROVIDER_SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --customer-subscription-id)
      CUSTOMER_SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --provider-tenant-id)
      PROVIDER_TENANT_ID="${2:-}"
      shift 2
      ;;
    --customer-tenant-id)
      CUSTOMER_TENANT_ID="${2:-}"
      shift 2
      ;;
    --provider-region)
      PROVIDER_REGION="${2:-}"
      shift 2
      ;;
    --customer-region)
      CUSTOMER_REGION="${2:-}"
      shift 2
      ;;
    --provider-existing-environment-id)
      PROVIDER_EXISTING_ENVIRONMENT_ID="${2:-}"
      shift 2
      ;;
    --customer-existing-environment-id)
      CUSTOMER_EXISTING_ENVIRONMENT_ID="${2:-}"
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
      echo "demo-fleet-configure: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${PROVIDER_SUBSCRIPTION_ID}" || -z "${CUSTOMER_SUBSCRIPTION_ID}" ]]; then
  echo "demo-fleet-configure: provider/customer subscription IDs are required." >&2
  usage >&2
  exit 2
fi

if ! command -v az >/dev/null 2>&1; then
  echo "demo-fleet-configure: Azure CLI is required." >&2
  exit 1
fi
if ! command -v pulumi >/dev/null 2>&1; then
  echo "demo-fleet-configure: Pulumi CLI is required." >&2
  exit 1
fi
if [[ ! -d "${IAC_DIR}" ]]; then
  echo "demo-fleet-configure: missing IaC directory: ${IAC_DIR}" >&2
  exit 1
fi
if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "demo-fleet-configure: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

resolve_tenant_for_subscription() {
  local subscription_id="$1"
  local tenant_id=""
  if ! tenant_id="$(az account show --subscription "${subscription_id}" --query tenantId -o tsv --only-show-errors 2>/dev/null)"; then
    echo "demo-fleet-configure: unable to resolve tenant for subscription ${subscription_id}" >&2
    exit 1
  fi
  echo "${tenant_id}"
}

if [[ -z "${PROVIDER_TENANT_ID}" ]]; then
  PROVIDER_TENANT_ID="$(resolve_tenant_for_subscription "${PROVIDER_SUBSCRIPTION_ID}")"
fi
if [[ -z "${CUSTOMER_TENANT_ID}" ]]; then
  CUSTOMER_TENANT_ID="$(resolve_tenant_for_subscription "${CUSTOMER_SUBSCRIPTION_ID}")"
fi
if [[ -z "${PROVIDER_REGION}" ]]; then
  PROVIDER_REGION="${LOCATION}"
fi
if [[ -z "${CUSTOMER_REGION}" ]]; then
  CUSTOMER_REGION="${LOCATION}"
fi

echo "demo-fleet-configure: stack=${STACK}"
echo "demo-fleet-configure: provider subscription=${PROVIDER_SUBSCRIPTION_ID} tenant=${PROVIDER_TENANT_ID}"
echo "demo-fleet-configure: customer subscription=${CUSTOMER_SUBSCRIPTION_ID} tenant=${CUSTOMER_TENANT_ID}"

pushd "${IAC_DIR}" >/dev/null
pulumi login --local >/dev/null
pulumi stack select "${STACK}" >/dev/null 2>&1 || pulumi stack init "${STACK}" >/dev/null

pulumi config set --stack "${STACK}" demoFleet:defaultLocation "${LOCATION}"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[0].id" "demo-target-01"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[0].tenantId" "${PROVIDER_TENANT_ID}"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[0].subscriptionId" "${PROVIDER_SUBSCRIPTION_ID}"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[0].targetGroup" "canary"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[0].region" "${PROVIDER_REGION}"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[0].environment" "prod"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[0].tier" "gold"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[0].customerName" "Demo Customer A"

pulumi config set --stack "${STACK}" --path "demoFleet:targets[1].id" "demo-target-02"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[1].tenantId" "${CUSTOMER_TENANT_ID}"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[1].subscriptionId" "${CUSTOMER_SUBSCRIPTION_ID}"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[1].targetGroup" "prod"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[1].region" "${CUSTOMER_REGION}"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[1].environment" "prod"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[1].tier" "gold"
pulumi config set --stack "${STACK}" --path "demoFleet:targets[1].customerName" "Demo Customer B"
if [[ -n "${PROVIDER_EXISTING_ENVIRONMENT_ID}" ]]; then
  pulumi config set --stack "${STACK}" --path "demoFleet:existingManagedEnvironmentIdsBySubscription.${PROVIDER_SUBSCRIPTION_ID}" "${PROVIDER_EXISTING_ENVIRONMENT_ID}"
fi
if [[ -n "${CUSTOMER_EXISTING_ENVIRONMENT_ID}" ]]; then
  pulumi config set --stack "${STACK}" --path "demoFleet:existingManagedEnvironmentIdsBySubscription.${CUSTOMER_SUBSCRIPTION_ID}" "${CUSTOMER_EXISTING_ENVIRONMENT_ID}"
fi
popd >/dev/null

echo "demo-fleet-configure: stack configured."
echo "next:"
echo "  ./scripts/demo_fleet_up.sh --stack ${STACK} --api-base-url <MAPPO_API_BASE_URL>"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IAC_DIR="${ROOT_DIR}/infra/pulumi"
STACK="${PULUMI_STACK:-demo}"

PROVIDER_SUBSCRIPTION_ID=""
CUSTOMER_SUBSCRIPTION_ID=""
CONTROL_PLANE_SUBSCRIPTION_ID=""
HOME_SUBSCRIPTION_ID="${MAPPO_HOME_SUBSCRIPTION_ID:-}"
RUNTIME_CLIENT_ID="${MAPPO_AZURE_CLIENT_ID:-}"
MANAGED_APP_LOCATION="${MAPPO_MANAGED_APP_LOCATION:-eastus}"
CONTROL_PLANE_LOCATION="${MAPPO_CONTROL_PLANE_LOCATION:-centralus}"
ENABLE_MANAGED_POSTGRES="${MAPPO_ENABLE_MANAGED_POSTGRES:-true}"
ALLOW_CURRENT_IP="${MAPPO_ALLOW_CURRENT_IP:-true}"
POSTGRES_ALLOWED_IP_RANGES="${MAPPO_POSTGRES_ALLOWED_IP_RANGES:-}"

usage() {
  cat <<EOF
usage: $(basename "$0") --provider-subscription-id <id> --customer-subscription-id <id> [options]

Configures a Pulumi stack for a deterministic 2-target marketplace demo:
- target-01 in provider subscription
- target-02 in customer subscription
- tenant-local publisher principal object IDs per subscription
- optional managed Postgres control plane location/subscription settings

Options:
  --stack <name>                      Pulumi stack name (default: \$PULUMI_STACK or demo)
  --provider-subscription-id <id>     Provider subscription ID (target-01)
  --customer-subscription-id <id>     Customer subscription ID (target-02)
  --home-subscription-id <id>         Home subscription for runtime app registration (default: provider)
  --runtime-client-id <app-id>        Runtime app/client ID (default: \$MAPPO_AZURE_CLIENT_ID)
  --managed-app-location <region>     Managed app target region (default: eastus)
  --control-plane-subscription-id <id> Subscription for managed Postgres (default: provider subscription)
  --control-plane-location <region>   Managed Postgres location (default: centralus)
  --enable-managed-postgres <bool>    true|false (default: true)
  --allow-current-ip <bool>           true|false auto-allow current public IP in Postgres firewall (default: true)
  --postgres-allowed-ip-ranges <csv>  Additional Postgres IP ranges (csv entries: ip or ip-ip)
  -h, --help                          Show help

Prereqs:
- az login is active
- runtime app is onboarded in both tenants (or this script can create service principals via az ad sp create)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack)
      STACK="${2:-}"
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
    --home-subscription-id)
      HOME_SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --runtime-client-id)
      RUNTIME_CLIENT_ID="${2:-}"
      shift 2
      ;;
    --managed-app-location)
      MANAGED_APP_LOCATION="${2:-}"
      shift 2
      ;;
    --control-plane-subscription-id)
      CONTROL_PLANE_SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --control-plane-location)
      CONTROL_PLANE_LOCATION="${2:-}"
      shift 2
      ;;
    --enable-managed-postgres)
      ENABLE_MANAGED_POSTGRES="${2:-}"
      shift 2
      ;;
    --allow-current-ip)
      ALLOW_CURRENT_IP="${2:-}"
      shift 2
      ;;
    --postgres-allowed-ip-ranges)
      POSTGRES_ALLOWED_IP_RANGES="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "iac-configure-marketplace-demo: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${PROVIDER_SUBSCRIPTION_ID}" || -z "${CUSTOMER_SUBSCRIPTION_ID}" ]]; then
  echo "iac-configure-marketplace-demo: provider/customer subscription IDs are required." >&2
  usage >&2
  exit 2
fi
if [[ -z "${RUNTIME_CLIENT_ID}" ]]; then
  echo "iac-configure-marketplace-demo: runtime client ID is required (--runtime-client-id or MAPPO_AZURE_CLIENT_ID)." >&2
  exit 2
fi
if [[ -z "${CONTROL_PLANE_SUBSCRIPTION_ID}" ]]; then
  CONTROL_PLANE_SUBSCRIPTION_ID="${PROVIDER_SUBSCRIPTION_ID}"
fi
if [[ -z "${HOME_SUBSCRIPTION_ID}" ]]; then
  HOME_SUBSCRIPTION_ID="${PROVIDER_SUBSCRIPTION_ID}"
fi

if ! command -v az >/dev/null 2>&1; then
  echo "iac-configure-marketplace-demo: Azure CLI is required." >&2
  exit 2
fi
if ! command -v pulumi >/dev/null 2>&1; then
  echo "iac-configure-marketplace-demo: Pulumi CLI is required." >&2
  exit 2
fi
if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "iac-configure-marketplace-demo: no active Azure login. Run 'az login' first." >&2
  exit 2
fi
if [[ ! -d "${IAC_DIR}" ]]; then
  echo "iac-configure-marketplace-demo: IaC directory missing: ${IAC_DIR}" >&2
  exit 2
fi

ORIGINAL_SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
cleanup() {
  az account set --subscription "${ORIGINAL_SUBSCRIPTION_ID}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

resolve_sp_object_id() {
  local subscription_id="$1"
  local attempts=8
  local delay_seconds=4
  local sp_object_id=""

  az account set --subscription "${subscription_id}" >/dev/null
  sp_object_id="$(az ad sp show --id "${RUNTIME_CLIENT_ID}" --query id -o tsv 2>/dev/null || true)"
  if [[ -n "${sp_object_id}" ]]; then
    echo "${sp_object_id}"
    return 0
  fi

  local attempt=1
  while [[ "${attempt}" -le "${attempts}" ]]; do
    az ad sp create --id "${RUNTIME_CLIENT_ID}" >/dev/null 2>&1 || true
    sp_object_id="$(az ad sp show --id "${RUNTIME_CLIENT_ID}" --query id -o tsv 2>/dev/null || true)"
    if [[ -n "${sp_object_id}" ]]; then
      echo "${sp_object_id}"
      return 0
    fi
    sleep "${delay_seconds}"
    attempt=$((attempt + 1))
  done

  echo "iac-configure-marketplace-demo: failed to create/resolve service principal for clientId ${RUNTIME_CLIENT_ID} in subscription ${subscription_id}." >&2
  echo "Ensure runtime app registration is multi-tenant (AzureADMultipleOrgs) and replicated, then retry." >&2
  exit 1
}

resolve_tenant_id() {
  local subscription_id="$1"
  az account set --subscription "${subscription_id}" >/dev/null
  az account show --query tenantId -o tsv
}

ensure_runtime_app_is_multitenant() {
  az account set --subscription "${HOME_SUBSCRIPTION_ID}" >/dev/null
  if ! az ad app show --id "${RUNTIME_CLIENT_ID}" --query id -o tsv >/dev/null 2>&1; then
    echo "iac-configure-marketplace-demo: runtime app ${RUNTIME_CLIENT_ID} not found in home tenant/subscription context (${HOME_SUBSCRIPTION_ID})." >&2
    echo "Use the clientId from make azure-auth-bootstrap, or pass --home-subscription-id for the app's tenant." >&2
    exit 1
  fi
  az ad app update --id "${RUNTIME_CLIENT_ID}" --sign-in-audience AzureADMultipleOrgs >/dev/null
}

echo "iac-configure-marketplace-demo: ensuring runtime app is multi-tenant in home subscription ${HOME_SUBSCRIPTION_ID}"
ensure_runtime_app_is_multitenant

echo "iac-configure-marketplace-demo: resolving tenant/principal for ${PROVIDER_SUBSCRIPTION_ID}"
provider_tenant_id="$(resolve_tenant_id "${PROVIDER_SUBSCRIPTION_ID}")"
provider_principal_object_id="$(resolve_sp_object_id "${PROVIDER_SUBSCRIPTION_ID}")"

echo "iac-configure-marketplace-demo: resolving tenant/principal for ${CUSTOMER_SUBSCRIPTION_ID}"
customer_tenant_id="$(resolve_tenant_id "${CUSTOMER_SUBSCRIPTION_ID}")"
customer_principal_object_id="$(resolve_sp_object_id "${CUSTOMER_SUBSCRIPTION_ID}")"

targets_json="$(python3 - <<'PY' \
  "${PROVIDER_SUBSCRIPTION_ID}" "${CUSTOMER_SUBSCRIPTION_ID}" \
  "${provider_tenant_id}" "${customer_tenant_id}" "${MANAGED_APP_LOCATION}"
import json
import sys

provider_sub, customer_sub, provider_tenant, customer_tenant, region = sys.argv[1:6]

rows = [
    {
        "id": "target-01",
        "tenantId": provider_tenant,
        "subscriptionId": provider_sub,
        "targetGroup": "canary",
        "region": region,
        "tier": "gold",
        "environment": "demo",
        "tags": {"tier": "gold", "environment": "demo", "ring": "canary"},
    },
    {
        "id": "target-02",
        "tenantId": customer_tenant,
        "subscriptionId": customer_sub,
        "targetGroup": "prod",
        "region": region,
        "tier": "gold",
        "environment": "demo",
        "tags": {"tier": "gold", "environment": "demo", "ring": "prod"},
    },
]
print(json.dumps(rows, separators=(",", ":")))
PY
)"

principal_map_json="$(python3 - <<'PY' \
  "${PROVIDER_SUBSCRIPTION_ID}" "${CUSTOMER_SUBSCRIPTION_ID}" \
  "${provider_principal_object_id}" "${customer_principal_object_id}"
import json
import sys

provider_sub, customer_sub, provider_id, customer_id = sys.argv[1:5]
print(json.dumps({provider_sub: provider_id, customer_sub: customer_id}, separators=(",", ":")))
PY
)"

echo "iac-configure-marketplace-demo: configuring Pulumi stack ${STACK}"
cd "${IAC_DIR}"
pulumi login --local >/dev/null
if ! pulumi stack select "${STACK}" >/dev/null 2>&1; then
  pulumi stack init "${STACK}" >/dev/null
fi

pulumi config set --stack "${STACK}" mappo:targetProfile empty >/dev/null
pulumi config set --stack "${STACK}" mappo:targets "${targets_json}" >/dev/null
pulumi config set --stack "${STACK}" mappo:publisherPrincipalObjectIds "${principal_map_json}" >/dev/null
pulumi config rm --stack "${STACK}" mappo:publisherPrincipalObjectId >/dev/null 2>&1 || true
pulumi config set --stack "${STACK}" mappo:defaultLocation "${MANAGED_APP_LOCATION}" >/dev/null
pulumi config set --stack "${STACK}" mappo:controlPlaneSubscriptionId "${CONTROL_PLANE_SUBSCRIPTION_ID}" >/dev/null
pulumi config set --stack "${STACK}" mappo:controlPlaneLocation "${CONTROL_PLANE_LOCATION}" >/dev/null
pulumi config set --stack "${STACK}" mappo:controlPlanePostgresEnabled "${ENABLE_MANAGED_POSTGRES}" >/dev/null

if [[ "${ENABLE_MANAGED_POSTGRES,,}" == "true" ]]; then
  allowed_ranges_csv="${POSTGRES_ALLOWED_IP_RANGES}"
  if [[ "${ALLOW_CURRENT_IP,,}" == "true" ]]; then
    current_ip="$(curl -fsSL https://api.ipify.org 2>/dev/null || true)"
    if [[ -n "${current_ip}" ]]; then
      if [[ -n "${allowed_ranges_csv}" ]]; then
        allowed_ranges_csv="${allowed_ranges_csv},${current_ip}"
      else
        allowed_ranges_csv="${current_ip}"
      fi
    else
      echo "iac-configure-marketplace-demo: warning: unable to resolve current public IP; continuing without adding it." >&2
    fi
  fi

  if [[ -n "${allowed_ranges_csv}" ]]; then
    allowed_ranges_json="$(python3 - <<'PY' "${allowed_ranges_csv}"
import json
import sys

rows = [item.strip() for item in sys.argv[1].replace(";", ",").split(",") if item.strip()]
print(json.dumps(rows, separators=(",", ":")))
PY
)"
    pulumi config set --stack "${STACK}" mappo:controlPlanePostgresAllowedIpRanges "${allowed_ranges_json}" >/dev/null
  else
    pulumi config rm --stack "${STACK}" mappo:controlPlanePostgresAllowedIpRanges >/dev/null 2>&1 || true
  fi

  if ! pulumi config get --stack "${STACK}" mappo:controlPlanePostgresAdminPassword >/dev/null 2>&1; then
    echo "iac-configure-marketplace-demo: managed Postgres is enabled but missing mappo:controlPlanePostgresAdminPassword." >&2
    echo "Set it with: cd infra/pulumi && pulumi config set --stack ${STACK} --secret mappo:controlPlanePostgresAdminPassword '<strong-password>'" >&2
    exit 1
  fi
fi

echo "iac-configure-marketplace-demo: stack ${STACK} configured"
echo "  provider subscription: ${PROVIDER_SUBSCRIPTION_ID} (tenant ${provider_tenant_id})"
echo "  customer subscription: ${CUSTOMER_SUBSCRIPTION_ID} (tenant ${customer_tenant_id})"
echo "  managed app location: ${MANAGED_APP_LOCATION}"
echo "  runtime app home subscription: ${HOME_SUBSCRIPTION_ID}"
echo "  control plane subscription: ${CONTROL_PLANE_SUBSCRIPTION_ID}"
echo "  control plane postgres location: ${CONTROL_PLANE_LOCATION}"
echo "  managed postgres enabled: ${ENABLE_MANAGED_POSTGRES}"
echo "  allow current IP: ${ALLOW_CURRENT_IP}"
if [[ "${ENABLE_MANAGED_POSTGRES,,}" == "true" ]]; then
  if [[ -n "${POSTGRES_ALLOWED_IP_RANGES}" ]]; then
    echo "  additional allowed IP ranges: ${POSTGRES_ALLOWED_IP_RANGES}"
  fi
fi
echo "next:"
echo "  make iac-up PULUMI_STACK=${STACK}"

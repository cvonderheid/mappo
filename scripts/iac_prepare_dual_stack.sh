#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

stack="dual-demo"
provider_subscription_id=""
customer_subscription_id=""
provider_principal_object_id="${MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID:-}"
customer_principal_object_id=""
location="eastus"
target_count="10"
output_file=""

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Generate Pulumi stack YAML for a 10-target managed-app demo split across provider/customer subscriptions.

Options:
  --stack <name>                           Stack name (default: dual-demo)
  --provider-subscription-id <id>          Provider subscription (default: active az account)
  --customer-subscription-id <id>          Customer subscription (required)
  --provider-principal-object-id <id>      Provider-tenant principal object ID used for provider subscription auth
  --customer-principal-object-id <id>      Customer-tenant principal object ID used for customer subscription auth
  --publisher-principal-object-id <id>     Deprecated alias for --provider-principal-object-id
  --location <region>                      Azure region for target deployments (default: eastus)
  --target-count <n>                       Number of targets to emit from demo profile (default: 10)
  --output-file <path>                     Output file path (default: infra/pulumi/Pulumi.<stack>.yaml)
  -h, --help                               Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack)
      stack="${2:-}"
      shift 2
      ;;
    --provider-subscription-id)
      provider_subscription_id="${2:-}"
      shift 2
      ;;
    --customer-subscription-id)
      customer_subscription_id="${2:-}"
      shift 2
      ;;
    --provider-principal-object-id)
      provider_principal_object_id="${2:-}"
      shift 2
      ;;
    --customer-principal-object-id)
      customer_principal_object_id="${2:-}"
      shift 2
      ;;
    --publisher-principal-object-id)
      provider_principal_object_id="${2:-}"
      shift 2
      ;;
    --location)
      location="${2:-}"
      shift 2
      ;;
    --target-count)
      target_count="${2:-}"
      shift 2
      ;;
    --output-file)
      output_file="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "iac-prepare-dual-stack: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v az >/dev/null 2>&1; then
  echo "iac-prepare-dual-stack: Azure CLI is required." >&2
  exit 1
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "iac-prepare-dual-stack: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

if [[ -z "${provider_subscription_id}" ]]; then
  provider_subscription_id="$(az account show --query id -o tsv)"
fi

if [[ -z "${customer_subscription_id}" ]]; then
  echo "iac-prepare-dual-stack: --customer-subscription-id is required." >&2
  exit 1
fi

if [[ -z "${provider_principal_object_id}" ]]; then
  echo "iac-prepare-dual-stack: --provider-principal-object-id is required (or set MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID)." >&2
  exit 1
fi

original_subscription_id="$(az account show --query id -o tsv)"

resolve_customer_principal_object_id() {
  local resolved=""

  if [[ -n "${MAPPO_AZURE_CLIENT_ID:-}" ]]; then
    az account set --subscription "${customer_subscription_id}" >/dev/null
    resolved="$(az ad sp show --id "${MAPPO_AZURE_CLIENT_ID}" --query id -o tsv 2>/dev/null || true)"
    if [[ -n "${resolved}" ]]; then
      echo "${resolved}"
      return
    fi
  fi

  az account set --subscription "${customer_subscription_id}" >/dev/null
  resolved="$(az ad signed-in-user show --query id -o tsv 2>/dev/null || true)"
  if [[ -n "${resolved}" ]]; then
    echo "${resolved}"
    return
  fi

  echo ""
}

if [[ -z "${customer_principal_object_id}" ]]; then
  customer_principal_object_id="$(resolve_customer_principal_object_id)"
fi

az account set --subscription "${original_subscription_id}" >/dev/null

if [[ -z "${customer_principal_object_id}" ]]; then
  echo "iac-prepare-dual-stack: unable to resolve customer principal object ID. Pass --customer-principal-object-id explicitly." >&2
  exit 1
fi

if [[ -z "${output_file}" ]]; then
  output_file="${ROOT_DIR}/infra/pulumi/Pulumi.${stack}.yaml"
fi

mkdir -p "$(dirname "${output_file}")"

python3 - <<'PY' \
  "${output_file}" \
  "${provider_subscription_id}" \
  "${customer_subscription_id}" \
  "${provider_principal_object_id}" \
  "${customer_principal_object_id}" \
  "${stack}" \
  "${location}" \
  "${target_count}"
from __future__ import annotations

from pathlib import Path
import re
import sys

output_path = Path(sys.argv[1])
provider_sub = sys.argv[2].strip()
customer_sub = sys.argv[3].strip()
publisher_principal_id = sys.argv[4].strip()
customer_principal_id = sys.argv[5].strip()
stack_name = sys.argv[6].strip()
location = sys.argv[7].strip() or "eastus"
target_count = int(sys.argv[8].strip())

if not publisher_principal_id:
    raise SystemExit("provider principal object ID is required")
if not customer_principal_id:
    raise SystemExit("customer principal object ID is required")

stack_slug = re.sub(r"[^a-z0-9-]+", "-", stack_name.lower()).strip("-")
if not stack_slug:
    stack_slug = "dual-demo"

definitions = [
    ("target-01", "tenant-001", "canary", "gold"),
    ("target-02", "tenant-002", "canary", "gold"),
    ("target-03", "tenant-003", "prod", "gold"),
    ("target-04", "tenant-004", "prod", "gold"),
    ("target-05", "tenant-005", "prod", "silver"),
    ("target-06", "tenant-006", "prod", "silver"),
    ("target-07", "tenant-007", "prod", "silver"),
    ("target-08", "tenant-008", "prod", "silver"),
    ("target-09", "tenant-009", "prod", "bronze"),
    ("target-10", "tenant-010", "prod", "bronze"),
]
if target_count <= 0:
    raise SystemExit("target_count must be > 0")
if target_count > len(definitions):
    raise SystemExit(f"target_count must be <= {len(definitions)}")

targets: list[dict[str, str]] = []
for idx, (target_id, tenant_id, group, tier) in enumerate(definitions[:target_count]):
    subscription_id = provider_sub if idx % 2 == 0 else customer_sub
    target_slug = re.sub(r"[^a-z0-9-]+", "-", target_id.lower()).strip("-")
    targets.append(
        {
            "id": target_id,
            "tenantId": tenant_id,
            "subscriptionId": subscription_id,
            "targetGroup": group,
            "region": location,
            "tier": tier,
            "environment": "demo",
            "managedApplicationName": f"mappo-ma-{target_slug}",
            "managedResourceGroupName": f"rg-mappo-ma-mrg-{target_slug}",
            "containerAppName": f"ca-mappo-ma-{target_slug}",
        }
    )

lines = [
    "config:",
    "  mappo:targetProfile: empty",
    "  mappo:publisherPrincipalObjectIds:",
    f"    {provider_sub}: {publisher_principal_id}",
    f"    {customer_sub}: {customer_principal_id}",
    f"  mappo:definitionNamePrefix: mappo-ma-def-{stack_slug}",
    f"  mappo:definitionResourceGroupPrefix: rg-mappo-ma-def-{stack_slug}",
    f"  mappo:applicationResourceGroupPrefix: rg-mappo-ma-apps-{stack_slug}",
    f"  mappo:managedEnvironmentNamePrefix: cae-mappo-ma-{stack_slug}",
    "  mappo:targets:",
]

for target in targets:
    lines.extend(
        [
            f"    - id: {target['id']}",
            f"      tenantId: {target['tenantId']}",
            f"      subscriptionId: {target['subscriptionId']}",
            f"      targetGroup: {target['targetGroup']}",
            f"      region: {target['region']}",
            f"      tier: {target['tier']}",
            f"      environment: {target['environment']}",
            f"      managedApplicationName: {target['managedApplicationName']}",
            f"      managedResourceGroupName: {target['managedResourceGroupName']}",
            f"      containerAppName: {target['containerAppName']}",
            "      tags:",
            f"        ring: {target['targetGroup']}",
            f"        tier: {target['tier']}",
            "        environment: demo",
        ]
    )

output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"wrote {output_path}")
PY

echo "iac-prepare-dual-stack: provider_subscription_id=${provider_subscription_id}"
echo "iac-prepare-dual-stack: customer_subscription_id=${customer_subscription_id}"
echo "iac-prepare-dual-stack: provider_principal_object_id=${provider_principal_object_id}"
echo "iac-prepare-dual-stack: customer_principal_object_id=${customer_principal_object_id}"
echo "iac-prepare-dual-stack: stack=${stack}"
echo "iac-prepare-dual-stack: location=${location}"
echo "iac-prepare-dual-stack: target_count=${target_count}"

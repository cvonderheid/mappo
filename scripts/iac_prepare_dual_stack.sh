#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

stack="dual-demo"
provider_subscription_id=""
customer_subscription_id=""
publisher_principal_object_id="${MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID:-}"
location="eastus"
output_file=""

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Generate Pulumi stack YAML for a 10-target managed-app demo split across provider/customer subscriptions.

Options:
  --stack <name>                           Stack name (default: dual-demo)
  --provider-subscription-id <id>          Provider subscription (default: active az account)
  --customer-subscription-id <id>          Customer subscription (required)
  --publisher-principal-object-id <id>     Publisher principal object ID (required if env not set)
  --location <region>                      Azure region for target deployments (default: eastus)
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
    --publisher-principal-object-id)
      publisher_principal_object_id="${2:-}"
      shift 2
      ;;
    --location)
      location="${2:-}"
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

if [[ -z "${publisher_principal_object_id}" ]]; then
  echo "iac-prepare-dual-stack: --publisher-principal-object-id is required (or set MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID)." >&2
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
  "${publisher_principal_object_id}" \
  "${stack}" \
  "${location}"
from __future__ import annotations

from pathlib import Path
import re
import sys

output_path = Path(sys.argv[1])
provider_sub = sys.argv[2].strip()
customer_sub = sys.argv[3].strip()
publisher_principal_id = sys.argv[4].strip()
stack_name = sys.argv[5].strip()
location = sys.argv[6].strip() or "eastus"

if not publisher_principal_id:
    raise SystemExit("publisher principal object ID is required")

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

targets: list[dict[str, str]] = []
for idx, (target_id, tenant_id, group, tier) in enumerate(definitions):
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
    f"  mappo:publisherPrincipalObjectId: {publisher_principal_id}",
    f"  mappo:definitionNamePrefix: mappo-ma-def-{stack_slug}",
    f"  mappo:definitionResourceGroupPrefix: rg-mappo-ma-def-{stack_slug}",
    f"  mappo:applicationResourceGroupPrefix: rg-mappo-ma-apps-{stack_slug}",
    f"  mappo:sharedEnvironmentNamePrefix: cae-mappo-ma-shared-{stack_slug}",
    f"  mappo:sharedEnvironmentResourceGroupPrefix: rg-mappo-ma-shared-env-{stack_slug}",
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
echo "iac-prepare-dual-stack: publisher_principal_object_id=${publisher_principal_object_id}"
echo "iac-prepare-dual-stack: stack=${stack}"
echo "iac-prepare-dual-stack: location=${location}"

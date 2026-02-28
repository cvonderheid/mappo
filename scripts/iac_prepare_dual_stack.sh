#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

stack="dual-demo"
provider_subscription_id=""
customer_subscription_id=""
output_file=""

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Generate Pulumi stack YAML for a 10-target demo split across provider/customer subscriptions.

Options:
  --stack <name>                     Stack name (default: dual-demo)
  --provider-subscription-id <id>    Provider subscription (default: active az account)
  --customer-subscription-id <id>    Customer subscription (required)
  --output-file <path>               Output file path (default: infra/pulumi/Pulumi.<stack>.yaml)
  -h, --help                         Show help
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
    --output-file)
      output_file="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
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

if [[ -z "${output_file}" ]]; then
  output_file="${ROOT_DIR}/infra/pulumi/Pulumi.${stack}.yaml"
fi

mkdir -p "$(dirname "${output_file}")"

python3 - <<'PY' "${output_file}" "${provider_subscription_id}" "${customer_subscription_id}" "${stack}"
from __future__ import annotations

from pathlib import Path
import re
import sys

output_path = Path(sys.argv[1])
provider_sub = sys.argv[2]
customer_sub = sys.argv[3]
stack_name = sys.argv[4]
stack_slug = re.sub(r"[^a-z0-9-]+", "-", stack_name.lower()).strip("-")
if not stack_slug:
    stack_slug = "dual-demo"

definitions = [
    ("target-01", "tenant-001", "canary", "eastus", "gold"),
    ("target-02", "tenant-002", "canary", "eastus", "gold"),
    ("target-03", "tenant-003", "prod", "eastus", "gold"),
    ("target-04", "tenant-004", "prod", "eastus", "gold"),
    ("target-05", "tenant-005", "prod", "eastus", "silver"),
    ("target-06", "tenant-006", "prod", "eastus", "silver"),
    ("target-07", "tenant-007", "prod", "eastus", "silver"),
    ("target-08", "tenant-008", "prod", "eastus", "silver"),
    ("target-09", "tenant-009", "prod", "eastus", "bronze"),
    ("target-10", "tenant-010", "prod", "eastus", "bronze"),
]

targets: list[dict[str, str]] = []
for idx, (target_id, tenant_id, group, region, tier) in enumerate(definitions):
    subscription_id = provider_sub if idx % 2 == 0 else customer_sub
    targets.append(
        {
            "id": target_id,
            "tenantId": tenant_id,
            "subscriptionId": subscription_id,
            "targetGroup": group,
            "region": region,
            "resourceGroupName": f"rg-mappo-{stack_slug}-{target_id}",
            "containerAppName": f"ca-mappo-{stack_slug}-{target_id}",
            "tier": tier,
        }
    )

lines = [
    "config:",
    "  mappo:targetProfile: empty",
    "  mappo:environmentMode: shared_per_subscription",
    f"  mappo:sharedEnvironmentNamePrefix: cae-mappo-{stack_slug}-shared",
    f"  mappo:sharedEnvironmentResourceGroupPrefix: rg-mappo-{stack_slug}-shared-env",
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
            f"      resourceGroupName: {target['resourceGroupName']}",
            f"      containerAppName: {target['containerAppName']}",
            "      tags:",
            f"        tier: {target['tier']}",
            "        environment: demo",
        ]
    )

output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"wrote {output_path}")
PY

echo "iac-prepare-dual-stack: provider_subscription_id=${provider_subscription_id}"
echo "iac-prepare-dual-stack: customer_subscription_id=${customer_subscription_id}"
echo "iac-prepare-dual-stack: stack=${stack}"

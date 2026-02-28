#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_OUTPUT_FILE="${ROOT_DIR}/.data/mappo-target-inventory.sim-seed.json"

output_file="${DEFAULT_OUTPUT_FILE}"
provider_subscription_id=""
customer_subscription_id=""
target_count=10
region="eastus"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Generate a deterministic MAPPO simulation target inventory split across provider/customer subscriptions.

Options:
  --output-file <path>               Output JSON file (default: ${DEFAULT_OUTPUT_FILE})
  --provider-subscription-id <id>    Provider subscription (default: active az account)
  --customer-subscription-id <id>    Customer subscription (default: first other visible subscription)
  --target-count <n>                 Number of targets to generate (default: 10)
  --region <azure-region>            Region tag value (default: eastus)
  -h, --help                         Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-file)
      output_file="${2:-}"
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
    --target-count)
      target_count="${2:-}"
      shift 2
      ;;
    --region)
      region="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "managed-app-sim-seed-targets: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v az >/dev/null 2>&1; then
  echo "managed-app-sim-seed-targets: Azure CLI is required." >&2
  exit 1
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "managed-app-sim-seed-targets: no active Azure login context. Run 'az login' first." >&2
  exit 1
fi

if [[ -z "${provider_subscription_id}" ]]; then
  provider_subscription_id="$(az account show --query id -o tsv)"
fi

if [[ -z "${customer_subscription_id}" ]]; then
  customer_subscription_id="$(
    az account list --all --query "[?id!='${provider_subscription_id}'].id | [0]" -o tsv
  )"
fi

if [[ -z "${customer_subscription_id}" ]]; then
  echo "managed-app-sim-seed-targets: customer subscription could not be inferred; pass --customer-subscription-id." >&2
  exit 1
fi

mkdir -p "$(dirname "${output_file}")"

python3 - <<'PY' "${output_file}" "${provider_subscription_id}" "${customer_subscription_id}" "${target_count}" "${region}"
from __future__ import annotations

import json
import sys
from pathlib import Path

output_file = Path(sys.argv[1]).expanduser().resolve()
provider_sub = sys.argv[2].strip()
customer_sub = sys.argv[3].strip()
target_count = int(sys.argv[4])
region = sys.argv[5].strip() or "eastus"

if target_count <= 0:
    raise SystemExit("managed-app-sim-seed-targets: --target-count must be > 0")

tier_by_index = {
    0: "gold",
    1: "gold",
    2: "gold",
    3: "gold",
    4: "silver",
    5: "silver",
    6: "silver",
    7: "silver",
    8: "bronze",
    9: "bronze",
}

rows: list[dict[str, object]] = []
for index in range(target_count):
    target_number = index + 1
    target_id = f"target-{target_number:02d}"
    tenant_id = f"tenant-{target_number:03d}"
    subscription_id = provider_sub if index % 2 == 0 else customer_sub
    ring = "canary" if index < 2 else "prod"
    tier = tier_by_index.get(index, "standard")
    rows.append(
        {
            "id": target_id,
            "tenant_id": tenant_id,
            "subscription_id": subscription_id,
            "managed_app_id": "placeholder",
            "tags": {
                "ring": ring,
                "region": region,
                "environment": "demo",
                "tier": tier,
            },
        }
    )

output_file.write_text(json.dumps(rows, indent=2) + "\n", encoding="utf-8")
print(
    f"managed-app-sim-seed-targets: wrote {output_file} with {len(rows)} target(s) "
    f"(provider={provider_sub}, customer={customer_sub})"
)
PY

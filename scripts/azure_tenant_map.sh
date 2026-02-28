#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.data/mappo-azure.env"
SUBSCRIPTION_IDS=""

usage() {
  cat <<'EOF'
usage: azure_tenant_map.sh --subscriptions "<sub1,sub2,...>" [--env-file /path/to/env]

Builds MAPPO_AZURE_TENANT_BY_SUBSCRIPTION JSON from the current Azure CLI account context
and writes/updates the export line in the target env file.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --subscriptions)
      SUBSCRIPTION_IDS="${2:-}"
      shift 2
      ;;
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "azure-tenant-map: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${SUBSCRIPTION_IDS}" ]]; then
  echo "azure-tenant-map: --subscriptions is required." >&2
  usage >&2
  exit 2
fi

if ! command -v az >/dev/null 2>&1; then
  echo "azure-tenant-map: Azure CLI is required." >&2
  exit 2
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "azure-tenant-map: no active Azure login. Run 'az login' first." >&2
  exit 2
fi

account_list_json="$(az account list --all -o json)"
tenant_map_json="$(python3 - <<'PY' "${SUBSCRIPTION_IDS}" "${account_list_json}"
import json
import sys

raw_subscriptions = sys.argv[1]
account_rows = json.loads(sys.argv[2])

if not isinstance(account_rows, list):
    raise SystemExit("azure-tenant-map: unexpected az account list payload")

requested_subscriptions = [
    value.strip() for value in raw_subscriptions.split(",") if value.strip()
]
if not requested_subscriptions:
    raise SystemExit("azure-tenant-map: no valid subscription IDs provided")

tenant_by_subscription: dict[str, str] = {}
for row in account_rows:
    if not isinstance(row, dict):
        continue
    subscription_id = str(row.get("id", "")).strip()
    tenant_id = str(row.get("tenantId", "")).strip()
    if subscription_id and tenant_id:
        tenant_by_subscription[subscription_id] = tenant_id

missing = [sub for sub in requested_subscriptions if sub not in tenant_by_subscription]
if missing:
    raise SystemExit(
        "azure-tenant-map: subscription(s) not present in az context: "
        + ", ".join(missing)
    )

ordered = {
    subscription_id: tenant_by_subscription[subscription_id]
    for subscription_id in requested_subscriptions
}
print(json.dumps(ordered, separators=(",", ":")))
PY
)"

mkdir -p "$(dirname "${ENV_FILE}")"
python3 - <<'PY' "${ENV_FILE}" "${tenant_map_json}"
from pathlib import Path
import sys

env_path = Path(sys.argv[1]).expanduser()
tenant_map_json = sys.argv[2]
key = "MAPPO_AZURE_TENANT_BY_SUBSCRIPTION"
line = f"export {key}='{tenant_map_json}'"

if env_path.exists():
    raw_lines = env_path.read_text(encoding="utf-8").splitlines()
else:
    raw_lines = []

updated: list[str] = []
replaced = False
for raw_line in raw_lines:
    stripped = raw_line.strip()
    if stripped.startswith(f"{key}=") or stripped.startswith(f"export {key}="):
        if not replaced:
            updated.append(line)
            replaced = True
        continue
    updated.append(raw_line)

if not replaced:
    if updated and updated[-1].strip() != "":
        updated.append("")
    updated.append(line)

env_path.write_text("\n".join(updated) + "\n", encoding="utf-8")
PY

echo "azure-tenant-map: wrote ${ENV_FILE}"
echo "export MAPPO_AZURE_TENANT_BY_SUBSCRIPTION='${tenant_map_json}'"

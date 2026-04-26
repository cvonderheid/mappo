#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IAC_DIR="${ROOT_DIR}/infra/demo/targets-pipeline-delivery"
STACK="targets-pipeline-delivery"
INVENTORY_FILE="${ROOT_DIR}/.data/targets-pipeline-delivery-inventory.json"
API_BASE_URL="${MAPPO_API_BASE_URL:-}"
SKIP_DELETE=false

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Delete imported App Service targets from MAPPO and destroy the Pulumi stack.

Options:
  --stack <name>               Pulumi stack (default: targets-pipeline-delivery)
  --iac-dir <path>             Pulumi project dir (default: infra/demo/targets-pipeline-delivery)
  --inventory-file <path>      Inventory json file (default: .data/targets-pipeline-delivery-inventory.json)
  --api-base-url <url>         MAPPO API base URL for target deletion (default: MAPPO_API_BASE_URL)
  --skip-delete                Destroy stack only; do not delete MAPPO targets
  --skip-events                Deprecated alias for --skip-delete
  -h, --help                   Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack)
      STACK="${2:-}"
      shift 2
      ;;
    --iac-dir)
      IAC_DIR="${2:-}"
      shift 2
      ;;
    --inventory-file)
      INVENTORY_FILE="${2:-}"
      shift 2
      ;;
    --api-base-url)
      API_BASE_URL="${2:-}"
      shift 2
      ;;
    --skip-delete)
      SKIP_DELETE=true
      shift 1
      ;;
    --skip-events)
      SKIP_DELETE=true
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "targets-pipeline-delivery-down: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v pulumi >/dev/null 2>&1; then
  echo "targets-pipeline-delivery-down: Pulumi CLI is required." >&2
  exit 1
fi
if [[ ! -d "${IAC_DIR}" ]]; then
  echo "targets-pipeline-delivery-down: missing IaC directory: ${IAC_DIR}" >&2
  exit 1
fi

pushd "${IAC_DIR}" >/dev/null
pulumi login --local >/dev/null
pulumi stack select "${STACK}" >/dev/null 2>&1 || pulumi stack init "${STACK}" >/dev/null

if [[ "${SKIP_DELETE}" != "true" ]]; then
  mkdir -p "$(dirname "${INVENTORY_FILE}")"
  if pulumi stack output --stack "${STACK}" mappoTargetInventory --json >"${INVENTORY_FILE}" 2>/dev/null; then
    echo "targets-pipeline-delivery-down: refreshed inventory ${INVENTORY_FILE}"
  fi
fi
popd >/dev/null

if [[ "${SKIP_DELETE}" != "true" && -n "${API_BASE_URL}" && -f "${INVENTORY_FILE}" ]]; then
  "${ROOT_DIR}/scripts/targets_pipeline_delivery_delete_targets.sh" \
    --inventory-file "${INVENTORY_FILE}" \
    --api-base-url "${API_BASE_URL}"
  echo "targets-pipeline-delivery-down: MAPPO target delete complete."
elif [[ "${SKIP_DELETE}" != "true" ]]; then
  echo "targets-pipeline-delivery-down: skipped MAPPO target delete (missing api-base-url or inventory file)."
fi

pushd "${IAC_DIR}" >/dev/null
pulumi destroy --stack "${STACK}" --yes
popd >/dev/null

echo "targets-pipeline-delivery-down: stack destroyed."

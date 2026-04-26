#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INVENTORY_FILE="${ROOT_DIR}/.data/targets-pipeline-delivery-inventory.json"
API_BASE_URL="${MAPPO_API_BASE_URL:-}"
DRY_RUN=false

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Delete MAPPO targets listed in the Pipeline Delivery Demo Targets Pulumi inventory.

Options:
  --inventory-file <path>      Pulumi target inventory JSON (default: .data/targets-pipeline-delivery-inventory.json)
  --api-base-url <url>         MAPPO API base URL (default: MAPPO_API_BASE_URL)
  --dry-run                    Print deletes without calling MAPPO
  -h, --help                   Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --inventory-file)
      INVENTORY_FILE="${2:-}"
      shift 2
      ;;
    --api-base-url)
      API_BASE_URL="${2:-}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "targets-pipeline-delivery-delete-targets: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${API_BASE_URL}" ]]; then
  echo "targets-pipeline-delivery-delete-targets: --api-base-url (or MAPPO_API_BASE_URL) is required." >&2
  exit 2
fi

ARGS=(
  target-delete-inventory
  --inventory-file "${INVENTORY_FILE}"
  --api-base-url "${API_BASE_URL}"
)
if [[ "${DRY_RUN}" == "true" ]]; then
  ARGS+=(--dry-run)
fi

"${ROOT_DIR}/scripts/run_tooling.sh" "${ARGS[@]}"

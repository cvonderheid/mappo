#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INVENTORY_FILE="${ROOT_DIR}/.data/appservice-fleet-target-inventory.json"
API_BASE_URL="${MAPPO_API_BASE_URL:-}"
PROJECT_ID="azure-appservice-ado-pipeline"
INGEST_TOKEN="${MAPPO_MARKETPLACE_INGEST_TOKEN:-}"
DRY_RUN=false

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Import App Service fleet targets into MAPPO from Pulumi inventory.

Options:
  --inventory-file <path>      Pulumi target inventory JSON (default: .data/appservice-fleet-target-inventory.json)
  --api-base-url <url>         MAPPO API base URL (default: MAPPO_API_BASE_URL)
  --project-id <id>            MAPPO project ID (default: azure-appservice-ado-pipeline)
  --ingest-token <token>       Optional x-mappo-ingest-token if the admin ingest API requires it
  --dry-run                    Print payloads without calling MAPPO
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
    --project-id)
      PROJECT_ID="${2:-}"
      shift 2
      ;;
    --ingest-token)
      INGEST_TOKEN="${2:-}"
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
      echo "appservice-fleet-import-targets: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${API_BASE_URL}" ]]; then
  echo "appservice-fleet-import-targets: --api-base-url (or MAPPO_API_BASE_URL) is required." >&2
  exit 2
fi

ARGS=(
  target-import-inventory
  --inventory-file "${INVENTORY_FILE}"
  --api-base-url "${API_BASE_URL}"
  --project-id "${PROJECT_ID}"
  --event-type "subscription_purchased"
  --event-id-prefix "evt-appservice-target-import"
  --source-label "appservice-fleet-iac-import"
)
if [[ -n "${INGEST_TOKEN}" ]]; then
  ARGS+=(--ingest-token "${INGEST_TOKEN}")
fi
if [[ "${DRY_RUN}" == "true" ]]; then
  ARGS+=(--dry-run)
fi

"${ROOT_DIR}/scripts/run_tooling.sh" "${ARGS[@]}"

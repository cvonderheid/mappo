#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IAC_DIR="${ROOT_DIR}/infra/demo/targets-azure-delivery"
STACK="targets-azure-delivery"
INVENTORY_FILE="${ROOT_DIR}/.data/targets-azure-delivery-inventory.json"
API_BASE_URL="${MAPPO_API_BASE_URL:-}"
INGEST_TOKEN="${MAPPO_MARKETPLACE_INGEST_TOKEN:-}"
SKIP_EVENTS=false
EVENT_TYPE="subscription_deleted"

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Emit simulated offboarding events and destroy targets-azure-delivery stack.

Options:
  --stack <name>               Pulumi stack (default: targets-azure-delivery)
  --iac-dir <path>             Pulumi project dir (default: infra/demo/targets-azure-delivery)
  --inventory-file <path>      Inventory json file (default: .data/targets-azure-delivery-inventory.json)
  --api-base-url <url>         MAPPO API base URL for ingest (default: MAPPO_API_BASE_URL)
  --ingest-token <token>       Optional x-mappo-ingest-token
  --event-type <name>          Event type for ingest (default: subscription_deleted)
  --skip-events                Destroy stack only (do not call MAPPO ingest)
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
    --ingest-token)
      INGEST_TOKEN="${2:-}"
      shift 2
      ;;
    --event-type)
      EVENT_TYPE="${2:-}"
      shift 2
      ;;
    --skip-events)
      SKIP_EVENTS=true
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "targets-azure-delivery-down: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v pulumi >/dev/null 2>&1; then
  echo "targets-azure-delivery-down: Pulumi CLI is required." >&2
  exit 1
fi
if [[ ! -d "${IAC_DIR}" ]]; then
  echo "targets-azure-delivery-down: missing IaC directory: ${IAC_DIR}" >&2
  exit 1
fi

pushd "${IAC_DIR}" >/dev/null
pulumi login --local >/dev/null
pulumi stack select "${STACK}" >/dev/null 2>&1 || pulumi stack init "${STACK}" >/dev/null

if [[ "${SKIP_EVENTS}" != "true" ]]; then
  mkdir -p "$(dirname "${INVENTORY_FILE}")"
  if pulumi stack output --stack "${STACK}" mappoTargetInventory --json >"${INVENTORY_FILE}" 2>/dev/null; then
    echo "targets-azure-delivery-down: refreshed inventory ${INVENTORY_FILE}"
  fi
fi

popd >/dev/null

if [[ "${SKIP_EVENTS}" != "true" && -n "${API_BASE_URL}" && -f "${INVENTORY_FILE}" ]]; then
  INGEST_ARGS=()
  if [[ -n "${INGEST_TOKEN}" ]]; then
    INGEST_ARGS+=(--ingest-token "${INGEST_TOKEN}")
  fi

  "${ROOT_DIR}/scripts/marketplace_ingest_events.sh" \
    --inventory-file "${INVENTORY_FILE}" \
    --api-base-url "${API_BASE_URL}" \
    --event-type "${EVENT_TYPE}" \
    --event-id-prefix "evt-targets-azure-delivery-down" \
    --source-label "targets-azure-delivery-down" \
    "${INGEST_ARGS[@]}"
  echo "targets-azure-delivery-down: marketplace offboarding ingest complete."
elif [[ "${SKIP_EVENTS}" != "true" ]]; then
  echo "targets-azure-delivery-down: skipped offboarding ingest (missing api-base-url or inventory file)."
fi

pushd "${IAC_DIR}" >/dev/null
pulumi destroy --stack "${STACK}" --yes
popd >/dev/null

echo "targets-azure-delivery-down: stack destroyed."

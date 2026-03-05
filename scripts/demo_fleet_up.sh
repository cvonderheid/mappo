#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IAC_DIR="${ROOT_DIR}/infra/demo-fleet"
STACK="demo-fleet"
INVENTORY_FILE="${ROOT_DIR}/.data/demo-fleet-target-inventory.json"
API_BASE_URL="${MAPPO_API_BASE_URL:-}"
INGEST_TOKEN="${MAPPO_MARKETPLACE_INGEST_TOKEN:-}"
SKIP_EVENTS=false
EVENT_TYPE="subscription_purchased"

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Deploy demo-fleet stack and emit simulated onboarding events.

Options:
  --stack <name>               Pulumi stack (default: demo-fleet)
  --iac-dir <path>             Pulumi project dir (default: infra/demo-fleet)
  --inventory-file <path>      Output inventory json (default: .data/demo-fleet-target-inventory.json)
  --api-base-url <url>         MAPPO API base URL for ingest (default: MAPPO_API_BASE_URL)
  --ingest-token <token>       Optional x-mappo-ingest-token
  --event-type <name>          Event type for ingest (default: subscription_purchased)
  --skip-events                Deploy stack only (do not call MAPPO ingest)
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
      echo "demo-fleet-up: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v pulumi >/dev/null 2>&1; then
  echo "demo-fleet-up: Pulumi CLI is required." >&2
  exit 1
fi
if [[ ! -d "${IAC_DIR}" ]]; then
  echo "demo-fleet-up: missing IaC directory: ${IAC_DIR}" >&2
  exit 1
fi

mkdir -p "$(dirname "${INVENTORY_FILE}")"

pushd "${IAC_DIR}" >/dev/null
pulumi login --local >/dev/null
pulumi stack select "${STACK}" >/dev/null 2>&1 || pulumi stack init "${STACK}" >/dev/null
pulumi up --stack "${STACK}" --yes
pulumi stack output --stack "${STACK}" mappoTargetInventory --json >"${INVENTORY_FILE}"
popd >/dev/null

echo "demo-fleet-up: wrote inventory ${INVENTORY_FILE}"

if [[ "${SKIP_EVENTS}" == "true" ]]; then
  echo "demo-fleet-up: skip-events enabled; no marketplace simulation events sent."
  exit 0
fi

if [[ -z "${API_BASE_URL}" ]]; then
  echo "demo-fleet-up: missing --api-base-url (or MAPPO_API_BASE_URL env)." >&2
  exit 2
fi

INGEST_ARGS=()
if [[ -n "${INGEST_TOKEN}" ]]; then
  INGEST_ARGS+=(--ingest-token "${INGEST_TOKEN}")
fi

"${ROOT_DIR}/scripts/marketplace_ingest_events.sh" \
  --inventory-file "${INVENTORY_FILE}" \
  --api-base-url "${API_BASE_URL}" \
  --event-type "${EVENT_TYPE}" \
  --event-id-prefix "evt-demo-fleet-up" \
  --source-label "demo-fleet-up" \
  "${INGEST_ARGS[@]}"

echo "demo-fleet-up: marketplace simulation ingest complete."

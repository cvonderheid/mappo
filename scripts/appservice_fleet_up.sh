#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IAC_DIR="${ROOT_DIR}/infra/appservice-fleet"
STACK="appservice-demo"
INVENTORY_FILE="${ROOT_DIR}/.data/appservice-fleet-target-inventory.json"
PACKAGE_FILE="${ROOT_DIR}/.data/appservice-fleet-package/appservice-demo-app.zip"
API_BASE_URL="${MAPPO_API_BASE_URL:-}"
INGEST_TOKEN="${MAPPO_MARKETPLACE_INGEST_TOKEN:-}"
SKIP_DEPLOY=false
SKIP_IMPORT=false

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Deploy the App Service fleet stack, publish the demo app package to every target, and import the targets into MAPPO.

Options:
  --stack <name>               Pulumi stack (default: appservice-demo)
  --iac-dir <path>             Pulumi project dir (default: infra/appservice-fleet)
  --inventory-file <path>      Output inventory json (default: .data/appservice-fleet-target-inventory.json)
  --package-file <path>        Zip artifact path (default: .data/appservice-fleet-package/appservice-demo-app.zip)
  --api-base-url <url>         MAPPO API base URL for target import (default: MAPPO_API_BASE_URL)
  --ingest-token <token>       Optional x-mappo-ingest-token if the admin ingest API requires it
  --skip-deploy                Provision stack only; do not deploy the app package
  --skip-import                Provision stack only; do not import targets into MAPPO
  --skip-events                Deprecated alias for --skip-import
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
    --package-file)
      PACKAGE_FILE="${2:-}"
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
    --skip-deploy)
      SKIP_DEPLOY=true
      shift 1
      ;;
    --skip-import)
      SKIP_IMPORT=true
      shift 1
      ;;
    --skip-events)
      SKIP_IMPORT=true
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "appservice-fleet-up: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v pulumi >/dev/null 2>&1; then
  echo "appservice-fleet-up: Pulumi CLI is required." >&2
  exit 1
fi
if ! command -v az >/dev/null 2>&1; then
  echo "appservice-fleet-up: Azure CLI is required." >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "appservice-fleet-up: jq is required." >&2
  exit 1
fi
if [[ ! -d "${IAC_DIR}" ]]; then
  echo "appservice-fleet-up: missing IaC directory: ${IAC_DIR}" >&2
  exit 1
fi

mkdir -p "$(dirname "${INVENTORY_FILE}")"

pushd "${IAC_DIR}" >/dev/null
pulumi login --local >/dev/null
pulumi stack select "${STACK}" >/dev/null 2>&1 || pulumi stack init "${STACK}" >/dev/null
pulumi up --stack "${STACK}" --yes
pulumi stack output --stack "${STACK}" mappoTargetInventory --json >"${INVENTORY_FILE}"
popd >/dev/null

echo "appservice-fleet-up: wrote inventory ${INVENTORY_FILE}"

if [[ "${SKIP_DEPLOY}" != "true" ]]; then
  "${ROOT_DIR}/scripts/appservice_fleet_package.sh" --output-file "${PACKAGE_FILE}"
  echo "appservice-fleet-up: deploying package ${PACKAGE_FILE} to App Service targets"
  while IFS= read -r row; do
    subscription_id="$(jq -r '.subscription_id' <<<"${row}")"
    target_id="$(jq -r '.id' <<<"${row}")"
    resource_group="$(jq -r '.metadata.execution_config.resourceGroup // empty' <<<"${row}")"
    app_service_name="$(jq -r '.metadata.execution_config.appServiceName // empty' <<<"${row}")"
    if [[ -z "${resource_group}" || -z "${app_service_name}" ]]; then
      echo "appservice-fleet-up: skipping ${target_id} because execution config is incomplete." >&2
      continue
    fi
    az webapp deploy \
      --subscription "${subscription_id}" \
      --resource-group "${resource_group}" \
      --name "${app_service_name}" \
      --src-path "${PACKAGE_FILE}" \
      --type zip \
      --clean true \
      --restart true \
      --track-status false \
      --only-show-errors >/dev/null
    echo "appservice-fleet-up: deployed ${target_id} -> ${app_service_name}"
  done < <(jq -c '.[]' "${INVENTORY_FILE}")
fi

if [[ "${SKIP_IMPORT}" == "true" ]]; then
  echo "appservice-fleet-up: skip-import enabled; no MAPPO targets imported."
  exit 0
fi

if [[ -z "${API_BASE_URL}" ]]; then
  echo "appservice-fleet-up: missing --api-base-url (or MAPPO_API_BASE_URL env)." >&2
  exit 2
fi

INGEST_ARGS=()
if [[ -n "${INGEST_TOKEN}" ]]; then
  INGEST_ARGS+=(--ingest-token "${INGEST_TOKEN}")
fi

"${ROOT_DIR}/scripts/appservice_fleet_import_targets.sh" \
  --inventory-file "${INVENTORY_FILE}" \
  --api-base-url "${API_BASE_URL}" \
  "${INGEST_ARGS[@]}"

echo "appservice-fleet-up: MAPPO target import complete."

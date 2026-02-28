#!/usr/bin/env bash
set -euo pipefail

provider_subscription_id=""
customer_subscription_id=""

usage() {
  cat <<EOF
Usage: $(basename "$0") --provider-subscription-id <id> --customer-subscription-id <id>

Delete legacy pre-Pulumi managed-app demo resource groups so the new Pulumi marketplace stack
can own lifecycle cleanly.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --provider-subscription-id)
      provider_subscription_id="${2:-}"
      shift 2
      ;;
    --customer-subscription-id)
      customer_subscription_id="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "cleanup-legacy-managed-app-demo: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${provider_subscription_id}" || -z "${customer_subscription_id}" ]]; then
  echo "cleanup-legacy-managed-app-demo: both subscription IDs are required." >&2
  usage >&2
  exit 1
fi

if ! command -v az >/dev/null 2>&1; then
  echo "cleanup-legacy-managed-app-demo: Azure CLI is required." >&2
  exit 1
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "cleanup-legacy-managed-app-demo: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

key_from_sub() {
  local sub="$1"
  echo "${sub//-/}" | cut -c1-8 | tr '[:upper:]' '[:lower:]'
}

provider_key="$(key_from_sub "${provider_subscription_id}")"
customer_key="$(key_from_sub "${customer_subscription_id}")"

provider_groups=(
  "rg-mappo-ma-apps-${provider_key}"
  "rg-mappo-ma-def-${provider_key}"
  "rg-mappo-ma-mrg-target-01"
  "rg-mappo-shared-env-${provider_key}"
)

customer_groups=(
  "rg-mappo-ma-apps-${customer_key}"
  "rg-mappo-ma-def-${customer_key}"
  "rg-mappo-ma-mrg-target-02"
  "rg-mappo-ma-shared-env-${customer_key}"
)

delete_group_if_exists() {
  local subscription_id="$1"
  local group_name="$2"

  local exists
  exists="$(az group exists --subscription "${subscription_id}" --name "${group_name}" -o tsv)"
  if [[ "${exists}" != "true" ]]; then
    echo "cleanup-legacy-managed-app-demo: skip (not found) ${subscription_id} :: ${group_name}"
    return
  fi

  echo "cleanup-legacy-managed-app-demo: deleting ${subscription_id} :: ${group_name}"
  az group delete \
    --subscription "${subscription_id}" \
    --name "${group_name}" \
    --yes \
    --no-wait \
    --only-show-errors
}

wait_for_group_deleted() {
  local subscription_id="$1"
  local group_name="$2"
  local iterations=0
  local max_iterations=120

  while true; do
    local exists
    exists="$(az group exists --subscription "${subscription_id}" --name "${group_name}" -o tsv)"
    if [[ "${exists}" != "true" ]]; then
      echo "cleanup-legacy-managed-app-demo: deleted ${subscription_id} :: ${group_name}"
      return
    fi

    iterations=$((iterations + 1))
    if (( iterations >= max_iterations )); then
      echo "cleanup-legacy-managed-app-demo: timed out waiting for delete: ${subscription_id} :: ${group_name}" >&2
      return
    fi
    sleep 5
  done
}

for group_name in "${provider_groups[@]}"; do
  delete_group_if_exists "${provider_subscription_id}" "${group_name}"
done
for group_name in "${customer_groups[@]}"; do
  delete_group_if_exists "${customer_subscription_id}" "${group_name}"
done

for group_name in "${provider_groups[@]}"; do
  wait_for_group_deleted "${provider_subscription_id}" "${group_name}"
done
for group_name in "${customer_groups[@]}"; do
  wait_for_group_deleted "${customer_subscription_id}" "${group_name}"
done

echo "cleanup-legacy-managed-app-demo: complete."

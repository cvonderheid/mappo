#!/usr/bin/env bash
set -euo pipefail

echo "$(basename "$0"): legacy runtime script; use infra/pulumi for MAPPO runtime infrastructure." >&2

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

STACK="${PULUMI_STACK:-demo}"
SUBSCRIPTION_ID=""
RESOURCE_GROUP=""
JOB_NAME=""
TIMEOUT_SECONDS="900"
TAIL_LINES="100"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Start and wait for the MAPPO runtime Flyway migration Container Apps Job execution.

Options:
  --stack <name>             Naming suffix seed (default: \$PULUMI_STACK or demo)
  --subscription-id <id>     Azure subscription for runtime resources
  --resource-group <name>    Runtime resource group (default: rg-mappo-runtime-<stack>)
  --job-name <name>          Migration job name (default: job-mappo-db-<stack>)
  --timeout-seconds <sec>    Wait timeout seconds (default: 900)
  --tail-lines <count>       Log lines on failure (default: 100)
  -h, --help                 Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack)
      STACK="${2:-}"
      shift 2
      ;;
    --subscription-id)
      SUBSCRIPTION_ID="${2:-}"
      shift 2
      ;;
    --resource-group)
      RESOURCE_GROUP="${2:-}"
      shift 2
      ;;
    --job-name)
      JOB_NAME="${2:-}"
      shift 2
      ;;
    --timeout-seconds)
      TIMEOUT_SECONDS="${2:-}"
      shift 2
      ;;
    --tail-lines)
      TAIL_LINES="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "runtime-db-migrate-job-run: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v az >/dev/null 2>&1; then
  echo "runtime-db-migrate-job-run: Azure CLI is required." >&2
  exit 1
fi
if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "runtime-db-migrate-job-run: no active Azure login context. Run 'az login' first." >&2
  exit 1
fi

if [[ -n "${SUBSCRIPTION_ID}" ]]; then
  az account set --subscription "${SUBSCRIPTION_ID}"
else
  SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
fi

normalize_stack() {
  printf "%s" "$1" \
    | tr "[:upper:]" "[:lower:]" \
    | tr -cd "a-z0-9-" \
    | sed -E 's/^-+//; s/-+$//; s/-+/-/g'
}

stack_token="$(normalize_stack "${STACK}")"
if [[ -z "${stack_token}" ]]; then
  stack_token="demo"
fi

if [[ -z "${RESOURCE_GROUP}" ]]; then
  RESOURCE_GROUP="rg-mappo-runtime-${stack_token}"
fi

if [[ -z "${JOB_NAME}" ]]; then
  JOB_NAME="$(printf "job-mappo-db-%s" "${stack_token}" | cut -c1-32 | sed -E 's/-+$//')"
fi
JOB_NAME="$(printf "%s" "${JOB_NAME}" | tr "[:upper:]" "[:lower:]")"

if ! [[ "${TIMEOUT_SECONDS}" =~ ^[0-9]+$ ]] || (( TIMEOUT_SECONDS < 60 )); then
  echo "runtime-db-migrate-job-run: --timeout-seconds must be an integer >= 60." >&2
  exit 2
fi
if ! [[ "${TAIL_LINES}" =~ ^[0-9]+$ ]] || (( TAIL_LINES < 1 )) || (( TAIL_LINES > 300 )); then
  echo "runtime-db-migrate-job-run: --tail-lines must be an integer between 1 and 300." >&2
  exit 2
fi
if ! [[ "${JOB_NAME}" =~ ^[a-z][a-z0-9-]{0,30}[a-z0-9]$ ]]; then
  echo "runtime-db-migrate-job-run: invalid job name '${JOB_NAME}' (must match ^[a-z][a-z0-9-]{0,30}[a-z0-9]$)." >&2
  exit 2
fi

if ! az containerapp job show --name "${JOB_NAME}" --resource-group "${RESOURCE_GROUP}" --only-show-errors >/dev/null 2>&1; then
  echo "runtime-db-migrate-job-run: job not found: ${JOB_NAME} in ${RESOURCE_GROUP}" >&2
  exit 1
fi

echo "runtime-db-migrate-job-run: subscription=${SUBSCRIPTION_ID}"
echo "runtime-db-migrate-job-run: resource_group=${RESOURCE_GROUP}"
echo "runtime-db-migrate-job-run: job_name=${JOB_NAME}"
echo "runtime-db-migrate-job-run: timeout_seconds=${TIMEOUT_SECONDS}"

start_json="$(
  az containerapp job start \
    --name "${JOB_NAME}" \
    --resource-group "${RESOURCE_GROUP}" \
    --only-show-errors \
    -o json
)"
execution_name="$("${ROOT_DIR}/scripts/run_tooling.sh" \
  azure-script-support job-execution-name \
  --json "${start_json}")"

if [[ -z "${execution_name}" ]]; then
  execution_name="$(az containerapp job execution list --name "${JOB_NAME}" --resource-group "${RESOURCE_GROUP}" --query "sort_by(@, &properties.startTime)[-1].name" -o tsv --only-show-errors 2>/dev/null || true)"
fi
if [[ -z "${execution_name}" ]]; then
  echo "runtime-db-migrate-job-run: failed to resolve execution name after start." >&2
  exit 1
fi

echo "runtime-db-migrate-job-run: execution=${execution_name}"
deadline_epoch="$(( $(date +%s) + TIMEOUT_SECONDS ))"
while true; do
  status="$(az containerapp job execution show --name "${JOB_NAME}" --resource-group "${RESOURCE_GROUP}" --job-execution-name "${execution_name}" --query "properties.status" -o tsv --only-show-errors 2>/dev/null || true)"
  if [[ "${status}" == "Succeeded" ]]; then
    echo "runtime-db-migrate-job-run: succeeded (${execution_name})."
    break
  fi
  if [[ "${status}" =~ ^(Failed|Canceled|Cancelled)$ ]]; then
    echo "runtime-db-migrate-job-run: failed (${execution_name}) status=${status}" >&2
    az containerapp job logs show \
      --name "${JOB_NAME}" \
      --resource-group "${RESOURCE_GROUP}" \
      --execution "${execution_name}" \
      --container flyway \
      --tail "${TAIL_LINES}" \
      --format text \
      --only-show-errors \
      || true
    exit 1
  fi
  if (( $(date +%s) >= deadline_epoch )); then
    echo "runtime-db-migrate-job-run: timeout (${TIMEOUT_SECONDS}s) waiting for ${execution_name}" >&2
    exit 1
  fi
  sleep 5
done

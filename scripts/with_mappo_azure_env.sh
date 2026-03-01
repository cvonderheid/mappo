#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${MAPPO_AZURE_ENV_FILE:-${ROOT_DIR}/.data/mappo-azure.env}"
DB_ENV_FILE="${MAPPO_DB_ENV_FILE:-${ROOT_DIR}/.data/mappo-db.env}"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  source "${ENV_FILE}"
fi

if [[ -f "${DB_ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  source "${DB_ENV_FILE}"
fi

if [[ $# -eq 0 ]]; then
  echo "with_mappo_azure_env: no command provided." >&2
  exit 2
fi

exec "$@"

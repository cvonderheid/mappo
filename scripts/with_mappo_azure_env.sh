#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${MAPPO_AZURE_ENV_FILE:-${ROOT_DIR}/.data/mappo-azure.env}"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  source "${ENV_FILE}"
fi

if [[ $# -eq 0 ]]; then
  echo "with_mappo_azure_env: no command provided." >&2
  exit 2
fi

exec "$@"

#!/usr/bin/env bash
set -euo pipefail

resource="https://api.partnercenter.microsoft.com"
env_file=""
raw="false"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Acquire a Partner Center API access token via Azure CLI.

Options:
  --resource <uri>     Token audience/resource (default: https://api.partnercenter.microsoft.com)
  --env-file <path>    Write MAPPO_PARTNER_CENTER_ACCESS_TOKEN and expiry to file
  --raw                Print only access token (for scripts)
  -h, --help           Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --resource)
      resource="${2:-}"
      shift 2
      ;;
    --env-file)
      env_file="${2:-}"
      shift 2
      ;;
    --raw)
      raw="true"
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "partner-center-get-token: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v az >/dev/null 2>&1; then
  echo "partner-center-get-token: Azure CLI is required." >&2
  exit 1
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "partner-center-get-token: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

payload="$(az account get-access-token --resource "${resource}" --query '{token:accessToken,expiresOn:expiresOn}' -o json)"
token="$(python3 - <<'PY' "${payload}"
import json
import sys
row = json.loads(sys.argv[1])
print(str(row.get("token", "")).strip())
PY
)"
expires_on="$(python3 - <<'PY' "${payload}"
import json
import sys
row = json.loads(sys.argv[1])
print(str(row.get("expiresOn", "")).strip())
PY
)"

if [[ -z "${token}" ]]; then
  echo "partner-center-get-token: Azure CLI returned an empty token." >&2
  exit 1
fi

if [[ -n "${env_file}" ]]; then
  mkdir -p "$(dirname "${env_file}")"
  cat > "${env_file}" <<EOF
MAPPO_PARTNER_CENTER_ACCESS_TOKEN='${token}'
MAPPO_PARTNER_CENTER_TOKEN_EXPIRES_ON='${expires_on}'
EOF
  echo "partner-center-get-token: wrote token env file: ${env_file}"
fi

if [[ "${raw}" == "true" ]]; then
  printf '%s\n' "${token}"
else
  echo "partner-center-get-token: acquired token (expires: ${expires_on:-unknown})."
fi

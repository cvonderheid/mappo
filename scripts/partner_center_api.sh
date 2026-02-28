#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

method="GET"
url=""
body_file=""
headers=()
token="${MAPPO_PARTNER_CENTER_ACCESS_TOKEN:-}"
token_env_file="${ROOT_DIR}/.data/mappo-partnercenter.env"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Invoke Partner Center APIs with bearer token auth.

Options:
  --method <verb>            HTTP method (default: GET)
  --url <full-url>           Full Partner Center API URL (required)
  --body-file <path>         JSON request body file (for POST/PUT/PATCH)
  --header <name:value>      Additional header (repeatable)
  --token <bearer-token>     Override MAPPO_PARTNER_CENTER_ACCESS_TOKEN
  --token-env-file <path>    Token env file used when token is absent (default: .data/mappo-partnercenter.env)
  -h, --help                 Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --method)
      method="${2:-}"
      shift 2
      ;;
    --url)
      url="${2:-}"
      shift 2
      ;;
    --body-file)
      body_file="${2:-}"
      shift 2
      ;;
    --header)
      headers+=("${2:-}")
      shift 2
      ;;
    --token)
      token="${2:-}"
      shift 2
      ;;
    --token-env-file)
      token_env_file="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "partner-center-api: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${url}" ]]; then
  echo "partner-center-api: --url is required." >&2
  exit 1
fi

if [[ -z "${token}" && -f "${token_env_file}" ]]; then
  # shellcheck source=/dev/null
  source "${token_env_file}"
  token="${MAPPO_PARTNER_CENTER_ACCESS_TOKEN:-}"
fi

if [[ -z "${token}" ]]; then
  token="$("${ROOT_DIR}/scripts/partner_center_get_token.sh" --raw)"
fi

curl_args=(
  --silent
  --show-error
  --fail-with-body
  -X "${method}"
  -H "Authorization: Bearer ${token}"
  -H "Accept: application/json"
  "${url}"
)

if [[ -n "${body_file}" ]]; then
  if [[ ! -f "${body_file}" ]]; then
    echo "partner-center-api: body file not found: ${body_file}" >&2
    exit 1
  fi
  curl_args+=(-H "Content-Type: application/json" --data "@${body_file}")
fi

for header in "${headers[@]}"; do
  curl_args+=(-H "${header}")
done

curl "${curl_args[@]}"

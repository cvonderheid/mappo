#!/bin/sh
set -eu

runtime_config_file="${MAPPO_RUNTIME_CONFIG_FILE:-/usr/share/nginx/html/runtime-config.js}"
api_base_url="${MAPPO_API_BASE_URL:-}"
escaped_api_base_url="$(printf '%s' "${api_base_url}" | sed 's/\\/\\\\/g; s/"/\\"/g')"

cat >"${runtime_config_file}" <<EOF
window.__MAPPO_RUNTIME_CONFIG__ = {
  apiBaseUrl: "${escaped_api_base_url}"
};
EOF

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if command -v flyway >/dev/null 2>&1; then
  exec flyway \
    -configFiles="${ROOT_DIR}/backend/flyway/flyway.conf" \
    -locations="filesystem:${ROOT_DIR}/backend/flyway/sql" \
    "$@"
fi

exec docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -v "${ROOT_DIR}/backend/flyway/sql:/flyway/sql" \
  -v "${ROOT_DIR}/backend/flyway/flyway.conf:/flyway/conf/flyway.conf" \
  flyway/flyway:10 \
  -url="jdbc:postgresql://host.docker.internal:5432/mappo" \
  "$@"

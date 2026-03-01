#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DB_HOST="${MAPPO_DB_HOST:-${PGHOST:-localhost}}"
DB_PORT="${MAPPO_DB_PORT:-${PGPORT:-5433}}"
DB_NAME="${MAPPO_DB_NAME:-mappo}"
DB_USER="${MAPPO_DB_USER:-${PGUSER:-mappo}}"
DB_PASSWORD="${MAPPO_DB_PASSWORD:-${PGPASSWORD:-mappo}}"
DB_SSLMODE="${MAPPO_DB_SSLMODE:-}"

JDBC_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
if [[ -n "${DB_SSLMODE}" ]]; then
  JDBC_URL="${JDBC_URL}?sslmode=${DB_SSLMODE}"
fi

if command -v flyway >/dev/null 2>&1; then
  exec flyway \
    -configFiles="${ROOT_DIR}/backend/flyway/flyway.conf" \
    -locations="filesystem:${ROOT_DIR}/backend/flyway/sql" \
    -url="${JDBC_URL}" \
    -user="${DB_USER}" \
    -password="${DB_PASSWORD}" \
    "$@"
fi

DOCKER_DB_HOST="${DB_HOST}"
if [[ "${DB_HOST}" == "localhost" || "${DB_HOST}" == "127.0.0.1" ]]; then
  DOCKER_DB_HOST="host.docker.internal"
fi

DOCKER_JDBC_URL="jdbc:postgresql://${DOCKER_DB_HOST}:${DB_PORT}/${DB_NAME}"
if [[ -n "${DB_SSLMODE}" ]]; then
  DOCKER_JDBC_URL="${DOCKER_JDBC_URL}?sslmode=${DB_SSLMODE}"
fi

exec docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -v "${ROOT_DIR}/backend/flyway/sql:/flyway/sql" \
  -v "${ROOT_DIR}/backend/flyway/flyway.conf:/flyway/conf/flyway.conf" \
  flyway/flyway:10 \
  -url="${DOCKER_JDBC_URL}" \
  -user="${DB_USER}" \
  -password="${DB_PASSWORD}" \
  "$@"

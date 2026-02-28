#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${MAPPO_COMPOSE_FILE:-${ROOT_DIR}/infra/docker-compose.yml}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5433}"
PGUSER="${PGUSER:-mappo}"
PGPASSWORD="${PGPASSWORD:-mappo}"
DB_NAME="${MAPPO_DB_NAME:-mappo}"

can_use_host_psql() {
  command -v psql >/dev/null 2>&1 && command -v createdb >/dev/null 2>&1
}

is_local_compose_postgres() {
  [[ "${PGHOST}" == "localhost" || "${PGHOST}" == "127.0.0.1" ]] && [[ "${PGPORT}" == "5433" ]]
}

wait_for_compose_postgres() {
  if ! command -v docker >/dev/null 2>&1; then
    return 1
  fi
  docker compose -f "${COMPOSE_FILE}" up -d postgres >/dev/null
  for _ in $(seq 1 30); do
    if docker compose -f "${COMPOSE_FILE}" exec -T postgres pg_isready -U "${PGUSER}" -d postgres >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

db_exists_host() {
  PGPASSWORD="$PGPASSWORD" psql \
    -h "$PGHOST" \
    -p "$PGPORT" \
    -U "$PGUSER" \
    -d postgres \
    -Atqc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1
}

create_db_host() {
  PGPASSWORD="$PGPASSWORD" createdb -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$DB_NAME"
}

db_exists_compose() {
  docker compose -f "${COMPOSE_FILE}" exec -T postgres \
    psql -U "${PGUSER}" -d postgres -Atqc \
    "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1
}

create_db_compose() {
  docker compose -f "${COMPOSE_FILE}" exec -T postgres createdb -U "${PGUSER}" "${DB_NAME}"
}

if can_use_host_psql; then
  if ! db_exists_host >/dev/null 2>&1; then
    if is_local_compose_postgres; then
      wait_for_compose_postgres || {
        echo "failed to start/wait for compose postgres service" >&2
        exit 1
      }
    fi
  fi
  if db_exists_host; then
    echo "db '${DB_NAME}' already exists"
    exit 0
  fi
  create_db_host
  echo "created db '${DB_NAME}'"
  exit 0
fi

if ! is_local_compose_postgres; then
  echo "psql/createdb not available and non-compose DB host configured (${PGHOST}:${PGPORT})" >&2
  exit 1
fi

wait_for_compose_postgres || {
  echo "failed to start/wait for compose postgres service" >&2
  exit 1
}

if db_exists_compose; then
  echo "db '${DB_NAME}' already exists"
  exit 0
fi

create_db_compose
echo "created db '${DB_NAME}'"

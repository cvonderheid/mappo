#!/usr/bin/env bash
set -euo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5433}"
PGUSER="${PGUSER:-mappo}"
PGPASSWORD="${PGPASSWORD:-mappo}"
DB_NAME="${MAPPO_DB_NAME:-mappo}"

if PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -Atqc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1; then
  echo "db '${DB_NAME}' already exists"
  exit 0
fi

PGPASSWORD="$PGPASSWORD" createdb -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$DB_NAME"
echo "created db '${DB_NAME}'"

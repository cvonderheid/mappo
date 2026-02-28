#!/usr/bin/env bash
set -euo pipefail

DATABASE_URL="${MAPPO_DATABASE_URL:-${DATABASE_URL:-postgresql+psycopg://mappo:mappo@localhost:5433/mappo}}"
OUTFILE="app/db/generated/models.py"

uv run sqlacodegen "$DATABASE_URL" --generator declarative --outfile "$OUTFILE"

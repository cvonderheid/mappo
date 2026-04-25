#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IAC_DIR="${ROOT_DIR}/infra/pulumi"
STACK="${PULUMI_STACK:-dev}"
ENV_FILE_DEFAULT="${ROOT_DIR}/.data/mappo.env"
ENV_FILE="${ENV_FILE_DEFAULT}"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Exports managed Postgres connection settings from Pulumi stack outputs into an env file.

Options:
  --stack <name>      Pulumi stack name (default: \$PULUMI_STACK or dev)
  --env-file <path>   Output env file path (default: ${ENV_FILE_DEFAULT})
  -h, --help          Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack)
      STACK="${2:-}"
      shift 2
      ;;
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "iac-export-db-env: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v pulumi >/dev/null 2>&1; then
  echo "iac-export-db-env: pulumi CLI is required." >&2
  exit 1
fi

if [[ ! -d "${IAC_DIR}" ]]; then
  echo "iac-export-db-env: missing IaC directory: ${IAC_DIR}" >&2
  exit 1
fi

mkdir -p "$(dirname "${ENV_FILE}")"

outputs_json="$(
  cd "${IAC_DIR}"
  pulumi stack output --stack "${STACK}" --json --show-secrets
)"
"${ROOT_DIR}/scripts/run_tooling.sh" \
  azure-script-support export-db-env \
  --outputs-json "${outputs_json}" \
  --env-file "${ENV_FILE}"

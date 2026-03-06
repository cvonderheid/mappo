#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="${ROOT_DIR}/integrations/marketplace-forwarder-function"
OUTPUT_ZIP="${ROOT_DIR}/.data/marketplace-forwarder-function.zip"
FUNCTION_APP_NAME="mappo-marketplace-forwarder"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Package the Java Azure Function marketplace forwarder for zip deployment.

Options:
  --src-dir <path>            Module directory (default: integrations/marketplace-forwarder-function)
  --output-zip <path>         Output zip path (default: .data/marketplace-forwarder-function.zip)
  --function-app-name <name>  Function app name used for staging directory (default: mappo-marketplace-forwarder)
  -h, --help            Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --src-dir)
      SRC_DIR="${2:-}"
      shift 2
      ;;
    --output-zip)
      OUTPUT_ZIP="${2:-}"
      shift 2
      ;;
    --function-app-name)
      FUNCTION_APP_NAME="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "marketplace-forwarder-package: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -d "${SRC_DIR}" ]]; then
  echo "marketplace-forwarder-package: source directory not found: ${SRC_DIR}" >&2
  exit 1
fi

if ! command -v zip >/dev/null 2>&1; then
  echo "marketplace-forwarder-package: zip is required." >&2
  exit 1
fi

mkdir -p "$(dirname "${OUTPUT_ZIP}")"

./mvnw -q -pl integrations/marketplace-forwarder-function -am \
  -DskipTests \
  -Dfunction.app.name="${FUNCTION_APP_NAME}" \
  package

STAGING_DIR="${SRC_DIR}/target/azure-functions/${FUNCTION_APP_NAME}"
if [[ ! -d "${STAGING_DIR}" ]]; then
  echo "marketplace-forwarder-package: expected staging directory not found: ${STAGING_DIR}" >&2
  exit 1
fi

rm -f "${OUTPUT_ZIP}"
(
  cd "${STAGING_DIR}"
  zip -rq "${OUTPUT_ZIP}" .
)

echo "marketplace-forwarder-package: wrote ${OUTPUT_ZIP}"

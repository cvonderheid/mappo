#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_DIR="${ROOT_DIR}/delivery/appservice-demo-app"
OUTPUT_FILE="${ROOT_DIR}/.data/appservice-fleet-package/appservice-demo-app.zip"

usage() {
  cat <<EOF
usage: $(basename "$0") [options]

Package the App Service demo app into a deployable zip artifact.

Options:
  --source-dir <path>     Application source directory (default: delivery/appservice-demo-app)
  --output-file <path>    Zip artifact output path (default: .data/appservice-fleet-package/appservice-demo-app.zip)
  -h, --help              Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source-dir)
      SOURCE_DIR="${2:-}"
      shift 2
      ;;
    --output-file)
      OUTPUT_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "appservice-fleet-package: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v zip >/dev/null 2>&1; then
  echo "appservice-fleet-package: zip is required." >&2
  exit 1
fi
if [[ ! -d "${SOURCE_DIR}" ]]; then
  echo "appservice-fleet-package: source dir not found: ${SOURCE_DIR}" >&2
  exit 1
fi

mkdir -p "$(dirname "${OUTPUT_FILE}")"
rm -f "${OUTPUT_FILE}"

pushd "${SOURCE_DIR}" >/dev/null
zip -qr "${OUTPUT_FILE}" .
popd >/dev/null

echo "appservice-fleet-package: wrote ${OUTPUT_FILE}"

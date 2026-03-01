#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="${ROOT_DIR}/integrations/marketplace-forwarder-function"
OUTPUT_ZIP="${ROOT_DIR}/.data/marketplace-forwarder-function.zip"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Package the Azure Function marketplace forwarder source for zip deployment.

Options:
  --src-dir <path>      Source directory (default: integrations/marketplace-forwarder-function)
  --output-zip <path>   Output zip path (default: .data/marketplace-forwarder-function.zip)
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

if ! command -v python3 >/dev/null 2>&1; then
  echo "marketplace-forwarder-package: python3 is required." >&2
  exit 1
fi

mkdir -p "$(dirname "${OUTPUT_ZIP}")"

python3 - <<'PY' "${SRC_DIR}" "${OUTPUT_ZIP}"
from pathlib import Path
import sys
import zipfile

src_dir = Path(sys.argv[1]).resolve()
output_zip = Path(sys.argv[2]).resolve()

if output_zip.exists():
    output_zip.unlink()

ignored_suffixes = {".pyc", ".pyo"}
ignored_dir_names = {"__pycache__", ".pytest_cache", ".venv", ".git"}

with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    for path in src_dir.rglob("*"):
        if path.is_dir():
            continue
        if any(part in ignored_dir_names for part in path.parts):
            continue
        if path.suffix in ignored_suffixes:
            continue
        archive.write(path, path.relative_to(src_dir))

print(f"marketplace-forwarder-package: wrote {output_zip}")
PY

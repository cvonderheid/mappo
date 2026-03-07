#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

args_file="$(mktemp)"
cleanup() {
  rm -f "${args_file}"
}
trap cleanup EXIT

for argument in "$@"; do
  printf '%s' "${argument}" | base64 | tr -d '\n' >> "${args_file}"
  printf '\n' >> "${args_file}"
done

cd "${ROOT_DIR}"
./mvnw -q -f tooling/pom.xml exec:java \
  -Dexec.mainClass=com.mappo.tooling.ToolingArgsFileBootstrap \
  -Dmappo.argsFile="${args_file}"

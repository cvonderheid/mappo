#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"
escaped_args=""
if [[ $# -gt 0 ]]; then
  printf -v escaped_args '%q ' "$@"
  escaped_args="${escaped_args% }"
fi
./mvnw -q -f tooling/pom.xml exec:java \
  -Dexec.mainClass=com.mappo.tooling.ToolingApplication \
  "-Dexec.args=marketplace-forwarder-replay${escaped_args:+ ${escaped_args}}"

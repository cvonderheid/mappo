#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

paths=(
  ".data/mappo-azure.env"
  ".data/mappo-db.env"
  ".data/mappo-runtime.env"
  ".data/mappo-easyauth.env"
  ".data/mappo-target-inventory.json"
  ".data/mappo-target-inventory"*.json
  ".data/mappo-managedapp-sim-state.json"
  ".data/marketplace-forwarder-function.zip"
)

removed=0
shopt -s nullglob
for pattern in "${paths[@]}"; do
  matches=("${ROOT_DIR}/${pattern}")
  for abs in "${matches[@]}"; do
    if [[ -e "${abs}" ]]; then
      rm -f "${abs}"
      rel="${abs#${ROOT_DIR}/}"
      echo "clean-slate-local: removed ${rel}"
      removed=$((removed + 1))
    fi
  done
done
shopt -u nullglob

if [[ -d "${ROOT_DIR}/.data/managedapp-sim" ]]; then
  rm -rf "${ROOT_DIR}/.data/managedapp-sim"
  echo "clean-slate-local: removed .data/managedapp-sim/"
  removed=$((removed + 1))
fi

echo "clean-slate-local: removed_files=${removed}"

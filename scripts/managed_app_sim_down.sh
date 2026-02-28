#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_STATE_FILE="${ROOT_DIR}/.data/mappo-managedapp-sim-state.json"

state_file="${DEFAULT_STATE_FILE}"
delete_resource_groups="false"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Tear down Managed Application simulation resources created by managed_app_sim_up.sh.

Options:
  --state-file <path>         State file from managed-app-sim-up (default: ${DEFAULT_STATE_FILE})
  --delete-resource-groups    Also delete app/definition resource groups tracked in state (default: false)
  -h, --help                  Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --state-file)
      state_file="${2:-}"
      shift 2
      ;;
    --delete-resource-groups)
      delete_resource_groups="true"
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "managed-app-sim-down: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v az >/dev/null 2>&1; then
  echo "managed-app-sim-down: Azure CLI is required." >&2
  exit 1
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "managed-app-sim-down: no active Azure login context. Run 'az login' first." >&2
  exit 1
fi

python3 - <<'PY' "${state_file}" "${delete_resource_groups}"
from __future__ import annotations

import json
import subprocess
import sys
import time
from pathlib import Path
from typing import Any


def run_az_no_output(args: list[str], *, allow_failure: bool = False) -> bool:
    command = ["az", *args, "--only-show-errors", "-o", "none"]
    result = subprocess.run(command, check=False, capture_output=True, text=True)
    if result.returncode != 0:
        if allow_failure:
            return False
        stderr = result.stderr.strip()
        raise RuntimeError(
            f"Azure CLI command failed ({' '.join(command)}): {stderr or 'unknown error'}"
        )
    return True


def run_az_json(args: list[str], *, allow_failure: bool = False) -> Any:
    command = ["az", *args, "-o", "json"]
    result = subprocess.run(command, check=False, capture_output=True, text=True)
    if result.returncode != 0:
        if allow_failure:
            return None
        stderr = result.stderr.strip()
        raise RuntimeError(
            f"Azure CLI command failed ({' '.join(command)}): {stderr or 'unknown error'}"
        )
    return json.loads(result.stdout or "null")


def delete_managed_app(
    *,
    subscription_id: str,
    resource_group: str,
    app_name: str,
) -> None:
    existing = run_az_json(
        [
            "managedapp",
            "show",
            "--subscription",
            subscription_id,
            "--resource-group",
            resource_group,
            "--name",
            app_name,
        ],
        allow_failure=True,
    )
    if not isinstance(existing, dict):
        return

    run_az_no_output(
        [
            "managedapp",
            "delete",
            "--subscription",
            subscription_id,
            "--resource-group",
            resource_group,
            "--name",
            app_name,
            "--yes",
        ]
    )


def delete_resource_group_if_exists(subscription_id: str, resource_group_name: str) -> None:
    exists = subprocess.run(
        [
            "az",
            "group",
            "exists",
            "--subscription",
            subscription_id,
            "--name",
            resource_group_name,
            "-o",
            "tsv",
        ],
        check=False,
        capture_output=True,
        text=True,
    ).stdout.strip()
    if exists.lower() != "true":
        return

    run_az_no_output(
        [
            "group",
            "delete",
            "--subscription",
            subscription_id,
            "--name",
            resource_group_name,
            "--yes",
            "--no-wait",
        ]
    )


def delete_definition_if_exists(
    *,
    subscription_id: str,
    definition_resource_group: str,
    definition_name: str,
) -> None:
    existing = run_az_json(
        [
            "managedapp",
            "definition",
            "show",
            "--subscription",
            subscription_id,
            "--resource-group",
            definition_resource_group,
            "--name",
            definition_name,
        ],
        allow_failure=True,
    )
    if not isinstance(existing, dict):
        return
    run_az_no_output(
        [
            "managedapp",
            "definition",
            "delete",
            "--subscription",
            subscription_id,
            "--resource-group",
            definition_resource_group,
            "--name",
            definition_name,
            "--yes",
        ]
    )


def main() -> int:
    state_file = Path(sys.argv[1]).expanduser().resolve()
    delete_resource_groups = sys.argv[2].strip().lower() == "true"

    if not state_file.exists():
        raise RuntimeError(f"State file not found: {state_file}")

    state = json.loads(state_file.read_text(encoding="utf-8"))
    if not isinstance(state, dict):
        raise RuntimeError(f"Invalid state file: {state_file}")

    subscriptions = state.get("subscriptions")
    if not isinstance(subscriptions, list):
        raise RuntimeError(f"Invalid state file (subscriptions missing): {state_file}")

    deleted_apps = 0
    deleted_mrgs = 0
    deleted_defs = 0

    for sub in subscriptions:
        if not isinstance(sub, dict):
            continue
        subscription_id = str(sub.get("subscription_id", "")).strip()
        if not subscription_id:
            continue
        app_rg = str(sub.get("application_resource_group", "")).strip()

        targets = sub.get("targets")
        if isinstance(targets, list):
            for target in targets:
                if not isinstance(target, dict):
                    continue
                app_name = str(target.get("managed_app_name", "")).strip()
                managed_rg_name = str(target.get("managed_resource_group_name", "")).strip()

                if app_name and app_rg:
                    delete_managed_app(
                        subscription_id=subscription_id,
                        resource_group=app_rg,
                        app_name=app_name,
                    )
                    deleted_apps += 1

                if managed_rg_name:
                    delete_resource_group_if_exists(subscription_id, managed_rg_name)
                    deleted_mrgs += 1

        definition = sub.get("definition")
        if isinstance(definition, dict):
            definition_rg = str(definition.get("resource_group", "")).strip()
            definition_name = str(definition.get("name", "")).strip()
            if definition_rg and definition_name:
                # Wait briefly so assignment/app delete propagates before definition delete.
                time.sleep(2)
                delete_definition_if_exists(
                    subscription_id=subscription_id,
                    definition_resource_group=definition_rg,
                    definition_name=definition_name,
                )
                deleted_defs += 1

        if delete_resource_groups:
            if app_rg:
                delete_resource_group_if_exists(subscription_id, app_rg)
            if isinstance(definition, dict):
                definition_rg = str(definition.get("resource_group", "")).strip()
                if definition_rg:
                    delete_resource_group_if_exists(subscription_id, definition_rg)

    print(
        "managed-app-sim-down: "
        f"requested deletion for {deleted_apps} managed app(s), "
        f"{deleted_mrgs} managed resource group(s), "
        f"{deleted_defs} definition(s)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
PY

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_TARGET_FILE="${ROOT_DIR}/.data/mappo-target-inventory.json"
DEFAULT_STATE_FILE="${ROOT_DIR}/.data/mappo-managedapp-sim-state.json"
DEFAULT_LOCATION="eastus"
DEFAULT_IMAGE="mcr.microsoft.com/azuredocs/containerapps-helloworld:latest"
DEFAULT_PREFIX="mappo-ma"
CONTRIBUTOR_ROLE_ID="b24988ac-6180-42a0-ab88-20f7382dd24c"

target_file="${DEFAULT_TARGET_FILE}"
state_file="${DEFAULT_STATE_FILE}"
subscriptions_csv=""
location="${DEFAULT_LOCATION}"
image="${DEFAULT_IMAGE}"
prefix="${DEFAULT_PREFIX}"
max_targets=10

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Provision service-catalog Managed Application simulation targets for MAPPO.

Options:
  --target-file <path>       Source target inventory JSON (default: ${DEFAULT_TARGET_FILE})
  --state-file <path>        Output state file for teardown (default: ${DEFAULT_STATE_FILE})
  --subscriptions <csv>      Optional filter of subscription IDs
  --location <azure-region>  Region for app/definition resource groups (default: ${DEFAULT_LOCATION})
  --image <container-image>  Container image for simulated target app (default: ${DEFAULT_IMAGE})
  --prefix <name-prefix>     Resource name prefix (default: ${DEFAULT_PREFIX})
  --max-targets <n>          Maximum targets to provision from source (default: 10)
  -h, --help                 Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target-file)
      target_file="${2:-}"
      shift 2
      ;;
    --state-file)
      state_file="${2:-}"
      shift 2
      ;;
    --subscriptions)
      subscriptions_csv="${2:-}"
      shift 2
      ;;
    --location)
      location="${2:-}"
      shift 2
      ;;
    --image)
      image="${2:-}"
      shift 2
      ;;
    --prefix)
      prefix="${2:-}"
      shift 2
      ;;
    --max-targets)
      max_targets="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "managed-app-sim-up: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v az >/dev/null 2>&1; then
  echo "managed-app-sim-up: Azure CLI is required." >&2
  exit 1
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "managed-app-sim-up: no active Azure login context. Run 'az login' first." >&2
  exit 1
fi

mkdir -p "$(dirname "${state_file}")"
mkdir -p "${ROOT_DIR}/.data/managedapp-sim"

python3 - <<'PY' \
  "${target_file}" \
  "${state_file}" \
  "${subscriptions_csv}" \
  "${location}" \
  "${image}" \
  "${prefix}" \
  "${max_targets}" \
  "${CONTRIBUTOR_ROLE_ID}"
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
from collections import defaultdict
from pathlib import Path
from typing import Any


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


def run_az_no_output(args: list[str]) -> None:
    command = ["az", *args, "--only-show-errors", "-o", "none"]
    result = subprocess.run(command, check=False, capture_output=True, text=True)
    if result.returncode != 0:
        stderr = result.stderr.strip()
        raise RuntimeError(
            f"Azure CLI command failed ({' '.join(command)}): {stderr or 'unknown error'}"
        )


def normalize_name(value: str, *, max_len: int = 50) -> str:
    normalized = re.sub(r"[^a-zA-Z0-9-]", "-", value.lower())
    normalized = re.sub(r"-{2,}", "-", normalized).strip("-")
    if not normalized:
        normalized = "item"
    return normalized[:max_len].rstrip("-")


def parse_subscription_filter(subscriptions_csv: str) -> set[str]:
    if not subscriptions_csv.strip():
        return set()
    return {item.strip() for item in subscriptions_csv.split(",") if item.strip()}


def safe_get(d: dict[str, Any], key: str, default: str = "") -> str:
    value = d.get(key)
    if isinstance(value, str):
        stripped = value.strip()
        if stripped:
            return stripped
    return default


def ensure_provider_registered(subscription_id: str, namespace: str) -> None:
    try:
        run_az_no_output(
            [
                "provider",
                "register",
                "--namespace",
                namespace,
                "--subscription",
                subscription_id,
            ]
        )
    except RuntimeError:
        # Continue to state polling: registration might already be in progress.
        pass

    last_state = "Unknown"
    for _ in range(30):
        state = subprocess.run(
            [
                "az",
                "provider",
                "show",
                "--namespace",
                namespace,
                "--subscription",
                subscription_id,
                "--query",
                "registrationState",
                "-o",
                "tsv",
                "--only-show-errors",
            ],
            check=False,
            capture_output=True,
            text=True,
        ).stdout.strip()
        last_state = state or "Unknown"
        if state == "Registered":
            return
        time.sleep(2)
    if last_state == "Registering":
        print(
            f"WARN: provider namespace '{namespace}' still Registering in subscription {subscription_id}; continuing."
        )
        return
    raise RuntimeError(
        f"Provider namespace '{namespace}' is not ready in subscription {subscription_id} (state={last_state})."
    )


def get_signed_in_user_id(subscription_id: str) -> str:
    run_az_no_output(["account", "set", "--subscription", subscription_id])
    result = subprocess.run(
        ["az", "ad", "signed-in-user", "show", "--query", "id", "-o", "tsv", "--only-show-errors"],
        check=False,
        capture_output=True,
        text=True,
    )
    principal_id = result.stdout.strip()
    if result.returncode != 0 or not principal_id:
        stderr = result.stderr.strip()
        raise RuntimeError(
            f"Unable to resolve signed-in user object ID in subscription {subscription_id}: "
            f"{stderr or 'unknown error'}"
        )
    return principal_id


def ensure_resource_group(subscription_id: str, resource_group_name: str, location: str) -> None:
    run_az_no_output(
        [
            "group",
            "create",
            "--subscription",
            subscription_id,
            "--name",
            resource_group_name,
            "--location",
            location,
        ]
    )


def write_definition_files(prefix: str, data_dir: Path) -> tuple[Path, Path]:
    main_template_path = data_dir / f"{prefix}-mainTemplate.json"
    create_ui_definition_path = data_dir / f"{prefix}-createUiDefinition.json"
    main_template = {
        "$schema": "https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#",
        "contentVersion": "1.0.0.0",
        "parameters": {},
        "resources": [],
        "outputs": {},
    }
    create_ui_definition = {
        "$schema": "https://schema.management.azure.com/schemas/0.1.2-preview/CreateUIDefinition.MultiVm.json#",
        "handler": "Microsoft.Azure.CreateUIDef",
        "version": "0.1.2-preview",
        "parameters": {
            "basics": [],
            "steps": [],
            "outputs": {},
        },
    }
    main_template_path.write_text(json.dumps(main_template, indent=2) + "\n", encoding="utf-8")
    create_ui_definition_path.write_text(
        json.dumps(create_ui_definition, indent=2) + "\n",
        encoding="utf-8",
    )
    return main_template_path, create_ui_definition_path


def get_or_create_definition(
    *,
    subscription_id: str,
    definition_resource_group: str,
    definition_name: str,
    location: str,
    principal_id: str,
    contributor_role_id: str,
    main_template_path: Path,
    create_ui_definition_path: Path,
) -> str:
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
    if isinstance(existing, dict):
        definition_id = safe_get(existing, "id")
        if definition_id:
            return definition_id

    run_az_no_output(
        [
            "managedapp",
            "definition",
            "create",
            "--subscription",
            subscription_id,
            "--resource-group",
            definition_resource_group,
            "--name",
            definition_name,
            "--location",
            location,
            "--display-name",
            definition_name,
            "--description",
            "MAPPO managed app simulation definition",
            "--authorizations",
            f"{principal_id}:{contributor_role_id}",
            "--lock-level",
            "None",
            "--create-ui-definition",
            f"@{create_ui_definition_path}",
            "--main-template",
            f"@{main_template_path}",
        ]
    )
    created = run_az_json(
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
        ]
    )
    definition_id = safe_get(created, "id")
    if not definition_id:
        raise RuntimeError(
            f"Managed app definition created but ID missing: {definition_name} ({subscription_id})."
        )
    return definition_id


def list_container_environments(subscription_id: str) -> list[dict[str, str]]:
    rows = run_az_json(["containerapp", "env", "list", "--subscription", subscription_id])
    environments: list[dict[str, str]] = []
    if isinstance(rows, list):
        for row in rows:
            if not isinstance(row, dict):
                continue
            env_id = safe_get(row, "id")
            env_name = safe_get(row, "name")
            resource_group = safe_get(row, "resourceGroup")
            if env_id and env_name and resource_group:
                environments.append({"id": env_id, "name": env_name, "resource_group": resource_group})
    return environments


def create_shared_environment_if_missing(
    *,
    subscription_id: str,
    prefix: str,
    location: str,
) -> dict[str, str]:
    environments = list_container_environments(subscription_id)
    if environments:
        return environments[0]

    key = normalize_name(subscription_id.replace("-", "")[:8], max_len=12)
    environment_resource_group = normalize_name(f"rg-{prefix}-shared-env-{key}", max_len=90)
    environment_name = normalize_name(f"cae-{prefix}-shared-{key}", max_len=32)
    ensure_resource_group(subscription_id, environment_resource_group, location)
    run_az_no_output(
        [
            "containerapp",
            "env",
            "create",
            "--subscription",
            subscription_id,
            "--resource-group",
            environment_resource_group,
            "--name",
            environment_name,
            "--location",
            location,
        ]
    )
    created = run_az_json(
        [
            "containerapp",
            "env",
            "show",
            "--subscription",
            subscription_id,
            "--resource-group",
            environment_resource_group,
            "--name",
            environment_name,
        ]
    )
    return {
        "id": safe_get(created, "id"),
        "name": environment_name,
        "resource_group": environment_resource_group,
    }


def create_managed_app_if_missing(
    *,
    subscription_id: str,
    app_resource_group: str,
    app_name: str,
    managed_resource_group_id: str,
    definition_id: str,
    location: str,
    tags: dict[str, str],
) -> dict[str, Any]:
    existing = run_az_json(
        [
            "managedapp",
            "show",
            "--subscription",
            subscription_id,
            "--resource-group",
            app_resource_group,
            "--name",
            app_name,
        ],
        allow_failure=True,
    )
    if isinstance(existing, dict):
        return existing

    tags_args = [f"{key}={value}" for key, value in tags.items() if value]
    args = [
        "managedapp",
        "create",
        "--subscription",
        subscription_id,
        "--resource-group",
        app_resource_group,
        "--name",
        app_name,
        "--location",
        location,
        "--kind",
        "ServiceCatalog",
        "--managed-rg-id",
        managed_resource_group_id,
        "--managedapp-definition-id",
        definition_id,
    ]
    if tags_args:
        args.extend(["--tags", *tags_args])
    run_az_no_output(args)

    for _ in range(90):
        current = run_az_json(
            [
                "managedapp",
                "show",
                "--subscription",
                subscription_id,
                "--resource-group",
                app_resource_group,
                "--name",
                app_name,
            ]
        )
        if isinstance(current, dict):
            state = safe_get(current, "provisioningState")
            if state == "Succeeded":
                return current
            if state == "Failed":
                raise RuntimeError(
                    f"Managed app {app_name} failed provisioning in subscription {subscription_id}."
                )
        time.sleep(2)
    raise RuntimeError(
        f"Timed out waiting for managed app {app_name} provisioning in subscription {subscription_id}."
    )


def create_container_app_if_missing(
    *,
    subscription_id: str,
    managed_resource_group_name: str,
    container_app_name: str,
    environment_id: str,
    image: str,
    location: str,
    tags: dict[str, str],
) -> dict[str, Any]:
    existing = run_az_json(
        [
            "containerapp",
            "show",
            "--subscription",
            subscription_id,
            "--resource-group",
            managed_resource_group_name,
            "--name",
            container_app_name,
        ],
        allow_failure=True,
    )
    if isinstance(existing, dict):
        return existing

    tags_args = [f"{key}={value}" for key, value in tags.items() if value]
    args = [
        "containerapp",
        "create",
        "--subscription",
        subscription_id,
        "--resource-group",
        managed_resource_group_name,
        "--name",
        container_app_name,
        "--environment",
        environment_id,
        "--image",
        image,
        "--ingress",
        "external",
        "--target-port",
        "80",
        "--min-replicas",
        "1",
        "--max-replicas",
        "1",
    ]
    if tags_args:
        args.extend(["--tags", *tags_args])
    run_az_no_output(args)
    created = run_az_json(
        [
            "containerapp",
            "show",
            "--subscription",
            subscription_id,
            "--resource-group",
            managed_resource_group_name,
            "--name",
            container_app_name,
        ]
    )
    return created if isinstance(created, dict) else {}


def parse_resource_group_name(resource_id: str) -> str:
    parts = [part for part in resource_id.strip("/").split("/") if part]
    for index, part in enumerate(parts):
        if part.lower() == "resourcegroups" and index + 1 < len(parts):
            return parts[index + 1]
    return ""


def main() -> int:
    target_file = Path(sys.argv[1]).expanduser().resolve()
    state_file = Path(sys.argv[2]).expanduser().resolve()
    subscription_filter = parse_subscription_filter(sys.argv[3])
    location = sys.argv[4].strip() or "eastus"
    image = sys.argv[5].strip()
    prefix = normalize_name(sys.argv[6].strip() or "mappo-ma", max_len=20)
    max_targets = int(sys.argv[7])
    contributor_role_id = sys.argv[8].strip()

    if max_targets <= 0:
        raise RuntimeError("--max-targets must be greater than 0.")
    if not target_file.exists():
        raise RuntimeError(f"Target file not found: {target_file}")
    if not image:
        raise RuntimeError("--image cannot be empty.")

    rows = json.loads(target_file.read_text(encoding="utf-8"))
    if not isinstance(rows, list):
        raise RuntimeError("Target file must contain a JSON array.")

    filtered_targets: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        subscription_id = safe_get(row, "subscription_id")
        target_id = safe_get(row, "id")
        if not subscription_id or not target_id:
            continue
        if subscription_filter and subscription_id not in subscription_filter:
            continue
        filtered_targets.append(row)
        if len(filtered_targets) >= max_targets:
            break

    if not filtered_targets:
        raise RuntimeError("No targets selected from target file after filters.")

    original_subscription = subprocess.run(
        ["az", "account", "show", "--query", "id", "-o", "tsv"],
        check=False,
        capture_output=True,
        text=True,
    ).stdout.strip()

    data_dir = state_file.parent / "managedapp-sim"
    data_dir.mkdir(parents=True, exist_ok=True)
    main_template_path, create_ui_definition_path = write_definition_files(prefix, data_dir)

    targets_by_subscription: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in filtered_targets:
        targets_by_subscription[safe_get(row, "subscription_id")].append(row)

    state: dict[str, Any] = {
        "version": 1,
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "source_target_file": str(target_file),
        "parameters": {
            "location": location,
            "image": image,
            "prefix": prefix,
            "max_targets": max_targets,
            "subscriptions_filter": sorted(subscription_filter),
        },
        "subscriptions": [],
    }

    created_count = 0
    try:
        for subscription_id, subscription_targets in targets_by_subscription.items():
            ensure_provider_registered(subscription_id, "Microsoft.Solutions")
            ensure_provider_registered(subscription_id, "Microsoft.App")

            principal_id = get_signed_in_user_id(subscription_id)
            key = normalize_name(subscription_id.replace("-", "")[:8], max_len=12)
            definition_resource_group = normalize_name(f"rg-{prefix}-def-{key}", max_len=90)
            app_resource_group = normalize_name(f"rg-{prefix}-apps-{key}", max_len=90)
            definition_name = normalize_name(f"{prefix}-def-{key}", max_len=60)

            ensure_resource_group(subscription_id, definition_resource_group, location)
            ensure_resource_group(subscription_id, app_resource_group, location)
            definition_id = get_or_create_definition(
                subscription_id=subscription_id,
                definition_resource_group=definition_resource_group,
                definition_name=definition_name,
                location=location,
                principal_id=principal_id,
                contributor_role_id=contributor_role_id,
                main_template_path=main_template_path,
                create_ui_definition_path=create_ui_definition_path,
            )

            shared_environment = create_shared_environment_if_missing(
                subscription_id=subscription_id,
                prefix=prefix,
                location=location,
            )
            environment_id = safe_get(shared_environment, "id")
            if not environment_id:
                raise RuntimeError(
                    f"Unable to resolve shared container app environment in subscription {subscription_id}."
                )

            subscription_state: dict[str, Any] = {
                "subscription_id": subscription_id,
                "authorization_principal_id": principal_id,
                "definition": {
                    "resource_group": definition_resource_group,
                    "name": definition_name,
                    "id": definition_id,
                },
                "application_resource_group": app_resource_group,
                "shared_environment": shared_environment,
                "targets": [],
            }

            for target in subscription_targets:
                target_id = normalize_name(safe_get(target, "id"), max_len=40)
                tenant_id = safe_get(target, "tenant_id", "unknown-tenant")
                tags_raw = target.get("tags") if isinstance(target.get("tags"), dict) else {}
                tags = {str(k): str(v) for k, v in tags_raw.items()} if isinstance(tags_raw, dict) else {}
                target_group = tags.get("ring", "prod")
                region = tags.get("region", location)
                tier = tags.get("tier", "standard")
                environment = tags.get("environment", "demo")

                app_name = normalize_name(f"{prefix}-{target_id}", max_len=60)
                managed_resource_group_name = normalize_name(f"rg-{prefix}-mrg-{target_id}", max_len=90)
                managed_resource_group_id = (
                    f"/subscriptions/{subscription_id}/resourceGroups/{managed_resource_group_name}"
                )
                container_app_name = normalize_name(f"ca-{prefix}-{target_id}", max_len=32)

                combined_tags = {
                    "ring": target_group,
                    "region": region,
                    "tier": tier,
                    "environment": environment,
                    "tenantId": tenant_id,
                    "targetId": target_id,
                    "mappoSim": "true",
                }

                managed_app = create_managed_app_if_missing(
                    subscription_id=subscription_id,
                    app_resource_group=app_resource_group,
                    app_name=app_name,
                    managed_resource_group_id=managed_resource_group_id,
                    definition_id=definition_id,
                    location=location,
                    tags=combined_tags,
                )
                managed_resource_group_id_actual = safe_get(
                    managed_app,
                    "managedResourceGroupId",
                    managed_resource_group_id,
                )
                managed_resource_group_name_actual = parse_resource_group_name(
                    managed_resource_group_id_actual
                )
                if not managed_resource_group_name_actual:
                    raise RuntimeError(
                        f"Managed app {app_name} has no managedResourceGroupId after provisioning."
                    )

                container_app = create_container_app_if_missing(
                    subscription_id=subscription_id,
                    managed_resource_group_name=managed_resource_group_name_actual,
                    container_app_name=container_app_name,
                    environment_id=environment_id,
                    image=image,
                    location=location,
                    tags=combined_tags,
                )
                container_app_id = safe_get(container_app, "id")
                latest_fqdn = ""
                if isinstance(container_app.get("properties"), dict):
                    latest_fqdn = safe_get(container_app["properties"], "latestRevisionFqdn")

                subscription_state["targets"].append(
                    {
                        "target_id": target_id,
                        "tenant_id": tenant_id,
                        "managed_app_name": app_name,
                        "managed_app_id": safe_get(managed_app, "id"),
                        "managed_resource_group_id": managed_resource_group_id_actual,
                        "managed_resource_group_name": managed_resource_group_name_actual,
                        "container_app_name": container_app_name,
                        "container_app_id": container_app_id,
                        "container_app_fqdn": latest_fqdn,
                        "tags": combined_tags,
                    }
                )
                created_count += 1

            state["subscriptions"].append(subscription_state)
    finally:
        if original_subscription:
            subprocess.run(
                ["az", "account", "set", "--subscription", original_subscription],
                check=False,
                capture_output=True,
                text=True,
            )

    state_file.parent.mkdir(parents=True, exist_ok=True)
    state_file.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    print(
        "managed-app-sim-up: "
        f"created/ensured {created_count} managed-app targets across {len(state['subscriptions'])} subscription(s); "
        f"state file: {state_file}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
PY

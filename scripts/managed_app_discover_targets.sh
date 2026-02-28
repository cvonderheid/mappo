#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_OUTPUT_FILE="${ROOT_DIR}/.data/mappo-target-inventory.json"

output_file="${DEFAULT_OUTPUT_FILE}"
subscriptions_csv=""
container_app_name=""
default_target_group="prod"
group_tag_key="ring"
managed_app_name_prefix=""
allow_empty="false"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Discover MAPPO targets from Azure Managed Application instances and emit MAPPO inventory JSON.

Options:
  --output-file <path>            Output inventory file path (default: ${DEFAULT_OUTPUT_FILE})
  --subscriptions <csv>           Comma-separated subscription IDs (default: all accessible subscriptions)
  --container-app-name <name>     Preferred Container App name inside each managed resource group
  --default-target-group <name>   Fallback target group tag when missing (default: prod)
  --group-tag-key <key>           Tag key used for target group lookup (default: ring)
  --managed-app-name-prefix <p>   Only include managed apps with names starting with prefix
  --allow-empty                   Exit successfully when 0 targets are discovered
  -h, --help                      Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-file)
      output_file="${2:-}"
      shift 2
      ;;
    --subscriptions)
      subscriptions_csv="${2:-}"
      shift 2
      ;;
    --container-app-name)
      container_app_name="${2:-}"
      shift 2
      ;;
    --default-target-group)
      default_target_group="${2:-}"
      shift 2
      ;;
    --group-tag-key)
      group_tag_key="${2:-}"
      shift 2
      ;;
    --managed-app-name-prefix)
      managed_app_name_prefix="${2:-}"
      shift 2
      ;;
    --allow-empty)
      allow_empty="true"
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "managed-app-discover-targets: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v az >/dev/null 2>&1; then
  echo "managed-app-discover-targets: Azure CLI is required." >&2
  exit 1
fi

if ! az account show --only-show-errors >/dev/null 2>&1; then
  echo "managed-app-discover-targets: no active Azure login. Run 'az login' first." >&2
  exit 1
fi

mkdir -p "$(dirname "${output_file}")"

python3 - <<'PY' \
  "${output_file}" \
  "${subscriptions_csv}" \
  "${container_app_name}" \
  "${default_target_group}" \
  "${group_tag_key}" \
  "${managed_app_name_prefix}" \
  "${allow_empty}"
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


def run_az_json(args: list[str]) -> Any:
    command = ["az", *args, "-o", "json"]
    result = subprocess.run(
        command,
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        stderr = result.stderr.strip()
        raise RuntimeError(
            f"Azure CLI command failed ({' '.join(command)}): {stderr or 'unknown error'}"
        )
    return json.loads(result.stdout or "null")


def as_dict(value: Any) -> dict[str, str]:
    if not isinstance(value, dict):
        return {}
    return {str(key): str(item) for key, item in value.items()}


def normalize_target_id(value: str) -> str:
    normalized = re.sub(r"[^a-zA-Z0-9-]", "-", value).strip("-").lower()
    normalized = re.sub(r"-{2,}", "-", normalized)
    return normalized or "target"


def parse_rg_name(resource_id: str) -> str | None:
    parts = [part for part in resource_id.strip("/").split("/") if part]
    for index, part in enumerate(parts):
        if part.lower() == "resourcegroups" and index + 1 < len(parts):
            return parts[index + 1]
    return None


def prefer_container_app(
    *,
    container_apps: list[dict[str, Any]],
    preferred_name: str,
    managed_app_name: str,
) -> tuple[dict[str, Any], bool]:
    if preferred_name:
        for app in container_apps:
            if str(app.get("name", "")).lower() == preferred_name.lower():
                return app, False

    if len(container_apps) == 1:
        return container_apps[0], False

    for app in container_apps:
        tags = as_dict(app.get("tags"))
        marker = tags.get("mappoTarget", "").strip().lower()
        if marker in {"true", "1", "yes"}:
            return app, False

    managed_prefix = normalize_target_id(managed_app_name)
    for app in container_apps:
        if normalize_target_id(str(app.get("name", ""))).startswith(managed_prefix):
            return app, False

    ordered = sorted(container_apps, key=lambda item: str(item.get("name", "")))
    return ordered[0], True


def resolve_group(
    *,
    group_tag_key: str,
    default_target_group: str,
    managed_app_tags: dict[str, str],
    container_app_tags: dict[str, str],
) -> str:
    alternate_keys = [
        group_tag_key,
        "targetGroup",
        "target_group",
        "ring",
        "mappoTargetGroup",
    ]
    for key in alternate_keys:
        for source in (container_app_tags, managed_app_tags):
            value = source.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
    return default_target_group


def append_unique_target_id(seen: set[str], base: str) -> str:
    if base not in seen:
        seen.add(base)
        return base

    index = 2
    while True:
        candidate = f"{base}-{index}"
        if candidate not in seen:
            seen.add(candidate)
            return candidate
        index += 1


def main() -> int:
    output_file = Path(sys.argv[1]).expanduser().resolve()
    subscriptions_csv = sys.argv[2].strip()
    preferred_container_app_name = sys.argv[3].strip()
    default_target_group = sys.argv[4].strip() or "prod"
    group_tag_key = sys.argv[5].strip() or "ring"
    managed_app_name_prefix = sys.argv[6].strip().lower()
    allow_empty = sys.argv[7].strip().lower() == "true"

    try:
        account_rows = run_az_json(["account", "list", "--all"])
    except RuntimeError as error:
        print(f"managed-app-discover-targets: {error}", file=sys.stderr)
        return 1

    if not isinstance(account_rows, list):
        print("managed-app-discover-targets: unexpected Azure account list payload.", file=sys.stderr)
        return 1

    tenant_by_subscription: dict[str, str] = {}
    for row in account_rows:
        if isinstance(row, dict):
            subscription_id = str(row.get("id", "")).strip()
            tenant_id = str(row.get("tenantId", "")).strip()
            if subscription_id:
                tenant_by_subscription[subscription_id] = tenant_id

    if subscriptions_csv:
        subscription_ids = [item.strip() for item in subscriptions_csv.split(",") if item.strip()]
    else:
        subscription_ids = sorted(tenant_by_subscription.keys())

    if not subscription_ids:
        print(
            "managed-app-discover-targets: no subscriptions selected. "
            "Pass --subscriptions or ensure az account list has visible subscriptions.",
            file=sys.stderr,
        )
        return 1

    targets: list[dict[str, Any]] = []
    seen_target_ids: set[str] = set()
    managed_app_count = 0
    skipped_count = 0

    for subscription_id in subscription_ids:
        try:
            managed_apps = run_az_json(
                [
                    "managedapp",
                    "list",
                    "--subscription",
                    subscription_id,
                ]
            )
        except RuntimeError as error:
            print(
                f"WARN: unable to list managed applications in subscription {subscription_id}: {error}",
                file=sys.stderr,
            )
            continue

        if not isinstance(managed_apps, list):
            continue

        for managed_app in managed_apps:
            if not isinstance(managed_app, dict):
                continue

            managed_app_name = str(managed_app.get("name", "")).strip()
            if not managed_app_name:
                skipped_count += 1
                print("WARN: skipping managed app with empty name.", file=sys.stderr)
                continue

            if managed_app_name_prefix and not managed_app_name.lower().startswith(managed_app_name_prefix):
                continue

            managed_app_count += 1
            managed_app_id = str(managed_app.get("id", "")).strip()
            managed_app_tags = as_dict(managed_app.get("tags"))
            managed_rg_id = str(managed_app.get("managedResourceGroupId", "")).strip()
            if not managed_rg_id and isinstance(managed_app.get("properties"), dict):
                managed_rg_id = str(
                    (managed_app.get("properties") or {}).get("managedResourceGroupId", "")
                ).strip()
            managed_rg_name = parse_rg_name(managed_rg_id) if managed_rg_id else None

            if not managed_rg_name:
                skipped_count += 1
                print(
                    f"WARN: managed app {managed_app_name} has no managedResourceGroupId; skipping.",
                    file=sys.stderr,
                )
                continue

            try:
                container_apps = run_az_json(
                    [
                        "resource",
                        "list",
                        "--subscription",
                        subscription_id,
                        "--resource-group",
                        managed_rg_name,
                        "--resource-type",
                        "Microsoft.App/containerApps",
                    ]
                )
            except RuntimeError as error:
                skipped_count += 1
                print(
                    f"WARN: unable to list Container Apps in {managed_rg_name}: {error}",
                    file=sys.stderr,
                )
                continue

            if not isinstance(container_apps, list) or len(container_apps) == 0:
                skipped_count += 1
                print(
                    f"WARN: no Container Apps found in managed resource group {managed_rg_name}; skipping {managed_app_name}.",
                    file=sys.stderr,
                )
                continue

            container_app, ambiguous = prefer_container_app(
                container_apps=container_apps,
                preferred_name=preferred_container_app_name,
                managed_app_name=managed_app_name,
            )
            if ambiguous:
                print(
                    f"WARN: multiple Container Apps found in {managed_rg_name}; picked {container_app.get('name')}.",
                    file=sys.stderr,
                )

            container_app_id = str(container_app.get("id", "")).strip()
            if not container_app_id:
                skipped_count += 1
                print(
                    f"WARN: selected Container App in {managed_rg_name} has no resource ID; skipping.",
                    file=sys.stderr,
                )
                continue

            container_app_tags = as_dict(container_app.get("tags"))
            target_group = resolve_group(
                group_tag_key=group_tag_key,
                default_target_group=default_target_group,
                managed_app_tags=managed_app_tags,
                container_app_tags=container_app_tags,
            )

            region = (
                str(container_app.get("location", "")).strip()
                or str(managed_app.get("location", "")).strip()
                or "unknown"
            )
            tier = (
                container_app_tags.get("tier")
                or managed_app_tags.get("tier")
                or "standard"
            )
            environment = (
                container_app_tags.get("environment")
                or managed_app_tags.get("environment")
                or "demo"
            )
            tenant_id = (
                tenant_by_subscription.get(subscription_id)
                or str(managed_app.get("tenantId", "")).strip()
                or "unknown-tenant"
            )

            container_props = container_app.get("properties")
            fqdn = ""
            if isinstance(container_props, dict):
                fqdn = str(container_props.get("latestRevisionFqdn", "")).strip()

            target_id_base = normalize_target_id(managed_app_name)
            target_id = append_unique_target_id(seen_target_ids, target_id_base)

            target_row = {
                "id": target_id,
                "tenant_id": tenant_id,
                "subscription_id": subscription_id,
                "managed_app_id": container_app_id,
                "tags": {
                    "ring": target_group,
                    "region": region,
                    "environment": environment,
                    "tier": tier,
                },
                "metadata": {
                    "managed_application_id": managed_app_id,
                    "managed_application_name": managed_app_name,
                    "managed_resource_group_id": managed_rg_id,
                    "managed_resource_group_name": managed_rg_name,
                    "container_app_name": str(container_app.get("name", "")).strip(),
                    "container_app_fqdn": fqdn,
                },
            }
            targets.append(target_row)

    if len(targets) == 0 and not allow_empty:
        print(
            "managed-app-discover-targets: no targets discovered. "
            "Use --allow-empty to bypass this failure.",
            file=sys.stderr,
        )
        if output_file.exists():
            print(
                f"managed-app-discover-targets: preserving existing output file {output_file}",
                file=sys.stderr,
            )
        return 1

    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text(json.dumps(targets, indent=2) + "\n", encoding="utf-8")

    print(
        "managed-app-discover-targets: "
        f"wrote {output_file} with {len(targets)} target(s) "
        f"from {managed_app_count} managed app(s), skipped {skipped_count}."
    )

    return 0


raise SystemExit(main())
PY

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INVENTORY_FILE="${ROOT_DIR}/.data/mappo-target-inventory.json"
FORWARDER_URL="${MAPPO_MARKETPLACE_FORWARDER_URL:-}"
EVENT_TYPE="subscription_purchased"
EVENT_ID_PREFIX="evt-marketplace-webhook"
DRY_RUN="false"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Replay target inventory through the deployed Function App webhook endpoint.
This exercises: Function App webhook -> MAPPO onboarding ingest endpoint.

Options:
  --forwarder-url <url>      Full function webhook URL (required; include ?code=... when enabled)
  --inventory-file <path>    Target inventory JSON (default: .data/mappo-target-inventory.json)
  --event-type <value>       Marketplace-style event type (default: subscription_purchased)
  --event-id-prefix <value>  Event ID prefix (default: evt-marketplace-webhook)
  --dry-run                  Print payloads only
  -h, --help                 Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --forwarder-url)
      FORWARDER_URL="${2:-}"
      shift 2
      ;;
    --inventory-file)
      INVENTORY_FILE="${2:-}"
      shift 2
      ;;
    --event-type)
      EVENT_TYPE="${2:-}"
      shift 2
      ;;
    --event-id-prefix)
      EVENT_ID_PREFIX="${2:-}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="true"
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "marketplace-forwarder-replay: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${FORWARDER_URL}" ]]; then
  echo "marketplace-forwarder-replay: --forwarder-url is required." >&2
  exit 1
fi

if [[ ! -f "${INVENTORY_FILE}" ]]; then
  echo "marketplace-forwarder-replay: inventory file not found: ${INVENTORY_FILE}" >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "marketplace-forwarder-replay: python3 is required." >&2
  exit 1
fi

python3 - <<'PY' "${INVENTORY_FILE}" "${FORWARDER_URL}" "${EVENT_TYPE}" "${EVENT_ID_PREFIX}" "${DRY_RUN}"
import json
import ssl
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

inventory_path = Path(sys.argv[1]).expanduser().resolve()
forwarder_url = sys.argv[2].strip()
event_type = sys.argv[3].strip() or "subscription_purchased"
event_id_prefix = sys.argv[4].strip() or "evt-marketplace-webhook"
dry_run = sys.argv[5].strip().lower() == "true"

with inventory_path.open("r", encoding="utf-8") as handle:
    rows = json.load(handle)

if not isinstance(rows, list):
    raise SystemExit("marketplace-forwarder-replay: inventory JSON must be an array")

applied = 0
failed = 0

for index, row in enumerate(rows, start=1):
    if not isinstance(row, dict):
        continue
    tenant_id = str(row.get("tenant_id", "")).strip()
    subscription_id = str(row.get("subscription_id", "")).strip()
    container_app_id = str(row.get("managed_app_id", "")).strip()
    metadata = row.get("metadata") if isinstance(row.get("metadata"), dict) else {}
    tags = row.get("tags") if isinstance(row.get("tags"), dict) else {}

    if tenant_id == "" or subscription_id == "" or container_app_id == "":
        continue

    target_id = str(row.get("id", f"target-{index:03d}")).strip() or f"target-{index:03d}"
    payload = {
        "id": f"{event_id_prefix}-{index:03d}-{int(time.time())}",
        "event_type": event_type,
        "action": event_type,
        "mappo_target": {
            "tenant_id": tenant_id,
            "subscription_id": subscription_id,
            "container_app_resource_id": container_app_id,
            "managed_application_id": metadata.get("managed_application_id"),
            "managed_resource_group_id": metadata.get("managed_resource_group_id"),
            "container_app_name": metadata.get("container_app_name"),
            "target_group": tags.get("ring", "prod"),
            "region": tags.get("region", "eastus"),
            "environment": tags.get("environment", "prod"),
            "tier": tags.get("tier", "standard"),
            "display_name": target_id,
            "tags": tags,
            "metadata": {
                "source": "marketplace-forwarder-replay",
                "inventory_target_id": target_id,
            },
        },
    }

    if dry_run:
        print(json.dumps(payload, indent=2))
        applied += 1
        continue

    request = urllib.request.Request(
        forwarder_url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(
            request,
            timeout=20,
            context=ssl.create_default_context(),
        ) as response:
            body = response.read().decode("utf-8", errors="replace")
            print(f"{target_id}: HTTP {response.status} :: {body}")
            if 200 <= response.status < 300:
                applied += 1
            else:
                failed += 1
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        print(f"{target_id}: HTTP {error.code} :: {body}", file=sys.stderr)
        failed += 1
    except urllib.error.URLError as error:
        print(f"{target_id}: request failed :: {error.reason}", file=sys.stderr)
        failed += 1

print(
    f"marketplace-forwarder-replay: applied={applied} failed={failed} dry_run={str(dry_run).lower()}"
)

if failed > 0:
    raise SystemExit(1)
PY

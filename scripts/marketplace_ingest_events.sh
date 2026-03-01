#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INVENTORY_FILE="${ROOT_DIR}/.data/mappo-target-inventory.json"
API_BASE_URL="${MAPPO_API_BASE_URL:-http://localhost:8010}"
EVENT_TYPE="subscription_purchased"
INGEST_TOKEN="${MAPPO_MARKETPLACE_INGEST_TOKEN:-}"
EVENT_ID_PREFIX="evt-marketplace-demo"
SOURCE_LABEL="marketplace-webhook-simulator"
DRY_RUN=false

usage() {
  cat <<'EOF'
usage: marketplace_ingest_events.sh [options]

Reads target inventory and submits onboarding events to:
  POST /api/v1/admin/onboarding/events

This simulates marketplace lifecycle forwarding for demo/testing while using
the same ingestion API path as production.

Options:
  --inventory-file <path>     Inventory JSON path (default: .data/mappo-target-inventory.json)
  --api-base-url <url>        MAPPO API base URL (default: http://localhost:8010)
  --event-type <name>         Event type value (default: subscription_purchased)
  --ingest-token <token>      Optional x-mappo-ingest-token header
  --event-id-prefix <prefix>  Event ID prefix (default: evt-marketplace-demo)
  --source-label <label>      Metadata source label (default: marketplace-webhook-simulator)
  --dry-run                   Print payloads only (no API calls)
  -h, --help                  Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --inventory-file)
      INVENTORY_FILE="${2:-}"
      shift 2
      ;;
    --api-base-url)
      API_BASE_URL="${2:-}"
      shift 2
      ;;
    --event-type)
      EVENT_TYPE="${2:-}"
      shift 2
      ;;
    --ingest-token)
      INGEST_TOKEN="${2:-}"
      shift 2
      ;;
    --event-id-prefix)
      EVENT_ID_PREFIX="${2:-}"
      shift 2
      ;;
    --source-label)
      SOURCE_LABEL="${2:-}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "marketplace-ingest-events: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -f "${INVENTORY_FILE}" ]]; then
  echo "marketplace-ingest-events: inventory file not found: ${INVENTORY_FILE}" >&2
  exit 2
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "marketplace-ingest-events: python3 is required." >&2
  exit 2
fi

python3 - <<'PY' \
  "${INVENTORY_FILE}" \
  "${API_BASE_URL}" \
  "${EVENT_TYPE}" \
  "${INGEST_TOKEN}" \
  "${EVENT_ID_PREFIX}" \
  "${SOURCE_LABEL}" \
  "${DRY_RUN}"
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from typing import Any

inventory_path = Path(sys.argv[1])
api_base_url = sys.argv[2].rstrip("/")
event_type = sys.argv[3].strip() or "subscription_purchased"
ingest_token = sys.argv[4].strip()
event_id_prefix = sys.argv[5].strip() or "evt-marketplace-demo"
source_label = sys.argv[6].strip() or "marketplace-webhook-simulator"
dry_run = sys.argv[7].strip().lower() == "true"

rows = json.loads(inventory_path.read_text(encoding="utf-8"))
if not isinstance(rows, list):
    raise SystemExit("marketplace-ingest-events: inventory JSON must be an array")

endpoint = f"{api_base_url}/api/v1/admin/onboarding/events"
batch_id = uuid.uuid4().hex[:10]

applied = 0
duplicate = 0
rejected = 0
request_failed = 0
validation_failed = 0

def _as_str(value: Any) -> str:
    return str(value).strip() if value is not None else ""

for index, row in enumerate(rows, start=1):
    if not isinstance(row, dict):
        print(f"marketplace-ingest-events: skipping non-object row #{index}", file=sys.stderr)
        validation_failed += 1
        continue

    target_id = _as_str(row.get("id"))
    tenant_id = _as_str(row.get("tenant_id"))
    subscription_id = _as_str(row.get("subscription_id"))
    container_app_resource_id = _as_str(row.get("managed_app_id"))
    tags = row.get("tags")
    metadata = row.get("metadata")
    if not isinstance(tags, dict):
        tags = {}
    if not isinstance(metadata, dict):
        metadata = {}

    missing: list[str] = []
    if target_id == "":
        missing.append("id")
    if tenant_id == "":
        missing.append("tenant_id")
    if subscription_id == "":
        missing.append("subscription_id")
    if container_app_resource_id == "":
        missing.append("managed_app_id")
    if missing:
        print(
            f"marketplace-ingest-events: skipping target row #{index} due to missing fields: {', '.join(missing)}",
            file=sys.stderr,
        )
        validation_failed += 1
        continue

    event_id = f"{event_id_prefix}-{batch_id}-{index:03d}"
    managed_application_id = _as_str(metadata.get("managed_application_id"))
    managed_resource_group_id = _as_str(metadata.get("managed_resource_group_id"))
    container_app_name = _as_str(metadata.get("container_app_name"))
    customer_name = _as_str(tags.get("customer")) or _as_str(metadata.get("customer_name"))
    display_name = _as_str(metadata.get("managed_application_name")) or target_id
    target_group = _as_str(tags.get("ring")) or "prod"
    environment = _as_str(tags.get("environment")) or "prod"
    tier = _as_str(tags.get("tier")) or "standard"
    region = _as_str(tags.get("region")) or None

    payload: dict[str, Any] = {
        "event_id": event_id,
        "event_type": event_type,
        "tenant_id": tenant_id,
        "subscription_id": subscription_id,
        "container_app_resource_id": container_app_resource_id,
        "target_id": target_id,
        "display_name": display_name,
        "target_group": target_group,
        "environment": environment,
        "tier": tier,
        "tags": tags,
        "metadata": {
            "source": source_label,
            "inventory_target_id": target_id,
            "inventory_managed_application_id": managed_application_id,
            "inventory_managed_resource_group_id": managed_resource_group_id,
        },
        "health_status": "registered",
        "last_deployed_release": "unknown",
    }
    if managed_application_id:
        payload["managed_application_id"] = managed_application_id
    if managed_resource_group_id:
        payload["managed_resource_group_id"] = managed_resource_group_id
    if container_app_name:
        payload["container_app_name"] = container_app_name
    if customer_name:
        payload["customer_name"] = customer_name
    if region:
        payload["region"] = region

    if dry_run:
        print(json.dumps(payload, separators=(",", ":"), sort_keys=True))
        continue

    headers = {"Content-Type": "application/json"}
    if ingest_token:
        headers["x-mappo-ingest-token"] = ingest_token

    request = urllib.request.Request(
        endpoint,
        method="POST",
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            body = response.read().decode("utf-8")
            response_payload = json.loads(body) if body else {}
            status_value = _as_str(response_payload.get("status")).lower()
            message = _as_str(response_payload.get("message")) or "(no message)"
            if status_value == "applied":
                applied += 1
            elif status_value == "duplicate":
                duplicate += 1
            elif status_value == "rejected":
                rejected += 1
            else:
                request_failed += 1
                print(
                    f"marketplace-ingest-events: unexpected response status for {target_id}: {status_value or 'unknown'}",
                    file=sys.stderr,
                )
            print(
                f"{target_id}: {status_value or 'unknown'} :: {message}"
            )
    except urllib.error.HTTPError as error:
        request_failed += 1
        body = error.read().decode("utf-8", errors="replace")
        print(
            f"marketplace-ingest-events: HTTP {error.code} for target {target_id}: {body}",
            file=sys.stderr,
        )
    except urllib.error.URLError as error:
        request_failed += 1
        print(
            f"marketplace-ingest-events: request failed for target {target_id}: {error.reason}",
            file=sys.stderr,
        )

total_rows = len(rows)
print(
    "marketplace-ingest-events: "
    f"rows={total_rows} applied={applied} duplicate={duplicate} rejected={rejected} "
    f"validation_failed={validation_failed} request_failed={request_failed} "
    f"dry_run={'true' if dry_run else 'false'}"
)

if validation_failed > 0 or request_failed > 0:
    raise SystemExit(1)
PY

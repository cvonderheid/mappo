#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_BASE_URL="${MAPPO_API_BASE_URL:-http://localhost:8010}"
API_BEARER_TOKEN="${MAPPO_API_BEARER_TOKEN:-}"

MANIFEST_FILE=""
MANIFEST_URL=""

GITHUB_REPO=""
GITHUB_PATH=""
GITHUB_REF="main"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"

ADO_ORG=""
ADO_PROJECT=""
ADO_REPOSITORY=""
ADO_PATH=""
ADO_REF="main"
ADO_TOKEN="${AZURE_DEVOPS_EXT_PAT:-}"

ALLOW_DUPLICATES=false
DRY_RUN=false

usage() {
  cat <<'EOF'
usage: release_ingest_from_repo.sh [options]

Registers releases into MAPPO via:
  GET  /api/v1/releases
  POST /api/v1/releases

Exactly one release source is required:
  --manifest-file <path>               Local JSON file
  --manifest-url <url>                 Direct URL to JSON file
  --github-repo <owner/repo> --github-path <path> [--github-ref <ref>]
  --ado-org <org> --ado-project <project> --ado-repository <repo> --ado-path <path> [--ado-ref <branch>]

Manifest JSON can be either:
  1) array of release objects
  2) object with a "releases" array

Release object fields (required):
  - template_spec_id
  - template_spec_version

Optional fields:
  - deployment_mode (default: template_spec)
  - template_spec_version_id
  - deployment_scope (default: resource_group)
  - deployment_mode_settings
  - parameter_defaults
  - release_notes
  - verification_hints

Options:
  --api-base-url <url>                 MAPPO API base URL (default: http://localhost:8010)
  --api-bearer-token <token>           Optional bearer token for API auth
  --github-token <token>               Optional GitHub token (or GITHUB_TOKEN env)
  --ado-token <token>                  Azure DevOps PAT (or AZURE_DEVOPS_EXT_PAT env)
  --allow-duplicates                   Create even when same template_spec_id+version exists
  --dry-run                            Validate/print actions without API writes
  -h, --help                           Show help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api-base-url)
      API_BASE_URL="${2:-}"
      shift 2
      ;;
    --api-bearer-token)
      API_BEARER_TOKEN="${2:-}"
      shift 2
      ;;
    --manifest-file)
      MANIFEST_FILE="${2:-}"
      shift 2
      ;;
    --manifest-url)
      MANIFEST_URL="${2:-}"
      shift 2
      ;;
    --github-repo)
      GITHUB_REPO="${2:-}"
      shift 2
      ;;
    --github-path)
      GITHUB_PATH="${2:-}"
      shift 2
      ;;
    --github-ref)
      GITHUB_REF="${2:-}"
      shift 2
      ;;
    --github-token)
      GITHUB_TOKEN="${2:-}"
      shift 2
      ;;
    --ado-org)
      ADO_ORG="${2:-}"
      shift 2
      ;;
    --ado-project)
      ADO_PROJECT="${2:-}"
      shift 2
      ;;
    --ado-repository)
      ADO_REPOSITORY="${2:-}"
      shift 2
      ;;
    --ado-path)
      ADO_PATH="${2:-}"
      shift 2
      ;;
    --ado-ref)
      ADO_REF="${2:-}"
      shift 2
      ;;
    --ado-token)
      ADO_TOKEN="${2:-}"
      shift 2
      ;;
    --allow-duplicates)
      ALLOW_DUPLICATES=true
      shift 1
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
      echo "release-ingest-from-repo: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v python3 >/dev/null 2>&1; then
  echo "release-ingest-from-repo: python3 is required." >&2
  exit 2
fi

python3 - <<'PY' \
  "${ROOT_DIR}" \
  "${API_BASE_URL}" \
  "${API_BEARER_TOKEN}" \
  "${MANIFEST_FILE}" \
  "${MANIFEST_URL}" \
  "${GITHUB_REPO}" \
  "${GITHUB_PATH}" \
  "${GITHUB_REF}" \
  "${GITHUB_TOKEN}" \
  "${ADO_ORG}" \
  "${ADO_PROJECT}" \
  "${ADO_REPOSITORY}" \
  "${ADO_PATH}" \
  "${ADO_REF}" \
  "${ADO_TOKEN}" \
  "${ALLOW_DUPLICATES}" \
  "${DRY_RUN}"
from __future__ import annotations

import base64
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


root_dir = Path(sys.argv[1])
api_base_url = sys.argv[2].strip().rstrip("/")
api_bearer_token = sys.argv[3].strip()

manifest_file = sys.argv[4].strip()
manifest_url = sys.argv[5].strip()

github_repo = sys.argv[6].strip()
github_path = sys.argv[7].strip()
github_ref = sys.argv[8].strip() or "main"
github_token = sys.argv[9].strip()

ado_org = sys.argv[10].strip()
ado_project = sys.argv[11].strip()
ado_repository = sys.argv[12].strip()
ado_path = sys.argv[13].strip()
ado_ref = sys.argv[14].strip() or "main"
ado_token = sys.argv[15].strip()

allow_duplicates = sys.argv[16].strip().lower() == "true"
dry_run = sys.argv[17].strip().lower() == "true"


def fail(message: str, *, code: int = 2) -> None:
    raise SystemExit(f"release-ingest-from-repo: {message}")


def parse_manifest(raw_payload: str) -> list[dict[str, Any]]:
    try:
        parsed = json.loads(raw_payload)
    except json.JSONDecodeError as error:
        fail(f"manifest is not valid JSON: {error}")

    releases_payload: Any
    if isinstance(parsed, list):
        releases_payload = parsed
    elif isinstance(parsed, dict):
        releases_payload = parsed.get("releases")
        if releases_payload is None:
            fail("manifest object must include a 'releases' array")
    else:
        fail("manifest must be a JSON array or an object with a 'releases' array")

    if not isinstance(releases_payload, list):
        fail("'releases' must be an array")

    normalized: list[dict[str, Any]] = []
    for index, item in enumerate(releases_payload, start=1):
        if not isinstance(item, dict):
            fail(f"release row #{index} is not an object")

        template_spec_id = str(item.get("template_spec_id") or "").strip()
        template_spec_version = str(item.get("template_spec_version") or "").strip()
        if template_spec_id == "" or template_spec_version == "":
            fail(
                f"release row #{index} missing required fields: "
                "template_spec_id and template_spec_version"
            )

        deployment_mode = str(item.get("deployment_mode") or "template_spec").strip()
        deployment_scope = str(item.get("deployment_scope") or "resource_group").strip()
        template_spec_version_id = item.get("template_spec_version_id")
        deployment_mode_settings = item.get("deployment_mode_settings") or {}
        parameter_defaults = item.get("parameter_defaults") or {}
        verification_hints = item.get("verification_hints") or []

        if not isinstance(deployment_mode_settings, dict):
            fail(f"release row #{index} has non-object deployment_mode_settings")
        if not isinstance(parameter_defaults, dict):
            fail(f"release row #{index} has non-object parameter_defaults")
        if not isinstance(verification_hints, list):
            fail(f"release row #{index} has non-array verification_hints")

        normalized.append(
            {
                "template_spec_id": template_spec_id,
                "template_spec_version": template_spec_version,
                "deployment_mode": deployment_mode,
                "template_spec_version_id": (
                    str(template_spec_version_id).strip() if template_spec_version_id else None
                ),
                "deployment_scope": deployment_scope,
                "deployment_mode_settings": deployment_mode_settings,
                "parameter_defaults": {
                    str(key): str(value)
                    for key, value in parameter_defaults.items()
                    if str(key).strip() != ""
                },
                "release_notes": str(item.get("release_notes") or "").strip(),
                "verification_hints": [
                    str(hint).strip()
                    for hint in verification_hints
                    if str(hint).strip() != ""
                ],
            }
        )
    return normalized


def load_manifest_payload() -> str:
    source_count = 0
    if manifest_file:
        source_count += 1
    if manifest_url:
        source_count += 1
    if github_repo or github_path:
        if github_repo == "" or github_path == "":
            fail("github source requires both --github-repo and --github-path")
        source_count += 1
    if ado_org or ado_project or ado_repository or ado_path:
        required = [
            ("--ado-org", ado_org),
            ("--ado-project", ado_project),
            ("--ado-repository", ado_repository),
            ("--ado-path", ado_path),
        ]
        missing = [name for name, value in required if value == ""]
        if missing:
            fail(f"ado source missing required options: {', '.join(missing)}")
        source_count += 1

    if source_count != 1:
        fail(
            "exactly one source is required: --manifest-file, --manifest-url, "
            "GitHub source, or Azure DevOps source"
        )

    if manifest_file:
        manifest_path = Path(manifest_file)
        if not manifest_path.is_absolute():
            manifest_path = root_dir / manifest_path
        if not manifest_path.exists():
            fail(f"manifest file not found: {manifest_path}")
        return manifest_path.read_text(encoding="utf-8")

    if github_repo:
        safe_path = github_path.lstrip("/")
        url = f"https://raw.githubusercontent.com/{github_repo}/{github_ref}/{safe_path}"
        headers = {}
        if github_token:
            headers["Authorization"] = f"Bearer {github_token}"
        request = urllib.request.Request(url, headers=headers, method="GET")
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            fail(f"GitHub manifest fetch failed (HTTP {error.code}): {body}")
        except urllib.error.URLError as error:
            fail(f"GitHub manifest fetch failed: {error.reason}")

    if ado_org:
        query = (
            f"path={urllib.parse.quote(ado_path, safe='/')}"
            f"&versionDescriptor.versionType=branch"
            f"&versionDescriptor.version={urllib.parse.quote(ado_ref, safe='')}"
            f"&download=true"
            f"&api-version=7.1"
        )
        url = (
            f"https://dev.azure.com/{ado_org}/{ado_project}/_apis/git/repositories/"
            f"{ado_repository}/items?{query}"
        )
        headers = {"Accept": "application/json"}
        if ado_token:
            token_value = base64.b64encode(f":{ado_token}".encode("utf-8")).decode("ascii")
            headers["Authorization"] = f"Basic {token_value}"
        request = urllib.request.Request(url, headers=headers, method="GET")
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            fail(f"ADO manifest fetch failed (HTTP {error.code}): {body}")
        except urllib.error.URLError as error:
            fail(f"ADO manifest fetch failed: {error.reason}")

    request = urllib.request.Request(manifest_url, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        fail(f"manifest URL fetch failed (HTTP {error.code}): {body}")
    except urllib.error.URLError as error:
        fail(f"manifest URL fetch failed: {error.reason}")


def api_request(
    *,
    path: str,
    method: str,
    payload: dict[str, Any] | None = None,
) -> Any:
    url = f"{api_base_url}{path}"
    headers = {"Accept": "application/json"}
    data: bytes | None = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode("utf-8")
    if api_bearer_token:
        headers["Authorization"] = f"Bearer {api_bearer_token}"
    request = urllib.request.Request(url=url, method=method, headers=headers, data=data)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read().decode("utf-8")
            if body.strip() == "":
                return None
            try:
                return json.loads(body)
            except json.JSONDecodeError:
                return body
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        fail(f"API {method} {path} failed (HTTP {error.code}): {body}")
    except urllib.error.URLError as error:
        fail(f"API {method} {path} failed: {error.reason}")


manifest_raw = load_manifest_payload()
release_requests = parse_manifest(manifest_raw)

existing_keys: set[tuple[str, str]] = set()
if not dry_run:
    existing_payload = api_request(path="/api/v1/releases", method="GET")
    if not isinstance(existing_payload, list):
        fail("GET /api/v1/releases returned unexpected payload")
    for item in existing_payload:
        if isinstance(item, dict):
            key = (
                str(item.get("template_spec_id") or "").strip(),
                str(item.get("template_spec_version") or "").strip(),
            )
            if key[0] and key[1]:
                existing_keys.add(key)

created = 0
skipped = 0
failed = 0

for index, payload in enumerate(release_requests, start=1):
    key = (payload["template_spec_id"], payload["template_spec_version"])
    if (not allow_duplicates) and key in existing_keys:
        skipped += 1
        print(
            "release-ingest-from-repo: skipped existing "
            f"{payload['template_spec_version']} ({payload['template_spec_id']})"
        )
        continue

    if dry_run:
        created += 1
        print(
            "release-ingest-from-repo: dry-run create "
            f"#{index} {payload['template_spec_version']} ({payload['template_spec_id']})"
        )
        existing_keys.add(key)
        continue

    try:
        response_payload = api_request(path="/api/v1/releases", method="POST", payload=payload)
        release_id = ""
        if isinstance(response_payload, dict):
            release_id = str(response_payload.get("id") or "").strip()
        created += 1
        existing_keys.add(key)
        print(
            "release-ingest-from-repo: created "
            f"{release_id or '(unknown-id)'} :: {payload['template_spec_version']}"
        )
    except SystemExit as error:
        failed += 1
        print(str(error), file=sys.stderr)

summary = (
    "release-ingest-from-repo: "
    f"manifest_releases={len(release_requests)} created={created} "
    f"skipped={skipped} failed={failed} dry_run={'true' if dry_run else 'false'}"
)
print(summary)

if failed > 0:
    raise SystemExit(1)
PY

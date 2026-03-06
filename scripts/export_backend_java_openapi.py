#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fetch Spring Boot OpenAPI JSON and write it to a deterministic artifact path."
    )
    parser.add_argument("--url", required=True, help="OpenAPI endpoint URL")
    parser.add_argument("--output", required=True, help="Output file path")
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=60,
        help="Maximum time to wait for the app to become ready",
    )
    parser.add_argument(
        "--poll-interval-seconds",
        type=float,
        default=1.0,
        help="Polling interval while waiting for the endpoint",
    )
    return parser.parse_args()


def fetch_openapi(url: str, timeout_seconds: int, poll_interval_seconds: float) -> dict[str, object]:
    deadline = time.monotonic() + timeout_seconds
    last_error: str | None = None

    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=10) as response:
                payload = json.loads(response.read().decode("utf-8"))
            if isinstance(payload, dict) and payload.get("openapi"):
                return payload
            last_error = "endpoint responded but did not return an OpenAPI document"
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            last_error = str(exc)

        time.sleep(poll_interval_seconds)

    raise RuntimeError(
        f"timed out waiting for OpenAPI endpoint {url!r}. Last error: {last_error or 'unknown'}"
    )


def main() -> int:
    args = parse_args()
    payload = fetch_openapi(args.url, args.timeout_seconds, args.poll_interval_seconds)

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"export-backend-java-openapi: wrote {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

from __future__ import annotations

import json
from pathlib import Path

from app.main import app


def main() -> None:
    output = Path("openapi/openapi.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(app.openapi(), indent=2, sort_keys=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()

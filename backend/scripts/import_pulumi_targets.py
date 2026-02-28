from __future__ import annotations

import asyncio
import warnings

from import_targets import main

if __name__ == "__main__":
    message = (
        "backend/scripts/import_pulumi_targets.py is deprecated; "
        "use backend/scripts/import_targets.py"
    )
    warnings.warn(
        message,
        DeprecationWarning,
        stacklevel=1,
    )
    asyncio.run(main())

from __future__ import annotations

import sys
from pathlib import Path


SRC = Path(__file__).resolve().parent / "src"

if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))


def main() -> int:
    try:
        from table_ocr.cli import main as cli_main
    except ModuleNotFoundError as exc:
        if exc.name not in {"table_ocr", "table_ocr.cli"}:
            raise
        raise SystemExit(
            "table_ocr.cli is not available yet. The CLI scaffold will be added in Task 2."
        ) from exc

    return cli_main()


if __name__ == "__main__":
    raise SystemExit(main())

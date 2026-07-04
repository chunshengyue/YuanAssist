from __future__ import annotations

import csv
from pathlib import Path

from .models import RowResult


CSV_HEADER = ["回合", "列1", "列2", "列3", "列4", "列5"]


def write_csv(output_path: str, rows: list[RowResult]) -> None:
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(CSV_HEADER)
        for row in rows:
            writer.writerow([row.round_label, *row.actions])

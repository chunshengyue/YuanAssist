from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True)
class PipelineConfig:
    input_path: str
    output_path: str
    tesseract_cmd: str | None
    debug_dir: str | None
    enable_table_region_extraction: bool = True
    enable_blur: bool = True
    enable_adaptive_threshold: bool = True
    enable_multiline_split: bool = True
    profile_name: str = "full"


@dataclass(slots=True)
class TableStructure:
    row_lines: list[int]
    col_lines: list[int]
    width: int = 0
    height: int = 0


@dataclass(slots=True)
class CellBox:
    row: int
    col: int
    x: int
    y: int
    w: int
    h: int


@dataclass(slots=True)
class LineSegment:
    top: int
    bottom: int
    height: int
    pixel_density: float
    is_noise: bool = False


@dataclass(slots=True)
class RowResult:
    round_label: str
    actions: list[str]

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Sequence

from .models import PipelineConfig


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="table-ocr")
    parser.add_argument("--input", required=True, help="Path to the source image.")
    parser.add_argument("--output", required=True, help="Path to the output CSV file.")
    parser.add_argument(
        "--tesseract-cmd",
        default=None,
        help=argparse.SUPPRESS,
    )
    parser.add_argument(
        "--debug-dir",
        default=None,
        help="Optional directory for intermediate debug artifacts.",
    )
    return parser


def build_config(args: argparse.Namespace) -> PipelineConfig:
    input_path = Path(args.input)
    if not input_path.is_file():
        raise FileNotFoundError(f"Input file does not exist: {input_path}")

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    debug_dir = None
    if args.debug_dir is not None:
        debug_path = Path(args.debug_dir)
        debug_path.mkdir(parents=True, exist_ok=True)
        debug_dir = str(debug_path)

    return PipelineConfig(
        input_path=str(input_path),
        output_path=str(output_path),
        tesseract_cmd=args.tesseract_cmd,
        debug_dir=debug_dir,
    )


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    config = build_config(args)

    try:
        from .pipeline import run_pipeline
    except ModuleNotFoundError as exc:
        if exc.name not in {"table_ocr.pipeline", "table_ocr"}:
            raise
        raise SystemExit(
            "table_ocr.pipeline is not available yet. The runnable pipeline will be added in Task 7."
        ) from exc

    run_pipeline(config)
    return 0

from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path
from typing import Mapping

import cv2

from .action_parser import ActionSequenceParser
from .exporter import write_csv
from .grid import build_cell_boxes, detect_table_structure
from .layout import CellLayoutAnalyzer
from .models import CellBox, PipelineConfig, RowResult, TableStructure
from .ocr import configure_tesseract, crop_cell, ocr_cells
from .postprocess import normalize_action_text, normalize_round_text
from .preprocess import build_binary_stages, extract_table_region, load_image


def group_text_rows(
    boxes: list[CellBox],
    text_by_cell: Mapping[tuple[int, int], str],
) -> list[RowResult]:
    cols_by_row: dict[int, set[int]] = defaultdict(set)
    for cell in boxes:
        cols_by_row[cell.row].add(cell.col)

    rows: list[RowResult] = []
    for row_index in sorted(cols_by_row):
        round_label = text_by_cell.get((row_index, 0), "").strip()
        actions = [text_by_cell.get((row_index, col), "").strip() for col in range(1, 6)]
        if not round_label and not any(actions):
            continue
        if not round_label:
            round_label = f"{row_index + 1}回合"
        rows.append(RowResult(round_label=round_label, actions=actions))
    return rows


def _draw_grid_overlay(
    table_image,
    boxes: list[CellBox],
) -> object:
    overlay = table_image.copy()
    if overlay.ndim == 2:
        overlay = cv2.cvtColor(overlay, cv2.COLOR_GRAY2BGR)
    for cell in boxes:
        color = (32, 96, 208) if cell.col == 0 else (48, 160, 64)
        cv2.rectangle(
            overlay,
            (cell.x, cell.y),
            (cell.x + cell.w, cell.y + cell.h),
            color,
            2,
        )
        cv2.putText(
            overlay,
            f"{cell.row},{cell.col}",
            (cell.x + 4, cell.y + 18),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.45,
            color,
            1,
            cv2.LINE_AA,
        )
    return overlay


def _build_step_notes(config: PipelineConfig) -> list[dict[str, object]]:
    notes = [
        {
            "key": "table_region",
            "title": "1. 原图与表格定位",
            "description": (
                "先在整张截图上做一次二值化和轮廓合并，圈出表格主区域，"
                "再裁成后续处理使用的纯表格图。"
            ),
        },
        {
            "key": "preprocess",
            "title": "2. 表格预处理",
            "description": (
                "只对裁出的表格做预处理，顺序是灰度图 -> 阈值输入图 -> 二值图，"
                "用于增强线条和文字对比度。"
            ),
        },
        {
            "key": "grid",
            "title": "3. 表格分割",
            "description": "根据横线和竖线恢复表格网格，并在纯表格图上标出切格结果。",
        },
        {
            "key": "ocr",
            "title": "4. 单元格 OCR",
            "description": "对切出的单元格逐个做 OCR，保留原始文本和规范化结果，方便逐格对照。",
        },
        {
            "key": "correction",
            "title": "5. 动作纠错与组件补救",
            "description": (
                "动作格会结合 OCR 文本、动作 token 解析、字符修复和组件级判形，"
                "把数字、A、上下箭头和圈整理成 App 端可消费的动作串。"
            ),
        },
    ]
    if config.enable_multiline_split:
        notes.append(
            {
                "key": "multiline",
                "title": "6. 多行与复杂格处理",
                "description": "遇到一格多行或结构复杂的内容时，再额外做分行和拼接处理。",
            }
        )
    else:
        notes.append(
            {
                "key": "multiline",
                "title": "6. 多行处理已关闭",
                "description": "这组对比实验不会拆多行单元格，直接用整格 OCR 结果参与后续处理。",
            }
        )
    return notes


def _write_debug_artifacts(
    debug_dir: str,
    config: PipelineConfig,
    source_debug: Mapping[str, object],
    table_image,
    table_rect: tuple[int, int, int, int],
    table_stages: Mapping[str, object],
    structure: TableStructure,
    boxes: list[CellBox],
    raw_text_by_cell: Mapping[tuple[int, int], str],
    normalized_text_by_cell: Mapping[tuple[int, int], str],
) -> None:
    debug_path = Path(debug_dir)
    debug_path.mkdir(parents=True, exist_ok=True)
    source_overlay = source_debug.get("source_region_overlay")
    source_binary = source_debug.get("source_binary")
    source_merged = source_debug.get("source_merged")
    if source_overlay is not None:
        cv2.imwrite(str(debug_path / "source_region_overlay.png"), source_overlay)
    if source_binary is not None:
        cv2.imwrite(str(debug_path / "source_binary.png"), source_binary)
    if source_merged is not None:
        cv2.imwrite(str(debug_path / "source_merged.png"), source_merged)
    cv2.imwrite(str(debug_path / "table_region.png"), table_image)
    if table_stages.get("gray") is not None:
        cv2.imwrite(str(debug_path / "gray.png"), table_stages["gray"])
    if table_stages.get("threshold_input") is not None:
        cv2.imwrite(str(debug_path / "threshold_input.png"), table_stages["threshold_input"])
    if table_stages.get("binary") is not None:
        cv2.imwrite(str(debug_path / "binary.png"), table_stages["binary"])
    cv2.imwrite(str(debug_path / "grid_overlay.png"), _draw_grid_overlay(table_image, boxes))
    with (debug_path / "cells.txt").open("w", encoding="utf-8") as handle:
        for cell in boxes:
            handle.write(f"{cell.row},{cell.col},{cell.x},{cell.y},{cell.w},{cell.h}\n")

    analyzer = CellLayoutAnalyzer()
    parser = ActionSequenceParser()
    complex_log_lines: list[str] = []
    multiline_entries: list[dict[str, object]] = []
    warning_entries: list[dict[str, object]] = []
    correction_entries: list[dict[str, object]] = []
    complex_dir = debug_path / "complex_cells"
    complex_dir.mkdir(exist_ok=True)
    cell_records: list[dict[str, object]] = []

    for cell in boxes:
        key = (cell.row, cell.col)
        raw_text = raw_text_by_cell.get(key, "")
        normalized = normalized_text_by_cell.get(key, "")
        cell_records.append(
            {
                "row": cell.row,
                "col": cell.col,
                "x": cell.x,
                "y": cell.y,
                "w": cell.w,
                "h": cell.h,
                "raw_text": raw_text,
                "normalized_text": normalized,
            }
        )
        if cell.col == 0:
            continue
        crop = crop_cell(table_image, cell, padding=0)
        segments = analyzer.analyze(crop)
        valid = [seg for seg in segments if not seg.is_noise]
        parse_result = parser.parse(raw_text) if raw_text else None

        correction_entries.append(
            {
                "row": cell.row,
                "col": cell.col,
                "raw_text": raw_text,
                "normalized_text": normalized,
                "confidence": parse_result.confidence.value if parse_result else "none",
                "fragment": parse_result.fragment if parse_result else "",
                "was_fixed": parse_result.was_fixed if parse_result else False,
                "is_complete": parse_result.is_complete if parse_result else False,
                "segment_count": len(valid),
                "note": (
                    "multiline_or_layout_complex"
                    if len(valid) >= 2
                    else "parser_low_confidence"
                    if parse_result and parse_result.confidence.value == "low"
                    else "corrected_or_normalized"
                    if raw_text != normalized or (parse_result and parse_result.was_fixed)
                    else "accepted"
                ),
            }
        )

        has_fragment = parse_result.fragment if parse_result else False
        confidence = parse_result.confidence.value if parse_result else "none"

        if len(valid) >= 2:
            image_name = f"r{cell.row}_c{cell.col}.png"
            line_texts = [f"y={seg.top}-{seg.bottom} d={seg.pixel_density:.3f}" for seg in valid]
            cv2.imwrite(str(complex_dir / image_name), crop)
            line_crops = analyzer.split_lines(crop)
            line_names: list[str] = []
            for idx, line_crop in enumerate(line_crops):
                line_name = f"r{cell.row}_c{cell.col}_line{idx}.png"
                line_names.append(line_name)
                cv2.imwrite(
                    str(complex_dir / line_name),
                    line_crop,
                )
            complex_log_lines.append(
                f"[{cell.row},{cell.col}] full=\"{raw_text}\" final=\"{normalized}\" "
                f"segments={len(valid)} confidence={confidence} fragment={has_fragment!r} "
                f"lines=[{','.join(line_texts)}]"
            )
            multiline_entries.append(
                {
                    "row": cell.row,
                    "col": cell.col,
                    "raw_text": raw_text,
                    "normalized_text": normalized,
                    "segment_count": len(valid),
                    "confidence": confidence,
                    "fragment": has_fragment,
                    "segments": [
                        {
                            "top": seg.top,
                            "bottom": seg.bottom,
                            "height": seg.height,
                            "pixel_density": seg.pixel_density,
                        }
                        for seg in valid
                    ],
                    "image": f"complex_cells/{image_name}",
                    "lines": [f"complex_cells/{name}" for name in line_names],
                }
            )
        elif parse_result and parse_result.confidence == parse_result.confidence.LOW:
            complex_log_lines.append(
                f"[{cell.row},{cell.col}] full=\"{raw_text}\" final=\"{normalized}\" "
                f"confidence={confidence} fragment={parse_result.fragment!r} "
                f"note=layout_single_row_parse_low"
            )
            warning_entries.append(
                {
                    "row": cell.row,
                    "col": cell.col,
                    "raw_text": raw_text,
                    "normalized_text": normalized,
                    "segment_count": len(valid),
                    "confidence": confidence,
                    "fragment": parse_result.fragment,
                    "note": "layout_single_row_parse_low",
                }
            )

    if complex_log_lines:
        with (debug_path / "complex_cells.txt").open("w", encoding="utf-8") as handle:
            handle.write("\n".join(complex_log_lines) + "\n")

    summary = {
        "profile_name": config.profile_name,
        "options": {
            "enable_table_region_extraction": config.enable_table_region_extraction,
            "enable_blur": config.enable_blur,
            "enable_adaptive_threshold": config.enable_adaptive_threshold,
            "enable_multiline_split": config.enable_multiline_split,
        },
        "table_rect": list(table_rect),
        "table_size": {
            "width": int(table_image.shape[1]),
            "height": int(table_image.shape[0]),
        },
        "row_lines": list(structure.row_lines),
        "col_lines": list(structure.col_lines),
        "cell_count": len(boxes),
        "row_count": len({cell.row for cell in boxes}),
        "multiline_cell_count": len(multiline_entries),
        "warning_cell_count": len(warning_entries),
        "correction_cell_count": len(correction_entries),
        "cells": cell_records,
        "correction_cells": correction_entries,
        "multiline_cells": multiline_entries,
        "warning_cells": warning_entries,
        "step_notes": _build_step_notes(config),
    }
    (debug_path / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def run_pipeline(config: PipelineConfig) -> list[RowResult]:
    image = load_image(config.input_path)
    table_image, table_rect, source_debug = extract_table_region(
        image,
        enable_blur=config.enable_blur,
        enable_adaptive_threshold=config.enable_adaptive_threshold,
        enabled=config.enable_table_region_extraction,
    )
    table_stages = build_binary_stages(
        table_image,
        enable_blur=config.enable_blur,
        enable_adaptive_threshold=config.enable_adaptive_threshold,
    )
    binary_image = table_stages["binary"]
    structure = detect_table_structure(binary_image)
    boxes = build_cell_boxes(structure)

    configure_tesseract(config.tesseract_cmd)
    raw_text_by_cell = ocr_cells(
        table_image,
        boxes,
        enable_multiline_split=config.enable_multiline_split,
    )
    normalized_text_by_cell: dict[tuple[int, int], str] = {}
    for cell in boxes:
        key = (cell.row, cell.col)
        raw_text = raw_text_by_cell.get(key, "")
        normalized_text_by_cell[key] = normalize_round_text(raw_text) if cell.col == 0 else normalize_action_text(raw_text)

    rows = group_text_rows(boxes, normalized_text_by_cell)
    write_csv(config.output_path, rows)

    if config.debug_dir:
        _write_debug_artifacts(
            config.debug_dir,
            config,
            source_debug,
            table_image,
            table_rect,
            table_stages,
            structure,
            boxes,
            raw_text_by_cell,
            normalized_text_by_cell,
        )

    return rows

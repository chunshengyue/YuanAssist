from __future__ import annotations

from collections.abc import Iterable

import cv2
import numpy as np

from .models import CellBox, TableStructure


def _normalize_binary_image(binary_image: np.ndarray) -> np.ndarray:
    array = np.asarray(binary_image)
    if array.ndim != 2:
        raise ValueError("binary_image must be a 2D array")
    if array.dtype != np.uint8:
        array = array.astype(np.uint8)
    return np.where(array > 0, 255, 0).astype(np.uint8)


def _cluster_positions(mask: np.ndarray) -> list[int]:
    positions = np.flatnonzero(mask)
    if positions.size == 0:
        return []

    clusters: list[list[int]] = [[int(positions[0])]]
    for pos in positions[1:]:
        value = int(pos)
        if value - clusters[-1][-1] <= 1:
            clusters[-1].append(value)
            continue
        clusters.append([value])
    return [sum(cluster) // len(cluster) for cluster in clusters]


def _extract_line_positions(line_image: np.ndarray, axis: int) -> list[int]:
    projection = np.count_nonzero(line_image, axis=axis)
    threshold = max(1, int(line_image.shape[axis] * 0.5))
    return _cluster_positions(projection >= threshold)


def detect_table_structure(binary_image: np.ndarray) -> TableStructure:
    image = _normalize_binary_image(binary_image)
    height, width = image.shape

    horizontal_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (max(3, width // 8), 1))
    vertical_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (1, max(3, height // 8)))

    horizontal_lines = cv2.morphologyEx(image, cv2.MORPH_OPEN, horizontal_kernel)
    vertical_lines = cv2.morphologyEx(image, cv2.MORPH_OPEN, vertical_kernel)

    row_lines = _extract_line_positions(horizontal_lines, axis=1)
    col_lines = _extract_line_positions(vertical_lines, axis=0)
    return TableStructure(row_lines=row_lines, col_lines=col_lines, width=width, height=height)


def _iter_spans(lines: Iterable[int]) -> list[tuple[int, int]]:
    sorted_lines = sorted(int(value) for value in lines)
    return list(zip(sorted_lines, sorted_lines[1:]))


def _drop_portrait_header_row(row_spans: list[tuple[int, int]]) -> list[tuple[int, int]]:
    if len(row_spans) < 3:
        return row_spans

    first_height = row_spans[0][1] - row_spans[0][0]
    action_heights = sorted(bottom - top for top, bottom in row_spans[1:])
    median_action_height = action_heights[len(action_heights) // 2]
    if first_height >= median_action_height * 2:
        return row_spans[1:]
    return row_spans


def _is_likely_missing_outer_gap(gap: int, median_gap: int) -> bool:
    return median_gap * 55 <= gap * 100 <= median_gap * 145


def _complete_outer_column_lines(lines: list[int], width: int, expected_cols: int) -> list[int]:
    expected_line_count = expected_cols + 1
    sorted_lines = sorted(int(value) for value in lines)
    if len(sorted_lines) >= expected_line_count or len(sorted_lines) < 2 or width <= 0:
        return sorted_lines

    gaps = [right - left for left, right in zip(sorted_lines, sorted_lines[1:])]
    median_gap = sorted(gaps)[len(gaps) // 2]
    if median_gap <= 0:
        return sorted_lines

    while len(sorted_lines) < expected_line_count:
        inserted = False
        left_gap = sorted_lines[0]
        if left_gap > 2 and _is_likely_missing_outer_gap(left_gap, median_gap):
            sorted_lines.insert(0, 0)
            inserted = True

        if len(sorted_lines) >= expected_line_count:
            break

        right_gap = width - 1 - sorted_lines[-1]
        if right_gap > 2 and _is_likely_missing_outer_gap(right_gap, median_gap):
            sorted_lines.append(width - 1)
            inserted = True

        if not inserted:
            break

    return sorted_lines


def build_cell_boxes(structure: TableStructure, max_output_cols: int = 6) -> list[CellBox]:
    row_spans = _drop_portrait_header_row(_iter_spans(structure.row_lines))
    col_lines = _complete_outer_column_lines(
        structure.col_lines,
        structure.width,
        max_output_cols,
    )
    col_spans = _iter_spans(col_lines)[: max(0, max_output_cols)]

    cells: list[CellBox] = []
    for row_index, (top, bottom) in enumerate(row_spans):
        for col_index, (left, right) in enumerate(col_spans):
            width = right - left
            height = bottom - top
            if width <= 0 or height <= 0:
                continue
            cells.append(
                CellBox(
                    row=row_index,
                    col=col_index,
                    x=left,
                    y=top,
                    w=width,
                    h=height,
                )
            )
    return cells

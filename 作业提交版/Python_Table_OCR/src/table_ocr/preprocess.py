from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np


def load_image(path: str) -> np.ndarray:
    image_path = Path(path)
    data = np.fromfile(image_path, dtype=np.uint8)
    image = cv2.imdecode(data, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError(f"unable to read image: {path}")
    return image


def _to_gray(image: np.ndarray) -> np.ndarray:
    array = np.asarray(image)
    if array.ndim == 3:
        return cv2.cvtColor(array, cv2.COLOR_BGR2GRAY)
    if array.ndim == 2:
        return array
    raise ValueError("image must be a 2D or 3D array")


def _find_best_rect_from_contours(
    contours: list[np.ndarray],
    min_area: int,
    image_width: int,
    image_height: int,
    score_fn,
) -> tuple[int, int, int, int] | None:
    best_rect: tuple[int, int, int, int] | None = None
    best_score = float("-inf")
    for contour in contours:
        x, y, w, h = cv2.boundingRect(contour)
        area = w * h
        if area < min_area:
            continue
        score = score_fn(x, y, w, h, image_width, image_height)
        if score > best_score:
            best_rect = (x, y, w, h)
            best_score = score
    return best_rect


def _table_like_score(
    x: int,
    y: int,
    w: int,
    h: int,
    image_width: int,
    image_height: int,
) -> float:
    area_ratio = (w * h) / max(1.0, float(image_width * image_height))
    width_ratio = w / max(1.0, float(image_width))
    height_ratio = h / max(1.0, float(image_height))
    aspect_ratio = w / max(1.0, float(h))
    center_x = (x + w / 2.0) / max(1.0, float(image_width))
    center_y = (y + h / 2.0) / max(1.0, float(image_height))

    score = area_ratio * 4.0
    score += min(width_ratio, 0.96) * 2.2
    score += min(height_ratio, 0.9) * 1.4

    if 0.7 <= aspect_ratio <= 2.8:
        score += 1.2
    elif 0.5 <= aspect_ratio <= 3.6:
        score += 0.5

    if area_ratio > 0.92:
        score -= (area_ratio - 0.92) * 12.0
    if width_ratio > 0.94:
        score -= (width_ratio - 0.94) * 10.0

    score -= abs(center_x - 0.5) * 0.8
    score -= abs(center_y - 0.62) * 0.6
    return score


def _fallback_rect_score(
    x: int,
    y: int,
    w: int,
    h: int,
    image_width: int,
    image_height: int,
) -> float:
    area_ratio = (w * h) / max(1.0, float(image_width * image_height))
    width_ratio = w / max(1.0, float(image_width))
    height_ratio = h / max(1.0, float(image_height))
    score = area_ratio * 3.0
    if area_ratio > 0.94:
        score -= (area_ratio - 0.94) * 10.0
    if width_ratio > 0.96:
        score -= (width_ratio - 0.96) * 8.0
    if height_ratio > 0.96:
        score -= (height_ratio - 0.96) * 4.0
    return score


def _extract_line_centers(mask: np.ndarray, axis: int) -> list[int]:
    if mask.size == 0:
        return []
    span = mask.shape[1] if axis == 1 else mask.shape[0]
    if span <= 0:
        return []
    densities = np.count_nonzero(mask, axis=axis) / float(span)
    max_density = float(densities.max()) if densities.size else 0.0
    threshold = max(0.16, max_density * 0.35)
    active = np.where(densities >= threshold)[0]
    if active.size == 0:
        return []

    groups: list[list[int]] = [[int(active[0])]]
    for index in active[1:]:
        value = int(index)
        if value == groups[-1][-1] + 1:
            groups[-1].append(value)
        else:
            groups.append([value])
    return [int(round(sum(group) / len(group))) for group in groups]


def _find_grid_body_top(horizontal_mask: np.ndarray) -> int | None:
    line_centers = _extract_line_centers(horizontal_mask, axis=1)
    if len(line_centers) < 5:
        return None

    gaps = [line_centers[index + 1] - line_centers[index] for index in range(len(line_centers) - 1)]
    positive_gaps = [gap for gap in gaps if gap >= 8]
    if len(positive_gaps) < 4:
        return None

    sorted_gaps = sorted(positive_gaps)
    lower_half_count = max(3, (len(sorted_gaps) + 1) // 2)
    dominant_gap = float(np.median(sorted_gaps[:lower_half_count]))
    if dominant_gap <= 0:
        return None

    stable_window = 3
    for index in range(len(gaps) - stable_window + 1):
        window = gaps[index : index + stable_window]
        stable_hits = sum(0.65 * dominant_gap <= gap <= 1.55 * dominant_gap for gap in window)
        if stable_hits >= stable_window - 1:
            return max(0, line_centers[index] - 2)
    return None


def _refine_rect_to_grid_body(
    rect: tuple[int, int, int, int],
    horizontal_mask: np.ndarray,
) -> tuple[int, int, int, int]:
    x, y, w, h = rect
    crop_horizontal = horizontal_mask[y : y + h, x : x + w]
    offset_top = _find_grid_body_top(crop_horizontal)
    if offset_top is None:
        return rect

    if offset_top <= 0 or offset_top >= h - 24:
        return rect

    return (x, y + offset_top, w, h - offset_top)


def build_binary_stages(
    image: np.ndarray,
    enable_blur: bool = True,
    enable_adaptive_threshold: bool = True,
) -> dict[str, np.ndarray]:
    gray = _to_gray(image)
    threshold_input = cv2.GaussianBlur(gray, (5, 5), 0) if enable_blur else gray.copy()
    if enable_adaptive_threshold:
        binary = cv2.adaptiveThreshold(
            threshold_input,
            255,
            cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
            cv2.THRESH_BINARY_INV,
            31,
            15,
        )
    else:
        binary = cv2.threshold(
            threshold_input,
            0,
            255,
            cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU,
        )[1]
    return {
        "gray": gray,
        "threshold_input": threshold_input,
        "binary": binary,
    }


def to_binary(
    image: np.ndarray,
    enable_blur: bool = True,
    enable_adaptive_threshold: bool = True,
) -> np.ndarray:
    return build_binary_stages(
        image,
        enable_blur=enable_blur,
        enable_adaptive_threshold=enable_adaptive_threshold,
    )["binary"]


def extract_table_region(
    image: np.ndarray,
    enable_blur: bool = True,
    enable_adaptive_threshold: bool = True,
    enabled: bool = True,
) -> tuple[np.ndarray, tuple[int, int, int, int], dict[str, np.ndarray | tuple[int, int, int, int]]]:
    array = np.asarray(image)
    if array.ndim not in (2, 3):
        raise ValueError("image must be a 2D or 3D array")

    stages = build_binary_stages(
        array,
        enable_blur=enable_blur,
        enable_adaptive_threshold=enable_adaptive_threshold,
    )
    binary = stages["binary"]
    height, width = binary.shape

    kernel = cv2.getStructuringElement(
        cv2.MORPH_RECT,
        (max(5, width // 30), max(5, height // 30)),
    )
    merged = cv2.morphologyEx(binary, cv2.MORPH_CLOSE, kernel, iterations=2)

    horizontal_kernel = cv2.getStructuringElement(
        cv2.MORPH_RECT,
        (max(12, width // 12), 1),
    )
    vertical_kernel = cv2.getStructuringElement(
        cv2.MORPH_RECT,
        (1, max(12, height // 12)),
    )
    horizontal = cv2.morphologyEx(binary, cv2.MORPH_OPEN, horizontal_kernel)
    vertical = cv2.morphologyEx(binary, cv2.MORPH_OPEN, vertical_kernel)
    grid_mask = cv2.bitwise_or(horizontal, vertical)
    grid_merge_kernel = cv2.getStructuringElement(
        cv2.MORPH_RECT,
        (max(8, width // 40), max(8, height // 40)),
    )
    grid_merged = cv2.morphologyEx(grid_mask, cv2.MORPH_CLOSE, grid_merge_kernel, iterations=2)

    overlay = array.copy()
    if overlay.ndim == 2:
        overlay = cv2.cvtColor(overlay, cv2.COLOR_GRAY2BGR)

    if not enabled:
        rect = (0, 0, width, height)
        cv2.rectangle(overlay, (0, 0), (max(0, width - 1), max(0, height - 1)), (0, 0, 255), 2)
        return array.copy(), rect, {
            "source_binary": binary,
            "source_merged": merged,
            "source_region_overlay": overlay,
        }

    min_area = max(1, int(height * width * 0.1))
    grid_contours, _ = cv2.findContours(grid_merged, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    best_rect = _find_best_rect_from_contours(
        grid_contours,
        min_area,
        width,
        height,
        _table_like_score,
    )

    if best_rect is None:
        contours, _ = cv2.findContours(merged, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        best_rect = _find_best_rect_from_contours(
            contours,
            min_area,
            width,
            height,
            _fallback_rect_score,
        )

    if best_rect is None:
        best_rect = (0, 0, width, height)

    best_rect = _refine_rect_to_grid_body(best_rect, horizontal)

    x, y, w, h = best_rect
    cv2.rectangle(
        overlay,
        (x, y),
        (max(x, x + w - 1), max(y, y + h - 1)),
        (0, 0, 255),
        2,
    )
    return array[y : y + h, x : x + w].copy(), best_rect, {
        "source_binary": binary,
        "source_merged": merged,
        "source_region_overlay": overlay,
    }

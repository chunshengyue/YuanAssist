from __future__ import annotations

import cv2
import numpy as np

from .models import LineSegment


def _to_inverted_binary(crop: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
    thresholded = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
    return 255 - thresholded


class CellLayoutAnalyzer:
    def __init__(
        self,
        min_segment_height: int = 4,
        merge_gap: int = 3,
        noise_density_threshold: float = 0.01,
    ) -> None:
        self.min_segment_height = min_segment_height
        self.merge_gap = merge_gap
        self.noise_density_threshold = noise_density_threshold

    def analyze(self, crop: np.ndarray) -> list[LineSegment]:
        if crop.size == 0:
            return []

        binary = _to_inverted_binary(crop)
        height, width = binary.shape
        row_sums = binary.sum(axis=1)

        spans: list[tuple[int, int]] = []
        start: int | None = None
        for idx, value in enumerate(row_sums):
            if value > 0 and start is None:
                start = idx
            elif value == 0 and start is not None:
                spans.append((start, idx))
                start = None
        if start is not None:
            spans.append((start, height))

        merged: list[tuple[int, int]] = []
        for top, bottom in spans:
            if bottom - top < self.min_segment_height:
                continue
            if merged and top - merged[-1][1] <= self.merge_gap:
                merged[-1] = (merged[-1][0], bottom)
            else:
                merged.append((top, bottom))

        segments: list[LineSegment] = []
        for top, bottom in merged:
            seg_height = bottom - top
            seg_area = width * seg_height
            pixel_sum = int(row_sums[top:bottom].sum())
            density = pixel_sum / seg_area if seg_area > 0 else 0.0
            is_noise = density < self.noise_density_threshold
            segments.append(
                LineSegment(
                    top=top,
                    bottom=bottom,
                    height=seg_height,
                    pixel_density=float(density),
                    is_noise=is_noise,
                )
            )

        return segments

    def is_complex_cell(self, crop: np.ndarray) -> bool:
        segments = self.analyze(crop)
        valid = [seg for seg in segments if not seg.is_noise]
        return len(valid) >= 2

    def split_lines(self, crop: np.ndarray, padding: int = 2) -> list[np.ndarray]:
        segments = self.analyze(crop)
        valid = [seg for seg in segments if not seg.is_noise]
        if len(valid) <= 1:
            return []

        h = crop.shape[0]
        line_crops: list[np.ndarray] = []
        for seg in valid:
            top = max(0, seg.top - padding)
            bottom = min(h, seg.bottom + padding)
            line_crops.append(crop[top:bottom, :].copy())
        return line_crops

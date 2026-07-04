from __future__ import annotations

import os
import re
from pathlib import Path

import cv2
import numpy as np

from .action_parser import ActionSequenceParser
from .layout import CellLayoutAnalyzer
from .models import CellBox

_PROJECT_ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("PADDLE_PDX_CACHE_HOME", str(_PROJECT_ROOT / ".paddlex"))
os.environ.setdefault("PADDLE_PDX_MODEL_SOURCE", "BOS")
os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")
_CANONICAL_ACTION_RE = re.compile(r"^(?:10|[1-9])(?:[A↑↓圈]+)$")

try:
    from paddleocr import TextRecognition
except ModuleNotFoundError:  # pragma: no cover - depends on local env
    TextRecognition = None

_TEXT_RECOGNIZER: TextRecognition | None = None


def _require_paddleocr() -> None:
    if TextRecognition is None:
        raise RuntimeError(
            "PaddleOCR is required to run OCR but is not installed. "
            "Install `paddlepaddle` and `paddleocr` first."
        )


def configure_tesseract(_legacy_tesseract_cmd: str | None = None) -> None:
    global _TEXT_RECOGNIZER

    _require_paddleocr()
    if _TEXT_RECOGNIZER is None:
        _TEXT_RECOGNIZER = TextRecognition(
            model_name="PP-OCRv5_mobile_rec",
            device="cpu",
            enable_mkldnn=False,
        )


def crop_cell(image: np.ndarray, cell: CellBox, padding: int = 2) -> np.ndarray:
    height, width = image.shape[:2]
    left = max(0, cell.x + padding)
    top = max(0, cell.y + padding)
    right = min(width, cell.x + cell.w - padding)
    bottom = min(height, cell.y + cell.h - padding)
    if right <= left or bottom <= top:
        right = min(width, cell.x + cell.w)
        bottom = min(height, cell.y + cell.h)
        left = max(0, cell.x)
        top = max(0, cell.y)
    return image[top:bottom, left:right].copy()


def _prepare_crop(crop: np.ndarray, scale: int = 4) -> np.ndarray:
    if crop.size == 0:
        return crop
    resized = cv2.resize(crop, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    return cv2.copyMakeBorder(resized, 8, 8, 8, 8, cv2.BORDER_CONSTANT, value=(255, 255, 255))


def _to_inverted_binary(crop: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
    thresholded = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
    return 255 - thresholded


def _find_components(binary_image: np.ndarray) -> list[tuple[int, int, int, int, int]]:
    num_labels, _, stats, _ = cv2.connectedComponentsWithStats(binary_image, 8)
    components: list[tuple[int, int, int, int, int]] = []
    for index in range(1, num_labels):
        x, y, w, h, area = stats[index]
        if area < 3:
            continue
        components.append((int(x), int(y), int(w), int(h), int(area)))
    components.sort(key=lambda item: item[0])
    return components


def _filter_border_components(
    binary_image: np.ndarray,
    components: list[tuple[int, int, int, int, int]],
) -> list[tuple[int, int, int, int, int]]:
    height, width = binary_image.shape[:2]
    filtered: list[tuple[int, int, int, int, int]] = []
    for component in components:
        x, y, w, h, area = component
        touches_border = x == 0 or y == 0 or x + w >= width or y + h >= height
        looks_like_gridline = (
            w <= 2
            or h <= 2
            or w >= max(8, int(width * 0.3))
            or h >= max(8, int(height * 0.3))
        )
        if touches_border and looks_like_gridline:
            continue
        filtered.append((x, y, w, h, area))
    return filtered


def _crop_components(
    crop: np.ndarray,
    components: list[tuple[int, int, int, int, int]],
    padding: int = 2,
) -> np.ndarray:
    if not components:
        return crop

    height, width = crop.shape[:2]
    left = max(0, min(component[0] for component in components) - padding)
    top = max(0, min(component[1] for component in components) - padding)
    right = min(width, max(component[0] + component[2] for component in components) + padding)
    bottom = min(height, max(component[1] + component[3] for component in components) + padding)
    return crop[top:bottom, left:right].copy()


def _extract_digit(text: str) -> str:
    match = re.search(r"[1-5]", text)
    return match.group(0) if match else ""


def _looks_complex_action_text(text: str) -> bool:
    compact = "".join(text.split())
    return "圈" in compact or len(re.findall(r"\d+", compact)) >= 2


def _is_non_action_text(text: str) -> bool:
    compact = "".join(text.split())
    if not compact:
        return False
    has_action_char = any(char in {"A", "↑", "↓", "圈"} or char.isdigit() for char in compact)
    has_chinese = any("\u4e00" <= char <= "\u9fff" for char in compact)
    return has_chinese and not has_action_char


def _classify_arrow_component(binary_image: np.ndarray, component: tuple[int, int, int, int, int]) -> str:
    x, y, w, h, _ = component
    if w * 100 > h * 70:
        return ""
    mask = binary_image[y : y + h, x : x + w]
    if mask.size == 0:
        return ""

    on_pixels = (mask > 0).astype(np.uint8)
    row_sums = on_pixels.sum(axis=1)
    if row_sums.size < 3:
        return ""

    window = max(1, row_sums.size // 3)
    top_mass = int(row_sums[:window].sum())
    bottom_mass = int(row_sums[-window:].sum())
    if top_mass >= bottom_mass + 2:
        return "↑"
    if bottom_mass >= top_mass + 2:
        return "↓"
    return ""


def _count_inner_contours(mask: np.ndarray) -> int:
    contours, hierarchy = cv2.findContours(mask.copy(), cv2.RETR_CCOMP, cv2.CHAIN_APPROX_SIMPLE)
    if hierarchy is None:
        return 0
    return sum(1 for item in hierarchy[0] if item[3] >= 0)


def _classify_circle_component(binary_image: np.ndarray, component: tuple[int, int, int, int, int]) -> str:
    x, y, w, h, _ = component
    if w < 12 or h < 12:
        return ""
    aspect_percent = w * 100 // max(1, h)
    if not 75 <= aspect_percent <= 125:
        return ""

    mask = binary_image[y : y + h, x : x + w]
    density_percent = int(np.count_nonzero(mask) * 100 / max(1, w * h))
    holes = _count_inner_contours(mask)
    return "圈" if holes >= 2 and density_percent >= 45 else ""


def _looks_like_zero_component(binary_image: np.ndarray, component: tuple[int, int, int, int, int]) -> bool:
    x, y, w, h, _ = component
    if w < 8 or h < 12:
        return False
    aspect_percent = w * 100 // max(1, h)
    if not 45 <= aspect_percent <= 95:
        return False

    mask = binary_image[y : y + h, x : x + w]
    density_percent = int(np.count_nonzero(mask) * 100 / max(1, w * h))
    holes = _count_inner_contours(mask)
    return holes >= 1 and 28 <= density_percent <= 62


def _looks_like_one_digit(component: tuple[int, int, int, int, int]) -> bool:
    _, _, w, h, _ = component
    return w * 100 <= h * 50


def _group_components_for_reading(
    components: list[tuple[int, int, int, int, int]],
) -> list[list[tuple[int, int, int, int, int]]]:
    if len(components) <= 1:
        return [components] if components else []

    lines: list[list[tuple[int, int, int, int, int]]] = []
    for component in sorted(components, key=lambda item: (item[1] + item[3] / 2, item[0])):
        _, y, _, h, _ = component
        center_y = y + h / 2
        threshold = max(6, h / 2)
        target_line: list[tuple[int, int, int, int, int]] | None = None
        for line in lines:
            avg_center = sum(item[1] + item[3] / 2 for item in line) / len(line)
            if abs(avg_center - center_y) <= threshold:
                target_line = line
                break
        if target_line is None:
            lines.append([component])
        else:
            target_line.append(component)

    return [
        sorted(line, key=lambda item: item[0])
        for line in sorted(lines, key=lambda line: sum(item[1] + item[3] / 2 for item in line) / len(line))
    ]


def _normalize_component_token(text: str, component: tuple[int, int, int, int, int]) -> str:
    compact = "".join(text.replace("/", "").split())
    if not compact:
        return ""

    result: list[str] = []
    for char in compact:
        if char in {"I", "l"}:
            result.append("1")
        elif char == "O":
            result.append("0")
        elif char in {"A", "圈"} or char.isdigit():
            result.append(char)

    token = "".join(result)
    _, _, w, h, _ = component
    if token == "A" and w * 100 >= h * 140:
        estimated_count = max(2, (w * 100 + h * 42) // max(1, h * 85))
        return "A" * estimated_count
    return token


def _choose_action_token(
    ocr_token: str,
    arrow: str,
    circle: str,
    looks_zero: bool,
    component: tuple[int, int, int, int, int],
) -> str:
    compact = "".join(ocr_token.split())
    if arrow:
        return arrow
    if circle:
        return circle
    if "圈" in compact:
        return "圈"

    digit = _extract_digit(compact)
    if digit:
        suffix = "".join(char for char in compact if char in {"A", "圈"})
        if digit == "7" and _looks_like_one_digit(component):
            digit = "1"
        return digit + suffix
    if "A" in compact:
        return "A"
    if _looks_like_one_digit(component):
        return "1"
    if looks_zero:
        return "0"
    return "".join(char for char in compact if char in {"A", "↑", "↓", "圈"})


def _transcribe_component_line(
    crop: np.ndarray,
    binary_image: np.ndarray,
    components: list[tuple[int, int, int, int, int]],
) -> str:
    parts: list[str] = []
    for component in components:
        component_crop = _crop_components(crop, [component])
        token_text = _recognize_text(_prepare_crop(component_crop, scale=8))
        normalized_token = _normalize_component_token(token_text, component)
        token = _choose_action_token(
            normalized_token,
            _classify_arrow_component(binary_image, component),
            _classify_circle_component(binary_image, component),
            _looks_like_zero_component(binary_image, component),
            component,
        )
        if token:
            parts.append(token)
    return "".join(parts)


def _assemble_action_text(
    full_text: str,
    crop: np.ndarray,
) -> str:
    if _is_non_action_text(full_text):
        return ""

    compact_full_text = "".join(full_text.replace("/", "").split())
    if _CANONICAL_ACTION_RE.fullmatch(compact_full_text):
        return compact_full_text

    binary_image = _to_inverted_binary(crop)
    components = _filter_border_components(binary_image, _find_components(binary_image))
    if len(components) < 2:
        return full_text

    component_lines = _group_components_for_reading(components)
    line_text = "".join(_transcribe_component_line(crop, binary_image, line) for line in component_lines)
    parsed_line = ActionSequenceParser().parse(line_text)
    if parsed_line.text and (parsed_line.is_complete or parsed_line.confidence.value != "low"):
        return parsed_line.text + parsed_line.fragment

    digit_component = components[:1]
    suffix_components = components[1:]

    digit_text = _recognize_text(_prepare_crop(_crop_components(crop, digit_component), scale=8))
    digit = _extract_digit(digit_text) or _extract_digit(full_text)
    if not digit:
        return full_text

    suffix_crop = _crop_components(crop, suffix_components)
    suffix_text = _recognize_text(_prepare_crop(suffix_crop, scale=8))
    arrow = _classify_arrow_component(binary_image, suffix_components[0])

    if "A" in suffix_text:
        if arrow:
            return f"{digit}{arrow}/A"
        return f"{digit}A"
    if arrow:
        return f"{digit}{arrow}"
    return f"{digit}{suffix_text}" if suffix_text else full_text


def _recognize_text(image: np.ndarray, compact: bool = True) -> str:
    _require_paddleocr()
    configure_tesseract(None)
    assert _TEXT_RECOGNIZER is not None

    if image.size == 0:
        return ""

    results = list(_TEXT_RECOGNIZER.predict(input=image, batch_size=1))
    if not results:
        return ""

    data = results[0].json.get("res", {})
    text = str(data.get("rec_text", ""))
    return "".join(text.split()) if compact else text


def ocr_full_image_raw_text(source_image: np.ndarray) -> str:
    if source_image.size == 0:
        return ""
    return _recognize_text(source_image, compact=False).strip()


def ocr_round_cell(source_image: np.ndarray, cell: CellBox) -> str:
    crop = crop_cell(source_image, cell)
    prepared = _prepare_crop(crop, scale=4)
    return _recognize_text(prepared)


def ocr_action_cell(
    source_image: np.ndarray,
    cell: CellBox,
    enable_multiline_split: bool = True,
) -> str:
    crop = crop_cell(source_image, cell)
    prepared = _prepare_crop(crop, scale=4)
    full_text = _recognize_text(prepared)

    parser = ActionSequenceParser()
    analyzer = CellLayoutAnalyzer()
    line_crops = analyzer.split_lines(crop) if enable_multiline_split else []
    if line_crops:
        parsed_parts: list[str] = []
        all_lines_usable = True
        for line_crop in line_crops:
            line_text = _recognize_text(_prepare_crop(line_crop, scale=4))
            assembled = _assemble_action_text(line_text, line_crop)
            if assembled:
                parsed_parts.append(assembled)
            else:
                all_lines_usable = False
        if parsed_parts and all_lines_usable:
            return "".join(parsed_parts)

    if _looks_complex_action_text(full_text):
        result = parser.parse(full_text)
        if result.is_complete and result.text:
            return result.text
        if result.text:
            return result.text + result.fragment
        return full_text

    return _assemble_action_text(full_text, crop)


def ocr_cells(
    source_image: np.ndarray,
    boxes: list[CellBox],
    enable_multiline_split: bool = True,
) -> dict[tuple[int, int], str]:
    text_by_cell: dict[tuple[int, int], str] = {}
    for cell in boxes:
        key = (cell.row, cell.col)
        text_by_cell[key] = (
            ocr_round_cell(source_image, cell)
            if cell.col == 0
            else ocr_action_cell(
                source_image,
                cell,
                enable_multiline_split=enable_multiline_split,
            )
        )
    return text_by_cell

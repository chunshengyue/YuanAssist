from __future__ import annotations

import re

_DIGIT_PATTERN = r"(?:10|[1-9])"
_SUFFIX_PATTERN = r"(?:[A↑↓圈]+)"
_ACTION_RE = re.compile(rf"^({_DIGIT_PATTERN})({_SUFFIX_PATTERN})$")
_SUFFIX_ONLY_RE = re.compile(rf"^{_SUFFIX_PATTERN}$")
_COMPLEX_ACTION_TOKEN_RE = re.compile(rf"{_DIGIT_PATTERN}{_SUFFIX_PATTERN}")
_DIGIT_FIXES = str.maketrans(
    {
        "I": "1",
        "l": "1",
        "O": "0",
    }
)
_ACTION_SUFFIX_FIXES = str.maketrans(
    {
        "T": "↑",
        "t": "↑",
        "V": "↓",
        "v": "↓",
        "Y": "↓",
        "y": "↓",
        "L": "↓",
        "U": "↓",
        "√": "↓",
        "」": "↓",
        "』": "↓",
        "{": "↑",
        "\\": "↓",
        "个": "↑",
    }
)


def _compact_text(text: str) -> str:
    return "".join(text.split())


def normalize_action_text(text: str) -> str:
    cleaned = _compact_text(text).replace("/", "")
    if not cleaned:
        return ""

    normalized = _repair_digit_one_as_up_arrow(cleaned.translate(_DIGIT_FIXES).translate(_ACTION_SUFFIX_FIXES))
    if _SUFFIX_ONLY_RE.fullmatch(normalized):
        return normalized

    match = _ACTION_RE.fullmatch(normalized)
    if not match:
        if "圈" in normalized or len(re.findall(r"\d+", normalized)) >= 2:
            tokens = _COMPLEX_ACTION_TOKEN_RE.findall(normalized)
            if tokens:
                joined = "".join(tokens)
                if joined == normalized or len(tokens) >= 2:
                    return joined
            if re.search(r"\d", normalized) and re.search(r"[A↑↓圈]", normalized):
                return normalized

        first_digit_match = re.search(r"10|[1-9]", normalized)
        if not first_digit_match:
            return ""

        first_digit = first_digit_match.group(0)
        tail = normalized[first_digit_match.end() :]
        if "↑" in tail:
            if "A" in tail:
                return f"{first_digit}↑A"
            return f"{first_digit}↑"
        if tail == "A":
            return f"{first_digit}A"
        if "A" in tail and "↓" in tail:
            return f"{first_digit}↓A"
        if "↓" in tail:
            return f"{first_digit}↓"
        return ""
    return f"{match.group(1)}{match.group(2)}"


def normalize_round_text(text: str) -> str:
    cleaned = _compact_text(text)
    if not cleaned:
        return ""
    if cleaned.endswith("回合"):
        prefix = cleaned[:-2].lstrip("第").translate(_DIGIT_FIXES)
        if prefix.isdigit():
            return f"{prefix}回合"
    normalized = cleaned.translate(_DIGIT_FIXES)
    match = re.search(r"(\d+)", normalized)
    if match:
        return f"{match.group(1)}回合"
    return ""


def _repair_digit_one_as_up_arrow(text: str) -> str:
    if not re.search(r"\d1(?:\d|$)", text):
        return text

    chars = list(text)
    for index in range(1, len(chars)):
        if chars[index] != "1" or not chars[index - 1].isdigit():
            continue
        chars[index] = "↑"
        candidate = "".join(chars)
        if _ACTION_RE.fullmatch(candidate) or "".join(_COMPLEX_ACTION_TOKEN_RE.findall(candidate)) == candidate:
            return candidate
        chars[index] = "1"
    return text

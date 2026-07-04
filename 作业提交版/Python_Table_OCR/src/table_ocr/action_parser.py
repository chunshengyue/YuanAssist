from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum


class ConfidenceLevel(Enum):
    HIGH = "high"
    MEDIUM = "medium"
    LOW = "low"


_DIGIT_PATTERN = r"(?:10|[1-9])"
_SUFFIX_PATTERN = r"(?:[A↑↓圈]+)"
_SINGLE_ACTION_RE = re.compile(rf"^({_DIGIT_PATTERN})({_SUFFIX_PATTERN})$")
_SUFFIX_ONLY_RE = re.compile(rf"^{_SUFFIX_PATTERN}$")
_COMPLEX_TOKEN_RE = re.compile(rf"{_DIGIT_PATTERN}{_SUFFIX_PATTERN}")

_DIGIT_FIXES = str.maketrans(
    {
        "I": "1",
        "l": "1",
        "O": "0",
    }
)
_SUFFIX_FIXES = str.maketrans(
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


@dataclass(slots=True)
class ParseResult:
    text: str
    is_complete: bool
    fragment: str = ""
    confidence: ConfidenceLevel = ConfidenceLevel.HIGH
    was_fixed: bool = False


class ActionSequenceParser:
    def __init__(self) -> None:
        self._token_re = _COMPLEX_TOKEN_RE

    def parse(self, text: str) -> ParseResult:
        compact = "".join(text.replace("/", "").split())
        if not compact:
            return ParseResult(
                "", is_complete=True, fragment="", confidence=ConfidenceLevel.HIGH, was_fixed=False
            )

        cleaned = _repair_digit_one_as_up_arrow(compact.translate(_DIGIT_FIXES).translate(_SUFFIX_FIXES))
        was_fixed = cleaned != compact

        suffix_only = _SUFFIX_ONLY_RE.fullmatch(cleaned)
        if suffix_only:
            level = ConfidenceLevel.MEDIUM if was_fixed else ConfidenceLevel.HIGH
            return ParseResult(
                cleaned,
                is_complete=True,
                fragment="",
                confidence=level,
                was_fixed=was_fixed,
            )

        single = _SINGLE_ACTION_RE.fullmatch(cleaned)
        if single:
            level = ConfidenceLevel.MEDIUM if was_fixed else ConfidenceLevel.HIGH
            return ParseResult(
                f"{single.group(1)}{single.group(2)}",
                is_complete=True,
                fragment="",
                confidence=level,
                was_fixed=was_fixed,
            )

        tokens = self._token_re.findall(cleaned)
        if not tokens:
            return ParseResult(
                "",
                is_complete=False,
                fragment=cleaned,
                confidence=ConfidenceLevel.LOW,
                was_fixed=was_fixed,
            )

        joined = "".join(tokens)
        is_complete = joined == cleaned
        fragment = cleaned[len(joined):] if not is_complete else ""

        if is_complete and not was_fixed:
            confidence = ConfidenceLevel.HIGH
        elif is_complete:
            confidence = ConfidenceLevel.MEDIUM
        else:
            confidence = ConfidenceLevel.LOW

        return ParseResult(
            joined,
            is_complete=is_complete,
            fragment=fragment,
            confidence=confidence,
            was_fixed=was_fixed,
        )


def _repair_digit_one_as_up_arrow(text: str) -> str:
    if not re.search(r"\d1(?:\d|$)", text):
        return text

    chars = list(text)
    for index in range(1, len(chars)):
        if chars[index] != "1" or not chars[index - 1].isdigit():
            continue
        chars[index] = "↑"
        candidate = "".join(chars)
        if _SINGLE_ACTION_RE.fullmatch(candidate) or "".join(_COMPLEX_TOKEN_RE.findall(candidate)) == candidate:
            return candidate
        chars[index] = "1"
    return text

from __future__ import annotations

from dataclasses import dataclass
import json
import re
from pathlib import Path
from typing import Any

from .battle_static import BattleStaticInput, BattleStaticReport, check_battle_static


DEFAULT_BATTLE_CASE_DIR = Path("tools/yuanassist_test_tool/cases/battle_static")


@dataclass(frozen=True)
class StrategyCase:
    object_id: str
    title: str
    script_content: str
    instructions: str
    visible: int | None = None
    created_at: str = ""
    updated_at: str = ""

    @classmethod
    def from_row(cls, row: dict[str, Any]) -> "StrategyCase":
        return cls(
            object_id=str(first_present(row, "objectId", "object_id", "id") or ""),
            title=str(first_present(row, "title", "name") or "未命名攻略"),
            script_content=str(first_present(row, "scriptContent", "script_content") or ""),
            instructions=normalize_instructions(first_present(row, "instructions", "instructionsJson", "instructions_json")),
            visible=read_optional_int(first_present(row, "visible")),
            created_at=str(first_present(row, "createdAt", "created_at") or ""),
            updated_at=str(first_present(row, "updatedAt", "updated_at") or ""),
        )

    def to_battle_input(self) -> BattleStaticInput:
        return BattleStaticInput(
            title=self.title,
            script_content=self.script_content,
            instructions_json=self.instructions,
        )

    def to_json_dict(self) -> dict[str, Any]:
        return {
            "objectId": self.object_id,
            "title": self.title,
            "scriptContent": self.script_content,
            "instructions": instructions_as_json_value(self.instructions),
            "visible": self.visible,
            "createdAt": self.created_at,
            "updatedAt": self.updated_at,
            "source": "supabase.strategy_detail",
        }


def save_strategy_cases(cases: list[StrategyCase], output_dir: Path = DEFAULT_BATTLE_CASE_DIR) -> list[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    used_names: set[str] = set()
    for index, case in enumerate(cases, start=1):
        base_name = slugify(f"{index:03d}_{case.object_id or case.title}")
        name = unique_name(base_name, used_names)
        path = output_dir / f"{name}.json"
        path.write_text(json.dumps(case.to_json_dict(), ensure_ascii=False, indent=2), encoding="utf-8")
        written.append(path)
    return written


def load_strategy_case(path: Path) -> StrategyCase:
    raw = path.read_text(encoding="utf-8")
    data = json.loads(raw)
    if not isinstance(data, dict):
        raise ValueError(f"case 文件顶层必须是 JSON object：{path}")
    return StrategyCase.from_row(data)


def check_strategy_cases(cases: list[StrategyCase]) -> list[BattleStaticReport]:
    return [check_battle_static(case.to_battle_input()) for case in cases]


def summarize_reports(reports: list[BattleStaticReport]) -> dict[str, int]:
    return {
        "cases": len(reports),
        "passed": sum(1 for report in reports if not report.errors),
        "failed": sum(1 for report in reports if report.errors),
        "warnings": sum(len(report.warnings) for report in reports),
        "errors": sum(len(report.errors) for report in reports),
        "turns": sum(len(report.turns) for report in reports),
        "actions": sum(sum(len(turn.actions) for turn in report.turns) for report in reports),
        "instructions": sum(report.instruction_count for report in reports),
    }


def reports_to_dict(cases: list[StrategyCase], reports: list[BattleStaticReport]) -> dict[str, Any]:
    return {
        "summary": summarize_reports(reports),
        "items": [
            {
                "objectId": case.object_id,
                "title": case.title,
                "report": report.to_dict(),
            }
            for case, report in zip(cases, reports)
        ],
    }


def first_present(row: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key in row:
            return row[key]
    return None


def normalize_instructions(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False)


def instructions_as_json_value(text: str) -> Any:
    if not text.strip():
        return []
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return text


def read_optional_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, int):
        return value
    if isinstance(value, float) and value.is_integer():
        return int(value)
    if isinstance(value, str) and re.fullmatch(r"-?\d+", value.strip()):
        return int(value.strip())
    return None


def slugify(value: str) -> str:
    cleaned = re.sub(r'[<>:"/\\|?*\x00-\x1f]+', "_", value.strip())
    cleaned = re.sub(r"\s+", "_", cleaned)
    cleaned = cleaned.strip("._ ")
    return cleaned[:90] or "case"


def unique_name(base_name: str, used_names: set[str]) -> str:
    candidate = base_name
    suffix = 2
    while candidate.lower() in used_names:
        candidate = f"{base_name}_{suffix}"
        suffix += 1
    used_names.add(candidate.lower())
    return candidate


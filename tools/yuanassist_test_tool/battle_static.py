from __future__ import annotations

from dataclasses import dataclass, field
import json
import re
from typing import Any


ACTION_RUN_RE = re.compile(r"(\d+)([A↑↓圈]+)")
COMMAND_ONLY_RE = re.compile(r"^[A↑↓圈]+$")
INVALID_COMMAND_RE = re.compile(r"[^0-9A↑↓圈]")
TURN_TOKEN_RE = re.compile(r"(\d+)")

ALLOWED_INSTRUCTION_TYPES = {
    "DELAY_ADD",
    "DELAY_SUBTRACT",
    "PAUSE",
    "STAGE_AUTO_NAV",
    "ALL_WIPE_CHECK",
    "DEATH_CHECK",
    "CRIT_CHECK",
    "ORANGE_STAR_CHECK",
    "PURPLE_STAR_CHECK",
    "TARGET_SWITCH",
    "TARGET_SWITCH_LEFT",
    "TARGET_SWITCH_RIGHT",
}

STEP_HIDDEN_TYPES = {
    "STAGE_AUTO_NAV",
    "ALL_WIPE_CHECK",
    "DEATH_CHECK",
    "ORANGE_STAR_CHECK",
    "PURPLE_STAR_CHECK",
}

VALUELESS_TYPES = {
    "PAUSE",
    "ALL_WIPE_CHECK",
    "CRIT_CHECK",
    "ORANGE_STAR_CHECK",
    "PURPLE_STAR_CHECK",
}

STAGE_AUTO_NAV_TARGET_CODES = {1, 2, 3, 4, 5, 6, 7, 8}
CAVE_TARGET_CODES = {2, 3}
STAGE_AUTO_NAV_CAVE_NEXT_FLOOR_FLAG = 1000


@dataclass(frozen=True)
class BattleStaticInput:
    title: str
    script_content: str = ""
    instructions_json: str = ""


@dataclass(frozen=True)
class ScriptAction:
    line_no: int
    turn: int
    slot: int
    order: int
    command: str
    repeat: int
    raw_cell: str


@dataclass(frozen=True)
class ScriptTurn:
    line_no: int
    turn: int
    cells: list[str]
    actions: list[ScriptAction]


@dataclass(frozen=True)
class Finding:
    severity: str
    code: str
    message: str
    location: str = ""

    def to_dict(self) -> dict[str, str]:
        return {
            "severity": self.severity,
            "code": self.code,
            "message": self.message,
            "location": self.location,
        }


@dataclass
class BattleStaticReport:
    title: str
    turns: list[ScriptTurn] = field(default_factory=list)
    instruction_count: int = 0
    findings: list[Finding] = field(default_factory=list)

    @property
    def errors(self) -> list[Finding]:
        return [finding for finding in self.findings if finding.severity == "ERROR"]

    @property
    def warnings(self) -> list[Finding]:
        return [finding for finding in self.findings if finding.severity == "WARN"]

    def add_error(self, code: str, message: str, location: str = "") -> None:
        self.findings.append(Finding("ERROR", code, message, location))

    def add_warning(self, code: str, message: str, location: str = "") -> None:
        self.findings.append(Finding("WARN", code, message, location))

    def to_dict(self) -> dict[str, Any]:
        return {
            "title": self.title,
            "summary": {
                "turns": len(self.turns),
                "actions": sum(len(turn.actions) for turn in self.turns),
                "instructions": self.instruction_count,
                "errors": len(self.errors),
                "warnings": len(self.warnings),
            },
            "findings": [finding.to_dict() for finding in self.findings],
            "turns": [
                {
                    "line": turn.line_no,
                    "turn": turn.turn,
                    "cells": turn.cells,
                    "actions": [
                        {
                            "slot": action.slot,
                            "order": action.order,
                            "command": action.command,
                            "repeat": action.repeat,
                        }
                        for action in turn.actions
                    ],
                }
                for turn in self.turns
            ],
        }

    def to_text(self) -> str:
        lines = [
            "YuanAssist 战斗静态检查报告",
            f"目标：{self.title}",
            (
                "汇总："
                f"{len(self.turns)} 回合，"
                f"{sum(len(turn.actions) for turn in self.turns)} 个动作，"
                f"{self.instruction_count} 条附加指令，"
                f"{len(self.errors)} 个错误，"
                f"{len(self.warnings)} 个警告"
            ),
        ]
        if not self.findings:
            lines.append("结果：通过")
            return "\n".join(lines)

        lines.append("问题：")
        for finding in self.findings:
            location = f" [{finding.location}]" if finding.location else ""
            lines.append(f"- {finding.severity} {finding.code}{location}: {finding.message}")
        return "\n".join(lines)


def check_battle_static(payload: BattleStaticInput) -> BattleStaticReport:
    report = BattleStaticReport(title=payload.title)
    turns = parse_script_content(payload.script_content, report)
    report.turns = turns
    check_turns(turns, report)
    check_instructions(payload.instructions_json, turns, report)
    return report


def parse_script_content(script_content: str, report: BattleStaticReport) -> list[ScriptTurn]:
    normalized = script_content.replace("\\n", "\n").replace("\\t", "\t").strip()
    if not normalized:
        report.add_warning("SCRIPT_EMPTY", "未提供战斗脚本文本，跳过回合动作检查")
        return []

    turns: list[ScriptTurn] = []
    for index, raw_line in enumerate(normalized.splitlines(), start=1):
        line = raw_line.rstrip("\r")
        if not line.strip():
            continue

        parts = line.split("\t") if "\t" in line else re.split(r"\s+", line.strip())
        if not parts:
            continue

        first = parts[0].strip()
        turn_match = TURN_TOKEN_RE.search(first)
        if turn_match:
            turn = int(turn_match.group(1))
            cell_start = 1 if ("回" in first or first.isdigit()) else 0
        else:
            turn = index
            cell_start = 0
            report.add_warning(
                "TURN_FALLBACK",
                f"未识别到回合号，按第 {index} 行作为第 {turn} 回合处理",
                f"line {index}",
            )

        cells = [cell.strip() for cell in parts[cell_start:]]
        if len(cells) > 5:
            report.add_warning(
                "TOO_MANY_COLUMNS",
                f"回合表超过 5 个角色列，多出的 {len(cells) - 5} 列运行时会被忽略",
                f"line {index}",
            )
        while len(cells) < 5:
            cells.append("")
        cells = cells[:5]

        actions: list[ScriptAction] = []
        for slot_index, cell in enumerate(cells, start=1):
            if not cell or cell == "-":
                continue

            parsed_actions, extra_text = parse_action_cell(cell, line_no=index, turn=turn, slot=slot_index)
            if not parsed_actions:
                report.add_error(
                    "UNSUPPORTED_ACTION_CELL",
                    f"第 {turn} 回合第 {slot_index} 列无法解析动作：{cell}",
                    f"line {index}",
                )
                continue

            if extra_text:
                report.add_warning(
                    "ACTION_CELL_EXTRA_TEXT",
                    f"动作格包含无法执行的附加文本：{cell}",
                    f"line {index}",
                )

            actions.extend(parsed_actions)

        turns.append(ScriptTurn(line_no=index, turn=turn, cells=cells, actions=actions))

    if not turns:
        report.add_warning("SCRIPT_NO_TURNS", "脚本文本没有解析出任何回合")
    return turns


def parse_action_cell(cell: str, *, line_no: int, turn: int, slot: int) -> tuple[list[ScriptAction], bool]:
    compact_cell = re.sub(r"\s+", "", cell.upper())
    normalized = INVALID_COMMAND_RE.sub("", compact_cell)
    has_extra_text = normalized != compact_cell
    if not normalized:
        return [], has_extra_text

    if COMMAND_ONLY_RE.fullmatch(normalized):
        return [
            ScriptAction(
                line_no=line_no,
                turn=turn,
                slot=slot,
                order=1,
                command=normalized[0],
                repeat=len(normalized),
                raw_cell=cell,
            )
        ], has_extra_text

    actions: list[ScriptAction] = []
    consumed_ranges: list[range] = []
    for match in ACTION_RUN_RE.finditer(normalized):
        order = int(match.group(1))
        commands = match.group(2)
        actions.append(
            ScriptAction(
                line_no=line_no,
                turn=turn,
                slot=slot,
                order=order,
                command=commands[0],
                repeat=len(commands),
                raw_cell=cell,
            )
        )
        consumed_ranges.append(range(match.start(), match.end()))

    consumed_indexes = {index for consumed in consumed_ranges for index in consumed}
    leftover = "".join(char for index, char in enumerate(normalized) if index not in consumed_indexes)
    return actions, has_extra_text or bool(leftover)


def check_turns(turns: list[ScriptTurn], report: BattleStaticReport) -> None:
    seen_turns: dict[int, int] = {}
    for turn in turns:
        if turn.turn <= 0:
            report.add_error("INVALID_TURN", f"回合号必须大于 0，当前为 {turn.turn}", f"line {turn.line_no}")

        if turn.turn in seen_turns:
            report.add_error(
                "DUPLICATE_TURN",
                f"第 {turn.turn} 回合重复出现，首次在第 {seen_turns[turn.turn]} 行",
                f"line {turn.line_no}",
            )
        else:
            seen_turns[turn.turn] = turn.line_no

        if not any(cell and cell != "-" for cell in turn.cells) and not turn.actions:
            report.add_warning("EMPTY_TURN", f"第 {turn.turn} 回合为空回合", f"line {turn.line_no}")

        orders: dict[int, ScriptAction] = {}
        for action in turn.actions:
            if action.order <= 0:
                report.add_error(
                    "INVALID_ACTION_ORDER",
                    f"动作序号必须大于 0，当前为 {action.order}",
                    f"line {action.line_no}",
                )
            if action.order in orders:
                prev = orders[action.order]
                report.add_warning(
                    "DUPLICATE_ACTION_ORDER",
                    f"第 {turn.turn} 回合动作序号 {action.order} 重复：第 {prev.slot} 列和第 {action.slot} 列",
                    f"line {action.line_no}",
                )
            else:
                orders[action.order] = action


def check_instructions(
    instructions_json: str,
    turns: list[ScriptTurn],
    report: BattleStaticReport,
) -> None:
    text = instructions_json.strip()
    if not text:
        return

    try:
        parsed = json.loads(text)
    except json.JSONDecodeError as exc:
        report.add_error("INSTRUCTIONS_JSON_INVALID", f"附加指令 JSON 解析失败：{exc}")
        return

    if not isinstance(parsed, list):
        report.add_error("INSTRUCTIONS_NOT_ARRAY", "附加指令必须是 JSON array")
        return

    report.instruction_count = len(parsed)
    turn_orders = {turn.turn: {action.order for action in turn.actions} for turn in turns}
    turn_numbers = set(turn_orders)

    for index, item in enumerate(parsed, start=1):
        location = f"instruction {index}"
        if not isinstance(item, dict):
            report.add_error("INSTRUCTION_NOT_OBJECT", "附加指令项必须是 JSON object", location)
            continue

        turn = read_int(item.get("turn"))
        step = read_int(item.get("step"))
        value = read_int(item.get("value"))
        instruction_type = item.get("type")

        if turn is None:
            report.add_error("INSTRUCTION_TURN_MISSING", "附加指令缺少合法 turn", location)
        if step is None:
            report.add_error("INSTRUCTION_STEP_MISSING", "附加指令缺少合法 step", location)
        if value is None:
            report.add_error("INSTRUCTION_VALUE_MISSING", "附加指令缺少合法 value", location)
        if not isinstance(instruction_type, str) or not instruction_type:
            report.add_error("INSTRUCTION_TYPE_MISSING", "附加指令缺少 type", location)
            continue
        if instruction_type not in ALLOWED_INSTRUCTION_TYPES:
            report.add_error("INSTRUCTION_TYPE_UNKNOWN", f"未知附加指令类型：{instruction_type}", location)
            continue

        if turn is None or step is None or value is None:
            continue

        if turn <= 0:
            report.add_error("INSTRUCTION_TURN_INVALID", f"{instruction_type} 的 turn 必须大于 0", location)
        if step < 0:
            report.add_error("INSTRUCTION_STEP_INVALID", f"{instruction_type} 的 step 不能小于 0", location)

        if instruction_type == "STAGE_AUTO_NAV":
            check_stage_auto_nav(value, report, location)
            if turn != 1:
                report.add_warning("INSTRUCTION_NORMALIZATION", "STAGE_AUTO_NAV 运行时会将 turn 归一为 1", location)
            if step != 0:
                report.add_warning("INSTRUCTION_NORMALIZATION", "STAGE_AUTO_NAV 运行时会将 step 归一为 0", location)
            continue

        if instruction_type in STEP_HIDDEN_TYPES and step != 0:
            report.add_warning(
                "INSTRUCTION_NORMALIZATION",
                f"{instruction_type} 运行时会将 step 归一为 0",
                location,
            )

        if turn_numbers and turn not in turn_numbers:
            report.add_warning(
                "INSTRUCTION_TURN_NOT_FOUND",
                f"{instruction_type} 指向第 {turn} 回合，但脚本文本里没有该回合",
                location,
            )

        if step > 0 and turn in turn_orders and step not in turn_orders[turn]:
            report.add_warning(
                "INSTRUCTION_STEP_NOT_FOUND",
                f"{instruction_type} 指向第 {turn} 回合动作 {step} 后，但该动作序号不存在",
                location,
            )

        check_instruction_value(instruction_type, value, report, location)


def check_instruction_value(
    instruction_type: str,
    value: int,
    report: BattleStaticReport,
    location: str,
) -> None:
    if instruction_type in {"DELAY_ADD", "DELAY_SUBTRACT"} and value <= 0:
        report.add_error("INSTRUCTION_VALUE_INVALID", f"{instruction_type} 的 value 必须大于 0", location)
    elif instruction_type == "DEATH_CHECK" and value not in {1, 2, 3, 4, 5}:
        report.add_error("INSTRUCTION_VALUE_INVALID", "DEATH_CHECK 的 value 必须是 1 到 5 号位", location)
    elif instruction_type in {"TARGET_SWITCH", "TARGET_SWITCH_LEFT", "TARGET_SWITCH_RIGHT"} and value <= 0:
        report.add_error("INSTRUCTION_VALUE_INVALID", f"{instruction_type} 的 value 必须大于 0", location)
    elif instruction_type in VALUELESS_TYPES and value != 0:
        report.add_warning("INSTRUCTION_VALUE_UNUSED", f"{instruction_type} 不使用 value，建议填 0", location)


def check_stage_auto_nav(value: int, report: BattleStaticReport, location: str) -> None:
    base_value = value
    if value >= STAGE_AUTO_NAV_CAVE_NEXT_FLOOR_FLAG:
        base_value = value - STAGE_AUTO_NAV_CAVE_NEXT_FLOOR_FLAG
        if base_value not in CAVE_TARGET_CODES:
            report.add_error(
                "STAGE_AUTO_NAV_VALUE_INVALID",
                "自动进入下一层标记只能用于洞窟左或洞窟右",
                location,
            )
            return

    if base_value not in STAGE_AUTO_NAV_TARGET_CODES:
        report.add_error("STAGE_AUTO_NAV_VALUE_INVALID", f"未知关卡自动导航 value：{value}", location)


def read_int(value: Any) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, float) and value.is_integer():
        return int(value)
    if isinstance(value, str):
        stripped = value.strip()
        if re.fullmatch(r"-?\d+", stripped):
            return int(stripped)
    return None

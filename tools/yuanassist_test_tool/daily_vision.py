from __future__ import annotations

import json
import re
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Any

from .daily_assets import ASSET_ROOT, DAILY_SCRIPT_DIR, as_clean_str, normalize_asset_dir


BASE_W = 1080.0
BASE_H = 1920.0
DEFAULT_THRESHOLD = 0.8
DEFAULT_STATUS_BAR_HEIGHT = 40
DEFAULT_DAILY_VISION_DIR = Path("tools/yuanassist_test_tool/fixtures/daily_vision")
GLOBAL_VISION_SCRIPT = "__all_daily_visual_units__"
TEMPLATE_EXTENSIONS = (".png", ".jpg", ".jpeg", ".webp")


@dataclass
class VisionNode:
    key: str
    kind: str
    script: str
    task_id: int
    name: str
    template_name: str = ""
    threshold: float = DEFAULT_THRESHOLD
    roi: dict[str, Any] | None = None
    target_text: str = ""
    target_chars: list[str] = field(default_factory=list)
    location: str = ""

    def label(self) -> str:
        return self.name or self.key

    def to_dict(self) -> dict[str, Any]:
        return {
            "key": self.key,
            "kind": self.kind,
            "script": self.script,
            "taskId": self.task_id,
            "name": self.name,
            "label": self.label(),
            "templateName": self.template_name,
            "threshold": self.threshold,
            "roi": self.roi,
            "targetText": self.target_text,
            "targetChars": self.target_chars,
            "location": self.location,
        }


@dataclass
class VisionExpectation:
    type: str
    node: str = ""
    node_name: str = ""
    template_name: str = ""
    min_score: float | None = None
    max_score: float | None = None
    expected_text: str = ""
    match: str = "contains"
    reason: str = ""


@dataclass
class VisionCase:
    id: str
    title: str
    script: str
    screenshot: Path
    case_file: Path
    display_width: int | None = None
    display_height: int | None = None
    raw_status_bar_height: int = DEFAULT_STATUS_BAR_HEIGHT
    warn_unexpected_hits: bool = False
    expectations: list[VisionExpectation] = field(default_factory=list)


@dataclass
class MatchObservation:
    node: VisionNode
    status: str
    score: float | None = None
    threshold: float = DEFAULT_THRESHOLD
    center: tuple[float, float] | None = None
    roi_rect: tuple[int, int, int, int] | None = None
    template_path: str = ""
    text: str = ""
    backend: str = ""
    message: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "node": self.node.key,
            "kind": self.node.kind,
            "name": self.node.name,
            "templateName": self.node.template_name,
            "status": self.status,
            "score": self.score,
            "threshold": self.threshold,
            "center": self.center,
            "roiRect": self.roi_rect,
            "templatePath": self.template_path,
            "text": self.text,
            "backend": self.backend,
            "message": self.message,
        }


@dataclass
class OcrResult:
    text: str
    confidence: float | None = None
    backend: str = ""


@dataclass
class VisionFinding:
    severity: str
    code: str
    message: str
    case_id: str = ""
    node: str = ""
    location: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "severity": self.severity,
            "code": self.code,
            "message": self.message,
            "caseId": self.case_id,
            "node": self.node,
            "location": self.location,
        }


@dataclass
class VisionCaseReport:
    case: VisionCase
    observations: list[MatchObservation] = field(default_factory=list)
    findings: list[VisionFinding] = field(default_factory=list)

    @property
    def errors(self) -> list[VisionFinding]:
        return [item for item in self.findings if item.severity == "ERROR"]

    @property
    def warnings(self) -> list[VisionFinding]:
        return [item for item in self.findings if item.severity == "WARN"]

    @property
    def skips(self) -> list[VisionFinding]:
        return [item for item in self.findings if item.severity == "SKIP"]

    def add(self, severity: str, code: str, message: str, *, node: str = "", location: str = "") -> None:
        self.findings.append(
            VisionFinding(
                severity=severity,
                code=code,
                message=message,
                case_id=self.case.id,
                node=node,
                location=location,
            )
        )

    def to_dict(self, include_observations: bool = True) -> dict[str, Any]:
        payload = {
            "id": self.case.id,
            "title": self.case.title,
            "script": self.case.script,
            "screenshot": self.case.screenshot.as_posix(),
            "summary": {
                "errors": len(self.errors),
                "warnings": len(self.warnings),
                "skips": len(self.skips),
                "observations": len(self.observations),
                "passed": not self.errors,
            },
            "findings": [item.to_dict() for item in self.findings],
        }
        if include_observations:
            payload["observations"] = [item.to_dict() for item in self.observations]
        return payload


@dataclass
class DailyVisionReport:
    cases: list[VisionCaseReport]
    project_root: Path

    @property
    def findings(self) -> list[VisionFinding]:
        items: list[VisionFinding] = []
        for case in self.cases:
            items.extend(case.findings)
        return items

    def summary(self) -> dict[str, int]:
        errors = sum(1 for item in self.findings if item.severity == "ERROR")
        warnings = sum(1 for item in self.findings if item.severity == "WARN")
        skips = sum(1 for item in self.findings if item.severity == "SKIP")
        return {
            "cases": len(self.cases),
            "passed": sum(1 for item in self.cases if not item.errors),
            "failed": sum(1 for item in self.cases if item.errors),
            "errors": errors,
            "warnings": warnings,
            "skips": skips,
            "observations": sum(len(item.observations) for item in self.cases),
        }

    def to_dict(self, include_observations: bool = True) -> dict[str, Any]:
        return {
            "summary": self.summary(),
            "items": [item.to_dict(include_observations=include_observations) for item in self.cases],
        }

    def to_text(self) -> str:
        summary = self.summary()
        lines = [
            "日常视觉回归汇总："
            f"{summary['cases']} 个 case，"
            f"{summary['passed']} 通过，"
            f"{summary['failed']} 失败，"
            f"{summary['errors']} 错误，"
            f"{summary['warnings']} 提醒，"
            f"{summary['skips']} 跳过"
        ]
        for case in self.cases:
            status = "失败" if case.errors else "通过"
            lines.append(
                f"- [{status}] {case.case.id}：{case.case.title or case.case.script}，"
                f"{len(case.observations)} 个观察结果"
            )
            for finding in case.findings[:10]:
                node = f" node={finding.node}" if finding.node else ""
                location = f" [{finding.location}]" if finding.location else ""
                lines.append(f"  {finding.severity} {finding.code}{node}{location}: {finding.message}")
        return "\n".join(lines)


def check_daily_vision(
    project_root: Path = Path("."),
    case_dir: Path = DEFAULT_DAILY_VISION_DIR,
    case_file: Path | None = None,
    expectation_warnings: bool = True,
) -> DailyVisionReport:
    root = project_root.resolve()
    cases = load_cases(root, case_dir, case_file)
    reports = [check_vision_case(root, case, expectation_warnings=expectation_warnings) for case in cases]
    if not reports:
        empty_case = VisionCase(
            id="daily_vision_empty",
            title="未找到视觉回归 case",
            script="",
            screenshot=case_dir,
            case_file=case_dir,
        )
        report = VisionCaseReport(empty_case)
        report.add("WARN", "VISION_CASES_EMPTY", f"未找到 case.json：{case_dir}")
        reports.append(report)
    return DailyVisionReport(reports, root)


def check_daily_vision_case_files(
    project_root: Path = Path("."),
    case_files: list[Path] | None = None,
    expectation_warnings: bool = True,
) -> DailyVisionReport:
    root = project_root.resolve()
    reports = [
        check_vision_case(root, load_case(resolve_path(root, path), root), expectation_warnings=expectation_warnings)
        for path in (case_files or [])
    ]
    return DailyVisionReport(reports, root)


def list_daily_vision_scripts(project_root: Path = Path(".")) -> list[dict[str, Any]]:
    root = project_root.resolve()
    script_dir = root / DAILY_SCRIPT_DIR
    if not script_dir.exists():
        return []

    items: list[dict[str, Any]] = []
    global_nodes = collect_global_visual_nodes(root)
    if global_nodes:
        global_display_nodes = build_display_node_dicts(global_nodes)
        items.append(
            {
                "script": GLOBAL_VISION_SCRIPT,
                "displayName": "全部视觉单位",
                "assetTemplateDir": "",
                "visualNodes": len(global_display_nodes),
                "rawVisualNodes": len(global_nodes),
                "templateNodes": sum(1 for node in global_display_nodes if node.get("kind") == "template"),
                "rawTemplateNodes": sum(1 for node in global_nodes if node.kind == "template"),
                "ocrNodes": sum(1 for node in global_display_nodes if node.get("kind") == "ocr"),
                "rawOcrNodes": sum(1 for node in global_nodes if node.kind == "ocr"),
                "nodes": global_display_nodes,
            }
        )

    for path in sorted(script_dir.glob("*.json"), key=lambda item: item.name.lower()):
        try:
            data = read_json_object(path)
        except ValueError:
            continue
        nodes = collect_visual_nodes(data, path.name)
        if not nodes:
            continue
        display_nodes = build_display_node_dicts(nodes)
        items.append(
            {
                "script": path.name,
                "displayName": as_clean_str(data.get("display_name")) or path.stem,
                "assetTemplateDir": normalize_asset_dir(as_clean_str(data.get("asset_template_dir"))),
                "visualNodes": len(display_nodes),
                "rawVisualNodes": len(nodes),
                "templateNodes": sum(1 for node in display_nodes if node.get("kind") == "template"),
                "rawTemplateNodes": sum(1 for node in nodes if node.kind == "template"),
                "ocrNodes": sum(1 for node in display_nodes if node.get("kind") == "ocr"),
                "rawOcrNodes": sum(1 for node in nodes if node.kind == "ocr"),
                "nodes": display_nodes,
            }
        )
    return items


def collect_global_template_nodes(project_root: Path = Path(".")) -> list[VisionNode]:
    return [node for node in collect_global_visual_nodes(project_root) if node.kind == "template"]


def collect_global_visual_nodes(project_root: Path = Path(".")) -> list[VisionNode]:
    root = project_root.resolve()
    script_dir = root / DAILY_SCRIPT_DIR
    if not script_dir.exists():
        return []

    nodes: list[VisionNode] = []
    for path in sorted(script_dir.glob("*.json"), key=lambda item: item.name.lower()):
        try:
            data = read_json_object(path)
        except ValueError:
            continue
        asset_dir = normalize_asset_dir(as_clean_str(data.get("asset_template_dir")))
        for node in collect_visual_nodes(data, path.name):
            template_name = node.template_name
            if node.kind == "template":
                template_name = normalize_global_template_name(node.template_name, asset_dir)
            nodes.append(
                replace(
                    node,
                    key=f"{path.name}:{node.key}",
                    template_name=template_name,
                    location=f"{path.name} · {node.location}",
                )
            )
    return nodes


def normalize_global_template_name(template_name: str, asset_dir: str) -> str:
    normalized = template_name.replace("\\", "/").strip()
    if not normalized:
        return ""
    if "/" in normalized:
        return normalized
    if asset_dir:
        return f"{asset_dir}/{normalized}"
    return normalized


def build_display_node_dicts(nodes: list[VisionNode]) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    seen_units: dict[str, int] = {}
    for node in nodes:
        dedupe_key = vision_node_unit_key(node)
        if dedupe_key and dedupe_key in seen_units:
            item = items[seen_units[dedupe_key]]
            item["duplicateCount"] = int(item.get("duplicateCount", 1)) + 1
            item.setdefault("duplicateNodes", []).append(node.key)
            duplicate_labels = item.setdefault("duplicateLabels", [])
            duplicate_labels.append(node.label())
            continue
        item = node.to_dict()
        item["unitKey"] = dedupe_key
        item["unitKind"] = node.kind
        item["unitValue"] = vision_node_unit_value(node)
        item["duplicateCount"] = 1
        item["duplicateNodes"] = [node.key]
        item["duplicateLabels"] = [node.label()]
        if dedupe_key:
            seen_units[dedupe_key] = len(items)
        items.append(item)
    return items


def vision_node_unit_key(node: VisionNode) -> str:
    if node.kind == "template":
        return template_roi_key(node)
    if node.kind == "ocr":
        return ocr_roi_key(node)
    return ""


def vision_node_unit_value(node: VisionNode) -> str:
    if node.kind == "template":
        return node.template_name.replace("\\", "/").strip()
    if node.kind == "ocr":
        return ocr_text_value(node)
    return ""


def template_roi_key(node: VisionNode) -> str:
    template = node.template_name.replace("\\", "/").strip().lower()
    roi = json.dumps(node.roi or {}, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return f"{template}|{roi}"


def ocr_roi_key(node: VisionNode) -> str:
    text = ocr_text_value(node)
    if not text:
        text = f"__node__:{node.key}"
    roi = json.dumps(node.roi or {}, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return f"{text}|{roi}"


def ocr_text_value(node: VisionNode) -> str:
    return node.target_text or "".join(node.target_chars)


def load_cases(project_root: Path, case_dir: Path, case_file: Path | None) -> list[VisionCase]:
    if case_file is not None:
        path = resolve_path(project_root, case_file)
        return [load_case(path, project_root)]

    base = resolve_path(project_root, case_dir)
    if not base.exists():
        return []
    return [load_case(path, project_root) for path in sorted(base.rglob("case.json"), key=lambda item: item.as_posix())]


def load_case(path: Path, project_root: Path) -> VisionCase:
    data = read_json_object(path)
    case_id = as_clean_str(data.get("id")) or path.parent.name
    script = normalize_script_name(as_clean_str(data.get("script")))
    screenshot_value = as_clean_str(data.get("screenshot")) or "screenshot.png"
    screenshot = (path.parent / screenshot_value).resolve()
    display = data.get("display") if isinstance(data.get("display"), dict) else {}
    raw_expectations = data.get("expectations", data.get("expects", []))
    expectations = [
        VisionExpectation(
            type=as_clean_str(item.get("type")).lower(),
            node=as_clean_str(item.get("node")),
            node_name=as_clean_str(item.get("node_name", item.get("name"))),
            template_name=as_clean_str(item.get("template_name")),
            min_score=as_float_or_none(item.get("min_score")),
            max_score=as_float_or_none(item.get("max_score")),
            expected_text=as_clean_str(item.get("expected_text", item.get("text"))),
            match=as_clean_str(item.get("match")) or "contains",
            reason=as_clean_str(item.get("reason")),
        )
        for item in raw_expectations
        if isinstance(item, dict)
    ]
    return VisionCase(
        id=case_id,
        title=as_clean_str(data.get("title")),
        script=script,
        screenshot=screenshot,
        case_file=path,
        display_width=as_int_or_none(display.get("width")),
        display_height=as_int_or_none(display.get("height")),
        raw_status_bar_height=as_int_or_none(display.get("raw_status_bar_height")) or DEFAULT_STATUS_BAR_HEIGHT,
        warn_unexpected_hits=bool(data.get("warn_unexpected_hits", data.get("scan_unexpected", False))),
        expectations=expectations,
    )


def check_vision_case(project_root: Path, case: VisionCase, *, expectation_warnings: bool = True) -> VisionCaseReport:
    report = VisionCaseReport(case)
    if not case.script:
        report.add("ERROR", "SCRIPT_MISSING", "case 缺少 script")
        return report
    if not case.screenshot.exists():
        report.add("ERROR", "SCREENSHOT_MISSING", f"截图不存在：{case.screenshot}", location=str(case.case_file))
        return report

    if case.script == GLOBAL_VISION_SCRIPT:
        script_data: dict[str, Any] = {}
        nodes = collect_global_visual_nodes(project_root)
    else:
        script_path = project_root / DAILY_SCRIPT_DIR / case.script
        if not script_path.exists():
            report.add("ERROR", "SCRIPT_FILE_MISSING", f"脚本不存在：{case.script}", location=str(script_path))
            return report

        try:
            script_data = read_json_object(script_path)
        except ValueError as exc:
            report.add("ERROR", "SCRIPT_JSON_INVALID", str(exc), location=str(script_path))
            return report

        nodes = collect_visual_nodes(script_data, case.script)
    node_by_key = {node.key: node for node in nodes}

    template_nodes = [node for node in nodes if node.kind == "template"]
    ocr_nodes = [node for node in nodes if node.kind == "ocr"]
    template_observations = run_template_observations(project_root, case, script_data, template_nodes, report)
    ocr_observations = run_ocr_observations(project_root, case, ocr_nodes, report)
    observations = template_observations + ocr_observations
    report.observations.extend(observations)
    observation_by_key = {item.node.key: item for item in observations}

    expected_hit_keys = set()
    expected_miss_keys = set()
    ignored_keys = set()
    expected_template_units = set()
    for expectation in case.expectations:
        node = resolve_expected_node(expectation, nodes)
        if node is None:
            report.add(
                "ERROR",
                "EXPECT_NODE_MISSING",
                f"找不到期望节点：{describe_expectation(expectation)}",
            )
            continue
        if expectation.type in {"hit", "match"}:
            expected_hit_keys.add(node.key)
            if node.kind == "template":
                expected_template_units.add(template_roi_key(node))
            check_expected_hit(report, expectation, node, observation_by_key.get(node.key))
        elif expectation.type in {"miss", "not_match", "no_match"}:
            expected_miss_keys.add(node.key)
            if node.kind == "template":
                expected_template_units.add(template_roi_key(node))
            check_expected_miss(report, expectation, node, observation_by_key.get(node.key))
        elif expectation.type == "ignore":
            ignored_keys.add(node.key)
            if node.kind == "template":
                expected_template_units.add(template_roi_key(node))
        elif expectation.type == "ocr":
            report.add(
                "WARN",
                "EXPECT_OCR_TYPE_LEGACY",
                "OCR 期望类型已并入 hit/miss；请把 type=ocr 改为 type=hit 并保留 expected_text",
                node=node.key,
            )
            check_expected_ocr(report, expectation, node, observation_by_key.get(node.key))
        else:
            report.add(
                "WARN",
                "EXPECT_TYPE_UNSUPPORTED",
                f"暂不支持的期望类型：{expectation.type}",
                node=node.key,
            )

    if expectation_warnings and case.warn_unexpected_hits:
        expected_keys = expected_hit_keys | expected_miss_keys | ignored_keys
        warned_units = set()
        for observation in observations:
            if observation.node.key in expected_keys:
                continue
            unit_key = template_roi_key(observation.node)
            if unit_key in expected_template_units or unit_key in warned_units:
                continue
            if observation.status == "hit":
                warned_units.add(unit_key)
                report.add(
                    "WARN",
                    "UNEXPECTED_TEMPLATE_HIT",
                    f"未声明期望的模板也命中：{observation.node.label()}，分数={format_score(observation.score)}",
                    node=observation.node.key,
                )

    if expectation_warnings and not case.expectations:
        report.add("WARN", "EXPECTATIONS_EMPTY", "case 没有 expectations，只输出观察结果")

    unknown_keys = set(node_by_key) - {item.node.key for item in observations}
    if unknown_keys and not template_nodes and not ocr_nodes:
        report.add("SKIP", "VISION_NODES_EMPTY", "脚本中没有可离线检查的视觉节点")

    return report


def collect_visual_nodes(script_data: dict[str, Any], script_name: str) -> list[VisionNode]:
    tasks = script_data.get("tasks")
    if not isinstance(tasks, list):
        return []
    nodes: list[VisionNode] = []
    for task in tasks:
        if not isinstance(task, dict):
            continue
        task_id = task.get("id")
        if not isinstance(task_id, int):
            continue
        action = as_clean_str(task.get("action")).upper()
        params = task.get("params") if isinstance(task.get("params"), dict) else {}
        task_name = as_clean_str(task.get("name"))
        if action == "MATCH_TEMPLATE":
            nodes.append(
                VisionNode(
                    key=f"task:{task_id}",
                    kind="template",
                    script=script_name,
                    task_id=task_id,
                    name=task_name,
                    template_name=as_clean_str(params.get("template_name")),
                    threshold=as_float_or_none(params.get("threshold")) or DEFAULT_THRESHOLD,
                    roi=as_dict_or_none(params.get("roi")),
                    location=f"task {task_id}",
                )
            )
        elif action == "OCR":
            nodes.append(
                VisionNode(
                    key=f"task:{task_id}",
                    kind="ocr",
                    script=script_name,
                    task_id=task_id,
                    name=task_name,
                    template_name=as_clean_str(params.get("template_name")),
                    threshold=as_float_or_none(params.get("threshold")) or DEFAULT_THRESHOLD,
                    roi=as_dict_or_none(params.get("roi")),
                    target_text=as_clean_str(params.get("target_text", params.get("button_name"))),
                    target_chars=as_text_list(params.get("target_chars")),
                    location=f"task {task_id}",
                )
            )
        elif action == "SCREENSHOT_GROUP":
            steps = params.get("screenshot_steps")
            if not isinstance(steps, list):
                continue
            group_roi = as_dict_or_none(params.get("roi"))
            group_threshold = as_float_or_none(params.get("threshold")) or DEFAULT_THRESHOLD
            for index, step in enumerate(steps):
                if not isinstance(step, dict):
                    continue
                step_type = as_clean_str(step.get("type")).upper()
                step_name = as_clean_str(step.get("name")) or task_name
                key = f"task:{task_id}:step:{index}"
                if step_type in {"TEMPLATE", "MATCH_TEMPLATE"}:
                    nodes.append(
                        VisionNode(
                            key=key,
                            kind="template",
                            script=script_name,
                            task_id=task_id,
                            name=step_name,
                            template_name=as_clean_str(step.get("template_name")),
                            threshold=as_float_or_none(step.get("threshold")) or group_threshold,
                            roi=as_dict_or_none(step.get("roi")) or group_roi,
                            location=f"task {task_id} screenshot_steps[{index}]",
                        )
                    )
                elif step_type == "OCR":
                    nodes.append(
                        VisionNode(
                            key=key,
                            kind="ocr",
                            script=script_name,
                            task_id=task_id,
                            name=step_name,
                            threshold=as_float_or_none(step.get("threshold")) or group_threshold,
                            roi=as_dict_or_none(step.get("roi")) or group_roi,
                            target_text=as_clean_str(step.get("target_text", step.get("button_name"))),
                            target_chars=as_text_list(step.get("target_chars")),
                            location=f"task {task_id} screenshot_steps[{index}]",
                        )
                    )
    return nodes


def run_template_observations(
    project_root: Path,
    case: VisionCase,
    script_data: dict[str, Any],
    nodes: list[VisionNode],
    report: VisionCaseReport,
) -> list[MatchObservation]:
    try:
        import cv2  # type: ignore
        import numpy as np  # noqa: F401
    except ImportError as exc:
        report.add("SKIP", "VISION_BACKEND_MISSING", f"缺少 OpenCV/numpy，跳过模板匹配：{exc}")
        return []

    screenshot = read_cv_image(cv2, np, case.screenshot)
    if screenshot is None:
        report.add("ERROR", "SCREENSHOT_READ_FAILED", f"无法读取截图：{case.screenshot}")
        return []

    screenshot_h, screenshot_w = screenshot.shape[:2]
    display_w = case.display_width or screenshot_w
    display_h = case.display_height or screenshot_h
    asset_dir = normalize_asset_dir(as_clean_str(script_data.get("asset_template_dir")))
    template_base = project_root / ASSET_ROOT / asset_dir if asset_dir else project_root / ASSET_ROOT
    observations: list[MatchObservation] = []

    for node in nodes:
        threshold = node.threshold
        if not node.template_name:
            observations.append(MatchObservation(node=node, status="error", threshold=threshold, message="节点缺少 template_name"))
            continue
        template_path = resolve_template_path(project_root, template_base, node.template_name)
        if template_path is None:
            observations.append(
                MatchObservation(
                    node=node,
                    status="error",
                    threshold=threshold,
                    message=f"模板文件不存在：{node.template_name}",
                )
            )
            continue
        template = read_cv_image(cv2, np, template_path)
        if template is None:
            observations.append(
                MatchObservation(
                    node=node,
                    status="error",
                    threshold=threshold,
                    template_path=template_path.relative_to(project_root).as_posix(),
                    message=f"无法读取模板：{template_path}",
                )
            )
            continue

        game_scale = min(screenshot_w / BASE_W, screenshot_h / BASE_H)
        scaled_w = max(1, int(template.shape[1] * game_scale))
        scaled_h = max(1, int(template.shape[0] * game_scale))
        if (scaled_w, scaled_h) != (template.shape[1], template.shape[0]):
            template = cv2.resize(template, (scaled_w, scaled_h), interpolation=cv2.INTER_LINEAR)

        roi_rect = build_roi_rect(
            node.roi,
            screenshot_w=screenshot_w,
            screenshot_h=screenshot_h,
            display_w=display_w,
            display_h=display_h,
            raw_status_bar_height=case.raw_status_bar_height,
        )
        x, y, w, h = roi_rect
        search = screenshot[y : y + h, x : x + w]
        if search.shape[1] < template.shape[1] or search.shape[0] < template.shape[0]:
            observations.append(
                MatchObservation(
                    node=node,
                    status="miss",
                    threshold=threshold,
                    roi_rect=roi_rect,
                    template_path=template_path.relative_to(project_root).as_posix(),
                    message="ROI 小于模板",
                )
            )
            continue

        result = cv2.matchTemplate(search, template, cv2.TM_CCOEFF_NORMED)
        _, max_val, _, max_loc = cv2.minMaxLoc(result)
        center = (x + max_loc[0] + template.shape[1] / 2.0, y + max_loc[1] + template.shape[0] / 2.0)
        status = "hit" if max_val >= threshold else "miss"
        observations.append(
            MatchObservation(
                node=node,
                status=status,
                score=float(max_val),
                threshold=threshold,
                center=center,
                roi_rect=roi_rect,
                template_path=template_path.relative_to(project_root).as_posix(),
            )
        )

    return observations


def run_ocr_observations(
    project_root: Path,
    case: VisionCase,
    nodes: list[VisionNode],
    report: VisionCaseReport,
) -> list[MatchObservation]:
    if not nodes:
        return []

    try:
        import cv2  # type: ignore
        import numpy as np  # type: ignore
    except ImportError as exc:
        report.add("SKIP", "VISION_BACKEND_MISSING", f"缺少 OpenCV/numpy，跳过 OCR：{exc}")
        return []

    backend, backend_error = resolve_ocr_backend()
    if backend is None:
        report.add("SKIP", "OCR_BACKEND_MISSING", backend_error or "未找到可用 OCR 后端")
        return [
            MatchObservation(
                node=node,
                status="skip",
                threshold=node.threshold,
                backend="",
                message=backend_error or "未找到可用 OCR 后端",
            )
            for node in nodes
        ]

    screenshot = read_cv_image(cv2, np, case.screenshot)
    if screenshot is None:
        report.add("ERROR", "SCREENSHOT_READ_FAILED", f"无法读取截图：{case.screenshot}")
        return []

    screenshot_h, screenshot_w = screenshot.shape[:2]
    display_w = case.display_width or screenshot_w
    display_h = case.display_height or screenshot_h
    observations: list[MatchObservation] = []
    for node in nodes:
        roi_rect = build_roi_rect(
            node.roi,
            screenshot_w=screenshot_w,
            screenshot_h=screenshot_h,
            display_w=display_w,
            display_h=display_h,
            raw_status_bar_height=case.raw_status_bar_height,
        )
        x, y, w, h = roi_rect
        crop = screenshot[y : y + h, x : x + w]
        try:
            result = backend(crop)
        except Exception as exc:  # OCR engines often fail because of local model/runtime setup.
            observations.append(
                MatchObservation(
                    node=node,
                    status="error",
                    threshold=node.threshold,
                    roi_rect=roi_rect,
                    message=f"OCR 执行失败：{exc}",
                )
            )
            continue
        expected = ocr_text_value(node)
        status = "ocr"
        if expected:
            status = "hit" if ocr_text_matches(result.text, expected, "contains") else "miss"
        observations.append(
            MatchObservation(
                node=node,
                status=status,
                score=result.confidence,
                threshold=node.threshold,
                roi_rect=roi_rect,
                text=result.text,
                backend=result.backend,
                message="已执行 OCR" if result.text else "OCR 未识别到文本",
            )
        )
    return observations


def resolve_ocr_backend() -> tuple[CallableOcr | None, str]:
    backend = resolve_pytesseract_backend()
    if backend is not None:
        return backend, ""
    backend = resolve_paddleocr_backend()
    if backend is not None:
        return backend, ""
    return None, "未找到可用 OCR 后端；可安装 pytesseract + Tesseract，或安装并配置 paddleocr 后重试"


CallableOcr = Any


def resolve_pytesseract_backend() -> CallableOcr | None:
    try:
        import cv2  # type: ignore
        import pytesseract  # type: ignore
        from PIL import Image  # type: ignore
    except ImportError:
        return None

    def run(crop: Any) -> OcrResult:
        rgb = cv2.cvtColor(crop, cv2.COLOR_BGR2RGB)
        image = Image.fromarray(rgb)
        text = pytesseract.image_to_string(image, lang="chi_sim+eng")
        return OcrResult(text=normalize_ocr_output(text), backend="pytesseract")

    return run


_PADDLE_OCR_INSTANCE: Any | None = None


def resolve_paddleocr_backend() -> CallableOcr | None:
    try:
        import cv2  # type: ignore
        from paddleocr import PaddleOCR  # type: ignore
    except ImportError:
        return None

    def get_instance() -> Any:
        global _PADDLE_OCR_INSTANCE
        if _PADDLE_OCR_INSTANCE is None:
            try:
                _PADDLE_OCR_INSTANCE = PaddleOCR(use_angle_cls=True, lang="ch", show_log=False)
            except TypeError:
                _PADDLE_OCR_INSTANCE = PaddleOCR(use_angle_cls=True, lang="ch")
        return _PADDLE_OCR_INSTANCE

    def run(crop: Any) -> OcrResult:
        rgb = cv2.cvtColor(crop, cv2.COLOR_BGR2RGB)
        raw = get_instance().ocr(rgb, cls=True)
        texts, scores = extract_paddle_texts(raw)
        confidence = sum(scores) / len(scores) if scores else None
        return OcrResult(text=normalize_ocr_output("".join(texts)), confidence=confidence, backend="paddleocr")

    return run


def extract_paddle_texts(raw: Any) -> tuple[list[str], list[float]]:
    texts: list[str] = []
    scores: list[float] = []

    def walk(value: Any) -> None:
        if isinstance(value, tuple) and value and isinstance(value[0], str):
            texts.append(value[0])
            if len(value) > 1 and isinstance(value[1], int | float):
                scores.append(float(value[1]))
            return
        if isinstance(value, list):
            for item in value:
                walk(item)

    walk(raw)
    return texts, scores


def read_cv_image(cv2_module: Any, np_module: Any, path: Path) -> Any:
    try:
        data = np_module.fromfile(str(path), dtype=np_module.uint8)
    except OSError:
        return None
    if data.size == 0:
        return None
    return cv2_module.imdecode(data, cv2_module.IMREAD_COLOR)


def check_expected_hit(
    report: VisionCaseReport,
    expectation: VisionExpectation,
    node: VisionNode,
    observation: MatchObservation | None,
) -> None:
    if node.kind == "ocr":
        check_expected_ocr(report, expectation, node, observation)
        return
    if observation is None:
        report.add("ERROR", "EXPECTED_HIT_NOT_OBSERVED", "期望命中，但没有观察结果", node=node.key)
        return
    min_score = expectation.min_score if expectation.min_score is not None else node.threshold
    if observation.status != "hit" or observation.score is None or observation.score < min_score:
        report.add(
            "ERROR",
            "EXPECTED_HIT_FAILED",
            f"期望命中 {node.label()}，实际分数={format_score(observation.score)}，要求>={min_score}",
            node=node.key,
        )


def check_expected_miss(
    report: VisionCaseReport,
    expectation: VisionExpectation,
    node: VisionNode,
    observation: MatchObservation | None,
) -> None:
    if node.kind == "ocr":
        check_expected_ocr_miss(report, expectation, node, observation)
        return
    if observation is None:
        report.add("ERROR", "EXPECTED_MISS_NOT_OBSERVED", "期望不命中，但没有观察结果", node=node.key)
        return
    max_score = expectation.max_score if expectation.max_score is not None else node.threshold
    if observation.score is not None and observation.score >= max_score:
        report.add(
            "ERROR",
            "EXPECTED_MISS_FAILED",
            f"期望不命中 {node.label()}，实际分数={format_score(observation.score)}，要求<{max_score}",
            node=node.key,
        )


def check_expected_ocr(
    report: VisionCaseReport,
    expectation: VisionExpectation,
    node: VisionNode,
    observation: MatchObservation | None,
) -> None:
    if node.kind != "ocr":
        report.add("WARN", "EXPECT_OCR_ON_TEMPLATE", "OCR 期望绑定到了非 OCR 节点", node=node.key)
        return
    expected_text = expectation.expected_text or node.target_text or "".join(node.target_chars)
    if observation is None:
        report.add("ERROR", "EXPECTED_OCR_NOT_OBSERVED", "期望 OCR 命中，但没有观察结果", node=node.key)
        return
    if observation.status == "skip":
        message = observation.message or "OCR 节点暂未接入离线识别后端"
        if expected_text:
            message += f"，已记录期望文本：{expected_text}"
        report.add("SKIP", "OCR_BACKEND_MISSING", message, node=node.key)
        return
    if observation.status == "error":
        report.add("ERROR", "OCR_EXECUTION_FAILED", observation.message or "OCR 执行失败", node=node.key)
        return
    if not expected_text:
        report.add("WARN", "OCR_EXPECTED_TEXT_MISSING", "OCR 期望缺少 expected_text，且脚本节点也没有目标文字", node=node.key)
        return
    if not ocr_text_matches(observation.text, expected_text, expectation.match):
        report.add(
            "ERROR",
            "EXPECTED_OCR_FAILED",
            f"期望 OCR 文本 {expected_text}，实际识别：{observation.text or '空'}",
            node=node.key,
        )


def check_expected_ocr_miss(
    report: VisionCaseReport,
    expectation: VisionExpectation,
    node: VisionNode,
    observation: MatchObservation | None,
) -> None:
    expected_text = expectation.expected_text or node.target_text or "".join(node.target_chars)
    if observation is None:
        report.add("ERROR", "EXPECTED_OCR_MISS_NOT_OBSERVED", "期望 OCR 不命中，但没有观察结果", node=node.key)
        return
    if observation.status == "skip":
        report.add("SKIP", "OCR_BACKEND_MISSING", observation.message or "OCR 节点暂未接入离线识别后端", node=node.key)
        return
    if observation.status == "error":
        report.add("ERROR", "OCR_EXECUTION_FAILED", observation.message or "OCR 执行失败", node=node.key)
        return
    if expected_text and ocr_text_matches(observation.text, expected_text, expectation.match):
        report.add(
            "ERROR",
            "EXPECTED_OCR_MISS_FAILED",
            f"期望 OCR 不出现 {expected_text}，实际识别：{observation.text or '空'}",
            node=node.key,
        )


def resolve_expected_node(expectation: VisionExpectation, nodes: list[VisionNode]) -> VisionNode | None:
    if expectation.node:
        for node in nodes:
            if node.key == expectation.node:
                return node
    if expectation.node_name:
        for node in nodes:
            if node.name == expectation.node_name:
                return node
    if expectation.template_name:
        normalized = expectation.template_name.replace("\\", "/")
        for node in nodes:
            if node.template_name.replace("\\", "/") == normalized:
                return node
    return None


def build_roi_rect(
    roi: dict[str, Any] | None,
    *,
    screenshot_w: int,
    screenshot_h: int,
    display_w: int,
    display_h: int,
    raw_status_bar_height: int,
) -> tuple[int, int, int, int]:
    if not roi:
        return (0, 0, screenshot_w, screenshot_h)
    if as_float_or_none(roi.get("w")) == 0 and as_float_or_none(roi.get("h")) == 0:
        return (0, 0, screenshot_w, screenshot_h)

    game_scale = min(screenshot_w / BASE_W, screenshot_h / BASE_H)
    game_width = BASE_W * game_scale
    offset_x = (screenshot_w - game_width) / 2.0
    align = as_clean_str(roi.get("align")) or "center"

    if align == "dynamic_avatar_bounds":
        top = int(1254.0 * game_scale)
        bottom = int(screenshot_h - (313.0 * game_scale))
        return clamp_rect(0, top, screenshot_w, bottom - top, screenshot_w, screenshot_h)
    if align == "dynamic_filter_bounds":
        center_y = 1254.0 * game_scale
        center_x = offset_x + (game_width * 0.9)
        return rect_from_center(center_x, center_y, game_width * 0.2, 300.0 * game_scale, screenshot_w, screenshot_h)

    x_value = as_float_or_none(roi.get("x"))
    y_value = as_float_or_none(roi.get("y"))
    w_value = as_float_or_none(roi.get("w"))
    h_value = as_float_or_none(roi.get("h"))
    if x_value is not None and y_value is not None and w_value is not None and h_value is not None:
        real_x, real_y = calculate_real_coordinate(x_value, y_value, align, display_w, display_h, raw_status_bar_height)
        screenshot_x = real_x * (screenshot_w / display_w)
        screenshot_y = real_y * (screenshot_h / display_h)
        return rect_from_center(
            screenshot_x,
            screenshot_y,
            w_value * game_scale,
            h_value * game_scale,
            screenshot_w,
            screenshot_h,
        )

    center_x = as_float_or_none(roi.get("centerX"))
    center_y = as_float_or_none(roi.get("centerY"))
    radius = as_float_or_none(roi.get("radius"))
    if center_x is not None and center_y is not None and radius is not None:
        real_x, real_y = calculate_real_coordinate(center_x, center_y, align, display_w, display_h, raw_status_bar_height)
        screenshot_x = real_x * (screenshot_w / display_w)
        screenshot_y = real_y * (screenshot_h / display_h)
        return rect_from_center(screenshot_x, screenshot_y, radius * 2 * game_scale, radius * 2 * game_scale, screenshot_w, screenshot_h)

    return (0, 0, screenshot_w, screenshot_h)


def calculate_real_coordinate(
    base_x: float,
    base_y: float,
    align: str,
    display_w: int,
    display_h: int,
    raw_status_bar_height: int,
) -> tuple[float, float]:
    game_scale = min(display_w / BASE_W, display_h / BASE_H)
    game_width = BASE_W * game_scale
    game_height = BASE_H * game_scale
    offset_x = (display_w - game_width) / 2.0
    offset_y = (display_h - game_height) / 2.0
    status_bar_height = raw_status_bar_height / 2.0
    real_x = offset_x + (base_x * game_scale)
    normalized_align = align.lower()
    if normalized_align == "absolute":
        real_y = base_y * (display_h / BASE_H)
    elif normalized_align == "top":
        real_y = status_bar_height + (base_y * game_scale)
    elif normalized_align == "bottom":
        real_y = display_h - ((BASE_H - base_y) * game_scale)
    else:
        real_y = offset_y + (base_y * game_scale)
    return real_x, real_y


def rect_from_center(cx: float, cy: float, width: float, height: float, max_w: int, max_h: int) -> tuple[int, int, int, int]:
    left = int(cx - width / 2.0)
    top = int(cy - height / 2.0)
    return clamp_rect(left, top, int(width), int(height), max_w, max_h)


def clamp_rect(left: int, top: int, width: int, height: int, max_w: int, max_h: int) -> tuple[int, int, int, int]:
    safe_left = max(0, min(left, max_w - 1))
    safe_top = max(0, min(top, max_h - 1))
    safe_w = max(1, min(width, max_w - safe_left))
    safe_h = max(1, min(height, max_h - safe_top))
    return safe_left, safe_top, safe_w, safe_h


def resolve_template_path(project_root: Path, template_base: Path, template_name: str) -> Path | None:
    normalized = template_name.replace("\\", "/")
    if "/" in normalized:
        candidate = project_root / ASSET_ROOT / normalized
    else:
        candidate = template_base / normalized
    if candidate.exists():
        return candidate
    if Path(normalized).suffix:
        return None
    for suffix in TEMPLATE_EXTENSIONS:
        item = candidate.with_name(f"{candidate.name}{suffix}")
        if item.exists():
            return item
    return None


def read_json_object(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"JSON 解析失败：{path} ({exc})") from exc
    if not isinstance(data, dict):
        raise ValueError(f"JSON 顶层必须是 object：{path}")
    return data


def resolve_path(project_root: Path, path: Path) -> Path:
    if path.is_absolute():
        return path.resolve()
    return (project_root / path).resolve()


def normalize_script_name(value: str) -> str:
    if not value:
        return ""
    if value == GLOBAL_VISION_SCRIPT:
        return value
    return value if value.lower().endswith(".json") else f"{value}.json"


def as_dict_or_none(value: Any) -> dict[str, Any] | None:
    return value if isinstance(value, dict) else None


def as_text_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [as_clean_str(item) for item in value if as_clean_str(item)]


def as_float_or_none(value: Any) -> float | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int | float):
        return float(value)
    if isinstance(value, str):
        try:
            return float(value)
        except ValueError:
            return None
    return None


def as_int_or_none(value: Any) -> int | None:
    number = as_float_or_none(value)
    return int(number) if number is not None else None


def normalize_ocr_output(value: str) -> str:
    return re.sub(r"\s+", "", value or "")


def ocr_text_matches(actual: str, expected: str, match: str) -> bool:
    actual_text = normalize_ocr_output(actual)
    expected_text = normalize_ocr_output(expected)
    if not expected_text:
        return False
    normalized_match = (match or "contains").lower()
    if normalized_match in {"exact", "equals", "equal"}:
        return actual_text == expected_text
    if normalized_match in {"regex", "regexp"}:
        try:
            return re.search(expected, actual or "") is not None
        except re.error:
            return False
    return expected_text in actual_text


def describe_expectation(expectation: VisionExpectation) -> str:
    parts = [
        f"type={expectation.type}" if expectation.type else "",
        f"node={expectation.node}" if expectation.node else "",
        f"name={expectation.node_name}" if expectation.node_name else "",
        f"template={expectation.template_name}" if expectation.template_name else "",
    ]
    return ", ".join(part for part in parts if part) or "<empty>"


def format_score(value: float | None) -> str:
    return "无" if value is None else f"{value:.4f}"

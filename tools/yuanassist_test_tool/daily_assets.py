from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


DAILY_SCRIPT_DIR = Path("app/src/main/assets/daily_scripts")
DAILY_TEMPLATE_ROOT = Path("app/src/main/assets/daily_script_templates")
ASSET_ROOT = Path("app/src/main/assets")
IGNORED_DAILY_FILES = {"levels.json"}
SPECIAL_TRANSITIONS = {-1, -2, -3, -4}
VALID_ACTIONS = {
    "BACK",
    "CLICK",
    "CLICK_LAST_MATCH",
    "CLICK_LAST_OCR",
    "MATCH_TEMPLATE",
    "OCR",
    "RUN_SCRIPT_SEGMENT",
    "SCREENSHOT_GROUP",
    "SET_VAR",
    "SWIPE",
}
VALID_ALIGN = {"top", "center", "bottom"}
TEMPLATE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"}
SCRIPT_VARIABLE_SENTINELS = {"BIRD_FOOD_TASK_SCRIPT"}


@dataclass
class DailyAssetFinding:
    severity: str
    code: str
    message: str
    file: str | None = None
    task_id: int | None = None
    location: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "severity": self.severity,
            "code": self.code,
            "message": self.message,
            "file": self.file,
            "taskId": self.task_id,
            "location": self.location,
        }


@dataclass
class DailyScriptReport:
    file: str
    display_name: str
    tasks: int = 0
    visual_nodes: int = 0
    template_refs: int = 0
    script_refs: int = 0
    reachable_tasks: int = 0
    unnamed_template_nodes: int = 0
    duplicate_template_names: int = 0
    findings: list[DailyAssetFinding] = field(default_factory=list)

    @property
    def errors(self) -> list[DailyAssetFinding]:
        return [item for item in self.findings if item.severity == "ERROR"]

    @property
    def warnings(self) -> list[DailyAssetFinding]:
        return [item for item in self.findings if item.severity == "WARN"]

    def add(
        self,
        severity: str,
        code: str,
        message: str,
        *,
        task_id: int | None = None,
        location: str | None = None,
    ) -> None:
        self.findings.append(
            DailyAssetFinding(
                severity=severity,
                code=code,
                message=message,
                file=self.file,
                task_id=task_id,
                location=location,
            )
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "file": self.file,
            "displayName": self.display_name,
            "tasks": self.tasks,
            "visualNodes": self.visual_nodes,
            "templateRefs": self.template_refs,
            "scriptRefs": self.script_refs,
            "reachableTasks": self.reachable_tasks,
            "unnamedTasks": self.unnamed_template_nodes,
            "duplicateTaskNames": self.duplicate_template_names,
            "summary": {
                "errors": len(self.errors),
                "warnings": len(self.warnings),
                "passed": not self.errors,
            },
            "findings": [item.to_dict() for item in self.findings],
        }


@dataclass
class DailyAssetsReport:
    scripts: list[DailyScriptReport]
    orphan_template_dirs: list[str]
    project_root: Path

    @property
    def findings(self) -> list[DailyAssetFinding]:
        items: list[DailyAssetFinding] = []
        for script in self.scripts:
            items.extend(script.findings)
        for directory in self.orphan_template_dirs:
            items.append(
                DailyAssetFinding(
                    severity="WARN",
                    code="TEMPLATE_DIR_UNUSED",
                    message=f"模板目录未被任何日常脚本 asset_template_dir 引用：{directory}",
                    file=directory,
                )
            )
        return items

    def summary(self) -> dict[str, int]:
        errors = sum(1 for item in self.findings if item.severity == "ERROR")
        warnings = sum(1 for item in self.findings if item.severity == "WARN")
        return {
            "scripts": len(self.scripts),
            "passed": sum(1 for item in self.scripts if not item.errors),
            "failed": sum(1 for item in self.scripts if item.errors),
            "errors": errors,
            "warnings": warnings,
            "tasks": sum(item.tasks for item in self.scripts),
            "visualNodes": sum(item.visual_nodes for item in self.scripts),
            "templateRefs": sum(item.template_refs for item in self.scripts),
            "scriptRefs": sum(item.script_refs for item in self.scripts),
            "unnamedTasks": sum(item.unnamed_template_nodes for item in self.scripts),
            "duplicateTaskNames": sum(item.duplicate_template_names for item in self.scripts),
            "orphanTemplateDirs": len(self.orphan_template_dirs),
        }

    def to_dict(self) -> dict[str, Any]:
        return {
            "summary": self.summary(),
            "items": [item.to_dict() for item in self.scripts],
            "orphanTemplateDirs": self.orphan_template_dirs,
        }

    def to_text(self) -> str:
        summary = self.summary()
        lines = [
            "日常脚本资产体检汇总："
            f"{summary['scripts']} 个脚本，"
            f"{summary['passed']} 通过，"
            f"{summary['failed']} 失败，"
            f"{summary['errors']} 错误，"
            f"{summary['warnings']} 提醒"
        ]
        for script in self.scripts:
            status = "失败" if script.errors else "通过"
            lines.append(f"- [{status}] {script.file}：{script.tasks} 节点，{script.template_refs} 模板引用")
            for finding in script.findings[:8]:
                task = f" task={finding.task_id}" if finding.task_id is not None else ""
                location = f" [{finding.location}]" if finding.location else ""
                lines.append(f"  {finding.severity} {finding.code}{task}{location}: {finding.message}")
        for directory in self.orphan_template_dirs[:8]:
            lines.append(f"  WARN TEMPLATE_DIR_UNUSED: {directory}")
        return "\n".join(lines)


def check_daily_assets(project_root: Path = Path(".")) -> DailyAssetsReport:
    root = project_root.resolve()
    script_dir = root / DAILY_SCRIPT_DIR
    template_root = root / DAILY_TEMPLATE_ROOT
    script_reports: list[DailyScriptReport] = []
    referenced_template_dirs: set[str] = set()

    if not script_dir.exists():
        missing = DailyScriptReport(file=str(DAILY_SCRIPT_DIR), display_name="daily_scripts")
        missing.add("ERROR", "DAILY_SCRIPT_DIR_MISSING", f"日常脚本目录不存在：{DAILY_SCRIPT_DIR}")
        return DailyAssetsReport([missing], [], root)

    script_names = {path.name for path in script_dir.glob("*.json")}
    for path in sorted(script_dir.glob("*.json"), key=lambda item: item.name.lower()):
        if path.name in IGNORED_DAILY_FILES:
            continue
        report = check_daily_script(path, root, script_names)
        script_reports.append(report)
        asset_dir = load_asset_template_dir(path)
        if asset_dir:
            referenced_template_dirs.add(normalize_asset_dir(asset_dir))

    orphan_dirs: list[str] = []
    if template_root.exists():
        for directory in sorted(template_root.iterdir(), key=lambda item: item.name.lower()):
            if directory.is_dir():
                relative = directory.relative_to(root).as_posix()
                if normalize_asset_dir(directory.relative_to(root / ASSET_ROOT).as_posix()) not in referenced_template_dirs:
                    orphan_dirs.append(relative)

    return DailyAssetsReport(script_reports, orphan_dirs, root)


def check_daily_script(path: Path, project_root: Path, script_names: set[str]) -> DailyScriptReport:
    relative_file = path.relative_to(project_root).as_posix()
    report = DailyScriptReport(file=relative_file, display_name=path.stem)
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
    except json.JSONDecodeError as exc:
        report.add("ERROR", "JSON_PARSE_ERROR", f"JSON 解析失败：{exc}")
        return report

    if not isinstance(data, dict):
        report.add("ERROR", "SCRIPT_NOT_OBJECT", "日常脚本顶层必须是 JSON object")
        return report

    display_name = as_clean_str(data.get("display_name"))
    report.display_name = display_name or path.stem
    if not display_name:
        report.add("WARN", "DISPLAY_NAME_MISSING", "脚本缺少 display_name，调试页和列表可读性会变差")

    tasks = data.get("tasks")
    if not isinstance(tasks, list):
        report.add("ERROR", "TASKS_NOT_ARRAY", "tasks 必须是数组")
        return report
    report.tasks = len(tasks)
    if not tasks:
        report.add("ERROR", "TASKS_EMPTY", "脚本没有任何任务节点")
        return report

    task_ids: set[int] = set()
    duplicates: set[int] = set()
    task_by_id: dict[int, dict[str, Any]] = {}
    for index, raw_task in enumerate(tasks):
        if not isinstance(raw_task, dict):
            report.add("ERROR", "TASK_NOT_OBJECT", "任务节点必须是 object", location=f"tasks[{index}]")
            continue
        task_id = raw_task.get("id")
        if not isinstance(task_id, int):
            report.add("ERROR", "TASK_ID_INVALID", "任务 id 必须是整数", location=f"tasks[{index}]")
            continue
        if task_id in task_ids:
            duplicates.add(task_id)
        task_ids.add(task_id)
        task_by_id[task_id] = raw_task

    for task_id in sorted(duplicates):
        report.add("ERROR", "TASK_ID_DUPLICATE", f"任务 id 重复：{task_id}", task_id=task_id)

    start_task = data.get("start_task_id")
    if not isinstance(start_task, int):
        report.add("ERROR", "START_TASK_INVALID", "start_task_id 必须是整数")
    elif start_task not in task_ids:
        report.add("ERROR", "START_TASK_MISSING", f"start_task_id 指向不存在的任务：{start_task}")

    asset_dir = normalize_asset_dir(as_clean_str(data.get("asset_template_dir")))
    template_base = resolve_template_base(project_root, asset_dir)
    if asset_dir and not template_base.exists():
        report.add("ERROR", "ASSET_TEMPLATE_DIR_MISSING", f"asset_template_dir 不存在：{asset_dir}")

    for task_id, task in sorted(task_by_id.items()):
        check_task(report, task, task_id, task_ids, script_names, project_root, template_base, asset_dir)

    check_template_debug_names(report, task_by_id)

    if isinstance(start_task, int) and start_task in task_ids:
        reachable = collect_reachable(start_task, task_by_id)
        report.reachable_tasks = len(reachable)
        for task_id in sorted(task_ids - reachable):
            report.add("WARN", "TASK_UNREACHABLE", f"从 start_task_id 无法到达任务：{task_id}", task_id=task_id)

    return report


def check_task(
    report: DailyScriptReport,
    task: dict[str, Any],
    task_id: int,
    task_ids: set[int],
    script_names: set[str],
    project_root: Path,
    template_base: Path,
    asset_dir: str,
) -> None:
    action = as_clean_str(task.get("action")).upper()
    params = task.get("params") if isinstance(task.get("params"), dict) else {}
    if not action:
        report.add("ERROR", "ACTION_MISSING", "任务缺少 action", task_id=task_id)
        return
    if action not in VALID_ACTIONS:
        report.add("ERROR", "ACTION_UNSUPPORTED", f"不支持的 action：{action}", task_id=task_id)

    if action in {"MATCH_TEMPLATE", "OCR", "SCREENSHOT_GROUP"}:
        report.visual_nodes += 1

    if not isinstance(task.get("params"), dict) and action not in {"BACK"}:
        report.add("WARN", "PARAMS_MISSING", "任务 params 为空或不是 object", task_id=task_id)

    check_transition(report, task.get("on_success", -1), task_ids, task_id, "on_success")
    check_transition(report, task.get("on_fail", -1), task_ids, task_id, "on_fail")
    check_routes(report, params.get("branch_routes"), task_ids, task_id, "branch_routes")
    check_routes(report, params.get("fail_branch_routes"), task_ids, task_id, "fail_branch_routes")

    align = params.get("align")
    if align is not None and align not in VALID_ALIGN:
        report.add("ERROR", "ALIGN_INVALID", f"align 只能是 top/center/bottom：{align}", task_id=task_id)
    check_roi(report, params.get("roi"), task_id, "params.roi")

    if action == "CLICK":
        has_xy = is_number(params.get("x")) and is_number(params.get("y"))
        has_ref = isinstance(params.get("ref_task_id"), int)
        if not has_xy and not has_ref:
            report.add("ERROR", "CLICK_TARGET_MISSING", "CLICK 需要 params.x/y 或 ref_task_id", task_id=task_id)
        if has_ref and params["ref_task_id"] not in task_ids:
            report.add("ERROR", "CLICK_REF_TASK_MISSING", f"CLICK ref_task_id 指向不存在的任务：{params['ref_task_id']}", task_id=task_id)
    elif action == "SWIPE":
        for key in ("startX", "startY", "endX", "endY"):
            if not is_number(params.get(key)):
                report.add("ERROR", "SWIPE_COORD_MISSING", f"SWIPE 缺少 {key}", task_id=task_id)
    elif action == "MATCH_TEMPLATE":
        check_template_ref(report, params.get("template_name"), template_base, asset_dir, task_id, "params.template_name")
    elif action == "OCR":
        has_target = bool(params.get("target_text")) or has_non_empty_list(params.get("target_chars"))
        if not has_target:
            report.add("ERROR", "OCR_TARGET_MISSING", "OCR 需要 target_text 或 target_chars", task_id=task_id)
        if params.get("roi") is None:
            report.add("WARN", "OCR_ROI_MISSING", "OCR 未配置 roi，容易扩大识别范围", task_id=task_id)
        template_name = params.get("template_name")
        if template_name:
            check_template_ref(report, template_name, template_base, asset_dir, task_id, "params.template_name")
    elif action == "SCREENSHOT_GROUP":
        steps = params.get("screenshot_steps")
        if not isinstance(steps, list) or not steps:
            report.add("ERROR", "SCREENSHOT_STEPS_EMPTY", "SCREENSHOT_GROUP 需要非空 screenshot_steps", task_id=task_id)
        else:
            for index, step in enumerate(steps):
                check_screenshot_step(report, step, index, task_id, task_ids, template_base, asset_dir)
    elif action == "RUN_SCRIPT_SEGMENT":
        report.script_refs += 1
        check_script_ref(report, params, script_names, task_id)
    elif action == "SET_VAR":
        if not as_clean_str(params.get("var_name")):
            report.add("ERROR", "SET_VAR_NAME_MISSING", "SET_VAR 需要 var_name", task_id=task_id)


def check_screenshot_step(
    report: DailyScriptReport,
    step: Any,
    index: int,
    task_id: int,
    task_ids: set[int],
    template_base: Path,
    asset_dir: str,
) -> None:
    location = f"screenshot_steps[{index}]"
    if not isinstance(step, dict):
        report.add("ERROR", "SCREENSHOT_STEP_NOT_OBJECT", "候选截图步骤必须是 object", task_id=task_id, location=location)
        return
    step_type = as_clean_str(step.get("type")).upper()
    check_transition(report, step.get("on_success", -1), task_ids, task_id, f"{location}.on_success")
    check_roi(report, step.get("roi"), task_id, f"{location}.roi")
    if step_type in {"TEMPLATE", "MATCH_TEMPLATE"}:
        report.template_refs += 1
        check_template_ref(report, step.get("template_name"), template_base, asset_dir, task_id, f"{location}.template_name")
    elif step_type == "OCR":
        has_target = bool(step.get("target_text")) or has_non_empty_list(step.get("target_chars"))
        if not has_target:
            report.add("ERROR", "SCREENSHOT_OCR_TARGET_MISSING", "候选 OCR 步骤缺少目标文本", task_id=task_id, location=location)
    else:
        report.add("ERROR", "SCREENSHOT_STEP_TYPE_UNSUPPORTED", f"不支持的候选类型：{step.get('type')}", task_id=task_id, location=location)


def check_template_debug_names(report: DailyScriptReport, task_by_id: dict[int, dict[str, Any]]) -> None:
    exposed_nodes: list[tuple[str, str, str]] = []
    missing: list[str] = []

    for task_id, task in sorted(task_by_id.items()):
        action = as_clean_str(task.get("action")).upper()
        params = task.get("params") if isinstance(task.get("params"), dict) else {}
        task_name = as_clean_str(task.get("name"))
        if action == "MATCH_TEMPLATE":
            location = f"task {task_id}"
            if task_name:
                exposed_nodes.append((task_name, normalize_template_debug_key(params.get("template_name")), location))
            else:
                missing.append(location)
        elif action == "SCREENSHOT_GROUP":
            steps = params.get("screenshot_steps")
            if not isinstance(steps, list):
                continue
            for index, step in enumerate(steps):
                if not isinstance(step, dict):
                    continue
                normalized_type = as_clean_str(step.get("type")).upper()
                if normalized_type not in {"TEMPLATE", "MATCH_TEMPLATE"}:
                    continue
                step_name = as_clean_str(step.get("name")) or task_name
                location = f"task {task_id} screenshot_steps[{index}]"
                if step_name:
                    exposed_nodes.append((step_name, normalize_template_debug_key(step.get("template_name")), location))
                else:
                    missing.append(location)

    report.unnamed_template_nodes = len(missing)
    if missing:
        report.add(
            "WARN",
            "TEMPLATE_DEBUG_NAME_MISSING",
            f"有 {len(missing)} 个模板调试项缺少 name：{format_text_list(missing)}",
        )

    name_templates: dict[str, dict[str, list[str]]] = {}
    for name, template_name, location in exposed_nodes:
        name_templates.setdefault(name, {}).setdefault(template_name, []).append(location)
    duplicates = {
        name: templates
        for name, templates in name_templates.items()
        if len(templates) > 1
    }
    report.duplicate_template_names = len(duplicates)
    for name, templates in sorted(duplicates.items(), key=lambda item: item[0]):
        detail = "; ".join(
            f"{template}: {format_text_list(locations)}"
            for template, locations in sorted(templates.items(), key=lambda item: item[0])
        )
        report.add(
            "WARN",
            "TEMPLATE_DEBUG_NAME_DUPLICATE",
            f"模板调试项 name 重复但 template_name 不同：{name}，{detail}",
        )


def check_transition(
    report: DailyScriptReport,
    value: Any,
    task_ids: set[int],
    task_id: int,
    field: str,
) -> None:
    if value is None:
        return
    if not isinstance(value, int):
        report.add("ERROR", "TRANSITION_NOT_INT", f"{field} 必须是整数", task_id=task_id, location=field)
        return
    if value in SPECIAL_TRANSITIONS:
        return
    if value not in task_ids:
        report.add("ERROR", "TRANSITION_TARGET_MISSING", f"{field} 指向不存在的任务：{value}", task_id=task_id, location=field)


def check_routes(report: DailyScriptReport, routes: Any, task_ids: set[int], task_id: int, field: str) -> None:
    if routes is None:
        return
    if not isinstance(routes, dict):
        report.add("ERROR", "BRANCH_ROUTES_INVALID", f"{field} 必须是 object", task_id=task_id, location=field)
        return
    for key, value in routes.items():
        check_transition(report, value, task_ids, task_id, f"{field}.{key}")


def check_template_ref(
    report: DailyScriptReport,
    raw_name: Any,
    template_base: Path,
    asset_dir: str,
    task_id: int,
    location: str,
) -> None:
    template_name = as_clean_str(raw_name)
    if not template_name:
        report.add("ERROR", "TEMPLATE_NAME_MISSING", "模板节点缺少 template_name", task_id=task_id, location=location)
        return
    report.template_refs += 1
    if not template_exists(template_base, template_name):
        prefix = f"{asset_dir}/" if asset_dir and "/" not in template_name.replace("\\", "/") else ""
        report.add("ERROR", "TEMPLATE_FILE_MISSING", f"模板文件不存在：{prefix}{template_name}", task_id=task_id, location=location)


def check_script_ref(report: DailyScriptReport, params: dict[str, Any], script_names: set[str], task_id: int) -> None:
    script_name = as_clean_str(params.get("script_name"))
    routes = params.get("script_name_routes")
    if script_name in SCRIPT_VARIABLE_SENTINELS:
        return
    if script_name:
        normalized = normalize_script_name(script_name)
        if normalized not in script_names:
            report.add("ERROR", "SEGMENT_SCRIPT_MISSING", f"子脚本不存在：{normalized}", task_id=task_id)
    elif isinstance(routes, dict) and routes:
        for key, value in routes.items():
            normalized = normalize_script_name(as_clean_str(value))
            if normalized not in script_names:
                report.add("ERROR", "SEGMENT_ROUTE_SCRIPT_MISSING", f"分支 {key} 指向的子脚本不存在：{normalized}", task_id=task_id)
    elif not as_clean_str(params.get("script_name_var")):
        report.add("ERROR", "SEGMENT_SCRIPT_UNRESOLVED", "RUN_SCRIPT_SEGMENT 需要 script_name、script_name_var 或 script_name_routes", task_id=task_id)


def check_roi(report: DailyScriptReport, roi: Any, task_id: int, location: str) -> None:
    if roi is None:
        return
    if not isinstance(roi, dict):
        report.add("ERROR", "ROI_INVALID", "roi 必须是 object", task_id=task_id, location=location)
        return
    align = roi.get("align")
    if align is not None and align not in VALID_ALIGN:
        report.add("ERROR", "ROI_ALIGN_INVALID", f"roi.align 只能是 top/center/bottom：{align}", task_id=task_id, location=location)
    has_rect = all(is_number(roi.get(key)) for key in ("x", "y", "w", "h"))
    has_circle = all(is_number(roi.get(key)) for key in ("centerX", "centerY", "radius"))
    if not has_rect and not has_circle:
        report.add("ERROR", "ROI_SHAPE_INVALID", "roi 需要 x/y/w/h 或 centerX/centerY/radius", task_id=task_id, location=location)


def collect_reachable(start_task: int, task_by_id: dict[int, dict[str, Any]]) -> set[int]:
    reachable: set[int] = set()
    stack = [start_task]
    while stack:
        task_id = stack.pop()
        if task_id in reachable or task_id not in task_by_id:
            continue
        reachable.add(task_id)
        task = task_by_id[task_id]
        params = task.get("params") if isinstance(task.get("params"), dict) else {}
        for target in transition_targets(task, params):
            if target not in SPECIAL_TRANSITIONS and target in task_by_id and target not in reachable:
                stack.append(target)
    return reachable


def transition_targets(task: dict[str, Any], params: dict[str, Any]) -> list[int]:
    values: list[int] = []
    for key in ("on_success", "on_fail"):
        value = task.get(key)
        if isinstance(value, int):
            values.append(value)
    for key in ("branch_routes", "fail_branch_routes"):
        routes = params.get(key)
        if isinstance(routes, dict):
            values.extend(value for value in routes.values() if isinstance(value, int))
    steps = params.get("screenshot_steps")
    if isinstance(steps, list):
        for step in steps:
            if isinstance(step, dict) and isinstance(step.get("on_success"), int):
                values.append(step["on_success"])
    return values


def resolve_template_base(project_root: Path, asset_dir: str) -> Path:
    if not asset_dir:
        return project_root / ASSET_ROOT
    return project_root / ASSET_ROOT / asset_dir


def template_exists(template_base: Path, template_name: str) -> bool:
    normalized = template_name.replace("\\", "/")
    if "/" in normalized:
        asset_root = find_asset_root(template_base)
        direct = asset_root.joinpath(*normalized.split("/"))
    else:
        direct = template_base / normalized
    if direct.exists():
        return True
    if Path(normalized).suffix:
        return False
    return any((template_base / f"{normalized}{suffix}").exists() for suffix in TEMPLATE_EXTENSIONS)


def find_asset_root(path: Path) -> Path:
    parts = path.parts
    marker = ("app", "src", "main", "assets")
    for index in range(0, len(parts) - len(marker) + 1):
        if tuple(parts[index : index + len(marker)]) == marker:
            return Path(*parts[: index + len(marker)])
    return path


def load_asset_template_dir(path: Path) -> str:
    try:
        data = json.loads(path.read_text(encoding="utf-8-sig"))
    except json.JSONDecodeError:
        return ""
    if not isinstance(data, dict):
        return ""
    return as_clean_str(data.get("asset_template_dir"))


def normalize_asset_dir(value: str) -> str:
    return value.strip().replace("\\", "/").rstrip("/")


def normalize_script_name(value: str) -> str:
    return value if value.lower().endswith(".json") else f"{value}.json"


def normalize_template_debug_key(value: Any) -> str:
    text = as_clean_str(value).replace("\\", "/")
    return text or "<missing-template>"


def as_clean_str(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def is_number(value: Any) -> bool:
    return isinstance(value, int | float) and not isinstance(value, bool)


def has_non_empty_list(value: Any) -> bool:
    return isinstance(value, list) and any(as_clean_str(item) for item in value)


def format_id_list(values: list[int], limit: int = 20) -> str:
    ordered = sorted(values)
    shown = ", ".join(str(item) for item in ordered[:limit])
    if len(ordered) > limit:
        shown += f", ... 还有 {len(ordered) - limit} 个"
    return shown


def format_text_list(values: list[str], limit: int = 20) -> str:
    shown = ", ".join(values[:limit])
    if len(values) > limit:
        shown += f", ... 还有 {len(values) - limit} 个"
    return shown

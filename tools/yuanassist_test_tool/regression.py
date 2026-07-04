from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Callable

from .app_smoke import DEFAULT_START_WAIT_SECONDS, run_app_smoke
from .battle_static import BattleStaticReport
from .cases import DEFAULT_BATTLE_CASE_DIR, check_strategy_cases, load_strategy_case, summarize_reports
from .daily_assets import check_daily_assets
from .daily_vision import DEFAULT_DAILY_VISION_DIR, check_daily_vision


DEFAULT_REGRESSION_REPORT_DIR = Path("tools/yuanassist_test_tool/reports/regression")


@dataclass
class RegressionSuiteResult:
    name: str
    title: str
    status: str
    summary: dict[str, Any] = field(default_factory=dict)
    findings: list[dict[str, Any]] = field(default_factory=list)
    data: dict[str, Any] = field(default_factory=dict)

    @property
    def errors(self) -> int:
        return int(self.summary.get("errors") or 0)

    @property
    def warnings(self) -> int:
        return int(self.summary.get("warnings") or 0)

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "title": self.title,
            "status": self.status,
            "summary": self.summary,
            "findings": self.findings,
            "data": self.data,
        }


@dataclass
class RegressionReport:
    id: str
    report_dir: Path
    suites: list[RegressionSuiteResult] = field(default_factory=list)

    @property
    def errors(self) -> int:
        return sum(item.errors for item in self.suites)

    @property
    def warnings(self) -> int:
        return sum(item.warnings for item in self.suites)

    def summary(self) -> dict[str, Any]:
        return {
            "passed": self.errors == 0,
            "suites": len(self.suites),
            "passedSuites": sum(1 for item in self.suites if item.status in {"passed", "skipped"}),
            "failedSuites": sum(1 for item in self.suites if item.status == "failed"),
            "errors": self.errors,
            "warnings": self.warnings,
        }

    def to_dict(self, project_root: Path | None = None) -> dict[str, Any]:
        root = project_root.resolve() if project_root else None
        report_dir = self.report_dir
        if root and report_dir.is_absolute():
            try:
                report_dir = report_dir.relative_to(root)
            except ValueError:
                pass
        return {
            "id": self.id,
            "reportDir": report_dir.as_posix(),
            "summary": self.summary(),
            "suites": [item.to_dict() for item in self.suites],
        }

    def to_text(self) -> str:
        summary = self.summary()
        lines = [
            "YuanAssist 一键回归汇总："
            f"{'通过' if summary['passed'] else '失败'}，"
            f"{summary['suites']} 个套件，"
            f"{summary['errors']} 错误，"
            f"{summary['warnings']} 提醒",
            f"报告目录：{self.report_dir}",
        ]
        for suite in self.suites:
            status = "失败" if suite.status == "failed" else "通过" if suite.status == "passed" else "跳过"
            lines.append(f"- [{status}] {suite.title}：{format_suite_summary(suite.summary)}")
            for finding in suite.findings[:8]:
                severity = finding.get("severity", "")
                code = finding.get("code", "")
                message = finding.get("message", "")
                location = finding.get("location") or finding.get("file") or finding.get("caseId") or ""
                location_text = f" [{location}]" if location else ""
                lines.append(f"  {severity} {code}{location_text}: {message}")
        return "\n".join(lines)


def run_regression(
    project_root: Path = Path("."),
    *,
    include_app_smoke: bool = False,
    serial: str = "emulator-5554",
    apk_path: Path | None = None,
    install_apk: bool = False,
    strict: bool = False,
    report_dir: Path = DEFAULT_REGRESSION_REPORT_DIR,
) -> RegressionReport:
    root = project_root.resolve()
    regression_id = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = resolve_under_root(root, report_dir) / regression_id
    output_dir.mkdir(parents=True, exist_ok=True)

    report = RegressionReport(id=regression_id, report_dir=output_dir)
    report.suites.append(run_suite("daily_assets", "日常脚本资产体检", lambda: run_daily_assets_suite(root)))
    report.suites.append(run_suite("daily_vision", "日常视觉回归", lambda: run_daily_vision_suite(root)))
    report.suites.append(run_suite("battle_static_cases", "战斗攻略本地用例", lambda: run_battle_cases_suite(root)))
    if include_app_smoke:
        report.suites.append(
            run_suite(
                "app_smoke",
                "App 冒烟体检",
                lambda: run_app_smoke_suite(
                    root,
                    serial=serial,
                    apk_path=apk_path,
                    install_apk=install_apk,
                    report_dir=output_dir / "app_smoke",
                ),
            )
        )
    else:
        report.suites.append(
            RegressionSuiteResult(
                name="app_smoke",
                title="App 冒烟体检",
                status="skipped",
                summary={"errors": 0, "warnings": 0, "reason": "未启用设备冒烟"},
            )
        )

    write_regression_report(report, root)
    if strict:
        mark_warning_suites_failed(report)
    return report


def run_suite(name: str, title: str, runner: Callable[[], RegressionSuiteResult]) -> RegressionSuiteResult:
    try:
        return runner()
    except Exception as exc:  # Defensive boundary for the aggregate runner.
        return RegressionSuiteResult(
            name=name,
            title=title,
            status="failed",
            summary={"errors": 1, "warnings": 0},
            findings=[
                {
                    "severity": "ERROR",
                    "code": "SUITE_EXCEPTION",
                    "message": f"套件执行异常：{exc}",
                }
            ],
        )


def run_daily_assets_suite(project_root: Path) -> RegressionSuiteResult:
    report = check_daily_assets(project_root)
    payload = report.to_dict()
    summary = payload["summary"]
    return RegressionSuiteResult(
        name="daily_assets",
        title="日常脚本资产体检",
        status="failed" if summary["errors"] else "passed",
        summary=summary,
        findings=report_findings(payload),
        data=payload,
    )


def run_daily_vision_suite(project_root: Path) -> RegressionSuiteResult:
    report = check_daily_vision(project_root, case_dir=DEFAULT_DAILY_VISION_DIR)
    payload = report.to_dict(include_observations=False)
    summary = payload["summary"]
    return RegressionSuiteResult(
        name="daily_vision",
        title="日常视觉回归",
        status="failed" if summary["errors"] else "passed",
        summary=summary,
        findings=report_findings(payload),
        data=payload,
    )


def run_battle_cases_suite(project_root: Path) -> RegressionSuiteResult:
    case_dir = project_root / DEFAULT_BATTLE_CASE_DIR
    if not case_dir.exists():
        return RegressionSuiteResult(
            name="battle_static_cases",
            title="战斗攻略本地用例",
            status="skipped",
            summary={"cases": 0, "errors": 0, "warnings": 0, "reason": "未找到本地战斗用例目录"},
        )

    cases = []
    load_findings: list[dict[str, Any]] = []
    for path in sorted(case_dir.glob("*.json"), key=lambda item: item.name.lower()):
        try:
            cases.append(load_strategy_case(path))
        except (OSError, ValueError, json.JSONDecodeError) as exc:
            load_findings.append(
                {
                    "severity": "ERROR",
                    "code": "BATTLE_CASE_LOAD_FAILED",
                    "message": f"战斗用例读取失败：{exc}",
                    "location": path.relative_to(project_root).as_posix(),
                }
            )
    reports = check_strategy_cases(cases)
    summary = summarize_reports(reports)
    summary["errors"] += len(load_findings)
    items = battle_reports_to_items(reports)
    return RegressionSuiteResult(
        name="battle_static_cases",
        title="战斗攻略本地用例",
        status="failed" if summary["errors"] else "passed",
        summary=summary,
        findings=load_findings + battle_report_findings(reports),
        data={"summary": summary, "items": items},
    )


def run_app_smoke_suite(
    project_root: Path,
    *,
    serial: str,
    apk_path: Path | None,
    install_apk: bool,
    report_dir: Path,
) -> RegressionSuiteResult:
    report = run_app_smoke(
        project_root,
        serial=serial,
        apk_path=apk_path,
        install_apk=install_apk,
        clear_logcat=True,
        wait_seconds=DEFAULT_START_WAIT_SECONDS,
        report_dir=report_dir,
    )
    payload = report.to_dict(project_root)
    summary = payload["summary"]
    return RegressionSuiteResult(
        name="app_smoke",
        title="App 冒烟体检",
        status="failed" if summary["errors"] else "passed",
        summary=summary,
        findings=payload.get("findings", []),
        data=payload,
    )


def report_findings(payload: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    for item in payload.get("items", []):
        for finding in item.get("findings", []):
            findings.append(finding)
    return findings


def battle_reports_to_items(reports: list[BattleStaticReport]) -> list[dict[str, Any]]:
    return [report.to_dict() for report in reports]


def battle_report_findings(reports: list[BattleStaticReport]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    for report in reports:
        for finding in report.findings:
            item = finding.to_dict()
            item["caseTitle"] = report.title
            findings.append(item)
    return findings


def write_regression_report(report: RegressionReport, project_root: Path) -> None:
    payload = report.to_dict(project_root)
    summary_path = report.report_dir / "summary.json"
    summary_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    text_path = report.report_dir / "summary.txt"
    text_path.write_text(report.to_text(), encoding="utf-8")


def mark_warning_suites_failed(report: RegressionReport) -> None:
    for suite in report.suites:
        if suite.status == "passed" and suite.warnings:
            suite.status = "failed"
            suite.summary["errors"] = int(suite.summary.get("errors") or 0)


def format_suite_summary(summary: dict[str, Any]) -> str:
    if "reason" in summary:
        return str(summary["reason"])
    parts = []
    for key, label in [
        ("cases", "case"),
        ("scripts", "脚本"),
        ("errors", "错误"),
        ("warnings", "提醒"),
        ("skips", "跳过"),
    ]:
        if key in summary:
            parts.append(f"{summary[key]} {label}")
    return "，".join(parts) if parts else json.dumps(summary, ensure_ascii=False)


def resolve_under_root(project_root: Path, path: Path) -> Path:
    if path.is_absolute():
        return path
    return project_root / path

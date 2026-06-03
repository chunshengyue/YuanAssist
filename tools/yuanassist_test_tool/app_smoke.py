from __future__ import annotations

import json
import re
import time
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any

from .adb_device import DEFAULT_ADB_BIN, DEFAULT_EMULATOR_SERIAL, AdbError, capture_screenshot, list_adb_devices, resolve_adb_bin, run_adb


APP_ID = "com.example.yuanassist"
MAIN_ACTIVITY = "com.example.yuanassist/.ui.MainActivity"
ACCESSIBILITY_SERVICE = "com.example.yuanassist/com.example.yuanassist.core.YuanAssistService"
DEFAULT_APP_SMOKE_REPORT_DIR = Path("tools/yuanassist_test_tool/reports/app_smoke")
DEFAULT_START_WAIT_SECONDS = 4.0


@dataclass
class AppSmokeStep:
    name: str
    status: str
    message: str = ""
    data: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "status": self.status,
            "message": self.message,
            "data": self.data,
        }


@dataclass
class AppSmokeFinding:
    severity: str
    code: str
    message: str
    location: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "severity": self.severity,
            "code": self.code,
            "message": self.message,
            "location": self.location,
        }


@dataclass
class AppSmokeReport:
    id: str
    report_dir: Path
    adb_bin: Path
    serial: str
    app_id: str = APP_ID
    main_activity: str = MAIN_ACTIVITY
    apk_path: Path | None = None
    installed: bool = False
    started: bool = False
    process_alive: bool = False
    screenshot: Path | None = None
    logcat: Path | None = None
    device: dict[str, Any] = field(default_factory=dict)
    build_config: dict[str, Any] = field(default_factory=dict)
    installed_package: dict[str, Any] = field(default_factory=dict)
    permissions: dict[str, Any] = field(default_factory=dict)
    steps: list[AppSmokeStep] = field(default_factory=list)
    findings: list[AppSmokeFinding] = field(default_factory=list)

    @property
    def errors(self) -> list[AppSmokeFinding]:
        return [item for item in self.findings if item.severity == "ERROR"]

    @property
    def warnings(self) -> list[AppSmokeFinding]:
        return [item for item in self.findings if item.severity == "WARN"]

    def add_step(self, name: str, status: str, message: str = "", **data: Any) -> None:
        self.steps.append(AppSmokeStep(name=name, status=status, message=message, data={k: v for k, v in data.items() if v is not None}))

    def add_finding(self, severity: str, code: str, message: str, *, location: str = "") -> None:
        self.findings.append(AppSmokeFinding(severity=severity, code=code, message=message, location=location))

    def summary(self) -> dict[str, Any]:
        return {
            "passed": not self.errors,
            "errors": len(self.errors),
            "warnings": len(self.warnings),
            "steps": len(self.steps),
            "installed": self.installed,
            "started": self.started,
            "processAlive": self.process_alive,
        }

    def to_dict(self, project_root: Path | None = None) -> dict[str, Any]:
        root = project_root.resolve() if project_root else None
        return {
            "id": self.id,
            "appId": self.app_id,
            "mainActivity": self.main_activity,
            "adbBin": str(self.adb_bin),
            "serial": self.serial,
            "apkPath": path_to_text(self.apk_path, root),
            "reportDir": path_to_text(self.report_dir, root),
            "screenshot": path_to_text(self.screenshot, root),
            "logcat": path_to_text(self.logcat, root),
            "summary": self.summary(),
            "device": self.device,
            "buildConfig": self.build_config,
            "installedPackage": self.installed_package,
            "permissions": self.permissions,
            "steps": [item.to_dict() for item in self.steps],
            "findings": [item.to_dict() for item in self.findings],
        }

    def to_text(self) -> str:
        summary = self.summary()
        lines = [
            "App 冒烟体检："
            f"{'通过' if summary['passed'] else '失败'}，"
            f"{summary['errors']} 错误，"
            f"{summary['warnings']} 提醒，"
            f"进程={'存活' if self.process_alive else '未确认'}",
            f"报告目录：{self.report_dir}",
        ]
        if self.screenshot:
            lines.append(f"截图：{self.screenshot}")
        if self.logcat:
            lines.append(f"日志：{self.logcat}")
        for step in self.steps:
            lines.append(f"- [{step.status}] {step.name}: {step.message}")
        for finding in self.findings[:12]:
            location = f" [{finding.location}]" if finding.location else ""
            lines.append(f"  {finding.severity} {finding.code}{location}: {finding.message}")
        return "\n".join(lines)


def run_app_smoke(
    project_root: Path = Path("."),
    *,
    serial: str = DEFAULT_EMULATOR_SERIAL,
    adb_bin: Path | None = None,
    apk_path: Path | None = None,
    install_apk: bool = False,
    clear_logcat: bool = True,
    wait_seconds: float = DEFAULT_START_WAIT_SECONDS,
    report_dir: Path = DEFAULT_APP_SMOKE_REPORT_DIR,
) -> AppSmokeReport:
    root = project_root.resolve()
    resolved_adb = resolve_adb_bin(adb_bin)
    smoke_id = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = resolve_under_root(root, report_dir) / smoke_id
    output_dir.mkdir(parents=True, exist_ok=True)

    report = AppSmokeReport(id=smoke_id, report_dir=output_dir, adb_bin=resolved_adb, serial=serial)
    report.build_config = read_build_config(root)

    try:
        devices = list_adb_devices(resolved_adb)
        target = next((item for item in devices.devices if item.serial == serial), None)
        if target is None:
            report.add_step("设备检查", "error", f"未发现目标设备：{serial}")
            report.add_finding("ERROR", "DEVICE_MISSING", f"未发现目标设备：{serial}")
            write_report_files(report, root)
            return report
        if not target.online:
            report.add_step("设备检查", "error", f"设备未在线：state={target.state}", state=target.state)
            report.add_finding("ERROR", "DEVICE_NOT_ONLINE", f"设备未在线：{serial} state={target.state}")
            write_report_files(report, root)
            return report
        report.device.update(target.to_dict())
        report.add_step("设备检查", "ok", f"{serial} 在线", kind=target.kind, model=target.model or target.device)
    except AdbError as exc:
        report.add_step("设备检查", "error", str(exc))
        report.add_finding("ERROR", "ADB_DEVICES_FAILED", str(exc))
        write_report_files(report, root)
        return report

    fill_device_properties(report, resolved_adb, serial)

    resolved_apk = resolve_optional_path(root, apk_path)
    report.apk_path = resolved_apk
    if resolved_apk and install_apk:
        install_package(report, resolved_adb, serial, resolved_apk)
    elif resolved_apk:
        report.add_step("APK 安装", "skip", "已提供 APK，但未启用安装")
    else:
        report.add_step("APK 安装", "skip", "未提供 APK，直接检查已安装应用")

    read_installed_package(report, resolved_adb, serial)
    compare_versions(report)
    read_permission_state(report, resolved_adb, serial)

    if clear_logcat:
        clear_device_logcat(report, resolved_adb, serial)

    start_app(report, resolved_adb, serial)
    if wait_seconds > 0:
        time.sleep(wait_seconds)
    check_process(report, resolved_adb, serial)
    capture_smoke_screenshot(report, resolved_adb, serial, output_dir)
    collect_logcat(report, resolved_adb, serial, output_dir)
    scan_logcat(report)
    write_report_files(report, root)
    return report


def install_package(report: AppSmokeReport, adb_bin: Path, serial: str, apk_path: Path) -> None:
    if not apk_path.exists():
        report.add_step("APK 安装", "error", f"APK 不存在：{apk_path}")
        report.add_finding("ERROR", "APK_MISSING", f"APK 不存在：{apk_path}")
        return
    try:
        result = run_adb(adb_bin, ["install", "-r", str(apk_path)], serial=serial, timeout=180)
        report.installed = True
        report.add_step("APK 安装", "ok", "安装完成", output=short_text(result.stdout or result.stderr))
    except AdbError as exc:
        report.add_step("APK 安装", "error", str(exc))
        report.add_finding("ERROR", "APK_INSTALL_FAILED", str(exc))


def fill_device_properties(report: AppSmokeReport, adb_bin: Path, serial: str) -> None:
    props = {
        "androidSdk": adb_text_or_empty(adb_bin, serial, ["shell", "getprop", "ro.build.version.sdk"]),
        "release": adb_text_or_empty(adb_bin, serial, ["shell", "getprop", "ro.build.version.release"]),
        "model": adb_text_or_empty(adb_bin, serial, ["shell", "getprop", "ro.product.model"]),
        "screenSize": adb_text_or_empty(adb_bin, serial, ["shell", "wm", "size"]),
        "density": adb_text_or_empty(adb_bin, serial, ["shell", "wm", "density"]),
    }
    report.device.update({key: value for key, value in props.items() if value})
    report.add_step("设备信息", "ok", "已读取 Android 版本、分辨率和 DPI", **props)


def read_installed_package(report: AppSmokeReport, adb_bin: Path, serial: str) -> None:
    text = adb_text_or_empty(adb_bin, serial, ["shell", "dumpsys", "package", APP_ID], timeout=20)
    if not text or "Unable to find package" in text:
        report.add_step("安装包信息", "error", f"设备上未找到包：{APP_ID}")
        report.add_finding("ERROR", "PACKAGE_NOT_FOUND", f"设备上未找到包：{APP_ID}")
        return
    version_code = first_match(text, r"versionCode=(\d+)")
    version_name = first_match(text, r"versionName=([^\s]+)")
    report.installed_package = {
        "versionCode": int(version_code) if version_code and version_code.isdigit() else None,
        "versionName": version_name,
    }
    report.add_step(
        "安装包信息",
        "ok",
        f"versionCode={report.installed_package.get('versionCode')} versionName={version_name or '-'}",
        **report.installed_package,
    )


def compare_versions(report: AppSmokeReport) -> None:
    expected_code = report.build_config.get("versionCode")
    expected_name = report.build_config.get("versionName")
    actual_code = report.installed_package.get("versionCode")
    actual_name = report.installed_package.get("versionName")
    if actual_code is None:
        return
    if expected_code is not None and actual_code != expected_code:
        report.add_finding("WARN", "VERSION_CODE_MISMATCH", f"设备 versionCode={actual_code}，Gradle versionCode={expected_code}")
    if expected_name and actual_name and actual_name != expected_name:
        report.add_finding("WARN", "VERSION_NAME_MISMATCH", f"设备 versionName={actual_name}，Gradle versionName={expected_name}")


def read_permission_state(report: AppSmokeReport, adb_bin: Path, serial: str) -> None:
    enabled_services = adb_text_or_empty(adb_bin, serial, ["shell", "settings", "get", "secure", "enabled_accessibility_services"])
    accessibility_enabled = ACCESSIBILITY_SERVICE.lower() in enabled_services.lower()
    overlay_text = adb_text_or_empty(adb_bin, serial, ["shell", "appops", "get", APP_ID, "SYSTEM_ALERT_WINDOW"])
    overlay_allowed = "allow" in overlay_text.lower()
    report.permissions = {
        "accessibilityEnabled": accessibility_enabled,
        "enabledAccessibilityServices": enabled_services,
        "overlayAllowed": overlay_allowed,
        "overlayRaw": overlay_text,
    }
    status = "ok" if accessibility_enabled and overlay_allowed else "warn"
    message = f"无障碍={'已开' if accessibility_enabled else '未开'}，悬浮窗={'已允许' if overlay_allowed else '未确认允许'}"
    report.add_step("权限状态", status, message, **report.permissions)
    if not accessibility_enabled:
        report.add_finding("WARN", "ACCESSIBILITY_DISABLED", "无障碍服务未开启，悬浮窗自动化链路无法完整执行")
    if not overlay_allowed:
        report.add_finding("WARN", "OVERLAY_NOT_ALLOWED", "悬浮窗权限未确认允许，悬浮窗可能无法显示", location=overlay_text)


def clear_device_logcat(report: AppSmokeReport, adb_bin: Path, serial: str) -> None:
    try:
        run_adb(adb_bin, ["logcat", "-c"], serial=serial, timeout=15)
        report.add_step("清理 logcat", "ok", "已清理启动前日志")
    except AdbError as exc:
        report.add_step("清理 logcat", "warn", str(exc))
        report.add_finding("WARN", "LOGCAT_CLEAR_FAILED", str(exc))


def start_app(report: AppSmokeReport, adb_bin: Path, serial: str) -> None:
    try:
        result = run_adb(adb_bin, ["shell", "am", "start", "-n", MAIN_ACTIVITY], serial=serial, timeout=20)
        report.started = True
        report.add_step("冷启动", "ok", "已发送启动命令", output=short_text(result.stdout or result.stderr))
    except AdbError as exc:
        report.add_step("冷启动", "error", str(exc))
        report.add_finding("ERROR", "APP_START_FAILED", str(exc))


def check_process(report: AppSmokeReport, adb_bin: Path, serial: str) -> None:
    pid = adb_text_or_empty(adb_bin, serial, ["shell", "pidof", APP_ID])
    report.process_alive = bool(pid.strip())
    if report.process_alive:
        report.add_step("进程检查", "ok", f"进程存活 pid={pid.strip()}", pid=pid.strip())
    else:
        report.add_step("进程检查", "error", "未找到应用进程")
        report.add_finding("ERROR", "APP_PROCESS_MISSING", "启动后未找到应用进程")


def capture_smoke_screenshot(report: AppSmokeReport, adb_bin: Path, serial: str, output_dir: Path) -> None:
    screenshot = output_dir / "screenshot.png"
    try:
        capture_screenshot(adb_bin, serial, screenshot)
        report.screenshot = screenshot
        report.add_step("启动截图", "ok", "已保存启动后截图", path=str(screenshot))
    except AdbError as exc:
        report.add_step("启动截图", "warn", str(exc))
        report.add_finding("WARN", "SCREENSHOT_FAILED", str(exc))


def collect_logcat(report: AppSmokeReport, adb_bin: Path, serial: str, output_dir: Path) -> None:
    logcat_path = output_dir / "logcat.txt"
    try:
        result = run_adb(adb_bin, ["logcat", "-d", "-t", "1200"], serial=serial, timeout=30)
        logcat_path.write_text(result.stdout, encoding="utf-8")
        report.logcat = logcat_path
        report.add_step("采集 logcat", "ok", "已保存启动日志", path=str(logcat_path))
    except (AdbError, OSError) as exc:
        report.add_step("采集 logcat", "warn", str(exc))
        report.add_finding("WARN", "LOGCAT_CAPTURE_FAILED", str(exc))


def scan_logcat(report: AppSmokeReport) -> None:
    if not report.logcat or not report.logcat.exists():
        return
    text = report.logcat.read_text(encoding="utf-8", errors="replace")
    patterns = [
        ("FATAL_EXCEPTION", r"FATAL EXCEPTION"),
        ("ANR", r"\bANR in\b"),
        ("SECURITY_EXCEPTION", r"SecurityException"),
        ("PERMISSION_DENIAL", r"Permission Denial"),
        ("ACTIVITY_NOT_FOUND", r"ActivityNotFoundException|Error type 3"),
    ]
    found = False
    for code, pattern in patterns:
        match = re.search(pattern, text, re.IGNORECASE)
        if not match:
            continue
        found = True
        line = line_around(text, match.start())
        severity = "ERROR" if code in {"FATAL_EXCEPTION", "ANR", "ACTIVITY_NOT_FOUND"} else "WARN"
        report.add_finding(severity, code, line, location=path_to_text(report.logcat))
    report.add_step("日志扫描", "error" if any(item.code in {"FATAL_EXCEPTION", "ANR", "ACTIVITY_NOT_FOUND"} for item in report.findings) else "ok", "发现异常日志" if found else "未发现关键崩溃/ANR 日志")


def read_build_config(project_root: Path) -> dict[str, Any]:
    path = project_root / "app/build.gradle.kts"
    if not path.exists():
        return {}
    text = path.read_text(encoding="utf-8", errors="replace")
    version_code = first_match(text, r"versionCode\s*=\s*(\d+)")
    version_name = first_match(text, r'versionName\s*=\s*"([^"]+)"')
    application_id = first_match(text, r'applicationId\s*=\s*"([^"]+)"')
    return {
        "applicationId": application_id or APP_ID,
        "versionCode": int(version_code) if version_code and version_code.isdigit() else None,
        "versionName": version_name,
    }


def write_report_files(report: AppSmokeReport, project_root: Path) -> None:
    summary_path = report.report_dir / "summary.json"
    summary_path.write_text(json.dumps(report.to_dict(project_root), ensure_ascii=False, indent=2), encoding="utf-8")


def adb_text_or_empty(adb_bin: Path, serial: str, args: list[str], *, timeout: int = 15) -> str:
    try:
        return run_adb(adb_bin, args, serial=serial, timeout=timeout).stdout.strip()
    except AdbError:
        return ""


def resolve_optional_path(project_root: Path, value: Path | None) -> Path | None:
    if value is None:
        return None
    path = value.expanduser()
    if not path.is_absolute():
        path = project_root / path
    return path.resolve()


def resolve_under_root(project_root: Path, value: Path) -> Path:
    path = value.expanduser()
    if not path.is_absolute():
        path = project_root / path
    return path.resolve()


def first_match(text: str, pattern: str) -> str:
    match = re.search(pattern, text)
    return match.group(1).strip() if match else ""


def short_text(text: str, limit: int = 300) -> str:
    value = " ".join(text.strip().split())
    return value if len(value) <= limit else value[: limit - 1] + "…"


def line_around(text: str, offset: int) -> str:
    start = text.rfind("\n", 0, offset) + 1
    end = text.find("\n", offset)
    if end < 0:
        end = len(text)
    return text[start:end].strip()


def path_to_text(path: Path | None, project_root: Path | None = None) -> str:
    if path is None:
        return ""
    if project_root is not None:
        try:
            return path.resolve().relative_to(project_root).as_posix()
        except ValueError:
            pass
    return str(path)

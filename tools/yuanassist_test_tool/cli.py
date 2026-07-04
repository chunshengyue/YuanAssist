from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from .adb_device import DEFAULT_ADB_BIN, DEFAULT_EMULATOR_SERIAL, AdbError, list_adb_devices
from .app_smoke import DEFAULT_APP_SMOKE_REPORT_DIR, DEFAULT_START_WAIT_SECONDS, run_app_smoke
from .battle_static import BattleStaticInput, check_battle_static
from .cases import DEFAULT_BATTLE_CASE_DIR, reports_to_dict, save_strategy_cases, summarize_reports
from .daily_assets import check_daily_assets
from .daily_vision import DEFAULT_DAILY_VISION_DIR, check_daily_vision
from .regression import DEFAULT_REGRESSION_REPORT_DIR, run_regression
from .supabase_cli import DEFAULT_SUPABASE_BIN, SupabaseCliError, fetch_strategy_cases
from .web_ui import serve


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="yuanassist-test-tool",
        description="YuanAssist offline and device automation test helpers.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    battle = subparsers.add_parser(
        "battle-static",
        help="Check battle scriptContent and InstructionJson without launching Android.",
    )
    battle.add_argument("--case-file", type=Path, help="JSON file with scriptContent and optional instructions.")
    battle.add_argument("--script-file", type=Path, help="Text file containing battle scriptContent.")
    battle.add_argument("--script-content", help="Battle scriptContent text.")
    battle.add_argument("--instructions-file", type=Path, help="JSON file containing InstructionJson array.")
    battle.add_argument("--instructions-json", help="InstructionJson array as text.")
    battle.add_argument("--json", action="store_true", help="Print machine-readable JSON report.")
    battle.add_argument("--strict", action="store_true", help="Treat warnings as failures.")
    battle.set_defaults(func=run_battle_static)

    fetch = subparsers.add_parser(
        "fetch-strategies",
        help="Fetch public strategy battle payloads through Supabase CLI and save local cases.",
    )
    add_supabase_args(fetch)
    fetch.add_argument("--output-dir", type=Path, default=DEFAULT_BATTLE_CASE_DIR, help="Directory for saved case JSON files.")
    fetch.set_defaults(func=run_fetch_strategies)

    check = subparsers.add_parser(
        "check-strategies",
        help="Fetch strategy battle payloads through Supabase CLI and run battle-static checks.",
    )
    add_supabase_args(check)
    check.add_argument("--save-cases", action="store_true", help="Save fetched payloads as local case JSON files.")
    check.add_argument("--output-dir", type=Path, default=DEFAULT_BATTLE_CASE_DIR, help="Directory for saved case JSON files.")
    check.add_argument("--json", action="store_true", help="Print machine-readable JSON report.")
    check.add_argument("--strict", action="store_true", help="Treat warnings as failures.")
    check.set_defaults(func=run_check_strategies)

    daily = subparsers.add_parser(
        "daily-assets",
        help="Check built-in daily script JSON, task graph, template references, and segment references.",
    )
    daily.add_argument("--json", action="store_true", help="Print machine-readable JSON report.")
    daily.add_argument("--strict", action="store_true", help="Treat warnings as failures.")
    daily.set_defaults(func=run_daily_assets)

    vision = subparsers.add_parser(
        "daily-vision",
        help="Run offline visual regression cases for built-in daily scripts.",
    )
    vision.add_argument("--case-dir", type=Path, default=DEFAULT_DAILY_VISION_DIR, help="Directory containing daily vision case.json files.")
    vision.add_argument("--case-file", type=Path, help="Run a single daily vision case.json file.")
    vision.add_argument("--json", action="store_true", help="Print machine-readable JSON report.")
    vision.add_argument("--no-observations", action="store_true", help="Omit per-node observations from JSON output.")
    vision.add_argument("--strict", action="store_true", help="Treat warnings as failures.")
    vision.set_defaults(func=run_daily_vision)

    adb = subparsers.add_parser(
        "adb-devices",
        help="List USB and emulator devices through adb.",
    )
    adb.add_argument("--adb-bin", type=Path, default=None, help=f"Path to adb.exe. Default: {DEFAULT_ADB_BIN}")
    adb.add_argument("--json", action="store_true", help="Print machine-readable JSON report.")
    adb.set_defaults(func=run_adb_devices)

    smoke = subparsers.add_parser(
        "app-smoke",
        help="Install optionally, cold-start the app, capture screenshot/logcat, and scan for crashes.",
    )
    smoke.add_argument("--serial", default=DEFAULT_EMULATOR_SERIAL, help=f"ADB serial. Default: {DEFAULT_EMULATOR_SERIAL}")
    smoke.add_argument("--adb-bin", type=Path, default=None, help=f"Path to adb.exe. Default: {DEFAULT_ADB_BIN}")
    smoke.add_argument("--apk", type=Path, help="APK path. If provided, the tool installs it unless --no-install is set.")
    smoke.add_argument("--no-install", action="store_true", help="Do not install the provided APK; inspect the already installed app.")
    smoke.add_argument("--keep-logcat", action="store_true", help="Do not clear logcat before launching the app.")
    smoke.add_argument("--wait", type=float, default=DEFAULT_START_WAIT_SECONDS, help="Seconds to wait after app start before checks.")
    smoke.add_argument("--report-dir", type=Path, default=DEFAULT_APP_SMOKE_REPORT_DIR, help="Directory for smoke reports.")
    smoke.add_argument("--json", action="store_true", help="Print machine-readable JSON report.")
    smoke.add_argument("--strict", action="store_true", help="Treat warnings as failures.")
    smoke.set_defaults(func=run_app_smoke_command)

    regression = subparsers.add_parser(
        "regression",
        help="Run the default one-command regression suite and write an aggregate report.",
    )
    regression.add_argument("--include-app-smoke", action="store_true", help="Also run adb-based App smoke checks.")
    regression.add_argument("--serial", default=DEFAULT_EMULATOR_SERIAL, help=f"ADB serial for --include-app-smoke. Default: {DEFAULT_EMULATOR_SERIAL}")
    regression.add_argument("--apk", type=Path, help="APK path for --include-app-smoke.")
    regression.add_argument("--install-apk", action="store_true", help="Install --apk before App smoke checks.")
    regression.add_argument("--report-dir", type=Path, default=DEFAULT_REGRESSION_REPORT_DIR, help="Directory for aggregate regression reports.")
    regression.add_argument("--json", action="store_true", help="Print machine-readable JSON report.")
    regression.add_argument("--strict", action="store_true", help="Treat warnings as failures.")
    regression.set_defaults(func=run_regression_command)

    ui = subparsers.add_parser(
        "ui",
        help="Start a local visual test dashboard.",
    )
    ui.add_argument("--host", default="127.0.0.1", help="Host for the local dashboard.")
    ui.add_argument("--port", type=int, default=8765, help="Port for the local dashboard.")
    ui.set_defaults(func=run_ui)

    return parser


def run_battle_static(args: argparse.Namespace) -> int:
    try:
        payload = load_battle_input(args)
    except ValueError as exc:
        print(f"输入错误：{exc}")
        return 2

    report = check_battle_static(payload)

    if args.json:
        print(json.dumps(report.to_dict(), ensure_ascii=False, indent=2))
    else:
        print(report.to_text())

    if report.errors:
        return 1
    if args.strict and report.warnings:
        return 1
    return 0


def add_supabase_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--limit", type=int, default=50, help="Maximum number of strategies to fetch.")
    parser.add_argument("--supabase-bin", type=Path, default=DEFAULT_SUPABASE_BIN, help="Path to supabase.exe.")
    parser.add_argument("--include-hidden", action="store_true", help="Include hidden strategies in the read-only query.")


def run_fetch_strategies(args: argparse.Namespace) -> int:
    try:
        cases = fetch_strategy_cases(
            limit=args.limit,
            supabase_bin=args.supabase_bin,
            project_root=Path("."),
            visible_only=not args.include_hidden,
        )
        paths = save_strategy_cases(cases, args.output_dir)
    except SupabaseCliError as exc:
        print(f"Supabase CLI 错误：{exc}")
        return 2
    except OSError as exc:
        print(f"文件写入错误：{exc}")
        return 2

    print(f"已拉取 {len(cases)} 条攻略 payload，保存 {len(paths)} 个 case 到 {args.output_dir}")
    for path in paths[:10]:
        print(f"- {path}")
    if len(paths) > 10:
        print(f"... 还有 {len(paths) - 10} 个")
    return 0


def run_check_strategies(args: argparse.Namespace) -> int:
    try:
        cases = fetch_strategy_cases(
            limit=args.limit,
            supabase_bin=args.supabase_bin,
            project_root=Path("."),
            visible_only=not args.include_hidden,
        )
        reports = [check_battle_static(case.to_battle_input()) for case in cases]
        if args.save_cases:
            save_strategy_cases(cases, args.output_dir)
    except SupabaseCliError as exc:
        print(f"Supabase CLI 错误：{exc}")
        return 2

    if args.json:
        print(json.dumps(reports_to_dict(cases, reports), ensure_ascii=False, indent=2))
    else:
        summary = summarize_reports(reports)
        print(
            "Supabase 攻略检查汇总："
            f"{summary['cases']} 个用例，"
            f"{summary['passed']} 通过，"
            f"{summary['failed']} 失败，"
            f"{summary['errors']} 错误，"
            f"{summary['warnings']} 警告"
        )
        for case, report in zip(cases, reports):
            status = "失败" if report.errors else "通过"
            print(f"- [{status}] {case.title} ({case.object_id})")
            for finding in report.findings[:5]:
                location = f" [{finding.location}]" if finding.location else ""
                print(f"  {finding.severity} {finding.code}{location}: {finding.message}")

    has_errors = any(report.errors for report in reports)
    has_warnings = any(report.warnings for report in reports)
    if has_errors:
        return 1
    if args.strict and has_warnings:
        return 1
    return 0


def run_daily_assets(args: argparse.Namespace) -> int:
    report = check_daily_assets(Path("."))

    if args.json:
        print(json.dumps(report.to_dict(), ensure_ascii=False, indent=2))
    else:
        print(report.to_text())

    summary = report.summary()
    if summary["errors"]:
        return 1
    if args.strict and summary["warnings"]:
        return 1
    return 0


def run_daily_vision(args: argparse.Namespace) -> int:
    report = check_daily_vision(Path("."), case_dir=args.case_dir, case_file=args.case_file)

    if args.json:
        print(json.dumps(report.to_dict(include_observations=not args.no_observations), ensure_ascii=False, indent=2))
    else:
        print(report.to_text())

    summary = report.summary()
    if summary["errors"]:
        return 1
    if args.strict and summary["warnings"]:
        return 1
    return 0


def run_adb_devices(args: argparse.Namespace) -> int:
    try:
        report = list_adb_devices(args.adb_bin)
    except AdbError as exc:
        print(f"ADB 错误：{exc}")
        return 2

    if args.json:
        print(json.dumps(report.to_dict(), ensure_ascii=False, indent=2))
    else:
        print(report.to_text())
    return 0


def run_app_smoke_command(args: argparse.Namespace) -> int:
    try:
        report = run_app_smoke(
            Path("."),
            serial=args.serial,
            adb_bin=args.adb_bin,
            apk_path=args.apk,
            install_apk=bool(args.apk and not args.no_install),
            clear_logcat=not args.keep_logcat,
            wait_seconds=args.wait,
            report_dir=args.report_dir,
        )
    except AdbError as exc:
        print(f"ADB 错误：{exc}")
        return 2
    except OSError as exc:
        print(f"文件错误：{exc}")
        return 2

    if args.json:
        print(json.dumps(report.to_dict(Path(".")), ensure_ascii=False, indent=2))
    else:
        print(report.to_text())

    if report.errors:
        return 1
    if args.strict and report.warnings:
        return 1
    return 0


def run_regression_command(args: argparse.Namespace) -> int:
    report = run_regression(
        Path("."),
        include_app_smoke=args.include_app_smoke,
        serial=args.serial,
        apk_path=args.apk,
        install_apk=bool(args.apk and args.install_apk),
        strict=args.strict,
        report_dir=args.report_dir,
    )

    if args.json:
        print(json.dumps(report.to_dict(Path(".")), ensure_ascii=False, indent=2))
    else:
        print(report.to_text())

    if report.errors:
        return 1
    if args.strict and report.warnings:
        return 1
    return 0


def run_ui(args: argparse.Namespace) -> int:
    serve(host=args.host, port=args.port, project_root=Path("."))
    return 0


def load_battle_input(args: argparse.Namespace) -> BattleStaticInput:
    case_data: dict[str, Any] = {}
    if args.case_file:
        case_data = read_json_object(args.case_file)

    script_content = first_non_empty(
        args.script_content,
        read_text(args.script_file) if args.script_file else None,
        get_case_value(case_data, "scriptContent", "script_content", "script"),
    )

    instructions_json = first_non_empty(
        args.instructions_json,
        read_text(args.instructions_file) if args.instructions_file else None,
        get_case_value(case_data, "instructions", "instructionsJson", "instructions_json"),
    )

    if not script_content and not instructions_json:
        raise ValueError("需要提供 --script-file/--script-content，或包含 scriptContent 的 --case-file")

    title = str(get_case_value(case_data, "title", "name") or args.case_file or args.script_file or "battle-static")
    return BattleStaticInput(
        title=title,
        script_content=script_content or "",
        instructions_json=instructions_json or "",
    )


def read_text(path: Path | None) -> str:
    if path is None:
        return ""
    if not path.exists():
        raise ValueError(f"文件不存在：{path}")
    return path.read_text(encoding="utf-8")


def read_json_object(path: Path) -> dict[str, Any]:
    raw = read_text(path)
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ValueError(f"case-file 不是合法 JSON：{path} ({exc})") from exc
    if not isinstance(data, dict):
        raise ValueError("case-file 顶层必须是 JSON object")
    return data


def get_case_value(case_data: dict[str, Any], *keys: str) -> str:
    for key in keys:
        if key in case_data and case_data[key] is not None:
            value = case_data[key]
            if isinstance(value, str):
                return value
            return json.dumps(value, ensure_ascii=False)
    return ""


def first_non_empty(*values: str | None) -> str:
    for value in values:
        if value is not None and value.strip():
            return value
    return ""

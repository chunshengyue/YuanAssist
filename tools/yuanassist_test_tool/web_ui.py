from __future__ import annotations

import json
import base64
import re
import shutil
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from .adb_device import AdbError, capture_screenshot, list_adb_devices, resolve_adb_bin
from .app_smoke import DEFAULT_START_WAIT_SECONDS, run_app_smoke
from .cases import reports_to_dict, save_strategy_cases
from .daily_assets import DAILY_SCRIPT_DIR, IGNORED_DAILY_FILES, check_daily_assets
from .daily_vision import (
    DEFAULT_DAILY_VISION_DIR,
    GLOBAL_VISION_SCRIPT,
    check_daily_vision,
    check_daily_vision_case_files,
    list_daily_vision_scripts,
)
from .supabase_cli import DEFAULT_SUPABASE_BIN, SupabaseCliError, fetch_strategy_cases


VISION_CAPTURE_DRAFT_DIR = Path("tools/yuanassist_test_tool/.tmp/vision_capture")


def serve(host: str = "127.0.0.1", port: int = 8765, project_root: Path = Path(".")) -> None:
    root = project_root.resolve()
    clear_vision_capture_drafts(root)
    handler = build_handler(root)
    server = ThreadingHTTPServer((host, port), handler)
    print(f"YuanAssist 测试工具界面：http://{host}:{port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止测试工具界面")
    finally:
        server.server_close()


def build_handler(project_root: Path) -> type[BaseHTTPRequestHandler]:
    class TestToolHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802
            parsed = urlparse(self.path)
            if parsed.path == "/":
                clear_vision_capture_drafts(project_root)
                self.send_html(INDEX_HTML)
                return
            if parsed.path == "/api/fetch-check":
                self.handle_fetch_check(parsed.query)
                return
            if parsed.path == "/api/daily-assets":
                self.handle_daily_assets()
                return
            if parsed.path == "/api/basic-diagnostics":
                self.handle_basic_diagnostics()
                return
            if parsed.path == "/api/vision-scripts":
                self.handle_vision_scripts()
                return
            if parsed.path == "/api/vision-cases":
                self.handle_vision_cases()
                return
            if parsed.path == "/api/vision-regression":
                self.handle_vision_regression(parsed.query)
                return
            if parsed.path == "/api/json-file":
                self.handle_read_json_file(parsed.query)
                return
            self.send_json({"error": "not found"}, status=404)

        def do_POST(self) -> None:  # noqa: N802
            parsed = urlparse(self.path)
            if parsed.path == "/api/json-file":
                self.handle_save_json_file()
                return
            if parsed.path == "/api/vision-capture":
                self.handle_vision_capture()
                return
            if parsed.path == "/api/vision-case":
                self.handle_save_vision_case()
                return
            if parsed.path == "/api/vision-case-recapture":
                self.handle_vision_case_recapture()
                return
            if parsed.path == "/api/app-smoke":
                self.handle_app_smoke()
                return
            self.send_json({"error": "not found"}, status=404)

        def handle_fetch_check(self, query: str) -> None:
            params = parse_qs(query)
            limit = parse_int(params.get("limit", ["20"])[0], fallback=20)
            save_cases = params.get("save", ["0"])[0] in {"1", "true", "yes"}
            visible_only = params.get("visible", ["1"])[0] not in {"0", "false", "no"}
            try:
                cases = fetch_strategy_cases(
                    limit=limit,
                    supabase_bin=DEFAULT_SUPABASE_BIN,
                    project_root=project_root,
                    visible_only=visible_only,
                )
                reports = [case.to_battle_input() for case in cases]
                from .battle_static import check_battle_static

                checked = [check_battle_static(item) for item in reports]
                payload = reports_to_dict(cases, checked)
                if save_cases:
                    paths = save_strategy_cases(cases, project_root / "tools/yuanassist_test_tool/cases/battle_static")
                    payload["saved"] = [str(path.relative_to(project_root)) for path in paths]
                self.send_json(payload)
            except SupabaseCliError as exc:
                self.send_json({"error": str(exc)}, status=500)
            except Exception as exc:  # Defensive boundary for the local UI.
                self.send_json({"error": f"测试工具界面异常：{exc}"}, status=500)

        def handle_daily_assets(self) -> None:
            try:
                report = check_daily_assets(project_root)
                self.send_json(report.to_dict())
            except Exception as exc:  # Defensive boundary for the local UI.
                self.send_json({"error": f"日常脚本体检异常：{exc}"}, status=500)

        def handle_basic_diagnostics(self) -> None:
            try:
                report = list_adb_devices()
                self.send_json(report.to_dict())
            except AdbError as exc:
                self.send_json({"error": f"ADB 诊断异常：{exc}"}, status=500)
            except Exception as exc:  # Defensive boundary for the local UI.
                self.send_json({"error": f"基础诊断异常：{exc}"}, status=500)

        def handle_vision_scripts(self) -> None:
            try:
                self.send_json({"items": list_daily_vision_scripts(project_root)})
            except Exception as exc:  # Defensive boundary for the local UI.
                self.send_json({"error": f"视觉脚本列表异常：{exc}"}, status=500)

        def handle_vision_cases(self) -> None:
            try:
                self.send_json({"items": list_vision_case_summaries(project_root)})
            except Exception as exc:  # Defensive boundary for the local UI.
                self.send_json({"error": f"视觉 case 列表异常：{exc}"}, status=500)

        def handle_vision_regression(self, query: str) -> None:
            params = parse_qs(query)
            file_value = params.get("caseFile", [""])[0]
            try:
                case_file = resolve_vision_case_path(project_root, file_value) if file_value else None
                if case_file:
                    report = check_daily_vision(project_root, case_file=case_file)
                else:
                    case_files = [project_root / item["caseFile"] for item in list_vision_case_summaries(project_root)]
                    report = check_daily_vision_case_files(project_root, case_files)
                self.send_json({"report": report.to_dict(include_observations=False)})
            except ValueError as exc:
                self.send_json({"error": str(exc)}, status=400)
            except Exception as exc:  # Defensive boundary for the local UI.
                self.send_json({"error": f"视觉回归异常：{exc}"}, status=500)

        def handle_vision_capture(self) -> None:
            try:
                payload = self.read_json_body()
                script = normalize_script_name(str(payload.get("script") or GLOBAL_VISION_SCRIPT))
                serial = str(payload.get("serial") or "emulator-5554").strip()
                case_id = safe_case_id(str(payload.get("caseId") or ""))
                if not case_id:
                    case_id = f"capture_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
                title = str(payload.get("title") or case_id).strip()
                warn_unexpected = bool(payload.get("warnUnexpected", True))
                if script != GLOBAL_VISION_SCRIPT:
                    ensure_daily_script_exists(project_root, script)

                case_dir = resolve_vision_draft_dir(project_root, script, case_id)
                screenshot_path = case_dir / "screenshot.png"
                adb_bin = resolve_adb_bin()
                capture_screenshot(adb_bin, serial, screenshot_path)

                case_path = case_dir / "case.json"
                write_vision_case(
                    case_path,
                    {
                        "id": case_id,
                        "title": title,
                        "script": script,
                        "screenshot": "screenshot.png",
                        "warn_unexpected_hits": warn_unexpected,
                        "expectations": [],
                    },
                )
                report = check_daily_vision(project_root, case_file=case_path, expectation_warnings=False)
                self.send_json(
                    {
                        "caseFile": case_path.relative_to(project_root).as_posix(),
                        "caseDir": case_dir.relative_to(project_root).as_posix(),
                        "screenshot": screenshot_path.relative_to(project_root).as_posix(),
                        "screenshotDataUrl": image_data_url(screenshot_path),
                        "scripts": list_daily_vision_scripts(project_root),
                        "report": report.to_dict(include_observations=True),
                    }
                )
            except (AdbError, ValueError) as exc:
                self.send_json({"error": str(exc)}, status=400)
            except Exception as exc:  # Defensive boundary for the local UI.
                self.send_json({"error": f"截图调试异常：{exc}"}, status=500)

        def handle_save_vision_case(self) -> None:
            try:
                payload = self.read_json_body()
                case_path = resolve_saved_or_draft_vision_case_path(project_root, str(payload.get("caseFile") or ""))
                data = read_json_object(case_path)
                expectations = payload.get("expectations")
                if not isinstance(expectations, list):
                    raise ValueError("expectations 必须是数组")
                data["expectations"] = [item for item in expectations if isinstance(item, dict)]
                if "title" in payload:
                    data["title"] = str(payload.get("title") or data.get("title") or data.get("id") or "")
                if "warnUnexpected" in payload:
                    data["warn_unexpected_hits"] = bool(payload.get("warnUnexpected"))
                if is_draft_vision_case(project_root, case_path):
                    case_path = promote_draft_vision_case(project_root, case_path, data)
                else:
                    write_vision_case(case_path, data)
                report = check_daily_vision(project_root, case_file=case_path)
                screenshot_path = case_path.parent / str(data.get("screenshot") or "screenshot.png")
                self.send_json(
                    {
                        "saved": True,
                        "caseFile": case_path.relative_to(project_root).as_posix(),
                        "screenshotDataUrl": image_data_url(screenshot_path) if screenshot_path.exists() else "",
                        "report": report.to_dict(include_observations=True),
                    }
                )
            except ValueError as exc:
                self.send_json({"error": str(exc)}, status=400)
            except Exception as exc:  # Defensive boundary for the local UI.
                self.send_json({"error": f"保存视觉 case 异常：{exc}"}, status=500)

        def handle_vision_case_recapture(self) -> None:
            try:
                payload = self.read_json_body()
                case_path = resolve_vision_case_path(project_root, str(payload.get("caseFile") or ""))
                serial = str(payload.get("serial") or "emulator-5554").strip()
                data = read_json_object(case_path)
                screenshot_name = str(data.get("screenshot") or "screenshot.png")
                screenshot_path = (case_path.parent / screenshot_name).resolve()
                try:
                    screenshot_path.relative_to(case_path.parent.resolve())
                except ValueError as exc:
                    raise ValueError("screenshot 路径不允许越过 case 目录") from exc
                adb_bin = resolve_adb_bin()
                capture_screenshot(adb_bin, serial, screenshot_path)
                report = check_daily_vision(project_root, case_file=case_path)
                self.send_json(
                    {
                        "caseFile": case_path.relative_to(project_root).as_posix(),
                        "screenshotDataUrl": image_data_url(screenshot_path),
                        "report": report.to_dict(include_observations=False),
                    }
                )
            except (AdbError, ValueError) as exc:
                self.send_json({"error": str(exc)}, status=400)
            except Exception as exc:  # Defensive boundary for the local UI.
                self.send_json({"error": f"重截并回归异常：{exc}"}, status=500)

        def handle_app_smoke(self) -> None:
            try:
                payload = self.read_json_body()
                serial = str(payload.get("serial") or "emulator-5554").strip()
                apk_value = str(payload.get("apkPath") or "").strip()
                apk_path = Path(apk_value) if apk_value else None
                install_apk = bool(apk_path and payload.get("install", True))
                wait_seconds = parse_float(payload.get("waitSeconds"), DEFAULT_START_WAIT_SECONDS)
                report = run_app_smoke(
                    project_root,
                    serial=serial,
                    apk_path=apk_path,
                    install_apk=install_apk,
                    clear_logcat=bool(payload.get("clearLogcat", True)),
                    wait_seconds=wait_seconds,
                )
                data = report.to_dict(project_root)
                if report.screenshot and report.screenshot.exists():
                    data["screenshotDataUrl"] = image_data_url(report.screenshot)
                self.send_json({"report": data})
            except (AdbError, ValueError) as exc:
                self.send_json({"error": str(exc)}, status=400)
            except Exception as exc:  # Defensive boundary for the local UI.
                self.send_json({"error": f"App 冒烟体检异常：{exc}"}, status=500)

        def handle_read_json_file(self, query: str) -> None:
            params = parse_qs(query)
            file_value = params.get("file", [""])[0]
            try:
                path = resolve_daily_json_path(project_root, file_value)
                self.send_json(
                    {
                        "file": path.relative_to(project_root).as_posix(),
                        "content": path.read_text(encoding="utf-8-sig"),
                    }
                )
            except ValueError as exc:
                self.send_json({"error": str(exc)}, status=400)
            except OSError as exc:
                self.send_json({"error": f"读取 JSON 失败：{exc}"}, status=500)

        def handle_save_json_file(self) -> None:
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                self.send_json({"error": "请求长度无效"}, status=400)
                return
            if length <= 0 or length > 5 * 1024 * 1024:
                self.send_json({"error": "请求内容为空或过大"}, status=400)
                return
            try:
                raw = self.rfile.read(length).decode("utf-8")
                payload = json.loads(raw)
                if not isinstance(payload, dict):
                    raise ValueError("请求体必须是 JSON object")
                file_value = str(payload.get("file") or "")
                content = payload.get("content")
                if not isinstance(content, str):
                    raise ValueError("content 必须是字符串")
                json.loads(content)
                path = resolve_daily_json_path(project_root, file_value)
                path.write_text(content, encoding="utf-8")
                report = check_daily_assets(project_root)
                self.send_json(
                    {
                        "file": path.relative_to(project_root).as_posix(),
                        "saved": True,
                        "report": report.to_dict(),
                    }
                )
            except json.JSONDecodeError as exc:
                self.send_json({"error": f"JSON 校验失败：第 {exc.lineno} 行第 {exc.colno} 列，{exc.msg}"}, status=400)
            except ValueError as exc:
                self.send_json({"error": str(exc)}, status=400)
            except OSError as exc:
                self.send_json({"error": f"保存 JSON 失败：{exc}"}, status=500)

        def log_message(self, format: str, *args: object) -> None:
            return

        def send_html(self, html: str, status: int = 200) -> None:
            data = html.encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def send_json(self, payload: dict, status: int = 200) -> None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def read_json_body(self) -> dict:
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError as exc:
                raise ValueError("请求长度无效") from exc
            if length <= 0 or length > 5 * 1024 * 1024:
                raise ValueError("请求内容为空或过大")
            raw = self.rfile.read(length).decode("utf-8")
            payload = json.loads(raw)
            if not isinstance(payload, dict):
                raise ValueError("请求体必须是 JSON object")
            return payload

    return TestToolHandler


def parse_int(value: str, fallback: int) -> int:
    try:
        return max(1, min(int(value), 1000))
    except ValueError:
        return fallback


def parse_float(value: object, fallback: float) -> float:
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return fallback
    return max(0.0, min(parsed, 60.0))


def resolve_daily_json_path(project_root: Path, file_value: str) -> Path:
    if not file_value:
        raise ValueError("缺少 file 参数")
    normalized = file_value.replace("\\", "/").lstrip("/")
    base = (project_root / DAILY_SCRIPT_DIR).resolve()
    candidate = (project_root / normalized).resolve()
    try:
        candidate.relative_to(base)
    except ValueError as exc:
        raise ValueError("只允许访问 daily_scripts 目录下的 JSON") from exc
    if candidate.name in IGNORED_DAILY_FILES:
        raise ValueError(f"{candidate.name} 暂不允许在测试工具中编辑")
    if candidate.suffix.lower() != ".json":
        raise ValueError("只允许访问 JSON 文件")
    if not candidate.exists():
        raise ValueError(f"文件不存在：{normalized}")
    if not candidate.is_file():
        raise ValueError(f"不是文件：{normalized}")
    return candidate


def normalize_script_name(value: str) -> str:
    normalized = value.strip().replace("\\", "/").split("/")[-1]
    if not normalized:
        raise ValueError("缺少 script")
    if normalized == GLOBAL_VISION_SCRIPT:
        return normalized
    return normalized if normalized.lower().endswith(".json") else f"{normalized}.json"


def ensure_daily_script_exists(project_root: Path, script: str) -> None:
    path = (project_root / DAILY_SCRIPT_DIR / script).resolve()
    base = (project_root / DAILY_SCRIPT_DIR).resolve()
    try:
        path.relative_to(base)
    except ValueError as exc:
        raise ValueError("script 不允许越过 daily_scripts 目录") from exc
    if not path.exists() or not path.is_file():
        raise ValueError(f"脚本不存在：{script}")


def safe_case_id(value: str) -> str:
    text = value.strip().replace("\\", "/").split("/")[-1]
    text = re.sub(r"[^0-9A-Za-z._-]+", "_", text).strip("._-")
    return text[:80]


def resolve_vision_case_dir(project_root: Path, script: str, case_id: str) -> Path:
    safe_script = safe_case_id(Path(script).stem)
    safe_id = safe_case_id(case_id)
    if not safe_script or not safe_id:
        raise ValueError("case id 无效")
    base = (project_root / DEFAULT_DAILY_VISION_DIR).resolve()
    candidate = (base / safe_script / safe_id).resolve()
    try:
        candidate.relative_to(base)
    except ValueError as exc:
        raise ValueError("case 路径不允许越过 daily_vision 目录") from exc
    return candidate


def resolve_vision_draft_dir(project_root: Path, script: str, case_id: str) -> Path:
    safe_script = safe_case_id(Path(script).stem)
    safe_id = safe_case_id(case_id)
    if not safe_script or not safe_id:
        raise ValueError("case id 无效")
    base = (project_root / VISION_CAPTURE_DRAFT_DIR).resolve()
    candidate = (base / safe_script / safe_id).resolve()
    try:
        candidate.relative_to(base)
    except ValueError as exc:
        raise ValueError("草稿路径不允许越过 vision_capture 目录") from exc
    return candidate


def resolve_vision_case_path(project_root: Path, file_value: str) -> Path:
    if not file_value:
        raise ValueError("缺少 caseFile")
    normalized = file_value.replace("\\", "/").lstrip("/")
    base = (project_root / DEFAULT_DAILY_VISION_DIR).resolve()
    candidate = (project_root / normalized).resolve()
    try:
        candidate.relative_to(base)
    except ValueError as exc:
        raise ValueError("只允许保存 daily_vision 目录下的 case") from exc
    if candidate.name != "case.json":
        raise ValueError("只允许保存 case.json")
    if not candidate.exists():
        raise ValueError(f"case 不存在：{normalized}")
    return candidate


def resolve_saved_or_draft_vision_case_path(project_root: Path, file_value: str) -> Path:
    try:
        return resolve_vision_case_path(project_root, file_value)
    except ValueError:
        return resolve_vision_draft_case_path(project_root, file_value)


def resolve_vision_draft_case_path(project_root: Path, file_value: str) -> Path:
    if not file_value:
        raise ValueError("缺少 caseFile")
    normalized = file_value.replace("\\", "/").lstrip("/")
    base = (project_root / VISION_CAPTURE_DRAFT_DIR).resolve()
    candidate = (project_root / normalized).resolve()
    try:
        candidate.relative_to(base)
    except ValueError as exc:
        raise ValueError("只允许保存 vision_capture 草稿目录下的 case") from exc
    if candidate.name != "case.json":
        raise ValueError("只允许保存 case.json")
    if not candidate.exists():
        raise ValueError(f"case 草稿不存在：{normalized}")
    return candidate


def is_draft_vision_case(project_root: Path, path: Path) -> bool:
    base = (project_root / VISION_CAPTURE_DRAFT_DIR).resolve()
    try:
        path.resolve().relative_to(base)
        return True
    except ValueError:
        return False


def promote_draft_vision_case(project_root: Path, draft_case_path: Path, data: dict) -> Path:
    script = normalize_script_name(str(data.get("script") or GLOBAL_VISION_SCRIPT))
    case_id = safe_case_id(str(data.get("id") or draft_case_path.parent.name))
    target_dir = resolve_vision_case_dir(project_root, script, case_id)
    target_case_path = target_dir / "case.json"
    screenshot_name = str(data.get("screenshot") or "screenshot.png")
    draft_screenshot = (draft_case_path.parent / screenshot_name).resolve()
    target_screenshot = target_dir / "screenshot.png"
    target_dir.mkdir(parents=True, exist_ok=True)
    if draft_screenshot.exists():
        shutil.copyfile(draft_screenshot, target_screenshot)
    data["script"] = script
    data["id"] = case_id
    data["screenshot"] = "screenshot.png"
    write_vision_case(target_case_path, data)
    return target_case_path


def clear_vision_capture_drafts(project_root: Path) -> None:
    base = (project_root / VISION_CAPTURE_DRAFT_DIR).resolve()
    expected_parent = (project_root / "tools/yuanassist_test_tool/.tmp").resolve()
    try:
        base.relative_to(expected_parent)
    except ValueError:
        return
    if base.exists():
        shutil.rmtree(base)


def list_vision_case_summaries(project_root: Path) -> list[dict]:
    root = project_root.resolve()
    base = (root / DEFAULT_DAILY_VISION_DIR).resolve()
    if not base.exists():
        return []
    items: list[dict] = []
    for path in sorted(base.rglob("case.json"), key=lambda item: item.as_posix()):
        try:
            data = read_json_object(path)
        except (OSError, ValueError, json.JSONDecodeError):
            continue
        expectations = data.get("expectations") if isinstance(data.get("expectations"), list) else []
        if not expectations:
            continue
        screenshot_name = str(data.get("screenshot") or "screenshot.png")
        screenshot_path = path.parent / screenshot_name
        items.append(
            {
                "caseFile": path.relative_to(root).as_posix(),
                "id": str(data.get("id") or path.parent.name),
                "title": str(data.get("title") or data.get("id") or path.parent.name),
                "script": str(data.get("script") or ""),
                "expectations": len(expectations),
                "screenshot": screenshot_path.relative_to(root).as_posix() if screenshot_path.exists() else "",
                "screenshotExists": screenshot_path.exists(),
            }
        )
    return items


def read_json_object(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(data, dict):
        raise ValueError("case.json 顶层必须是 JSON object")
    return data


def write_vision_case(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def image_data_url(path: Path) -> str:
    data = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:image/png;base64,{data}"


INDEX_HTML = r"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>YuanAssist 测试工具</title>
  <style>
    :root {
      --ink: #2a211b;
      --muted: #7c6759;
      --paper: #f5ead8;
      --paper-soft: #fbf2e4;
      --line: #b88f5a;
      --red: #8e2f2f;
      --green: #557a43;
      --blue: #365d72;
      --shadow: rgba(72, 45, 20, .18);
    }

    * { box-sizing: border-box; }
    body {
      margin: 0;
      color: var(--ink);
      background:
        linear-gradient(90deg, rgba(118, 74, 33, .05) 1px, transparent 1px),
        linear-gradient(rgba(118, 74, 33, .04) 1px, transparent 1px),
        radial-gradient(circle at 24% 12%, rgba(142, 47, 47, .10), transparent 25%),
        #efe0c8;
      background-size: 28px 28px, 28px 28px, auto, auto;
      font-family: "Georgia", "Times New Roman", "Microsoft YaHei", serif;
    }

    main {
      width: min(1180px, calc(100vw - 32px));
      margin: 0 auto;
      padding: 34px 0 48px;
    }

    header {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 24px;
      align-items: end;
      margin-bottom: 16px;
      border-bottom: 2px solid rgba(120, 83, 44, .28);
      padding-bottom: 18px;
    }

    h1 {
      margin: 0;
      font-size: 38px;
      line-height: 1.05;
      font-weight: 800;
      color: #5a2520;
    }

    .subtitle {
      margin-top: 10px;
      color: var(--muted);
      font-size: 15px;
    }

    .panel {
      background: linear-gradient(180deg, rgba(255,255,255,.42), rgba(255,255,255,.12)), var(--paper);
      border: 1px solid rgba(126, 88, 48, .42);
      box-shadow: 0 16px 42px var(--shadow);
      border-radius: 8px;
    }

    .tabs {
      display: flex;
      gap: 10px;
      margin: 0 0 18px;
      flex-wrap: wrap;
    }

    .tab {
      min-height: 38px;
      color: var(--ink);
      background: rgba(255, 255, 255, .28);
      border-color: rgba(126, 88, 48, .40);
      box-shadow: none;
    }

    .tab.active {
      color: #fff7eb;
      background: linear-gradient(180deg, #8b453a, #652b27);
      border-color: #6c442e;
      box-shadow: 0 8px 16px rgba(77, 40, 28, .2);
    }

    .toolbar {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 14px;
      align-items: center;
      padding: 18px;
      margin-bottom: 18px;
    }

    .controls {
      display: grid;
      grid-template-columns: 160px 160px 1fr auto;
      gap: 14px;
      align-items: end;
      padding: 18px;
      margin-bottom: 18px;
    }

    .vision-controls {
      grid-template-columns: 190px minmax(220px, 1fr) minmax(180px, 1fr) minmax(180px, 1fr) auto;
    }

    label {
      display: block;
      color: var(--muted);
      font-size: 13px;
      margin-bottom: 7px;
      font-weight: 700;
    }

    input[type="number"],
    input[type="text"],
    select {
      width: 100%;
      border: 1px solid rgba(126, 88, 48, .48);
      background: rgba(255,255,255,.45);
      color: var(--ink);
      border-radius: 6px;
      padding: 10px 12px;
      font: inherit;
    }

    select {
      min-height: 42px;
    }

    .checkline {
      display: flex;
      gap: 10px;
      align-items: center;
      height: 42px;
      color: var(--muted);
      font-weight: 700;
      font-size: 14px;
    }

    button {
      border: 1px solid #6c442e;
      background: linear-gradient(180deg, #8b453a, #652b27);
      color: #fff7eb;
      border-radius: 6px;
      min-height: 42px;
      padding: 0 18px;
      font: inherit;
      font-weight: 800;
      cursor: pointer;
      box-shadow: 0 8px 16px rgba(77, 40, 28, .2);
    }

    button:disabled { opacity: .6; cursor: wait; }

    .summary {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 12px;
      margin-bottom: 18px;
    }

    .metric {
      padding: 14px;
      min-height: 86px;
    }

    .metric .value {
      font-size: 30px;
      font-weight: 900;
      line-height: 1;
      color: #4b241d;
    }

    .metric .label {
      margin-top: 9px;
      color: var(--muted);
      font-size: 13px;
      font-weight: 700;
    }

    .content {
      display: grid;
      grid-template-columns: 1fr;
      gap: 12px;
    }

    .item {
      padding: 16px 18px;
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 12px;
      align-items: start;
    }

    .item h2 {
      margin: 0;
      font-size: 18px;
      color: #45231d;
      overflow-wrap: anywhere;
    }

    .meta {
      margin-top: 7px;
      color: var(--muted);
      font-size: 13px;
      line-height: 1.7;
    }

    .badge {
      display: inline-flex;
      align-items: center;
      height: 28px;
      padding: 0 10px;
      border-radius: 999px;
      color: white;
      font-weight: 800;
      font-size: 12px;
      background: var(--green);
      white-space: nowrap;
    }

    .badge.fail { background: var(--red); }
    .badge.warn { background: var(--blue); }

    .findings {
      grid-column: 1 / -1;
      margin-top: 8px;
      display: grid;
      gap: 8px;
    }

    .finding {
      padding: 10px 12px;
      border-left: 4px solid var(--blue);
      background: rgba(255,255,255,.36);
      border-radius: 6px;
      color: var(--ink);
      font-size: 13px;
      line-height: 1.55;
      overflow-wrap: anywhere;
    }

    .finding.error { border-left-color: var(--red); }
    .finding.clickable {
      cursor: pointer;
      transition: transform .12s ease, background .12s ease;
    }

    .finding.clickable:hover {
      background: rgba(255,255,255,.56);
      transform: translateX(2px);
    }

    .item-actions {
      display: flex;
      gap: 10px;
      justify-content: flex-end;
      align-items: center;
      flex-wrap: wrap;
    }

    .ghost-button {
      min-height: 28px;
      padding: 0 10px;
      color: var(--ink);
      background: rgba(255,255,255,.34);
      border-color: rgba(126, 88, 48, .45);
      box-shadow: none;
      font-size: 12px;
    }

    .state {
      padding: 18px;
      color: var(--muted);
      font-weight: 700;
      line-height: 1.7;
    }

    .vision-grid {
      display: grid;
      grid-template-columns: minmax(280px, 440px) minmax(0, 1fr);
      gap: 14px;
      align-items: start;
    }

    .vision-shot {
      width: 100%;
      max-height: 720px;
      object-fit: contain;
      background: rgba(38, 28, 24, .92);
      border: 1px solid rgba(126, 88, 48, .42);
      border-radius: 8px;
      box-shadow: 0 12px 28px rgba(72, 45, 20, .18);
    }

    .vision-list {
      display: grid;
      gap: 10px;
    }

    .vision-group {
      border: 1px solid rgba(126, 88, 48, .36);
      background: rgba(255,255,255,.20);
      border-radius: 8px;
      overflow: hidden;
    }

    .vision-group summary {
      cursor: pointer;
      list-style: none;
      min-height: 42px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 10px 14px;
      color: #45231d;
      font-weight: 900;
      background: rgba(255,255,255,.32);
      border-bottom: 1px solid rgba(126, 88, 48, .18);
    }

    .vision-group summary::-webkit-details-marker { display: none; }

    .vision-group summary::after {
      content: "展开";
      color: var(--muted);
      font-size: 12px;
      font-weight: 800;
    }

    .vision-group[open] summary::after { content: "收起"; }

    .vision-group-body {
      display: grid;
      gap: 10px;
      padding: 10px;
    }

    .vision-row {
      padding: 12px;
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 10px;
      align-items: center;
      background: rgba(255,255,255,.30);
      border: 1px solid rgba(126, 88, 48, .24);
      border-radius: 7px;
    }

    .vision-row.selected-hit {
      border-color: #2f7a32;
      border-left: 8px solid #2f7a32;
      background: linear-gradient(90deg, rgba(47, 122, 50, .24), rgba(255,255,255,.34));
      box-shadow: inset 0 0 0 1px rgba(47, 122, 50, .22);
    }
    .vision-row.selected-miss {
      border-color: #a12c2c;
      border-left: 8px solid #a12c2c;
      background: linear-gradient(90deg, rgba(161, 44, 44, .24), rgba(255,255,255,.34));
      box-shadow: inset 0 0 0 1px rgba(161, 44, 44, .22);
    }
    .vision-row.selected-ignore { border-color: rgba(124, 103, 89, .60); background: rgba(124, 103, 89, .08); }

    .vision-actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      justify-content: flex-end;
    }

    .vision-actions button {
      min-height: 30px;
      padding: 0 10px;
      font-size: 12px;
    }

    .vision-ocr-input {
      margin-top: 8px;
      max-width: 360px;
    }

    .tab-page { display: none; }
    .tab-page.active { display: block; }

    .json-drawer {
      position: fixed;
      top: 0;
      right: 0;
      width: min(720px, 92vw);
      height: 100vh;
      z-index: 20;
      transform: translateX(104%);
      transition: transform .18s ease;
      background: #2a211b;
      color: #f8ead7;
      border-left: 1px solid rgba(184, 143, 90, .72);
      box-shadow: -18px 0 48px rgba(48, 30, 18, .34);
      display: grid;
      grid-template-columns: 10px 1fr;
    }

    .json-drawer.open { transform: translateX(0); }

    .drawer-resizer {
      cursor: col-resize;
      background: linear-gradient(90deg, rgba(184,143,90,.28), rgba(184,143,90,.06));
    }

    .drawer-shell {
      min-width: 0;
      display: grid;
      grid-template-rows: auto auto 1fr auto;
      height: 100vh;
    }

    .drawer-head {
      padding: 16px 18px 10px;
      border-bottom: 1px solid rgba(184, 143, 90, .38);
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 12px;
      align-items: start;
    }

    .drawer-title {
      font-size: 18px;
      font-weight: 900;
      color: #ffe8bf;
      overflow-wrap: anywhere;
    }

    .drawer-path {
      margin-top: 6px;
      color: #c9aa80;
      font-size: 12px;
      overflow-wrap: anywhere;
    }

    .drawer-tools {
      display: flex;
      gap: 8px;
      padding: 10px 18px;
      border-bottom: 1px solid rgba(184, 143, 90, .22);
      flex-wrap: wrap;
    }

    .drawer-tools button,
    .drawer-head button {
      min-height: 32px;
      padding: 0 12px;
      font-size: 12px;
      background: rgba(248, 234, 215, .10);
      color: #ffe8bf;
      border-color: rgba(248, 234, 215, .28);
      box-shadow: none;
    }

    .editor-wrap {
      min-height: 0;
      display: grid;
      grid-template-columns: 1fr;
      position: relative;
    }

    .json-highlight,
    .json-editor {
      grid-area: 1 / 1;
      margin: 0;
      padding: 16px 18px;
      border: 0;
      outline: 0;
      overflow: auto;
      white-space: pre;
      tab-size: 2;
      font: 13px/1.55 "Consolas", "Cascadia Mono", "Microsoft YaHei UI", monospace;
    }

    .json-highlight {
      pointer-events: none;
      color: #ead6bd;
      background:
        linear-gradient(rgba(255,255,255,.035) 1px, transparent 1px),
        #261c18;
      background-size: 100% 24.8px;
    }

    .json-editor {
      resize: none;
      color: transparent;
      caret-color: #fff0d0;
      background: transparent;
      -webkit-text-fill-color: transparent;
    }

    .json-key { color: #e9b66d; }
    .json-string { color: #9fd0a2; }
    .json-number { color: #91c7e0; }
    .json-bool { color: #d9a1a1; }
    .json-null { color: #c6a2dd; }
    .json-mark { color: #d0b58a; }
    .json-tag-name,
    .json-tag-template,
    .json-tag-ocr,
    .json-tag-threshold,
    .json-tag-align {
      border-radius: 3px;
      box-shadow:
        inset 0 -1px 0 currentColor,
        inset 0 0 0 999px rgba(255, 255, 255, .035);
    }
    .json-tag-name {
      color: #ffe39c;
      background: rgba(255, 214, 126, .12);
    }
    .json-tag-template {
      color: #9ff5dc;
      background: rgba(68, 201, 171, .12);
    }
    .json-tag-ocr {
      color: #e7ccff;
      background: rgba(187, 139, 255, .12);
    }
    .json-tag-threshold {
      color: #ffd09e;
      background: rgba(255, 170, 94, .12);
    }
    .json-tag-align {
      color: #b9d5ff;
      background: rgba(118, 169, 255, .12);
    }
    .jump-flash { animation: flashLine 1.2s ease; }

    @keyframes flashLine {
      0% { box-shadow: inset 0 0 0 9999px rgba(255, 216, 126, .16); }
      100% { box-shadow: none; }
    }

    .drawer-status {
      min-height: 44px;
      padding: 10px 18px;
      color: #c9aa80;
      border-top: 1px solid rgba(184, 143, 90, .28);
      font-size: 12px;
      overflow-wrap: anywhere;
    }

    .drawer-status.error { color: #ffb4a8; }
    .drawer-status.ok { color: #bfe2a3; }

    @media (max-width: 820px) {
      header, .toolbar, .controls { grid-template-columns: 1fr; }
      .summary { grid-template-columns: repeat(2, 1fr); }
      .item { grid-template-columns: 1fr; }
      .vision-grid { grid-template-columns: 1fr; }
      .json-drawer { width: 100vw; }
    }
  </style>
</head>
<body>
  <main>
    <header>
      <div>
        <h1>YuanAssist 测试工具</h1>
        <div class="subtitle">优先检查内置脚本、模板资产和运行链路里真正容易埋问题的地方。</div>
      </div>
    </header>

    <nav class="tabs" aria-label="测试类型">
      <button class="tab active" data-tab="basic" type="button">基础诊断</button>
      <button class="tab" data-tab="smoke" type="button">App 冒烟</button>
      <button class="tab" data-tab="vision" type="button">截图调试</button>
      <button class="tab" data-tab="daily" type="button">日常脚本体检</button>
      <button class="tab" data-tab="battle" type="button">战斗攻略检查</button>
    </nav>

    <section id="basic-page" class="tab-page active">
      <section class="panel toolbar">
        <div class="state" id="basic-status">查看 adb、本地模拟器目标和当前 USB / 模拟器设备。</div>
        <button id="run-basic" type="button">刷新诊断</button>
      </section>
      <section class="summary" id="basic-summary"></section>
      <section class="content" id="basic-content">
        <div class="panel state">点击“刷新诊断”读取当前 adb 设备状态。</div>
      </section>
    </section>

    <section id="smoke-page" class="tab-page">
      <section class="panel controls">
        <div>
          <label for="smoke-device">ADB 目标</label>
          <select id="smoke-device"></select>
        </div>
        <div>
          <label for="smoke-apk">APK 路径</label>
          <input id="smoke-apk" type="text" placeholder="app/build/outputs/apk/debug/app-debug.apk" />
        </div>
        <div>
          <label for="smoke-wait">启动等待秒数</label>
          <input id="smoke-wait" type="number" min="0" max="60" step="0.5" value="4" />
        </div>
        <button id="run-smoke" type="button">运行冒烟</button>
      </section>
      <section class="panel toolbar">
        <label class="checkline">
          <input id="smoke-install" type="checkbox" checked />
          提供 APK 时先安装
        </label>
        <label class="checkline">
          <input id="smoke-clear-logcat" type="checkbox" checked />
          启动前清理 logcat
        </label>
      </section>
      <section class="panel state" id="smoke-status">检查安装、启动、权限、截图和崩溃日志。</section>
      <section class="summary" id="smoke-summary"></section>
      <section class="content" id="smoke-content">
        <div class="panel state">选择设备后运行冒烟体检。APK 路径可留空，用于检查已安装应用。</div>
      </section>
    </section>

    <section id="vision-page" class="tab-page">
      <section class="panel controls vision-controls">
        <div>
          <label for="vision-device">ADB 目标</label>
          <select id="vision-device"></select>
        </div>
        <div>
          <label for="vision-script">匹配范围</label>
          <select id="vision-script"></select>
        </div>
        <div>
          <label for="vision-case-id">素材 ID</label>
          <input id="vision-case-id" type="text" placeholder="自动生成" />
        </div>
        <div>
          <label for="vision-title">素材标题</label>
          <input id="vision-title" type="text" placeholder="截图调试素材" />
        </div>
        <button id="run-vision-capture" type="button">采集截图</button>
      </section>
      <section class="panel toolbar">
        <label class="checkline">
          <input id="vision-warn-unexpected" type="checkbox" checked />
          保存后回归时提醒未声明命中
        </label>
        <button id="save-vision-case" type="button" disabled>保存回归素材</button>
      </section>
      <section class="panel state" id="vision-status">等待采集</section>
      <section class="panel controls">
        <div>
          <label for="vision-case-select">回归 Case</label>
          <select id="vision-case-select"></select>
        </div>
        <button id="refresh-vision-cases" type="button">刷新列表</button>
        <button id="run-vision-all" type="button">测试全部</button>
        <button id="run-vision-one" type="button">测试选中</button>
        <button id="recapture-vision-one" type="button">重截选中并测试</button>
      </section>
      <section class="content" id="vision-regression-content">
        <div class="panel state">保存素材后，可在这里运行全部或单个回归 case。</div>
      </section>
      <section class="content" id="vision-content">
        <div class="panel state">选择设备后采集当前画面，并用全部日常素材单位进行匹配。</div>
      </section>
    </section>

    <section id="daily-page" class="tab-page">
      <section class="panel toolbar">
        <div class="state" id="daily-status">检查内置 daily_scripts、模板文件、子脚本引用和任务跳转。</div>
        <button id="run-daily" type="button">开始体检</button>
      </section>
      <section class="summary" id="daily-summary"></section>
      <section class="content" id="daily-content">
        <div class="panel state">点击“开始体检”扫描本地内置日常脚本。</div>
      </section>
    </section>

    <section id="battle-page" class="tab-page">
      <section class="panel controls">
        <div>
          <label for="limit">拉取数量</label>
          <input id="limit" type="number" min="1" max="1000" value="20" />
        </div>
        <label class="checkline">
          <input id="visible" type="checkbox" checked />
          仅公开攻略
        </label>
        <label class="checkline">
          <input id="save" type="checkbox" />
          保存为本地 case
        </label>
        <button id="run-battle" type="button">拉取并检查</button>
      </section>
      <section class="panel state" id="battle-status">等待执行</section>
      <section class="summary" id="battle-summary"></section>
      <section class="content" id="battle-content">
        <div class="panel state">点击“拉取并检查”开始。</div>
      </section>
    </section>
  </main>

  <aside class="json-drawer" id="json-drawer" aria-label="JSON 编辑器">
    <div class="drawer-resizer" id="drawer-resizer"></div>
    <div class="drawer-shell">
      <div class="drawer-head">
        <div>
          <div class="drawer-title" id="drawer-title">JSON</div>
          <div class="drawer-path" id="drawer-path"></div>
        </div>
        <button id="close-json" type="button">关闭</button>
      </div>
      <div class="drawer-tools">
        <button id="save-json" type="button">保存</button>
        <button id="format-json" type="button">格式化</button>
        <button id="reload-json" type="button">重新加载</button>
      </div>
      <div class="editor-wrap">
        <pre class="json-highlight" id="json-highlight" aria-hidden="true"></pre>
        <textarea class="json-editor" id="json-editor" spellcheck="false"></textarea>
      </div>
      <div class="drawer-status" id="drawer-status">未打开文件</div>
    </div>
  </aside>

  <script>
    const tabs = document.querySelectorAll(".tab");
    const runBasicButton = document.querySelector("#run-basic");
    const runSmokeButton = document.querySelector("#run-smoke");
    const runVisionCaptureButton = document.querySelector("#run-vision-capture");
    const saveVisionCaseButton = document.querySelector("#save-vision-case");
    const refreshVisionCasesButton = document.querySelector("#refresh-vision-cases");
    const runVisionAllButton = document.querySelector("#run-vision-all");
    const runVisionOneButton = document.querySelector("#run-vision-one");
    const recaptureVisionOneButton = document.querySelector("#recapture-vision-one");
    const runDailyButton = document.querySelector("#run-daily");
    const runBattleButton = document.querySelector("#run-battle");
    const drawer = document.querySelector("#json-drawer");
    const editor = document.querySelector("#json-editor");
    const highlight = document.querySelector("#json-highlight");
    const drawerStatus = document.querySelector("#drawer-status");
    const drawerTitle = document.querySelector("#drawer-title");
    const drawerPath = document.querySelector("#drawer-path");
    let currentDailyData = null;
    let currentJsonFile = "";
    let pendingJump = null;
    let currentVisionScripts = [];
    let currentVisionCases = [];
    let currentVisionCapture = null;
    let currentVisionExpectations = new Map();

    tabs.forEach(tab => {
      tab.addEventListener("click", () => {
        tabs.forEach(item => item.classList.toggle("active", item === tab));
        document.querySelectorAll(".tab-page").forEach(page => {
          page.classList.toggle("active", page.id === `${tab.dataset.tab}-page`);
        });
        if (tab.dataset.tab === "vision" && !currentVisionScripts.length) {
          loadVisionSetup();
        }
        if (tab.dataset.tab === "smoke" && !document.querySelector("#smoke-device").options.length) {
          loadSmokeSetup();
        }
      });
    });

    async function loadSmokeSetup() {
      const statusEl = document.querySelector("#smoke-status");
      statusEl.textContent = "正在加载 adb 设备...";
      try {
        const res = await fetch("/api/basic-diagnostics");
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || "ADB 诊断失败");
        renderSmokeDeviceOptions(data);
        statusEl.textContent = "设备列表已加载";
      } catch (error) {
        statusEl.textContent = `加载失败：${error.message}`;
      }
    }

    async function loadVisionSetup() {
      const statusEl = document.querySelector("#vision-status");
      statusEl.textContent = "正在加载截图调试配置...";
      try {
        const [diagRes, scriptsRes] = await Promise.all([
          fetch("/api/basic-diagnostics"),
          fetch("/api/vision-scripts")
        ]);
        const diag = await diagRes.json();
        const scripts = await scriptsRes.json();
        if (!diagRes.ok || diag.error) throw new Error(diag.error || "ADB 诊断失败");
        if (!scriptsRes.ok || scripts.error) throw new Error(scripts.error || "脚本列表失败");
        renderVisionDeviceOptions(diag);
        currentVisionScripts = scripts.items || [];
        renderVisionScriptOptions(currentVisionScripts);
        await loadVisionCases();
        statusEl.textContent = "截图调试配置已加载";
      } catch (error) {
        statusEl.textContent = `加载失败：${error.message}`;
      }
    }

    async function loadVisionCases() {
      const res = await fetch("/api/vision-cases");
      const data = await res.json();
      if (!res.ok || data.error) throw new Error(data.error || "case 列表失败");
      currentVisionCases = data.items || [];
      renderVisionCaseOptions(currentVisionCases);
    }

    runBasicButton.addEventListener("click", async () => {
      const statusEl = document.querySelector("#basic-status");
      const summaryEl = document.querySelector("#basic-summary");
      const contentEl = document.querySelector("#basic-content");
      runBasicButton.disabled = true;
      statusEl.textContent = "正在读取 adb 设备...";
      contentEl.innerHTML = '<div class="panel state">诊断中...</div>';
      summaryEl.innerHTML = "";
      try {
        const res = await fetch("/api/basic-diagnostics");
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || "请求失败");
        renderBasic(data);
        statusEl.textContent = "诊断完成";
      } catch (error) {
        statusEl.textContent = "诊断失败";
        contentEl.innerHTML = `<div class="panel state">${escapeHtml(error.message)}</div>`;
      } finally {
        runBasicButton.disabled = false;
      }
    });

    runSmokeButton.addEventListener("click", async () => {
      const statusEl = document.querySelector("#smoke-status");
      const summaryEl = document.querySelector("#smoke-summary");
      const contentEl = document.querySelector("#smoke-content");
      const serial = document.querySelector("#smoke-device").value || "emulator-5554";
      const apkPath = document.querySelector("#smoke-apk").value || "";
      const waitSeconds = document.querySelector("#smoke-wait").value || "4";
      runSmokeButton.disabled = true;
      statusEl.textContent = "正在运行 App 冒烟体检...";
      summaryEl.innerHTML = "";
      contentEl.innerHTML = '<div class="panel state">安装、启动和采集日志中...</div>';
      try {
        const res = await fetch("/api/app-smoke", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            serial,
            apkPath,
            waitSeconds,
            install: document.querySelector("#smoke-install").checked,
            clearLogcat: document.querySelector("#smoke-clear-logcat").checked
          })
        });
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || "冒烟失败");
        renderSmoke(data.report);
        const s = data.report?.summary || {};
        statusEl.textContent = `冒烟完成：${s.passed ? "通过" : "失败"}，${s.errors || 0} 错误，${s.warnings || 0} 提醒`;
      } catch (error) {
        statusEl.textContent = "冒烟失败";
        contentEl.innerHTML = `<div class="panel state">${escapeHtml(error.message)}</div>`;
      } finally {
        runSmokeButton.disabled = false;
      }
    });

    runVisionCaptureButton.addEventListener("click", async () => {
      const statusEl = document.querySelector("#vision-status");
      const contentEl = document.querySelector("#vision-content");
      const serial = document.querySelector("#vision-device").value || "emulator-5554";
      const script = document.querySelector("#vision-script").value;
      const caseId = document.querySelector("#vision-case-id").value;
      const title = document.querySelector("#vision-title").value;
      const warnUnexpected = document.querySelector("#vision-warn-unexpected").checked;
      if (!script) {
        statusEl.textContent = "请先选择日常脚本";
        return;
      }
      runVisionCaptureButton.disabled = true;
      saveVisionCaseButton.disabled = true;
      statusEl.textContent = "正在通过 adb 采集截图...";
      contentEl.innerHTML = '<div class="panel state">采集中...</div>';
      try {
        const res = await fetch("/api/vision-capture", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ serial, script, caseId, title, warnUnexpected })
        });
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || "采集失败");
        currentVisionCapture = data;
        currentVisionExpectations = new Map();
        renderVisionCapture(data);
        statusEl.textContent = `采集完成：${data.caseFile}`;
        saveVisionCaseButton.disabled = false;
      } catch (error) {
        statusEl.textContent = "采集失败";
        contentEl.innerHTML = `<div class="panel state">${escapeHtml(error.message)}</div>`;
      } finally {
        runVisionCaptureButton.disabled = false;
      }
    });

    saveVisionCaseButton.addEventListener("click", async () => {
      if (!currentVisionCapture?.caseFile) return;
      const statusEl = document.querySelector("#vision-status");
      const expectations = buildVisionExpectations();
      saveVisionCaseButton.disabled = true;
      statusEl.textContent = "正在保存回归素材...";
      try {
        const res = await fetch("/api/vision-case", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            caseFile: currentVisionCapture.caseFile,
            title: document.querySelector("#vision-title").value,
            warnUnexpected: document.querySelector("#vision-warn-unexpected").checked,
            expectations
          })
        });
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || "保存失败");
        currentVisionCapture.report = data.report;
        statusEl.textContent = `已保存：${data.caseFile}，期望 ${expectations.length} 条`;
        await loadVisionCases();
        document.querySelector("#vision-case-select").value = data.caseFile;
        saveVisionCaseButton.disabled = false;
      } catch (error) {
        statusEl.textContent = `保存失败：${error.message}`;
        saveVisionCaseButton.disabled = false;
      }
    });

    refreshVisionCasesButton.addEventListener("click", async () => {
      try {
        await loadVisionCases();
        document.querySelector("#vision-status").textContent = `已刷新回归 case：${currentVisionCases.length} 个`;
      } catch (error) {
        document.querySelector("#vision-status").textContent = `刷新失败：${error.message}`;
      }
    });

    runVisionAllButton.addEventListener("click", () => runVisionRegression(""));
    runVisionOneButton.addEventListener("click", () => {
      const caseFile = document.querySelector("#vision-case-select").value;
      if (!caseFile) {
        document.querySelector("#vision-status").textContent = "请先选择回归 case";
        return;
      }
      runVisionRegression(caseFile);
    });

    recaptureVisionOneButton.addEventListener("click", async () => {
      const statusEl = document.querySelector("#vision-status");
      const caseFile = document.querySelector("#vision-case-select").value;
      const serial = document.querySelector("#vision-device").value || "emulator-5554";
      if (!caseFile) {
        statusEl.textContent = "请先选择回归 case";
        return;
      }
      recaptureVisionOneButton.disabled = true;
      statusEl.textContent = "正在重截选中 case 并运行回归...";
      try {
        const res = await fetch("/api/vision-case-recapture", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ caseFile, serial })
        });
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || "重截失败");
        renderVisionRegression(data.report);
        statusEl.textContent = `重截并测试完成：${data.caseFile}`;
      } catch (error) {
        statusEl.textContent = `重截失败：${error.message}`;
      } finally {
        recaptureVisionOneButton.disabled = false;
      }
    });

    async function runVisionRegression(caseFile) {
      const statusEl = document.querySelector("#vision-status");
      const query = caseFile ? `?caseFile=${encodeURIComponent(caseFile)}` : "";
      statusEl.textContent = caseFile ? "正在测试选中 case..." : "正在测试全部 case...";
      try {
        const res = await fetch(`/api/vision-regression${query}`);
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || "回归失败");
        renderVisionRegression(data.report);
        const s = data.report?.summary || {};
        statusEl.textContent = `回归完成：${s.cases || 0} case，${s.errors || 0} 错误，${s.warnings || 0} 提醒`;
      } catch (error) {
        statusEl.textContent = `回归失败：${error.message}`;
      }
    }

    runDailyButton.addEventListener("click", async () => {
      const statusEl = document.querySelector("#daily-status");
      const summaryEl = document.querySelector("#daily-summary");
      const contentEl = document.querySelector("#daily-content");
      runDailyButton.disabled = true;
      statusEl.textContent = "正在扫描本地脚本和模板资产...";
      contentEl.innerHTML = '<div class="panel state">体检中...</div>';
      summaryEl.innerHTML = "";
      try {
        const res = await fetch("/api/daily-assets");
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || "请求失败");
        currentDailyData = data;
        renderDaily(data);
        statusEl.textContent = "体检完成";
      } catch (error) {
        statusEl.textContent = "体检失败";
        contentEl.innerHTML = `<div class="panel state">${escapeHtml(error.message)}</div>`;
      } finally {
        runDailyButton.disabled = false;
      }
    });

    runBattleButton.addEventListener("click", async () => {
      const statusEl = document.querySelector("#battle-status");
      const summaryEl = document.querySelector("#battle-summary");
      const contentEl = document.querySelector("#battle-content");
      runBattleButton.disabled = true;
      statusEl.textContent = "正在通过 Supabase CLI 查询...";
      contentEl.innerHTML = '<div class="panel state">查询和检查中...</div>';
      summaryEl.innerHTML = "";
      const limit = document.querySelector("#limit").value || "20";
      const save = document.querySelector("#save").checked ? "1" : "0";
      const visible = document.querySelector("#visible").checked ? "1" : "0";
      try {
        const res = await fetch(`/api/fetch-check?limit=${encodeURIComponent(limit)}&save=${save}&visible=${visible}`);
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || "请求失败");
        renderBattle(data);
        statusEl.textContent = data.saved ? `完成，已保存 ${data.saved.length} 个 case` : "完成";
      } catch (error) {
        statusEl.textContent = "失败";
        contentEl.innerHTML = `<div class="panel state">${escapeHtml(error.message)}</div>`;
      } finally {
        runBattleButton.disabled = false;
      }
    });

    function renderVisionDeviceOptions(data) {
      const select = document.querySelector("#vision-device");
      const target = data.defaultTarget || { serial: "emulator-5554", kind: "emulator", state: "missing" };
      const devices = data.devices || [];
      const seen = new Set();
      const options = [];
      options.push({ serial: target.serial || "emulator-5554", label: `${target.serial || "emulator-5554"} · 默认模拟器 · ${target.state || "missing"}` });
      seen.add(target.serial || "emulator-5554");
      devices.forEach(device => {
        if (seen.has(device.serial)) return;
        seen.add(device.serial);
        options.push({ serial: device.serial, label: `${device.serial} · ${device.kind} · ${device.state}` });
      });
      select.innerHTML = options.map(item => `<option value="${escapeAttr(item.serial)}">${escapeHtml(item.label)}</option>`).join("");
    }

    function renderSmokeDeviceOptions(data) {
      const select = document.querySelector("#smoke-device");
      const target = data.defaultTarget || { serial: "emulator-5554", kind: "emulator", state: "missing" };
      const devices = data.devices || [];
      const seen = new Set();
      const options = [];
      options.push({ serial: target.serial || "emulator-5554", label: `${target.serial || "emulator-5554"} · 默认模拟器 · ${target.state || "missing"}` });
      seen.add(target.serial || "emulator-5554");
      devices.forEach(device => {
        if (seen.has(device.serial)) return;
        seen.add(device.serial);
        options.push({ serial: device.serial, label: `${device.serial} · ${device.kind} · ${device.state}` });
      });
      select.innerHTML = options.map(item => `<option value="${escapeAttr(item.serial)}">${escapeHtml(item.label)}</option>`).join("");
    }

    function renderSmoke(report) {
      const s = report?.summary || {};
      document.querySelector("#smoke-summary").innerHTML = [
        metric("通过", s.passed ? 1 : 0),
        metric("错误", s.errors),
        metric("提醒", s.warnings),
        metric("步骤", s.steps),
        metric("已安装", s.installed ? 1 : 0),
        metric("进程", s.processAlive ? 1 : 0)
      ].join("");

      const contentEl = document.querySelector("#smoke-content");
      const findings = report.findings || [];
      const steps = report.steps || [];
      const screenshot = report.screenshotDataUrl
        ? `<img class="vision-shot" src="${report.screenshotDataUrl}" alt="启动截图" />`
        : '<div class="panel state">没有启动截图</div>';
      const packageInfo = report.installedPackage || {};
      const permissions = report.permissions || {};
      contentEl.innerHTML = `
        <section class="vision-grid">
          <div>${screenshot}</div>
          <div class="vision-list">
            <article class="panel item">
              <div>
                <h2>${escapeHtml(report.appId || "应用")}</h2>
                <div class="meta">${escapeHtml(report.serial || "")} · ${escapeHtml(report.mainActivity || "")}</div>
                <div class="meta">versionCode=${escapeHtml(packageInfo.versionCode ?? "-")} · versionName=${escapeHtml(packageInfo.versionName || "-")}</div>
                <div class="meta">报告：${escapeHtml(report.reportDir || "")}</div>
              </div>
              <span class="badge ${s.passed ? "" : "fail"}">${s.passed ? "通过" : "失败"}</span>
            </article>
            <article class="panel item">
              <div>
                <h2>权限状态</h2>
                <div class="meta">无障碍=${permissions.accessibilityEnabled ? "已开" : "未开"} · 悬浮窗=${permissions.overlayAllowed ? "已允许" : "未确认"}</div>
                <div class="meta">${escapeHtml(permissions.overlayRaw || "")}</div>
              </div>
              <span class="badge ${permissions.accessibilityEnabled && permissions.overlayAllowed ? "" : "warn"}">权限</span>
            </article>
            ${findings.length ? `<article class="panel item"><div><h2>异常摘要</h2><div class="findings">${findings.map(f => renderFinding(f, "", false)).join("")}</div></div></article>` : ""}
            <details class="vision-group" open>
              <summary>执行步骤（${steps.length}）</summary>
              <div class="vision-group-body">
                ${steps.map(step => `
                  <article class="vision-row panel">
                    <div>
                      <h2>${escapeHtml(step.name || "")}</h2>
                      <div class="meta">${escapeHtml(step.status || "")} · ${escapeHtml(step.message || "")}</div>
                    </div>
                    <span class="badge ${step.status === "error" ? "fail" : step.status === "warn" ? "warn" : ""}">${escapeHtml(step.status || "")}</span>
                  </article>
                `).join("")}
              </div>
            </details>
          </div>
        </section>
      `;
    }

    function renderVisionScriptOptions(items) {
      const select = document.querySelector("#vision-script");
      select.innerHTML = items.map(item => {
        const templateRawSuffix = item.rawTemplateNodes && item.rawTemplateNodes !== item.templateNodes ? ` / 原${item.rawTemplateNodes}` : "";
        const ocrRawSuffix = item.rawOcrNodes && item.rawOcrNodes !== item.ocrNodes ? ` / 原${item.rawOcrNodes}` : "";
        const label = `${item.displayName || item.script} · ${item.templateNodes || 0}${templateRawSuffix} 模板 · ${item.ocrNodes || 0}${ocrRawSuffix} OCR`;
        return `<option value="${escapeAttr(item.script)}">${escapeHtml(label)}</option>`;
      }).join("");
    }

    function renderVisionCaseOptions(items) {
      const select = document.querySelector("#vision-case-select");
      if (!items.length) {
        select.innerHTML = '<option value="">暂无 case</option>';
        return;
      }
      select.innerHTML = items.map(item => {
        const label = `${item.title || item.id} · ${item.expectations || 0} 期望 · ${item.screenshotExists ? "有截图" : "缺截图"}`;
        return `<option value="${escapeAttr(item.caseFile)}">${escapeHtml(label)}</option>`;
      }).join("");
    }

    function renderVisionRegression(report) {
      const contentEl = document.querySelector("#vision-regression-content");
      const s = report?.summary || {};
      const items = report?.items || [];
      const summary = `
        <section class="summary">
          ${metric("Case", s.cases)}
          ${metric("通过", s.passed)}
          ${metric("失败", s.failed)}
          ${metric("错误", s.errors)}
          ${metric("提醒", s.warnings)}
          ${metric("跳过", s.skips)}
        </section>
      `;
      const body = items.length ? items.map(item => {
        const sum = item.summary || {};
        const findings = item.findings || [];
        const failed = (sum.errors || 0) > 0;
        const warned = !failed && (sum.warnings || 0) > 0;
        return `
          <article class="panel item">
            <div>
              <h2>${escapeHtml(item.title || item.id || item.script)}</h2>
              <div class="meta">${escapeHtml(item.script || "")} · ${sum.observations || 0} 观察 · ${sum.errors || 0} 错误 · ${sum.warnings || 0} 提醒</div>
              <div class="meta">${escapeHtml(item.screenshot || "")}</div>
            </div>
            <span class="badge ${failed ? "fail" : warned ? "warn" : ""}">${failed ? "失败" : warned ? "提醒" : "通过"}</span>
            ${findings.length ? `<div class="findings">${findings.slice(0, 10).map(f => renderFinding(f, "", false)).join("")}</div>` : ""}
          </article>
        `;
      }).join("") : '<div class="panel state">没有回归 case。</div>';
      contentEl.innerHTML = summary + `<section class="content">${body}</section>`;
    }

    function renderVisionCapture(data) {
      const contentEl = document.querySelector("#vision-content");
      const reportItem = data.report?.items?.[0] || {};
      const observations = reportItem.observations || [];
      const observationByNode = new Map(observations.map(item => [item.node, item]));
      const scriptName = document.querySelector("#vision-script").value;
      const scriptInfo = (data.scripts || currentVisionScripts || []).find(item => item.script === scriptName);
      const nodes = scriptInfo?.nodes || [];
      const unitObservations = nodes.map(node => observationByNode.get(node.key)).filter(Boolean);
      const groupedNodes = groupVisionNodes(nodes, observationByNode);
      const image = data.screenshotDataUrl
        ? `<img class="vision-shot" src="${data.screenshotDataUrl}" alt="当前截图" />`
        : '<div class="panel state">没有截图预览</div>';
      contentEl.innerHTML = `
        <section class="vision-grid">
          <div>${image}</div>
          <div class="vision-list">
            ${renderVisionSummary(reportItem, unitObservations, nodes)}
            ${renderVisionGroup("命中", groupedNodes.hit, observationByNode, true)}
            ${renderVisionGroup("未命中", groupedNodes.miss, observationByNode, true)}
            ${renderVisionGroup("其它 / 异常", groupedNodes.other, observationByNode, false)}
          </div>
        </section>
      `;
      applyVisionDefaultExpectations(nodes, observationByNode);
      bindVisionNodeActions();
    }

    function renderVisionSummary(reportItem, observations, nodes) {
      const hitCount = observations.filter(item => item.status === "hit").length;
      const missCount = observations.filter(item => item.status === "miss").length;
      const ocrCount = nodes.filter(item => item.kind === "ocr").length;
      const findings = reportItem.findings || [];
      return `
        <article class="panel item">
          <div>
            <h2>观察结果</h2>
            <div class="meta">${hitCount} 命中 · ${missCount} 未命中 · ${ocrCount} OCR 节点 · ${findings.length} 条提示</div>
          </div>
          <span class="badge ${findings.some(item => item.severity === "ERROR") ? "fail" : findings.length ? "warn" : ""}">${findings.length ? "有提示" : "干净"}</span>
          ${findings.length ? `<div class="findings">${findings.slice(0, 5).map(f => renderFinding(f, "", false)).join("")}</div>` : ""}
        </article>
      `;
    }

    function groupVisionNodes(nodes, observationByNode) {
      const groups = { hit: [], miss: [], other: [] };
      nodes.forEach(node => {
        if (!["template", "ocr"].includes(node.kind)) {
          groups.other.push(node);
          return;
        }
        const observation = observationByNode.get(node.key);
        if (observation?.status === "hit") groups.hit.push(node);
        else if (observation?.status === "miss") groups.miss.push(node);
        else groups.other.push(node);
      });
      return groups;
    }

    function renderVisionGroup(title, nodes, observationByNode, open) {
      const body = nodes.length
        ? nodes.map(node => renderVisionNodeRow(node, observationByNode.get(node.key))).join("")
        : '<div class="state">没有项目</div>';
      return `
        <details class="vision-group" ${open ? "open" : ""}>
          <summary>${escapeHtml(title)}（${nodes.length}）</summary>
          <div class="vision-group-body">${body}</div>
        </details>
      `;
    }

    function renderVisionNodeRow(node, observation) {
      const score = observation?.score === null || observation?.score === undefined ? "无" : Number(observation.score).toFixed(4);
      const status = observation?.status || (node.kind === "ocr" ? "ocr" : "未观察");
      const threshold = node.threshold === null || node.threshold === undefined ? "-" : Number(node.threshold).toFixed(2);
      const duplicateBasis = node.kind === "ocr" ? "同字同 ROI" : "同素材同 ROI";
      const duplicateNote = Number(node.duplicateCount || 1) > 1 ? ` · 合并 ${Number(node.duplicateCount)} 个${duplicateBasis}节点` : "";
      const message = observation?.message ? `<div class="meta">${escapeHtml(observation.message)}</div>` : "";
      const unitValue = node.unitValue || visionNodeText(node) || "";
      const ocrInput = node.kind === "ocr"
        ? `<input class="vision-ocr-input" type="text" data-ocr-text="${escapeAttr(node.key)}" value="${escapeAttr(unitValue)}" placeholder="OCR 期望文本" />`
        : "";
      return `
        <article class="vision-row panel" data-node="${escapeAttr(node.key)}" data-node-name="${escapeAttr(node.name || "")}" data-kind="${escapeAttr(node.kind)}" data-template="${escapeAttr(node.templateName || "")}" data-unit-value="${escapeAttr(unitValue)}">
          <div>
            <h2>${escapeHtml(node.label || node.name || node.key)}</h2>
            <div class="meta">${escapeHtml(node.key)} · ${escapeHtml(node.kind)} · ${escapeHtml(status)} · 分数=${escapeHtml(score)} · 阈值=${escapeHtml(threshold)}${escapeHtml(duplicateNote)}</div>
            <div class="meta">${renderVisionUnitDetail(node)}</div>
            <div class="meta">${escapeHtml(node.location || "")}</div>
            ${message}
            ${ocrInput}
          </div>
          <div class="vision-actions">
            ${["template", "ocr"].includes(node.kind) ? `<button type="button" data-vision-mark="hit">应命中</button><button type="button" data-vision-mark="miss">不应命中</button>` : ""}
            <button class="ghost-button" type="button" data-vision-mark="ignore">忽略</button>
          </div>
        </article>
      `;
    }

    function renderVisionUnitDetail(node) {
      const value = node.kind === "ocr"
        ? (visionNodeText(node) || "未配置文字")
        : (node.templateName || node.unitValue || "未配置素材");
      const prefix = node.kind === "ocr" ? "字" : "素材";
      return `${escapeHtml(prefix)}=${escapeHtml(value)} · ROI=${escapeHtml(formatVisionRoi(node.roi))}`;
    }

    function visionNodeText(node) {
      return node.targetText || (node.targetChars || []).join("") || node.unitValue || "";
    }

    function formatVisionRoi(roi) {
      if (!roi || typeof roi !== "object") return "全屏";
      const keys = ["align", "x", "y", "w", "h", "centerX", "centerY", "radius"];
      const parts = keys
        .filter(key => roi[key] !== undefined && roi[key] !== null && roi[key] !== "")
        .map(key => `${key}=${roi[key]}`);
      return parts.length ? parts.join(", ") : "全屏";
    }

    function bindVisionNodeActions() {
      document.querySelectorAll("[data-vision-mark]").forEach(button => {
        button.addEventListener("click", () => {
          const row = button.closest(".vision-row");
          const node = row?.dataset.node;
          const kind = row?.dataset.kind;
          const mark = button.dataset.visionMark;
          if (!row || !node || !mark) return;
          row.classList.remove("selected-hit", "selected-miss", "selected-ignore");
          const expectation = { type: mark, node };
          if (row.dataset.nodeName) {
            expectation.node_name = row.dataset.nodeName;
          }
          if (kind === "ocr" && ["hit", "miss"].includes(mark)) {
            const input = row.querySelector(`[data-ocr-text="${cssEscape(node)}"]`);
            expectation.expected_text = input?.value || "";
          }
          if (kind === "template") {
            expectation.template_name = row.dataset.template || "";
          }
          currentVisionExpectations.set(node, expectation);
          row.classList.add(`selected-${mark}`);
        });
      });
    }

    function applyVisionDefaultExpectations(nodes, observationByNode) {
      currentVisionExpectations = new Map();
      nodes.forEach(node => {
        if (!["template", "ocr"].includes(node.kind)) return;
        const observation = observationByNode.get(node.key);
        if (!observation || !["hit", "miss"].includes(observation.status)) return;
        const mark = observation.status === "hit" ? "hit" : "miss";
        const expectation = {
          type: mark,
          node: node.key,
          node_name: node.name || ""
        };
        if (node.kind === "template") {
          expectation.template_name = node.templateName || "";
        }
        if (node.kind === "ocr") {
          expectation.expected_text = node.unitValue || visionNodeText(node) || "";
        }
        currentVisionExpectations.set(node.key, expectation);
        const row = document.querySelector(`.vision-row[data-node="${cssEscape(node.key)}"]`);
        row?.classList.add(`selected-${mark}`);
      });
    }

    function buildVisionExpectations() {
      document.querySelectorAll(".vision-row[data-kind='ocr']").forEach(row => {
        const node = row.dataset.node;
        if (!node || !currentVisionExpectations.has(node)) return;
        const expectation = currentVisionExpectations.get(node);
        if (!["hit", "miss"].includes(expectation.type)) return;
        const input = row.querySelector(`[data-ocr-text="${cssEscape(node)}"]`);
        expectation.expected_text = input?.value || "";
      });
      return Array.from(currentVisionExpectations.values());
    }

    function cssEscape(value) {
      return String(value).replace(/["\\]/g, "\\$&");
    }

    function renderBasic(data) {
      const s = data.summary || {};
      const target = data.defaultTarget || {};
      document.querySelector("#basic-summary").innerHTML = [
        metric("设备", s.devices),
        metric("在线", s.online),
        metric("模拟器", s.emulators),
        metric("USB", s.usb),
        metric("Offline", s.offline),
        metric("未授权", s.unauthorized)
      ].join("");

      const devices = data.devices || [];
      const contentEl = document.querySelector("#basic-content");
      const targetBlock = `
        <article class="panel item">
          <div>
            <h2>默认 ADB 目标</h2>
            <div class="meta">serial=${escapeHtml(target.serial || "emulator-5554")} · kind=${escapeHtml(target.kind || "emulator")} · state=${escapeHtml(target.state || "missing")}</div>
            <div class="meta">adb=${escapeHtml(data.adbBin || "")}</div>
          </div>
          <span class="badge ${target.online ? "" : "warn"}">${target.online ? "在线" : target.present ? "未在线" : "未发现"}</span>
        </article>
      `;
      if (!devices.length) {
        contentEl.innerHTML = targetBlock + '<div class="panel state">当前没有 adb 设备在线或可见。</div>';
        return;
      }
      contentEl.innerHTML = targetBlock + devices.map(device => {
        const online = device.state === "device";
        const label = device.model || device.device || device.product || device.serial;
        return `
          <article class="panel item">
            <div>
              <h2>${escapeHtml(label)}</h2>
              <div class="meta">${escapeHtml(device.serial)} · ${escapeHtml(device.kind)} · state=${escapeHtml(device.state)}</div>
              <div class="meta">${escapeHtml(device.raw || "")}</div>
            </div>
            <span class="badge ${online ? "" : "warn"}">${online ? "在线" : escapeHtml(device.state || "未知")}</span>
          </article>
        `;
      }).join("");
    }

    function renderDaily(data) {
      const s = data.summary || {};
      currentDailyData = data;
      document.querySelector("#daily-summary").innerHTML = [
        metric("脚本", s.scripts),
        metric("通过", s.passed),
        metric("失败", s.failed),
        metric("错误", s.errors),
        metric("提醒", s.warnings),
        metric("模板无名", s.unnamedTasks),
        metric("模板重名", s.duplicateTaskNames)
      ].join("");

      const items = data.items || [];
      const contentEl = document.querySelector("#daily-content");
      if (!items.length) {
        contentEl.innerHTML = '<div class="panel state">没有找到可检查的日常脚本。</div>';
        return;
      }

      const sorted = [...items].sort((a, b) => {
        const ae = a.summary?.errors || 0;
        const be = b.summary?.errors || 0;
        const aw = a.summary?.warnings || 0;
        const bw = b.summary?.warnings || 0;
        return be - ae || bw - aw || String(a.file).localeCompare(String(b.file), "zh-Hans-CN");
      });

      const orphan = data.orphanTemplateDirs || [];
      const orphanBlock = orphan.length ? `
        <article class="panel item">
          <div>
            <h2>未引用模板目录</h2>
            <div class="meta">${orphan.length} 个模板目录没有被任何日常脚本 asset_template_dir 引用</div>
          </div>
          <span class="badge warn">提醒</span>
          <div class="findings">${orphan.slice(0, 10).map(dir => `<div class="finding">${escapeHtml(dir)}</div>`).join("")}</div>
        </article>
      ` : "";

      contentEl.innerHTML = orphanBlock + sorted.map(item => {
        const sum = item.summary || {};
        const findings = item.findings || [];
        const failed = (sum.errors || 0) > 0;
        const warned = !failed && (sum.warnings || 0) > 0;
        return `
          <article class="panel item">
            <div>
              <h2>${escapeHtml(item.displayName || item.file)}</h2>
              <div class="meta">${escapeHtml(item.file)} · ${item.tasks || 0} 节点 · ${item.visualNodes || 0} 视觉节点 · ${item.templateRefs || 0} 模板引用 · ${item.scriptRefs || 0} 子脚本引用 · ${item.unnamedTasks || 0} 模板无名 · ${item.duplicateTaskNames || 0} 模板重名</div>
            </div>
            <div class="item-actions">
              <button class="ghost-button" type="button" data-open-json="${escapeAttr(item.file)}">打开 JSON</button>
              <span class="badge ${failed ? "fail" : warned ? "warn" : ""}">${failed ? "失败" : warned ? "提醒" : "通过"}</span>
            </div>
            ${findings.length ? `<div class="findings">${findings.slice(0, 8).map(f => renderFinding(f, item.file, true)).join("")}</div>` : ""}
          </article>
        `;
      }).join("");
      bindDailyJsonActions();
    }

    function renderBattle(data) {
      const s = data.summary || {};
      document.querySelector("#battle-summary").innerHTML = [
        metric("用例", s.cases),
        metric("通过", s.passed),
        metric("失败", s.failed),
        metric("警告", s.warnings),
        metric("错误", s.errors),
        metric("动作", s.actions)
      ].join("");

      const items = data.items || [];
      const contentEl = document.querySelector("#battle-content");
      if (!items.length) {
        contentEl.innerHTML = '<div class="panel state">没有拉到可检查的攻略。</div>';
        return;
      }

      contentEl.innerHTML = items.map(item => {
        const report = item.report || {};
        const sum = report.summary || {};
        const findings = report.findings || [];
        const failed = (sum.errors || 0) > 0;
        return `
          <article class="panel item">
            <div>
              <h2>${escapeHtml(item.title || "未命名攻略")}</h2>
              <div class="meta">${escapeHtml(item.objectId || "")} · ${sum.turns || 0} 回合 · ${sum.actions || 0} 动作 · ${sum.instructions || 0} 指令</div>
            </div>
            <span class="badge ${failed ? "fail" : ""}">${failed ? "失败" : "通过"}</span>
            ${findings.length ? `<div class="findings">${findings.slice(0, 5).map(f => renderFinding(f, "", false)).join("")}</div>` : ""}
          </article>
        `;
      }).join("");
    }

    function renderFinding(finding, file, clickable) {
      const where = finding.taskId !== null && finding.taskId !== undefined ? ` task=${finding.taskId}` : "";
      const loc = finding.location ? ` ${finding.location}` : "";
      const attrs = clickable
        ? ` role="button" tabindex="0" data-open-json="${escapeAttr(file)}" data-task-id="${escapeAttr(finding.taskId ?? "")}" data-location="${escapeAttr(finding.location || "")}" data-code="${escapeAttr(finding.code || "")}"`
        : "";
      return `<div class="finding ${finding.severity === "ERROR" ? "error" : ""} ${clickable ? "clickable" : ""}"${attrs}>${escapeHtml(finding.severity)} ${escapeHtml(finding.code)}${escapeHtml(where)}${escapeHtml(loc)}：${escapeHtml(finding.message)}</div>`;
    }

    function metric(label, value) {
      return `<div class="panel metric"><div class="value">${Number(value || 0)}</div><div class="label">${label}</div></div>`;
    }

    function bindDailyJsonActions() {
      document.querySelectorAll("[data-open-json]").forEach(node => {
        node.addEventListener("click", () => openJsonFromNode(node));
        node.addEventListener("keydown", event => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            openJsonFromNode(node);
          }
        });
      });
    }

    function openJsonFromNode(node) {
      const file = node.dataset.openJson;
      if (!file) return;
      const jump = {
        taskId: node.dataset.taskId || "",
        location: node.dataset.location || "",
        code: node.dataset.code || ""
      };
      openJson(file, jump);
    }

    async function openJson(file, jump = null) {
      pendingJump = jump;
      drawer.classList.add("open");
      setDrawerStatus("正在打开 JSON...");
      try {
        const res = await fetch(`/api/json-file?file=${encodeURIComponent(file)}`);
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || "读取失败");
        currentJsonFile = data.file;
        editor.value = data.content || "";
        drawerTitle.textContent = currentJsonFile.split("/").pop() || "JSON";
        drawerPath.textContent = currentJsonFile;
        syncHighlight();
        setDrawerStatus("已打开");
        requestAnimationFrame(() => jumpToFinding(pendingJump));
      } catch (error) {
        setDrawerStatus(error.message, true);
      }
    }

    async function saveJson() {
      if (!currentJsonFile) {
        setDrawerStatus("还没有打开 JSON", true);
        return;
      }
      try {
        JSON.parse(editor.value);
      } catch (error) {
        setDrawerStatus(`JSON 校验失败：${error.message}`, true);
        return;
      }
      setDrawerStatus("正在保存...");
      try {
        const res = await fetch("/api/json-file", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ file: currentJsonFile, content: editor.value })
        });
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || "保存失败");
        currentDailyData = data.report;
        renderDaily(data.report);
        setDrawerStatus("已保存，并刷新体检结果", false, true);
        requestAnimationFrame(() => {
          drawer.classList.add("open");
          jumpToFinding(pendingJump);
        });
      } catch (error) {
        setDrawerStatus(error.message, true);
      }
    }

    function formatJson() {
      try {
        editor.value = JSON.stringify(JSON.parse(editor.value), null, 2) + "\n";
        syncHighlight();
        setDrawerStatus("已格式化");
      } catch (error) {
        setDrawerStatus(`JSON 校验失败：${error.message}`, true);
      }
    }

    function jumpToFinding(jump) {
      if (!jump || !editor.value) return;
      let index = -1;
      const taskId = Number(jump.taskId);
      if (Number.isFinite(taskId)) {
        index = findTaskIndex(editor.value, taskId);
      }
      if (index < 0 && jump.location) {
        index = findLocationIndex(editor.value, jump.location);
      }
      if (index < 0 && jump.code === "START_TASK_MISSING") {
        index = editor.value.indexOf('"start_task_id"');
      }
      if (index < 0) return;
      editor.focus();
      editor.setSelectionRange(index, index);
      const before = editor.value.slice(0, index);
      const line = before.split("\n").length;
      const lineHeight = Number.parseFloat(getComputedStyle(editor).lineHeight) || 20;
      editor.scrollTop = Math.max(0, (line - 4) * lineHeight);
      highlight.scrollTop = editor.scrollTop;
      highlight.scrollLeft = editor.scrollLeft;
      flashEditor();
    }

    function findTaskIndex(text, taskId) {
      const pattern = new RegExp(`"id"\\s*:\\s*${taskId}(?=\\s*[,}])`, "g");
      let match;
      while ((match = pattern.exec(text)) !== null) {
        const objectStart = text.lastIndexOf("{", match.index);
        const actionWindow = text.slice(objectStart, Math.min(text.length, objectStart + 900));
        if (actionWindow.includes('"action"')) return Math.max(0, objectStart);
      }
      return -1;
    }

    function findLocationIndex(text, location) {
      const field = location.split(".").pop();
      if (!field || field.includes("[") || field === location) return -1;
      return text.indexOf(`"${field}"`);
    }

    function flashEditor() {
      highlight.classList.remove("jump-flash");
      void highlight.offsetWidth;
      highlight.classList.add("jump-flash");
    }

    function syncHighlight() {
      highlight.innerHTML = syntaxHighlightJson(editor.value) + "\n";
    }

    function syntaxHighlightJson(value) {
      let arrayContextKey = "";
      return value.split("\n").map(line => {
        const lineKey = jsonLineKey(line);
        const inheritedKey = lineKey ? "" : arrayContextKey;
        const html = highlightJsonLine(line, inheritedKey) || " ";
        if (lineKey && line.includes("[") && !line.includes("]")) arrayContextKey = lineKey;
        if (arrayContextKey && line.includes("]")) arrayContextKey = "";
        return html;
      }).join("\n");
    }

    function highlightJsonLine(line, inheritedKey = "") {
      let activeKey = inheritedKey;
      return line.replace(/("(?:\\u[a-fA-F0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\btrue\b|\bfalse\b|\bnull\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|[{}\[\],])/g, token => {
        const safeToken = escapeHtml(token);
        if (isJsonKeyToken(token)) {
          activeKey = decodeJsonTokenKey(token);
          return `<span class="${keyClassFor(activeKey)}">${safeToken}</span>`;
        }
        if (token === "," || token === "}" || token === "]") {
          if (token === ",") activeKey = "";
          return `<span class="json-mark">${safeToken}</span>`;
        }
        if (token.startsWith('"')) {
          return `<span class="${valueClassFor(activeKey, token)}">${safeToken}</span>`;
        }
        if (token === "true" || token === "false") return `<span class="${scalarClassFor(activeKey, "json-bool")}">${safeToken}</span>`;
        if (token === "null") return `<span class="${scalarClassFor(activeKey, "json-null")}">${safeToken}</span>`;
        if (/^-?\d/.test(token)) return `<span class="${scalarClassFor(activeKey, "json-number")}">${safeToken}</span>`;
        return `<span class="json-mark">${safeToken}</span>`;
      });
    }

    function isJsonKeyToken(token) {
      return /^"(?:\\u[a-fA-F0-9]{4}|\\[^u]|[^\\"])*"\s*:$/.test(token);
    }

    function jsonLineKey(line) {
      const match = line.match(/^\s*"((?:\\.|[^"\\])*)"\s*:/);
      if (!match) return "";
      try {
        return JSON.parse(`"${match[1]}"`);
      } catch {
        return match[1];
      }
    }

    function decodeJsonTokenKey(token) {
      const raw = token.replace(/:\s*$/, "");
      try {
        return JSON.parse(raw);
      } catch {
        return raw.replace(/^"|"$/g, "");
      }
    }

    function keyClassFor(key) {
      return "json-key";
    }

    function valueClassFor(key, token) {
      if (isNameKey(key)) return "json-tag-name";
      if (isTemplateKey(key)) return "json-tag-template";
      if (isOcrTextKey(key)) return "json-tag-ocr";
      if (key === "align") return "json-tag-align";
      return "json-string";
    }

    function scalarClassFor(key, fallback) {
      if (key === "threshold") return "json-tag-threshold";
      return fallback;
    }

    function isNameKey(key) {
      return key === "name" || key === "display_name";
    }

    function isTemplateKey(key) {
      return key === "template_name" || key === "asset_template_dir" || key === "template";
    }

    function isOcrTextKey(key) {
      return key === "target_chars" || key === "target_text" || key === "button_name";
    }

    function setDrawerStatus(message, isError = false, isOk = false) {
      drawerStatus.textContent = message;
      drawerStatus.classList.toggle("error", isError);
      drawerStatus.classList.toggle("ok", isOk);
    }

    document.querySelector("#close-json").addEventListener("click", () => {
      drawer.classList.remove("open");
    });
    document.querySelector("#save-json").addEventListener("click", saveJson);
    document.querySelector("#format-json").addEventListener("click", formatJson);
    document.querySelector("#reload-json").addEventListener("click", () => {
      if (currentJsonFile) openJson(currentJsonFile, pendingJump);
    });
    editor.addEventListener("input", syncHighlight);
    editor.addEventListener("scroll", () => {
      highlight.scrollTop = editor.scrollTop;
      highlight.scrollLeft = editor.scrollLeft;
    });

    const resizer = document.querySelector("#drawer-resizer");
    resizer.addEventListener("pointerdown", event => {
      event.preventDefault();
      resizer.setPointerCapture(event.pointerId);
      const onMove = moveEvent => {
        const nextWidth = Math.min(window.innerWidth, Math.max(420, window.innerWidth - moveEvent.clientX));
        drawer.style.width = `${nextWidth}px`;
      };
      const onUp = upEvent => {
        resizer.releasePointerCapture(upEvent.pointerId);
        window.removeEventListener("pointermove", onMove);
        window.removeEventListener("pointerup", onUp);
      };
      window.addEventListener("pointermove", onMove);
      window.addEventListener("pointerup", onUp);
    });

    function escapeHtml(value) {
      return String(value).replace(/[&<>"']/g, char => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#039;"
      }[char]));
    }

    function escapeAttr(value) {
      return escapeHtml(value).replace(/`/g, "&#096;");
    }
  </script>
</body>
</html>
"""

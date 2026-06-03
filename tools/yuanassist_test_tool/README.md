# YuanAssist Test Tool

Python-based test helpers for YuanAssist.

## Current Capabilities

The tool currently supports:
- offline static checks for battle scripts and strategy import payloads
- local static checks for built-in daily script assets
- offline visual regression case checks for built-in daily scripts
- adb device discovery for USB devices and Android emulators
- adb-based app smoke checks for install, launch, permissions, screenshots, logcat, and crash scanning
- read-only strategy payload fetching through Supabase CLI
- a local visual dashboard with tabs for daily asset checks and battle strategy checks

The battle checker can validate:
- battle `scriptContent` row format
- turn numbers and duplicate turns
- supported action cells: `A`, `↑`, `↓`, `圈`
- standalone command cells such as `圈`
- repeated command runs such as `4↓↓↓`
- empty turns and repeated action orders
- `InstructionJson` array structure
- supported instruction types
- instruction turn/step/value basics
- `STAGE_AUTO_NAV` target values

The daily asset checker can validate:
- built-in `app/src/main/assets/daily_scripts/*.json` parsing
- `start_task_id`, duplicate task IDs, unreachable tasks, and broken transitions
- missing debug names for template-matching nodes exposed to the debug page
- duplicate debug names only when the same name points to different `template_name` values
- `on_success`, `on_fail`, `branch_routes`, `fail_branch_routes`, and screenshot step targets
- required parameters for `CLICK`, `SWIPE`, `MATCH_TEMPLATE`, `OCR`, `SCREENSHOT_GROUP`, `RUN_SCRIPT_SEGMENT`, and `SET_VAR`
- template files resolved the same way as runtime: `asset_template_dir`, direct asset names, and full asset paths
- segment script references such as `RUN_SCRIPT_SEGMENT`
- unused daily template directories

The daily visual checker can validate local fixture cases:
- `MATCH_TEMPLATE` nodes
- `SCREENSHOT_GROUP` template / match_template child steps
- expected hits and expected misses against a saved screenshot
- OCR expectations as structured case data; OCR execution is currently skipped until a Python/client OCR backend is connected

The adb helper can:
- resolve adb from `C:\Users\17525\AppData\Local\Android\Sdk\platform-tools\adb.exe`, or from PATH
- list `adb devices -l`
- classify `emulator-*` devices as emulator and other devices as USB
- report `device`, `offline`, and `unauthorized` states
- expose the default local emulator target `emulator-5554`
- provide a screenshot helper for the later visual fixture collector

The app smoke checker can:
- optionally install an APK with `adb install -r`
- cold-start `com.example.yuanassist/.ui.MainActivity`
- read installed `versionCode` / `versionName` and compare them with `app/build.gradle.kts`
- check Android version, screen size, density, accessibility service state, and overlay appops
- capture a launch screenshot and logcat
- scan logcat for fatal exceptions, ANR, security exceptions, permission denials, and Activity launch failures
- write `summary.json`, `screenshot.png`, and `logcat.txt` under `tools/yuanassist_test_tool/reports/app_smoke/`

The local dashboard also provides a constrained JSON editor for built-in daily scripts:
- open JSON from a daily asset check card
- click a warning/error to open the JSON and jump near the related task or field
- edit, format, reload, and save after JSON validation
- highlight important fields such as `name`, `template_name`, OCR targets, actions, transitions, ROI, and thresholds
- editing is restricted to `app/src/main/assets/daily_scripts/*.json`

The local dashboard also has a `基础诊断` tab:
- adb path
- default adb target `emulator-5554`
- USB and emulator device list
- online, offline, and unauthorized device counts

The local dashboard also has an `App 冒烟` tab:
- select an adb target
- optionally provide an APK path and install it before launch
- clear logcat before launch
- cold-start the app and show a launch screenshot
- display install/start/process/permission/log scan steps
- display report paths and error/warning findings

The local dashboard also has a `截图调试` tab:
- select an adb target, defaulting to `emulator-5554`
- organize all visual units referenced by built-in daily scripts
- match template units in the captured screenshot
- capture the current device screenshot through adb
- display one result per full template path + ROI unit, and one OCR unit per expected text + ROI
- mark over-threshold units as expected hits by default, and below-threshold units as expected misses by default
- mark nodes as expected hit, expected miss, OCR expectation, or ignored
- save `screenshot.png` and `case.json` under `tools/yuanassist_test_tool/fixtures/daily_vision/`
- refresh saved visual cases
- run all saved visual regression cases
- run one selected visual case
- replace one selected case screenshot with the current adb screenshot and run it immediately

## Usage

Run from the repository root:

```powershell
python -m tools.yuanassist_test_tool battle-static --script-file path\to\script.txt
```

With instructions:

```powershell
python -m tools.yuanassist_test_tool battle-static `
  --script-file path\to\script.txt `
  --instructions-file path\to\instructions.json
```

With a case file:

```powershell
python -m tools.yuanassist_test_tool battle-static --case-file path\to\case.json
```

Case file example:

```json
{
  "title": "sample",
  "scriptContent": "1回合\t1A\t2↑\t-\t-\t-",
  "instructions": [
    { "turn": 1, "step": 2, "type": "DELAY_ADD", "value": 500 }
  ]
}
```

Machine-readable report:

```powershell
python -m tools.yuanassist_test_tool battle-static --case-file path\to\case.json --json
```

Run app smoke against an APK:

```powershell
python -m tools.yuanassist_test_tool app-smoke --apk app\build\outputs\apk\debug\app-debug.apk
```

Run app smoke against the already installed app:

```powershell
python -m tools.yuanassist_test_tool app-smoke --no-install
```

Fetch public strategy payloads through Supabase CLI and save local cases:

```powershell
python -m tools.yuanassist_test_tool fetch-strategies --limit 20
```

Fetch and check directly:

```powershell
python -m tools.yuanassist_test_tool check-strategies --limit 20
```

Check built-in daily script assets:

```powershell
python -m tools.yuanassist_test_tool daily-assets
```

Run daily visual regression cases:

```powershell
python -m tools.yuanassist_test_tool daily-vision
```

Run one case:

```powershell
python -m tools.yuanassist_test_tool daily-vision --case-file tools\yuanassist_test_tool\fixtures\daily_vision\sample\case.json
```

List adb devices:

```powershell
python -m tools.yuanassist_test_tool adb-devices
```

Machine-readable adb device report:

```powershell
python -m tools.yuanassist_test_tool adb-devices --json
```

Daily visual case example:

```json
{
  "id": "zhu_xian_6_24_home_story_entry",
  "title": "主线 6-24：首页故事入口",
  "script": "zhu_xian_6_24.json",
  "screenshot": "screenshot.png",
  "display": {
    "width": 1080,
    "height": 1920,
    "raw_status_bar_height": 40
  },
  "warn_unexpected_hits": true,
  "expectations": [
    {
      "type": "hit",
      "node": "task:10",
      "min_score": 0.82
    },
    {
      "type": "miss",
      "node_name": "6-24战斗页",
      "max_score": 0.75,
      "reason": "防止首页误识别成战斗页"
    },
    {
      "type": "ocr",
      "node": "task:12",
      "expected_text": "开始"
    }
  ]
}
```

Start the local visual dashboard:

```powershell
python -m tools.yuanassist_test_tool ui --port 8899
```

Or double-click from the repository root:

```text
start_yuanassist_test_tool.bat
```

Then open:

```text
http://127.0.0.1:8899
```

Notes:
- Supabase access uses `node_modules/@supabase/cli-windows-x64/bin/supabase.exe`.
- Queries are read-only and use `supabase db query --linked`.
- Port `8765` may be blocked on some Windows machines; use `--port 8899` if needed.

Exit code:
- `0`: no errors
- `1`: check failed
- `2`: invalid CLI input

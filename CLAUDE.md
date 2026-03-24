# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

YuanAssist is an Android accessibility service app (Kotlin) that automates turn-based combat actions for a mobile game. It uses the Android AccessibilityService API to dispatch gestures (taps, swipes) and floating overlay windows for its UI. The app has two primary modes: **Record Mode** (capture user touches into a turn-based action table) and **Follow/Combat Mode** (replay scripted actions automatically).

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew testDebugUnitTest --tests "com.example.com.ExampleUnitTest"

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

- **AGP**: 8.13.1, **Kotlin**: 2.0.21, **compileSdk**: 36, **minSdk**: 24, **targetSdk**: 36
- JVM target: Java 11
- NDK ABI filter: `arm64-v8a` only
- Maven repositories use Alibaba Cloud mirrors (China) with priority over Google/MavenCentral
- JitPack is included for Bmob SDK

## Architecture

### Core Layer (`core/`)

The heart of the app. All core classes are instantiated and orchestrated by `YuanAssistService`.

- **`YuanAssistService`** — The `AccessibilityService` subclass and central orchestrator. Manages the lifecycle of all engines, floating windows, mode switching (record ↔ follow), script import/export, and OCR processing. Exposes a singleton `instance` for cross-component access. Receives commands via `onStartCommand` intents (`ACTION_RELOAD_CONFIG`, `ACTION_START_DAILY_WINDOW`, `ACTION_START_COMBAT_WINDOW`, `ACTION_IMPORT_SCRIPT`).

- **`CombatEngine`** — Drives Follow/Combat mode. Parses `TurnData` rows into ordered `ActionItem` queues, executes gestures via `GestureDispatcher` with configurable delays, handles pause/resume, target switching, and supports scripted instructions (`PAUSE`, `DELAY_ADD`, `TARGET_SWITCH`). Action notation: `A` = tap, `↑` = swipe up (skill), `↓` = swipe down, `圈` = circle tap. Numbers prefix actions to specify step order (e.g., `1A2↑` means step 1 tap, step 2 swipe up).

- **`RecordEngine`** — Drives Record mode. Intercepts touch events from the transparent input overlay, classifies them as tap/swipe-up/swipe-down based on movement delta, maps screen X to one of 5 character columns, and appends step-numbered actions to `TurnData`. Supports undo/redo via `HistoryAction` stack.

- **`GestureDispatcher`** — Abstracts gesture execution into two paths:
  - `performActionDirect()` — Used by CombatEngine; dispatches gestures immediately.
  - `performActionPenetrate()` — Used by RecordEngine; temporarily hides the input overlay so the gesture "passes through" to the game, then restores it.

- **`CoordinateManager`** — Adaptive coordinate system based on a **1440×2560 design reference**. Calculates scale factor, black-bar offsets, and 5-column widths for any screen resolution. Provides `getActionCoordinates()` (bottom-up Y) and `getTargetCoordinates()` (top-down Y).

- **`AutoTaskEngine`** — State-machine task runner for automated multi-step flows (e.g., auto character selection). Executes a `DailyTaskPlan` (a DAG of `DailyTask` nodes with `on_success`/`on_fail` branching). Supports actions: `CLICK`, `SWIPE`, `BACK`, `MATCH_TEMPLATE` (OpenCV), `CLICK_LAST_MATCH`. Uses a **1080×1920 design reference** (different from CombatEngine's 1440×2560).

- **`AutoSelectScriptBuilder`** — Generates `DailyTaskPlan` for auto character selection from agent name arrays and the agent repository.

- **`DailyWindowManager`** — Manages the daily task floating window UI.

### UI Layer (`ui/`)

- **`MainActivity`** — Bottom navigation host with 3 fragments: Home, Strategy, Mine.
- **`FloatingUIManager`** — Creates/manages three overlay windows: control panel, minimized bubble, and transparent input layer. All use `TYPE_APPLICATION_OVERLAY`.
- **`HomeFragment`** — Main entry point for starting the accessibility service and choosing combat vs daily mode.
- **`StrategyFragment` / `StrategyDetailActivity`** — Browse and import community-shared combat strategies.
- **`ScriptLibraryActivity`** — Local script management (save/load JSON scripts).
- **`LogAdapter`** — RecyclerView adapter for the 5-column turn data table.

### Model Layer (`model/`)

- **`TurnData`** — Core data unit: turn number, 5-column `CharSequence` array for character actions, step counter, conflict/executing flags, remark.
- **`ScriptModels`** — `ScriptInstruction`, `InstructionType` (PAUSE, DELAY_ADD, TARGET_SWITCH), `InstructionJson` for serialization.
- **`DailyTaskModels`** — `DailyTaskPlan`, `DailyTask`, `TaskParams`, `ROI` for the AutoTaskEngine flow.
- **`AgentRepository`** — Static registry of all game characters with coordinates for auto-selection.

### Network Layer (`network/`)

- **`OcrManager`** — Sends screenshot images (Base64 JPEG) to `yuanassist.space/ocr` for cloud OCR recognition. Uses OkHttp with auto-retry on QPS limits.
- **`ImageUploader` / `ImageUploadApi`** — Retrofit-based image upload for strategy sharing.

### Utils (`utils/`)

- **`ConfigManager`** — SharedPreferences wrapper for `AppConfig` (timing intervals, swipe threshold, game speed, etc.). Preference name: `game_assist_global_config`.
- **`GameConstants`** — Design-reference coordinates and randomization parameters for anti-detection.
- **`RunLogger`** — In-memory log buffer for debugging task execution flows.
- **`DialogUtils`** — Safe overlay dialog display helper.
- **`ImageExportUtils`** — Renders turn data table to a shareable image.

### Backend

- **Bmob** (BaaS) — Initialized in `YuanAssistApp` for cloud data storage (strategies, feedback, app updates).
- **Cloud OCR** — Custom API at `yuanassist.space/ocr` for recognizing combat action tables from screenshots.

## Key Design Patterns

- **Two coordinate systems**: CombatEngine uses 1440×2560 base (via `CoordinateManager`), AutoTaskEngine uses 1080×1920 base (internal `calculateRealCoordinate`). Both handle letterboxing/pillarboxing for different aspect ratios.
- **Callback-driven engines**: Core engines communicate with `YuanAssistService` via function callbacks (not interfaces), keeping dependencies loose.
- **Intent-based commands**: External components (activities) communicate with the service via `startService()` with action strings.
- **Script format**: Text-based (one line per turn, whitespace-separated columns), stored as JSON with embedded config and instructions.

## Important Notes

- The codebase uses a mix of Simplified Chinese (code comments) and Traditional Chinese (some UI text/comments).
- `YuanAssistService.instance` is a static singleton — be aware of lifecycle implications.
- OpenCV is initialized in `onServiceConnected()` and is required for template matching features.
- The app targets `arm64-v8a` only — no x86/x86_64 support.

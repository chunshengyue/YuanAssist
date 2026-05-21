# Stone OCR Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `不OCR / 本地OCR / 云端OCR` choices to both stone OCR entry points and route both through one shared import path.

**Architecture:** Keep the choice UI inside each caller, but move the OCR execution and store update flow into a shared stone OCR coordinator. Reuse the existing cloud OCR path and add a local Paddle-based tokenizer that still feeds `StoneOcrParser`.

**Tech Stack:** Kotlin, Android AlertDialog, coroutines, existing Paddle OCR wrapper, existing stone parser/store.

---

### Task 1: Add shared stone OCR coordinator

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/utils/StoneOcrCoordinator.kt`
- Modify: `app/src/main/java/com/example/yuanassist/utils/StoneOcrParser.kt`

- [ ] Define a shared `StoneOcrMode` enum and a coordinator entry that accepts context, stone type, archive id, image files, mode, and optional cloud retry callback.
- [ ] Reuse `OcrManager.recognizeStoneImage(...)` for cloud mode and `PaddleTextRecognizer.recognize(...)` for local mode.
- [ ] Add local tokenization that extracts level tokens and allowed stone names from Paddle line text.
- [ ] Reuse `StoneOcrParser.buildRows(...)`, `aggregate(...)`, and `MyStoneStore.saveOcrResult(...)`.

### Task 2: Update the floating-window prompt

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/core/DailyWindowManager.kt`

- [ ] Replace the yes/no prompt with a three-button choice dialog.
- [ ] Route `本地OCR` and `云端OCR` into the shared coordinator.
- [ ] Keep `不OCR` as a no-op and preserve the existing in-progress guard.

### Task 3: Update the MyStone page entry

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/MyStoneActivity.kt`

- [ ] Replace the direct cloud OCR trigger with a three-button choice dialog.
- [ ] Route `本地OCR` and `云端OCR` into the shared coordinator.
- [ ] Preserve the existing loading state and post-import refresh behavior.

### Task 4: Sanity-check the touched flow

**Files:**
- Review only touched files from Tasks 1-3

- [ ] Verify both callers use the same OCR mode enum and shared coordinator.
- [ ] Verify local mode never calls `OcrManager`.
- [ ] Verify cloud mode still logs and retries through the existing network path.
- [ ] Verify no proactive build or test command is introduced in this pass.

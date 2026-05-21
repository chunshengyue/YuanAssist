# Stone Local OCR Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let stitched stone imports accumulate local OCR results in memory per segment, then use that cached result only if the user chooses local OCR after stitching.

**Architecture:** Keep cloud OCR unchanged. Add a small local-cache pipeline alongside `InventoryStitchEngine` that tokenizes each stitched segment during capture, stores the accumulated token rows in memory, and exposes a consume/clear API for `DailyWindowManager`.

**Tech Stack:** Kotlin, Android AccessibilityService, existing Paddle local OCR parser flow

---

### Task 1: Add cache model and parser bridge

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/utils/StoneLocalOcrSession.kt`
- Modify: `app/src/main/java/com/example/yuanassist/utils/StoneOcrCoordinator.kt`

- [ ] Define an in-memory session model for accumulated local OCR token rows and raw log lines.
- [ ] Add a coordinator entry that converts the session into rows/stats and writes `MyStoneStore.saveOcrResult(...)`.

### Task 2: Accumulate local OCR during stitching

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/core/InventoryStitchEngine.kt`

- [ ] Add per-run local OCR session state.
- [ ] OCR each appended segment and merge it into the session.
- [ ] Clear the session on stop, restart, and failure.
- [ ] Expose consume/clear helpers for the post-stitch prompt flow.

### Task 3: Switch the post-stitch OCR branch

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/core/DailyWindowManager.kt`

- [ ] On local OCR selection, import from the in-memory session instead of stitched images.
- [ ] On cloud OCR selection, clear the in-memory session first, then keep the current image-based cloud flow.

### Task 4: Add a focused parser test

**Files:**
- Create: `app/src/test/java/com/example/yuanassist/utils/StoneLocalOcrSessionTest.kt`

- [ ] Add a small unit test that proves per-line local OCR token extraction preserves level/name order for downstream parsing.

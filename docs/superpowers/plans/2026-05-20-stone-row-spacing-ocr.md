# Stone Row Spacing OCR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the loose-image OCR split logic with OCR-derived row spacing, using the first two rows to infer the rest, then comparing full-row OCR with four-way split OCR and keeping the better result.

**Architecture:** `StonePaddleLocalRecognizer` will use OCR text to detect the first two rows, infer row spacing for the full image, then build row crops from those centers. Each row crop will be recognized twice, once as a whole and once split into four, and the better result will be kept. `MyStoneActivity` only needs to keep the loose-image OCR entry wired to this flow.

**Tech Stack:** Kotlin, Android Bitmap/OpenCV preprocessing, existing Paddle det/rec bridge, existing stone row models.

---

### Task 1: Add regression tests for row spacing and split-choice behavior

**Files:**
- Modify: `app/src/test/java/com/example/yuanassist/utils/StoneLocalOcrSessionTest.kt`
- Modify: `app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt`

- [ ] **Step 1: Write failing tests**

Cover:
- row spacing can be inferred from the first two OCR rows
- equal-count split choice keeps four-way split
- final row is still included when spacing is inferred

- [ ] **Step 2: Verify failing state**

Run: `./gradlew testDebugUnitTest --tests "*StoneLocalOcrSessionTest"`
Expected: FAIL before the new helpers exist.

- [ ] **Step 3: Implement minimal spacing helpers**

Expose helper methods for:
- deriving row centers from two OCR rows
- building row crops from inferred spacing
- comparing whole-row vs four-way OCR counts

- [ ] **Step 4: Re-run and expect pass**

Run: `./gradlew testDebugUnitTest --tests "*StoneLocalOcrSessionTest"`
Expected: PASS

### Task 2: Rebuild loose-image OCR around inferred row spacing

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt`

- [ ] **Step 1: Replace the current candidate-row clustering path**

Use OCR text to:
- identify the first two name rows and first two level rows
- infer spacing from their centers
- generate the full row center list

- [ ] **Step 2: Implement dual recognition per row**

For each row crop:
- run whole-row OCR
- run four-way split OCR
- keep the result with more recognized cells
- break ties in favor of four-way split

- [ ] **Step 3: Keep existing result model and debug output**

Continue returning `MyStoneRow/MyStoneCell` and log:
- first-two-row centers
- inferred spacing
- whole-row vs split-row counts
- selected mode

### Task 3: Wire the loose-image OCR entry to the new row-spacing flow

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/MyStoneActivity.kt`
- Modify: `app/src/main/java/com/example/yuanassist/utils/StoneOcrCoordinator.kt`

- [ ] **Step 1: Make local OCR use loose images only**

Keep the local OCR button pointed at `currentLooseImageFiles`.

- [ ] **Step 2: Ensure the imported session uses the new row-spacing recognizer**

Do not change the cloud OCR path.

### Task 4: Update docs

**Files:**
- Modify: `docs/project_context.md`

- [ ] **Step 1: Replace old partition notes**

Document the new row-spacing OCR logic.

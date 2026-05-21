# Stone Local Paddle Grid OCR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace star-stone local OCR with a Paddle-based grid/card recognizer and add a Debug workbench entry that visualizes the new region partitioning on uploaded screenshots.

**Architecture:** Keep `StoneOcrCoordinator` as the orchestration entry, but move local recognition into a dedicated `StonePaddleLocalRecognizer` helper. The helper first detects star-stone disk candidates with OpenCV, clusters them into rows/columns, derives per-card/name/level ROIs, and then uses `PaddleTextRecognizer` on small crops. Debug visualization reuses the same analysis result and draws annotated rectangles directly onto the preview bitmap.

**Tech Stack:** Kotlin, Android Bitmap APIs, OpenCV, existing Paddle OCR wrapper, JUnit4 unit tests.

---

## File Structure

- Create: `app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt`
  - Owns disk detection, row/column clustering, ROI derivation, local Paddle OCR, and debug analysis output.
- Modify: `app/src/main/java/com/example/yuanassist/utils/StoneOcrCoordinator.kt`
  - Switch local star-stone OCR from ML Kit to the new recognizer while preserving cloud flow and save flow.
- Modify: `app/src/main/java/com/example/yuanassist/utils/StoneOcrParser.kt`
  - Add tiny helpers for legal-name matching / token normalization only if needed by the recognizer.
- Modify: `app/src/main/java/com/example/yuanassist/ui/main/DebugWorkbenchCoordinator.kt`
  - Register a new debug option and render annotated preview/log output for the star-stone partition algorithm.
- Modify: `app/src/test/java/com/example/yuanassist/utils/StoneLocalOcrSessionTest.kt`
  - Replace old ML Kit tokenization expectations with pure tests for the new recognizer’s row/column and token assembly helpers.
- Create if needed: `docs/project_context.md`
  - Update only if the new recognizer/debug entry changes reusable project guidance.

### Task 1: Add failing unit tests for geometry and token assembly

**Files:**
- Modify: `app/src/test/java/com/example/yuanassist/utils/StoneLocalOcrSessionTest.kt`
- Create: `app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt`

- [ ] **Step 1: Write failing tests for column clustering and row token layout**

```kotlin
@Test
fun `cluster columns falls back to four stable centers`() {
    val centers = StonePaddleLocalRecognizer.clusterColumnsForTest(
        imageWidth = 1080,
        xCenters = listOf(138f, 139f, 352f, 356f, 570f, 782f, 786f)
    )

    assertEquals(4, centers.size)
    assertEquals(listOf(138, 354, 570, 784), centers.map { it.toInt() })
}

@Test
fun `build row tokens keeps levels before names for each detected row`() {
    val row = StonePaddleLocalRecognizer.buildWordGroupForTest(
        cells = listOf(
            StonePaddleLocalRecognizer.DebugCellToken(level = "60级", name = "天府"),
            StonePaddleLocalRecognizer.DebugCellToken(level = "57级", name = "武曲"),
            StonePaddleLocalRecognizer.DebugCellToken(level = "", name = ""),
            StonePaddleLocalRecognizer.DebugCellToken(level = "1级", name = "太阳"),
        )
    )

    assertEquals(
        listOf("60级", "57级", "1级", "天府", "武曲", "太阳"),
        row
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.yuanassist.utils.StoneLocalOcrSessionTest"`

Expected: FAIL because `StonePaddleLocalRecognizer` and the new helper APIs do not exist yet.

- [ ] **Step 3: Add minimal helper shell to satisfy compilation**

```kotlin
object StonePaddleLocalRecognizer {
    data class DebugCellToken(val level: String, val name: String)

    fun clusterColumnsForTest(imageWidth: Int, xCenters: List<Float>): List<Float> = emptyList()

    fun buildWordGroupForTest(cells: List<DebugCellToken>): List<String> = emptyList()
}
```

- [ ] **Step 4: Run test to verify it still fails on assertions**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.yuanassist.utils.StoneLocalOcrSessionTest"`

Expected: FAIL on expected values rather than missing symbols.

- [ ] **Step 5: Commit checkpoint**

```bash
git add app/src/test/java/com/example/yuanassist/utils/StoneLocalOcrSessionTest.kt app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt
git commit -m "test: add star stone local OCR geometry coverage"
```

### Task 2: Implement pure clustering and token assembly helpers

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt`
- Test: `app/src/test/java/com/example/yuanassist/utils/StoneLocalOcrSessionTest.kt`

- [ ] **Step 1: Implement the minimal pure helper logic**

```kotlin
private const val GRID_COLUMN_COUNT = 4

fun clusterColumnsForTest(imageWidth: Int, xCenters: List<Float>): List<Float> =
    clusterColumns(imageWidth.toFloat(), xCenters)

fun buildWordGroupForTest(cells: List<DebugCellToken>): List<String> =
    buildWordGroup(cells.map { CellToken(it.level, it.name) })

private fun clusterColumns(imageWidth: Float, xCenters: List<Float>): List<Float> {
    if (xCenters.isEmpty()) return evenlySplitColumns(imageWidth)
    val sorted = xCenters.sorted()
    val merged = mutableListOf<MutableList<Float>>()
    val tolerance = (imageWidth / 12f).coerceAtLeast(24f)
    sorted.forEach { value ->
        val bucket = merged.firstOrNull { kotlin.math.abs(it.average().toFloat() - value) <= tolerance }
        if (bucket != null) bucket += value else merged += mutableListOf(value)
    }
    val centers = merged
        .sortedBy { it.average() }
        .map { it.average().toFloat() }
    return when {
        centers.size == GRID_COLUMN_COUNT -> centers
        centers.size > GRID_COLUMN_COUNT -> centers.take(GRID_COLUMN_COUNT)
        else -> evenlySplitColumns(imageWidth)
    }
}

private fun evenlySplitColumns(imageWidth: Float): List<Float> {
    val stride = imageWidth / GRID_COLUMN_COUNT.toFloat()
    return List(GRID_COLUMN_COUNT) { index -> stride * index + stride / 2f }
}

private fun buildWordGroup(cells: List<CellToken>): List<String> {
    val levels = cells.mapNotNull { it.level.takeIf(String::isNotBlank) }
    val names = cells.mapNotNull { it.name.takeIf(String::isNotBlank) }
    return levels + names
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.yuanassist.utils.StoneLocalOcrSessionTest"`

Expected: PASS

- [ ] **Step 3: Refactor helper names if needed while staying green**

```kotlin
private data class CellToken(
    val level: String,
    val name: String,
)
```

- [ ] **Step 4: Re-run tests after refactor**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.yuanassist.utils.StoneLocalOcrSessionTest"`

Expected: PASS

- [ ] **Step 5: Commit checkpoint**

```bash
git add app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt app/src/test/java/com/example/yuanassist/utils/StoneLocalOcrSessionTest.kt
git commit -m "feat: add star stone local OCR layout helpers"
```

### Task 3: Implement bitmap analysis, ROI derivation, and local Paddle OCR

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt`
- Modify: `app/src/main/java/com/example/yuanassist/utils/StoneOcrParser.kt`
- Modify: `app/src/main/java/com/example/yuanassist/utils/StoneOcrCoordinator.kt`

- [ ] **Step 1: Add analysis/result models and OpenCV detection pipeline**

```kotlin
data class StoneGridAnalysis(
    val cardRows: List<StoneCardRow>,
    val debugLines: List<String>,
)

data class StoneCardRow(
    val rowIndex: Int,
    val cards: List<StoneCardDetection>,
)

data class StoneCardDetection(
    val columnIndex: Int,
    val cardRect: Rect,
    val levelRect: Rect,
    val nameRect: Rect,
    val levelText: String = "",
    val nameText: String = "",
)

suspend fun recognize(
    context: Context,
    bitmap: Bitmap,
    stoneType: String,
): StoneLocalOcrSession = withContext(Dispatchers.Default) {
    ensureOpenCvReady()
    val analysis = analyzeBitmap(bitmap)
    val allowedNames = StoneOcrParser.validStoneNamesForType(stoneType)
    val rows = analysis.cardRows.map { row ->
        row.cards.map { card ->
            val level = recognizeLevel(context, bitmap, card.levelRect)
            val name = recognizeName(context, bitmap, card.nameRect, allowedNames)
            CellToken(level = level, name = name)
        }
    }
    StoneLocalOcrSession(
        wordGroups = rows.map(::buildWordGroup),
        rawLogLines = buildDebugLogs(analysis, rows),
        strategyUsed = "local:paddle-grid-v1",
    )
}
```

- [ ] **Step 2: Wire `StoneOcrCoordinator` local branch to the new recognizer**

```kotlin
private suspend fun recognizeLocalStoneImage(
    context: Context,
    bitmap: Bitmap,
    stoneType: String,
): StoneFileOcrResult = withContext(Dispatchers.IO) {
    val session = StonePaddleLocalRecognizer.recognize(context, bitmap, stoneType)
    StoneFileOcrResult(
        wordGroups = session.wordGroups,
        rawLogLines = session.rawLogLines,
        rawLogTitle = "【本地OCR返回原文本】",
        strategyUsed = session.strategyUsed,
    )
}
```

- [ ] **Step 3: Add minimal legal-name post-processing helper if the recognizer needs it**

```kotlin
fun matchValidStoneName(
    text: String,
    stoneType: String,
): String {
    val candidates = validStoneNamesForType(stoneType)
    val normalized = normalizeToken(text)
    return candidates.firstOrNull { it == normalized || it.contains(normalized) || normalized.contains(it) }.orEmpty()
}
```

- [ ] **Step 4: Run unit tests and compile check**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.yuanassist.utils.StoneLocalOcrSessionTest"`

Expected: PASS

Run: `./gradlew.bat compileDebugKotlin`

Expected: PASS

- [ ] **Step 5: Commit checkpoint**

```bash
git add app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt app/src/main/java/com/example/yuanassist/utils/StoneOcrCoordinator.kt app/src/main/java/com/example/yuanassist/utils/StoneOcrParser.kt
git commit -m "feat: switch star stone local OCR to paddle grid recognizer"
```

### Task 4: Add Debug workbench entry and annotated preview

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/main/DebugWorkbenchCoordinator.kt`

- [ ] **Step 1: Add a new debug option constant and include it in battle-flow template options**

```kotlin
private const val STONE_GRID_DEBUG_OPTION = "STONE_GRID_DEBUG"
private const val STONE_GRID_DEBUG_LABEL = "星石区域划分（本地Paddle）"
```

```kotlin
val options = linkedSetOf(
    START_BATTLE_OCR_OPTION,
    BATTLE_FLOW_FIRST_ACTION_DELAY_OPTION,
    BATTLE_TURN_OCR_OPTION,
    STONE_GRID_DEBUG_OPTION,
    CHARACTER_PROFICIENCY_OPTION,
    CHARACTER_FATE_OPTION,
    ...
)
```

- [ ] **Step 2: Add a failing branch in the debug dispatcher**

```kotlin
when (selectedTemplate) {
    STONE_GRID_DEBUG_OPTION -> runStoneGridDebugTest()
    ...
}
```

- [ ] **Step 3: Implement `runStoneGridDebugTest()` using the shared recognizer analysis**

```kotlin
private fun runStoneGridDebugTest() {
    val screenshot = currentBitmap ?: return log("请先上传截图")
    log("------------------------")
    log("Start matching: $STONE_GRID_DEBUG_LABEL")
    ocrScope.launch {
        runCatching {
            val analysis = StonePaddleLocalRecognizer.analyzeForDebug(activity, screenshot)
            val preview = StonePaddleLocalRecognizer.drawDebugPreview(screenshot, analysis)
            withContext(Dispatchers.Main) {
                previewBitmap = preview
                analysis.debugLines.forEach(::log)
                analysis.cardRows.forEach { row ->
                    row.cards.forEach { card ->
                        log("row=${row.rowIndex} col=${card.columnIndex} level=${card.levelText} name=${card.nameText}")
                    }
                }
                pushState()
            }
        }.onFailure { error ->
            withContext(Dispatchers.Main) {
                previewBitmap = screenshot
                log("星石区域划分失败: ${error.message}")
                pushState()
            }
        }
    }
}
```

- [ ] **Step 4: Update labels and scope hint**

```kotlin
selectedTemplate == STONE_GRID_DEBUG_OPTION ->
    "使用星石圆盘定位 + 四列聚类，显示卡片框、等级ROI、名称ROI，并输出每格识别结果"
```

```kotlin
templateName == STONE_GRID_DEBUG_OPTION -> STONE_GRID_DEBUG_LABEL
```

- [ ] **Step 5: Compile check**

Run: `./gradlew.bat compileDebugKotlin`

Expected: PASS

- [ ] **Step 6: Commit checkpoint**

```bash
git add app/src/main/java/com/example/yuanassist/ui/main/DebugWorkbenchCoordinator.kt
git commit -m "feat: add star stone grid debug visualization"
```

### Task 5: Final verification and docs sync

**Files:**
- Modify if needed: `docs/project_context.md`
- Verify: changed OCR/debug files

- [ ] **Step 1: Update `docs/project_context.md` only if the new recognizer/debug entry adds stable reusable guidance**

```md
- 星石本地 OCR：
  - 入口仍在 `StoneOcrCoordinator`
  - 本地链路使用 `StonePaddleLocalRecognizer`
  - Debug 工作台新增星石区域划分验证入口
```

- [ ] **Step 2: Run focused verification**

Run: `./gradlew.bat testDebugUnitTest --tests "com.example.yuanassist.utils.StoneLocalOcrSessionTest"`

Expected: PASS

Run: `./gradlew.bat compileDebugKotlin`

Expected: PASS

- [ ] **Step 3: Review git diff to ensure only intended files changed**

Run: `git diff -- app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt app/src/main/java/com/example/yuanassist/utils/StoneOcrCoordinator.kt app/src/main/java/com/example/yuanassist/utils/StoneOcrParser.kt app/src/main/java/com/example/yuanassist/ui/main/DebugWorkbenchCoordinator.kt app/src/test/java/com/example/yuanassist/utils/StoneLocalOcrSessionTest.kt docs/project_context.md`

Expected: Diff limited to star-stone OCR/debug scope.

- [ ] **Step 4: Commit final checkpoint**

```bash
git add app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt app/src/main/java/com/example/yuanassist/utils/StoneOcrCoordinator.kt app/src/main/java/com/example/yuanassist/utils/StoneOcrParser.kt app/src/main/java/com/example/yuanassist/ui/main/DebugWorkbenchCoordinator.kt app/src/test/java/com/example/yuanassist/utils/StoneLocalOcrSessionTest.kt docs/project_context.md
git commit -m "feat: improve star stone local OCR and debug visualization"
```

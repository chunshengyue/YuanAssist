package com.example.yuanassist.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.example.yuanassist.tableocr.PaddleTextRecognizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader

object StonePaddleLocalRecognizer {

    private const val GRID_COLUMN_COUNT = 4
    private const val STRATEGY = "local:paddle-row-spacing-v3"
    private const val ANALYSIS_BASE_WIDTH = 1080
    private const val MIN_FIRST_TWO_GAP = 12

    data class DebugCellToken(
        val level: String,
        val name: String,
    )

    data class StoneGridAnalysis(
        val cardRows: List<StoneCardRow>,
        val debugLines: List<String>,
        val levelGuideRects: List<Rect> = emptyList(),
        val nameGuideRects: List<Rect> = emptyList(),
    )

    data class StoneCardRow(
        val rowIndex: Int,
        val cards: List<StoneCardDetection>,
        val levelRowRect: Rect,
        val nameRowRect: Rect,
    )

    data class StoneCardDetection(
        val columnIndex: Int,
        val levelRect: Rect,
        val nameRect: Rect,
        val levelText: String = "",
        val nameText: String = "",
    )

    private data class AnalysisBitmap(
        val bitmap: Bitmap,
        val scaleBack: Float,
    )

    private data class CellToken(
        val level: String,
        val name: String,
    )

    private data class LineInfo(
        val text: String,
        val boundingBox: Rect,
    )

    private data class RowSeries(
        val kind: String,
        val firstCenterY: Float,
        val spacing: Float,
        val centers: List<Float>,
        val rowHeight: Int,
    )

    private data class RowRecognition(
        val wholeText: String,
        val wholeSegments: List<String>,
        val splitSegments: List<String>,
        val wholeCells: List<String>,
        val splitCells: List<String>,
        val chosenCells: List<String>,
        val choseSplit: Boolean,
        val wholeValidCount: Int,
        val splitValidCount: Int,
    )

    suspend fun recognize(
        context: Context,
        bitmap: Bitmap,
        stoneType: String,
    ): StoneLocalOcrSession = withContext(Dispatchers.Default) {
        val analysis = analyze(context, bitmap, stoneType)
        val rows = analysis.cardRows.map { row ->
            val cells = row.cards.map { card ->
                MyStoneCell(level = card.levelText.ifBlank { "0级" }, name = card.nameText)
            }.toMutableList()
            MyStoneRow(cells)
        }
        StoneLocalOcrSession(
            wordGroups = rows.map { row -> buildWordGroup(row.cells.map { CellToken(it.level, it.name) }) },
            rawLogLines = analysis.debugLines,
            strategyUsed = STRATEGY,
            rows = rows,
        )
    }

    fun analyzeForDebug(
        context: Context,
        bitmap: Bitmap,
    ): StoneGridAnalysis = analyze(context, bitmap, null)

    fun drawDebugPreview(
        bitmap: Bitmap,
        analysis: StoneGridAnalysis,
    ): Bitmap {
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#56C271")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4DA3FF")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val splitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7D76B")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            style = Paint.Style.FILL
        }

        analysis.levelGuideRects.forEach { canvas.drawRect(it, levelPaint) }
        analysis.nameGuideRects.forEach { canvas.drawRect(it, namePaint) }
        analysis.cardRows.forEach { row ->
            row.cards.forEachIndexed { index, card ->
                if (index < row.cards.lastIndex) {
                    canvas.drawLine(
                        card.nameRect.right.toFloat(),
                        row.nameRowRect.top.toFloat(),
                        card.nameRect.right.toFloat(),
                        row.nameRowRect.bottom.toFloat(),
                        splitPaint,
                    )
                }
                canvas.drawText(
                    "r${row.rowIndex + 1}c${index + 1}",
                    card.nameRect.left.toFloat(),
                    (row.nameRowRect.top - 8).toFloat(),
                    textPaint,
                )
            }
        }
        return mutable
    }

    fun buildWordGroupForTest(
        cells: List<DebugCellToken>,
    ): List<String> = buildWordGroup(cells.map { CellToken(it.level, it.name) })

    fun inferRowSpacingForTest(firstRowCenterY: Float, secondRowCenterY: Float): Float =
        abs(secondRowCenterY - firstRowCenterY)

    fun buildRowCentersForTest(
        firstCenterY: Float,
        spacing: Float,
        rowCount: Int,
        imageHeight: Int,
    ): List<Float> = buildRowCenters(firstCenterY, spacing, rowCount, imageHeight)

    fun resolveRowCountForTest(
        imageWidth: Int,
        imageHeight: Int,
        levelSeedCount: Int,
        nameSeedCount: Int,
        inferredLevelCount: Int,
        inferredNameCount: Int,
    ): Int = resolveRowCount(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        levelSeedCount = levelSeedCount,
        nameSeedCount = nameSeedCount,
        inferredLevelCount = inferredLevelCount,
        inferredNameCount = inferredNameCount,
    )

    fun chooseSplitForTest(wholeCount: Int, splitCount: Int): Boolean =
        splitCount >= wholeCount

    fun mergeCellsForTest(
        directWholeCells: List<String>,
        directSplitCells: List<String>,
        repairWholeCells: List<String>,
        repairSplitCells: List<String>,
        kind: String,
    ): List<String> = mergeCellsByConfidence(
        directWholeCells = directWholeCells,
        directSplitCells = directSplitCells,
        repairWholeCells = repairWholeCells,
        repairSplitCells = repairSplitCells,
        kind = kind,
    )

    private fun analyze(
        context: Context,
        bitmap: Bitmap,
        stoneType: String?,
    ): StoneGridAnalysis {
        ensureOpenCvReady()
        val prepared = prepareAnalysisBitmap(bitmap)
        try {
            val detected = PaddleTextRecognizer.recognizeLines(context, prepared.bitmap)
            val lines = detected.textLines
                .mapNotNull { line ->
                    val normalized = StoneOcrParser.normalizeToken(line.text)
                    if (normalized.isEmpty()) null else LineInfo(normalized, line.boundingBox)
                }
                .sortedBy { it.boundingBox.top }

            val levelSeed = lines.filter { looksLikeLevelLine(it.text) }
            val nameSeed = lines.filter { countStoneChars(it.text, stoneType) >= 3 }

            val levelSpacing = inferSpacingFromFirstTwo(levelSeed, prepared.bitmap.height)
            val nameSpacing = inferSpacingFromFirstTwo(nameSeed, prepared.bitmap.height)
            val inferredLevelCount = inferRowCountFromHeight(levelSeed, levelSpacing, prepared.bitmap.height)
            val inferredNameCount = inferRowCountFromHeight(nameSeed, nameSpacing, prepared.bitmap.height)
            val rowCount = resolveRowCount(
                imageWidth = prepared.bitmap.width,
                imageHeight = prepared.bitmap.height,
                levelSeedCount = levelSeed.size,
                nameSeedCount = nameSeed.size,
                inferredLevelCount = inferredLevelCount,
                inferredNameCount = inferredNameCount,
            )
            val levelSeries = buildRowSeries(levelSeed, rowCount, prepared.bitmap.height, "level")
            val nameSeries = buildRowSeries(nameSeed, rowCount, prepared.bitmap.height, "name")

            val levelRecognitions = mutableListOf<RowRecognition>()
            val nameRecognitions = mutableListOf<RowRecognition>()
            val rows = (0 until rowCount).map { rowIndex ->
                val levelRect = buildGuideRect(prepared.bitmap.width, prepared.bitmap.height, levelSeries.centers[rowIndex], levelSeries.rowHeight)
                val nameRect = buildGuideRect(prepared.bitmap.width, prepared.bitmap.height, nameSeries.centers[rowIndex], nameSeries.rowHeight)

                val levelRecognition = recognizeRow(context, prepared.bitmap, levelRect, "level", stoneType)
                val nameRecognition = recognizeRow(context, prepared.bitmap, nameRect, "name", stoneType)
                levelRecognitions += levelRecognition
                nameRecognitions += nameRecognition

                val cards = List(GRID_COLUMN_COUNT) { columnIndex ->
                    val levelCellRect = splitRowRect(levelRect)[columnIndex]
                    val nameCellRect = splitRowRect(nameRect)[columnIndex]
                    StoneCardDetection(
                        columnIndex = columnIndex,
                        levelRect = scaleRectToOriginal(levelCellRect, prepared.scaleBack, bitmap.width, bitmap.height),
                        nameRect = scaleRectToOriginal(nameCellRect, prepared.scaleBack, bitmap.width, bitmap.height),
                        levelText = normalizeLevelChoice(levelRecognition.chosenCells.getOrElse(columnIndex) { "" }),
                        nameText = normalizeNameChoice(nameRecognition.chosenCells.getOrElse(columnIndex) { "" }, stoneType),
                    )
                }

                StoneCardRow(
                    rowIndex = rowIndex,
                    cards = cards,
                    levelRowRect = scaleRectToOriginal(levelRect, prepared.scaleBack, bitmap.width, bitmap.height),
                    nameRowRect = scaleRectToOriginal(nameRect, prepared.scaleBack, bitmap.width, bitmap.height),
                )
            }

            val debugLines = buildList {
                add("analysis=${prepared.bitmap.width}x${prepared.bitmap.height} scale=${"%.3f".format(prepared.scaleBack)}")
                add("paddle行=${lines.size}")
                add("等级种子=${levelSeed.size} spacing=${"%.1f".format(levelSeries.spacing)} inferred=$inferredLevelCount")
                add("名字种子=${nameSeed.size} spacing=${"%.1f".format(nameSeries.spacing)} inferred=$inferredNameCount")
                add("推导行数=$rowCount")
                rows.forEachIndexed { index, row ->
                    add("r${row.rowIndex + 1} levelWholeRaw=${levelRecognitions[index].wholeText.ifBlank { "∅" }}")
                    add("r${row.rowIndex + 1} levelWhole=${levelRecognitions[index].wholeCells.joinToString(" | ") { formatLogText(it) }}")
                    add("r${row.rowIndex + 1} levelSplitRaw=${levelRecognitions[index].splitSegments.joinToString(" | ") { formatLogText(it) }}")
                    add("r${row.rowIndex + 1} levelSplit=${levelRecognitions[index].splitCells.joinToString(" | ") { formatLogText(it) }}")
                    add("r${row.rowIndex + 1} levelChosen=${levelRecognitions[index].chosenCells.joinToString(" | ") { formatLogText(it) }}")
                    add("r${row.rowIndex + 1} nameWholeRaw=${nameRecognitions[index].wholeText.ifBlank { "∅" }}")
                    add("r${row.rowIndex + 1} nameWhole=${nameRecognitions[index].wholeCells.joinToString(" | ") { formatLogText(it) }}")
                    add("r${row.rowIndex + 1} nameSplitRaw=${nameRecognitions[index].splitSegments.joinToString(" | ") { formatLogText(it) }}")
                    add("r${row.rowIndex + 1} nameSplit=${nameRecognitions[index].splitCells.joinToString(" | ") { formatLogText(it) }}")
                    add("r${row.rowIndex + 1} nameChosen=${nameRecognitions[index].chosenCells.joinToString(" | ") { formatLogText(it) }}")
                }
            }

            return StoneGridAnalysis(
                cardRows = rows,
                debugLines = debugLines,
                levelGuideRects = levelSeries.centers.map {
                    scaleRectToOriginal(
                        buildGuideRect(prepared.bitmap.width, prepared.bitmap.height, it, levelSeries.rowHeight),
                        prepared.scaleBack,
                        bitmap.width,
                        bitmap.height,
                    )
                },
                nameGuideRects = nameSeries.centers.map {
                    scaleRectToOriginal(
                        buildGuideRect(prepared.bitmap.width, prepared.bitmap.height, it, nameSeries.rowHeight),
                        prepared.scaleBack,
                        bitmap.width,
                        bitmap.height,
                    )
                },
            )
        } finally {
            prepared.bitmap.recycle()
        }
    }

    private fun resolveRowCount(
        imageWidth: Int,
        imageHeight: Int,
        levelSeedCount: Int,
        nameSeedCount: Int,
        inferredLevelCount: Int,
        inferredNameCount: Int,
    ): Int {
        val aspectRatio = imageWidth.toFloat() / imageHeight.coerceAtLeast(1).toFloat()
        val maxSeedCount = max(levelSeedCount, nameSeedCount)
        if (aspectRatio > 3f && maxSeedCount <= 1) {
            return 1
        }
        return max(inferredLevelCount, inferredNameCount).coerceAtLeast(1)
    }

    private fun buildRowSeries(
        seeds: List<LineInfo>,
        rowCount: Int,
        imageHeight: Int,
        kind: String,
    ): RowSeries {
        val firstCenter = seeds.firstOrNull()?.boundingBox?.centerY()?.toFloat() ?: (imageHeight / 2f)
        val secondCenter = seeds.getOrNull(1)?.boundingBox?.centerY()?.toFloat()
        val spacing = secondCenter?.let { abs(it - firstCenter) }?.coerceAtLeast(MIN_FIRST_TWO_GAP.toFloat())
            ?: max(1f, imageHeight / 12f)
        val baseHeight = seeds.firstOrNull()?.boundingBox?.height()?.coerceAtLeast(36) ?: (imageHeight / 18)
        val height = when (kind) {
            "level" -> max(48, (baseHeight * 1.55f).roundToInt())
            "name" -> max(42, (baseHeight * 1.25f).roundToInt())
            else -> max(36, baseHeight)
        }
        val centers = buildRowCenters(firstCenter, spacing, rowCount, imageHeight)
        return RowSeries(
            kind = kind,
            firstCenterY = firstCenter,
            spacing = spacing,
            centers = centers,
            rowHeight = height,
        )
    }

    private fun buildRowCenters(
        firstCenter: Float,
        spacing: Float,
        rowCount: Int,
        imageHeight: Int,
    ): List<Float> {
        val safeHeight = imageHeight.coerceAtLeast(1)
        val safeSpacing = spacing.coerceAtLeast(1f)
        return List(rowCount.coerceAtLeast(0)) { index ->
            (firstCenter + index * safeSpacing).coerceIn(0f, safeHeight - 1f)
        }
    }

    private fun inferSpacingFromFirstTwo(
        seeds: List<LineInfo>,
        imageHeight: Int,
    ): Float {
        if (seeds.size < 2) {
            return max(1f, imageHeight / 12f)
        }
        return abs(seeds[1].boundingBox.centerY() - seeds[0].boundingBox.centerY())
            .toFloat()
            .coerceAtLeast(MIN_FIRST_TWO_GAP.toFloat())
    }

    private fun inferRowCountFromHeight(
        seeds: List<LineInfo>,
        spacing: Float,
        imageHeight: Int,
    ): Int {
        if (spacing <= 0f) return max(1, seeds.size)
        val firstCenter = seeds.firstOrNull()?.boundingBox?.centerY()?.toFloat() ?: (imageHeight / 2f)
        if (firstCenter > imageHeight - 1f) return 0
        var count = 0
        var center = firstCenter
        while (center <= imageHeight - 1f) {
            count++
            center += spacing
        }
        return count.coerceAtLeast(1)
    }

    private fun recognizeRow(
        context: Context,
        bitmap: Bitmap,
        rowRect: Rect,
        kind: String,
        stoneType: String?,
    ): RowRecognition {
        val wholeSegments = recognizeWholeRowSegments(context, bitmap, rowRect)
        val wholeText = wholeSegments.joinToString("")
        val wholeCells = normalizeSegments(wholeSegments, kind, stoneType)
        val splitRects = splitRowRect(rowRect)
        val splitTexts = splitRects.map { recognizeText(context, bitmap, it) }
        val splitCells = normalizeSegments(splitTexts, kind, stoneType, trimLevelAfterJi = true)
        val directWholeCells = directCells(wholeSegments, wholeText, kind, stoneType)
        val directSplitCells = directCells(splitTexts, splitTexts.joinToString(""), kind, stoneType)
        val rowWholeCells = rowRepairCells(wholeText, wholeCells, kind, stoneType)
        val chosenCells = mergeCellsByConfidence(
            directWholeCells = directWholeCells,
            directSplitCells = directSplitCells,
            repairWholeCells = rowWholeCells,
            repairSplitCells = splitCells,
            kind = kind,
        )
        val wholeCount = countValidCells(rowWholeCells, kind)
        val splitCount = countValidCells(splitCells, kind)
        val choseSplit = splitCount >= wholeCount
        return RowRecognition(
            wholeText = wholeText,
            wholeSegments = wholeSegments,
            splitSegments = splitTexts,
            wholeCells = normalizeCellCount(rowWholeCells),
            splitCells = normalizeCellCount(splitCells),
            chosenCells = normalizeCellCount(chosenCells),
            choseSplit = choseSplit,
            wholeValidCount = wholeCount,
            splitValidCount = splitCount,
        )
    }

    private fun normalizeCellCount(cells: List<String>): List<String> {
        val normalized = cells.take(GRID_COLUMN_COUNT)
        return if (normalized.size < GRID_COLUMN_COUNT) {
            normalized + List(GRID_COLUMN_COUNT - normalized.size) { "" }
        } else {
            normalized
        }
    }

    private fun directCells(
        segments: List<String>,
        rowText: String,
        kind: String,
        stoneType: String?,
    ): List<String> {
        return when (kind) {
            "level" -> if (segments.size == GRID_COLUMN_COUNT) {
                normalizeCellCount(segments.map { StoneOcrParser.resolveDirectStoneLevel(it) })
            } else {
                StoneOcrParser.splitDirectStoneLevelRow(rowText)
            }
            "name" -> if (segments.size == GRID_COLUMN_COUNT) {
                normalizeCellCount(segments.map { StoneOcrParser.resolveDirectStoneName(it, stoneType ?: MyStoneStore.TYPE_MAIN) })
            } else {
                StoneOcrParser.splitDirectStoneNameRow(rowText, stoneType ?: MyStoneStore.TYPE_MAIN)
            }
            else -> normalizeSegments(segments, kind, stoneType)
        }
    }

    private fun rowRepairCells(
        rowText: String,
        fallbackCells: List<String>,
        kind: String,
        stoneType: String?,
    ): List<String> {
        return when (kind) {
            "level" -> StoneOcrParser.splitStoneLevelRow(rowText)
            "name" -> StoneOcrParser.splitStoneNameRow(rowText, stoneType ?: MyStoneStore.TYPE_MAIN)
            else -> fallbackCells
        }
    }

    private fun mergeCellsByConfidence(
        directWholeCells: List<String>,
        directSplitCells: List<String>,
        repairWholeCells: List<String>,
        repairSplitCells: List<String>,
        kind: String,
    ): List<String> {
        return List(GRID_COLUMN_COUNT) { index ->
            val directWhole = directWholeCells.getOrElse(index) { "" }
            val directSplit = directSplitCells.getOrElse(index) { "" }
            val repairWhole = repairWholeCells.getOrElse(index) { "" }
            val repairSplit = repairSplitCells.getOrElse(index) { "" }
            val directWholeValid = isValidCell(directWhole, kind)
            val directSplitValid = isValidCell(directSplit, kind)
            when {
                directWholeValid && directSplitValid -> directSplit
                directSplitValid -> directSplit
                directWholeValid -> directWhole
                isValidCell(repairSplit, kind) -> repairSplit
                isValidCell(repairWhole, kind) -> repairWhole
                else -> ""
            }
        }
    }

    private fun isValidCell(
        cell: String,
        kind: String,
    ): Boolean {
        return when (kind) {
            "level" -> StoneOcrParser.isValidLevel(cell)
            "name" -> StoneOcrParser.isValidStoneName(cell)
            else -> cell.isNotBlank()
        }
    }

    private fun splitRowRect(rowRect: Rect): List<Rect> {
        val stride = rowRect.width().toFloat() / GRID_COLUMN_COUNT.toFloat()
        return List(GRID_COLUMN_COUNT) { index ->
            val left = (rowRect.left + index * stride).roundToInt()
            val right = if (index == GRID_COLUMN_COUNT - 1) rowRect.right else (rowRect.left + (index + 1) * stride).roundToInt()
            Rect(left, rowRect.top, right, rowRect.bottom)
        }
    }

    private fun buildGuideRect(
        imageWidth: Int,
        imageHeight: Int,
        centerY: Float,
        rowHeight: Int,
    ): Rect {
        val top = (centerY - rowHeight / 2f).roundToInt()
        val bottom = top + rowHeight
        return Rect(0, top, imageWidth, bottom)
    }

    private fun recognizeText(
        context: Context,
        bitmap: Bitmap,
        rect: Rect,
    ): String {
        val crop = cropBitmap(bitmap, rect) ?: return ""
        return try {
            PaddleTextRecognizer.recognize(context, crop).text.trim()
        } finally {
            crop.recycle()
        }
    }

    private fun chunkRowText(text: String): List<String> {
        val normalized = StoneOcrParser.normalizeToken(text)
        if (normalized.isEmpty()) return List(GRID_COLUMN_COUNT) { "" }
        val chunkSize = max(1, normalized.length / GRID_COLUMN_COUNT)
        return normalized.chunked(chunkSize)
    }

    private fun recognizeWholeRowSegments(
        context: Context,
        bitmap: Bitmap,
        rowRect: Rect,
    ): List<String> {
        val crop = cropBitmap(bitmap, rowRect) ?: return emptyList()
        return try {
            val result = PaddleTextRecognizer.recognizeLines(context, crop)
            val lines = result.textLines
                .mapNotNull { line ->
                    val normalized = StoneOcrParser.normalizeToken(line.text)
                    normalized.takeIf { it.isNotBlank() }?.let { normalized to line.boundingBox }
                }
                .sortedBy { it.second.left }
            if (lines.isEmpty()) return emptyList()

            val averageWidth = lines.map { it.second.width() }.average().toFloat().coerceAtLeast(1f)
            val gapThreshold = max(16f, averageWidth * 1.15f)
            val segments = mutableListOf<String>()
            var current = StringBuilder(lines.first().first)
            var previousRight = lines.first().second.right
            for ((text, rect) in lines.drop(1)) {
                val gap = rect.left - previousRight
                if (gap > gapThreshold) {
                    segments += current.toString()
                    current = StringBuilder(text)
                } else {
                    current.append(text)
                }
                previousRight = rect.right
            }
            segments += current.toString()
            segments
        } finally {
            crop.recycle()
        }
    }

    private fun normalizeSegments(
        segments: List<String>,
        kind: String,
        stoneType: String?,
        trimLevelAfterJi: Boolean = false,
    ): List<String> {
        val normalized = segments.map { text ->
            when (kind) {
                "level" -> normalizeLevelChoice(if (trimLevelAfterJi) trimLevelSuffixAfterJi(text) else text)
                "name" -> normalizeNameChoice(text, stoneType)
                else -> StoneOcrParser.normalizeToken(text)
            }
        }
        return normalizeCellCount(normalized)
    }

    private fun countValidCells(
        cells: List<String>,
        kind: String,
    ): Int {
        return cells.count { cell ->
            when (kind) {
                "level" -> StoneOcrParser.isValidLevel(cell)
                "name" -> StoneOcrParser.isValidStoneName(cell)
                else -> cell.isNotBlank()
            }
        }
    }

    private fun formatLogText(text: String): String {
        val normalized = StoneOcrParser.normalizeToken(text)
        return if (normalized.isBlank()) "∅" else normalized
    }

    private fun looksLikeLevelLine(text: String): Boolean {
        val normalized = StoneOcrParser.normalizeToken(text)
        return normalized.count { it == '级' } >= 1 || normalized.count(Char::isDigit) >= 2
    }

    private fun normalizeLevelChoice(text: String): String =
        StoneOcrParser.normalizeLevel(text).takeIf { StoneOcrParser.isValidLevel(it) }.orEmpty()

    private fun trimLevelSuffixAfterJi(text: String): String {
        val normalized = StoneOcrParser.normalizeToken(text)
        val jiIndex = normalized.indexOf('级')
        return if (jiIndex >= 0) normalized.substring(0, jiIndex + 1) else normalized
    }

    private fun normalizeNameChoice(text: String, stoneType: String?): String =
        StoneOcrParser.matchValidStoneName(text, stoneType ?: MyStoneStore.TYPE_MAIN)

    private fun countStoneChars(text: String, stoneType: String?): Int {
        val names = when {
            stoneType.isNullOrBlank() -> StoneOcrParser.validStoneNames
            else -> StoneOcrParser.validStoneNamesForType(stoneType)
        }
        val allowedChars = names.flatMap { it.toList() }.toSet()
        return StoneOcrParser.normalizeToken(text).count { it in allowedChars }
    }

    private fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap? {
        val safeRect = clampRect(rect, bitmap.width, bitmap.height)
        if (safeRect.width() <= 0 || safeRect.height() <= 0) return null
        return Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height())
    }

    private fun prepareAnalysisBitmap(bitmap: Bitmap): AnalysisBitmap {
        if (bitmap.width == ANALYSIS_BASE_WIDTH) {
            return AnalysisBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, false), 1f)
        }
        val scale = bitmap.width.toFloat() / ANALYSIS_BASE_WIDTH.toFloat()
        val targetHeight = max(1, (bitmap.height / scale).roundToInt())
        val resized = Bitmap.createScaledBitmap(bitmap, ANALYSIS_BASE_WIDTH, targetHeight, true)
        return AnalysisBitmap(resized, scale)
    }

    private fun buildWordGroup(cells: List<CellToken>): List<String> {
        val levels = cells.map { it.level.ifBlank { "0级" } }
        val names = cells.mapNotNull { it.name.takeIf(String::isNotBlank) }
        return levels + names
    }

    private fun scaleRectToOriginal(
        rect: Rect,
        scale: Float,
        imageWidth: Int,
        imageHeight: Int,
    ): Rect = clampRect(
        Rect(
            (rect.left * scale).roundToInt(),
            (rect.top * scale).roundToInt(),
            (rect.right * scale).roundToInt(),
            (rect.bottom * scale).roundToInt(),
        ),
        imageWidth,
        imageHeight,
    )

    private fun clampRect(
        rect: Rect,
        imageWidth: Int,
        imageHeight: Int,
    ): Rect {
        val safeWidth = imageWidth.coerceAtLeast(1)
        val safeHeight = imageHeight.coerceAtLeast(1)
        val left = rect.left.coerceIn(0, safeWidth - 1)
        val top = rect.top.coerceIn(0, safeHeight - 1)
        val right = rect.right.coerceIn(left + 1, safeWidth)
        val bottom = rect.bottom.coerceIn(top + 1, safeHeight)
        return Rect(left, top, right, bottom)
    }

    private fun ensureOpenCvReady() {
        check(OpenCVLoader.initDebug()) { "OpenCV 初始化失败" }
    }
}

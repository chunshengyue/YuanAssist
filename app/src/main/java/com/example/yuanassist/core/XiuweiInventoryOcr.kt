package com.example.yuanassist.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.example.yuanassist.tableocr.PaddleTextLine
import com.example.yuanassist.tableocr.PaddleTextRecognizer
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.TraditionalModeStore
import kotlin.math.max
import kotlin.math.roundToInt

data class XiuweiOcrResult(
    val counts: Map<String, Int>,
    val rawLines: List<String>,
    val recognizedNames: List<String>,
)

object XiuweiInventoryOcr {
    private const val STANDARD_WIDTH = 1080
    private const val STANDARD_HEIGHT = 1920
    private const val COLUMN_COUNT = 4
    // 从背包 Tab 附近开始扫描；材料名白名单会过滤顶部货币/体力栏的无关文字。
    // 长图按宽度缩放后首行材料会上移，不能再使用原短图的 420 固定起点。
    private const val INVENTORY_TOP = 300
    private const val NUMBER_ROI_HEIGHT = 105
    private const val NUMBER_ROI_GAP = 5

    private data class NameCandidate(
        val material: XiuweiMaterial,
        val score: Float,
        val rect: Rect,
    )

    private data class ColumnOcrResult(
        val lines: List<PaddleTextLine>,
        val absoluteLines: List<PaddleTextLine>,
    )

    private data class NumberResult(val value: Int)

    fun recognize(context: Context, bitmap: Bitmap): XiuweiOcrResult {
        val normalized = normalizeTo1080Width(bitmap)
        val standard = normalized.bitmap
        RunLogger.i(
            module = "修为计算",
            section = "OCR",
            message = "图片基准：${bitmap.width}x${bitmap.height}，内容区=(${normalized.sourceRect.left},${normalized.sourceRect.top},${normalized.sourceRect.right},${normalized.sourceRect.bottom})，缩放=${"%.4f".format(java.util.Locale.US, normalized.scale)}，OCR图=${standard.width}x${standard.height}，背包区域=${INVENTORY_TOP}..${standard.height}，分栏宽度=${STANDARD_WIDTH / COLUMN_COUNT}，繁体模式=${TraditionalModeStore.isEnabled(context)}",
        )
        val counts = linkedMapOf<String, Int>()
        val rawLines = mutableListOf<String>()
        val recognizedNames = mutableListOf<String>()

        try {
            val columnWidth = STANDARD_WIDTH / COLUMN_COUNT
            for (columnIndex in 0 until COLUMN_COUNT) {
                val left = columnIndex * columnWidth
                val right = if (columnIndex == COLUMN_COUNT - 1) STANDARD_WIDTH else (columnIndex + 1) * columnWidth
                val columnRect = Rect(left, INVENTORY_TOP, right, standard.height)
                val columnBitmap = Bitmap.createBitmap(
                    standard,
                    columnRect.left,
                    columnRect.top,
                    columnRect.width(),
                    columnRect.height(),
                )
                val columnResult = try {
                    recognizeColumn(context, columnBitmap, columnRect)
                } finally {
                    columnBitmap.recycle()
                }

                logColumnLines(columnIndex, columnResult.absoluteLines, rawLines)
                val nameCandidates = columnResult.lines
                    .mapNotNull { line -> findNameCandidate(line, columnRect) }
                    .sortedWith(compareBy<NameCandidate> { it.rect.top }.thenBy { it.rect.left })

                RunLogger.i(
                    module = "修为计算",
                    section = "OCR",
                    message = "第${columnIndex + 1}列名称候选：${nameCandidates.joinToString("、") { "${it.material.name}@${it.rect.left},${it.rect.top}" }.ifBlank { "无" }}",
                )

                nameCandidates.forEach { candidate ->
                    if (candidate.material.id in counts) return@forEach
                    val numberResult = recognizeNumberAboveName(context, standard, columnRect, candidate.rect, columnIndex)
                    if (numberResult != null) {
                        counts[candidate.material.id] = numberResult.value
                        recognizedNames += candidate.material.name
                    }
                }
            }
        } finally {
            if (normalized.ownsBitmap) standard.recycle()
        }

        RunLogger.i(
            module = "修为计算",
            section = "OCR",
            message = if (counts.isEmpty()) {
                "配对结果：未得到材料数量"
            } else {
                "配对结果：${counts.entries.joinToString("、") { (id, count) ->
                    "${XiuweiCatalog.materials.firstOrNull { it.id == id }?.name ?: id}=$count"
                }}"
            },
        )
        return XiuweiOcrResult(
            counts = counts,
            rawLines = rawLines,
            recognizedNames = recognizedNames.distinct(),
        )
    }

    private fun recognizeColumn(
        context: Context,
        columnBitmap: Bitmap,
        columnRect: Rect,
    ): ColumnOcrResult {
        val result = PaddleTextRecognizer.recognizeLines(context, columnBitmap)
        val absoluteLines = result.textLines.map { line ->
            line.copy(
                boundingBox = line.boundingBox.translated(columnRect.left, columnRect.top),
                elements = line.elements.map { element ->
                    element.copy(boundingBox = element.boundingBox.translated(columnRect.left, columnRect.top))
                },
            )
        }
        return ColumnOcrResult(result.textLines, absoluteLines)
    }

    private fun findNameCandidate(line: PaddleTextLine, columnRect: Rect): NameCandidate? {
        val text = normalizeText(line.text)
        if (text.isEmpty()) return null
        val best = XiuweiCatalog.materials.mapNotNull { material ->
            bestNameMatch(text, material)?.let { match ->
                NameCandidate(
                    material = material,
                    score = match.third,
                    rect = line.boundingBox.translated(columnRect.left, columnRect.top),
                )
            }
        }.maxByOrNull { it.score }
        return best?.takeIf { it.score >= 0.58f }
    }

    private fun recognizeNumberAboveName(
        context: Context,
        standard: Bitmap,
        columnRect: Rect,
        nameRect: Rect,
        columnIndex: Int,
    ): NumberResult? {
        val numberTop = (nameRect.top - NUMBER_ROI_HEIGHT).coerceAtLeast(columnRect.top)
        val numberBottom = (nameRect.top - NUMBER_ROI_GAP).coerceAtLeast(numberTop + 1)
        if (numberBottom <= numberTop) return null
        val numberRect = Rect(columnRect.left, numberTop, columnRect.right, numberBottom)
        val numberBitmap = Bitmap.createBitmap(
            standard,
            numberRect.left,
            numberRect.top,
            numberRect.width(),
            numberRect.height(),
        )
        return try {
            val result = PaddleTextRecognizer.recognizeLines(context, numberBitmap)
            val raw = result.textLines.joinToString(" | ") { it.text }
                .ifBlank { PaddleTextRecognizer.recognize(context, numberBitmap).text }
            val value = parseNumber(raw)
            RunLogger.i(
                module = "修为计算",
                section = "OCR",
                message = "第${columnIndex + 1}列名称区域=(${nameRect.left},${nameRect.top},${nameRect.right},${nameRect.bottom})，数字区域=(${numberRect.left},${numberRect.top},${numberRect.right},${numberRect.bottom})，原文：${raw.ifBlank { "无" }}，结果=${value?.toString() ?: "失败"}",
            )
            value
        } catch (t: Throwable) {
            RunLogger.e(
                module = "修为计算",
                section = "OCR",
                message = "第${columnIndex + 1}列数字区域识别失败",
                throwable = t,
            )
            null
        } finally {
            numberBitmap.recycle()
        }
    }

    private fun logColumnLines(columnIndex: Int, lines: List<PaddleTextLine>, rawLines: MutableList<String>) {
        if (lines.isEmpty()) {
            RunLogger.i(module = "修为计算", section = "OCR", message = "第${columnIndex + 1}列：未识别到文字行")
            return
        }
        lines.sortedWith(compareBy<PaddleTextLine> { it.boundingBox.top }.thenBy { it.boundingBox.left })
            .forEachIndexed { lineIndex, line ->
                rawLines += line.text
                val rect = line.boundingBox
                RunLogger.i(
                    module = "修为计算",
                    section = "OCR",
                    message = "第${columnIndex + 1}列原文第${lineIndex + 1}行：${line.text}，区域=(${rect.left},${rect.top},${rect.right},${rect.bottom})",
                )
            }
    }

    private fun parseNumber(raw: String): NumberResult? {
        // 先取 OCR 原文中的真实数字，避免把噪声字母 O 转成 0 后抢在有效数量前面。
        val rawMatch = Regex("\\d+").find(raw)
        if (rawMatch != null) {
            return rawMatch.value.toIntOrNull()?.let(::NumberResult)
        }

        // 原文完全没有数字时，才兼容 OCR 将数字误识别为 O/o 的情况。
        val normalized = raw
            .replace('O', '0')
            .replace('o', '0')
            .replace('О', '0')
            .replace('，', ',')
            .replace(" ", "")
            .replace("　", "")
        val match = Regex("\\d+").find(normalized) ?: return null
        return match.value.toIntOrNull()?.let(::NumberResult)
    }

    private fun bestNameMatch(text: String, material: XiuweiMaterial): Triple<Int, Int, Float>? {
        val name = normalizeText(material.name)
        val exact = text.indexOf(name)
        if (exact >= 0) return Triple(exact, exact + name.length, 1f)

        var best: Triple<Int, Int, Float>? = null
        val minLength = max(1, name.length - 1)
        val maxLength = name.length + 1
        for (start in text.indices) {
            for (length in minLength..maxLength) {
                val end = start + length
                if (end > text.length) continue
                val candidate = text.substring(start, end)
                val distance = editDistance(name, candidate)
                val score = 1f - distance.toFloat() / max(name.length, candidate.length).toFloat()
                if (best == null || score > best!!.third) best = Triple(start, end, score)
            }
        }
        return best?.takeIf { it.third >= 0.58f }
    }

    private fun normalizeText(value: String): String = value
        .replace(" ", "")
        .replace("　", "")
        .replace('絹', '绢')
        .replace('絲', '丝')
        .replace('門', '门')
        .replace('風', '风')
        .replace('濁', '浊')
        .replace('靈', '灵')
        .replace('淚', '泪')
        .replace('蘭', '兰')
        .replace('銅', '铜')
        .replace('鏡', '镜')
        .replace('寶', '宝')
        .replace('漢', '汉')

    private data class NormalizedBitmap(
        val bitmap: Bitmap,
        val sourceRect: Rect,
        val scale: Float,
        val ownsBitmap: Boolean,
    )

    private fun normalizeTo1080Width(bitmap: Bitmap): NormalizedBitmap {
        val targetAspect = STANDARD_WIDTH.toFloat() / STANDARD_HEIGHT.toFloat()
        val sourceAspect = bitmap.width.toFloat() / bitmap.height.toFloat()

        // 仅在横向比标准画面更宽时裁掉居中的左右黑边；纵向内容始终完整保留。
        val sourceRect = if (sourceAspect > targetAspect) {
            val contentWidth = (bitmap.height * targetAspect).roundToInt().coerceAtMost(bitmap.width)
            val offsetX = ((bitmap.width - contentWidth) / 2f).roundToInt()
            Rect(offsetX, 0, offsetX + contentWidth, bitmap.height)
        } else {
            Rect(0, 0, bitmap.width, bitmap.height)
        }
        val scale = STANDARD_WIDTH.toFloat() / sourceRect.width().toFloat()
        val targetHeight = (sourceRect.height() * scale).roundToInt().coerceAtLeast(1)
        if (sourceRect == Rect(0, 0, STANDARD_WIDTH, STANDARD_HEIGHT)) {
            return NormalizedBitmap(bitmap, sourceRect, 1f, ownsBitmap = false)
        }
        val content = Bitmap.createBitmap(
            bitmap,
            sourceRect.left,
            sourceRect.top,
            sourceRect.width(),
            sourceRect.height(),
        )
        return try {
            val scaled = Bitmap.createScaledBitmap(content, STANDARD_WIDTH, targetHeight, true)
            NormalizedBitmap(scaled, sourceRect, scale, ownsBitmap = true)
        } finally {
            content.recycle()
        }
    }

    private fun Rect.translated(dx: Int, dy: Int): Rect =
        Rect(left + dx, top + dy, right + dx, bottom + dy)

    private fun editDistance(first: String, second: String): Int {
        var previous = IntArray(second.length + 1) { it }
        var current = IntArray(second.length + 1)
        first.forEachIndexed { row, firstChar ->
            current[0] = row + 1
            second.forEachIndexed { column, secondChar ->
                current[column + 1] = minOf(
                    current[column] + 1,
                    previous[column + 1] + 1,
                    previous[column] + if (firstChar == secondChar) 0 else 1,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[second.length]
    }
}

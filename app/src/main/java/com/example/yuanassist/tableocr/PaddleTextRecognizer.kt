package com.example.yuanassist.tableocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.example.yuanassist.utils.RunLogger
import kotlin.math.max
import kotlin.math.roundToInt

data class PaddleTextResult(
    val text: String,
    val blocks: List<PaddleTextBlock>,
    val detectedLineCount: Int = blocks.size,
)

data class PaddleTextBlock(
    val text: String,
    val boundingBox: Rect,
    val lines: List<PaddleTextLine>
)

data class PaddleTextLine(
    val text: String,
    val boundingBox: Rect,
    val elements: List<PaddleTextElement>
)

data class PaddleTextElement(
    val text: String,
    val boundingBox: Rect
)

object PaddleTextRecognizer {
    private const val MAX_LINE_COUNT = 24
    private const val CROP_PADDING = 4
    private const val OCR_SCALE = 3.0

    @Synchronized
    fun recognize(context: Context, bitmap: Bitmap): PaddleTextResult {
        if (!init(context)) {
            RunLogger.e("Paddle OCR 初始化失败")
            return PaddleTextResult("", emptyList())
        }

        val source = ensureArgb8888(bitmap)
        val detectedBoxes = detectTextLines(source)
        val boxes = detectedBoxes.ifEmpty {
            listOf(Rect(0, 0, source.width, source.height))
        }
        val lines = boxes
            .mapNotNull { rect ->
                val padded = rect.padded(source.width, source.height, CROP_PADDING)
                val text = recognizeCrop(source, padded).trim()
                if (text.isEmpty()) {
                    null
                } else {
                    PaddleTextLine(
                        text = text,
                        boundingBox = padded,
                        elements = buildElements(text, padded)
                    )
                }
            }

        if (source !== bitmap) source.recycle()

        val blocks = lines.map { line ->
            PaddleTextBlock(
                text = line.text,
                boundingBox = line.boundingBox,
                lines = listOf(line)
            )
        }
        return PaddleTextResult(
            text = lines.joinToString("\n") { it.text },
            blocks = blocks,
            detectedLineCount = detectedBoxes.size,
        )
    }

    private fun init(context: Context): Boolean {
        val recModelPath = AssetCopier.getModelPath(context)
        val detModelPath = AssetCopier.getDetModelPath(context)
        val labelPath = AssetCopier.getLabelPath(context)
        return PaddleOcrNative.init(recModelPath, labelPath) &&
            PaddleOcrNative.initDetector(detModelPath)
    }

    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    private fun recognizeCrop(source: Bitmap, rect: Rect): String {
        if (rect.width() <= 0 || rect.height() <= 0) return ""
        val crop = Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
        val scaled = scaleForRecognition(crop)
        if (scaled !== crop) crop.recycle()
        return try {
            PaddleOcrNative.recognize(scaled)
        } finally {
            scaled.recycle()
        }
    }

    private fun scaleForRecognition(bitmap: Bitmap): Bitmap {
        val targetWidth = max(1, (bitmap.width * OCR_SCALE).roundToInt())
        val targetHeight = max(1, (bitmap.height * OCR_SCALE).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun detectTextLines(bitmap: Bitmap): List<Rect> {
        return try {
            PaddleOcrNative.detect(bitmap)
                .toList()
                .chunked(5)
                .mapNotNull { values ->
                    if (values.size < 5) return@mapNotNull null
                    val rect = Rect(
                        values[0].coerceIn(0, bitmap.width),
                        values[1].coerceIn(0, bitmap.height),
                        values[2].coerceIn(0, bitmap.width),
                        values[3].coerceIn(0, bitmap.height)
                    )
                    if (rect.width() <= 0 || rect.height() <= 0) null else rect
                }
                .mergeLineRects()
                .sortedWith(compareBy<Rect> { it.top }.thenBy { it.left })
                .take(MAX_LINE_COUNT)
        } catch (t: Throwable) {
            RunLogger.e("Paddle OCR 文字区域检测失败", t)
            emptyList()
        }
    }

    private fun List<Rect>.mergeLineRects(): List<Rect> {
        if (isEmpty()) return emptyList()
        val sorted = sortedBy { it.centerY() }
        val merged = mutableListOf<Rect>()
        var current = Rect(sorted.first())
        var currentCenter = current.centerY()

        for (rect in sorted.drop(1)) {
            val tolerance = max(current.height(), rect.height()) * 0.65f
            if (kotlin.math.abs(rect.centerY() - currentCenter) <= tolerance) {
                current.union(rect)
                currentCenter = current.centerY()
            } else {
                merged += current
                current = Rect(rect)
                currentCenter = current.centerY()
            }
        }
        merged += current
        return merged
    }

    private fun buildElements(text: String, lineRect: Rect): List<PaddleTextElement> {
        val chars = text.filterNot { it.isWhitespace() }
        if (chars.isEmpty()) return emptyList()
        val charWidth = lineRect.width().toFloat() / chars.length.toFloat()
        return chars.mapIndexed { index, ch ->
            val left = (lineRect.left + charWidth * index).roundToInt()
            val right = (lineRect.left + charWidth * (index + 1)).roundToInt()
            PaddleTextElement(
                text = ch.toString(),
                boundingBox = Rect(left, lineRect.top, right.coerceAtLeast(left + 1), lineRect.bottom)
            )
        }
    }

    private fun Rect.padded(width: Int, height: Int, padding: Int): Rect =
        Rect(
            (left - padding).coerceAtLeast(0),
            (top - padding).coerceAtLeast(0),
            (right + padding).coerceAtMost(width),
            (bottom + padding).coerceAtMost(height)
        )
}

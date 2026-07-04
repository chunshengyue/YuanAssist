package com.example.yuanassist.tableocr

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object OcrScheduler {

    private const val DEBUG_TAG = "TableOcrV2"

    private var ocrFunc: ((Mat) -> String)? = null
    private val analyzer = CellLayoutAnalyzer()

    fun setOcrFunction(func: (Mat) -> String) {
        ocrFunc = func
    }

    fun ocrRoundCell(tableImage: Mat, cell: CellBox): String {
        android.util.Log.d("Scheduler", "ocrRoundCell cell[${cell.row},${cell.col}] xy=${cell.x},${cell.y} wh=${cell.w}x${cell.h}")
        val crop = cropCell(tableImage, cell)
        val prepared = prepareCrop(crop)
        crop.release()
        return ocrFunc?.invoke(prepared)?.also { prepared.release() } ?: ""
    }

    fun ocrActionCell(tableImage: Mat, cell: CellBox): String {
        android.util.Log.d("Scheduler", "ocrActionCell cell[${cell.row},${cell.col}] xy=${cell.x},${cell.y} wh=${cell.w}x${cell.h}")
        val crop = cropCell(tableImage, cell)
        val lineCrops = analyzer.splitLines(crop)
        if (lineCrops.isNotEmpty()) {
            val parts = mutableListOf<String>()
            var allLinesUsable = true
            for ((lineIndex, lc) in lineCrops.withIndex()) {
                val text = assembleActionText(lc, "cell[${cell.row},${cell.col}] line=$lineIndex")
                lc.release()
                if (text.isNotEmpty()) {
                    parts.add(text)
                } else {
                    allLinesUsable = false
                }
            }
            if (allLinesUsable) {
                android.util.Log.d(DEBUG_TAG, "cell[${cell.row},${cell.col}] splitResult='${parts.joinToString("")}'")
                crop.release()
                return parts.joinToString("")
            }
        }
        val result = assembleActionText(crop, "cell[${cell.row},${cell.col}] full")
        android.util.Log.d(DEBUG_TAG, "cell[${cell.row},${cell.col}] fullResult='$result'")
        crop.release()
        return result
    }

    fun ocrCells(tableImage: Mat, boxes: List<CellBox>): Map<Pair<Int, Int>, String> {
        val results = mutableMapOf<Pair<Int, Int>, String>()
        for (cell in boxes) {
            val key = cell.row to cell.col
            results[key] = if (cell.col == 0) {
                ocrRoundCell(tableImage, cell)
            } else {
                ocrActionCell(tableImage, cell)
            }
        }
        return results
    }

    private fun cropCell(image: Mat, cell: CellBox, padding: Int = 2): Mat {
        val height = image.rows()
        val width = image.cols()
        val left = maxOf(0, cell.x + padding)
        val top = maxOf(0, cell.y + padding)
        val right = minOf(width, cell.x + cell.w - padding)
        val bottom = minOf(height, cell.y + cell.h - padding)
        if (right <= left || bottom <= top) {
            return Mat(image, Rect(cell.x, cell.y, cell.w, cell.h)).clone()
        }
        return Mat(image, Rect(left, top, right - left, bottom - top)).clone()
    }

    private fun prepareCrop(crop: Mat, scale: Int = 4): Mat {
        android.util.Log.d("Scheduler", "prepareCrop input type=${crop.type()} depth=${crop.depth()} ch=${crop.channels()} ${crop.cols()}x${crop.rows()} empty=${crop.empty()}")
        if (crop.empty()) return crop.clone()
        val resized = Mat()
        Imgproc.resize(crop, resized, Size(0.0, 0.0), scale.toDouble(), scale.toDouble(), Imgproc.INTER_CUBIC)
        val result = Mat()
        Core.copyMakeBorder(resized, result, 8, 8, 8, 8, Core.BORDER_CONSTANT, org.opencv.core.Scalar(255.0, 255.0, 255.0))
        android.util.Log.d("Scheduler", "prepareCrop output type=${result.type()} depth=${result.depth()} ch=${result.channels()} ${result.cols()}x${result.rows()}")
        resized.release()
        return result
    }

    private fun assembleActionText(crop: Mat, debugLabel: String): String {
        val rawText = recognizeCropText(crop)
        android.util.Log.d(DEBUG_TAG, "$debugLabel rawCropOcr='$rawText' size=${crop.cols()}x${crop.rows()}")
        if (isNonActionText(rawText)) return ""

        val binary = ActionComponentAnalyzer.toInvertedBinary(crop)
        val components = ActionComponentAnalyzer.filterBorderComponents(
            binary,
            ActionComponentAnalyzer.findComponents(binary)
        )
        val readingComponents = splitTallComponentsByAnchorRows(components, debugLabel)
        val lines = dropTinyComponentLines(ActionComponentAnalyzer.groupComponentsForReading(readingComponents))
        android.util.Log.d(
            DEBUG_TAG,
            "$debugLabel components=${components.joinToString { rectLabel(it) }} " +
                "readingComponents=${readingComponents.joinToString { rectLabel(it) }} lines=${lines.size}"
        )
        if (lines.isEmpty()) {
            binary.release()
            return ""
        }

        val parts = mutableListOf<String>()
        for ((lineIndex, line) in lines.withIndex()) {
            parts.add(transcribeComponentLine(crop, binary, line.sortedBy { it.x }, "$debugLabel componentLine=$lineIndex"))
        }
        binary.release()

        val text = parts.joinToString("")
        android.util.Log.d(DEBUG_TAG, "$debugLabel assembled='$text'")
        if (text.isEmpty()) return ""
        return text
    }

    private fun splitTallComponentsByAnchorRows(components: List<Rect>, debugLabel: String): List<Rect> {
        if (components.size < 4) return components

        val medianHeight = components.map { it.height }.sorted()[components.size / 2].coerceAtLeast(1)
        val tallComponents = components.filter { it.height * 100 >= medianHeight * 155 }
        if (tallComponents.isEmpty()) return components

        val anchorComponents = components.filter { it !in tallComponents }
        val anchorLines = ActionComponentAnalyzer.groupComponentsForReading(anchorComponents)
        if (anchorLines.size < 2) return components

        val rowRanges = anchorLines
            .map { line -> line.minOf { it.y } to line.maxOf { it.y + it.height } }
            .sortedBy { it.first + it.second }
        if (rowRanges.size < 2) return components

        val result = mutableListOf<Rect>()
        for (component in components) {
            if (component !in tallComponents) {
                result.add(component)
                continue
            }

            val splitParts = mutableListOf<Rect>()
            for ((rowIndex, range) in rowRanges.withIndex()) {
                val previous = rowRanges.getOrNull(rowIndex - 1)
                val next = rowRanges.getOrNull(rowIndex + 1)
                val rowTopBoundary = previous?.let { (it.second + range.first) / 2 } ?: component.y
                val rowBottomBoundary = next?.let { (range.second + it.first) / 2 } ?: component.y + component.height
                val top = maxOf(component.y, rowTopBoundary)
                val bottom = minOf(component.y + component.height, rowBottomBoundary)
                if (bottom - top >= 8) {
                    splitParts.add(Rect(component.x, top, component.width, bottom - top))
                }
            }

            if (splitParts.size >= 2) {
                android.util.Log.d(
                    DEBUG_TAG,
                    "$debugLabel splitTall rect=${rectLabel(component)} -> ${splitParts.joinToString { rectLabel(it) }}"
                )
                result.addAll(splitParts)
            } else {
                result.add(component)
            }
        }
        return result
    }

    private fun dropTinyComponentLines(lines: List<List<Rect>>): List<List<Rect>> {
        if (lines.size <= 1) return lines
        val maxLineHeight = lines.maxOf { line -> line.maxOf { it.height } }.coerceAtLeast(1)
        return lines.filter { line ->
            val lineHeight = line.maxOf { it.height }
            lineHeight >= 12 || lineHeight * 100 >= maxLineHeight * 60
        }
    }

    private fun transcribeComponentLine(
        crop: Mat,
        binary: Mat,
        lineComponents: List<Rect>,
        debugLabel: String
    ): String {
        val parts = mutableListOf<String>()
        var previousCircle: Rect? = null
        var index = 0
        while (index < lineComponents.size) {
            val component = lineComponents[index]
            val shapeArrow = ActionComponentAnalyzer.classifyActionArrow(binary, component, lineComponents)
            val shapeCircle = ActionComponentAnalyzer.classifyCircle(binary, component)
            val shapeZero = ActionComponentAnalyzer.looksLikeZero(binary, component)
            val ocrToken = recognizeComponentToken(crop, component)
            val token = chooseActionToken(ocrToken, shapeArrow, shapeCircle, shapeZero, component)
            android.util.Log.d(
                DEBUG_TAG,
                "$debugLabel index=$index rect=${rectLabel(component)} ocrToken='$ocrToken' " +
                    "shapeArrow='$shapeArrow' shapeCircle='$shapeCircle' shapeZero=$shapeZero " +
                    "looksLikeOne=${ActionComponentAnalyzer.looksLikeOneDigit(component)} token='$token'"
            )
            val circle = previousCircle
            if (token == "0" && circle != null && isCircleRightFragment(circle, component)) {
                android.util.Log.d(
                    DEBUG_TAG,
                    "$debugLabel index=$index skipCircleZeroFragment rect=${rectLabel(component)} circle=${rectLabel(circle)}"
                )
                index++
                continue
            }
            if (token.isNotEmpty()) {
                parts.add(token)
                previousCircle = if (token == "圈") component else null
            } else if (looksLikeVisibleCharacter(component, lineComponents)) {
                val relaxedArrow = ActionComponentAnalyzer.classifyArrow(binary, component, relaxedShape = true)
                if (relaxedArrow.isNotEmpty()) {
                    android.util.Log.d(
                        DEBUG_TAG,
                        "$debugLabel index=$index relaxedArrow='$relaxedArrow' rect=${rectLabel(component)}"
                    )
                    parts.add(relaxedArrow)
                    previousCircle = null
                    index++
                    continue
                }
                if (circle == null || !isCircleRightFragment(circle, component)) {
                    parts.add("?")
                    previousCircle = null
                }
            }
            index++
        }
        android.util.Log.d(DEBUG_TAG, "$debugLabel text='${parts.joinToString("")}'")
        return parts.joinToString("")
    }

    private fun rectLabel(rect: Rect): String =
        "(${rect.x},${rect.y},${rect.width}x${rect.height})"

    private fun isCircleRightFragment(circle: Rect, candidate: Rect): Boolean {
        val gap = candidate.x - (circle.x + circle.width)
        if (candidate.x >= circle.x &&
            candidate.y >= circle.y &&
            candidate.x + candidate.width <= circle.x + circle.width &&
            candidate.y + candidate.height <= circle.y + circle.height
        ) {
            return true
        }
        if (candidate.x < circle.x + circle.width / 2) {
            return false
        }
        if (gap > maxOf(3, circle.width / 3)) {
            return false
        }
        val overlapTop = maxOf(circle.y, candidate.y)
        val overlapBottom = minOf(circle.y + circle.height, candidate.y + candidate.height)
        val overlap = overlapBottom - overlapTop
        if (overlap <= 0) {
            return false
        }
        val minHeight = minOf(circle.height, candidate.height)
        val overlapPercent = overlap * 100 / maxOf(1, minHeight)
        val widthPercent = candidate.width * 100 / maxOf(1, circle.width)
        val heightPercent = candidate.height * 100 / maxOf(1, circle.height)
        if (overlapPercent < 60) {
            return false
        }
        if (widthPercent > 70) {
            return false
        }
        if (heightPercent < 45) {
            return false
        }
        return true
    }

    private fun looksLikeVisibleCharacter(component: Rect, lineComponents: List<Rect>): Boolean {
        if (component.width < 4 || component.height < 8) return false
        if (lineComponents.isEmpty()) return true
        val medianHeight = lineComponents.map { it.height }.sorted()[lineComponents.size / 2]
        return component.height * 100 >= medianHeight * 70
    }

    private fun recognizeCropText(crop: Mat): String {
        val prepared = prepareCrop(crop)
        return ocrFunc?.invoke(prepared)?.also { prepared.release() } ?: ""
    }

    private fun isNonActionText(text: String): Boolean {
        val compact = text.replace(" ", "").replace("\n", "").replace("\t", "")
        if (compact.isEmpty()) return false
        val hasActionChar = compact.any { it == 'A' || it == '↑' || it == '↓' || it == '圈' || it.isDigit() }
        val hasChinese = compact.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        return hasChinese && !hasActionChar
    }

    private fun chooseActionToken(
        ocrToken: String,
        shapeArrow: String,
        shapeCircle: String,
        shapeZero: Boolean,
        component: Rect
    ): String {
        val compact = ocrToken.replace(" ", "").replace("\n", "").replace("\t", "")

        if (shapeArrow.isNotEmpty()) {
            return shapeArrow
        }
        if (shapeCircle.isNotEmpty()) return shapeCircle
        if ("圈" in compact) return "圈"

        val digit = ActionParser.extractDigit(compact)
        if (digit.isNotEmpty()) {
            val suffix = compact.filter { it == 'A' || it == '圈' }
            return correctDigitByShape(digit, component) + suffix
        }
        if ("A" in compact) return compact.filter { it == 'A' }.ifEmpty { "A" }
        if (ActionComponentAnalyzer.looksLikeOneDigit(component)) {
            return "1"
        }
        if (shapeZero) return "0"
        return compact.filter { it == 'A' || it == '↑' || it == '↓' || it == '圈' }
    }

    private fun recognizeComponentToken(crop: Mat, component: Rect): String {
        val componentCrop = cropComponents(crop, listOf(component))
        val prepared = prepareCrop(componentCrop, scale = 12)
        val text = ocrFunc?.invoke(prepared) ?: ""
        prepared.release()
        componentCrop.release()

        return ActionComponentAnalyzer.normalizeOcrToken(text, component)
    }

    private fun correctDigitByShape(digit: String, component: Rect): String {
        if (digit == "7" && ActionComponentAnalyzer.looksLikeOneDigit(component)) return "1"
        return digit
    }

    private fun cropComponents(crop: Mat, components: List<Rect>, padding: Int = 2): Mat {
        if (components.isEmpty()) return crop.clone()
        val width = crop.cols()
        val height = crop.rows()
        val left = maxOf(0, components.minOf { it.x } - padding)
        val top = maxOf(0, components.minOf { it.y } - padding)
        val right = minOf(width, components.maxOf { it.x + it.width } + padding)
        val bottom = minOf(height, components.maxOf { it.y + it.height } + padding)
        return Mat(crop, Rect(left, top, right - left, bottom - top)).clone()
    }
}

package com.example.yuanassist.tableocr

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object OcrScheduler {

    private var ocrFunc: ((Mat) -> String)? = null
    private val analyzer = CellLayoutAnalyzer()
    
    private val canonicalAction = Regex("^(?:10|[1-9])A$")
    private val singleArrowAction = Regex("^(?:10|[1-9])[↑↓]$")
    private val repeatedSingleAAction = Regex("^(?:10|[1-9])A{2,}$")
    private val suffixOnlyAction = Regex("^[A↑↓圈]+$")
    private val repeatedActionTokenSequence = Regex("^((?:10|[1-9])[A↑↓圈]+)\\1+$")

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
        val prepared = prepareCrop(crop)
        val fullText = ocrFunc?.invoke(prepared) ?: ""
        prepared.release()

        val lineCrops = analyzer.splitLines(crop)
        if (lineCrops.isNotEmpty()) {
            val parts = mutableListOf<String>()
            var allLinesUsable = true
            for (lc in lineCrops) {
                val lp = prepareCrop(lc)
                val text = ocrFunc?.invoke(lp) ?: ""
                lp.release()
                val result = ActionParser.parse(text)
                val assembled = assembleActionText(text, lc)
                lc.release()
                val chosen = chooseComponentResult(result.text + result.fragment, assembled)
                if (chosen.isNotEmpty()) {
                    parts.add(chosen)
                } else {
                    allLinesUsable = false
                }
            }
            if (allLinesUsable) {
                val lineResult = parts.joinToString("")
                if (lineResult.isNotEmpty() && !repeatedActionTokenSequence.matches(lineResult)) {
                    crop.release()
                    return lineResult
                }
            }
        }

        val fullResult = ActionParser.parse(fullText)
        if (fullResult.isComplete && fullResult.text.isNotEmpty() &&
            !singleArrowAction.matches(fullResult.text) &&
            !canonicalAction.matches(fullResult.text) &&
            !repeatedSingleAAction.matches(fullResult.text) &&
            !suffixOnlyAction.matches(fullResult.text) &&
            !repeatedActionTokenSequence.matches(fullResult.text)) {
            if (ActionParser.looksComplex(fullResult.text)) {
                val assembled = assembleActionText(fullText, crop)
                val chosen = chooseComponentResult(fullResult.text, assembled)
                crop.release()
                return chosen
            }
            crop.release()
            return fullResult.text
        }

        if (ActionParser.looksComplex(fullText) && !repeatedActionTokenSequence.matches(fullResult.text)) {
            crop.release()
            return when {
                fullResult.text.isNotEmpty() -> fullResult.text + fullResult.fragment
                else -> fullText
            }
        }

        val assembled = assembleActionText(fullText, crop)
        crop.release()
        return assembled
    }

    private fun chooseComponentResult(ocrText: String, componentText: String): String {
        if (componentText.isEmpty()) return ocrText
        if (ocrText.isEmpty()) return componentText

        val parsedComponent = ActionParser.parse(componentText)
        if (parsedComponent.text.isEmpty()) return ocrText

        val normalizedComponent = parsedComponent.text + parsedComponent.fragment
        if (suffixOnlyAction.matches(ocrText) && !suffixOnlyAction.matches(normalizedComponent)) {
            return normalizedComponent
        }
        if (repeatedActionTokenSequence.matches(ocrText) &&
            !repeatedActionTokenSequence.matches(normalizedComponent)) {
            return normalizedComponent
        }

        val ocrDigitCount = actionDigitCount(ocrText)
        val componentDigitCount = actionDigitCount(normalizedComponent)
        return if (componentDigitCount > ocrDigitCount &&
            parsedComponent.isComplete &&
            !repeatedActionTokenSequence.matches(normalizedComponent)) {
            normalizedComponent
        } else {
            ocrText
        }
    }

    private fun actionDigitCount(text: String): Int {
        return Regex("10|[1-9]").findAll(text).count()
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

    fun recognizeCellBitmap(bitmap: Bitmap): String {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)
        val prepared = prepareCrop(mat)
        mat.release()
        return ocrFunc?.invoke(prepared)?.also { prepared.release() } ?: ""
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

    private fun assembleActionText(fullText: String, crop: Mat): String {
        val binary = toInvertedBinary(crop)
        val components = findComponents(binary)
        val filtered = filterBorderComponents(binary, components)
        binary.release()

        if (filtered.size < 2) {
            val parsedFullText = ActionParser.parse(fullText)
            return assembleSuffixOnlyComponents(crop, filtered).ifEmpty {
                parsedFullText.text.ifEmpty { fullText }
            }
        }
        if (canonicalAction.matches(fullText) && filtered.size <= 2) {
            return fullText
        }

        val ordered = orderComponentsForReading(filtered)
        val digitComp = listOf(ordered[0])
        val suffixComps = ordered.drop(1)
        val hasComplexComponents = ordered.size > 2

        val digitCrop = cropComponents(crop, digitComp)
        val digitPrepared = prepareCrop(digitCrop, scale = 8)
        val digitText = ocrFunc?.invoke(digitPrepared) ?: ""
        digitPrepared.release()
        digitCrop.release()

        val componentDigitText = recognizeComponentToken(crop, digitComp[0])
        val rawDigit = ActionParser.extractDigit(digitText).ifEmpty {
            ActionParser.extractDigit(componentDigitText)
        }.ifEmpty {
            if (looksLikeOneDigitComponent(digitComp[0])) "1" else ""
        }.ifEmpty {
            ActionParser.extractDigit(fullText)
        }
        val digit = correctDigitByShape(rawDigit, digitComp[0])
        if (digit.isEmpty()) {
            return assembleSuffixOnlyComponents(crop, ordered).ifEmpty { fullText }
        }

        val suffixCrop = cropComponents(crop, suffixComps)
        val suffixPrepared = prepareCrop(suffixCrop, scale = 8)
        val suffixText = ocrFunc?.invoke(suffixPrepared) ?: ""
        suffixPrepared.release()
        suffixCrop.release()

        val componentText = assembleComponentSequence(crop, ordered, digit)
        if (componentText.isNotEmpty()) {
            return componentText
        }

        val suffixResult = ActionParser.parse("$digit$suffixText")
        if (Regex("\\d").containsMatchIn(suffixText) &&
            suffixResult.isComplete &&
            suffixResult.text.isNotEmpty() &&
            !repeatedActionTokenSequence.matches(suffixResult.text)) {
            return suffixResult.text
        }

        val arrowBinary = toInvertedBinary(crop)
        val arrows = suffixComps.mapNotNull { comp ->
            classifyArrowComponent(arrowBinary, comp).ifEmpty { null }
        }.joinToString("")
        arrowBinary.release()

        val result = when {
            arrows.isNotEmpty() && "A" in suffixText -> "${digit}${arrows}A"
            arrows.isNotEmpty() -> "$digit$arrows"
            "A" in suffixText -> "${digit}A"
            suffixText.isNotEmpty() && !hasComplexComponents -> "$digit$suffixText"
            repeatedActionTokenSequence.matches(fullText) -> ""
            else -> fullText
        }
        return result
    }

    private fun assembleSuffixOnlyComponents(crop: Mat, components: List<Rect>): String {
        if (components.isEmpty()) return ""

        val binary = toInvertedBinary(crop)
        val parts = mutableListOf<String>()
        var complete = true
        for (comp in orderComponentsForReading(components)) {
            val arrow = classifyArrowComponent(binary, comp, relaxedShape = true)
            if (arrow.isNotEmpty()) {
                parts.add(arrow)
                continue
            }
            val token = recognizeComponentToken(crop, comp)
            if (token.isEmpty()) {
                complete = false
                break
            }
            parts.add(token)
        }
        binary.release()

        if (!complete) return ""
        val parsed = ActionParser.parse(parts.joinToString(""))
        return if (parsed.isComplete && parsed.text.isNotEmpty()) parsed.text else ""
    }

    private fun assembleComponentSequence(crop: Mat, components: List<Rect>, firstDigit: String): String {
        if (components.size < 2) return ""

        val binary = toInvertedBinary(crop)
        val parts = mutableListOf(firstDigit)
        var complete = true

        val suffixComponents = components.drop(1)
        var index = 0
        while (index < suffixComponents.size) {
            val comp = suffixComponents[index]
            val arrow = classifyArrowComponent(binary, comp, relaxedShape = true)
            if (arrow.isNotEmpty()) {
                val previousIsSuffix = parts.lastOrNull()?.all { it == 'A' || it == '↑' || it == '↓' || it == '圈' } == true
                if (previousIsSuffix && index + 1 < suffixComponents.size) {
                    val token = recognizeComponentToken(crop, comp)
                    val nextToken = recognizeComponentToken(crop, suffixComponents[index + 1])
                    if (token.isNotEmpty() && token.any { it.isDigit() }) {
                        parts.add(token)
                        index++
                        continue
                    }
                    if (arrow == "↑" && nextToken == "0") {
                        parts.add("10")
                        index += 2
                        continue
                    }
                    if (arrow == "↑" && classifyArrowComponent(binary, suffixComponents[index + 1], relaxedShape = true).isNotEmpty()) {
                        parts.add("9")
                        index++
                        continue
                    }
                }
                parts.add(arrow)
                index++
                continue
            }

            val token = recognizeComponentToken(crop, comp)
            if (token.isEmpty()) {
                complete = false
                break
            }
            parts.add(token)
            index++
        }
        binary.release()

        if (!complete || parts.size <= 1) return ""
        val text = parts.joinToString("")
        val parsed = ActionParser.parse(text)
        return if (parsed.text.isNotEmpty()) parsed.text + parsed.fragment else ""
    }

    private fun recognizeComponentToken(crop: Mat, component: Rect): String {
        val componentCrop = cropComponents(crop, listOf(component))
        val prepared = prepareCrop(componentCrop, scale = 12)
        val text = ocrFunc?.invoke(prepared) ?: ""
        prepared.release()
        componentCrop.release()

        return normalizeComponentToken(text, component)
    }

    private fun correctDigitByShape(digit: String, component: Rect): String {
        if (digit == "7" && looksLikeOneDigitComponent(component)) return "1"
        return digit
    }

    private fun looksLikeOneDigitComponent(component: Rect): Boolean {
        return component.width * 100 <= component.height * 50
    }

    private fun normalizeComponentToken(text: String, component: Rect): String {
        val compact = text.replace(" ", "").replace("\n", "").replace("\t", "").replace("/", "")
        if (compact.isEmpty()) return ""

        val result = StringBuilder()
        for (ch in compact) {
            when (ch) {
                'I', 'l' -> result.append('1')
                'O' -> result.append('0')
                'A' -> result.append('A')
                '圈' -> result.append('圈')
                in '0'..'9' -> result.append(ch)
            }
        }
        val token = result.toString()
        if (token == "A" && component.width * 100 >= component.height * 140) {
            val estimatedCount = maxOf(2, (component.width * 100 + component.height * 42) / (component.height * 85))
            return "A".repeat(estimatedCount)
        }
        return token
    }

    private fun orderComponentsForReading(components: List<Rect>): List<Rect> {
        if (components.size <= 1) return components

        val lines = mutableListOf<MutableList<Rect>>()
        val sortedByY = components.sortedWith(compareBy<Rect> { it.y + it.height / 2 }.thenBy { it.x })
        for (component in sortedByY) {
            val centerY = component.y + component.height / 2
            val threshold = maxOf(6, component.height / 2)
            val line = lines.firstOrNull { existing ->
                val avgCenter = existing.sumOf { it.y + it.height / 2 } / existing.size
                kotlin.math.abs(avgCenter - centerY) <= threshold
            }
            if (line != null) {
                line.add(component)
            } else {
                lines.add(mutableListOf(component))
            }
        }

        return lines
            .sortedBy { line -> line.sumOf { it.y + it.height / 2 } / line.size }
            .flatMap { line -> line.sortedBy { it.x } }
    }

    private fun toInvertedBinary(crop: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(crop, gray, Imgproc.COLOR_BGR2GRAY)
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        gray.release()
        Core.bitwise_not(binary, binary)
        return binary
    }

    private fun findComponents(binary: Mat): List<Rect> {
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val numLabels = Imgproc.connectedComponentsWithStats(binary, labels, stats, centroids, 8, CvType.CV_32S)
        labels.release()
        centroids.release()

        val components = mutableListOf<Rect>()
        for (i in 1 until numLabels) {
            val data = IntArray(5)
            stats.get(i, 0, data)
            val x = data[Imgproc.CC_STAT_LEFT]
            val y = data[Imgproc.CC_STAT_TOP]
            val w = data[Imgproc.CC_STAT_WIDTH]
            val h = data[Imgproc.CC_STAT_HEIGHT]
            val area = data[Imgproc.CC_STAT_AREA]
            if (area >= 3) {
                components.add(Rect(x, y, w, h))
            }
        }
        stats.release()
        components.sortBy { it.x }
        return components
    }

    private fun filterBorderComponents(binary: Mat, components: List<Rect>): List<Rect> {
        val width = binary.cols()
        val height = binary.rows()
        return components.filter { comp ->
            val touchesBorder = comp.x == 0 || comp.y == 0 ||
                comp.x + comp.width >= width || comp.y + comp.height >= height
            val looksLikeGridline = comp.width <= 2 || comp.height <= 2 ||
                comp.width >= maxOf(8, (width * 0.3).toInt()) ||
                comp.height >= maxOf(8, (height * 0.3).toInt())
            !(touchesBorder && (looksLikeGridline || components.size > 1))
        }
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

    private fun classifyArrowComponent(binary: Mat, component: Rect, relaxedShape: Boolean = false): String {
        if (!looksLikeArrowComponent(component, relaxedShape)) return ""

        val mask = Mat(binary, component)
        if (mask.empty()) {
            mask.release()
            return ""
        }

        val rowSums = IntArray(component.height)
        val rowData = ByteArray(component.width)
        for (y in 0 until component.height) {
            mask.get(y, 0, rowData)
            rowSums[y] = rowData.sumOf { (it.toInt() and 0xFF) }
        }

        if (rowSums.size < 3) {
            mask.release()
            return ""
        }
        val window = maxOf(1, rowSums.size / 3)
        val topMass = rowSums.take(window).sum()
        val bottomMass = rowSums.takeLast(window).sum()
        mask.release()

        return when {
            topMass >= bottomMass + 2 * 255 -> "↑"
            bottomMass >= topMass + 2 * 255 -> "↓"
            else -> ""
        }
    }

    private fun looksLikeArrowComponent(component: Rect, relaxedShape: Boolean = false): Boolean {
        val maxWidthPercent = if (relaxedShape) 70 else 65
        return component.width * 100 <= component.height * maxWidthPercent
    }
}

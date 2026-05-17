package com.example.yuanassist.tableocr

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

object ActionComponentAnalyzer {

    fun toInvertedBinary(crop: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(crop, gray, Imgproc.COLOR_BGR2GRAY)
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        gray.release()
        Core.bitwise_not(binary, binary)
        return binary
    }

    fun findComponents(binary: Mat): List<Rect> {
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

    fun filterBorderComponents(binary: Mat, components: List<Rect>): List<Rect> {
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

    fun groupComponentsForReading(components: List<Rect>): List<List<Rect>> {
        return groupLinesForReading(components)
    }

    private fun groupLinesForReading(components: List<Rect>): List<List<Rect>> {
        if (components.size <= 1) return if (components.isEmpty()) emptyList() else listOf(components)

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
            .map { it.toList() }
    }

    fun classifyArrow(binary: Mat, component: Rect, relaxedShape: Boolean = false): String {
        if (!looksLikeArrow(component, relaxedShape)) return ""

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

    fun looksLikeArrow(component: Rect, relaxedShape: Boolean = false): Boolean {
        val maxWidthPercent = if (relaxedShape) 70 else 65
        return component.width * 100 <= component.height * maxWidthPercent
    }

    fun looksLikeOneDigit(component: Rect): Boolean {
        return component.width * 100 <= component.height * 50
    }

    fun classifyActionArrow(binary: Mat, component: Rect, components: List<Rect>): String {
        if (!looksLikeActionArrowCandidate(component, components)) return ""
        if (classifyCircle(binary, component).isNotEmpty()) return ""
        if (looksLikeZero(binary, component)) return ""

        val mask = Mat(binary, component)
        if (mask.empty()) {
            mask.release()
            return ""
        }

        val rowMass = IntArray(component.height)
        val rowSpan = IntArray(component.height)
        val rowCenterX = DoubleArray(component.height) { -1.0 }
        val leftMass = IntArray(component.height)
        val rightMass = IntArray(component.height)
        val rowData = ByteArray(component.width)
        for (y in 0 until component.height) {
            mask.get(y, 0, rowData)
            var first = -1
            var last = -1
            var sum = 0
            for (x in 0 until component.width) {
                val value = rowData[x].toInt() and 0xFF
                sum += value
                if (value > 0) {
                    if (first < 0) first = x
                    last = x
                }
            }
            rowMass[y] = sum
            if (first >= 0) {
                rowSpan[y] = last - first + 1
                rowCenterX[y] = (first + last) / 2.0
            }
        }

        if (rowMass.size < 6) {
            mask.release()
            return ""
        }

        val middleTop = component.height / 4
        val middleBottom = component.height - middleTop
        val shaftRows = (middleTop until middleBottom).filter { rowMass[it] > 0 }
        val shaftContinuity = shaftRows.size.toDouble() / maxOf(1, middleBottom - middleTop).toDouble()
        if (shaftContinuity < 0.58) {
            mask.release()
            return ""
        }

        val shaftWidth = averageIntOrZero(shaftRows.map { rowSpan[it] }.filter { it > 0 })
        if (shaftWidth <= 0.0 || shaftWidth * 100 > component.width * 72) {
            mask.release()
            return ""
        }

        val narrowRows = shaftRows.filter { rowSpan[it] > 0 && rowSpan[it] <= shaftWidth * 1.55 }
        if (narrowRows.size < maxOf(3, shaftRows.size / 2)) {
            mask.release()
            return ""
        }
        val axisX = averageDoubleOrZero(narrowRows.map { rowCenterX[it] })
        val axisStdDev = stdDev(narrowRows.map { rowCenterX[it] }, axisX)
        if (axisStdDev > 2.25) {
            mask.release()
            return ""
        }
        val componentCenterX = (component.width - 1) / 2.0
        val centerTolerance = maxOf(1.0, component.width * 0.14)
        if (kotlin.math.abs(axisX - componentCenterX) > centerTolerance) {
            mask.release()
            return ""
        }
        val shaftEdgeSampleSize = maxOf(2, narrowRows.size / 4)
        val upperShaftAxis = averageDoubleOrZero(narrowRows.take(shaftEdgeSampleSize).map { rowCenterX[it] })
        val lowerShaftAxis = averageDoubleOrZero(narrowRows.takeLast(shaftEdgeSampleSize).map { rowCenterX[it] })
        val verticalDriftTolerance = maxOf(1.25, component.width * 0.16)
        if (kotlin.math.abs(upperShaftAxis - lowerShaftAxis) > verticalDriftTolerance) {
            mask.release()
            return ""
        }

        for (y in 0 until component.height) {
            mask.get(y, 0, rowData)
            var left = 0
            var right = 0
            for (x in 0 until component.width) {
                val value = rowData[x].toInt() and 0xFF
                if (value == 0) continue
                if (x < componentCenterX) {
                    left += value
                } else if (x > componentCenterX) {
                    right += value
                }
            }
            leftMass[y] = left
            rightMass[y] = right
        }
        mask.release()

        val totalMass = rowMass.sum()
        if (totalMass <= 0) return ""
        val symmetryNumerator = (0 until component.height).sumOf { kotlin.math.abs(leftMass[it] - rightMass[it]) }
        val symmetryPercent = 100 - (symmetryNumerator * 100 / totalMass)
        if (symmetryPercent < 48) return ""

        val headBand = maxOf(2, component.height * 35 / 100)
        val topSpan = rowSpan.take(headBand).maxOrNull() ?: 0
        val bottomSpan = rowSpan.takeLast(headBand).maxOrNull() ?: 0
        val minHeadSpan = maxOf(shaftWidth * 1.45, shaftWidth + 2.0)
        val minHeadRows = if (component.height >= 24) 3 else 2
        val topHeadRows = rowSpan.take(headBand).count { it >= minHeadSpan }
        val bottomHeadRows = rowSpan.takeLast(headBand).count { it >= minHeadSpan }
        val topHead = topSpan >= minHeadSpan && topHeadRows >= minHeadRows
        val bottomHead = bottomSpan >= minHeadSpan && bottomHeadRows >= minHeadRows

        return when {
            topHead && topSpan * 100 >= bottomSpan * 112 -> "↑"
            bottomHead && bottomSpan * 100 >= topSpan * 112 -> "↓"
            else -> ""
        }
    }

    private fun looksLikeActionArrowCandidate(component: Rect, components: List<Rect>): Boolean {
        if (component.height < 10 || component.width < 4) return false
        if (component.width * 100 > component.height * 65) return false
        if (components.size == 1) return true
        if (components.size == 2) {
            val other = components.firstOrNull { it != component } ?: return true
            return component.height * 100 >= other.height * 108
        }

        val textHeight = estimateTextHeight(components)
        return component.height * 100 >= textHeight * 95
    }

    private fun estimateTextHeight(components: List<Rect>): Int {
        val heights = components
            .filter { it.width * 100 > it.height * 50 || it.height < 18 }
            .map { it.height }
            .ifEmpty { components.map { it.height } }
            .sorted()
        return heights[(heights.size - 1) / 2].coerceAtLeast(1)
    }

    private fun averageIntOrZero(values: List<Int>): Double {
        return if (values.isEmpty()) 0.0 else values.sum().toDouble() / values.size.toDouble()
    }

    private fun averageDoubleOrZero(values: List<Double>): Double {
        return if (values.isEmpty()) 0.0 else values.sum() / values.size.toDouble()
    }

    private fun stdDev(values: List<Double>, mean: Double): Double {
        if (values.isEmpty()) return 0.0
        val variance = values.sumOf { value ->
            val delta = value - mean
            delta * delta
        } / values.size.toDouble()
        return kotlin.math.sqrt(variance)
    }

    fun classifyCircle(binary: Mat, component: Rect): String {
        if (!looksLikeCircleCandidate(component)) return ""

        val mask = Mat(binary, component)
        if (mask.empty()) {
            mask.release()
            return ""
        }

        val area = Core.countNonZero(mask)
        val densityPercent = area * 100 / maxOf(1, component.width * component.height)
        val holes = countInnerContours(mask)
        mask.release()

        return if (holes >= 2 && densityPercent >= 45) "圈" else ""
    }

    fun looksLikeZero(binary: Mat, component: Rect): Boolean {
        if (component.width < 8 || component.height < 12) return false
        val aspectPercent = component.width * 100 / maxOf(1, component.height)
        if (aspectPercent !in 45..95) return false

        val mask = Mat(binary, component)
        if (mask.empty()) {
            mask.release()
            return false
        }
        val area = Core.countNonZero(mask)
        val densityPercent = area * 100 / maxOf(1, component.width * component.height)
        val holes = countInnerContours(mask)
        mask.release()

        return holes >= 1 && densityPercent in 28..62
    }

    private fun looksLikeCircleCandidate(component: Rect): Boolean {
        if (component.width < 12 || component.height < 12) return false
        val aspectPercent = component.width * 100 / maxOf(1, component.height)
        return aspectPercent in 75..125
    }

    private fun countInnerContours(mask: Mat): Int {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        val contourMask = mask.clone()
        Imgproc.findContours(contourMask, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE)
        contourMask.release()
        var holes = 0
        if (!hierarchy.empty()) {
            for (i in contours.indices) {
                val data = hierarchy.get(0, i) ?: continue
                if (data.size >= 4 && data[3] >= 0.0) {
                    holes++
                }
            }
        }
        hierarchy.release()
        contours.forEach { it.release() }
        return holes
    }

    fun normalizeOcrToken(text: String, component: Rect): String {
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
}

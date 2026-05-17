package com.example.yuanassist.tableocr

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class CellLayoutAnalyzer(
    private val minSegmentHeight: Int = 4,
    private val mergeGap: Int = 3,
    private val noiseDensityThreshold: Float = 0.01f
) {

    fun analyze(crop: Mat): List<LineSegment> {
        if (crop.empty()) return emptyList()

        // OTSU binary + invert
        val gray = Mat()
        Imgproc.cvtColor(crop, gray, Imgproc.COLOR_BGR2GRAY)
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        Core.bitwise_not(binary, binary)
        gray.release()

        val height = binary.rows()
        val width = binary.cols()

        // Horizontal projection
        val rowSums = IntArray(height)
        val rowData = ByteArray(width)
        for (y in 0 until height) {
            binary.get(y, 0, rowData)
            rowSums[y] = rowData.sumOf { (it.toInt() and 0xFF) }
        }
        binary.release()

        // Find continuous non-zero spans
        val spans = mutableListOf<Pair<Int, Int>>()
        var start: Int? = null
        for (y in 0 until height) {
            if (rowSums[y] > 0 && start == null) {
                start = y
            } else if (rowSums[y] == 0 && start != null) {
                spans.add(start to y)
                start = null
            }
        }
        if (start != null) {
            spans.add(start to height)
        }

        // Filter short and merge near
        val merged = mutableListOf<Pair<Int, Int>>()
        for ((top, bottom) in spans) {
            if (bottom - top < minSegmentHeight) continue
            if (merged.isNotEmpty() && top - merged.last().second <= mergeGap) {
                merged[merged.size - 1] = merged.last().first to bottom
            } else {
                merged.add(top to bottom)
            }
        }

        // Build segments with density
        val segments = mutableListOf<LineSegment>()
        for ((top, bottom) in merged) {
            val segHeight = bottom - top
            val segArea = width * segHeight
            val pixelSum = rowSums.slice(top until bottom).sum()
            val density = pixelSum.toFloat() / (segArea * 255)
            val isNoise = density < noiseDensityThreshold
            segments.add(LineSegment(top, bottom, segHeight, density, isNoise))
        }
        return segments
    }

    fun splitLines(crop: Mat, padding: Int = 2): List<Mat> {
        val segments = analyze(crop)
        val valid = dropTinySecondarySegments(segments.filter { !it.isNoise })
        if (valid.size <= 1) return emptyList()

        val height = crop.rows()
        return valid.map { seg ->
            val top = maxOf(0, seg.top - padding)
            val bottom = minOf(height, seg.bottom + padding)
            crop.submat(top, bottom, 0, crop.cols()).clone()
        }
    }

    private fun dropTinySecondarySegments(segments: List<LineSegment>): List<LineSegment> {
        if (segments.size <= 1) return segments
        val maxHeight = segments.maxOf { it.height }.coerceAtLeast(1)
        return segments.filter { segment ->
            segment.height >= 12 || segment.height * 100 >= maxHeight * 60
        }
    }
}

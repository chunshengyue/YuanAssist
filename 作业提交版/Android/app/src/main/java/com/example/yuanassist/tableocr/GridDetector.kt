package com.example.yuanassist.tableocr

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object GridDetector {

    fun detectTableStructure(binary: Mat): TableStructure {
        val height = binary.rows()
        val width = binary.cols()

        val hKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size((width / 8.0).toInt().coerceAtLeast(3).toDouble(), 1.0))
        val vKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, (height / 8.0).toInt().coerceAtLeast(3).toDouble()))

        val hLines = Mat()
        Imgproc.morphologyEx(binary, hLines, Imgproc.MORPH_OPEN, hKernel)
        val vLines = Mat()
        Imgproc.morphologyEx(binary, vLines, Imgproc.MORPH_OPEN, vKernel)

        val rowLines = extractLinePositions(hLines, axis = 1)
        val colLines = extractLinePositions(vLines, axis = 0)

        hLines.release()
        vLines.release()

        return TableStructure(rowLines, colLines, width, height)
    }

    private fun extractLinePositions(lineMat: Mat, axis: Int): List<Int> {
        val projection = Mat()
        Core.reduce(lineMat, projection, axis, Core.REDUCE_SUM, CvType.CV_32S)
        val data = IntArray(projection.rows() * projection.cols())
        projection.get(0, 0, data)

        val requiredLength = if (axis == 0) {
            lineMat.rows() * 45 / 100
        } else {
            lineMat.cols() / 2
        }
        val threshold = maxOf(1, requiredLength * 255)
        val mask = data.map { if (it >= threshold) 1 else 0 }

        projection.release()
        return clusterPositions(mask)
    }

    private fun clusterPositions(mask: List<Int>): List<Int> {
        val positions = mask.mapIndexedNotNull { i, v -> if (v > 0) i else null }
        if (positions.isEmpty()) return emptyList()

        val clusters = mutableListOf(mutableListOf(positions[0]))
        for (i in 1 until positions.size) {
            if (positions[i] - clusters.last().last() <= 1) {
                clusters.last().add(positions[i])
            } else {
                clusters.add(mutableListOf(positions[i]))
            }
        }
        return clusters.map { it.sum() / it.size }
    }

    fun buildCellBoxes(structure: TableStructure, maxCols: Int = 6): List<CellBox> {
        val rowSpans = dropPortraitHeaderRow(spansFromLines(structure.rowLines))
        val colLines = completeOuterColumnLines(structure.colLines, structure.width, maxCols)
        val colSpans = spansFromLines(colLines).take(maxCols)

        val cells = mutableListOf<CellBox>()
        for ((rowIdx, rowSpan) in rowSpans.withIndex()) {
            for ((colIdx, colSpan) in colSpans.withIndex()) {
                val w = colSpan.second - colSpan.first
                val h = rowSpan.second - rowSpan.first
                if (w <= 0 || h <= 0) continue
                cells.add(CellBox(rowIdx, colIdx, colSpan.first, rowSpan.first, w, h))
            }
        }
        return cells
    }

    private fun dropPortraitHeaderRow(rowSpans: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        if (rowSpans.size < 3) return rowSpans

        val firstHeight = rowSpans.first().second - rowSpans.first().first
        val actionHeights = rowSpans.drop(1).map { it.second - it.first }.sorted()
        val medianActionHeight = actionHeights[actionHeights.size / 2]
        return if (firstHeight >= medianActionHeight * 2) {
            rowSpans.drop(1)
        } else {
            rowSpans
        }
    }

    private fun completeOuterColumnLines(lines: List<Int>, width: Int, expectedCols: Int): List<Int> {
        val expectedLineCount = expectedCols + 1
        val sorted = lines.sorted().toMutableList()
        if (sorted.size >= expectedLineCount || sorted.size < 2) return sorted

        val gaps = sorted.zip(sorted.drop(1)).map { (left, right) -> right - left }
        val medianGap = gaps.sorted()[gaps.size / 2]
        if (medianGap <= 0) return sorted

        while (sorted.size < expectedLineCount) {
            val inserted = when {
                shouldInsertLeftBorder(sorted, medianGap) -> {
                    sorted.add(0, 0)
                    true
                }
                shouldInsertRightBorder(sorted, width, medianGap) -> {
                    sorted.add(width - 1)
                    true
                }
                else -> false
            }
            if (!inserted) break
        }
        return sorted
    }

    private fun shouldInsertLeftBorder(lines: List<Int>, medianGap: Int): Boolean {
        val first = lines.first()
        if (first <= 2) return false
        return isLikelyMissingOuterGap(first, medianGap)
    }

    private fun shouldInsertRightBorder(lines: List<Int>, width: Int, medianGap: Int): Boolean {
        if (width <= 0) return false
        val rightGap = width - 1 - lines.last()
        if (rightGap <= 2) return false
        return isLikelyMissingOuterGap(rightGap, medianGap)
    }

    private fun isLikelyMissingOuterGap(gap: Int, medianGap: Int): Boolean {
        return gap * 100 in (medianGap * 55)..(medianGap * 145)
    }

    private fun spansFromLines(lines: List<Int>): List<Pair<Int, Int>> {
        val sorted = lines.sorted()
        return sorted.zip(sorted.drop(1))
    }
}

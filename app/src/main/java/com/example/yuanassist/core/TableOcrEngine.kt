package com.example.yuanassist.core

import android.content.Context
import android.graphics.Bitmap
import com.example.yuanassist.tableocr.*
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

object TableOcrEngine {

    private var initialized = false

    fun init(context: Context): Boolean {
        if (initialized) return true
        val modelPath = AssetCopier.getModelPath(context)
        val labelPath = AssetCopier.getLabelPath(context)
        if (!PaddleOcrNative.init(modelPath, labelPath)) return false

        OcrScheduler.setOcrFunction { mat ->
            android.util.Log.d("TableOcr", "ocrFunc mat type=${mat.type()} depth=${mat.depth()} ch=${mat.channels()} ${mat.cols()}x${mat.rows()} empty=${mat.empty()}")
            if (mat.empty() || mat.cols() <= 0 || mat.rows() <= 0) return@setOcrFunction ""
            val bgr = if (mat.channels() == 3 && mat.depth() == 0) mat.clone() else {
                val m = Mat()
                when {
                    mat.channels() == 4 && mat.depth() == 0 -> Imgproc.cvtColor(mat, m, Imgproc.COLOR_BGRA2BGR)
                    mat.channels() == 1 && mat.depth() == 0 -> Imgproc.cvtColor(mat, m, Imgproc.COLOR_GRAY2BGR)
                    else -> { m.release(); return@setOcrFunction "" }
                }
                m
            }
            android.util.Log.d("TableOcr", "ocrFunc bgr type=${bgr.type()} depth=${bgr.depth()} ch=${bgr.channels()} ${bgr.cols()}x${bgr.rows()}")
            val bmp = Bitmap.createBitmap(bgr.cols(), bgr.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(bgr, bmp)
            bgr.release()
            val text = PaddleOcrNative.recognize(bmp)
            bmp.recycle()
            text
        }
        initialized = true
        return true
    }

    fun recognize(context: Context, bitmap: Bitmap): List<RowResult> =
        recognizeInternal(context, bitmap, includeRowImages = false).rows

    fun recognizeWithPreview(context: Context, bitmap: Bitmap): TableOcrPreviewResult =
        recognizeInternal(context, bitmap, includeRowImages = true)

    private fun recognizeInternal(
        context: Context,
        bitmap: Bitmap,
        includeRowImages: Boolean
    ): TableOcrPreviewResult {
        if (!init(context)) throw IllegalStateException("PaddleOCR init failed")

        // 1. Convert Bitmap to Mat
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)
        Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2BGR)

        // 2. Extract table region
        val (tableMat, _) = TableDetector.extractTableRegion(srcMat)
        srcMat.release()

        // 3. Binarize
        val binary = TableDetector.toBinary(tableMat)

        // 4. Detect table structure
        val structure = GridDetector.detectTableStructure(binary)
        binary.release()

        // 5. Build cell boxes
        val boxes = GridDetector.buildCellBoxes(structure)
        val rowPreview = if (includeRowImages) {
            buildRowPreview(tableMat, boxes)
        } else {
            RowPreview(emptyList(), emptyList())
        }

        // 6. OCR each cell
        val rawTextByCell = OcrScheduler.ocrCells(tableMat, boxes)

        // 7. Normalize
        val normalizedByCell = mutableMapOf<Pair<Int, Int>, String>()
        for (box in boxes) {
            val key = box.row to box.col
            val raw = rawTextByCell[key] ?: ""
            normalizedByCell[key] = if (box.col == 0) {
                PostProcessor.normalizeRoundText(raw)
            } else {
                raw
            }
        }
        tableMat.release()

        // 8. Group into rows
        return TableOcrPreviewResult(
            rows = groupTextRows(boxes, normalizedByCell),
            rowImages = rowPreview.images,
            rowColumnWidths = rowPreview.columnWidths
        )
    }

    private data class RowPreview(
        val images: List<Bitmap>,
        val columnWidths: List<List<Int>>
    )

    private fun buildRowPreview(tableMat: Mat, boxes: List<CellBox>): RowPreview {
        val images = mutableListOf<Bitmap>()
        val widths = mutableListOf<List<Int>>()
        val boxesByRow = boxes.groupBy { it.row }
        for (rowIndex in boxesByRow.keys.sorted()) {
            val rowBoxes = (0..5).mapNotNull { col -> boxesByRow[rowIndex]?.firstOrNull { it.col == col } }
            if (rowBoxes.isEmpty()) continue
            val left = rowBoxes.minOf { it.x }.coerceAtLeast(0)
            val top = rowBoxes.minOf { it.y }.coerceAtLeast(0)
            val right = rowBoxes.maxOf { it.x + it.w }.coerceAtMost(tableMat.cols())
            val bottom = rowBoxes.maxOf { it.y + it.h }.coerceAtMost(tableMat.rows())
            if (right <= left || bottom <= top) continue
            val rowMat = Mat(tableMat, Rect(left, top, right - left, bottom - top))
            val rgba = Mat()
            Imgproc.cvtColor(rowMat, rgba, Imgproc.COLOR_BGR2RGBA)
            val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, bitmap)
            rgba.release()
            rowMat.release()
            images.add(bitmap)
            widths.add((0..5).map { col ->
                rowBoxes.firstOrNull { it.col == col }?.w?.coerceAtLeast(1) ?: 1
            })
        }
        return RowPreview(images, widths)
    }

    private fun groupTextRows(
        boxes: List<CellBox>,
        textByCell: Map<Pair<Int, Int>, String>
    ): List<RowResult> {
        val colsByRow = mutableMapOf<Int, MutableSet<Int>>()
        for (cell in boxes) {
            colsByRow.getOrPut(cell.row) { mutableSetOf() }.add(cell.col)
        }

        val rows = mutableListOf<RowResult>()
        for (rowIdx in colsByRow.keys.sorted()) {
            val roundLabel = textByCell[rowIdx to 0]?.trim().orEmpty()
            val actions = (1..5).map { col ->
                textByCell[rowIdx to col]?.trim().orEmpty()
            }
            val label = roundLabel.ifEmpty { "${rowIdx + 1}回合" }
            rows.add(RowResult(label, actions))
        }
        return rows
    }

    fun release() {
        PaddleOcrNative.release()
        initialized = false
    }
}

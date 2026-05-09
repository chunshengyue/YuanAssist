package com.example.yuanassist.core

import android.content.Context
import android.graphics.Bitmap
import com.example.yuanassist.tableocr.*
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
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

    fun recognize(context: Context, bitmap: Bitmap): List<RowResult> {
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
                PostProcessor.normalizeActionText(raw)
            }
        }
        tableMat.release()

        // 8. Group into rows
        return groupTextRows(boxes, normalizedByCell)
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
            if (roundLabel.isEmpty() && actions.all { it.isEmpty() }) continue
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

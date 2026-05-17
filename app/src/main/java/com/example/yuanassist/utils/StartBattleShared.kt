package com.example.yuanassist.utils

import android.graphics.PointF
import com.example.yuanassist.model.ROI
import com.example.yuanassist.tableocr.PaddleTextResult

object StartBattleShared {

    const val TASK_DELAY_MS = 3000L
    const val RED_THRESHOLD = 0.72f
    const val CENTER_X = 540f
    const val CENTER_Y = 1700f
    const val ROI_WIDTH = 500f
    const val ROI_HEIGHT = 400f
    const val ALIGN = "bottom"
    const val OCR_MIN_HIT_COUNT = 2
    private val OCR_TARGET_CHARS = listOf('开', '始', '战', '斗')

    data class OcrLineMatch(
        val lineText: String,
        val normalizedLineText: String,
        val hitChars: List<Char>,
        val center: PointF,
        val containsPhrase: Boolean,
        val area: Int
    ) {
        val hitCount: Int
            get() = hitChars.size
    }

    fun buildRoi(): ROI = ROI(
        x = CENTER_X,
        y = CENTER_Y,
        w = ROI_WIDTH,
        h = ROI_HEIGHT,
        align = ALIGN
    )

    fun findBestOcrLineMatch(result: PaddleTextResult): OcrLineMatch? {
        return result.blocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val boundingBox = line.boundingBox
                val normalizedLineText = line.text.filterNot { it.isWhitespace() }
                val hitChars = OCR_TARGET_CHARS.filter { normalizedLineText.contains(it) }
                if (hitChars.size < OCR_MIN_HIT_COUNT) {
                    return@mapNotNull null
                }
                OcrLineMatch(
                    lineText = line.text,
                    normalizedLineText = normalizedLineText,
                    hitChars = hitChars,
                    center = PointF(boundingBox.exactCenterX(), boundingBox.exactCenterY()),
                    containsPhrase = normalizedLineText.contains("开始战斗"),
                    area = boundingBox.width() * boundingBox.height()
                )
            }
            .maxWithOrNull(compareBy<OcrLineMatch>({ it.containsPhrase }, { it.hitCount }, { it.area }))
    }
}

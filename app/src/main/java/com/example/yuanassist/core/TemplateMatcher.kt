package com.example.yuanassist.core

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

object TemplateMatcher {
    data class Result(
        val center: PointF,
        val score: Double,
    )

    fun match(screenBitmap: Bitmap, templateBitmap: Bitmap, threshold: Float): Result? {
        val ownsSourceBitmap = screenBitmap.config != Bitmap.Config.ARGB_8888
        val sourceBitmap = if (ownsSourceBitmap) {
            screenBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            screenBitmap
        }
        try {
            return Mat().use { srcMat ->
                Mat().use { tmplMat ->
                    Mat().use { resultMat ->
                        Utils.bitmapToMat(sourceBitmap, srcMat)
                        Utils.bitmapToMat(templateBitmap, tmplMat)
                        Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
                        Imgproc.cvtColor(tmplMat, tmplMat, Imgproc.COLOR_RGBA2GRAY)
                        Imgproc.matchTemplate(srcMat, tmplMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
                        val minMax = Core.minMaxLoc(resultMat)
                        if (minMax.maxVal >= threshold) {
                            Result(
                                center = PointF(
                                    (minMax.maxLoc.x + templateBitmap.width / 2.0).toFloat(),
                                    (minMax.maxLoc.y + templateBitmap.height / 2.0).toFloat(),
                                ),
                                score = minMax.maxVal,
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        } finally {
            if (ownsSourceBitmap) sourceBitmap.recycle()
        }
    }

    private inline fun <T> Mat.use(block: (Mat) -> T): T {
        try {
            return block(this)
        } finally {
            release()
        }
    }
}

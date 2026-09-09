package com.example.yuanassist.core

import android.graphics.Bitmap
import android.graphics.Color

object OcrPreprocessor {
    const val YELLOW_TEXT = "yellow_text"
    const val LIGHT_TEXT = "light_text"
    const val SOFT_TEXT = "soft_text"

    fun createConfigured(
        source: Bitmap,
        preprocess: String?,
        onError: ((String, Throwable) -> Unit)? = null,
    ): Bitmap? {
        return when (preprocess?.lowercase()) {
            YELLOW_TEXT -> createYellowTextBitmap(source, onError)
            LIGHT_TEXT -> createLightTextBitmap(source, onError)
            SOFT_TEXT -> createSoftTextBitmap(source, onError)
            else -> null
        }
    }

    private fun createYellowTextBitmap(
        source: Bitmap,
        onError: ((String, Throwable) -> Unit)?,
    ): Bitmap? {
        return createBinaryTextBitmap(source, onError, "OCR 黄字预处理失败") { r, g, b ->
            r >= 150 && g >= 110 && b <= 180 && (r - b) >= 40 && (g - b) >= 30
        }
    }

    private fun createLightTextBitmap(
        source: Bitmap,
        onError: ((String, Throwable) -> Unit)?,
    ): Bitmap? {
        return createBinaryTextBitmap(source, onError, "OCR 浅色文字预处理失败") { r, g, b ->
            r >= 145 && g >= 120 && b >= 90 && r >= b - 10 && g >= b - 35
        }
    }

    private fun createSoftTextBitmap(
        source: Bitmap,
        onError: ((String, Throwable) -> Unit)?,
    ): Bitmap? {
        return try {
            val width = source.width
            val height = source.height
            val input = IntArray(width * height)
            val output = IntArray(width * height)
            source.getPixels(input, 0, width, 0, 0, width, height)
            for (i in input.indices) {
                val color = input[i]
                val gray = (
                    Color.red(color) * 30 +
                        Color.green(color) * 59 +
                        Color.blue(color) * 11
                    ) / 100
                val enhanced = ((gray - 128) * 1.12f + 128)
                    .toInt()
                    .coerceIn(0, 255)
                output[i] = Color.rgb(enhanced, enhanced, enhanced)
            }
            Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            onError?.invoke("OCR 轻预处理失败", t)
            null
        }
    }

    private inline fun createBinaryTextBitmap(
        source: Bitmap,
        noinline onError: ((String, Throwable) -> Unit)?,
        errorMessage: String,
        crossinline isText: (r: Int, g: Int, b: Int) -> Boolean,
    ): Bitmap? {
        return try {
            val width = source.width
            val height = source.height
            val input = IntArray(width * height)
            val output = IntArray(width * height)
            source.getPixels(input, 0, width, 0, 0, width, height)
            for (i in input.indices) {
                val color = input[i]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                output[i] = if (isText(r, g, b)) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            onError?.invoke(errorMessage, t)
            null
        }
    }
}

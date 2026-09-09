package com.example.yuanassist.tableocr

import android.content.Context
import java.io.File

object AssetCopier {
    private val REQUIRED_ASSETS = listOf(
        "models/PP-OCRv6_small_det.onnx",
        "models/PP-OCRv6_small_rec.onnx",
        "labels/ppocr_keys_ocrv6_small.txt"
    )

    fun copyOcrAssets(context: Context): File {
        val targetDir = File(context.filesDir, "ocr")
        if (REQUIRED_ASSETS.any { !File(targetDir, it).exists() }) {
            copyRequiredAssets(context, targetDir)
        }
        return targetDir
    }

    fun getModelPath(context: Context): String {
        val dir = copyOcrAssets(context)
        return File(dir, "models/PP-OCRv6_small_rec.onnx").absolutePath
    }

    fun getDetModelPath(context: Context): String {
        val dir = copyOcrAssets(context)
        return File(dir, "models/PP-OCRv6_small_det.onnx").absolutePath
    }

    fun getLabelPath(context: Context): String {
        val dir = copyOcrAssets(context)
        return File(dir, "labels/ppocr_keys_ocrv6_small.txt").absolutePath
    }

    private fun copyRequiredAssets(context: Context, targetDir: File) {
        for (relativePath in REQUIRED_ASSETS) {
            val childTarget = File(targetDir, relativePath)
            childTarget.parentFile?.mkdirs()
            context.assets.open("ocr/$relativePath").use { input ->
                childTarget.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}

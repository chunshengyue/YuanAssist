package com.example.yuanassist.tableocr

import android.content.Context
import java.io.File

object AssetCopier {

    fun copyOcrAssets(context: Context): File {
        val targetDir = File(context.filesDir, "ocr")
        if (!targetDir.exists() || targetDir.listFiles().isNullOrEmpty()) {
            copyAssetDir(context, "ocr", targetDir)
        }
        return targetDir
    }

    fun getModelPath(context: Context): String {
        val dir = copyOcrAssets(context)
        return File(dir, "models/PP-OCRv5_mobile_rec.nb").absolutePath
    }

    fun getLabelPath(context: Context): String {
        val dir = copyOcrAssets(context)
        return File(dir, "labels/ppocr_keys_ocrv5.txt").absolutePath
    }

    private fun copyAssetDir(context: Context, assetPath: String, targetDir: File) {
        if (!targetDir.exists()) targetDir.mkdirs()
        val children = context.assets.list(assetPath) ?: return
        for (name in children) {
            val childAsset = "$assetPath/$name"
            val childTarget = File(targetDir, name)
            val subChildren = context.assets.list(childAsset)
            if (subChildren != null && subChildren.isNotEmpty()) {
                copyAssetDir(context, childAsset, childTarget)
            } else {
                context.assets.open(childAsset).use { input ->
                    childTarget.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

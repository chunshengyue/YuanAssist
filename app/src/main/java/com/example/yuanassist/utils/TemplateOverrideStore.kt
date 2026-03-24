package com.example.yuanassist.utils

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object TemplateOverrideStore {

    private const val DIR_NAME = "template_overrides"

    private fun overrideDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    fun overrideFile(context: Context, fileName: String): File =
        File(overrideDir(context), fileName)

    fun hasOverride(context: Context, fileName: String): Boolean =
        overrideFile(context, fileName).exists()

    fun saveOverride(context: Context, fileName: String, bitmap: Bitmap): Boolean {
        return try {
            val file = overrideFile(context, fileName)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun restoreOverride(context: Context, fileName: String): Boolean {
        val file = overrideFile(context, fileName)
        return !file.exists() || file.delete()
    }

    fun loadBitmap(context: Context, assets: AssetManager, fileName: String): Bitmap? {
        return try {
            val overrideFile = overrideFile(context, fileName)
            if (overrideFile.exists()) {
                BitmapFactory.decodeFile(overrideFile.absolutePath)
            } else {
                assets.open(fileName).use { BitmapFactory.decodeStream(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun cacheKey(context: Context, fileName: String): String {
        val overrideFile = overrideFile(context, fileName)
        return if (overrideFile.exists()) {
            "$fileName#override#${overrideFile.lastModified()}"
        } else {
            "$fileName#asset"
        }
    }
}

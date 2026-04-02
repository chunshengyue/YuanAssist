package com.example.yuanassist.utils

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object TemplateOverrideStore {

    private const val DIR_NAME = "template_overrides"
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_START_BATTLE_MATCH_MODE = "start_battle_match_mode"
    private const val MATCH_MODE_COLOR = "color"
    private const val MATCH_MODE_TEMPLATE = "template"

    const val START_BATTLE_TEMPLATE_FILE_NAME = "__start_battle_template__.png"
    const val START_BATTLE_TEMPLATE_THRESHOLD = 0.90f

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

    fun isStartBattleTemplateMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_START_BATTLE_MATCH_MODE, MATCH_MODE_COLOR) == MATCH_MODE_TEMPLATE
    }

    fun enableStartBattleTemplateMode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_START_BATTLE_MATCH_MODE, MATCH_MODE_TEMPLATE)
            .apply()
    }

    fun disableStartBattleTemplateMode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_START_BATTLE_MATCH_MODE, MATCH_MODE_COLOR)
            .apply()
    }
}

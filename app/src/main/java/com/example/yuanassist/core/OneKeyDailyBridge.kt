package com.example.yuanassist.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object OneKeyDailyBridge {
    const val ACTION_IMPORT_ONE_KEY_DAILY_QUEUE = "ACTION_IMPORT_ONE_KEY_DAILY_QUEUE"

    private const val PREFS_APP = "app_prefs"
    private const val KEY_PENDING_ONE_KEY_DAILY_QUEUE = "pending_one_key_daily_queue"

    @Volatile
    var pendingScriptFileNames: List<String>? = null

    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    fun savePendingScriptFileNames(context: Context, fileNames: List<String>) {
        pendingScriptFileNames = fileNames
        context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_ONE_KEY_DAILY_QUEUE, gson.toJson(fileNames))
            .apply()
    }

    fun peekPendingScriptFileNames(context: Context): List<String>? {
        pendingScriptFileNames?.takeIf { it.isNotEmpty() }?.let { return it }
        val raw = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_ONE_KEY_DAILY_QUEUE, null)
            ?: return null
        return runCatching { gson.fromJson<List<String>>(raw, listType) }
            .getOrNull()
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
    }

    fun consumePendingScriptFileNames(context: Context): List<String>? {
        val fileNames = peekPendingScriptFileNames(context)
        clearPendingScriptFileNames(context)
        return fileNames
    }

    fun clearPendingScriptFileNames(context: Context) {
        pendingScriptFileNames = null
        context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_ONE_KEY_DAILY_QUEUE)
            .apply()
    }
}

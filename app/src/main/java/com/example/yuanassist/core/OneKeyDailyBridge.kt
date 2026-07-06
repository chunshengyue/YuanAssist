package com.example.yuanassist.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object OneKeyDailyBridge {
    const val ACTION_IMPORT_ONE_KEY_DAILY_QUEUE = "ACTION_IMPORT_ONE_KEY_DAILY_QUEUE"

    private const val PREFS_APP = "app_prefs"
    private const val KEY_PENDING_ONE_KEY_DAILY_QUEUE = "pending_one_key_daily_queue"

    @Volatile
    var pendingSelections: List<DailyPlanSelection>? = null

    private val gson = Gson()
    private val listType = object : TypeToken<List<DailyPlanSelection>>() {}.type

    fun savePendingSelections(context: Context, selections: List<DailyPlanSelection>) {
        pendingSelections = selections
        context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_ONE_KEY_DAILY_QUEUE, gson.toJson(selections))
            .apply()
    }

    fun peekPendingSelections(context: Context): List<DailyPlanSelection>? {
        pendingSelections?.takeIf { it.isNotEmpty() }?.let { return it }
        val raw = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_ONE_KEY_DAILY_QUEUE, null)
            ?: return null
        return runCatching { gson.fromJson<List<DailyPlanSelection>>(raw, listType) }
            .getOrNull()
            ?.filter { it.fileName.isNotBlank() && it.jsonContent.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
    }

    fun consumePendingSelections(context: Context): List<DailyPlanSelection>? {
        val selections = peekPendingSelections(context)
        clearPendingSelections(context)
        return selections
    }

    fun clearPendingSelections(context: Context) {
        pendingSelections = null
        context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_ONE_KEY_DAILY_QUEUE)
            .apply()
    }
}

package com.example.yuanassist.utils

import android.content.Context
import com.example.yuanassist.model.DailyTaskPlan

object DailyGlobalDelayStore {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_DAILY_GLOBAL_DELAY_MS = "daily_global_delay_ms"

    fun getDelayMs(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DAILY_GLOBAL_DELAY_MS, 0L)
            .coerceAtLeast(0L)
    }

    fun setDelayMs(context: Context, delayMs: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DAILY_GLOBAL_DELAY_MS, delayMs.coerceAtLeast(0L))
            .apply()
    }

    fun applyToPlan(context: Context, plan: DailyTaskPlan): DailyTaskPlan {
        val delayMs = getDelayMs(context)
        if (delayMs <= 0L) return plan
        return plan.copy(
            tasks = plan.tasks.map { task ->
                task.copy(delay = (task.delay + delayMs).coerceAtLeast(0L))
            },
        )
    }
}

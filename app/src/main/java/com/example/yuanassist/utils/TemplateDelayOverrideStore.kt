package com.example.yuanassist.utils

import android.content.Context
import com.example.yuanassist.model.DailyTaskPlan

object TemplateDelayOverrideStore {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_PREFIX = "template_delay_increment"

    fun getIncrementMs(context: Context, taskKey: String, templateName: String): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(prefKey(taskKey, templateName), 0L)
            .coerceAtLeast(0L)
    }

    fun setIncrementMs(context: Context, taskKey: String, templateName: String, incrementMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = prefKey(taskKey, templateName)
        prefs.edit().apply {
            if (incrementMs <= 0L) {
                remove(key)
            } else {
                putLong(key, incrementMs)
            }
        }.apply()
    }

    fun applyToPlan(context: Context, taskKey: String, plan: DailyTaskPlan): DailyTaskPlan {
        var changed = false
        val updatedTasks = plan.tasks.map { task ->
            if (task.action != "MATCH_TEMPLATE") return@map task
            val templateName = task.params?.template_name?.takeIf { it.isNotBlank() } ?: return@map task
            val incrementMs = getIncrementMs(context, taskKey, templateName)
            if (incrementMs <= 0L) return@map task
            changed = true
            task.copy(delay = (task.delay + incrementMs).coerceAtLeast(0L))
        }
        return if (changed) plan.copy(tasks = updatedTasks) else plan
    }

    private fun prefKey(taskKey: String, templateName: String): String =
        "$KEY_PREFIX::$taskKey::$templateName"
}

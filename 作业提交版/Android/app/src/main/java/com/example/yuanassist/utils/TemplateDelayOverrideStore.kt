package com.example.yuanassist.utils

import android.content.Context
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ScreenshotStep

object TemplateDelayOverrideStore {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_PREFIX = "template_delay_increment"
    private const val DAI_BAN_GONG_WU_SCRIPT = "dai_ban_gong_wu.json"

    fun getIncrementMs(context: Context, taskKey: String, templateName: String): Long {
        return getIncrementMs(context, lookupTaskKeys(taskKey), templateName)
    }

    fun setIncrementMs(context: Context, taskKey: String, templateName: String, incrementMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val primaryKey = prefKey(taskKey, templateName)
        val aliasKeys = lookupTaskKeys(taskKey)
            .map { prefKey(it, templateName) }
            .filter { it != primaryKey }
        prefs.edit().apply {
            if (incrementMs <= 0L) {
                remove(primaryKey)
            } else {
                putLong(primaryKey, incrementMs)
            }
            aliasKeys.forEach(::remove)
        }.apply()
    }

    fun applyToPlan(
        context: Context,
        taskKey: String,
        plan: DailyTaskPlan,
        extraTaskKeys: List<String> = emptyList()
    ): DailyTaskPlan {
        val lookupTaskKeys = lookupTaskKeys(taskKey, extraTaskKeys)
        var changed = false
        val updatedTasks = plan.tasks.map { task ->
            val incrementMs = taskDelayIncrementMs(context, lookupTaskKeys, taskKey, task)
            if (incrementMs <= 0L) return@map task
            changed = true
            task.copy(delay = (task.delay + incrementMs).coerceAtLeast(0L))
        }
        return if (changed) plan.copy(tasks = updatedTasks) else plan
    }

    private fun taskDelayIncrementMs(
        context: Context,
        lookupTaskKeys: List<String>,
        taskKey: String,
        task: DailyTask
    ): Long {
        val visualIncrement = visualOptionKeys(taskKey, task)
            .distinct()
            .maxOfOrNull { optionKey -> getIncrementMs(context, lookupTaskKeys, optionKey) }
            ?: 0L
        val specialIncrement = specialTaskDelayIncrementMs(context, lookupTaskKeys, task)
        return (visualIncrement + specialIncrement).coerceAtLeast(0L)
    }

    private fun visualOptionKeys(taskKey: String, task: DailyTask): List<String> {
        val params = task.params ?: return emptyList()
        return when (task.action) {
            "MATCH_TEMPLATE" -> listOfNotNull(params.template_name?.takeIf { it.isNotBlank() })
            "OCR" -> {
                val derivedTemplateName = TemplateOverrideStore.ocrTemplateFileName(taskKey, task.id)
                listOfNotNull(
                    params.template_name?.takeIf { it.isNotBlank() },
                    derivedTemplateName
                )
            }
            "SCREENSHOT_GROUP" -> {
                params.screenshot_steps.orEmpty().mapIndexedNotNull { index, step ->
                    screenshotStepOptionKey(taskKey, task.id, index, step)
                }
            }
            else -> emptyList()
        }
    }

    private fun screenshotStepOptionKey(
        taskKey: String,
        taskId: Int,
        index: Int,
        step: ScreenshotStep
    ): String? {
        val normalizedType = step.type.trim().uppercase()
        val isTemplate = normalizedType == "TEMPLATE" || normalizedType == "MATCH_TEMPLATE"
        val isOcr = normalizedType == "OCR"
        if (!isTemplate && !isOcr) return null
        step.template_name?.takeIf { it.isNotBlank() }?.let { return it }
        if (!isOcr) return null
        return TemplateOverrideStore.ocrTemplateFileName(taskKey, taskId)
            ?.replace("_ocr.png", "_step_${index + 1}_ocr.png")
    }

    private fun specialTaskDelayIncrementMs(
        context: Context,
        lookupTaskKeys: List<String>,
        task: DailyTask
    ): Long {
        if (task.action != "OCR") return 0L
        if (DAI_BAN_GONG_WU_SCRIPT !in lookupTaskKeys) return 0L
        return getIncrementMs(context, lookupTaskKeys, DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION)
    }

    private fun getIncrementMs(
        context: Context,
        lookupTaskKeys: List<String>,
        templateName: String
    ): Long {
        val normalizedTemplateName = templateName.takeIf { it.isNotBlank() } ?: return 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        lookupTaskKeys.forEach { taskKey ->
            val value = prefs.getLong(prefKey(taskKey, normalizedTemplateName), 0L).coerceAtLeast(0L)
            if (value > 0L) return value
        }
        return 0L
    }

    private fun lookupTaskKeys(taskKey: String, extraTaskKeys: List<String> = emptyList()): List<String> =
        (listOf(taskKey) + extraTaskKeys)
            .flatMap(::taskKeyAliases)
            .distinct()

    private fun taskKeyAliases(taskKey: String): List<String> {
        val normalized = taskKey.trim().takeIf { it.isNotBlank() } ?: return emptyList()
        val aliases = linkedSetOf<String>()
        fun addWithJsonPair(value: String) {
            aliases += value
            if (value.startsWith("TASK_")) return
            if (value.endsWith(".json", ignoreCase = true)) {
                aliases += value.removeSuffix(".json")
            } else {
                aliases += "$value.json"
            }
        }

        addWithJsonPair(normalized)
        if (normalized.startsWith("user:")) {
            addWithJsonPair(normalized.removePrefix("user:"))
        }
        return aliases.toList()
    }

    private fun prefKey(taskKey: String, templateName: String): String =
        "$KEY_PREFIX::$taskKey::$templateName"
}

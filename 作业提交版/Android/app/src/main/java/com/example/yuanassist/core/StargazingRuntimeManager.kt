package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.widget.Toast
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.StargazingConfig
import com.example.yuanassist.utils.CloudScriptOverrideStore
import com.example.yuanassist.utils.RunLogger
import com.google.gson.Gson

class StargazingRuntimeManager(
    private val service: AccessibilityService,
    private val onRunningChanged: (Boolean) -> Unit
) {

    companion object {
        private const val SCRIPT_FILE_NAME = "wu_yue_ka_guan_xing_batch.json"
        private const val STARGAZING_CLICK_X = 763.49f
        private const val STARGAZING_CLICK_Y = 1318.2489f
        private const val STARGAZING_CLICK_ALIGN = "bottom"
        private const val INITIAL_BATCH_START_TASK_ID = 0
        private const val FOLLOW_UP_BATCH_START_TASK_ID = 3
    }

    private val engine = AutoTaskEngine(service)
    private val gson = Gson()
    private var config: StargazingConfig? = null
    private var remainingCount = 0
    private var hasCompletedFirstBatch = false

    var isRunning = false
        private set

    fun prepare(config: StargazingConfig) {
        this.config = config
        engine.debugRoiEnabled = config.debugModeEnabled
        engine.verboseLoggingEnabled = true
        engine.diagnosticLoggingEnabled = config.debugModeEnabled
        engine.globalDelayOffsetMs = config.lowSpecDelayMs
        engine.setRunLogScope("无月卡观星", "脚本节点")
    }

    fun start(): Boolean {
        val currentConfig = config ?: return false
        val plan = loadPlan() ?: return false
        if (currentConfig.totalCount <= 0) return false
        remainingCount = currentConfig.totalCount
        hasCompletedFirstBatch = false
        isRunning = true
        onRunningChanged(true)
        RunLogger.clear()
        RunLogger.i(module = "无月卡观星", section = "总流程", message = "开始，总次数=${currentConfig.totalCount}")
        return runNextBatch(plan, currentConfig)
    }

    fun stop(showToast: Boolean = false) {
        engine.stop()
        isRunning = false
        remainingCount = 0
        hasCompletedFirstBatch = false
        onRunningChanged(false)
        if (showToast) {
            Toast.makeText(service, "无月卡观星已停止", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPlan(): DailyTaskPlan? {
        return try {
            CloudScriptOverrideStore.loadAssetPlanWithOverride(service, SCRIPT_FILE_NAME, gson)
        } catch (t: Throwable) {
            RunLogger.e(module = "无月卡观星", section = "总流程", message = "加载脚本失败：$SCRIPT_FILE_NAME", throwable = t)
            null
        }
    }

    private fun runNextBatch(plan: DailyTaskPlan, config: StargazingConfig): Boolean {
        val currentBatchCount = remainingCount.coerceAtMost(30)
        if (currentBatchCount <= 0) {
            isRunning = false
            hasCompletedFirstBatch = false
            onRunningChanged(false)
            val message = "无月卡观星已完成"
            RunLogger.i(module = "无月卡观星", section = "总流程", message = message)
            Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
            return true
        }
        val startTaskId = if (hasCompletedFirstBatch) {
            FOLLOW_UP_BATCH_START_TASK_ID
        } else {
            INITIAL_BATCH_START_TASK_ID
        }
        RunLogger.i(module = "无月卡观星", section = "批次", message = "本批$currentBatchCount 次，剩余=$remainingCount")
        engine.startPlan(
            plan = plan.copy(start_task_id = startTaskId),
            onCompleted = { success, errorMsg ->
                if (!success) {
                    isRunning = false
                    remainingCount = 0
                    hasCompletedFirstBatch = false
                    onRunningChanged(false)
                    val message = "无月卡观星已停止：$errorMsg"
                    RunLogger.e(module = "无月卡观星", section = "总流程", message = message)
                    Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
                    return@startPlan
                }
                remainingCount -= currentBatchCount
                hasCompletedFirstBatch = true
                runNextBatch(plan, config)
            },
            initialVariables = buildMap {
                put("current_batch_count", currentBatchCount.toString())
                put("stargazing_click_x", STARGAZING_CLICK_X.toString())
                put("stargazing_click_y", STARGAZING_CLICK_Y.toString())
                put("stargazing_click_align", STARGAZING_CLICK_ALIGN)
                put("stargazing_click_interval", config.clickIntervalMs.toString())
            },
            scriptFileName = SCRIPT_FILE_NAME,
        )
        return true
    }
}

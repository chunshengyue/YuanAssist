package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.yuanassist.model.BirdFoodConfig
import com.example.yuanassist.model.BirdFoodStopCondition
import com.example.yuanassist.model.BirdFoodTaskType
import com.example.yuanassist.model.DaiBanGongWuEntry
import com.example.yuanassist.model.DaiBanGongWuOption
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.TaskParams
import com.example.yuanassist.utils.CloudScriptOverrideStore
import com.example.yuanassist.utils.DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.TemplateDelayOverrideStore
import com.google.gson.Gson

class BirdFoodRuntimeManager(
    private val service: AccessibilityService,
    private val onRunningChanged: (Boolean) -> Unit
) {

    companion object {
        private const val CONTROLLER_SCRIPT = "bird_food_controller.json"
        private const val RETURN_AFTER_RUN_DELAY_MS = 1000L
        private const val WU_ZHU_QIAN_SELECTION_ROI_X = 838f
        private const val WU_ZHU_QIAN_SELECTION_ROI_Y = 1150f
    }

    private val engine = AutoTaskEngine(service)
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    private var generation = 0L
    private var config: BirdFoodConfig? = null
    private var totalCompletedRuns = 0
    private var startTimeMs = 0L

    var isRunning = false
        private set

    fun prepare(config: BirdFoodConfig) {
        this.config = config
        engine.debugRoiEnabled = config.debugModeEnabled
        engine.debugScreenshotEnabled = config.saveDebugScreenshotsEnabled
        engine.verboseLoggingEnabled = true
        engine.diagnosticLoggingEnabled = config.debugModeEnabled
        engine.globalDelayOffsetMs = config.lowSpecDelayMs
        engine.setRunLogScope("刷鸟食", config.selectedTask.displayName)
    }

    fun start(): Boolean {
        val currentConfig = config ?: return false
        generation += 1
        handler.removeCallbacksAndMessages(null)
        totalCompletedRuns = 0
        startTimeMs = System.currentTimeMillis()
        isRunning = true
        onRunningChanged(true)
        RunLogger.clear()
        engine.logDiagnosticSessionStart()
        RunLogger.i(module = "刷鸟食", section = "总流程", message = "开始，任务=${currentConfig.selectedTask.displayName}")
        runNextPlan(generation)
        return true
    }

    fun stop(showToast: Boolean = false) {
        generation += 1
        handler.removeCallbacksAndMessages(null)
        if (engine.isRunning) engine.stop()
        isRunning = false
        onRunningChanged(false)
        if (showToast) {
            Toast.makeText(service, "鸟食任务已停止", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runNextPlan(generation: Long) {
        if (!isRunning || generation != this.generation) return
        if (shouldStopBeforeNextTask()) return
        val currentConfig = config ?: return
        val taskType = currentConfig.selectedTask
        val plan = loadPlan(CONTROLLER_SCRIPT)?.withBirdFoodTaskScript(taskType.scriptFileName)
        if (plan == null) {
            stopByFailure("无法加载脚本 $CONTROLLER_SCRIPT")
            return
        }
        RunLogger.i(module = "刷鸟食", section = taskType.displayName, message = "开始")
        engine.startPlan(
            plan = plan,
            onCompleted = { success, errorMsg ->
                if (!isRunning || generation != this.generation) return@startPlan
                when {
                    success -> handleTaskSuccess(taskType, generation)
                    else -> stopByFailure("${taskType.displayName} 执行失败：$errorMsg")
                }
            },
            initialVariables = buildScriptVariables(),
            scriptFileName = CONTROLLER_SCRIPT,
        )
    }

    private fun buildScriptVariables(): Map<String, String> {
        val currentConfig = config
        return mapOf(
            "auto_eat_enabled" to if (currentConfig?.autoEatEnabled == true) "1" else "0",
            "game_variant" to when (currentConfig?.taDeChuanWenOption?.name) {
                "DAIHAOYUAN" -> "daihaoyuan"
                else -> "ruyuan"
            }
        )
    }

    private fun DailyTaskPlan.withBirdFoodTaskScript(scriptFileName: String): DailyTaskPlan {
        return copy(
            tasks = tasks.map { task ->
                val params = task.params
                if (params?.script_name == "BIRD_FOOD_TASK_SCRIPT") {
                    task.copy(params = params.copy(script_name = scriptFileName))
                } else {
                    task
                }
            }
        )
    }

    private fun handleTaskSuccess(taskType: BirdFoodTaskType, generation: Long) {
        totalCompletedRuns += 1
        RunLogger.i(module = "刷鸟食", section = taskType.displayName, message = "完成，第${totalCompletedRuns}次")
        handler.postDelayed({
            if (!isRunning || generation != this.generation) return@postDelayed
            runNextPlan(generation)
        }, RETURN_AFTER_RUN_DELAY_MS)
    }

    private fun shouldStopBeforeNextTask(): Boolean {
        val currentConfig = config ?: return true
        return when (currentConfig.stopCondition) {
            BirdFoodStopCondition.RUN_COUNT -> {
                val maxRuns = currentConfig.maxRuns ?: return false
                if (totalCompletedRuns >= maxRuns) {
                    finishSuccessfully("已达到次数上限")
                    true
                } else {
                    false
                }
            }
            BirdFoodStopCondition.DURATION_MINUTES -> {
                val durationMinutes = currentConfig.maxDurationMinutes ?: return false
                val elapsed = System.currentTimeMillis() - startTimeMs
                if (elapsed >= durationMinutes * 60_000L) {
                    finishSuccessfully("已达到时长上限")
                    true
                } else {
                    false
                }
            }
            BirdFoodStopCondition.RESOURCE_EXHAUSTED -> false
        }
    }

    private fun finishSuccessfully(message: String) {
        RunLogger.i(module = "刷鸟食", section = "总流程", message = message)
        stop()
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
    }

    private fun stopByFailure(message: String) {
        RunLogger.e(module = "刷鸟食", section = "总流程", message = message)
        stop()
        Toast.makeText(service, message, Toast.LENGTH_LONG).show()
    }

    private fun loadPlan(fileName: String): DailyTaskPlan? {
        return try {
            val plan = CloudScriptOverrideStore.loadAssetPlanWithOverride(service, fileName, gson)
            TemplateDelayOverrideStore.applyToPlan(
                service,
                fileName,
                applyStartBattleDelayOverrides(fileName, customizePlan(fileName, plan))
            )
        } catch (t: Throwable) {
            RunLogger.e(module = "刷鸟食", section = "总流程", message = "加载脚本失败：$fileName", throwable = t)
            null
        }
    }

    private fun customizePlan(fileName: String, plan: DailyTaskPlan): DailyTaskPlan {
        val currentConfig = config ?: return plan
        val updatedTasks = plan.tasks.map { task ->
            when {
                fileName == BirdFoodTaskType.DAI_BAN_GONG_WU.scriptFileName &&
                    currentConfig.daiBanGongWuOption == DaiBanGongWuOption.WU_ZHU_QIAN -> {
                    task.copy(params = applyWuZhuQianSelectionRoi(task.params))
                }

                else -> task
            }
        }
        val customizedPlan = plan.copy(tasks = updatedTasks)
        if (fileName != BirdFoodTaskType.DAI_BAN_GONG_WU.scriptFileName) {
            return customizedPlan
        }
        return applyDaiBanGongWuSkipEntries(
            customizedPlan,
            currentConfig.skippedDaiBanGongWuEntries
        )
    }

    private fun applyWuZhuQianSelectionRoi(params: TaskParams?): TaskParams? {
        if (params == null) return null
        val updatedSteps = params.screenshot_steps?.map { step ->
            if (step.template_name == "xuanze.png") {
                step.copy(
                    roi = step.roi?.copy(
                        x = WU_ZHU_QIAN_SELECTION_ROI_X,
                        y = WU_ZHU_QIAN_SELECTION_ROI_Y
                    )
                )
            } else {
                step
            }
        }

        return params.copy(
            screenshot_steps = updatedSteps
        )
    }

    private fun applyDaiBanGongWuSkipEntries(
        plan: DailyTaskPlan,
        skippedEntries: Set<DaiBanGongWuEntry>
    ): DailyTaskPlan {
        if (skippedEntries.isEmpty()) return plan

        val allEntries = DaiBanGongWuEntry.values().toList()
        val enabledEntries = allEntries.filterNot { skippedEntries.contains(it) }
        if (enabledEntries.isEmpty()) return plan

        val enabledEntrySet = enabledEntries.toSet()
        val firstEnabledTaskId = enabledEntries.first().taskId
        val nextTaskIdByEntry = allEntries.associate { entry ->
            entry.taskId to findNextEnabledGongWuEntry(entry, allEntries, enabledEntrySet).taskId
        }
        val nextTaskIdByBranch = allEntries.associate { entry ->
            entry.branchValue to (nextTaskIdByEntry[entry.taskId] ?: firstEnabledTaskId)
        }

        return plan.copy(
            start_task_id = plan.start_task_id,
            tasks = plan.tasks.map { task ->
                when (task.id) {
                    2 -> task.copy(on_success = firstEnabledTaskId)
                    31, 32, 33, 34 -> {
                        val nextTaskId = nextTaskIdByEntry[task.id] ?: task.on_fail
                        task.copy(on_fail = nextTaskId)
                    }
                    99 -> task.copy(
                        params = task.params?.copy(branch_routes = nextTaskIdByBranch)
                    )
                    else -> task
                }
            }
        )
    }

    private fun findNextEnabledGongWuEntry(
        currentEntry: DaiBanGongWuEntry,
        allEntries: List<DaiBanGongWuEntry>,
        enabledEntries: Set<DaiBanGongWuEntry>
    ): DaiBanGongWuEntry {
        val startIndex = allEntries.indexOf(currentEntry)
        for (offset in 1..allEntries.size) {
            val nextEntry = allEntries[(startIndex + offset) % allEntries.size]
            if (enabledEntries.contains(nextEntry)) {
                return nextEntry
            }
        }
        return currentEntry
    }

    private fun applyStartBattleDelayOverrides(fileName: String, plan: DailyTaskPlan): DailyTaskPlan {
        if (fileName != BirdFoodTaskType.DAI_BAN_GONG_WU.scriptFileName) return plan
        val incrementMs = TemplateDelayOverrideStore.getIncrementMs(
            service,
            fileName,
            DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION
        )
        if (incrementMs <= 0L) return plan
        return plan.copy(
            tasks = plan.tasks.map { task ->
                if (task.action != "OCR") return@map task
                task.copy(delay = (task.delay + incrementMs).coerceAtLeast(0L))
            }
        )
    }
}

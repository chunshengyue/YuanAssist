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
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ROI
import com.example.yuanassist.model.TaskParams
import com.example.yuanassist.utils.BIRD_FOOD_NAV_TEST_TASK_KEY
import com.example.yuanassist.utils.DAI_BAN_GONG_WU_START_BATTLE_DELAY_OPTION
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.TemplateDelayOverrideStore
import com.google.gson.Gson

class BirdFoodRuntimeManager(
    private val service: AccessibilityService,
    private val onRunningChanged: (Boolean) -> Unit
) {

    companion object {
        private const val DEFAULT_COOL_DOWN_MS = 5 * 60 * 1000L + 10 * 1000L
        private const val SHORT_COOL_DOWN_MS = 35 * 1000L
        private const val RETURN_AFTER_TASK_DELAY_MS = 1000L
        private const val ENTRY_CHECK_DELAY_MS = 1200L
        private const val DEBUG_COOLDOWN_LOG_INTERVAL_MS = 30_000L
        private const val MAX_BACK_STEPS = 4
    }

    private val engine = AutoTaskEngine(service)
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    private var generation = 0L
    private var config: BirdFoodConfig? = null
    private var nextReadyAtMs: Long? = null
    private var cooldownAwaitingReadyLog = false
    private var totalCompletedRuns = 0
    private var startTimeMs = 0L

    private fun cooldownMsFor(taskType: BirdFoodTaskType): Long {
        return when (taskType) {
            BirdFoodTaskType.TU_FA_QING_KUANG,
            BirdFoodTaskType.XIAO_DAO_XIAO_XI -> SHORT_COOL_DOWN_MS
            else -> DEFAULT_COOL_DOWN_MS
        }
    }

    var isRunning = false
        private set

    fun prepare(config: BirdFoodConfig) {
        this.config = config
        engine.debugRoiEnabled = config.debugModeEnabled
        engine.debugScreenshotEnabled = config.saveDebugScreenshotsEnabled
        engine.verboseLoggingEnabled = true
        engine.diagnosticLoggingEnabled = config.debugModeEnabled
        engine.globalDelayOffsetMs = config.lowSpecDelayMs
    }

    fun start(): Boolean {
        config ?: return false
        generation += 1
        handler.removeCallbacksAndMessages(null)
        nextReadyAtMs = null
        cooldownAwaitingReadyLog = false
        totalCompletedRuns = 0
        startTimeMs = System.currentTimeMillis()
        isRunning = true
        onRunningChanged(true)
        RunLogger.clear()
        engine.logDiagnosticSessionStart()
        RunLogger.i("鸟食任务运行开始")
        scheduleDebugCooldownSnapshot(generation)
        ensureYuanBaoScreen(0, generation) {
            scheduleNextTask(generation)
        }
        return true
    }

    fun stop(showToast: Boolean = false) {
        generation += 1
        handler.removeCallbacksAndMessages(null)
        if (engine.isRunning) engine.stop()
        nextReadyAtMs = null
        cooldownAwaitingReadyLog = false
        isRunning = false
        onRunningChanged(false)
        if (showToast) {
            Toast.makeText(service, "鸟食任务已停止", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureYuanBaoScreen(backAttempts: Int, generation: Long, onReady: () -> Unit) {
        if (!isRunning || generation != this.generation) return
        RunLogger.i("检查鸢报界面，第${backAttempts + 1}轮")

        detectTemplate(
            templateName = "tfqk.png",
            x = 170f,
            y = 1054f,
            align = "center",
            click = false,
            generation = generation,
            onDetected = {
                RunLogger.i("已确认当前处于鸢报界面")
                onReady()
            },
            onMissed = {
                RunLogger.i("未识别到突发情况按钮，尝试识别鸢报按钮")
                detectTemplate(
                    templateName = "yuanbao.png",
                    x = 441f,
                    y = 920f,
                    align = "center",
                    click = true,
                    generation = generation,
                    onDetected = {
                        RunLogger.i("识别到鸢报按钮，点击进入鸢报界面")
                        handler.postDelayed({
                            ensureYuanBaoScreen(0, generation, onReady)
                        }, ENTRY_CHECK_DELAY_MS)
                    },
                    onMissed = {
                        if (backAttempts >= MAX_BACK_STEPS) {
                            stopByFailure("无法返回鸢报界面")
                            return@detectTemplate
                        }
                        RunLogger.i("未识别到鸢报按钮，执行返回，第${backAttempts + 1}次")
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        handler.postDelayed({
                            ensureYuanBaoScreen(backAttempts + 1, generation, onReady)
                        }, ENTRY_CHECK_DELAY_MS)
                    }
                )
            }
        )
    }

    private fun scheduleNextTask(generation: Long) {
        if (!isRunning || generation != this.generation) return
        if (shouldStopBeforeNextTask()) return

        val taskType = config?.selectedTask ?: return

        val now = System.currentTimeMillis()
        emitCooldownReadyLogs(now, taskType)
        val readyAt = nextReadyAtMs ?: 0L
        if (now >= readyAt) {
            RunLogger.i("执行任务 ${taskType.displayName}")
            executeTask(taskType, generation)
            return
        }

        val delay = (readyAt - now).coerceAtLeast(300L)
        RunLogger.i("当前无可立即执行任务，等待 ${delay}ms 后重试调度")
        handler.postDelayed({
            scheduleNextTask(generation)
        }, delay)
    }

    private fun executeTask(taskType: BirdFoodTaskType, generation: Long) {
        if (!isRunning || generation != this.generation) return
        val now = System.currentTimeMillis()
        val readyAt = nextReadyAtMs ?: 0L
        if (now < readyAt) {
            val delay = (readyAt - now).coerceAtLeast(300L)
            RunLogger.i("${taskType.displayName} 仍在冷却中，${delay}ms 后再尝试调度")
            handler.postDelayed({
                scheduleNextTask(generation)
            }, delay)
            return
        }
        val plan = loadPlan(taskType.scriptFileName)
        if (plan == null) {
            stopByFailure("无法加载脚本 ${taskType.scriptFileName}")
            return
        }

        var terminalHandled = false
        var planCompletion: DailyPlanCompletion? = null
        engine.startPlan(
            plan = plan,
            onCompleted = { success, errorMsg ->
                if (!isRunning || generation != this.generation) return@startPlan
                if (success) {
                    handleTaskSuccess(taskType, generation, planCompletion?.cooldownStartedAtMs)
                } else if (!terminalHandled) {
                    stopByFailure("${taskType.name} 执行失败：$errorMsg")
                }
            },
            onCompletedDetailed = { result ->
                if (!isRunning || generation != this.generation) return@startPlan
                planCompletion = result
                when (result.terminalCode) {
                    -4 -> {
                        terminalHandled = true
                        handleTaskCoolingDown(taskType, result.terminalNote, generation, result.cooldownStartedAtMs)
                    }
                    -3 -> {
                        terminalHandled = true
                        handleTaskExhausted(taskType, result.terminalNote, generation)
                    }
                    -2 -> {
                        terminalHandled = true
                        stopByFailure(result.terminalNote ?: "${taskType.name} 执行失败")
                    }
                }
            },
            initialVariables = buildScriptVariables(),
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

    private fun handleTaskSuccess(
        taskType: BirdFoodTaskType,
        generation: Long,
        cooldownStartedAtMs: Long? = null
    ) {
        totalCompletedRuns += 1
        if (taskType.hasCooldown) {
            val cooldownMs = cooldownMsFor(taskType)
            val cooldownBase = cooldownStartedAtMs ?: System.currentTimeMillis()
            nextReadyAtMs = cooldownBase + cooldownMs
            cooldownAwaitingReadyLog = true
            RunLogger.i("【冷却开始】${taskType.displayName}，预计 ${cooldownMs / 1000} 秒后重试")
        } else {
            nextReadyAtMs = null
            cooldownAwaitingReadyLog = false
        }
        RunLogger.i("${taskType.name} 执行成功")
        handler.postDelayed({
            if (!isRunning || generation != this.generation) return@postDelayed
            RunLogger.i("任务结束后执行返回，准备回到鸢报界面")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            handler.postDelayed({
                ensureYuanBaoScreen(0, generation) {
                    scheduleNextTask(generation)
                }
            }, ENTRY_CHECK_DELAY_MS)
        }, RETURN_AFTER_TASK_DELAY_MS)
    }

    private fun handleTaskExhausted(
        taskType: BirdFoodTaskType,
        terminalNote: String?,
        generation: Long
    ) {
        nextReadyAtMs = null
        cooldownAwaitingReadyLog = false
        val message = terminalNote ?: "${taskType.name} 已耗尽"
        handler.postDelayed({
            ensureYuanBaoScreen(0, generation) {
                finishSuccessfully(message)
            }
        }, ENTRY_CHECK_DELAY_MS)
    }

    private fun handleTaskCoolingDown(
        taskType: BirdFoodTaskType,
        terminalNote: String?,
        generation: Long,
        cooldownStartedAtMs: Long? = null
    ) {
        if (!taskType.hasCooldown) {
            handler.postDelayed({
                ensureYuanBaoScreen(0, generation) {
                    scheduleNextTask(generation)
                }
            }, ENTRY_CHECK_DELAY_MS)
            return
        }
        val cooldownMs = cooldownMsFor(taskType)
        val cooldownBase = cooldownStartedAtMs ?: System.currentTimeMillis()
        nextReadyAtMs = cooldownBase + cooldownMs
        cooldownAwaitingReadyLog = true
        val message = terminalNote ?: "${taskType.displayName} 倒计时中，${formatDuration(cooldownMs)}后重试"
        RunLogger.i("【冷却开始】${taskType.displayName}，预计 ${cooldownMs / 1000} 秒后重试")
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
        handler.postDelayed({
            RunLogger.i("任务冷却处理中，返回鸢报界面等待下次执行")
            ensureYuanBaoScreen(0, generation) {
                scheduleNextTask(generation)
            }
        }, ENTRY_CHECK_DELAY_MS)
    }

    private fun scheduleDebugCooldownSnapshot(generation: Long) {
        if (config?.debugModeEnabled != true) return
        handler.postDelayed({
            if (!isRunning || generation != this.generation) return@postDelayed
            logCooldownSnapshot()
            scheduleDebugCooldownSnapshot(generation)
        }, DEBUG_COOLDOWN_LOG_INTERVAL_MS)
    }

    private fun logCooldownSnapshot() {
        val currentConfig = config ?: return
        val selectedTask = currentConfig.selectedTask
        val now = System.currentTimeMillis()
        val readyAt = nextReadyAtMs ?: return
        val hasPendingState = now < readyAt
        if (!hasPendingState) return
        emitCooldownReadyLogs(now, selectedTask)
        val snapshot = "${selectedTask.displayName}:${describeTaskState(now)}"
        RunLogger.i("【冷却状态】$snapshot")
    }

    private fun emitCooldownReadyLogs(now: Long, taskType: BirdFoodTaskType) {
        val readyAt = nextReadyAtMs ?: return
        if (cooldownAwaitingReadyLog && now >= readyAt) {
            RunLogger.i("【冷却结束】${taskType.displayName}，已恢复可执行")
            cooldownAwaitingReadyLog = false
        }
    }

    private fun describeTaskState(now: Long): String {
        val readyAt = nextReadyAtMs ?: return "就绪"
        if (now >= readyAt) return "就绪"
        return "冷却中(${formatDuration(readyAt - now)})"
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) + 999L) / 1000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
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
        RunLogger.i(message)
        stop()
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
    }

    private fun stopByFailure(message: String) {
        RunLogger.e(message)
        stop()
        Toast.makeText(service, message, Toast.LENGTH_LONG).show()
    }

    private fun detectTemplate(
        templateName: String,
        x: Float,
        y: Float,
        align: String,
        click: Boolean,
        generation: Long,
        onDetected: () -> Unit,
        onMissed: () -> Unit
    ) {
        val plan = DailyTaskPlan(
            start_task_id = 1,
            tasks = listOf(
                DailyTask(
                    id = 1,
                    action = "MATCH_TEMPLATE",
                    delay = 500,
                    params = TaskParams(
                        template_name = templateName,
                        threshold = 0.85f,
                        click = if (click) 1 else 0,
                        roi = ROI(x = x, y = y, w = 200f, h = 300f, align = align)
                    ),
                    on_success = -1,
                    on_fail = -2
                )
            )
        )

        engine.startPlan(
            plan = TemplateDelayOverrideStore.applyToPlan(service, BIRD_FOOD_NAV_TEST_TASK_KEY, plan),
            onCompleted = { success, _ ->
                if (!isRunning || generation != this.generation) return@startPlan
                if (success) {
                    RunLogger.i("界面检测成功：$templateName")
                    onDetected()
                } else {
                    RunLogger.i("界面检测失败：$templateName")
                    onMissed()
                }
            }
        )
    }

    private fun loadPlan(fileName: String): DailyTaskPlan? {
        return try {
            service.assets.open("daily_scripts/$fileName").use { input ->
                val plan = gson.fromJson(input.reader(), DailyTaskPlan::class.java)
                TemplateDelayOverrideStore.applyToPlan(
                    service,
                    fileName,
                    applyStartBattleDelayOverrides(fileName, customizePlan(fileName, plan))
                )
            }
        } catch (t: Throwable) {
            RunLogger.e("加载鸟食脚本失败：$fileName", t)
            null
        }
    }

    private fun customizePlan(fileName: String, plan: DailyTaskPlan): DailyTaskPlan {
        val currentConfig = config ?: return plan
        val updatedTasks = plan.tasks.map { task ->
            when {
                fileName == BirdFoodTaskType.DAI_BAN_GONG_WU.scriptFileName &&
                    currentConfig.daiBanGongWuOption == DaiBanGongWuOption.WU_ZHU_QIAN &&
                    task.params?.template_name == "xuanze.png" -> {
                    task.copy(
                        params = task.params.copy(
                            roi = task.params.roi?.copy(
                                x = 838f,
                                y = 1150f
                            )
                        )
                    )
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

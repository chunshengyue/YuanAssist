package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.yuanassist.model.BirdFoodConfig
import com.example.yuanassist.model.BirdFoodStopCondition
import com.example.yuanassist.model.BirdFoodTaskType
import com.example.yuanassist.model.DaiBanGongWuOption
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ROI
import com.example.yuanassist.model.TaskParams
import com.example.yuanassist.utils.RunLogger
import com.google.gson.Gson

class BirdFoodRuntimeManager(
    private val service: AccessibilityService,
    private val onRunningChanged: (Boolean) -> Unit
) {

    companion object {
        private const val COOL_DOWN_MS = 5 * 60 * 1000L + 10 * 1000L
        private const val RETURN_AFTER_TASK_DELAY_MS = 1000L
        private const val ENTRY_CHECK_DELAY_MS = 1200L
        private const val MAX_BACK_STEPS = 4
        private val TASK_EXECUTION_ORDER = listOf(
            BirdFoodTaskType.TU_FA_QING_KUANG,
            BirdFoodTaskType.XIAO_DAO_XIAO_XI,
            BirdFoodTaskType.DAI_BAN_GONG_WU,
            BirdFoodTaskType.TA_DE_CHUAN_WEN
        )
    }

    private val engine = AutoTaskEngine(service)
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    private var generation = 0L
    private var config: BirdFoodConfig? = null
    private val activeTasks = linkedSetOf<BirdFoodTaskType>()
    private val nextReadyAt = mutableMapOf<BirdFoodTaskType, Long>()
    private var totalCompletedRuns = 0
    private var startTimeMs = 0L

    var isRunning = false
        private set

    fun prepare(config: BirdFoodConfig) {
        this.config = config
        engine.debugRoiEnabled = config.debugModeEnabled
        engine.verboseLoggingEnabled = true
        engine.globalDelayOffsetMs = config.lowSpecDelayMs
    }

    fun start(): Boolean {
        val currentConfig = config ?: return false
        generation += 1
        handler.removeCallbacksAndMessages(null)
        activeTasks.clear()
        activeTasks.addAll(currentConfig.selectedTasks.distinct())
        nextReadyAt.clear()
        totalCompletedRuns = 0
        startTimeMs = System.currentTimeMillis()
        isRunning = true
        onRunningChanged(true)
        RunLogger.clear()
        RunLogger.i("鸟食任务运行开始")
        ensureYuanBaoScreen(0, generation)
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

    private fun ensureYuanBaoScreen(backAttempts: Int, generation: Long) {
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
                scheduleNextTask(generation)
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
                            ensureYuanBaoScreen(0, generation)
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
                            ensureYuanBaoScreen(backAttempts + 1, generation)
                        }, ENTRY_CHECK_DELAY_MS)
                    }
                )
            }
        )
    }

    private fun scheduleNextTask(generation: Long) {
        if (!isRunning || generation != this.generation) return
        if (shouldStopBeforeNextTask()) return

        if (activeTasks.isEmpty()) {
            finishSuccessfully("没有可执行的鸟食任务")
            return
        }

        val now = System.currentTimeMillis()
        val readyTask = TASK_EXECUTION_ORDER.firstOrNull { task ->
            activeTasks.contains(task) && now >= (nextReadyAt[task] ?: 0L)
        }

        if (readyTask != null) {
            RunLogger.i("调度任务 ${readyTask.displayName}")
            executeTask(readyTask, generation)
            return
        }

        val nearestReadyAt = activeTasks
            .mapNotNull { nextReadyAt[it] }
            .minOrNull()

        if (nearestReadyAt == null) {
            finishSuccessfully("没有可执行的鸟食任务")
            return
        }

        val delay = (nearestReadyAt - now).coerceAtLeast(300L)
        RunLogger.i("当前无可立即执行任务，等待 ${delay}ms 后重试调度")
        handler.postDelayed({
            scheduleNextTask(generation)
        }, delay)
    }

    private fun executeTask(taskType: BirdFoodTaskType, generation: Long) {
        if (!isRunning || generation != this.generation) return
        val now = System.currentTimeMillis()
        val readyAt = nextReadyAt[taskType] ?: 0L
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
            treatFailMinusOneAsSuccess = true
        )
    }

    private fun buildScriptVariables(): Map<String, String> {
        return mapOf(
            "auto_eat_enabled" to if (config?.autoEatEnabled == true) "1" else "0"
        )
    }

    private fun handleTaskSuccess(
        taskType: BirdFoodTaskType,
        generation: Long,
        cooldownStartedAtMs: Long? = null
    ) {
        totalCompletedRuns += 1
        if (taskType.hasCooldown) {
            val cooldownBase = cooldownStartedAtMs ?: System.currentTimeMillis()
            nextReadyAt[taskType] = cooldownBase + COOL_DOWN_MS
            RunLogger.i("${taskType.displayName} 冷却开始，预计 ${COOL_DOWN_MS / 1000} 秒后重试")
        }
        RunLogger.i("${taskType.name} 执行成功")
        handler.postDelayed({
            if (!isRunning || generation != this.generation) return@postDelayed
            RunLogger.i("任务结束后执行返回，准备回到鸢报界面")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            handler.postDelayed({
                ensureYuanBaoScreen(0, generation)
            }, ENTRY_CHECK_DELAY_MS)
        }, RETURN_AFTER_TASK_DELAY_MS)
    }

    private fun handleTaskExhausted(
        taskType: BirdFoodTaskType,
        terminalNote: String?,
        generation: Long
    ) {
        activeTasks.remove(taskType)
        val message = terminalNote ?: "${taskType.name} 已耗尽"
        RunLogger.i(message)
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
        handler.postDelayed({
            ensureYuanBaoScreen(0, generation)
        }, ENTRY_CHECK_DELAY_MS)
    }

    private fun handleTaskCoolingDown(
        taskType: BirdFoodTaskType,
        terminalNote: String?,
        generation: Long,
        cooldownStartedAtMs: Long? = null
    ) {
        val cooldownBase = cooldownStartedAtMs ?: System.currentTimeMillis()
        val newReadyAt = cooldownBase + COOL_DOWN_MS
        nextReadyAt[taskType] = newReadyAt
        val message = terminalNote ?: "${taskType.displayName} 倒计时中，5分10秒后重试"
        RunLogger.i(message)
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
        handler.postDelayed({
            RunLogger.i("任务冷却处理中，返回鸢报界面继续调度其他任务")
            ensureYuanBaoScreen(0, generation)
        }, ENTRY_CHECK_DELAY_MS)
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
                    delay = 300,
                    params = TaskParams(
                        template_name = templateName,
                        threshold = 0.85f,
                        click = if (click) 1 else 0,
                        roi = ROI(x = x, y = y, w = 200f, h = 300f, align = align)
                    ),
                    on_success = -1,
                    on_fail = -1
                )
            )
        )

        engine.startPlan(
            plan = plan,
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
                customizePlan(fileName, plan)
            }
        } catch (t: Throwable) {
            RunLogger.e("加载鸟食脚本失败：$fileName", t)
            null
        }
    }

    private fun customizePlan(fileName: String, plan: DailyTaskPlan): DailyTaskPlan {
        val currentConfig = config ?: return plan
        if (fileName != BirdFoodTaskType.DAI_BAN_GONG_WU.scriptFileName) return plan
        if (currentConfig.daiBanGongWuOption != DaiBanGongWuOption.WU_ZHU_QIAN) return plan

        val updatedTasks = plan.tasks.map { task ->
            if (task.params?.template_name == "xuanze.png") {
                task.copy(
                    params = task.params.copy(
                        roi = task.params.roi?.copy(
                            x = 838f,
                            y = 1150f
                        )
                    )
                )
            } else {
                task
            }
        }
        return plan.copy(tasks = updatedTasks)
    }
}

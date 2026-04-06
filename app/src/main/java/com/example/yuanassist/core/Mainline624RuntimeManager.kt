package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.Mainline624GameVariant
import com.example.yuanassist.model.ROI
import com.example.yuanassist.model.Mainline624Config
import com.example.yuanassist.model.TaskParams
import com.example.yuanassist.utils.RunLogger
import com.google.gson.Gson

class Mainline624RuntimeManager(
    private val service: AccessibilityService,
    private val onRunningChanged: (Boolean) -> Unit
) {

    companion object {
        private const val SCRIPT_FILE_NAME = "zhu_xian_6_24.json"
        private const val LOOP_START_TASK_ID = 15
        private const val LOOP_ENTRY_TASK_ID = 15
        private const val NEXT_RUN_DELAY_MS = 1500L
    }

    private val engine = AutoTaskEngine(service)
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    private var generation = 0L
    private var config: Mainline624Config? = null
    private var completedRuns = 0

    var isRunning = false
        private set

    fun prepare(config: Mainline624Config) {
        this.config = config
        engine.debugRoiEnabled = config.debugModeEnabled
        engine.verboseLoggingEnabled = true
        engine.globalDelayOffsetMs = config.lowSpecDelayMs
    }

    fun start(): Boolean {
        val currentConfig = config ?: return false
        generation += 1
        handler.removeCallbacksAndMessages(null)
        completedRuns = 0
        isRunning = true
        onRunningChanged(true)
        RunLogger.clear()
        val targetSummary = currentConfig.maxRuns?.let { "目标次数=$it" } ?: "运行至体力耗尽"
        RunLogger.i("6-24任务运行开始，$targetSummary")
        executeEntryPhase(generation)
        return true
    }

    fun stop(showToast: Boolean = false) {
        generation += 1
        handler.removeCallbacksAndMessages(null)
        if (engine.isRunning) engine.stop()
        isRunning = false
        onRunningChanged(false)
        if (showToast) {
            Toast.makeText(service, "6-24任务已停止", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeEntryPhase(generation: Long) {
        if (!isRunning || generation != this.generation) return
        RunLogger.i("开始执行6-24进图流程")
        engine.startPlan(
            plan = buildEntryPlan(),
            onCompleted = { success, errorMsg ->
                if (!isRunning || generation != this.generation) return@startPlan
                if (success) {
                    executeNextRun(generation)
                } else {
                    stopByFailure("6-24进图失败：$errorMsg")
                }
            }
        )
    }

    private fun executeNextRun(generation: Long) {
        if (!isRunning || generation != this.generation) return
        val currentConfig = config ?: run {
            stopByFailure("6-24配置缺失")
            return
        }
        val maxRuns = currentConfig.maxRuns
        if (maxRuns != null && completedRuns >= maxRuns) {
            finishSuccessfully("已达到次数上限")
            return
        }

        val plan = loadLoopPlan()
        if (plan == null) {
            stopByFailure("无法加载脚本 $SCRIPT_FILE_NAME")
            return
        }

        val currentRound = completedRuns + 1
        val roundSummary = maxRuns?.let { "第${currentRound}/$it 轮" } ?: "第${currentRound}轮"
        RunLogger.i("调度任务 6-24")
        RunLogger.i("开始执行 6-24 $roundSummary")
        engine.startPlan(
            plan = plan,
            onCompleted = { success, errorMsg ->
                if (!isRunning || generation != this.generation) return@startPlan
                if (success) {
                    handleRunSuccess(generation)
                } else {
                    stopByFailure("6-24执行失败：$errorMsg")
                }
            }
        )
    }

    private fun handleRunSuccess(generation: Long) {
        completedRuns += 1
        RunLogger.i("6-24第${completedRuns}轮执行成功")
        val currentConfig = config
        val maxRuns = currentConfig?.maxRuns
        if (maxRuns != null && completedRuns >= maxRuns) {
            finishSuccessfully("已达到次数上限")
            return
        }
        handler.postDelayed({
            executeNextRun(generation)
        }, NEXT_RUN_DELAY_MS)
    }

    private fun loadPlan(): DailyTaskPlan? {
        return try {
            service.assets.open("daily_scripts/$SCRIPT_FILE_NAME").use { input ->
                gson.fromJson(input.reader(), DailyTaskPlan::class.java)
            }
        } catch (t: Throwable) {
            RunLogger.e("加载6-24脚本失败：$SCRIPT_FILE_NAME", t)
            null
        }
    }

    private fun buildEntryPlan(): DailyTaskPlan {
        val currentConfig = config

        // 1. 判断是否为代号鸢
        val isDaiHaoYuan = currentConfig?.gameVariant == Mainline624GameVariant.CODE_NAME_YUAN

        // 2. 动态决定找到 gushi 后的下一步。
        // 如果是代号鸢，去找幽州 (Task 99)；如果是如鸢，直接去滑动页面 (Task 10)。
        val onGushiSuccess = if (isDaiHaoYuan) 99 else 10

        val tasks = mutableListOf<DailyTask>()

        // 3. 添加找故事的重试链路 (注意 onSuccess 全部改为了动态的 onGushiSuccess)
        tasks.add(templateTask(id = 1, delay = 500, templateName = "gushi.png", threshold = 0.85f, click = 1, roi = centerRoi(907f, 1253f, 300f, 300f), onSuccess = onGushiSuccess, onFail = 2))
        tasks.add(backTask(id = 2, onSuccess = 3))
        tasks.add(templateTask(id = 3, delay = 1200, templateName = "gushi.png", threshold = 0.85f, click = 1, roi = centerRoi(907f, 1253f, 300f, 300f), onSuccess = onGushiSuccess, onFail = 4))
        tasks.add(backTask(id = 4, onSuccess = 5))
        tasks.add(templateTask(id = 5, delay = 1200, templateName = "gushi.png", threshold = 0.85f, click = 1, roi = centerRoi(907f, 1253f, 300f, 300f), onSuccess = onGushiSuccess, onFail = 6))
        tasks.add(backTask(id = 6, onSuccess = 7))
        tasks.add(templateTask(id = 7, delay = 1200, templateName = "gushi.png", threshold = 0.85f, click = 1, roi = centerRoi(907f, 1253f, 300f, 300f), onSuccess = onGushiSuccess, onFail = 8))
        tasks.add(backTask(id = 8, onSuccess = 9))
        tasks.add(templateTask(id = 9, delay = 1200, templateName = "gushi.png", threshold = 0.85f, click = 1, roi = centerRoi(907f, 1253f, 300f, 300f), terminalNote = "未找到故事入口，请确认当前是否在主页", onSuccess = onGushiSuccess, onFail = -2))

        // 4. 如果是代号鸢，插入幽州识别任务
        if (isDaiHaoYuan) {
            tasks.add(
                templateTask(
                    id = 99,
                    delay = 1500,
                    templateName = "youzhou.png",
                    threshold = 0.75f, // 根据你的要求设为 0.75
                    click = 1, // 默认点击进入，如果你只需要纯视觉校验不需要点击，把这里改成 null
                    roi = topRoi(963f, 236f, 400f, 400f), // 同 tiaoguo 的位置，宽高设为 400
                    onSuccess = 10, // 幽州识别完后，继续去执行 ID: 10 的滑动操作
                    onFail = 100
                )
            )
            tasks.add(
                templateTask(
                    id = 100,
                    delay = 1500,
                    templateName = "jinru.png",
                    threshold = 0.75f,
                    click = 1,
                    roi = centerRoi(540f, 1440f, 1080f, 960f),
                    terminalNote = "未找到幽州或进入入口，任务终止",
                    onSuccess = 10,
                    onFail = -2
                )
            )
        }

        // 5. 添加后续的滑动和寻找第六章逻辑
        tasks.add(swipeTask(id = 10, delay = 1500, onSuccess = 11))
        tasks.add(swipeTask(id = 11, delay = 1000, onSuccess = 12))
        tasks.add(templateTask(id = 12, delay = 1200, templateName = "chapter6.png", threshold = 0.85f, click = 1, terminalNote = "未找到第六章入口，请确认当前是否在主线任务界面", onSuccess = 13, onFail = -2))
        tasks.add(swipeTask(id = 13, delay = 1200, onSuccess = 14))
        tasks.add(swipeTask(id = 14, delay = 1000, onSuccess = -1))

        return DailyTaskPlan(
            start_task_id = 1,
            tasks = tasks
        )
    }

    private fun loadLoopPlan(): DailyTaskPlan? {
        val plan = loadPlan() ?: return null
        return applyGameVariant(plan.copy(start_task_id = LOOP_START_TASK_ID))
    }

    private fun applyGameVariant(plan: DailyTaskPlan): DailyTaskPlan {
        val currentConfig = config ?: return plan
        val entryTemplateName = currentConfig.gameVariant.entryTemplateName
        val updatedTasks = plan.tasks.map { task ->
            if (task.id != LOOP_ENTRY_TASK_ID) return@map task
            val params = task.params ?: return@map task
            task.copy(
                params = params.copy(template_name = entryTemplateName)
            )
        }
        return plan.copy(tasks = updatedTasks)
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

    private fun templateTask(
        id: Int,
        delay: Long,
        templateName: String,
        threshold: Float,
        click: Int? = null,
        roi: ROI? = null,
        terminalNote: String? = null,
        onSuccess: Int,
        onFail: Int
    ): DailyTask {
        return DailyTask(
            id = id,
            action = "MATCH_TEMPLATE",
            delay = delay,
            params = TaskParams(
                template_name = templateName,
                threshold = threshold,
                click = click,
                roi = roi,
                terminal_note = terminalNote
            ),
            on_success = onSuccess,
            on_fail = onFail
        )
    }

    private fun backTask(id: Int, onSuccess: Int): DailyTask {
        return DailyTask(
            id = id,
            action = "BACK",
            delay = 1000,
            params = TaskParams(),
            on_success = onSuccess,
            on_fail = -1
        )
    }

    private fun swipeTask(id: Int, delay: Long, onSuccess: Int): DailyTask {
        return DailyTask(
            id = id,
            action = "SWIPE",
            delay = delay,
            params = TaskParams(
                startX = 540f,
                startY = 960f,
                endX = 540f,
                endY = 1680f,
                duration = 1000L,
                align = "center"
            ),
            on_success = onSuccess,
            on_fail = -1
        )
    }

    private fun centerRoi(x: Float, y: Float, w: Float, h: Float): ROI {
        return ROI(
            x = x,
            y = y,
            w = w,
            h = h,
            align = "center"
        )
    }
    private fun topRoi(x: Float, y: Float, w: Float, h: Float): ROI {
        return ROI(
            x = x,
            y = y,
            w = w,
            h = h,
            align = "top"
        )
    }
}

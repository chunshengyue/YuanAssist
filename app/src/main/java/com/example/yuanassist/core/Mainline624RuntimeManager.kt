package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.Mainline624Config
import com.example.yuanassist.utils.MAINLINE_624_START_BATTLE_DELAY_OPTION
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.TemplateDelayOverrideStore
import com.google.gson.Gson

class Mainline624RuntimeManager(
    private val service: AccessibilityService,
    private val onRunningChanged: (Boolean) -> Unit
) {

    companion object {
        private const val SCRIPT_FILE_NAME = "zhu_xian_6_24.json"
        private const val ENTRY_START_TASK_ID = 1
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
        engine.diagnosticLoggingEnabled = config.debugModeEnabled
        engine.globalDelayOffsetMs = config.lowSpecDelayMs
        engine.setRunLogScope("刷6-24", "脚本节点")
    }

    fun start(): Boolean {
        val currentConfig = config ?: return false
        generation += 1
        handler.removeCallbacksAndMessages(null)
        completedRuns = 0
        isRunning = true
        onRunningChanged(true)
        RunLogger.clear()
        engine.logDiagnosticSessionStart()
        val targetSummary = currentConfig.maxRuns?.let { "目标次数=$it" } ?: "运行至体力耗尽"
        RunLogger.i(module = "刷6-24", section = "总流程", message = "开始，$targetSummary")
        executePlan(
            generation = generation,
            startTaskId = ENTRY_START_TASK_ID,
            section = "进图流程",
            startMessage = "开始",
            failurePrefix = "6-24进图失败",
            applyStartBattleDelay = false
        )
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

        val currentRound = completedRuns + 1
        val roundSummary = maxRuns?.let { "第${currentRound}/$it 轮" } ?: "第${currentRound}轮"
        executePlan(
            generation = generation,
            startTaskId = ENTRY_START_TASK_ID,
            section = "战斗轮次",
            startMessage = "开始 $roundSummary",
            failurePrefix = "6-24执行失败",
            applyStartBattleDelay = true
        )
    }

    private fun executePlan(
        generation: Long,
        startTaskId: Int,
        section: String,
        startMessage: String,
        failurePrefix: String,
        applyStartBattleDelay: Boolean
    ) {
        if (!isRunning || generation != this.generation) return
        RunLogger.i(module = "刷6-24", section = section, message = startMessage)
        val plan = loadPlan(startTaskId, applyStartBattleDelay)
        if (plan == null) {
            stopByFailure("无法加载脚本 $SCRIPT_FILE_NAME")
            return
        }
        engine.startPlan(
            plan = plan,
            onCompleted = { success, errorMsg ->
                if (!isRunning || generation != this.generation) return@startPlan
                if (success) {
                    handleRunSuccess(generation)
                } else {
                    stopByFailure("$failurePrefix：$errorMsg")
                }
            },
            initialVariables = buildScriptVariables(),
            scriptFileName = SCRIPT_FILE_NAME,
        )
    }

    private fun handleRunSuccess(generation: Long) {
        completedRuns += 1
        RunLogger.i(module = "刷6-24", section = "战斗轮次", message = "第${completedRuns}轮完成")
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

    private fun loadPlan(startTaskId: Int, applyStartBattleDelay: Boolean): DailyTaskPlan? {
        val plan = try {
            service.assets.open("daily_scripts/$SCRIPT_FILE_NAME").use { input ->
                gson.fromJson(input.reader(), DailyTaskPlan::class.java)
            }
        } catch (t: Throwable) {
            RunLogger.e(module = "刷6-24", section = "总流程", message = "加载脚本失败：$SCRIPT_FILE_NAME", throwable = t)
            null
        } ?: return null
        val overridden = TemplateDelayOverrideStore.applyToPlan(
            service,
            SCRIPT_FILE_NAME,
            plan.copy(start_task_id = startTaskId)
        )
        return if (applyStartBattleDelay) {
            applyStartBattleDelayOverrides(overridden)
        } else {
            overridden
        }
    }

    private fun buildScriptVariables(): Map<String, String> =
        mapOf(
            "game_variant" to when (config?.gameVariant?.name) {
                "CODE_NAME_YUAN" -> "daihaoyuan"
                else -> "ruyuan"
            }
        )

    private fun applyStartBattleDelayOverrides(plan: DailyTaskPlan): DailyTaskPlan {
        val incrementMs = TemplateDelayOverrideStore.getIncrementMs(
            service,
            SCRIPT_FILE_NAME,
            MAINLINE_624_START_BATTLE_DELAY_OPTION
        )
        if (incrementMs <= 0L) return plan
        return plan.copy(
            tasks = plan.tasks.map { task ->
                if (task.action != "OCR") return@map task
                task.copy(delay = (task.delay + incrementMs).coerceAtLeast(0L))
            }
        )
    }

    private fun finishSuccessfully(message: String) {
        RunLogger.i(module = "刷6-24", section = "总流程", message = message)
        stop()
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
    }

    private fun stopByFailure(message: String) {
        RunLogger.e(module = "刷6-24", section = "总流程", message = message)
        stop()
        Toast.makeText(service, message, Toast.LENGTH_LONG).show()
    }

}

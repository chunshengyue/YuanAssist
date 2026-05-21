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
        private const val LOOP_START_TASK_ID = 15
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
        val plan = loadEntryPlan()
        if (plan == null) {
            stopByFailure("无法加载6-24进图脚本 $SCRIPT_FILE_NAME")
            return
        }
        engine.startPlan(
            plan = plan,
            onCompleted = { success, errorMsg ->
                if (!isRunning || generation != this.generation) return@startPlan
                if (success) {
                    handleRunSuccess(generation)
                } else {
                    stopByFailure("6-24进图失败：$errorMsg")
                }
            },
            initialVariables = buildScriptVariables()
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
            },
            initialVariables = buildScriptVariables()
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

    private fun loadLoopPlan(): DailyTaskPlan? {
        val plan = loadPlan() ?: return null
        return applyStartBattleDelayOverrides(
            TemplateDelayOverrideStore.applyToPlan(
                service,
                SCRIPT_FILE_NAME,
                plan.copy(start_task_id = LOOP_START_TASK_ID)
            )
        )
    }

    private fun loadEntryPlan(): DailyTaskPlan? {
        val plan = loadPlan() ?: return null
        return TemplateDelayOverrideStore.applyToPlan(
            service,
            SCRIPT_FILE_NAME,
            plan.copy(start_task_id = 1)
        )
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
        RunLogger.i(message)
        stop()
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
    }

    private fun stopByFailure(message: String) {
        RunLogger.e(message)
        stop()
        Toast.makeText(service, message, Toast.LENGTH_LONG).show()
    }

}

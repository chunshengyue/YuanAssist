package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.PiJingZhanJiConfig
import com.example.yuanassist.model.PiJingZhanJiStargazingMode
import com.example.yuanassist.model.PiJingZhanJiTaskConfig
import com.example.yuanassist.model.PiJingZhanJiTaskType
import com.example.yuanassist.utils.DialogUtils
import com.example.yuanassist.utils.RunLogger
import com.google.gson.Gson
import java.io.InputStreamReader

class PiJingZhanJiRuntimeManager(
    private val service: AccessibilityService,
    private val onRunningChanged: (Boolean) -> Unit
) {

    private val engine = AutoTaskEngine(service)
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private var activityEngine: PiJingZhanJiActivityEngine? = null
    private var config: PiJingZhanJiConfig? = null
    private var runningTasks: List<PiJingZhanJiTaskConfig> = emptyList()
    private var currentTaskIndex = 0
    private var stargazingRemainingCount = 0
    private val failedTaskNames = mutableListOf<String>()
    private var firstModuleContinuationRunnable: Runnable? = null
    private var firstModuleFailureDialog: AlertDialog? = null

    var isRunning = false
        private set

    fun prepare(config: PiJingZhanJiConfig) {
        this.config = config
        engine.debugRoiEnabled = config.debugModeEnabled
        engine.verboseLoggingEnabled = true
        engine.diagnosticLoggingEnabled = config.debugModeEnabled
        engine.globalDelayOffsetMs = config.lowSpecDelayMs
        engine.setRunLogScope("披荆斩棘", "前置任务")
    }

    fun start(): Boolean {
        val currentConfig = config ?: return false
        if (currentConfig.tasks.isEmpty() && !currentConfig.enableActivityModule) return false
        runningTasks = currentConfig.tasks
        currentTaskIndex = 0
        failedTaskNames.clear()
        clearFirstModuleFailurePrompt()
        isRunning = true
        onRunningChanged(true)
        RunLogger.clear()
        RunLogger.i(module = "披荆斩棘", section = "总流程", message = "开始，前置任务${runningTasks.size}项")
        val started = if (runningTasks.isEmpty()) {
            startActivityModule(currentConfig)
        } else {
            executeTaskAt(0, currentConfig)
        }
        if (!started) {
            isRunning = false
            runningTasks = emptyList()
            currentTaskIndex = 0
            onRunningChanged(false)
        }
        return started
    }

    fun stop(showToast: Boolean = false) {
        isRunning = false
        onRunningChanged(false)
        engine.stop()
        activityEngine?.stop()
        activityEngine = null
        clearFirstModuleFailurePrompt()
        failedTaskNames.clear()
        runningTasks = emptyList()
        currentTaskIndex = 0
        if (showToast) {
            Toast.makeText(service, "披荆斩棘任务已停止", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishAllTasks() {
        clearFirstModuleFailurePrompt()
        isRunning = false
        runningTasks = emptyList()
        currentTaskIndex = 0
        onRunningChanged(false)
        val message = if (failedTaskNames.isNotEmpty()) {
            "披荆斩棘任务已完成，但有部分任务失败"
        } else {
            "披荆斩棘任务已完成"
        }
        RunLogger.i(module = "披荆斩棘", section = "总流程", message = message)
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
        failedTaskNames.clear()
    }

    private fun executeTaskAt(index: Int, config: PiJingZhanJiConfig): Boolean {
        val taskConfig = runningTasks.getOrNull(index) ?: return false
        if (taskConfig.type == PiJingZhanJiTaskType.GUAN_XING_WU_ZHU_QIAN) {
            return executeStargazingTask(index, config, taskConfig)
        }
        val plan = loadPlan(taskConfig.type.scriptFileName) ?: return false
        currentTaskIndex = index
        engine.setRunLogScope("披荆斩棘", taskConfig.type.displayName)
        RunLogger.i(module = "披荆斩棘", section = taskConfig.type.displayName, message = "开始（${index + 1}/${runningTasks.size}）")
        engine.startPlan(
            plan = plan,
            onCompleted = { success, errorMsg ->
                if (!isRunning) return@startPlan
                if (!success) {
                    handleFirstModuleTaskFailure(taskConfig, errorMsg, index, config)
                    return@startPlan
                }
                proceedToNextTaskOrFinish(index, config)
            },
            initialVariables = buildVariables(config, taskConfig),
            scriptFileName = taskConfig.type.scriptFileName,
        )
        return true
    }

    private fun executeStargazingTask(
        index: Int,
        config: PiJingZhanJiConfig,
        taskConfig: PiJingZhanJiTaskConfig
    ): Boolean {
        val mode = taskConfig.stargazingMode ?: return false
        val value = taskConfig.stargazingValue ?: return false
        currentTaskIndex = index
        stargazingRemainingCount = value
        return when (mode) {
            PiJingZhanJiStargazingMode.NO_MONTH_CARD ->
                executeNoMonthCardStargazing(index, config)
            PiJingZhanJiStargazingMode.MONTH_CARD ->
                executeMonthCardStargazing(index, config)
        }
    }

    private fun executeNoMonthCardStargazing(
        index: Int,
        config: PiJingZhanJiConfig
    ): Boolean {
        val plan = loadPlan("wu_yue_ka_guan_xing_batch.json") ?: return false
        val taskConfig = runningTasks.getOrNull(index) ?: return false
        val currentBatchCount = stargazingRemainingCount.coerceAtMost(30)
        if (currentBatchCount <= 0) {
            proceedToNextTaskOrFinish(index, config)
            return true
        }
        val startTaskId = if (stargazingRemainingCount == (runningTasks[index].stargazingValue ?: 0)) 0 else 5
        engine.setRunLogScope("披荆斩棘", "观星消耗五铢钱")
        RunLogger.i(module = "披荆斩棘", section = "观星消耗五铢钱", message = "无月卡，本批=$currentBatchCount")
        engine.startPlan(
            plan = plan.copy(start_task_id = startTaskId),
            onCompleted = { success, errorMsg ->
                if (!isRunning) return@startPlan
                if (!success) {
                    handleFirstModuleTaskFailure(taskConfig, errorMsg, index, config)
                    return@startPlan
                }
                stargazingRemainingCount -= currentBatchCount
                if (stargazingRemainingCount > 0) {
                    executeNoMonthCardStargazing(index, config)
                } else {
                    proceedToNextTaskOrFinish(index, config)
                }
            },
            initialVariables = buildMap {
                put("current_batch_count", currentBatchCount.toString())
                put("stargazing_click_x", "763.49")
                put("stargazing_click_y", "1318.2489")
                put("stargazing_click_align", "bottom")
                put("stargazing_click_interval", "1200")
            },
            scriptFileName = "wu_yue_ka_guan_xing_batch.json",
        )
        return true
    }

    private fun executeMonthCardStargazing(
        index: Int,
        config: PiJingZhanJiConfig
    ): Boolean {
        val plan = loadPlan("you_yue_ka_guan_xing_batch.json") ?: return false
        val taskConfig = runningTasks.getOrNull(index) ?: return false
        if (stargazingRemainingCount <= 0) {
            proceedToNextTaskOrFinish(index, config)
            return true
        }
        val startTaskId = if (stargazingRemainingCount == (runningTasks[index].stargazingValue ?: 0)) {
            0
        } else {
            5
        }
        engine.setRunLogScope("披荆斩棘", "观星消耗五铢钱")
        RunLogger.i(module = "披荆斩棘", section = "观星消耗五铢钱", message = "有月卡，剩余轮数=$stargazingRemainingCount")
        engine.startPlan(
            plan = plan.copy(start_task_id = startTaskId),
            onCompleted = { success, errorMsg ->
                if (!isRunning) return@startPlan
                if (!success) {
                    handleFirstModuleTaskFailure(taskConfig, errorMsg, index, config)
                    return@startPlan
                }
                stargazingRemainingCount -= 1
                if (stargazingRemainingCount > 0) {
                    executeMonthCardStargazing(index, config)
                } else {
                    proceedToNextTaskOrFinish(index, config)
                }
            },
            scriptFileName = "you_yue_ka_guan_xing_batch.json",
        )
        return true
    }

    private fun handleFirstModuleTaskFailure(
        taskConfig: PiJingZhanJiTaskConfig,
        errorMsg: String?,
        index: Int,
        config: PiJingZhanJiConfig
    ) {
        val taskName = taskConfig.type.displayName
        failedTaskNames += taskName
        val reason = errorMsg?.takeIf { it.isNotBlank() } ?: "脚本返回失败"
        RunLogger.e(module = "披荆斩棘", section = taskName, message = "失败，继续后续任务：$reason")
        Toast.makeText(service, "$taskName 失败，继续执行下一个任务", Toast.LENGTH_SHORT).show()
        proceedToNextTaskOrFinish(index, config)
    }

    private fun proceedToNextTaskOrFinish(index: Int, config: PiJingZhanJiConfig) {
        val nextIndex = index + 1
        if (nextIndex < runningTasks.size) {
            executeTaskAt(nextIndex, config)
            return
        }
        onFirstModuleFinished(config)
    }

    private fun onFirstModuleFinished(config: PiJingZhanJiConfig) {
        if (failedTaskNames.isEmpty()) {
            if (config.enableActivityModule) {
                startActivityModule(config)
            } else {
                finishAllTasks()
            }
            return
        }
        val failedSummary = failedTaskNames.joinToString("、")
        val suffix = if (config.enableActivityModule) {
            "5秒后自动关闭并进入活动页面"
        } else {
            "5秒后自动关闭"
        }
        RunLogger.i(module = "披荆斩棘", section = "前置任务", message = "第一模块结束，未完成：$failedSummary")
        showFirstModuleFailurePrompt("前置任务未完成：$failedSummary\n$suffix")
        val continuation = Runnable {
            clearFirstModuleFailurePrompt()
            if (!isRunning) return@Runnable
            if (config.enableActivityModule) {
                startActivityModule(config)
            } else {
                finishAllTasks()
            }
        }
        firstModuleContinuationRunnable = continuation
        handler.postDelayed(continuation, 5000L)
    }

    private fun showFirstModuleFailurePrompt(message: String) {
        clearFirstModuleFailurePrompt()
        val builder = AlertDialog.Builder(DialogUtils.getThemeContext(service))
            .setTitle("第一模块有未完成任务")
            .setMessage(message)
            .setCancelable(false)
        firstModuleFailureDialog = DialogUtils.safeShowOverlayDialog(builder)
    }

    private fun clearFirstModuleFailurePrompt() {
        firstModuleContinuationRunnable?.let(handler::removeCallbacks)
        firstModuleContinuationRunnable = null
        firstModuleFailureDialog?.dismiss()
        firstModuleFailureDialog = null
    }

    private fun startActivityModule(config: PiJingZhanJiConfig): Boolean {
        if (!config.enableActivityModule) return false
        activityEngine?.stop()
        val nextEngine = PiJingZhanJiActivityEngine(service, config) { success, errorMsg ->
            activityEngine = null
            if (!isRunning) return@PiJingZhanJiActivityEngine
            if (success) {
                finishAllTasks()
            } else {
                isRunning = false
                onRunningChanged(false)
                val message = "披荆斩棘任务已停止：$errorMsg"
                RunLogger.e(module = "披荆斩棘", section = "总流程", message = message)
                Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
            }
        }
        activityEngine = nextEngine
        return nextEngine.start()
    }

    private fun loadPlan(fileName: String): DailyTaskPlan? {
        return try {
            service.assets.open("daily_scripts/$fileName").use { input ->
                gson.fromJson(InputStreamReader(input, Charsets.UTF_8), DailyTaskPlan::class.java)
            }
        } catch (t: Throwable) {
            RunLogger.e(module = "披荆斩棘", section = "前置任务", message = "加载脚本失败：$fileName", throwable = t)
            null
        }
    }

    private fun buildVariables(
        config: PiJingZhanJiConfig,
        taskConfig: PiJingZhanJiTaskConfig
    ): Map<String, String> {
        val attributeList = taskConfig.selectedAttributes
        return buildMap {
            put(
                "game_variant",
                if (config.gameVariant.name == "CODE_NAME_YUAN") "daihaoyuan" else "ruyuan"
            )
            attributeList.forEachIndexed { index, value ->
                put("attribute_${index + 1}", attributeScriptSuffix(value))
            }
            put("attribute_count", attributeList.size.toString())
            if (taskConfig.type == PiJingZhanJiTaskType.MAINLINE_624_TWICE) {
                put("mainline_loop_start", "15")
            }
        }
    }

    private fun attributeScriptSuffix(value: String): String {
        return when (value.trim()) {
            "地" -> "di"
            "水" -> "shui"
            "火" -> "huo"
            "风" -> "feng"
            "阳" -> "yang"
            "阴" -> "yin"
            else -> value.trim()
        }
    }
}

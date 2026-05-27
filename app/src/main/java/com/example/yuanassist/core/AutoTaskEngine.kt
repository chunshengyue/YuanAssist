package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ROI
import com.example.yuanassist.model.ScreenshotStep
import com.example.yuanassist.model.TaskParams
import com.example.yuanassist.utils.BirdFoodDebugScreenshotStore
import com.example.yuanassist.utils.CloudScriptOverrideStore
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.StartBattleShared
import com.example.yuanassist.utils.TemplateOverrideStore
import com.example.yuanassist.tableocr.PaddleTextBlock
import com.example.yuanassist.tableocr.PaddleTextElement
import com.example.yuanassist.tableocr.PaddleTextLine
import com.example.yuanassist.tableocr.PaddleTextRecognizer
import com.example.yuanassist.tableocr.PaddleTextResult
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.abs
import kotlin.math.min

class AutoTaskEngine(private val service: AccessibilityService) {

    companion object {
        private const val BASE_W = 1080f
        private const val BASE_H = 1920f
    }

    var isRunning = false
    var lastMatchX = 0f
    var lastMatchY = 0f
    var debugRoiEnabled = true
    var debugScreenshotEnabled = false
    var verboseLoggingEnabled = true
    var diagnosticLoggingEnabled = false
    var globalDelayOffsetMs = 0L
    var runLogModule: String? = null
    var runLogSection: String? = null

    private var currentTaskPlan: DailyTaskPlan? = null
    private var currentTaskId = -1
    private var onPlanCompleted: ((Boolean, String) -> Unit)? = null
    private var onPlanCompletedDetailed: ((DailyPlanCompletion) -> Unit)? = null
    private var onTaskScheduled: ((DailyTask, Long) -> Unit)? = null
    private var customActionHandler: ((DailyTask, () -> Unit, () -> Unit) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager =
        service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private val matchedPointsByTaskId = mutableMapOf<Int, PointF>()
    private val variables = mutableMapOf<String, String>()
    private var runGeneration = 0L
    private var debugRoiView: View? = null
    private var cooldownStartedAtMs: Long? = null
    private var currentTemplateDir: File? = null
    private var currentAssetTemplateDir: String? = null
    private var currentScriptFileName: String? = null
    private var ocrScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private var childScriptEngine: AutoTaskEngine? = null

    private fun verboseInfo(message: String) {
        if (!verboseLoggingEnabled) return
        if (shouldSuppressInfoLog(message)) return
        logInfo(message)
    }

    private fun diagnosticInfo(message: String) {
        if (!diagnosticLoggingEnabled) return
        logInfo("[调试诊断] $message")
    }

    private fun diagnosticError(message: String) {
        if (!diagnosticLoggingEnabled) return
        logError("[调试诊断] $message")
    }

    fun setRunLogScope(module: String?, section: String? = null) {
        runLogModule = module?.trim()?.takeIf { it.isNotBlank() }
        runLogSection = section?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun logInfo(message: String) {
        val module = runLogModule
        if (module == null) {
            RunLogger.i(message)
        } else {
            RunLogger.i(module = module, section = runLogSection, message = message)
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        val module = runLogModule
        if (module == null) {
            if (throwable == null) RunLogger.e(message) else RunLogger.e(message, throwable)
        } else if (throwable == null) {
            RunLogger.e(module = module, section = runLogSection, message = message)
        } else {
            RunLogger.e(module = module, section = runLogSection, message = message, throwable = throwable)
        }
    }

    fun logDiagnosticSessionStart() {
        if (!diagnosticLoggingEnabled) return
        diagnosticInfo(
            "设备=${Build.BRAND} ${Build.MODEL} " +
                "Android=${Build.VERSION.RELEASE} SDK=${Build.VERSION.SDK_INT}"
        )
    }

    private fun shouldSuppressInfoLog(message: String): Boolean {
        return message.contains("[startPlan]") ||
            message.contains("引擎已启动") ||
            message.contains("引擎已被用户停止") ||
            message.contains("准备执行任务") ||
            message.contains("模板缓存命中") ||
            message.contains("模板已加载") ||
            message.contains("截图缩放")
    }
    private val templateCache = object : android.util.LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    private data class ScreenshotMapping(
        val screenshotToDisplayX: Float,
        val screenshotToDisplayY: Float,
        val displayToScreenshotX: Float,
        val displayToScreenshotY: Float,
        val orientationMismatch: Boolean,
        val nonUniformScale: Boolean,
        val displayWidth: Float,
        val displayHeight: Float,
        val screenshotWidth: Int,
        val screenshotHeight: Int
    )

    private data class SearchRegion(
        val bitmap: Bitmap,
        val offsetX: Float,
        val offsetY: Float,
        val ownsBitmap: Boolean
    )

    private data class StartBattleOcrHit(
        val lineText: String,
        val normalizedLineText: String,
        val hitChars: List<Char>,
        val center: PointF,
        val containsPhrase: Boolean,
        val area: Int
    ) {
        val hitCount: Int
            get() = hitChars.size
    }

    private data class OcrConfig(
        val targetChars: List<Char>,
        val minHitCount: Int,
        val roi: ROI?,
        val preprocess: String?,
        val templateName: String?,
        val threshold: Float,
        val clickOnSuccess: Boolean,
    )

    private data class OcrHit(
        val lineText: String,
        val normalizedLineText: String,
        val hitChars: List<Char>,
        val center: PointF,
        val area: Int
    ) {
        val hitCount: Int
            get() = hitChars.size
    }

    private data class ScreenshotMatch(
        val nextTaskId: Int,
        val clickOnSuccess: Boolean,
        val centerInScreenshot: PointF,
        val logMessage: String,
        val terminalNote: String? = null,
    )

    private data class TemplateMatchResult(
        val center: PointF,
        val score: Double,
    )

    fun startPlan(
        plan: DailyTaskPlan,
        onCompleted: (Boolean, String) -> Unit,
        onCustomAction: ((DailyTask, () -> Unit, () -> Unit) -> Unit)? = null,
        onCompletedDetailed: ((DailyPlanCompletion) -> Unit)? = null,
        onTaskScheduled: ((DailyTask, Long) -> Unit)? = null,
        initialVariables: Map<String, String> = emptyMap(),
        templateDir: File? = null,
        scriptFileName: String? = null
    ) {
        runGeneration += 1
        handler.removeCallbacksAndMessages(null)
        ocrScope.cancel()
        ocrScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        clearDebugRoi()
        currentTaskPlan = plan
        currentTaskId = plan.start_task_id
        isRunning = true
        matchedPointsByTaskId.clear()
        variables.clear()
        variables.putAll(initialVariables)
        lastMatchX = 0f
        lastMatchY = 0f
        cooldownStartedAtMs = null
        this.onPlanCompleted = onCompleted
        this.onPlanCompletedDetailed = onCompletedDetailed
        this.onTaskScheduled = onTaskScheduled
        customActionHandler = onCustomAction
        currentTemplateDir = templateDir
        currentAssetTemplateDir = plan.asset_template_dir?.trim()?.takeIf { it.isNotBlank() }
        currentScriptFileName = scriptFileName?.trim()?.takeIf { it.isNotBlank() }
        logDisplayMetrics("startPlan")
        verboseInfo("引擎已启动")
        executeNextTask()
    }

    fun stop() {
        runGeneration += 1
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        clearDebugRoi()
        childScriptEngine?.release()
        childScriptEngine = null
        matchedPointsByTaskId.clear()
        variables.clear()
        verboseInfo("引擎已被用户停止")
        completePlan(false, "已停止", -1)
    }

    fun release() {
        stop()
        ocrScope.cancel()
        currentTaskPlan = null
        currentTaskId = -1
        onPlanCompleted = null
        onPlanCompletedDetailed = null
        customActionHandler = null
        currentTemplateDir = null
        currentAssetTemplateDir = null
        currentScriptFileName = null
        childScriptEngine?.release()
        childScriptEngine = null
        templateCache.evictAll()
    }

    private fun completePlan(
        success: Boolean,
        message: String,
        terminalCode: Int,
        task: DailyTask? = null,
        terminalNote: String? = null
    ) {
        isRunning = false
        val detailedCallback = onPlanCompletedDetailed
        val completionCallback = onPlanCompleted
        val completionCooldownStartedAtMs = cooldownStartedAtMs
        onPlanCompletedDetailed = null
        onPlanCompleted = null
        onTaskScheduled = null
        customActionHandler = null
        cooldownStartedAtMs = null
        detailedCallback?.invoke(
            DailyPlanCompletion(
                success,
                message,
                terminalCode,
                task?.id ?: -1,
                terminalNote,
                completionCooldownStartedAtMs
            )
        )
        completionCallback?.invoke(success, message)
    }

    fun finishTask(task: DailyTask, isSuccess: Boolean) {
        val taskLabel = taskLogLabel(task)
        if (!isSuccess) {
            when (task.on_fail) {
                -4, -3, -2 -> {
                    val msg = task.params?.terminal_note
                        ?: when (task.on_fail) {
                            -4 -> "任务倒计时中"
                            -3 -> "资源耗尽"
                            else -> "任务失败"
                        }
                    if (task.on_fail == -4 || task.on_fail == -3) {
                        logInfo("$taskLabel：$msg")
                    } else {
                        logError("$taskLabel：$msg")
                    }
                    completePlan(false, msg, task.on_fail, task, task.params?.terminal_note)
                    return
                }
                -1 -> {
                    verboseInfo("$taskLabel 失败分支=-1，按正常结束处理")
                    completePlan(true, "已完成", -1, task, task.params?.terminal_note)
                    return
                }
                else -> {
                    val nextTaskId = resolveFailTaskId(task)
                    diagnosticError("$taskLabel 失败，跳转=${nextTaskId}")
                    currentTaskId = nextTaskId
                    executeNextTask()
                    return
                }
            }
        }

        if (task.start_cooldown_on_success && cooldownStartedAtMs == null) {
            cooldownStartedAtMs = System.currentTimeMillis()
            verboseInfo("$taskLabel 成功，开始记录冷却计时")
        }

        val nextTaskId = resolveSuccessTaskId(task)
        when (nextTaskId) {
            -4, -3, -2 -> {
                val msg = task.params?.terminal_note
                    ?: when (nextTaskId) {
                        -4 -> "任务倒计时中"
                        -3 -> "资源耗尽"
                        else -> "任务失败"
                    }
                if (nextTaskId == -4 || nextTaskId == -3) {
                    logInfo("$taskLabel：$msg")
                } else {
                    logError("$taskLabel：$msg")
                }
                completePlan(false, msg, nextTaskId, task, task.params?.terminal_note)
            }
            -1 -> {
                verboseInfo("$taskLabel 成功，计划完成")
                completePlan(true, "已完成", -1, task)
            }
            else -> {
                verboseInfo("$taskLabel 成功，下一步=$nextTaskId")
                currentTaskId = nextTaskId
                executeNextTask()
            }
        }
    }

    fun dispatchClick(x: Float, y: Float, onComplete: () -> Unit) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onComplete()
            override fun onCancelled(gestureDescription: GestureDescription?) {
                logError("点击手势被取消")
                onComplete()
            }
        }, null)
    }

    private fun executeNextTask() {
        if (!isRunning) return
        val generation = runGeneration
        val task = currentTaskPlan?.tasks?.find { it.id == currentTaskId }
        if (task == null) {
            val msg = "未找到任务:$currentTaskId"
            logError(msg)
            completePlan(false, msg, -1)
            return
        }

        val baseDelay = task.delay
        val effectiveDelay = (baseDelay + globalDelayOffsetMs).coerceAtLeast(0L)
        verboseInfo("准备执行${taskLogLabel(task)} ${task.action}，延迟=${baseDelay}+${globalDelayOffsetMs}=${effectiveDelay}")
        onTaskScheduled?.invoke(task, effectiveDelay)
        handler.postDelayed({
            if (!isRunning || generation != runGeneration) return@postDelayed
            try {
                when (task.action) {
                    "CLICK" -> executeClick(task)
                    "SWIPE" -> executeSwipe(task)
                    "BACK" -> executeGlobalBack(task)
                    "SET_VAR" -> executeSetVar(task)
                    "RUN_SCRIPT_SEGMENT" -> executeRunScriptSegment(task)
                    "CLICK_LAST_MATCH", "CLICK_LAST_OCR" -> executeContextClick(task)
                    "MATCH_TEMPLATE" -> executeMatchTemplate(task)
                    "OCR" -> executeOcrTask(task)
                    "SCREENSHOT_GROUP" -> executeScreenshotGroup(task)
                    else -> {
                        val custom = customActionHandler
                        if (custom != null) {
                            custom(task, { finishTask(task, true) }, { finishTask(task, false) })
                        } else {
                            logError("不支持的动作 ${task.action}")
                            finishTask(task, false)
                        }
                    }
                }
            } catch (t: Throwable) {
                logError("${taskLogLabel(task)} 执行崩溃 ${task.action}", t)
                finishTask(task, false)
            }
        }, effectiveDelay)
    }

    private fun taskLogLabel(task: DailyTask): String {
        val name = task.name?.trim()?.takeIf { it.isNotBlank() }
        return if (name == null) "任务 ${task.id}" else "任务 ${task.id}($name)"
    }

    private fun taskHumanLabel(taskId: Int, name: String? = null): String {
        val cleanName = name?.trim()?.takeIf { it.isNotBlank() }
        return cleanName ?: currentTaskPlan?.tasks?.firstOrNull { it.id == taskId }?.name
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "任务$taskId"
    }

    private fun taskHumanLabel(task: DailyTask): String = taskHumanLabel(task.id, task.name)

    private fun executeClick(task: DailyTask) {
        val generation = runGeneration
        val p = task.params ?: return finishTask(task, false)
        val repeatCount = resolveRepeatCount(p)
        val repeatInterval = resolveRepeatInterval(p)
        val refPoint = p.ref_task_id?.let { matchedPointsByTaskId[it] }
        if (refPoint != null) {
            verboseInfo("任务 ${task.id} 点击 x=${refPoint.x.toInt()} y=${refPoint.y.toInt()}")
            performRepeatedClicks(
                generation = generation,
                x = refPoint.x,
                y = refPoint.y,
                repeatCount = repeatCount,
                repeatInterval = repeatInterval
            ) { success ->
                if (generation == runGeneration && isRunning) finishTask(task, success)
            }
            return
        }
        if (p.x == null || p.y == null) return finishTask(task, false)
        val (realX, realY) = calculateRealCoordinate(p.x, p.y, p.align)
        verboseInfo("任务 ${task.id} 点击 x=${realX.toInt()} y=${realY.toInt()}")
        performRepeatedClicks(
            generation = generation,
            x = realX,
            y = realY,
            repeatCount = repeatCount,
            repeatInterval = repeatInterval
        ) { success ->
            if (generation == runGeneration && isRunning) finishTask(task, success)
        }
    }

    private fun resolveRepeatCount(params: com.example.yuanassist.model.TaskParams): Int? {
        params.repeat_count_var?.let { varName ->
            variables[varName]?.toIntOrNull()?.let { return it }
        }
        return params.repeat_count
    }

    private fun resolveRepeatInterval(params: com.example.yuanassist.model.TaskParams): Long? {
        params.repeat_interval_var?.let { varName ->
            variables[varName]?.toLongOrNull()?.let { return it }
        }
        return params.repeat_interval
    }

    private fun performRepeatedClicks(
        generation: Long,
        x: Float,
        y: Float,
        repeatCount: Int?,
        repeatInterval: Long?,
        onComplete: (Boolean) -> Unit
    ) {
        val totalCount = (repeatCount ?: 1).coerceAtLeast(1)
        val intervalMs = (repeatInterval ?: 0L).coerceAtLeast(0L)

        fun runClick(index: Int) {
            if (!isRunning || generation != runGeneration) {
                onComplete(false)
                return
            }
            dispatchClick(x, y) {
                if (!isRunning || generation != runGeneration) {
                    onComplete(false)
                    return@dispatchClick
                }
                if (index >= totalCount - 1) {
                    onComplete(true)
                    return@dispatchClick
                }
                handler.postDelayed(
                    { runClick(index + 1) },
                    intervalMs
                )
            }
        }

        runClick(0)
    }

    private fun executeSwipe(task: DailyTask) {
        val generation = runGeneration
        val p = task.params ?: return finishTask(task, false)
        if (p.startX == null || p.startY == null || p.endX == null || p.endY == null) {
            return finishTask(task, false)
        }
        val (sx, sy) = calculateRealCoordinate(p.startX, p.startY, p.align)
        val (ex, ey) = calculateRealCoordinate(p.endX, p.endY, p.align)
        val duration = p.duration ?: 300L
        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        verboseInfo("任务 ${task.id} 执行滑动")
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (generation == runGeneration && isRunning) finishTask(task, true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                logError("滑动手势被取消")
                if (generation == runGeneration && isRunning) finishTask(task, false)
            }
        }, null)
    }

    private fun executeContextClick(task: DailyTask) {
        val generation = runGeneration
        val refPoint = task.params?.ref_task_id?.let { matchedPointsByTaskId[it] }
        if (refPoint != null) {
            dispatchClick(refPoint.x, refPoint.y) {
                if (generation == runGeneration && isRunning) finishTask(task, true)
            }
            return
        }
        if (lastMatchX == 0f && lastMatchY == 0f) return finishTask(task, false)
        dispatchClick(lastMatchX, lastMatchY) {
            if (generation == runGeneration && isRunning) finishTask(task, true)
        }
    }

    private fun executeGlobalBack(task: DailyTask) {
        verboseInfo("任务 ${task.id} 执行全局返回")
        finishTask(task, service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
    }

    private fun executeSetVar(task: DailyTask) {
        val p = task.params ?: return finishTask(task, false)
        val name = p.var_name ?: return finishTask(task, false)
        val value = p.var_value ?: return finishTask(task, false)
        val resolvedValue = resolveVariablePlaceholders(value)
        variables[name] = resolvedValue
        verboseInfo("任务 ${task.id} 设置变量 $name=$resolvedValue")
        finishTask(task, true)
    }

    private fun executeRunScriptSegment(task: DailyTask) {
        val p = task.params ?: return finishTask(task, false)
        val scriptName = resolveScriptName(p) ?: return finishTask(task, false)
        val entryTaskId = resolveSegmentTaskId(p.entry_task_id, p.entry_task_id_var)
            ?: return finishTask(task, false)
        val exitTaskId = resolveSegmentTaskId(p.exit_task_id, p.exit_task_id_var)
        val childPlan = loadSegmentPlan(scriptName, entryTaskId, exitTaskId)
            ?: return finishTask(task, false)
        val childVariables = buildChildVariables(p)
        verboseInfo("任务 ${task.id} 调度脚本 $scriptName entry=$entryTaskId exit=${exitTaskId ?: "end"}")
        childScriptEngine?.release()
        childScriptEngine = AutoTaskEngine(service).also { child ->
            child.debugRoiEnabled = debugRoiEnabled
            child.debugScreenshotEnabled = debugScreenshotEnabled
            child.verboseLoggingEnabled = verboseLoggingEnabled
            child.diagnosticLoggingEnabled = diagnosticLoggingEnabled
            child.globalDelayOffsetMs = globalDelayOffsetMs
            child.setRunLogScope(runLogModule, runLogSection)
        }
        childScriptEngine?.startPlan(
            plan = childPlan,
            onCompleted = { success, _ ->
                if (!isRunning) return@startPlan
                childScriptEngine?.release()
                childScriptEngine = null
                finishTask(task, success)
            },
            onTaskScheduled = onTaskScheduled,
            initialVariables = childVariables,
            scriptFileName = scriptName,
        )
    }

    private fun executeOcrTask(task: DailyTask) {
        val p = task.params ?: return finishTask(task, false)
        val targetChars = p.target_chars
            ?.mapNotNull { it.trim().firstOrNull() }
            ?.distinct()
            .orEmpty()
        val targetText = (p.target_text ?: p.button_name)?.trim().orEmpty()
        val useStartBattlePreset = targetChars.isEmpty() && targetText.isBlank()
        val isExplicitStartBattleTarget = targetChars.size == 4 &&
            targetChars.containsAll(listOf('开', '始', '战', '斗'))
        val templateName = p.template_name
        val templateOverrideName = ocrTemplateOverrideName(task, p)
        if (templateOverrideName != null && TemplateOverrideStore.hasOverride(service, templateOverrideName)) {
            return executeTemplateSearch(
                task = task,
                templateName = templateOverrideName,
                threshold = p.threshold,
                clickOnSuccess = p.click == 1,
                logLabel = "OCR替换模板",
                roiOverride = p.roi,
            )
        }
        if (useStartBattlePreset && TemplateOverrideStore.hasOverride(service, TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME)) {
            return executeTemplateSearch(
                task = task,
                templateName = TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME,
                threshold = TemplateOverrideStore.START_BATTLE_TEMPLATE_THRESHOLD,
                clickOnSuccess = p.click != 0,
                logLabel = "开始战斗模板",
                roiOverride = StartBattleShared.buildRoi(),
            )
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            diagnosticError("任务 ${task.id} OCR截图不支持，当前 SDK=${Build.VERSION.SDK_INT}")
            return finishTask(task, false)
        }

        val config = OcrConfig(
            targetChars = targetChars,
            minHitCount = (p.min_hit_count ?: targetChars.size).coerceAtLeast(1),
            roi = if (useStartBattlePreset) StartBattleShared.buildRoi() else p.roi,
            preprocess = p.preprocess,
            templateName = templateName,
            threshold = p.threshold,
            clickOnSuccess = p.click == 1 || useStartBattlePreset || (isExplicitStartBattleTarget && p.click != 0),
        )
        val generation = runGeneration
        diagnosticInfo(
            "任务 ${task.id} OCR，" +
                if (useStartBattlePreset) {
                    "目标=开始战斗，命中条件=同一行至少${StartBattleShared.OCR_MIN_HIT_COUNT}个字"
                } else if (targetChars.isNotEmpty()) {
                    "目标=${targetChars.joinToString("")}，命中条件=至少${config.minHitCount}个字"
                } else {
                    "目标=${formatOcrLog(targetText)}"
                }
        )

        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (!isRunning || generation != runGeneration) {
                        result.hardwareBuffer.close()
                        return
                    }

                    var swBitmap: Bitmap? = null
                    var searchRegion: SearchRegion? = null
                    var ocrBitmap: Bitmap? = null
                    var buffer: android.hardware.HardwareBuffer? = null

                    try {
                        buffer = result.hardwareBuffer
                        val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        if (swBitmap == null) {
                            logError("${taskHumanLabel(task)}：OCR截图转换失败")
                            finishTask(task, false)
                            return
                        }

                        val mapping = buildScreenshotMapping(swBitmap)
                        searchRegion = buildSearchRegionForOcr(swBitmap, config.roi, mapping, task.id)
                        saveDebugScreenshots(swBitmap, searchRegion.bitmap)
                        val preprocess = config.preprocess ?: if (targetText.isBlank() && targetChars.isEmpty() && !useStartBattlePreset) "yellow_text" else null
                        ocrBitmap = createConfiguredOcrBitmap(searchRegion.bitmap, preprocess)
                        val bitmapForOcr = ocrBitmap ?: searchRegion.bitmap
                        ocrScope.launch {
                            try {
                                val text = PaddleTextRecognizer.recognize(service, bitmapForOcr)
                                withContext(Dispatchers.Main) {
                                if (!isRunning || generation != runGeneration) {
                                    if (ocrBitmap != null && ocrBitmap !== searchRegion?.bitmap) ocrBitmap?.recycle()
                                    searchRegion?.release()
                                    swBitmap?.recycle()
                                    return@withContext
                                }

                                val hit = when {
                                    useStartBattlePreset -> findStartBattleOcrHit(text)?.toOcrHit()
                                    targetChars.isNotEmpty() -> findOcrHitByChars(text, config)
                                    else -> findOcrHitByText(text, targetText, targetText.isBlank())
                                }
                                logOcrResult(task.id, text, hit, config, targetText, useStartBattlePreset)
                                if (hit == null) {
                                    finishTask(task, false)
                                } else {
                                    val screenshotX = searchRegion.offsetX + hit.center.x
                                    val screenshotY = searchRegion.offsetY + hit.center.y
                                    lastMatchX = screenshotX * mapping.screenshotToDisplayX
                                    lastMatchY = screenshotY * mapping.screenshotToDisplayY
                                    matchedPointsByTaskId[task.id] = PointF(lastMatchX, lastMatchY)
                                    diagnosticInfo(
                                        "任务 ${task.id} OCR坐标 x=${lastMatchX.toInt()} y=${lastMatchY.toInt()}"
                                    )
                                    if (config.clickOnSuccess) {
                                        dispatchClick(lastMatchX, lastMatchY) {
                                            if (generation == runGeneration && isRunning) finishTask(task, true)
                                        }
                                    } else {
                                        finishTask(task, true)
                                    }
                                }

                                if (ocrBitmap != null && ocrBitmap !== searchRegion?.bitmap) ocrBitmap?.recycle()
                                searchRegion?.release()
                                swBitmap?.recycle()
                                }
                            } catch (error: Throwable) {
                                withContext(Dispatchers.Main) {
                                logError("${taskHumanLabel(task)}：OCR识别失败：${error.message}", error)
                                if (generation == runGeneration && isRunning) finishTask(task, false)
                                if (ocrBitmap != null && ocrBitmap !== searchRegion?.bitmap) ocrBitmap?.recycle()
                                searchRegion?.release()
                                swBitmap?.recycle()
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        logError("${taskHumanLabel(task)}：OCR执行失败", t)
                        if (generation == runGeneration && isRunning) finishTask(task, false)
                        if (ocrBitmap != null && ocrBitmap !== searchRegion?.bitmap) ocrBitmap?.recycle()
                        searchRegion?.release()
                        swBitmap?.recycle()
                    } finally {
                        buffer?.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    diagnosticError("任务 ${task.id} OCR截图失败 errorCode=$errorCode")
                    logError("${taskHumanLabel(task)}：OCR截图失败，错误码=$errorCode")
                    if (generation == runGeneration && isRunning) finishTask(task, false)
                }
            }
        )
    }

    private fun executeScreenshotGroup(task: DailyTask) {
        val params = task.params ?: return finishTask(task, false)
        val steps = params.screenshot_steps
            ?.filter { it.type.isNotBlank() }
            .orEmpty()
        if (steps.isEmpty()) {
            logError("${taskHumanLabel(task)}：候选截图组未配置")
            return finishTask(task, false)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            diagnosticError("任务 ${task.id} SCREENSHOT_GROUP 截图不支持，当前 SDK=${Build.VERSION.SDK_INT}")
            return finishTask(task, false)
        }
        val generation = runGeneration
        verboseInfo("任务 ${task.id} SCREENSHOT_GROUP，候选数=${steps.size}")
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (!isRunning || generation != runGeneration) {
                        result.hardwareBuffer.close()
                        return
                    }
                    var swBitmap: Bitmap? = null
                    var searchRegion: SearchRegion? = null
                    var buffer: android.hardware.HardwareBuffer? = null
                    try {
                        buffer = result.hardwareBuffer
                        val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        if (swBitmap == null) {
                            logError("${taskHumanLabel(task)}：候选截图组截图转换失败")
                            finishTask(task, false)
                            return
                        }
                        val mapping = buildScreenshotMapping(swBitmap)
                        searchRegion = buildSearchRegionForOcr(swBitmap, params.roi, mapping, task.id)
                        saveDebugScreenshots(swBitmap, searchRegion.bitmap)
                        executeScreenshotGroupStep(
                            task = task,
                            generation = generation,
                            params = params,
                            steps = steps,
                            index = 0,
                            swBitmap = swBitmap,
                            sharedRegion = searchRegion,
                            mapping = mapping,
                        )
                    } catch (t: Throwable) {
                        logError("${taskHumanLabel(task)}：候选截图组执行失败", t)
                        if (generation == runGeneration && isRunning) finishTask(task, false)
                        searchRegion?.release()
                        swBitmap?.recycle()
                    } finally {
                        buffer?.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    diagnosticError("任务 ${task.id} SCREENSHOT_GROUP 截图失败 errorCode=$errorCode")
                    logError("${taskHumanLabel(task)}：候选截图组截图失败，错误码=$errorCode")
                    if (generation == runGeneration && isRunning) finishTask(task, false)
                }
            }
        )
    }

    private fun executeScreenshotGroupStep(
        task: DailyTask,
        generation: Long,
        params: com.example.yuanassist.model.TaskParams,
        steps: List<ScreenshotStep>,
        index: Int,
        swBitmap: Bitmap,
        sharedRegion: SearchRegion,
        mapping: ScreenshotMapping,
    ) {
        if (!isRunning || generation != runGeneration) {
            sharedRegion.release()
            swBitmap.recycle()
            return
        }
        if (index >= steps.size) {
            sharedRegion.release()
            swBitmap.recycle()
            finishTask(task, false)
            return
        }
        val step = steps[index]
        val loweredType = step.type.trim().lowercase()
        when (loweredType) {
            "ocr" -> executeScreenshotGroupOcrStep(
                task = task,
                generation = generation,
                params = params,
                steps = steps,
                index = index,
                step = step,
                swBitmap = swBitmap,
                sharedRegion = sharedRegion,
                mapping = mapping,
            )
            "template", "match_template" -> {
                val match = findScreenshotGroupTemplateMatch(task, step, swBitmap, sharedRegion, mapping)
                if (match != null) {
                    applyScreenshotGroupMatch(task, generation, match, sharedRegion, swBitmap, mapping)
                } else {
                    executeScreenshotGroupStep(task, generation, params, steps, index + 1, swBitmap, sharedRegion, mapping)
                }
            }
            else -> {
                logError("${taskHumanLabel(task)}：候选截图组不支持 ${step.type}")
                executeScreenshotGroupStep(task, generation, params, steps, index + 1, swBitmap, sharedRegion, mapping)
            }
        }
    }

    private fun executeScreenshotGroupOcrStep(
        task: DailyTask,
        generation: Long,
        params: com.example.yuanassist.model.TaskParams,
        steps: List<ScreenshotStep>,
        index: Int,
        step: ScreenshotStep,
        swBitmap: Bitmap,
        sharedRegion: SearchRegion,
        mapping: ScreenshotMapping,
    ) {
        val stepRoi = step.roi
        val region = if (stepRoi == null) {
            sharedRegion
        } else {
            buildSearchRegionForOcr(swBitmap, stepRoi, mapping, task.id)
        }
        var ocrBitmap: Bitmap? = null
        try {
            val targetChars = step.target_chars
                ?.mapNotNull { it.trim().firstOrNull() }
                ?.distinct()
                .orEmpty()
            val targetText = (step.target_text ?: step.button_name)?.trim().orEmpty()
            if (targetChars.isEmpty() && targetText.isBlank()) {
                if (region !== sharedRegion) region.release()
                executeScreenshotGroupStep(task, generation, params, steps, index + 1, swBitmap, sharedRegion, mapping)
                return
            }
            val config = OcrConfig(
                targetChars = targetChars,
                minHitCount = (step.min_hit_count ?: targetChars.size).coerceAtLeast(1),
                roi = stepRoi ?: params.roi,
                preprocess = step.preprocess,
                templateName = step.template_name,
                threshold = step.threshold ?: params.threshold,
                clickOnSuccess = step.click == 1,
            )
            val preprocess = config.preprocess ?: if (targetText.isBlank() && targetChars.isEmpty()) "yellow_text" else null
            ocrBitmap = createConfiguredOcrBitmap(region.bitmap, preprocess)
            val bitmapForOcr = ocrBitmap ?: region.bitmap
            ocrScope.launch {
                try {
                    val text = PaddleTextRecognizer.recognize(service, bitmapForOcr)
                    withContext(Dispatchers.Main) {
                        if (!isRunning || generation != runGeneration) {
                            if (ocrBitmap != null && ocrBitmap !== region.bitmap) ocrBitmap?.recycle()
                            if (region !== sharedRegion) region.release()
                            sharedRegion.release()
                            swBitmap.recycle()
                            return@withContext
                        }
                        val hit = if (targetChars.isNotEmpty()) {
                            findOcrHitByChars(text, config)
                        } else {
                            findOcrHitByText(text, targetText, targetText.isBlank())
                        }
                        logOcrResult(task.id, text, hit, config, targetText, false)
                        if (hit == null) {
                            if (ocrBitmap != null && ocrBitmap !== region.bitmap) ocrBitmap?.recycle()
                            if (region !== sharedRegion) region.release()
                            executeScreenshotGroupStep(task, generation, params, steps, index + 1, swBitmap, sharedRegion, mapping)
                            return@withContext
                        }
                        val match = ScreenshotMatch(
                            nextTaskId = resolveScreenshotGroupSuccessTaskId(task, step),
                            clickOnSuccess = step.click == 1,
                            centerInScreenshot = PointF(
                                region.offsetX + hit.center.x,
                                region.offsetY + hit.center.y
                            ),
                            logMessage = "${taskHumanLabel(task)}：OCR成功，原文=${formatOcrLog(text.text)}，命中=${formatOcrLog(hit.lineText)}",
                            terminalNote = step.terminal_note,
                        )
                        if (ocrBitmap != null && ocrBitmap !== region.bitmap) ocrBitmap?.recycle()
                        if (region !== sharedRegion) region.release()
                        applyScreenshotGroupMatch(task, generation, match, sharedRegion, swBitmap, mapping)
                    }
                } catch (error: Throwable) {
                    withContext(Dispatchers.Main) {
                        logError("${taskHumanLabel(task)}：候选截图组 OCR 识别失败：${error.message}", error)
                        if (ocrBitmap != null && ocrBitmap !== region.bitmap) ocrBitmap?.recycle()
                        if (region !== sharedRegion) region.release()
                        executeScreenshotGroupStep(task, generation, params, steps, index + 1, swBitmap, sharedRegion, mapping)
                    }
                }
            }
        } catch (t: Throwable) {
            logError("${taskHumanLabel(task)}：候选截图组 OCR 步骤失败", t)
            if (ocrBitmap != null && ocrBitmap !== region.bitmap) ocrBitmap.recycle()
            if (region !== sharedRegion) region.release()
            executeScreenshotGroupStep(task, generation, params, steps, index + 1, swBitmap, sharedRegion, mapping)
        }
    }

    private fun findScreenshotGroupTemplateMatch(
        task: DailyTask,
        step: ScreenshotStep,
        swBitmap: Bitmap,
        sharedRegion: SearchRegion,
        mapping: ScreenshotMapping,
    ): ScreenshotMatch? {
        val templateName = step.template_name ?: return null
        val region = if (step.roi == null) {
            sharedRegion
        } else {
            buildSearchRegionForOcr(swBitmap, step.roi, mapping, task.id)
        }
        val rawTemplate = loadTemplateFromAssets(templateName)
        if (rawTemplate == null) {
            if (region !== sharedRegion) region.release()
            return null
        }
        val gameScale = min(swBitmap.width / BASE_W, swBitmap.height / BASE_H)
        val scaledWidth = (rawTemplate.width * gameScale).toInt().coerceAtLeast(1)
        val scaledHeight = (rawTemplate.height * gameScale).toInt().coerceAtLeast(1)
        var scaledTemplate: Bitmap? = null
        val ownsScaledTemplate = scaledWidth != rawTemplate.width || scaledHeight != rawTemplate.height
        try {
            scaledTemplate = if (ownsScaledTemplate) {
                Bitmap.createScaledBitmap(rawTemplate, scaledWidth, scaledHeight, true)
            } else {
                rawTemplate
            }
            if (region.bitmap.width < scaledTemplate.width || region.bitmap.height < scaledTemplate.height) {
                return null
            }
            val matchResult = matchTemplate(region.bitmap, scaledTemplate, step.threshold ?: 0.8f)
                ?: return null
            return ScreenshotMatch(
                nextTaskId = resolveScreenshotGroupSuccessTaskId(task, step),
                clickOnSuccess = step.click == 1,
                centerInScreenshot = PointF(
                    region.offsetX + matchResult.center.x,
                    region.offsetY + matchResult.center.y
                ),
                logMessage = "${taskHumanLabel(task)}：模板成功，模板=$templateName，分数=${formatScore(matchResult.score)}",
                terminalNote = step.terminal_note,
            )
        } finally {
            if (ownsScaledTemplate) scaledTemplate?.recycle()
            if (region !== sharedRegion) region.release()
        }
    }

    private fun applyScreenshotGroupMatch(
        task: DailyTask,
        generation: Long,
        match: ScreenshotMatch,
        sharedRegion: SearchRegion,
        swBitmap: Bitmap,
        mapping: ScreenshotMapping,
    ) {
        val taskLabel = taskLogLabel(task)
        val realX = match.centerInScreenshot.x * mapping.screenshotToDisplayX
        val realY = match.centerInScreenshot.y * mapping.screenshotToDisplayY
        lastMatchX = realX
        lastMatchY = realY
        matchedPointsByTaskId[task.id] = PointF(realX, realY)
        logInfo(match.logMessage)
        diagnosticInfo("任务 ${task.id} 命中坐标 x=${realX.toInt()} y=${realY.toInt()}")
        sharedRegion.release()
        swBitmap.recycle()
        if (task.start_cooldown_on_success && cooldownStartedAtMs == null) {
            cooldownStartedAtMs = System.currentTimeMillis()
            verboseInfo("$taskLabel 成功，开始记录冷却计时")
        }
        fun continueSuccess() {
            if (!isRunning || generation != runGeneration) return
            when (match.nextTaskId) {
                -4, -3, -2 -> {
                    val msg = match.terminalNote
                        ?: when (match.nextTaskId) {
                            -4 -> "任务倒计时中"
                            -3 -> "资源耗尽"
                            else -> "任务失败"
                        }
                    if (match.nextTaskId == -4 || match.nextTaskId == -3) {
                        logInfo("$taskLabel：$msg")
                    } else {
                        logError("$taskLabel：$msg")
                    }
                    completePlan(false, msg, match.nextTaskId, task, match.terminalNote)
                }
                -1 -> completePlan(true, "已完成", -1, task, match.terminalNote)
                else -> {
                    currentTaskId = match.nextTaskId
                    executeNextTask()
                }
            }
        }
        if (match.clickOnSuccess) {
            dispatchClick(realX, realY) {
                if (generation == runGeneration && isRunning) continueSuccess()
            }
        } else {
            continueSuccess()
        }
    }

    private fun executeStartBattleTemplateClick(task: DailyTask) {
        if (!TemplateOverrideStore.hasOverride(service, TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME)) {
            logError("${taskHumanLabel(task)}：未找到开始战斗模板，请重新上传截图后一键替换")
            return finishTask(task, false)
        }
        executeTemplateSearch(
            task = task,
            templateName = TemplateOverrideStore.START_BATTLE_TEMPLATE_FILE_NAME,
            threshold = TemplateOverrideStore.START_BATTLE_TEMPLATE_THRESHOLD,
            clickOnSuccess = true,
            logLabel = "开始战斗模板",
            roiOverride = StartBattleShared.buildRoi()
        )
    }

    private fun executeStartBattleOcrClick(task: DailyTask) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            diagnosticError("任务 ${task.id} 开始战斗OCR截图不支持，当前 SDK=${Build.VERSION.SDK_INT}")
            return finishTask(task, false)
        }
        val startBattleRoi = StartBattleShared.buildRoi()

        val generation = runGeneration
        verboseInfo(
            "任务 ${task.id} 开始战斗OCR，" +
                "命中条件=同一行至少${StartBattleShared.OCR_MIN_HIT_COUNT}个字"
        )

        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (!isRunning || generation != runGeneration) {
                        result.hardwareBuffer.close()
                        return
                    }

                    var swBitmap: Bitmap? = null
                    var searchRegion: SearchRegion? = null
                    var buffer: android.hardware.HardwareBuffer? = null

                    try {
                        buffer = result.hardwareBuffer
                        val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        if (swBitmap == null) {
                            logError("${taskHumanLabel(task)}：开始战斗 OCR 截图转换失败")
                            finishTask(task, false)
                            return
                        }

                        val mapping = buildScreenshotMapping(swBitmap)
                        searchRegion = buildSearchRegionForOcr(swBitmap, startBattleRoi, mapping, task.id)
                        saveDebugScreenshots(swBitmap, searchRegion.bitmap)
                        ocrScope.launch {
                            try {
                                val text = PaddleTextRecognizer.recognize(service, searchRegion.bitmap)
                                withContext(Dispatchers.Main) {
                                if (!isRunning || generation != runGeneration) {
                                    searchRegion?.release()
                                    swBitmap?.recycle()
                                    return@withContext
                                }

                                val hit = findStartBattleOcrHit(text)
                                logStartBattleOcr(task.id, text, hit)
                                if (hit == null) {
                                    finishTask(task, false)
                                } else {
                                    val screenshotX = searchRegion.offsetX + hit.center.x
                                    val screenshotY = searchRegion.offsetY + hit.center.y
                                    lastMatchX = screenshotX * mapping.screenshotToDisplayX
                                    lastMatchY = screenshotY * mapping.screenshotToDisplayY
                                    matchedPointsByTaskId[task.id] = PointF(lastMatchX, lastMatchY)
                                    verboseInfo(
                                            "任务 ${task.id} 开始战斗OCR命中 " +
                                            "line=${formatOcrLog(hit.lineText)} " +
                                            "hits=${hit.hitChars.joinToString("")} " +
                                            "count=${hit.hitCount} " +
                                            "x=${lastMatchX.toInt()} y=${lastMatchY.toInt()}"
                                    )
                                    dispatchClick(lastMatchX, lastMatchY) {
                                        if (generation == runGeneration && isRunning) finishTask(task, true)
                                    }
                                }

                                searchRegion?.release()
                                swBitmap?.recycle()
                                }
                            } catch (error: Throwable) {
                                withContext(Dispatchers.Main) {
                                logError("${taskHumanLabel(task)}：开始战斗 OCR 识别失败：${error.message}", error)
                                if (generation == runGeneration && isRunning) finishTask(task, false)
                                searchRegion?.release()
                                swBitmap?.recycle()
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        logError("${taskHumanLabel(task)}：开始战斗 OCR 执行失败", t)
                        if (generation == runGeneration && isRunning) finishTask(task, false)
                        searchRegion?.release()
                        swBitmap?.recycle()
                    } finally {
                        buffer?.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    diagnosticError("任务 ${task.id} 开始战斗OCR截图失败 errorCode=$errorCode")
                    logError("${taskHumanLabel(task)}：开始战斗 OCR 截图失败，错误码=$errorCode")
                    if (generation == runGeneration && isRunning) finishTask(task, false)
                }
            }
        )
    }

    private fun buildSearchRegionForOcr(
        swBitmap: Bitmap,
        roi: ROI?,
        mapping: ScreenshotMapping,
        taskId: Int
    ): SearchRegion {
        logScreenshotMapping(taskId, mapping)
        val gameScale = min(swBitmap.width / BASE_W, swBitmap.height / BASE_H)
        val gameWidth = BASE_W * gameScale
        val offsetX = (swBitmap.width - gameWidth) / 2f

        var searchBitmap = swBitmap
        var roiOffsetX = 0f
        var roiOffsetY = 0f
        var ownsBitmap = false
        var debugLeft = 0
        var debugTop = 0
        var debugWidth = swBitmap.width
        var debugHeight = swBitmap.height

        if (roi?.w == 0f && roi.h == 0f) {
            // 宽高为 0 时按全屏识别处理，不写运行日志。
        } else if (roi?.align == "dynamic_avatar_bounds") {
            val topBound = 1254f * gameScale
            val bottomBound = swBitmap.height - (313f * gameScale)
            val safeY = topBound.toInt().coerceAtLeast(0)
            val safeH = (bottomBound - topBound).toInt()
                .coerceAtMost(swBitmap.height - safeY)
                .coerceAtLeast(1)
            searchBitmap = Bitmap.createBitmap(swBitmap, 0, safeY, swBitmap.width, safeH)
            roiOffsetY = safeY.toFloat()
            ownsBitmap = searchBitmap !== swBitmap
            debugTop = safeY
            debugHeight = safeH
        } else if (roi?.align == "dynamic_filter_bounds") {
            val realCenterY = 1254f * gameScale
            val realCenterX = offsetX + (gameWidth * 0.9f)
            val realW = gameWidth * 0.2f
            val realH = 300f * gameScale
            val safeX = (realCenterX - realW / 2f).toInt().coerceIn(0, swBitmap.width - 1)
            val safeY = (realCenterY - realH / 2f).toInt().coerceIn(0, swBitmap.height - 1)
            val safeW = realW.toInt().coerceAtMost(swBitmap.width - safeX).coerceAtLeast(1)
            val safeH = realH.toInt().coerceAtMost(swBitmap.height - safeY).coerceAtLeast(1)
            searchBitmap = Bitmap.createBitmap(swBitmap, safeX, safeY, safeW, safeH)
            roiOffsetX = safeX.toFloat()
            roiOffsetY = safeY.toFloat()
            ownsBitmap = searchBitmap !== swBitmap
            debugLeft = safeX
            debugTop = safeY
            debugWidth = safeW
            debugHeight = safeH
        } else if (roi?.x != null && roi.y != null && roi.w != null && roi.h != null) {
            val (realCenterX, realCenterY) = calculateRealCoordinate(roi.x, roi.y, roi.align)
            val screenshotCenterX = realCenterX * mapping.displayToScreenshotX
            val screenshotCenterY = realCenterY * mapping.displayToScreenshotY
            val realW = roi.w * gameScale
            val realH = roi.h * gameScale
            val safeX = (screenshotCenterX - realW / 2f).toInt().coerceIn(0, swBitmap.width - 1)
            val safeY = (screenshotCenterY - realH / 2f).toInt().coerceIn(0, swBitmap.height - 1)
            val safeW = realW.toInt().coerceAtMost(swBitmap.width - safeX).coerceAtLeast(1)
            val safeH = realH.toInt().coerceAtMost(swBitmap.height - safeY).coerceAtLeast(1)
            searchBitmap = Bitmap.createBitmap(swBitmap, safeX, safeY, safeW, safeH)
            roiOffsetX = safeX.toFloat()
            roiOffsetY = safeY.toFloat()
            ownsBitmap = searchBitmap !== swBitmap
            debugLeft = safeX
            debugTop = safeY
            debugWidth = safeW
            debugHeight = safeH
        }

        if (debugRoiEnabled) {
            showDebugRoi(
                (debugLeft * mapping.screenshotToDisplayX).toInt(),
                (debugTop * mapping.screenshotToDisplayY).toInt(),
                (debugWidth * mapping.screenshotToDisplayX).toInt(),
                (debugHeight * mapping.screenshotToDisplayY).toInt(),
                1500L
            )
        }

        return SearchRegion(searchBitmap, roiOffsetX, roiOffsetY, ownsBitmap)
    }

    private fun SearchRegion.release() {
        if (ownsBitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun saveDebugScreenshots(originalBitmap: Bitmap, croppedBitmap: Bitmap) {
        if (!debugScreenshotEnabled) return
        BirdFoodDebugScreenshotStore.saveSnapshots(service, originalBitmap, croppedBitmap)
    }

    private fun createYellowTextOcrBitmap(source: Bitmap): Bitmap? {
        return try {
            val width = source.width
            val height = source.height
            val input = IntArray(width * height)
            val output = IntArray(width * height)
            source.getPixels(input, 0, width, 0, 0, width, height)
            for (i in input.indices) {
                val color = input[i]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val isYellowText = r >= 150 && g >= 110 && b <= 180 && (r - b) >= 40 && (g - b) >= 30
                output[i] = if (isYellowText) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            logError("OCR 黄字预处理失败", t)
            null
        }
    }

    private fun createConfiguredOcrBitmap(source: Bitmap, preprocess: String?): Bitmap? {
        return when (preprocess?.lowercase()) {
            "yellow_text" -> createYellowTextOcrBitmap(source)
            "light_text" -> createLightTextOcrBitmap(source)
            else -> null
        }
    }

    private fun createLightTextOcrBitmap(source: Bitmap): Bitmap? {
        return try {
            val width = source.width
            val height = source.height
            val input = IntArray(width * height)
            val output = IntArray(width * height)
            source.getPixels(input, 0, width, 0, 0, width, height)
            for (i in input.indices) {
                val color = input[i]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val isLightWarmText = r >= 145 && g >= 120 && b >= 90 && r >= b - 10 && g >= b - 35
                output[i] = if (isLightWarmText) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            logError("OCR 浅色文字预处理失败", t)
            null
        }
    }

    private fun findStartBattleOcrHit(text: PaddleTextResult): StartBattleOcrHit? {
        return StartBattleShared.findBestOcrLineMatch(text)?.let { match ->
            StartBattleOcrHit(
                lineText = match.lineText,
                normalizedLineText = match.normalizedLineText,
                hitChars = match.hitChars,
                center = match.center,
                containsPhrase = match.containsPhrase,
                area = match.area
            )
        }
    }

    private fun StartBattleOcrHit.toOcrHit(): OcrHit =
        OcrHit(
            lineText = lineText,
            normalizedLineText = normalizedLineText,
            hitChars = hitChars,
            center = center,
            area = area,
        )

    private fun findOcrHitByChars(
        text: PaddleTextResult,
        config: OcrConfig,
    ): OcrHit? {
        return text.blocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val boundingBox = line.boundingBox
                val normalizedLineText = line.text.filterNot { it.isWhitespace() }
                val hitChars = config.targetChars.filter { normalizedLineText.contains(it) }
                if (hitChars.size < config.minHitCount) {
                    return@mapNotNull null
                }
                OcrHit(
                    lineText = line.text,
                    normalizedLineText = normalizedLineText,
                    hitChars = hitChars,
                    center = PointF(boundingBox.exactCenterX(), boundingBox.exactCenterY()),
                    area = boundingBox.width() * boundingBox.height(),
                )
            }
            .maxWithOrNull(compareBy<OcrHit>({ it.hitCount }, { it.area }))
    }

    private fun findOcrHitByText(
        text: PaddleTextResult,
        targetText: String,
        matchAnyChinese: Boolean,
    ): OcrHit? {
        val components = listOf(
            text.blocks.flatMap { block -> block.lines.flatMap { line -> line.elements } },
            text.blocks.flatMap { block -> block.lines },
            text.blocks,
        )
        for (group in components) {
            findOcrComponentHit(group, targetText, matchAnyChinese)?.let { return it }
        }
        return null
    }

    private fun findOcrComponentHit(
        components: List<Any>,
        targetText: String,
        matchAnyChinese: Boolean,
    ): OcrHit? {
        var bestBox: android.graphics.Rect? = null
        var bestText = ""
        var bestArea = -1
        val normalizedTarget = normalizeOcrText(targetText)
        for (component in components) {
            val textValue = extractOcrText(component)
            val normalizedText = normalizeOcrText(textValue)
            val box = extractOcrBoundingBox(component) ?: continue
            val matched = if (matchAnyChinese) containsChinese(textValue) else normalizedText.contains(normalizedTarget)
            if (!matched) continue
            val area = box.width() * box.height()
            if (area > bestArea) {
                bestArea = area
                bestBox = box
                bestText = textValue
            }
        }
        val box = bestBox ?: return null
        return OcrHit(
            lineText = bestText,
            normalizedLineText = normalizeOcrText(bestText),
            hitChars = if (matchAnyChinese) bestText.filter { it in '\u4E00'..'\u9FFF' }.toList() else normalizedTarget.toList(),
            center = PointF(box.exactCenterX(), box.exactCenterY()),
            area = bestArea,
        )
    }

    private fun logOcrResult(
        taskId: Int,
        text: PaddleTextResult,
        hit: OcrHit?,
        config: OcrConfig,
        targetText: String,
        startBattlePreset: Boolean,
    ) {
        val rawText = text.text
        val targetLabel = when {
            startBattlePreset -> "开始战斗"
            config.targetChars.isNotEmpty() -> config.targetChars.joinToString("")
            targetText.isNotBlank() -> targetText
            else -> "任意中文"
        }
        val nodeLabel = taskHumanLabel(taskId)
        if (hit == null) {
            logInfo("$nodeLabel：OCR失败，目标=${formatOcrLog(targetLabel)}，原文=${formatOcrLog(rawText)}")
            return
        }
        logInfo("$nodeLabel：OCR成功，目标=${formatOcrLog(targetLabel)}，原文=${formatOcrLog(rawText)}，命中=${formatOcrLog(hit.lineText)}")
    }

    private fun logStartBattleOcr(taskId: Int, text: PaddleTextResult, hit: StartBattleOcrHit?) {
        val rawText = text.text
        val nodeLabel = taskHumanLabel(taskId)
        if (hit == null) {
            logInfo("$nodeLabel：OCR失败，目标=\"开始战斗\"，原文=${formatOcrLog(rawText)}")
            return
        }
        logInfo("$nodeLabel：OCR成功，目标=\"开始战斗\"，原文=${formatOcrLog(rawText)}，命中=${formatOcrLog(hit.lineText)}")
    }

    private fun formatOcrLog(text: String): String {
        if (text.isEmpty()) return "\"\""
        return "\"" + text
            .replace("\r\n", "\\n")
            .replace("\r", "\\n")
            .replace("\n", "\\n") + "\""
    }

    private fun extractOcrText(component: Any): String = when (component) {
        is PaddleTextBlock -> component.text
        is PaddleTextLine -> component.text
        is PaddleTextElement -> component.text
        else -> ""
    }

    private fun extractOcrBoundingBox(component: Any): android.graphics.Rect? = when (component) {
        is PaddleTextBlock -> component.boundingBox
        is PaddleTextLine -> component.boundingBox
        is PaddleTextElement -> component.boundingBox
        else -> null
    }

    private fun containsChinese(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.any { it in '\u4E00'..'\u9FFF' }
    }

    private fun normalizeOcrText(value: String): String =
        value.filterNot { it.isWhitespace() }

    private fun resolveSuccessTaskId(task: DailyTask): Int {
        val p = task.params ?: return task.on_success
        val varName = p.branch_var ?: return task.on_success
        val routes = p.branch_routes ?: return task.on_success
        val value = variables[varName] ?: return task.on_success
        return routes[value] ?: task.on_success
    }

    private fun resolveScreenshotGroupSuccessTaskId(task: DailyTask, step: ScreenshotStep): Int {
        if (step.on_success != task.on_success) return step.on_success
        return resolveSuccessTaskId(task)
    }

    private fun resolveFailTaskId(task: DailyTask): Int {
        val p = task.params ?: return task.on_fail
        val varName = p.fail_branch_var ?: return task.on_fail
        val routes = p.fail_branch_routes ?: return task.on_fail
        val value = variables[varName] ?: return task.on_fail
        return routes[value] ?: task.on_fail
    }

    private fun resolveScriptName(params: com.example.yuanassist.model.TaskParams): String? {
        params.script_branch_var?.let { branchVar ->
            val branchValue = variables[branchVar]
            val routed = branchValue?.let { params.script_name_routes?.get(it) }
            if (!routed.isNullOrBlank()) {
                return normalizeScriptFileName(routed)
            }
        }
        params.script_name_var?.let { varName ->
            val dynamicName = variables[varName]
            if (!dynamicName.isNullOrBlank()) {
                return normalizeScriptFileName(dynamicName)
            }
        }
        return params.script_name?.takeIf { it.isNotBlank() }?.let(::normalizeScriptFileName)
    }

    private fun resolveSegmentTaskId(staticId: Int?, dynamicVarName: String?): Int? {
        dynamicVarName?.let { varName ->
            variables[varName]?.toIntOrNull()?.let { return it }
        }
        return staticId
    }

    private fun normalizeScriptFileName(value: String): String {
        return if (value.endsWith(".json", ignoreCase = true)) value else "$value.json"
    }

    private fun buildChildVariables(params: com.example.yuanassist.model.TaskParams): Map<String, String> {
        val inherited = if (params.inherit_variables == false) {
            mutableMapOf()
        } else {
            variables.toMutableMap()
        }
        params.script_variables?.forEach { (key, value) ->
            inherited[key] = resolveVariablePlaceholders(value)
        }
        return inherited
    }

    private fun resolveVariablePlaceholders(value: String): String {
        var resolved = value
        variables.forEach { (key, variableValue) ->
            resolved = resolved.replace("\${$key}", variableValue)
        }
        return resolved
    }

    private fun loadSegmentPlan(
        scriptName: String,
        entryTaskId: Int,
        exitTaskId: Int?
    ): DailyTaskPlan? {
        val plan = try {
            CloudScriptOverrideStore.loadAssetPlanWithOverride(service, scriptName, gson)
        } catch (t: Throwable) {
            logError("加载子脚本失败：$scriptName", t)
            null
        } ?: return null
        if (plan.tasks.none { it.id == entryTaskId }) {
            logError("子脚本入口无效：$scriptName entry=$entryTaskId")
            return null
        }
        if (exitTaskId == null) {
            return plan.copy(start_task_id = entryTaskId)
        }
        if (plan.tasks.none { it.id == exitTaskId }) {
            logError("子脚本出口无效：$scriptName exit=$exitTaskId")
            return null
        }
        return plan.copy(
            start_task_id = entryTaskId,
            tasks = plan.tasks.map { source ->
                var nextTask = source
                if (source.on_success == exitTaskId) {
                    nextTask = nextTask.copy(on_success = -1)
                }
                if (source.on_fail == exitTaskId) {
                    nextTask = nextTask.copy(on_fail = -1)
                }
                if (source.params?.branch_routes?.containsValue(exitTaskId) == true) {
                    val nextRoutes = source.params.branch_routes.mapValues { (_, value) ->
                        if (value == exitTaskId) -1 else value
                    }
                    nextTask = nextTask.copy(
                        params = source.params.copy(branch_routes = nextRoutes)
                    )
                }
                if (source.params?.fail_branch_routes?.containsValue(exitTaskId) == true) {
                    val nextFailRoutes = source.params.fail_branch_routes.mapValues { (_, value) ->
                        if (value == exitTaskId) -1 else value
                    }
                    nextTask = nextTask.copy(
                        params = nextTask.params?.copy(fail_branch_routes = nextFailRoutes)
                    )
                }
                if (nextTask.id == exitTaskId) {
                    return@map nextTask.copy(on_success = -1, on_fail = -1)
                }
                nextTask
            }
        )
    }

    private fun loadTemplateFromAssets(fileName: String): Bitmap? {
        val localTemplateFile = currentTemplateDir
            ?.let { File(it, fileName) }
            ?.takeIf { it.exists() }
        val assetTemplatePath = if (fileName.contains('/') || fileName.contains('\\')) {
            fileName
        } else {
            currentAssetTemplateDir
                ?.let { dir -> "$dir/$fileName" }
                ?: fileName
        }
        val overrideTemplateFile = TemplateOverrideStore.overrideFile(service, fileName)
            .takeIf { it.exists() }
        val cacheKey = if (localTemplateFile != null) {
            "${localTemplateFile.absolutePath}#${localTemplateFile.lastModified()}"
        } else if (overrideTemplateFile != null) {
            "$fileName#override#${overrideTemplateFile.lastModified()}"
        } else {
            TemplateOverrideStore.cacheKey(service, assetTemplatePath)
        }
        templateCache.get(cacheKey)?.let {
            if (!it.isRecycled) {
                verboseInfo("模板缓存命中 $assetTemplatePath")
                return it
            }
            templateCache.remove(cacheKey)
        }
        return try {
            val bitmap = if (localTemplateFile != null) {
                BitmapFactory.decodeFile(localTemplateFile.absolutePath)
            } else if (overrideTemplateFile != null) {
                BitmapFactory.decodeFile(overrideTemplateFile.absolutePath)
            } else {
                TemplateOverrideStore.loadBitmap(service, service.assets, assetTemplatePath)
            }
            if (bitmap != null) {
                templateCache.put(cacheKey, bitmap)
                verboseInfo("模板已加载 $assetTemplatePath ${bitmap.width}x${bitmap.height}")
            } else {
                logError("模板解码失败：$assetTemplatePath")
            }
            bitmap
        } catch (t: Throwable) {
            logError("模板打开失败：$assetTemplatePath", t)
            null
        }
    }

    private fun executeMatchTemplate(task: DailyTask) {
        val p = task.params ?: return finishTask(task, false)
        val templateName = p.template_name ?: return finishTask(task, false)
        executeTemplateSearch(
            task = task,
            templateName = templateName,
            threshold = p.threshold,
            clickOnSuccess = p.click == 1,
            logLabel = "模板"
        )
    }

    private fun ocrTemplateOverrideName(task: DailyTask, params: TaskParams): String? {
        params.template_name?.let { templateName ->
            return if (templateName.contains('/') || templateName.contains('\\')) {
                templateName
            } else {
                currentAssetTemplateDir
                    ?.let { dir -> "$dir/$templateName" }
                    ?: templateName
            }
        }
        return TemplateOverrideStore.ocrTemplateFileName(currentScriptFileName, task.id)
    }

    private fun executeTemplateSearch(
        task: DailyTask,
        templateName: String,
        threshold: Float,
        clickOnSuccess: Boolean,
        logLabel: String,
        roiOverride: ROI? = null
    ) {
        val p = task.params ?: return finishTask(task, false)
        val generation = runGeneration
        val effectiveRoi = roiOverride ?: p.roi
        diagnosticInfo("任务 ${task.id} 匹配${logLabel}=$templateName")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            diagnosticError("任务 ${task.id} 模板截图不支持，当前 SDK=${Build.VERSION.SDK_INT} template=$templateName")
            return finishTask(task, false)
        }

        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (!isRunning || generation != runGeneration) {
                        result.hardwareBuffer.close()
                        return
                    }
                    var swBitmap: Bitmap? = null
                    var searchRegion: SearchRegion? = null
                    var scaledTemplate: Bitmap? = null
                    var ownsScaledTemplate = false
                    var buffer: android.hardware.HardwareBuffer? = null
                    try {
                        buffer = result.hardwareBuffer
                        val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        swBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            ?: return finishTask(task, false)
                        val mapping = buildScreenshotMapping(swBitmap)
                        searchRegion = buildSearchRegionForOcr(swBitmap, effectiveRoi, mapping, task.id)
                        val searchBitmap = searchRegion.bitmap
                        saveDebugScreenshots(swBitmap, searchBitmap)
                        val rawTemplate = loadTemplateFromAssets(templateName)
                            ?: return finishTask(task, false)
                        val gameScale = min(swBitmap.width / BASE_W, swBitmap.height / BASE_H)
                        val scaledWidth = (rawTemplate.width * gameScale).toInt().coerceAtLeast(1)
                        val scaledHeight = (rawTemplate.height * gameScale).toInt().coerceAtLeast(1)
                        scaledTemplate = if (scaledWidth == rawTemplate.width && scaledHeight == rawTemplate.height) {
                            rawTemplate
                        } else {
                            ownsScaledTemplate = true
                            Bitmap.createScaledBitmap(rawTemplate, scaledWidth, scaledHeight, true)
                        }

                        if (searchBitmap.width < scaledTemplate.width || searchBitmap.height < scaledTemplate.height) {
                            return finishTask(task, false)
                        }

                        val matchResult = matchTemplate(searchBitmap, scaledTemplate, threshold)
                        if (matchResult != null) {
                            val screenshotMatchX = matchResult.center.x + searchRegion.offsetX
                            val screenshotMatchY = matchResult.center.y + searchRegion.offsetY
                            lastMatchX = screenshotMatchX * mapping.screenshotToDisplayX
                            lastMatchY = screenshotMatchY * mapping.screenshotToDisplayY
                            matchedPointsByTaskId[task.id] = PointF(lastMatchX, lastMatchY)
                            logInfo("${taskHumanLabel(task)}：模板成功，模板=$templateName，分数=${formatScore(matchResult.score)}")
                            diagnosticInfo("任务 ${task.id} 模板坐标 x=${lastMatchX.toInt()} y=${lastMatchY.toInt()}")
                            if (clickOnSuccess) {
                                dispatchClick(lastMatchX, lastMatchY) {
                                    if (generation == runGeneration && isRunning) finishTask(task, true)
                                }
                            } else {
                                finishTask(task, true)
                            }
                        } else {
                            logInfo("${taskHumanLabel(task)}：模板失败，模板=$templateName，阈值=$threshold")
                            finishTask(task, false)
                        }
                    } catch (t: Throwable) {
                        logError("${taskHumanLabel(task)}：模板匹配崩溃，模板=$templateName", t)
                        if (generation == runGeneration && isRunning) finishTask(task, false)
                    } finally {
                        buffer?.close()
                        if (ownsScaledTemplate) scaledTemplate?.recycle()
                        searchRegion?.release()
                        swBitmap?.recycle()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    diagnosticError("任务 ${task.id} 模板截图失败 errorCode=$errorCode template=$templateName")
                    logError("${taskHumanLabel(task)}：模板截图失败，错误码=$errorCode，模板=$templateName")
                    if (generation == runGeneration && isRunning) finishTask(task, false)
                }
            }
        )
    }

    private fun getRealScreenSize(): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            Pair(bounds.width().toFloat(), bounds.height().toFloat())
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            Pair(point.x.toFloat(), point.y.toFloat())
        }
    }

    private fun calculateRealCoordinate(baseX: Float, baseY: Float, align: String): Pair<Float, Float> {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val gameScale = min(screenWidth / BASE_W, screenHeight / BASE_H)
        val gameWidth = BASE_W * gameScale
        val gameHeight = BASE_H * gameScale
        val offsetX = (screenWidth - gameWidth) / 2f
        val offsetY = (screenHeight - gameHeight) / 2f
        val statusBarHeight = getRawStatusBarHeight() / 2f
        val realX = offsetX + (baseX * gameScale)
        val realY = when (align.lowercase()) {
            "absolute" -> baseY * (screenHeight / BASE_H)
            "top" -> statusBarHeight + (baseY * gameScale)
            "bottom" -> screenHeight - ((BASE_H - baseY) * gameScale)
            else -> offsetY + (baseY * gameScale)
        }
        return Pair(realX, realY)
    }

    private fun getRawStatusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else 40
    }

    private fun logDisplayMetrics(tag: String) {
        val metrics = service.resources.displayMetrics
        val (realWidth, realHeight) = getRealScreenSize()
        val rawStatusBarHeight = getRawStatusBarHeight()
        val scaledStatusBarHeight = rawStatusBarHeight / 2f
        verboseInfo("显示[$tag] 实际=${realWidth.toInt()}x${realHeight.toInt()} 资源=${metrics.widthPixels}x${metrics.heightPixels}")
        verboseInfo("显示[$tag] 状态栏原始=$rawStatusBarHeight 缩放后=${scaledStatusBarHeight.toInt()}")
    }

    private fun buildScreenshotMapping(bitmap: Bitmap): ScreenshotMapping {
        val (displayWidth, displayHeight) = getRealScreenSize()
        val stx = displayWidth / bitmap.width.toFloat()
        val sty = displayHeight / bitmap.height.toFloat()
        val dtx = bitmap.width.toFloat() / displayWidth
        val dty = bitmap.height.toFloat() / displayHeight
        return ScreenshotMapping(
            stx,
            sty,
            dtx,
            dty,
            (displayWidth >= displayHeight) != (bitmap.width >= bitmap.height),
            abs(dtx - dty) > 0.02f,
            displayWidth,
            displayHeight,
            bitmap.width,
            bitmap.height
        )
    }

    private fun logScreenshotMapping(taskId: Int, mapping: ScreenshotMapping) {
        verboseInfo("任务 $taskId 截图缩放 x=${"%.4f".format(mapping.screenshotToDisplayX)} y=${"%.4f".format(mapping.screenshotToDisplayY)} 实际=${mapping.displayWidth.toInt()}x${mapping.displayHeight.toInt()} 截图=${mapping.screenshotWidth}x${mapping.screenshotHeight}")
        if (mapping.orientationMismatch) logError("任务 $taskId 截图方向不一致")
        if (mapping.nonUniformScale) {
            logError("任务 $taskId 截图缩放不一致 x=${"%.4f".format(mapping.displayToScreenshotX)} y=${"%.4f".format(mapping.displayToScreenshotY)}")
        }
    }

    private fun showDebugRoi(left: Int, top: Int, width: Int, height: Int, holdMs: Long) {
        handler.post {
            clearDebugRoi()
            val borderView = View(service).apply {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(4, Color.RED)
                }
            }
            val params = WindowManager.LayoutParams(
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = left
                y = top
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            try {
                windowManager.addView(borderView, params)
                debugRoiView = borderView
            } catch (t: Throwable) {
                logError("显示调试范围失败", t)
                clearDebugRoi()
                return@post
            }
            handler.postDelayed({
                if (debugRoiView === borderView) clearDebugRoi()
            }, holdMs)
        }
    }

    private fun clearDebugRoi() {
        debugRoiView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
            } finally {
                debugRoiView = null
            }
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private inline fun <T> Mat.use(block: (Mat) -> T): T {
        try {
            return block(this)
        } finally {
            release()
        }
    }

    private fun matchTemplate(screenBitmap: Bitmap, templateBitmap: Bitmap, threshold: Float): TemplateMatchResult? {
        val ownsSourceBitmap = screenBitmap.config != Bitmap.Config.ARGB_8888
        val sourceBitmap = if (ownsSourceBitmap) screenBitmap.copy(Bitmap.Config.ARGB_8888, false) else screenBitmap
        try {
            return Mat().use { srcMat ->
                Mat().use { tmplMat ->
                    Mat().use { resultMat ->
                        Utils.bitmapToMat(sourceBitmap, srcMat)
                        Utils.bitmapToMat(templateBitmap, tmplMat)
                        Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
                        Imgproc.cvtColor(tmplMat, tmplMat, Imgproc.COLOR_RGBA2GRAY)
                        Imgproc.matchTemplate(srcMat, tmplMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
                        val mmLoc = Core.minMaxLoc(resultMat)
                        diagnosticInfo("匹配分数=${formatScore(mmLoc.maxVal)} 阈值=$threshold")
                        if (mmLoc.maxVal >= threshold) {
                            TemplateMatchResult(
                                center = PointF(
                                    (mmLoc.maxLoc.x + templateBitmap.width / 2.0).toFloat(),
                                    (mmLoc.maxLoc.y + templateBitmap.height / 2.0).toFloat()
                                ),
                                score = mmLoc.maxVal,
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        } finally {
            if (ownsSourceBitmap) sourceBitmap.recycle()
        }
    }

    private fun formatScore(score: Double): String = "%.4f".format(score)
}

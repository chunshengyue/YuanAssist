package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.PiJingZhanJiConfig
import com.example.yuanassist.model.PiJingZhanJiGameVariant
import com.example.yuanassist.tableocr.PaddleTextRecognizer
import com.example.yuanassist.tableocr.PaddleTextResult
import com.example.yuanassist.utils.RunLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import java.io.InputStreamReader
import kotlin.math.min

class PiJingZhanJiActivityEngine(
    private val service: AccessibilityService,
    private val config: PiJingZhanJiConfig,
    private val onCompleted: (Boolean, String?) -> Unit
) {

    private val engine = AutoTaskEngine(service)
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val windowManager =
        service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private var isRunning = false
    private var taskLoopCount = 0
    private var currentAgentIndex = 0
    private var quizMatcher: PiJingZhanJiQuizMatcher? = null
    private val taskQueueStates = mutableListOf<TaskQueueState>()
    private var taskQueueGeneration = 0
    private var taskQueueNeedsRebuild = true
    private var currentTaskContext: TaskExecutionContext? = null

    fun start(): Boolean {
        if (isRunning) return false
        isRunning = true
        engine.debugRoiEnabled = config.activityDebugModeEnabled
        engine.verboseLoggingEnabled = true
        engine.diagnosticLoggingEnabled = config.activityDebugModeEnabled
        engine.globalDelayOffsetMs = config.lowSpecDelayMs
        engine.setRunLogScope("披荆斩棘", "活动页")
        logFlow("第二模块开始")
        runPlan(ENTER_SCRIPT, required = true) { success, message, completion ->
            if (!isRunning) return@runPlan
            if (!success) {
                finish(false, "进入活动页失败：$message")
            } else if (completion?.terminalTaskId == ENTER_ALREADY_IN_TASK_PAGE_ID) {
                performBackToActivityPage {
                    enterAgentTaskPage()
                }
            } else {
                enterAgentTaskPage()
            }
        }
        return true
    }

    fun stop() {
        isRunning = false
        engine.stop()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
    }

    private fun runTaskPageLoop() {
        if (!isRunning) return
        taskLoopCount += 1
        if (taskLoopCount > MAX_TASK_LOOP_COUNT) {
            finish(false, "活动页任务循环次数过多，已停止")
            return
        }
        runPlan(CLAIM_SCRIPT, required = false) { _, _, _ ->
            if (!isRunning) return@runPlan
            handler.postDelayed(
                {
                    if (!isRunning) return@postDelayed
                    handleTaskPageGoFlow()
                },
                TASK_PAGE_GO_SCAN_DELAY_MS + config.lowSpecDelayMs
            )
        }
    }

    private fun handleTaskPageGoFlow() {
        scanTaskPageGoSlots { snapshot ->
            if (!isRunning) return@scanTaskPageGoSlots
            reconcileTaskQueue(snapshot)
            val nextQueueIndex = taskQueueStates.indexOfFirst { it == TaskQueueState.PENDING }
            if (nextQueueIndex < 0) {
                clearCurrentTaskContext()
                if (snapshot.availableSlots.isEmpty()) {
                    handleNoGoTaskSlots()
                } else if (config.refreshUnsupportedActivityTask) {
                    logFlow("当前轮任务均不可完成，执行刷新 queue=${formatTaskQueueStates()}")
                    runPlan(REFRESH_SCRIPT, required = false) { _, _, _ -> handleRefreshTaskResult() }
                } else {
                    logFlow("当前轮任务均不可完成，切换下一个密探 queue=${formatTaskQueueStates()}")
                    switchAgentOrFinish()
                }
                return@scanTaskPageGoSlots
            }
            val slot = snapshot.availableSlots.getOrNull(nextQueueIndex)
            if (slot == null) {
                logFlow(
                    "任务队列与页面槽位不一致，重建后重试 count=${snapshot.availableSlots.size} " +
                        "queue=${formatTaskQueueStates()}"
                )
                taskQueueNeedsRebuild = true
                runTaskPageLoop()
                return@scanTaskPageGoSlots
            }
            currentTaskContext = TaskExecutionContext(
                queueIndex = nextQueueIndex,
                visibleTaskCountBeforeEnter = snapshot.availableSlots.size,
                generation = taskQueueGeneration
            )
            logFlow(
                "选择任务 generation=$taskQueueGeneration queueIndex=${nextQueueIndex + 1}/${snapshot.availableSlots.size} " +
                    "queue=${formatTaskQueueStates()} point=${formatPoint(slot.clickPoint)}"
            )
            clickScreenPoint(slot.clickPoint) {
                handler.postDelayed({
                    if (!isRunning) return@postDelayed
                    handleCurrentActivityTask()
                }, GO_TASK_TRANSITION_DELAY_MS + config.lowSpecDelayMs)
            }
        }
    }

    private fun handleNoGoTaskSlots() {
        logFlow("任务页未识别到可前往任务，尝试补领一键领取")
        runPlan(CLAIM_SCRIPT, required = false) { _, _, completion ->
            if (!isRunning) return@runPlan
            if (completion?.terminalTaskId == CLAIM_DISMISS_TASK_ID) {
                logFlow("任务页补领一键领取成功，等待下一轮任务")
                handler.postDelayed(
                    {
                        if (!isRunning) return@postDelayed
                        runTaskPageLoop()
                    },
                    TASK_PAGE_GO_SCAN_DELAY_MS + config.lowSpecDelayMs
                )
            } else {
                logFlow("任务页无前往且未识别到一键领取，切换下一个密探")
                switchAgentOrFinish()
            }
        }
    }

    private fun handleCurrentActivityTask() {
        handleQuizTask { quizHandled ->
            if (!isRunning) return@handleQuizTask
            if (quizHandled) {
                handleCompletedTaskSuccess()
                runTaskPageLoop()
                return@handleQuizTask
            }
            runPlan(SWEEP_SCRIPT, required = true) { sweepHandled, _, _ ->
                if (!isRunning) return@runPlan
                if (sweepHandled) {
                    handleCompletedTaskSuccess()
                    runTaskPageLoop()
                } else {
                    handleMysteryTask { mysteryHandled ->
                        if (!isRunning) return@handleMysteryTask
                        if (mysteryHandled) {
                            handleCompletedTaskSuccess()
                            runTaskPageLoop()
                        } else {
                            handleUnsupportedTask()
                        }
                    }
                }
            }
        }
    }

    private fun handleQuizTask(onDone: (Boolean) -> Unit) {
        captureScreenshot(
            onSuccess = { screenshot ->
                scope.launch {
                    val marker = recognizeCrop(screenshot, QUIZ_MARKER_SPEC)
                    if (!marker.contains("正确") && !marker.contains("確")) {
                        screenshot.recycle()
                        withContext(Dispatchers.Main) {
                            if (isRunning) onDone(false)
                        }
                        return@launch
                    }
                    val question = recognizeCrop(screenshot, QUESTION_SPEC)
                    val options = OPTION_SPECS.map { spec -> recognizeCrop(screenshot, spec) }
                    screenshot.recycle()
                    val matcher = try {
                        loadQuizMatcher()
                    } catch (t: Throwable) {
                        logError("披荆答题", "题库加载失败", t)
                        null
                    }
                    logOcr(
                        "答题页 marker=${formatLogText(marker)} " +
                            "question=${formatLogText(question)} " +
                            "options=${options.joinToString(" / ") { formatLogText(it, 18) }}"
                    )
                    val matchedEntry = matcher?.findEntry(question, options)
                    val answer = matchedEntry?.answer
                    val optionIndex = if (matcher != null && matchedEntry != null) {
                        matcher.chooseOptionIndex(matchedEntry, options)
                    } else {
                        null
                    }
                    withContext(Dispatchers.Main) {
                        if (!isRunning) return@withContext
                        if (answer == null) {
                            logQuiz(
                                "未匹配 question=${formatLogText(question)} " +
                                    "options=${options.joinToString("/") { formatLogText(it, 14) }}"
                            )
                            onDone(false)
                        } else if (optionIndex == null) {
                            logQuiz(
                                "题目命中但选项未命中 question=${formatLogText(question)} " +
                                    "options=${options.joinToString("/") { formatLogText(it, 14) }}"
                            )
                            onDone(false)
                        } else {
                            logQuiz(
                                "命中 answer=${formatLogText(answer, 18)} " +
                                    "option=${optionIndex + 1} question=${formatLogText(question, 24)}"
                            )
                            val point = OPTION_CLICK_POINTS[optionIndex]
                            val (x, y) = realCoordinate(point.x, point.y, point.align)
                            engine.dispatchClick(x, y) {
                                handler.postDelayed({
                                    runPlan(QUIZ_EXIT_SCRIPT, required = false) { _, _, _ -> onDone(true) }
                                }, 800L + config.lowSpecDelayMs)
                            }
                        }
                    }
                }
            },
            onFailure = {
                logError("披荆OCR", "答题截图失败 errorCode=$it")
                onDone(false)
            }
        )
    }

    private fun handleMysteryTask(onDone: (Boolean) -> Unit) {
        runPlan(MYSTERY_CARD_SCRIPT, required = true) { cardClicked, _, _ ->
            if (!isRunning) return@runPlan
            if (!cardClicked) {
                onDone(false)
                return@runPlan
            }
            handler.postDelayed({
                pollMysteryLoop(0, onDone)
            }, MYSTERY_INITIAL_POLL_DELAY_MS + config.lowSpecDelayMs)
        }
    }

    private fun pollMysteryLoop(depth: Int, onDone: (Boolean) -> Unit) {
        if (!isRunning) return
        if (depth >= MAX_MYSTERY_DEPTH) {
            logMystery("轮询 depth=$depth exceed-limit action=返回任务页")
            confirmMysteryReturnedToTaskPage(onDone)
            return
        }
        captureScreenshot(
            onSuccess = { screenshot ->
                scope.launch {
                    val scan = scanMysteryTargets(screenshot)
                    screenshot.recycle()
                    withContext(Dispatchers.Main) {
                        if (!isRunning) return@withContext
                        when {
                            scan.exitHitChars.size >= MYSTERY_EXIT_MIN_HIT_COUNT -> {
                                logMystery(
                                    "轮询 depth=$depth exit raw=${scan.scanSummary} " +
                                        "hits=${formatHitChars(scan.exitHitChars)} " +
                                        "action=已退出神秘事件，继续任务页"
                                )
                                onDone(true)
                            }
                            scan.battleHit != null -> {
                                logMystery(
                                    "轮询 depth=$depth battle raw=${formatLogText(scan.battleHit.text)} " +
                                        "matched=${scan.battleHit.matchedTarget} " +
                                        "action=点击战斗 ${formatPoint(scan.battleHit.point)}"
                                )
                                clickScreenPoint(scan.battleHit.point) {
                                    handler.postDelayed(
                                        { checkMysteryBattleManual(depth, onDone) },
                                        MYSTERY_BATTLE_INITIAL_CHECK_DELAY_MS + config.lowSpecDelayMs
                                    )
                                }
                            }
                            scan.rewardHit != null -> {
                                logMystery(
                                    "轮询 depth=$depth reward raw=${formatLogText(scan.rewardHit.lineText)} " +
                                        "hits=${formatHitChars(scan.rewardHit.hitChars)} " +
                                        "action=点击铜匣 ${formatPoint(scan.rewardHit.point)}"
                                )
                                clickScreenPoint(scan.rewardHit.point) {
                                    handler.postDelayed(
                                        { pollMysteryLoop(depth + 1, onDone) },
                                        MYSTERY_REWARD_REENTRY_DELAY_MS + config.lowSpecDelayMs
                                    )
                                }
                            }
                            scan.cardPoint != null -> {
                                logMystery(
                                    "轮询 depth=$depth card template=card.png " +
                                        "action=点击卡牌 ${formatPoint(scan.cardPoint)}"
                                )
                                clickScreenPoint(scan.cardPoint) {
                                    handler.postDelayed(
                                        { pollMysteryLoop(depth + 1, onDone) },
                                        MYSTERY_CARD_REENTRY_DELAY_MS + config.lowSpecDelayMs
                                    )
                                }
                            }
                            scan.terminalHit != null -> {
                                logMystery(
                                    "轮询 depth=$depth terminal raw=${formatLogText(scan.terminalHit.lineText)} " +
                                        "hits=${formatHitChars(scan.terminalHit.hitChars)} " +
                                        "action=点击终点 ${formatPoint(scan.terminalHit.point)}"
                                )
                                clickScreenPoint(scan.terminalHit.point) {
                                    handler.postDelayed(
                                        { recheckMysteryAfterTerminal(depth, onDone) },
                                        MYSTERY_TERMINAL_RETURN_DELAY_MS + config.lowSpecDelayMs
                                    )
                                }
                            }
                            else -> {
                                logMystery(
                                    "轮询 depth=$depth fallback raw=${scan.scanSummary} action=点击剧情/移动"
                                )
                                clickBase(1018.5f, 1864.5f) {
                                    handler.postDelayed(
                                        { pollMysteryLoop(depth + 1, onDone) },
                                        MYSTERY_STORY_MOVE_POLL_DELAY_MS + config.lowSpecDelayMs
                                    )
                                }
                            }
                        }
                    }
                }
            },
            onFailure = {
                logError("披荆OCR", "神秘事件截图失败 errorCode=$it")
                onDone(false)
            }
        )
    }

    private fun recheckMysteryAfterTerminal(depth: Int, onDone: (Boolean) -> Unit) {
        if (!isRunning) return
        captureScreenshot(
            onSuccess = { screenshot ->
                scope.launch {
                    val scan = scanMysteryTargets(screenshot)
                    screenshot.recycle()
                    withContext(Dispatchers.Main) {
                        if (!isRunning) return@withContext
                        val stillInMystery = scan.battleHit != null ||
                            scan.rewardHit != null ||
                            scan.cardPoint != null ||
                            scan.terminalHit != null
                        if (stillInMystery) {
                            logMystery(
                                "终点复检 depth=$depth raw=${scan.scanSummary} action=仍在神秘事件，继续轮询"
                            )
                            pollMysteryLoop(depth + 1, onDone)
                        } else {
                            logMystery(
                                "终点复检 depth=$depth raw=${scan.scanSummary} action=已退出神秘事件，继续任务页"
                            )
                            onDone(true)
                        }
                    }
                }
            },
            onFailure = {
                logError("披荆OCR", "神秘事件终点复检截图失败 errorCode=$it")
                onDone(true)
            }
        )
    }

    private fun checkMysteryBattleManual(depth: Int, onDone: (Boolean) -> Unit) {
        if (!isRunning) return
        captureScreenshot(
            onSuccess = { screenshot ->
                scope.launch {
                    try {
                        val scan = scanCharsInSpec(screenshot, MYSTERY_MANUAL_BUTTON_SPEC, MYSTERY_MANUAL_TARGET_CHARS)
                        screenshot.recycle()
                        withContext(Dispatchers.Main) {
                            if (!isRunning) return@withContext
                            val matched = scan.point != null && scan.hitChars.size >= MYSTERY_MANUAL_MIN_HIT_COUNT
                            logOcr(
                                "神秘战斗手动检查 raw=${formatLogText(scan.rawText)} " +
                                    "hits=${formatHitChars(scan.hitChars)} " +
                                    "action=${if (matched) "点击手动 ${formatPoint(scan.point!!)}" else "继续等待确定"}"
                            )
                            if (matched) {
                                clickScreenPoint(scan.point!!) {
                                    handler.postDelayed(
                                        { waitMysteryBattleEnd(depth, 0, onDone) },
                                        MYSTERY_BATTLE_CONFIRM_POLL_INTERVAL_MS + config.lowSpecDelayMs
                                    )
                                }
                            } else {
                                handler.postDelayed(
                                    { waitMysteryBattleEnd(depth, 0, onDone) },
                                    MYSTERY_BATTLE_CONFIRM_POLL_INTERVAL_MS + config.lowSpecDelayMs
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        if (!screenshot.isRecycled) screenshot.recycle()
                        withContext(Dispatchers.Main) {
                            if (!isRunning) return@withContext
                            logError("披荆OCR", "神秘事件手动检查识别失败", t)
                            handler.postDelayed(
                                { waitMysteryBattleEnd(depth, 0, onDone) },
                                MYSTERY_BATTLE_CONFIRM_POLL_INTERVAL_MS + config.lowSpecDelayMs
                            )
                        }
                    }
                }
            },
            onFailure = {
                logError("披荆OCR", "神秘事件手动检查截图失败 errorCode=$it")
                handler.postDelayed(
                    { waitMysteryBattleEnd(depth, 0, onDone) },
                    MYSTERY_BATTLE_CONFIRM_POLL_INTERVAL_MS + config.lowSpecDelayMs
                )
            }
        )
    }

    private fun waitMysteryBattleEnd(depth: Int, attempt: Int, onDone: (Boolean) -> Unit) {
        if (!isRunning) return
        if (attempt >= MAX_MYSTERY_BATTLE_POLLS) {
            logError("披荆神秘", "战斗确定等待超时 poll=$attempt")
            onDone(false)
            return
        }
        captureScreenshot(
            onSuccess = { screenshot ->
                scope.launch {
                    try {
                        val scan = scanCharsInSpec(screenshot, MYSTERY_BATTLE_BUTTON_SPEC, MYSTERY_CONFIRM_TARGET_CHARS)
                        screenshot.recycle()
                        withContext(Dispatchers.Main) {
                            if (!isRunning) return@withContext
                            val matched = scan.point != null && scan.hitChars.size >= MYSTERY_CONFIRM_MIN_HIT_COUNT
                            logOcr(
                                "神秘战斗确定检查 raw=${formatLogText(scan.rawText)} " +
                                    "hits=${formatHitChars(scan.hitChars)} " +
                                    "action=${if (matched) "点击确定 ${formatPoint(scan.point!!)}" else "继续等待"}"
                            )
                            if (matched) {
                                clickScreenPoint(scan.point!!) {
                                    handler.postDelayed(
                                        { pollMysteryLoop(depth + 1, onDone) },
                                        MYSTERY_BATTLE_CONFIRM_REENTRY_DELAY_MS + config.lowSpecDelayMs
                                    )
                                }
                            } else {
                                handler.postDelayed(
                                    { waitMysteryBattleEnd(depth, attempt + 1, onDone) },
                                    MYSTERY_BATTLE_CONFIRM_POLL_INTERVAL_MS + config.lowSpecDelayMs
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        if (!screenshot.isRecycled) screenshot.recycle()
                        withContext(Dispatchers.Main) {
                            if (!isRunning) return@withContext
                            logError("披荆OCR", "神秘事件确定检查识别失败", t)
                            handler.postDelayed(
                                { waitMysteryBattleEnd(depth, attempt + 1, onDone) },
                                MYSTERY_BATTLE_CONFIRM_POLL_INTERVAL_MS + config.lowSpecDelayMs
                            )
                        }
                    }
                }
            },
            onFailure = {
                logError("披荆OCR", "神秘事件确定检查截图失败 errorCode=$it")
                handler.postDelayed(
                    { waitMysteryBattleEnd(depth, attempt + 1, onDone) },
                    MYSTERY_BATTLE_CONFIRM_POLL_INTERVAL_MS + config.lowSpecDelayMs
                )
            }
        )
    }

    private fun scanCharsInSpec(
        screenshot: Bitmap,
        spec: CaptureSpec,
        targetChars: List<Char>
    ): CharSpecOcrResult {
        val crop = cropBySpec(screenshot, spec)
        return try {
            val result = PaddleTextRecognizer.recognize(service, crop)
            val rawText = result.text.trim()
            val bestLine = result.blocks
                .flatMap { it.lines }
                .mapNotNull { line ->
                    val normalizedLineText = line.text.filterNot { it.isWhitespace() }
                    val hitChars = targetChars.filter { normalizedLineText.contains(it) }
                    if (hitChars.isEmpty()) return@mapNotNull null
                    CharOcrHitCandidate(
                        lineText = line.text,
                        normalizedLineText = normalizedLineText,
                        hitChars = hitChars,
                        boundingBox = line.boundingBox
                    )
                }
                .maxWithOrNull(
                    compareBy<CharOcrHitCandidate>({ it.hitChars.size }, { it.boundingBox.width() * it.boundingBox.height() })
                )
            val point = bestLine?.boundingBox?.let { rect ->
                val (centerX, centerY) = screenshotCoordinate(screenshot, spec.x, spec.y, spec.align)
                val scale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
                val left = centerX - spec.w * scale / 2f
                val top = centerY - spec.h * scale / 2f
                screenshotRectCenterToScreen(
                    screenshot,
                    Rect(
                        (left + rect.left).toInt(),
                        (top + rect.top).toInt(),
                        (left + rect.right).toInt(),
                        (top + rect.bottom).toInt()
                    )
                )
            }
            CharSpecOcrResult(
                rawText = rawText,
                hitChars = bestLine?.hitChars.orEmpty(),
                point = point
            )
        } finally {
            crop.recycle()
        }
    }

    private fun scanMysteryTargets(screenshot: Bitmap): MysteryScanResult {
        val result = PaddleTextRecognizer.recognize(service, screenshot)
        val exitHitChars = findOcrHitCharsInFullText(result.text, MYSTERY_EXIT_TARGET_CHARS)
        return MysteryScanResult(
            battleHit = recognizeMysteryBattleButton(screenshot),
            rewardHit = findOcrHitByChars(
                screenshot = screenshot,
                result = result,
                targetChars = listOf('小', '铜', '銅', '匣'),
                minHitCount = 1
            ),
            cardPoint = recognizeMysteryCard(screenshot),
            terminalHit = findOcrHitByChars(
                screenshot = screenshot,
                result = result,
                targetChars = listOf('终', '點', '点'),
                minHitCount = 1
            ),
            exitHitChars = exitHitChars,
            scanSummary = formatLogText(result.text, 80)
        )
    }

    private fun findOcrHitCharsInFullText(rawText: String, targetChars: List<Char>): List<Char> {
        val normalizedText = rawText.filterNot { it.isWhitespace() }
        return targetChars.filter { normalizedText.contains(it) }
    }

    private fun confirmMysteryReturnedToTaskPage(onDone: (Boolean) -> Unit) {
        if (!isRunning) return
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        if (!success) logError("披荆神秘", "终点返回失败")
        handler.postDelayed({
            if (!isRunning) return@postDelayed
            captureScreenshot(
                onSuccess = { screenshot ->
                    scope.launch {
                        val result = PaddleTextRecognizer.recognize(service, screenshot)
                        val returnHit = findOcrHitByChars(
                            screenshot = screenshot,
                            result = result,
                            targetChars = listOf('今', '日', '已', '从'),
                            minHitCount = 2
                        )
                        screenshot.recycle()
                        withContext(Dispatchers.Main) {
                            if (!isRunning) return@withContext
                            if (returnHit != null) {
                                logMystery(
                                    "终点返回确认 raw=${formatLogText(returnHit.lineText)} " +
                                        "hits=${formatHitChars(returnHit.hitChars)} action=已回到二级页面"
                                )
                                onDone(true)
                            } else {
                                onDone(false)
                            }
                        }
                    }
                },
                onFailure = {
                    logError("披荆OCR", "神秘事件终点回页确认截图失败 errorCode=$it")
                    onDone(false)
                }
            )
        }, MYSTERY_TERMINAL_RETURN_DELAY_MS + config.lowSpecDelayMs)
    }

    private fun handleUnsupportedTask() {
        logFlow("当前活动页任务无法自动完成")
        performBackToTaskPage {
            if (!isRunning) return@performBackToTaskPage
            handleUnsupportedTaskOnTaskPage()
        }
    }

    private fun handleUnsupportedTaskOnTaskPage() {
        val blocked = markCurrentTaskBlocked()
        if (blocked && taskQueueStates.any { it == TaskQueueState.PENDING }) {
            logFlow("当前任务无法完成，继续尝试下一任务 queue=${formatTaskQueueStates()}")
            runTaskPageLoop()
            return
        }
        if (config.refreshUnsupportedActivityTask) {
            logFlow("配置允许刷新无法完成任务，执行刷新 queue=${formatTaskQueueStates()}")
            runPlan(REFRESH_SCRIPT, required = false) { _, _, _ -> handleRefreshTaskResult() }
        } else {
            logFlow("仅记录日志，切换下一个密探")
            switchAgentOrFinish()
        }
    }

    private fun handleRefreshTaskResult() {
        clearCurrentTaskContext()
        taskQueueNeedsRebuild = true
        taskQueueStates.clear()
        handler.postDelayed(
            {
                if (!isRunning) return@postDelayed
                detectRefreshLimitReached { limitReached ->
                    if (!isRunning) return@detectRefreshLimitReached
                    if (limitReached) {
                        logFlow("刷新次数已达上限，执行双返回并切换下一个密探")
                        performDoubleBackAfterRefresh {
                            if (!isRunning) return@performDoubleBackAfterRefresh
                            switchAgentFromActivityPageOrFinish()
                        }
                    } else {
                        runTaskPageLoop()
                    }
                }
            },
            REFRESH_LIMIT_CHECK_DELAY_MS + config.lowSpecDelayMs
        )
    }

    private fun detectRefreshLimitReached(onDone: (Boolean) -> Unit) {
        captureScreenshot(
            onSuccess = { screenshot ->
                scope.launch {
                    val crop = cropBySpec(screenshot, REFRESH_LIMIT_CHECK_SPEC)
                    try {
                        val rawText = PaddleTextRecognizer.recognize(service, crop).text.trim()
                        val normalizedText = rawText.filterNot { it.isWhitespace() }
                        val hitChars = REFRESH_LIMIT_TARGET_CHARS.filter { normalizedText.contains(it) }
                        screenshot.recycle()
                        withContext(Dispatchers.Main) {
                            if (!isRunning) return@withContext
                            val matched = hitChars.size >= REFRESH_LIMIT_MIN_HIT_COUNT
                            logOcr(
                                "刷新上限检查 raw=${formatLogText(rawText)} " +
                                    "hits=${formatHitChars(hitChars)} " +
                                    "action=${if (matched) "双返回切换密探" else "继续任务页循环"}"
                            )
                            onDone(matched)
                        }
                    } catch (t: Throwable) {
                        screenshot.recycle()
                        withContext(Dispatchers.Main) {
                            if (!isRunning) return@withContext
                            logError("披荆OCR", "刷新上限检查识别失败", t)
                            onDone(false)
                        }
                    } finally {
                        crop.recycle()
                        if (!screenshot.isRecycled) screenshot.recycle()
                    }
                }
            },
            onFailure = {
                logError("披荆OCR", "刷新上限检查截图失败 errorCode=$it")
                onDone(false)
            }
        )
    }

    private fun performDoubleBackAfterRefresh(onDone: () -> Unit) {
        val firstSuccess = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        if (!firstSuccess) logError("披荆流程", "刷新上限首次返回失败")
        handler.postDelayed(
            {
                if (!isRunning) return@postDelayed
                val secondSuccess = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                if (!secondSuccess) logError("披荆流程", "刷新上限二次返回失败")
                handler.postDelayed(onDone, REFRESH_LIMIT_BACK_INTERVAL_MS + config.lowSpecDelayMs)
            },
            REFRESH_LIMIT_BACK_INTERVAL_MS + config.lowSpecDelayMs
        )
    }

    private fun switchAgentFromActivityPageOrFinish() {
        resetTaskQueueState()
        currentAgentIndex += 1
        if (currentAgentIndex >= AGENT_ENTRY_POINTS.size) {
            finish(true, null)
            return
        }
        enterAgentTaskPage()
    }

    private fun switchAgentOrFinish() {
        resetTaskQueueState()
        currentAgentIndex += 1
        if (currentAgentIndex >= AGENT_ENTRY_POINTS.size) {
            finish(true, null)
            return
        }
        performBackToActivityPage {
            enterAgentTaskPage()
        }
    }

    private fun enterAgentTaskPage() {
        if (!isRunning) return
        resetTaskQueueState()
        val point = AGENT_ENTRY_POINTS.getOrNull(currentAgentIndex)
        if (point == null) {
            finish(true, null)
            return
        }
        logFlow("进入第 ${currentAgentIndex + 1} 个披荆斩棘密探入口")
        clickBase(point) {
            handler.postDelayed(
                {
                    runPlan(CONFIRM_TASK_PAGE_SCRIPT, required = true) { success, message, _ ->
                        if (!isRunning) return@runPlan
                        if (success) {
                            runTaskPageLoop()
                        } else {
                            finish(false, "进入密探任务页失败：$message")
                        }
                    }
                },
                1000L + config.lowSpecDelayMs
            )
        }
    }

    private fun performBackToTaskPage(onDone: () -> Unit) {
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        if (!success) logError("披荆流程", "返回密探任务页失败")
        handler.postDelayed(onDone, 800L + config.lowSpecDelayMs)
    }

    private fun performBackToActivityPage(onDone: () -> Unit) {
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        if (!success) logError("披荆流程", "返回披荆斩棘活动页失败")
        handler.postDelayed(onDone, 800L + config.lowSpecDelayMs)
    }

    private fun scanTaskPageGoSlots(onDone: (TaskPageSnapshot) -> Unit) {
        captureScreenshot(
            onSuccess = { screenshot ->
                scope.launch {
                    try {
                        val slots = TASK_PAGE_GO_SLOT_SPECS.mapIndexed { index, spec ->
                            scanTaskPageGoSlot(screenshot, spec, TASK_PAGE_GO_SLOT_POINTS[index])
                        }
                        screenshot.recycle()
                        withContext(Dispatchers.Main) {
                            if (!isRunning) return@withContext
                            val snapshot = TaskPageSnapshot(slots)
                            logOcr(
                                "任务页槽位 " +
                                    snapshot.slots.mapIndexed { index, slot ->
                                        "${index + 1}=${formatLogText(slot.rawText, 12)}:${if (slot.hasGo) "前往" else "无"}"
                                    }.joinToString(" / ")
                            )
                            onDone(snapshot)
                        }
                    } catch (t: Throwable) {
                        if (!screenshot.isRecycled) screenshot.recycle()
                        withContext(Dispatchers.Main) {
                            if (!isRunning) return@withContext
                            logError("披荆OCR", "任务页槽位识别失败", t)
                            onDone(TaskPageSnapshot(emptyList()))
                        }
                    }
                }
            },
            onFailure = {
                logError("披荆OCR", "任务页截图失败 errorCode=$it")
                onDone(TaskPageSnapshot(emptyList()))
            }
        )
    }

    private fun reconcileTaskQueue(snapshot: TaskPageSnapshot) {
        val availableCount = snapshot.availableSlots.size
        if (taskQueueNeedsRebuild || taskQueueStates.size != availableCount) {
            taskQueueGeneration += 1
            taskQueueStates.clear()
            repeat(availableCount) { taskQueueStates += TaskQueueState.PENDING }
            taskQueueNeedsRebuild = false
            clearCurrentTaskContext()
            logFlow(
                "重建任务队列 generation=$taskQueueGeneration count=$availableCount queue=${formatTaskQueueStates()}"
            )
        }
    }

    private fun handleCompletedTaskSuccess() {
        val context = currentTaskContext ?: return
        if (context.generation != taskQueueGeneration) {
            taskQueueNeedsRebuild = true
            clearCurrentTaskContext()
            return
        }
        if (context.visibleTaskCountBeforeEnter <= 1) {
            taskQueueStates.clear()
            taskQueueNeedsRebuild = true
            logFlow("任务完成后触发整轮刷新，等待重建任务队列")
        } else if (context.queueIndex in taskQueueStates.indices) {
            taskQueueStates.removeAt(context.queueIndex)
            logFlow("任务完成，移除当前队列项 queue=${formatTaskQueueStates()}")
        } else {
            taskQueueNeedsRebuild = true
        }
        clearCurrentTaskContext()
    }

    private fun markCurrentTaskBlocked(): Boolean {
        val context = currentTaskContext ?: return false
        if (context.generation != taskQueueGeneration) {
            taskQueueNeedsRebuild = true
            clearCurrentTaskContext()
            return false
        }
        if (context.queueIndex !in taskQueueStates.indices) {
            clearCurrentTaskContext()
            return false
        }
        taskQueueStates[context.queueIndex] = TaskQueueState.BLOCKED
        logFlow("任务标记不可完成 queue=${formatTaskQueueStates()}")
        clearCurrentTaskContext()
        return true
    }

    private fun clearCurrentTaskContext() {
        currentTaskContext = null
    }

    private fun resetTaskQueueState() {
        clearCurrentTaskContext()
        taskQueueStates.clear()
        taskQueueNeedsRebuild = true
    }

    private fun formatTaskQueueStates(): String =
        if (taskQueueStates.isEmpty()) "(空)"
        else taskQueueStates.joinToString("/") { state ->
            when (state) {
                TaskQueueState.PENDING -> "P"
                TaskQueueState.BLOCKED -> "B"
            }
        }

    private fun scanTaskPageGoSlot(
        screenshot: Bitmap,
        spec: CaptureSpec,
        fallbackPoint: BasePoint
    ): TaskPageSlotSnapshot {
        val cropRect = screenshotRectForSpec(screenshot, spec)
        val crop = Bitmap.createBitmap(
            screenshot,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )
        return try {
            val result = PaddleTextRecognizer.recognize(service, crop)
            val hitRect = result.blocks
                .flatMap { it.lines }
                .mapNotNull { line ->
                    val normalized = line.text.filterNot { it.isWhitespace() }
                    val matched = normalized.contains("前往") || (normalized.contains('前') && normalized.contains('往'))
                    if (!matched) return@mapNotNull null
                    Rect(line.boundingBox).apply { offset(cropRect.left, cropRect.top) }
                }
                .maxByOrNull { rect -> rect.width() * rect.height() }
            TaskPageSlotSnapshot(
                rawText = result.text.trim(),
                hasGo = hitRect != null,
                clickPoint = hitRect?.let { screenshotRectCenterToScreen(screenshot, it) }
                    ?: realCoordinate(fallbackPoint.x, fallbackPoint.y, fallbackPoint.align)
            )
        } finally {
            crop.recycle()
        }
    }

    private fun screenshotRectForSpec(screenshot: Bitmap, spec: CaptureSpec): Rect {
        val (centerX, centerY) = screenshotCoordinate(screenshot, spec.x, spec.y, spec.align)
        val scale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val width = (spec.w * scale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.width)
        val height = (spec.h * scale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.height)
        val left = (centerX - width / 2f).toInt().coerceIn(0, screenshot.width - 1)
        val top = (centerY - height / 2f).toInt().coerceIn(0, screenshot.height - 1)
        val right = (left + width).coerceAtMost(screenshot.width)
        val bottom = (top + height).coerceAtMost(screenshot.height)
        return Rect(left, top, right, bottom)
    }

    private fun runPlan(
        scriptName: String,
        required: Boolean,
        onDone: (Boolean, String, DailyPlanCompletion?) -> Unit
    ) {
        val plan = loadPlan(scriptName)?.let { applyGameVariantToPlan(scriptName, it) }
        if (plan == null) {
            onDone(false, "脚本不存在：$scriptName", null)
            return
        }
        engine.startPlan(
            plan = plan,
            onCompleted = { _, _ -> },
            onCompletedDetailed = { completion ->
                if (!isRunning) return@startPlan
                val success = if (required) completion.success else true
                onDone(success, completion.message, completion)
            },
            scriptFileName = scriptName,
        )
    }

    private fun applyGameVariantToPlan(scriptName: String, plan: DailyTaskPlan): DailyTaskPlan {
        if (scriptName != ENTER_SCRIPT) return plan
        return when (config.gameVariant) {
            PiJingZhanJiGameVariant.RU_YUAN -> {
                val entryTargetChars = ACTIVITY_ENTRY_TARGET_CHARS_RU_YUAN
                plan.copy(
                    tasks = plan.tasks.map { task ->
                        if (task.id !in ACTIVITY_ENTRY_OCR_TASK_IDS) return@map task
                        val params = task.params ?: return@map task
                        task.copy(params = params.copy(target_chars = entryTargetChars))
                    }
                )
            }
            PiJingZhanJiGameVariant.CODE_NAME_YUAN -> {
                plan.copy(
                    asset_template_dir = null,
                    tasks = plan.tasks.map { task ->
                        if (task.id !in ACTIVITY_ENTRY_OCR_TASK_IDS) return@map task
                        val params = task.params ?: return@map task
                        task.copy(
                            action = "MATCH_TEMPLATE",
                            params = params.copy(
                                template_name = ACTIVITY_ENTRY_TEMPLATE_CODE_NAME_YUAN,
                                threshold = ACTIVITY_ENTRY_TEMPLATE_THRESHOLD,
                                roi = params.roi?.copy(h = ACTIVITY_ENTRY_TEMPLATE_ROI_HEIGHT)
                            )
                        )
                    }
                )
            }
        }
    }

    private fun loadPlan(fileName: String): DailyTaskPlan? {
        return try {
            service.assets.open("daily_scripts/$fileName").use { input ->
                gson.fromJson(InputStreamReader(input, Charsets.UTF_8), DailyTaskPlan::class.java)
            }
        } catch (t: Throwable) {
            logError("披荆流程", "加载活动脚本失败 file=$fileName", t)
            null
        }
    }

    private fun loadQuizMatcher(): PiJingZhanJiQuizMatcher {
        quizMatcher?.let { return it }
        val entries = service.assets.open("pi_jing_zhan_ji/qadb.json").use { input ->
            val type = object : TypeToken<List<PiJingZhanJiQuizEntry>>() {}.type
            gson.fromJson<List<PiJingZhanJiQuizEntry>>(InputStreamReader(input, Charsets.UTF_8), type)
        }
        return PiJingZhanJiQuizMatcher(entries).also { quizMatcher = it }
    }

    private suspend fun recognizeCrop(screenshot: Bitmap, spec: CaptureSpec): String {
        val crop = cropBySpec(screenshot, spec)
        return try {
            PaddleTextRecognizer.recognize(service, crop).text.trim()
        } finally {
            crop.recycle()
        }
    }

    private fun captureScreenshot(onSuccess: (Bitmap) -> Unit, onFailure: (Int) -> Unit) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                    val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBitmap?.recycle()
                    buffer.close()
                    if (softwareBitmap == null) onFailure(-1) else onSuccess(softwareBitmap)
                }

                override fun onFailure(errorCode: Int) {
                    onFailure(errorCode)
                }
            }
        )
    }

    private fun cropBySpec(screenshot: Bitmap, spec: CaptureSpec): Bitmap {
        val (centerX, centerY) = screenshotCoordinate(screenshot, spec.x, spec.y, spec.align)
        val scale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val width = (spec.w * scale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.width)
        val height = (spec.h * scale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.height)
        val left = (centerX - width / 2f).toInt().coerceIn(0, screenshot.width - 1)
        val top = (centerY - height / 2f).toInt().coerceIn(0, screenshot.height - 1)
        val safeW = width.coerceAtMost(screenshot.width - left).coerceAtLeast(1)
        val safeH = height.coerceAtMost(screenshot.height - top).coerceAtLeast(1)
        return Bitmap.createBitmap(screenshot, left, top, safeW, safeH)
    }

    private fun clickBase(x: Float, y: Float, align: String = "center", onDone: () -> Unit) {
        val (realX, realY) = realCoordinate(x, y, align)
        engine.dispatchClick(realX, realY, onDone)
    }

    private fun clickBase(point: BasePoint, onDone: () -> Unit) {
        clickBase(point.x, point.y, point.align, onDone)
    }

    private fun clickScreenPoint(point: Pair<Float, Float>, onDone: () -> Unit) {
        engine.dispatchClick(point.first, point.second, onDone)
    }

    private fun findOcrPoint(
        screenshot: Bitmap,
        result: PaddleTextResult,
        vararg targets: String
    ): Pair<Float, Float>? {
        val rect = result.blocks
            .firstOrNull { block -> targets.any { target -> block.text.contains(target) } }
            ?.boundingBox
            ?: return null
        return screenshotRectCenterToScreen(screenshot, rect)
    }

    private fun findOcrHitByChars(
        screenshot: Bitmap,
        result: PaddleTextResult,
        targetChars: List<Char>,
        minHitCount: Int
    ): CharOcrHit? {
        val bestLine = result.blocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val normalizedLineText = line.text.filterNot { it.isWhitespace() }
                val hitChars = targetChars.filter { normalizedLineText.contains(it) }
                if (hitChars.size < minHitCount) return@mapNotNull null
                CharOcrHitCandidate(
                    lineText = line.text,
                    normalizedLineText = normalizedLineText,
                    hitChars = hitChars,
                    boundingBox = line.boundingBox
                )
            }
            .maxWithOrNull(
                compareBy<CharOcrHitCandidate>({ it.hitChars.size }, { it.boundingBox.width() * it.boundingBox.height() })
            )
            ?: return null
        return CharOcrHit(
            point = screenshotRectCenterToScreen(screenshot, bestLine.boundingBox),
            lineText = bestLine.lineText,
            normalizedLineText = bestLine.normalizedLineText,
            hitChars = bestLine.hitChars
        )
    }

    private fun screenshotRectCenterToScreen(screenshot: Bitmap, rect: Rect): Pair<Float, Float> {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val x = rect.centerX().toFloat() * screenWidth / screenshot.width
        val y = rect.centerY().toFloat() * screenHeight / screenshot.height
        return x to y
    }

    private fun recognizeMysteryBattleButton(screenshot: Bitmap): TextOcrHit? {
        val crop = cropBySpec(screenshot, MYSTERY_BATTLE_BUTTON_SPEC)
        return try {
            val result = PaddleTextRecognizer.recognize(service, crop)
            val targets = listOf("开始战斗", "開始戰鬥", "战斗", "戰鬥", "开始", "開始")
            val matched = result.blocks
                .mapNotNull { block ->
                    val target = targets.firstOrNull { candidate -> block.text.contains(candidate) } ?: return@mapNotNull null
                    block to target
                }
                .maxByOrNull { (block, _) -> block.boundingBox.width() * block.boundingBox.height() }
                ?: return null
            val rect = matched.first.boundingBox
            val (centerX, centerY) = screenshotCoordinate(
                screenshot,
                MYSTERY_BATTLE_BUTTON_SPEC.x,
                MYSTERY_BATTLE_BUTTON_SPEC.y,
                MYSTERY_BATTLE_BUTTON_SPEC.align
            )
            val scale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
            val left = centerX - MYSTERY_BATTLE_BUTTON_SPEC.w * scale / 2f
            val top = centerY - MYSTERY_BATTLE_BUTTON_SPEC.h * scale / 2f
            val point = screenshotRectCenterToScreen(
                screenshot,
                Rect(
                    (left + rect.left).toInt(),
                    (top + rect.top).toInt(),
                    (left + rect.right).toInt(),
                    (top + rect.bottom).toInt()
                )
            )
            TextOcrHit(
                point = point,
                text = matched.first.text,
                matchedTarget = matched.second
            )
        } finally {
            crop.recycle()
        }
    }

    private fun recognizeMysteryCard(screenshot: Bitmap): Pair<Float, Float>? {
        val crop = cropBySpec(screenshot, MYSTERY_CARD_SPEC)
        val template = loadTemplate("card.png") ?: run {
            crop.recycle()
            return null
        }
        val scale = min(screenshot.width / BASE_W, screenshot.height / BASE_H)
        val scaledWidth = (template.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (template.height * scale).toInt().coerceAtLeast(1)
        val scaledTemplate =
            if (scaledWidth == template.width && scaledHeight == template.height) template
            else Bitmap.createScaledBitmap(template, scaledWidth, scaledHeight, true)
        return try {
            val matchLoc = matchTemplate(crop, scaledTemplate, MYSTERY_CARD_THRESHOLD) ?: return null
            val (centerX, centerY) = screenshotCoordinate(
                screenshot,
                MYSTERY_CARD_SPEC.x,
                MYSTERY_CARD_SPEC.y,
                MYSTERY_CARD_SPEC.align
            )
            val left = centerX - MYSTERY_CARD_SPEC.w * scale / 2f
            val top = centerY - MYSTERY_CARD_SPEC.h * scale / 2f
            val rect = Rect(
                (left + matchLoc.x - scaledTemplate.width / 2f).toInt(),
                (top + matchLoc.y - scaledTemplate.height / 2f).toInt(),
                (left + matchLoc.x + scaledTemplate.width / 2f).toInt(),
                (top + matchLoc.y + scaledTemplate.height / 2f).toInt()
            )
            screenshotRectCenterToScreen(screenshot, rect)
        } finally {
            if (scaledTemplate !== template) scaledTemplate.recycle()
            crop.recycle()
        }
    }

    private fun loadTemplate(fileName: String): Bitmap? {
        return try {
            service.assets.open("daily_script_templates/pi_jing_zhan_ji_activity/$fileName").use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (t: Throwable) {
            logError("披荆神秘", "加载模板失败 file=$fileName", t)
            null
        }
    }

    private fun matchTemplate(screenBitmap: Bitmap, templateBitmap: Bitmap, threshold: Float): PointF? {
        val ownsSourceBitmap = screenBitmap.config != Bitmap.Config.ARGB_8888
        val sourceBitmap = if (ownsSourceBitmap) screenBitmap.copy(Bitmap.Config.ARGB_8888, false) else screenBitmap
        return try {
            val srcMat = Mat()
            val tmplMat = Mat()
            val resultMat = Mat()
            try {
                Utils.bitmapToMat(sourceBitmap, srcMat)
                Utils.bitmapToMat(templateBitmap, tmplMat)
                Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.cvtColor(tmplMat, tmplMat, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.matchTemplate(srcMat, tmplMat, resultMat, Imgproc.TM_CCOEFF_NORMED)
                val mmLoc = Core.minMaxLoc(resultMat)
                if (mmLoc.maxVal >= threshold) {
                    PointF(
                        (mmLoc.maxLoc.x + templateBitmap.width / 2.0).toFloat(),
                        (mmLoc.maxLoc.y + templateBitmap.height / 2.0).toFloat()
                    )
                } else {
                    null
                }
            } finally {
                srcMat.release()
                tmplMat.release()
                resultMat.release()
            }
        } finally {
            if (ownsSourceBitmap) sourceBitmap.recycle()
        }
    }

    private fun screenshotCoordinate(screenshot: Bitmap, x: Float, y: Float, align: String): Pair<Float, Float> {
        val (realX, realY) = realCoordinate(x, y, align)
        val (screenWidth, screenHeight) = getRealScreenSize()
        return realX * screenshot.width / screenWidth to realY * screenshot.height / screenHeight
    }

    private fun realCoordinate(baseX: Float, baseY: Float, align: String): Pair<Float, Float> {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val gameScale = min(screenWidth / BASE_W, screenHeight / BASE_H)
        val offsetX = (screenWidth - BASE_W * gameScale) / 2f
        val offsetY = (screenHeight - BASE_H * gameScale) / 2f
        val statusBarHeight = getRawStatusBarHeight() / 2f
        val realX = offsetX + baseX * gameScale
        val realY = when (align.lowercase()) {
            "top" -> statusBarHeight + baseY * gameScale
            "bottom" -> screenHeight - ((BASE_H - baseY) * gameScale)
            "absolute" -> baseY * (screenHeight / BASE_H)
            else -> offsetY + baseY * gameScale
        }
        return realX to realY
    }

    private fun getRealScreenSize(): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width().toFloat() to bounds.height().toFloat()
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            point.x.toFloat() to point.y.toFloat()
        }
    }

    private fun getRawStatusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else 40
    }

    private fun finish(success: Boolean, error: String?) {
        if (!isRunning) return
        isRunning = false
        engine.stop()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        if (success) {
            logFlow("第二模块完成")
        } else {
            logError("披荆流程", error ?: "第二模块失败")
        }
        onCompleted(success, error)
    }

    private fun logFlow(message: String) {
        logInfo("活动页", message)
    }

    private fun logQuiz(message: String) {
        logInfo("答题", message)
    }

    private fun logMystery(message: String) {
        logInfo("神秘事件", message)
    }

    private fun logOcr(message: String) {
        logInfo("OCR", message)
    }

    private fun logInfo(category: String, message: String) {
        RunLogger.i(module = "披荆斩棘", section = category, message = message)
    }

    private fun logError(category: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            RunLogger.e(module = "披荆斩棘", section = category, message = message, throwable = throwable)
        } else {
            RunLogger.e(module = "披荆斩棘", section = category, message = message)
        }
    }

    private fun formatLogText(value: String, maxLength: Int = 48): String {
        val normalized = value.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return "(空)"
        return if (normalized.length <= maxLength) normalized else normalized.take(maxLength) + "..."
    }

    private fun formatHitChars(hitChars: List<Char>): String =
        if (hitChars.isEmpty()) "(无)" else hitChars.joinToString("")

    private fun formatPoint(point: Pair<Float, Float>): String =
        "x=${point.first.toInt()} y=${point.second.toInt()}"

    private data class CaptureSpec(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val align: String = "center"
    )

    private data class BasePoint(
        val x: Float,
        val y: Float,
        val align: String = "center"
    )

    private data class CharOcrHitCandidate(
        val lineText: String,
        val normalizedLineText: String,
        val hitChars: List<Char>,
        val boundingBox: Rect
    )

    private data class CharOcrHit(
        val point: Pair<Float, Float>,
        val lineText: String,
        val normalizedLineText: String,
        val hitChars: List<Char>
    )

    private data class TextOcrHit(
        val point: Pair<Float, Float>,
        val text: String,
        val matchedTarget: String
    )

    private data class CharSpecOcrResult(
        val rawText: String,
        val hitChars: List<Char>,
        val point: Pair<Float, Float>?
    )

    private data class MysteryScanResult(
        val battleHit: TextOcrHit?,
        val rewardHit: CharOcrHit?,
        val cardPoint: Pair<Float, Float>?,
        val terminalHit: CharOcrHit?,
        val exitHitChars: List<Char>,
        val scanSummary: String
    )

    private data class TaskPageSlotSnapshot(
        val rawText: String,
        val hasGo: Boolean,
        val clickPoint: Pair<Float, Float>
    )

    private data class TaskPageSnapshot(
        val slots: List<TaskPageSlotSnapshot>
    ) {
        val availableSlots: List<TaskPageSlotSnapshot>
            get() = slots.filter { it.hasGo }
    }

    private data class TaskExecutionContext(
        val queueIndex: Int,
        val visibleTaskCountBeforeEnter: Int,
        val generation: Int
    )

    private enum class TaskQueueState {
        PENDING,
        BLOCKED
    }

    private companion object {
        private const val BASE_W = 1080f
        private const val BASE_H = 1920f
        private const val MAX_TASK_LOOP_COUNT = 80
        private const val MAX_MYSTERY_DEPTH = 50
        private const val MAX_MYSTERY_BATTLE_POLLS = 8
        private const val MYSTERY_INITIAL_POLL_DELAY_MS = 2500L
        private const val MYSTERY_STORY_MOVE_POLL_DELAY_MS = 1200L
        private const val MYSTERY_CARD_REENTRY_DELAY_MS = 800L
        private const val MYSTERY_REWARD_REENTRY_DELAY_MS = 1000L
        private const val MYSTERY_TERMINAL_RETURN_DELAY_MS = 2000L
        private const val MYSTERY_BATTLE_INITIAL_CHECK_DELAY_MS = 5000L
        private const val MYSTERY_BATTLE_CONFIRM_POLL_INTERVAL_MS = 5000L
        private const val MYSTERY_BATTLE_CONFIRM_REENTRY_DELAY_MS = 2000L
        private const val MYSTERY_MANUAL_MIN_HIT_COUNT = 2
        private const val MYSTERY_CONFIRM_MIN_HIT_COUNT = 1
        private const val GO_TASK_TRANSITION_DELAY_MS = 1000L
        private const val TASK_PAGE_GO_SCAN_DELAY_MS = 1000L
        private const val REFRESH_LIMIT_CHECK_DELAY_MS = 1000L
        private const val REFRESH_LIMIT_BACK_INTERVAL_MS = 900L
        private const val REFRESH_LIMIT_MIN_HIT_COUNT = 2
        private const val ENTER_ALREADY_IN_TASK_PAGE_ID = 1
        private const val CLAIM_DISMISS_TASK_ID = 2
        private const val ENTER_SCRIPT = "pi_jing_zhan_ji_activity_enter.json"
        private const val CLAIM_SCRIPT = "pi_jing_zhan_ji_activity_claim.json"
        private const val GO_TASK_SCRIPT = "pi_jing_zhan_ji_activity_go_task.json"
        private const val SWEEP_SCRIPT = "pi_jing_zhan_ji_activity_sweep.json"
        private const val REFRESH_SCRIPT = "pi_jing_zhan_ji_activity_refresh.json"
        private const val CONFIRM_TASK_PAGE_SCRIPT = "pi_jing_zhan_ji_activity_confirm_task_page.json"
        private const val QUIZ_EXIT_SCRIPT = "pi_jing_zhan_ji_activity_quiz_exit.json"
        private const val MYSTERY_CARD_SCRIPT = "pi_jing_zhan_ji_activity_mystery_card.json"
        private val ACTIVITY_ENTRY_OCR_TASK_IDS = setOf(3, 6)
        private val ACTIVITY_ENTRY_TARGET_CHARS_RU_YUAN = listOf("披", "荆", "斩", "棘")
        private const val ACTIVITY_ENTRY_TEMPLATE_CODE_NAME_YUAN = "夜以继日.png"
        private const val ACTIVITY_ENTRY_TEMPLATE_THRESHOLD = 0.75f
        private const val ACTIVITY_ENTRY_TEMPLATE_ROI_HEIGHT = 440f
        private val AGENT_ENTRY_POINTS = listOf(
            BasePoint(281f, 934f),
            BasePoint(755f, 959f),
            BasePoint(297f, 1473f),
            BasePoint(784f, 1433f)
        )

        private val QUIZ_MARKER_SPEC = CaptureSpec(460.5f, 1387.5f, 324f, 168f)
        private val QUESTION_SPEC = CaptureSpec(537.75f, 537f, 913.5f, 159f)
        private val OPTION_SPECS = listOf(
            CaptureSpec(537f, 719.25f, 786f, 97.5f),
            CaptureSpec(535.5f, 864.75f, 786f, 103.5f),
            CaptureSpec(538.5f, 1005.75f, 798f, 103.5f),
            CaptureSpec(537f, 1145.25f, 792f, 103.5f)
        )
        private val MYSTERY_CARD_SPEC = CaptureSpec(542.25f, 1136.25f, 1075.5f, 1000f)
        private val MYSTERY_MANUAL_BUTTON_SPEC = CaptureSpec(1021f, 976f, 200f, 200f)
        private val MYSTERY_BATTLE_BUTTON_SPEC = CaptureSpec(552f, 1765f, 200f, 200f, "bottom")
        private val MYSTERY_MANUAL_TARGET_CHARS = listOf('手', '动')
        private val MYSTERY_CONFIRM_TARGET_CHARS = listOf('确', '定')
        private val MYSTERY_EXIT_TARGET_CHARS = listOf('今', '日', '已', '从', '处', '获', '取', '考', '绩', '本', '剩', '余', '免', '费', '刷', '新')
        private const val MYSTERY_EXIT_MIN_HIT_COUNT = 9
        private val TASK_PAGE_GO_SLOT_POINTS = listOf(
            BasePoint(840f, 1029f),
            BasePoint(842f, 1295f),
            BasePoint(842f, 1564f)
        )
        private val TASK_PAGE_GO_SLOT_SPECS = listOf(
            CaptureSpec(840f, 1029f, 200f, 200f),
            CaptureSpec(842f, 1295f, 200f, 200f),
            CaptureSpec(842f, 1564f, 200f, 200f)
        )
        private val REFRESH_LIMIT_CHECK_SPEC = CaptureSpec(538f, 1114f, 250f, 250f)
        private val REFRESH_LIMIT_TARGET_CHARS = listOf('白', '金', '币', '恢', '复')
        private const val MYSTERY_CARD_THRESHOLD = 0.75f
        private val OPTION_CLICK_POINTS = listOf(
            BasePoint(537f, 719.25f),
            BasePoint(535.5f, 864.75f),
            BasePoint(538.5f, 1005.75f),
            BasePoint(537f, 1145.25f)
        )
    }
}

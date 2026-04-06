// 檔案路徑：yuanassist/core/CombatEngine.kt
package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.LruCache
import android.view.Display
import android.view.WindowManager
import com.example.yuanassist.model.ActionItem
import com.example.yuanassist.model.BattleStageNavigationRegistry
import com.example.yuanassist.model.BattleStageTarget
import com.example.yuanassist.model.DirectStageNavigationConfig
import com.example.yuanassist.model.InstructionType
import com.example.yuanassist.model.ScriptInstruction
import com.example.yuanassist.model.TemplateRegionConfig
import com.example.yuanassist.model.TurnData
import com.example.yuanassist.model.decodeStageAutoNavTarget
import com.example.yuanassist.model.isCaveTarget
import com.example.yuanassist.model.isStageAutoNavAutoEnterNextFloorEnabled
import com.example.yuanassist.utils.AppConfig
import com.example.yuanassist.utils.GameConstants
import com.example.yuanassist.utils.RunLogger
import com.example.yuanassist.utils.TemplateOverrideStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.util.LinkedList
import java.util.Queue
import java.util.Locale
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList

class CombatEngine(
    private val accessibilityService: AccessibilityService,
    private val serviceScope: CoroutineScope,
    private val coordinateManager: CoordinateManager,
    private val gestureDispatcher: GestureDispatcher,
    private val getConfig: () -> AppConfig,
    private val shouldRunAutoSelectBeforeStageBattle: () -> Boolean,
    private val runAutoSelectBeforeStageBattle: suspend () -> Boolean,
    private val onStateChanged: () -> Unit, // 通知狀態改變 (停止/完成)
    private val onRowUpdated: (rowIndex: Int) -> Unit, // 通知 UI 更新特定行 (-1代表全部)
    private val onScrollToRow: (rowIndex: Int) -> Unit, // 通知 UI 滾動
    private val onHudUpdated: (text: String, isWarning: Boolean) -> Unit, // 通知更新按鈕文字
    private val showToast: (msg: String, isLong: Boolean) -> Unit // 統一處理 Toast
) {
    val followData = CopyOnWriteArrayList<TurnData>()
    val instructionList = ArrayList<ScriptInstruction>()

    var isRunning = false
        private set
    var isPaused = false
        private set

    private var currentExecutingRowIndex = -1
    private val actionQueue: Queue<ActionItem> = LinkedList()
    private var delayJob: Job? = null
    private val random = Random()
    private var lastTurnPauseTriggered = -1
    private var startOnlyInstructionsHandled = false
    private val windowManager by lazy {
        accessibilityService.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    }
    private val templateCache = object : LruCache<String, ByteArray>(8 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int =
            (value.size / 1024).coerceAtLeast(1)
    }

    private data class OrangeStarCandidate(
        val center: PointF,
        val confidence: Float,
        val glowScore: Float,
        val glowRatio: Float,
        val shapeScore: Float,
        val armContinuity: Float,
        val boundsWidth: Int,
        val boundsHeight: Int
    )

    private enum class OrangeStarAttemptStatus {
        HIT,
        SHAPE_NO_ORANGE,
        NO_SHAPE
    }

    private data class OrangeStarAttemptResult(
        val attemptIndex: Int,
        val status: OrangeStarAttemptStatus,
        val candidate: OrangeStarCandidate? = null
    )

    private data class RedRegionMatch(
        val center: PointF,
        val confidence: Float,
        val areaRatio: Float,
        val fillRatio: Float,
        val rednessScore: Float
    )

    private data class DeathCheckResult(
        val slotIndex: Int,
        val meanSaturation: Float,
        val isDead: Boolean
    )

    private data class BattleStateOcrResult(
        val rawText: String,
        val normalizedText: String,
        val hitChars: List<Char>,
        val turnNumber: Int?
    ) {
        val hitCount: Int
            get() = hitChars.size
    }

    companion object {
        private val REGEX_PARSE_NUM_ACTION = Regex("(\\d+)([A-Z↑↓圈]+)")
        private val REGEX_PARSE_PURE_ACTION = Regex("^([A-Z↑↓圈]+)$")
        private const val VISION_BASE_W = 1080f
        private const val VISION_BASE_H = 1920f
        private const val ORANGE_STAR_CENTER_X = 163f
        private const val ORANGE_STAR_CENTER_Y = 302f
        private const val ORANGE_STAR_ROI_SIZE = 200f
        private const val ORANGE_STAR_THRESHOLD = 0.60f
        private const val ORANGE_STAR_ATTEMPTS = 3
        private const val ORANGE_STAR_ATTEMPT_INTERVAL_MS = 500L
        private const val ORANGE_STAR_SHAPE_THRESHOLD = 0.34f
        private const val ORANGE_STAR_MIN_GLOW_RATIO = 0.07f
        private const val ORANGE_STAR_RECOVERY_TEMPLATE = "queding2.png"
        private const val ORANGE_STAR_RECOVERY_THRESHOLD = 0.80f
        private const val ORANGE_STAR_RECOVERY_CENTER_X = 759f
        private const val ORANGE_STAR_RECOVERY_CENTER_Y = 1158f
        private const val ORANGE_STAR_RECOVERY_ROI_SIZE = 300f
        private const val ALL_WIPE_TEMPLATE = "zaicitiaozhan.png"
        private const val ALL_WIPE_TEMPLATE_THRESHOLD = 0.80f
        private const val ALL_WIPE_TEMPLATE_CENTER_X = 785f
        private const val ALL_WIPE_TEMPLATE_CENTER_Y = 1699f
        private const val ALL_WIPE_TEMPLATE_ROI_SIZE = 300f
        private const val ALL_WIPE_RETRY_DELAY_MS = 1500L
        private const val ALL_WIPE_RESTART_DELAY_MS = 4000L
        private const val STAGE_HOME_RECOVERY_MAX_BACK_STEPS = 4
        private const val STAGE_HOME_RECOVERY_BACK_DELAY_MS = 1200L
        private const val STAGE_HOME_RECOVERY_BETWEEN_TEMPLATES_DELAY_MS = 500L
        private const val STAGE_HOME_RECOVERY_SWIPE_DELAY_MS = 2000L
        private const val STAGE_HOME_RECOVERY_POST_LANTAI_DELAY_MS = 2000L
        private const val STAGE_HOME_RECOVERY_POST_BAIHU_DELAY_MS = 2000L
        private const val STAGE_HOME_RECOVERY_SWIPE_DURATION_MS = 420L
        private const val STAGE_HOME_RECOVERY_LANTAI_TEMPLATE = "lantai.png"
        private const val STAGE_HOME_RECOVERY_LANTAI_CENTER_X = 499f
        private const val STAGE_HOME_RECOVERY_LANTAI_CENTER_Y = 567f
        private const val STAGE_HOME_RECOVERY_LANTAI_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_LANTAI_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_LANTAI_THRESHOLD = 0.75f
        private const val STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE = "yuanbao.png"
        private const val STAGE_HOME_RECOVERY_YUANBAO_CENTER_X = 441f
        private const val STAGE_HOME_RECOVERY_YUANBAO_CENTER_Y = 920f
        private const val STAGE_HOME_RECOVERY_YUANBAO_ROI_WIDTH = 200f
        private const val STAGE_HOME_RECOVERY_YUANBAO_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_YUANBAO_THRESHOLD = 0.85f
        private const val STAGE_HOME_RECOVERY_BAIHU_TEMPLATE = "baihu.png"
        private const val STAGE_HOME_RECOVERY_BAIHU_CENTER_X = 84f
        private const val STAGE_HOME_RECOVERY_BAIHU_CENTER_Y = 1505f
        private const val STAGE_HOME_RECOVERY_BAIHU_ROI_WIDTH = 200f
        private const val STAGE_HOME_RECOVERY_BAIHU_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_BAIHU_THRESHOLD = 0.80f
        private const val STAGE_HOME_RECOVERY_DIGONG_TEMPLATE = "digong.png"
        private const val STAGE_HOME_RECOVERY_DIGONG_CENTER_X = 573f
        private const val STAGE_HOME_RECOVERY_DIGONG_CENTER_Y = 811f
        private const val STAGE_HOME_RECOVERY_DIGONG_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_DIGONG_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_DIGONG_THRESHOLD = 0.75f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_TEMPLATE = "yijirukou.png"
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_X = 931f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_Y = 1688f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_YIJI_RUKOU_THRESHOLD = 0.80f
        private const val STAGE_HOME_RECOVERY_POST_DIGONG_DELAY_MS = 2000L
        private const val STAGE_HOME_RECOVERY_POST_YIJI_RUKOU_DELAY_MS = 2000L
        private const val STAGE_HOME_RECOVERY_XINZHI_TEMPLATE = "xinzhi.png"
        private const val STAGE_HOME_RECOVERY_XINZHI_CENTER_X = 999f
        private const val STAGE_HOME_RECOVERY_XINZHI_CENTER_Y = 1544f
        private const val STAGE_HOME_RECOVERY_XINZHI_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_XINZHI_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_XINZHI_THRESHOLD = 0.75f
        private const val STAGE_HOME_RECOVERY_LIXIAN_TEMPLATE = "lixian.png"
        private const val STAGE_HOME_RECOVERY_LIXIAN_CENTER_X = 922f
        private const val STAGE_HOME_RECOVERY_LIXIAN_CENTER_Y = 1039f
        private const val STAGE_HOME_RECOVERY_LIXIAN_ROI_WIDTH = 300f
        private const val STAGE_HOME_RECOVERY_LIXIAN_ROI_HEIGHT = 300f
        private const val STAGE_HOME_RECOVERY_LIXIAN_THRESHOLD = 0.80f
        private const val STAGE_HOME_RECOVERY_POST_XINZHI_DELAY_MS = 2000L
        private const val STAGE_HOME_RECOVERY_POST_LIXIAN_DELAY_MS = 2000L
        private const val CAVE_DONGKU_TEMPLATE = "dongku.png"
        private const val CAVE_DONGKU_TEMPLATE_FALLBACK = "dongku2.png"
        private const val CAVE_DONGKU_CENTER_X = 666f
        private const val CAVE_DONGKU_CENTER_Y = 313f
        private const val CAVE_DONGKU_ROI_WIDTH = 300f
        private const val CAVE_DONGKU_ROI_HEIGHT = 300f
        private const val CAVE_DONGKU_THRESHOLD = 0.75f
        private const val CAVE_NEXT_FLOOR_TEMPLATE = "xiayiceng.png"
        private const val CAVE_NEXT_FLOOR_CENTER_X = 700f
        private const val CAVE_NEXT_FLOOR_CENTER_Y = 1700f
        private const val CAVE_NEXT_FLOOR_ROI_WIDTH = 500f
        private const val CAVE_NEXT_FLOOR_ROI_HEIGHT = 500f
        private const val CAVE_NEXT_FLOOR_THRESHOLD = 0.80f
        private const val DEATH_CHECK_TOP_Y = 1350f
        private const val DEATH_CHECK_BOTTOM_Y = 1700f
        private const val DEATH_CHECK_SLOT_COUNT = 5
        private const val DEATH_CHECK_SATURATION_THRESHOLD = 20f
        private const val STAGE_AUTO_NAV_CHECK_DELAY_MS = 500L
        private const val STAGE_SCREENSHOT_COOLDOWN_DELAY_MS = 500L
        private const val STAGE_AUTO_SELECT_ENTRY_OFFSET_DP = 200f
        private const val STAGE_AUTO_SELECT_ENTRY_SETTLE_DELAY_MS = 1500L
        private const val STAGE_AUTO_SELECT_POST_CONFIRM_DELAY_MS = 5000L
        private const val START_BATTLE_RED_THRESHOLD = 0.72f
        private const val START_BATTLE_RED_CENTER_X = 540f
        private const val START_BATTLE_RED_CENTER_Y = 1700f
        private const val START_BATTLE_RED_ROI_WIDTH = 500f
        private const val START_BATTLE_RED_ROI_HEIGHT = 400f
        private const val STAGE_BATTLE_OCR_ROI_WIDTH = 400f
        private const val STAGE_BATTLE_OCR_ROI_HEIGHT = 300f
        private const val STAGE_BATTLE_OCR_MIN_HIT_COUNT = 2
    }

    private fun getTargetSwitchSettleDelayMs(): Long = 500L

    fun start() {
        val appConfig = getConfig()
        isPaused = false
        lastTurnPauseTriggered = -1
        startOnlyInstructionsHandled = false
        delayJob?.cancel()

        if (followData.isEmpty()) {
            showToast("请先导入数据", false)
            stop()
            return
        }

        ensureTurnsForInstructions()

        followData.forEach { it.isExecuting = false }
        onRowUpdated(-1)

        val startIndex = (appConfig.startTurn - 1).coerceAtLeast(0)
        if (startIndex >= followData.size) {
            showToast("起始回合超出范围", false)
            stop()
            return
        }

        isRunning = true
        showToast("从第 ${appConfig.startTurn} 回合开始跟打", false)
        startSafeDelay(1000, "准备", false) {
            handleStartOnlyInstructions {
                processRowAndExecute(startIndex)
            }
        }
    }

    private fun ensureTurnsForInstructions() {
        val maxInstructionTurn = instructionList.maxOfOrNull { it.turn } ?: return
        if (maxInstructionTurn <= followData.size) return

        val startTurn = followData.size + 1
        for (turn in startTurn..maxInstructionTurn) {
            followData.add(TurnData(turn))
        }
        RunLogger.i("已根据指令自动补齐空回合 start=$startTurn end=$maxInstructionTurn")
    }

    fun stop() {
        isRunning = false
        isPaused = false
        lastTurnPauseTriggered = -1
        startOnlyInstructionsHandled = false
        delayJob?.cancel()
        templateCache.evictAll()
        followData.forEach { it.isExecuting = false }
        onRowUpdated(-1)
        onStateChanged()
    }

    fun hasStageAutoNavigationInstruction(): Boolean {
        return instructionList.any { it.type == InstructionType.STAGE_AUTO_NAV }
    }

    private fun handleStartOnlyInstructions(onReady: () -> Unit) {
        if (!isRunning) return
        if (startOnlyInstructionsHandled) {
            onReady()
            return
        }
        startOnlyInstructionsHandled = true

        val navigationTask = instructionList.firstOrNull {
            it.type == InstructionType.STAGE_AUTO_NAV
        }?.normalized()
        if (navigationTask == null) {
            onReady()
            return
        }

        val target = decodeStageAutoNavTarget(navigationTask.value)
        if (target == null) {
            RunLogger.e("关卡自动导航配置无效 value=${navigationTask.value}")
            showToast("关卡自动导航配置无效，已停止", true)
            stop()
            return
        }

        onHudUpdated("自动导航 ${target.description}", true)
        performStageAutoNavigation(target, onReady)
    }

    fun togglePauseResume() {
        isPaused = !isPaused
        if (isPaused) {
            delayJob?.cancel()
            showToast("已暂停", false)
            onStateChanged()
            onHudUpdated("已暂停", true) // 🔴 暂停时固定显示
        } else {
            showToast("继续运行", false)
            onStateChanged()
            startSafeDelay(500, "继续", false) { // 🔴 继续时的短暂缓冲
                if (currentExecutingRowIndex in followData.indices) {
                    val currentTurnNum = followData[currentExecutingRowIndex].turnNumber
                    val nextAction = actionQueue.peek()
                    val step = nextAction?.stepIndex ?: 0
                    checkAndSwitchTarget(currentTurnNum, step) {
                        executeActionQueue { processRowAndExecute(currentExecutingRowIndex + 1) }
                    }
                }
            }
        }
    }

    fun clearData() {
        followData.clear()
        onRowUpdated(-1)
    }

    private fun processRowAndExecute(rowIndex: Int) {
        RunLogger.i("准备执行回合行 rowIndex=$rowIndex size=${followData.size} isRunning=$isRunning")
        if (rowIndex >= followData.size || !isRunning) {
            RunLogger.e("回合执行终止 rowIndex=$rowIndex size=${followData.size} isRunning=$isRunning")
            showToast("跟打结束", false)
            stop()
            return
        }

        currentExecutingRowIndex = rowIndex
        val currentTurnNum = followData[rowIndex].turnNumber
        RunLogger.i("开始执行第 $currentTurnNum 回合")

        // 取消上一行的高亮
        if (rowIndex > 0) {
            followData[rowIndex - 1].isExecuting = false
            onRowUpdated(rowIndex - 1)
        }

        val turnData = followData[rowIndex]
        turnData.isExecuting = true
        turnData.hasConflict = false
        onRowUpdated(rowIndex)

        try {
            val sortedActions = parseRowActions(turnData)
            onScrollToRow(rowIndex)

            actionQueue.clear()
            for (item in sortedActions) {
                for (char in item.command) {
                    actionQueue.add(ActionItem(item.stepIndex, item.colIndex, char.toString()))
                }
            }

            handleTurnStartInstructions(currentTurnNum) {
                checkAndSwitchTarget(currentTurnNum, 0) {
                    executeActionQueue { processRowAndExecute(rowIndex + 1) }
                }
            }
        } catch (e: Exception) {
            RunLogger.e("第 ${turnData.turnNumber} 回合处理异常", e)
            turnData.hasConflict = true
            onRowUpdated(rowIndex)
            showToast("第 ${turnData.turnNumber} 回合指令冲突！停止运行", true)
            stop()
        }
    }

    private suspend fun continueAfterStageStartBattleDetected(
        config: DirectStageNavigationConfig,
        startBattlePoint: PointF,
        logPrefix: String,
        onReady: () -> Unit
    ): Boolean {
        if (!isRunning) return true

        if (shouldRunAutoSelectBeforeStageBattle()) {
            val density = accessibilityService.resources.displayMetrics.density
            val pickEntryY = (startBattlePoint.y - STAGE_AUTO_SELECT_ENTRY_OFFSET_DP * density)
                .coerceAtLeast(0f)
            gestureDispatcher.performActionDirect(
                startBattlePoint.x,
                pickEntryY,
                startBattlePoint.x,
                pickEntryY,
                true
            )
            RunLogger.i(
                "$logPrefix 识别到开始战斗，点击上方选人入口 x=${startBattlePoint.x.toInt()} y=${pickEntryY.toInt()}"
            )
            delay(STAGE_AUTO_SELECT_ENTRY_SETTLE_DELAY_MS)
            if (!isRunning) return true
            val autoSelectSuccess = runAutoSelectBeforeStageBattle()
            if (!isRunning) return true
            if (autoSelectSuccess) {
                RunLogger.i("$logPrefix 自动选人完成，5秒后进入跟打")
                delay(STAGE_AUTO_SELECT_POST_CONFIRM_DELAY_MS)
                if (!isRunning) return true
                onReady()
                return true
            }
            RunLogger.e("$logPrefix 自动选人失败，已停止")
            stop()
            return true
        }

        gestureDispatcher.performActionDirect(
            startBattlePoint.x,
            startBattlePoint.y,
            startBattlePoint.x,
            startBattlePoint.y,
            true
        )
        RunLogger.i(
            "$logPrefix 识别到开始战斗，点击 x=${startBattlePoint.x.toInt()} y=${startBattlePoint.y.toInt()}"
        )
        delay(config.delayAfterStartBattleClickMs)
        if (isRunning) {
            onReady()
        }
        return true
    }

    @Throws(Exception::class)
    private fun parseRowActions(data: TurnData): List<ActionItem> {
        val stepMap = HashMap<Int, ActionItem>()
        val floatingActions = ArrayList<Pair<Int, String>>()

        for (col in 0 until 5) {
            val rawText = data.characterActions[col].toString().trim().uppercase()
            if (rawText.isEmpty()) continue

            val numMatches = REGEX_PARSE_NUM_ACTION.findAll(rawText)
            if (numMatches.any()) {
                for (match in numMatches) {
                    val step = match.groupValues[1].toInt()
                    val cmd = match.groupValues[2]
                    if (stepMap.containsKey(step)) throw Exception("Conflict at step $step")
                    stepMap[step] = ActionItem(step, col, cmd)
                }
            } else if (REGEX_PARSE_PURE_ACTION.matches(rawText)) {
                floatingActions.add(Pair(col, rawText))
            }
        }

        var currentStepScanner = 1
        for (floating in floatingActions) {
            while (stepMap.containsKey(currentStepScanner)) currentStepScanner++
            stepMap[currentStepScanner] =
                ActionItem(currentStepScanner, floating.first, floating.second)
        }
        return stepMap.values.sortedBy { it.stepIndex }
    }

    private fun executeActionQueue(onComplete: () -> Unit) {
        if (!isRunning) return
        val appConfig = getConfig()

        val action = actionQueue.poll()
        if (action == null) {
            val currentTurn = followData[currentExecutingRowIndex].turnNumber
            val turnPause =
                instructionList.find { it.turn == currentTurn && it.step == 0 && it.type == InstructionType.PAUSE }

            if (turnPause != null&& lastTurnPauseTriggered != currentTurn) {
                lastTurnPauseTriggered = currentTurn
                isPaused = true
                showToast("指令触发：第${currentTurn}回合后暂停", true)
                onStateChanged()
                onHudUpdated("已暂停", true)
                return
            }

            val hasActions =
                followData[currentExecutingRowIndex].characterActions.any { it.isNotBlank() }
            if (!hasActions) {
                showToast("第${currentTurn}回合为空，跳过等待", false)
                startSafeDelay(200, getNextTurnFirstAction(), false) {
                    if (currentExecutingRowIndex == followData.lastIndex && shouldAutoEnterNextCaveFloor()) {
                        serviceScope.launch {
                            continueAfterCaveNextFloorCheck(
                                triggerLabel = "最终回合结算后",
                                onNextTurn = onComplete
                            )
                        }
                    } else {
                        onComplete()
                    }
                }
                return
            }

            RunLogger.i("等待敌方回合结束 (${appConfig.waitTurn / 1000}秒)...")
            waitForEnemyTurn(currentExecutingRowIndex == followData.lastIndex, onComplete)
            return
        }

        RunLogger.i("  -> 执行动作: 站位${action.colIndex + 1} ${action.command}")
        val char = action.command[0]

        val basePointA = coordinateManager.getActionCoordinates(
            action.colIndex,
            appConfig.attackYFromBottom
        )
        val baseStartUp = coordinateManager.getActionCoordinates(
            action.colIndex,
            appConfig.upYFromBottom
        )
        val baseStartDown = coordinateManager.getActionCoordinates(
            action.colIndex,
            appConfig.downYFromBottom
        )

        when (char) {
            'A' -> {
                val p = getRandomPointInCircle(
                    basePointA.x,
                    basePointA.y,
                    GameConstants.RND_RADIUS_CLICK * coordinateManager.gameScale
                )
                gestureDispatcher.performActionDirect(p.x, p.y, p.x, p.y, true)
            }

            '圈' -> {
                val p = coordinateManager.getActionCoordinates(
                    action.colIndex,
                    appConfig.circleYFromBottom
                )
                gestureDispatcher.performActionDirect(p.x, p.y, p.x, p.y, true)
            }

            '↑' -> {
                val sx = addRandomOffset(
                    baseStartUp.x,
                    GameConstants.RND_OFFSET_SWIPE_X * coordinateManager.gameScale
                )
                val sy = addRandomOffset(
                    baseStartUp.y,
                    GameConstants.RND_OFFSET_SWIPE_Y * coordinateManager.gameScale
                )
                val ex = addRandomOffset(
                    baseStartUp.x,
                    GameConstants.RND_OFFSET_SWIPE_X * coordinateManager.gameScale
                )
                val ey = addRandomOffset(
                    baseStartUp.y - (GameConstants.SWIPE_DISTANCE * coordinateManager.gameScale),
                    GameConstants.RND_OFFSET_SWIPE_Y * coordinateManager.gameScale
                )
                gestureDispatcher.performActionDirect(sx, sy, ex, ey, false)
            }

            '↓' -> {
                val baseStart = baseStartDown
                val sx = addRandomOffset(
                    baseStart.x,
                    GameConstants.RND_OFFSET_SWIPE_X * coordinateManager.gameScale
                )
                val sy = addRandomOffset(
                    baseStart.y,
                    GameConstants.RND_OFFSET_SWIPE_Y * coordinateManager.gameScale
                )
                val ex = addRandomOffset(
                    baseStart.x,
                    GameConstants.RND_OFFSET_SWIPE_X * coordinateManager.gameScale
                )
                val ey = addRandomOffset(
                    baseStart.y + GameConstants.SWIPE_DISTANCE * coordinateManager.gameScale,
                    GameConstants.RND_OFFSET_SWIPE_Y * coordinateManager.gameScale
                )
                gestureDispatcher.performActionDirect(sx, sy, ex, ey, false)
            }
        }

        val speedMultiplier = if (appConfig.gameSpeed == 2) 1.5 else 1.0
        var delay = if (char == '↑') {
            (appConfig.intervalSkill * speedMultiplier).toLong()
        } else {
            (appConfig.intervalAttack * speedMultiplier).toLong()
        }
        val currentTurn = followData[currentExecutingRowIndex].turnNumber
        val currentStep = action.stepIndex

        val turnDelayDelta = resolveDelayDelta(currentTurn, 0)
        if (turnDelayDelta != 0L) {
            delay += turnDelayDelta
        }

        val stepDelayDelta = resolveDelayDelta(currentTurn, currentStep)
        if (stepDelayDelta != 0L) {
            delay += stepDelayDelta
            val actionText = if (stepDelayDelta > 0L) {
                "延时增加 ${stepDelayDelta}ms"
            } else {
                "延时缩短 ${-stepDelayDelta}ms"
            }
            showToast("指令生效：$actionText", false)
        }
        delay = delay.coerceAtLeast(0L)

        val stepPauseIns =
            instructionList.find { it.turn == currentTurn && it.step == currentStep && it.type == InstructionType.PAUSE }

        val nextActionPeek = actionQueue.peek()
        val nextActionStr = if (nextActionPeek != null) {
            "${nextActionPeek.stepIndex}${nextActionPeek.command}"
        } else {
            getNextTurnFirstAction()
        }

        startSafeDelay(delay, nextActionStr, true) {
            if (stepPauseIns != null) {
                isPaused = true
                showToast("指令触发：步骤${currentStep}后暂停", true)
                onStateChanged()
                onHudUpdated("已暂停", true) // 🔴 指令触发暂停时显示
                return@startSafeDelay
            }
            val currentTurnNum = followData[currentExecutingRowIndex].turnNumber
            checkAndSwitchTarget(currentTurnNum, action.stepIndex) {
                executeActionQueue(onComplete)
            }
        }
    }

    private fun waitForEnemyTurn(isFinalTurn: Boolean, onNextTurn: () -> Unit) {
        if (!isRunning) return
        val appConfig = getConfig()
        val speedMultiplier = if (appConfig.gameSpeed == 2) 1.5 else 1.0
        val actualWaitTurn = (appConfig.waitTurn * speedMultiplier).toLong()

        val nextStepStr = getNextTurnFirstAction()
        showToast("行动结束，等待 ${actualWaitTurn / 1000} 秒...", false)

        startSafeDelay(actualWaitTurn, nextStepStr, false) {
            if (isRunning) {
                RunLogger.i("敌方回合等待结束，准备进入下一回合")
                if (isFinalTurn && shouldAutoEnterNextCaveFloor()) {
                    serviceScope.launch {
                        continueAfterCaveNextFloorCheck(
                            triggerLabel = "最终回合结算后",
                            onNextTurn = onNextTurn
                        )
                    }
                } else {
                    try {
                        onNextTurn()
                    } catch (t: Throwable) {
                        RunLogger.e("进入下一回合回调异常", t)
                        showToast("进入下一回合异常，已停止", true)
                        stop()
                    }
                }
            }
        }
    }

    private fun handleTurnStartInstructions(turn: Int, onNext: () -> Unit) {
        val shouldCheckCaveNextFloorAtTurnStart = shouldAutoEnterNextCaveFloor() && turn > 1
        val tasks = instructionList.filter {
            it.turn == turn &&
                (
                    it.type == InstructionType.ALL_WIPE_CHECK ||
                        it.type == InstructionType.DEATH_CHECK ||
                        it.type == InstructionType.ORANGE_STAR_CHECK
                    )
        }.sortedBy { task ->
            when (task.type) {
                InstructionType.ALL_WIPE_CHECK -> 0
                InstructionType.DEATH_CHECK -> 1
                InstructionType.ORANGE_STAR_CHECK -> 2
                else -> 3
            }
        }
        RunLogger.i(
            "第${turn}回合开始前检测项: " +
                if (tasks.isEmpty()) "无" else tasks.joinToString(" -> ") { task ->
                    when (task.type) {
                        InstructionType.ALL_WIPE_CHECK -> "全灭检测"
                        InstructionType.DEATH_CHECK -> "阵亡检测(${task.value})"
                        InstructionType.ORANGE_STAR_CHECK -> "橙星检测"
                        else -> task.type.name
                    }
                }
        )
        val shouldUseSharedScreenshot =
            shouldCheckCaveNextFloorAtTurnStart ||
                tasks.any { it.type != InstructionType.ORANGE_STAR_CHECK } ||
                (getConfig().enableTurnNumberCheck && turn >= 2)

        serviceScope.launch {
            val sharedScreenshot = if (shouldUseSharedScreenshot) captureScreenshotBitmap() else null
            try {
                if (shouldUseSharedScreenshot && sharedScreenshot == null) {
                    RunLogger.e("第${turn}回合开始前共享截图失败")
                }
                if (shouldCheckCaveNextFloorAtTurnStart) {
                    val caveNextFloorHandled = tryAutoEnterNextCaveFloorIfNeeded(
                        triggerLabel = "第${turn}回合开始",
                        sharedScreenshot = sharedScreenshot
                    )
                    if (caveNextFloorHandled || !isRunning) {
                        return@launch
                    }
                }
                if (tasks.isEmpty()) {
                    performTurnNumberCheckIfNeeded(turn, onNext, sharedScreenshot)
                    return@launch
                }

                performTurnStartInstructions(turn, tasks, 0, onNext, sharedScreenshot)
            } catch (t: Throwable) {
                RunLogger.e("第${turn}回合开始前检测链异常", t)
                showToast("第${turn}回合开始前检测异常，已停止", true)
                stop()
            } finally {
                if (sharedScreenshot != null && !sharedScreenshot.isRecycled) {
                    sharedScreenshot.recycle()
                }
            }
        }
    }

    private suspend fun performTurnStartInstructions(
        turn: Int,
        tasks: List<ScriptInstruction>,
        index: Int,
        onComplete: () -> Unit,
        sharedScreenshot: Bitmap?
    ) {
        if (index >= tasks.size || !isRunning) {
            if (isRunning) {
                performTurnNumberCheckIfNeeded(turn, onComplete, sharedScreenshot)
            }
            return
        }

        val task = tasks[index]
        when (task.type) {
            InstructionType.ALL_WIPE_CHECK -> {
                onHudUpdated("全灭检测", true)
                val shouldContinue = performAllWipeCheck(sharedScreenshot)
                if (shouldContinue && isRunning) {
                    performTurnStartInstructions(turn, tasks, index + 1, onComplete, sharedScreenshot)
                }
            }
            InstructionType.DEATH_CHECK -> {
                val deathTasks = ArrayList<ScriptInstruction>()
                var nextIndex = index
                while (nextIndex < tasks.size && tasks[nextIndex].type == InstructionType.DEATH_CHECK) {
                    deathTasks.add(tasks[nextIndex])
                    nextIndex++
                }
                val label = deathTasks.joinToString("、") { "${it.value}号位" }
                RunLogger.i("第${turn}回合开始执行阵亡检测 slots=${deathTasks.joinToString(",") { it.value.toString() }}")
                onHudUpdated("阵亡检测 $label", true)
                val shouldContinue = performDeathCheckBatch(
                    slotIndices = deathTasks.map { it.value.toInt() },
                    sharedScreenshot = sharedScreenshot
                )
                if (shouldContinue && isRunning) {
                    performTurnStartInstructions(turn, tasks, nextIndex, onComplete, sharedScreenshot)
                }
            }
            InstructionType.ORANGE_STAR_CHECK -> {
                onHudUpdated("橙星检测", true)
                if (sharedScreenshot != null) {
                    delay(STAGE_SCREENSHOT_COOLDOWN_DELAY_MS)
                    if (!isRunning) return
                }
                val shouldContinue = performOrangeStarCheck()
                if (shouldContinue && isRunning) {
                    performTurnStartInstructions(turn, tasks, index + 1, onComplete, sharedScreenshot)
                }
            }
            else -> performTurnStartInstructions(turn, tasks, index + 1, onComplete, sharedScreenshot)
        }
    }

    private suspend fun performTurnNumberCheckIfNeeded(
        turn: Int,
        onNext: () -> Unit,
        sharedScreenshot: Bitmap? = null
    ) {
        val appConfig = getConfig()
        if (!appConfig.enableTurnNumberCheck || turn < 2) {
            if (turn >= 2) {
                RunLogger.i("第${turn}回合跳过回合数检测 enabled=${appConfig.enableTurnNumberCheck}")
            }
            onNext()
            return
        }

        RunLogger.i("第${turn}回合开始执行回合数检测")
        onHudUpdated("回合数检测", true)
        val screenshot = sharedScreenshot ?: captureScreenshotBitmap()
        if (screenshot == null) {
            RunLogger.e("回合数检测截图失败")
            showToast("回合数检测截图失败，已停止", true)
            stop()
            return
        }

        val result = detectBattleStateOcrFromScreenshot(
            screenshot = screenshot,
            logLabel = "回合数检测OCR"
        )
        if (!isRunning) return

        val ocrTurn = result?.turnNumber
        if (ocrTurn == turn) {
            RunLogger.i(
                "回合数检测通过 预期=$turn OCR=$ocrTurn " +
                    "text=${formatOcrLogText(result?.rawText.orEmpty())}"
            )
            onNext()
        } else {
            RunLogger.e(
                "回合数与表格不符 预期=$turn OCR=${ocrTurn ?: "无"} " +
                    "text=${formatOcrLogText(result?.rawText.orEmpty())}"
            )
            showToast("回合数与表格不符", true)
            stop()
        }
    }

    private suspend fun performAllWipeCheck(screenshot: Bitmap?): Boolean {
        val wipePoint = if (screenshot != null) {
            findTemplatePointInVisionRoiFromScreenshot(
                screenshot = screenshot,
                templateName = ALL_WIPE_TEMPLATE,
                centerX = ALL_WIPE_TEMPLATE_CENTER_X,
                centerY = ALL_WIPE_TEMPLATE_CENTER_Y,
                align = "bottom",
                roiWidth = ALL_WIPE_TEMPLATE_ROI_SIZE,
                roiHeight = ALL_WIPE_TEMPLATE_ROI_SIZE,
                threshold = ALL_WIPE_TEMPLATE_THRESHOLD,
                logLabel = "全灭检测模板"
            )
        } else {
            findTemplatePointInVisionRoi(
                templateName = ALL_WIPE_TEMPLATE,
                centerX = ALL_WIPE_TEMPLATE_CENTER_X,
                centerY = ALL_WIPE_TEMPLATE_CENTER_Y,
                align = "bottom",
                roiWidth = ALL_WIPE_TEMPLATE_ROI_SIZE,
                roiHeight = ALL_WIPE_TEMPLATE_ROI_SIZE,
                threshold = ALL_WIPE_TEMPLATE_THRESHOLD,
                logLabel = "全灭检测模板"
            )
        }

        if (!isRunning) return false

        if (wipePoint == null) {
            return true
        }

        gestureDispatcher.performActionDirect(wipePoint.x, wipePoint.y, wipePoint.x, wipePoint.y, true)
        RunLogger.i(
            "全灭检测命中模板 ${ALL_WIPE_TEMPLATE}，点击 x=${wipePoint.x.toInt()} y=${wipePoint.y.toInt()}"
        )
        showToast("识别到再次挑战，准备重开", false)
        delay(ALL_WIPE_RETRY_DELAY_MS)
        if (!isRunning) return false

        val startBattlePoint = findStartBattleRedPoint()
        if (startBattlePoint == null) {
            RunLogger.e("全灭恢复未识别到开始战斗按钮，已退出")
            showToast("未识别到开始战斗，已停止", true)
            stop()
            return false
        }

        gestureDispatcher.performActionDirect(
            startBattlePoint.x,
            startBattlePoint.y,
            startBattlePoint.x,
            startBattlePoint.y,
            true
        )
        RunLogger.i(
            "全灭恢复识别到开始战斗，点击 x=${startBattlePoint.x.toInt()} y=${startBattlePoint.y.toInt()}"
        )
        delay(ALL_WIPE_RESTART_DELAY_MS)
        if (!isRunning) return false
        restartFromFirstTurn()
        return false
    }

    private fun performStageAutoNavigation(
        target: BattleStageTarget,
        onReady: () -> Unit
    ) {
        serviceScope.launch {
            val config = BattleStageNavigationRegistry.getDirectConfig(target)
            if (config == null) {
                RunLogger.e("关卡 ${target.description} 的自动导航脚本尚未实现")
                showToast("${target.description} 自动导航暂未实现", true)
                stop()
                return@launch
            }
            performDirectStageNavigation(config, onReady)
        }
    }

    private suspend fun performDirectStageNavigation(
        config: DirectStageNavigationConfig,
        onReady: () -> Unit
    ) {
        delay(STAGE_AUTO_NAV_CHECK_DELAY_MS)
        if (!isRunning) return
        val initialScreenshot = captureScreenshotBitmap()
        if (initialScreenshot == null) {
            RunLogger.e("${config.target.description} 自动导航截图失败")
            showToast("${config.target.description} 自动导航截图失败，已停止", true)
            stop()
            return
        }
        var entryPoint: PointF? = null
        var selectionPoint: PointF? = null
        var cavePoint: PointF? = null
        var battleStateOcrResult: BattleStateOcrResult? = null
        var startBattlePoint: PointF? = null
        try {
            battleStateOcrResult = detectBattleStateOcrFromScreenshot(
                screenshot = initialScreenshot,
                logLabel = "${config.target.description} 自动导航战斗OCR"
            )

            if (battleStateOcrResult != null && battleStateOcrResult.hitCount >= STAGE_BATTLE_OCR_MIN_HIT_COUNT) {
                RunLogger.i(
                    "${config.target.description} 自动导航检测到战斗中文 " +
                        "hits=${battleStateOcrResult.hitChars.joinToString("")} " +
                        "count=${battleStateOcrResult.hitCount} " +
                        "text=${formatOcrLogText(battleStateOcrResult.rawText)}"
                )
                onReady()
                return
            }
            if (battleStateOcrResult == null) {
                RunLogger.i(
                    "${config.target.description} 自动导航战斗OCR未返回结果，继续检测开始战斗"
                )
            } else {
                RunLogger.i(
                    "${config.target.description} 自动导航战斗OCR未命中 " +
                        "hits=${if (battleStateOcrResult.hitChars.isEmpty()) "无" else battleStateOcrResult.hitChars.joinToString("")} " +
                        "count=${battleStateOcrResult.hitCount} " +
                        "threshold=$STAGE_BATTLE_OCR_MIN_HIT_COUNT " +
                        "text=${formatOcrLogText(battleStateOcrResult.rawText)}"
                )
            }

            startBattlePoint = withContext(Dispatchers.Default) {
                findStartBattleRedPointFromScreenshot(
                    screenshot = initialScreenshot,
                    logLowConfidence = false
                )
            }
            if (startBattlePoint != null) {
                continueAfterStageStartBattleDetected(
                    config = config,
                    startBattlePoint = startBattlePoint,
                    logPrefix = "${config.target.description} 自动导航",
                    onReady = onReady
                )
                return
            }

            entryPoint = withContext(Dispatchers.Default) {
                findTemplatePointInVisionRoiFromScreenshot(
                    screenshot = initialScreenshot,
                    templateName = config.entryTemplateRegion.templateName,
                    centerX = config.entryTemplateRegion.x,
                    centerY = config.entryTemplateRegion.y,
                    align = config.entryTemplateRegion.align,
                    roiWidth = config.entryTemplateRegion.width,
                    roiHeight = config.entryTemplateRegion.height,
                    threshold = config.entryTemplateRegion.threshold,
                    logLabel = "${config.target.description} 进入挑战模板"
                )
            }
            if (entryPoint == null && config.target.isCaveTarget()) {
                cavePoint = withContext(Dispatchers.Default) {
                    findCaveDongkuPointFromScreenshot(
                        screenshot = initialScreenshot,
                        logLabel = "${config.target.description} 洞窟模板"
                    )
                }
            }
            if (entryPoint == null && config.recoverySelectionRegion != null) {
                val selectionRegion = config.recoverySelectionRegion
                selectionPoint = withContext(Dispatchers.Default) {
                    findTemplatePointInVisionRoiFromScreenshot(
                        screenshot = initialScreenshot,
                        templateName = selectionRegion.templateName,
                        centerX = selectionRegion.x,
                        centerY = selectionRegion.y,
                        align = selectionRegion.align,
                        roiWidth = selectionRegion.width,
                        roiHeight = selectionRegion.height,
                        threshold = selectionRegion.threshold,
                        logLabel = "${config.target.description} 关卡模板"
                    )
                }
            }
        } finally {
            if (!initialScreenshot.isRecycled) {
                initialScreenshot.recycle()
            }
        }

        if (!isRunning) return

        if (config.target.isCaveTarget()) {
            val completed = when {
                entryPoint != null -> continueCaveEntryFlow(config, entryPoint, onReady)
                cavePoint != null -> continueCaveFromDongkuFlow(config, cavePoint, onReady)
                else -> false
            }
            if (completed || !isRunning) {
                return
            }
            delay(STAGE_SCREENSHOT_COOLDOWN_DELAY_MS)
            if (!isRunning) return
            val recovered = performCaveIndirectHomeRecovery(config, onReady)
            if (recovered || !isRunning) {
                return
            }
            RunLogger.e("${config.target.description} 自动导航未识别到回合内、开始战斗、前往讨伐、洞窟或主页入口，已停止")
            showToast("${config.target.description} 自动导航失败，已停止", true)
            stop()
            return
        }

        if (selectionPoint != null) {
            gestureDispatcher.performActionDirect(
                selectionPoint.x,
                selectionPoint.y,
                selectionPoint.x,
                selectionPoint.y,
                true
            )
            RunLogger.i(
                "${config.target.description} 自动导航识别到 ${config.recoverySelectionRegion?.templateName}，点击 x=${selectionPoint.x.toInt()} y=${selectionPoint.y.toInt()}"
            )
            delay(config.delayAfterEntryClickMs)
            if (!isRunning) return

            val selectionFlowCompleted = continueStageEntryFlow(config, onReady, tryStartBattleFirst = false)
            if (selectionFlowCompleted || !isRunning) {
                return
            }
        }

        if (entryPoint == null) {
            if (config.target == BattleStageTarget.BAI_HU) {
                delay(STAGE_SCREENSHOT_COOLDOWN_DELAY_MS)
                if (!isRunning) return
                val recovered = performBaiHuIndirectRecovery(config, onReady)
                if (recovered || !isRunning) {
                    return
                }
            } else if (config.recoverySelectionRegion != null) {
                delay(STAGE_SCREENSHOT_COOLDOWN_DELAY_MS)
                if (!isRunning) return
                val recovered = performRelicIndirectAutoNavigation(config, config.recoverySelectionRegion, onReady)
                if (recovered || !isRunning) {
                    return
                }
            }
            RunLogger.e("${config.target.description} 自动导航未识别到开始战斗或进入挑战，已停止")
            showToast("${config.target.description} 自动导航失败，已停止", true)
            stop()
            return
        }

        gestureDispatcher.performActionDirect(entryPoint.x, entryPoint.y, entryPoint.x, entryPoint.y, true)
        RunLogger.i(
            "${config.target.description} 自动导航识别到 ${config.entryTemplateRegion.templateName}，点击 x=${entryPoint.x.toInt()} y=${entryPoint.y.toInt()}"
        )
        delay(config.delayAfterEntryClickMs)
        if (!isRunning) return

        val postEntryStartBattlePoint = findStartBattleRedPoint()
        if (postEntryStartBattlePoint == null) {
            RunLogger.e("${config.target.description} 自动导航点击进入挑战后未识别到开始战斗，已停止")
            showToast("未识别到开始战斗，已停止", true)
            stop()
            return
        }

        continueAfterStageStartBattleDetected(
            config = config,
            startBattlePoint = postEntryStartBattlePoint,
            logPrefix = "${config.target.description} 自动导航进入挑战后",
            onReady = onReady
        )
    }

    private suspend fun performBaiHuIndirectRecovery(
        config: DirectStageNavigationConfig,
        onReady: () -> Unit
    ): Boolean {
        RunLogger.i("白鹄自动导航直达失败，开始执行间接路线恢复")
        var backAttempts = 0
        while (isRunning && backAttempts <= STAGE_HOME_RECOVERY_MAX_BACK_STEPS) {
            val lantaiPoint = findTemplatePointInVisionRoi(
                templateName = STAGE_HOME_RECOVERY_LANTAI_TEMPLATE,
                centerX = STAGE_HOME_RECOVERY_LANTAI_CENTER_X,
                centerY = STAGE_HOME_RECOVERY_LANTAI_CENTER_Y,
                align = "center",
                roiWidth = STAGE_HOME_RECOVERY_LANTAI_ROI_WIDTH,
                roiHeight = STAGE_HOME_RECOVERY_LANTAI_ROI_HEIGHT,
                threshold = STAGE_HOME_RECOVERY_LANTAI_THRESHOLD,
                logLabel = "白鹄间接路线 览台模板"
            )
            if (!isRunning) return true
            if (lantaiPoint != null) {
                return openLantaiAndContinueToBaiHu(config, lantaiPoint, onReady)
            }
            delay(STAGE_HOME_RECOVERY_BETWEEN_TEMPLATES_DELAY_MS)
            if (!isRunning) return true

            val yuanbaoPoint = findTemplatePointInVisionRoi(
                templateName = STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE,
                centerX = STAGE_HOME_RECOVERY_YUANBAO_CENTER_X,
                centerY = STAGE_HOME_RECOVERY_YUANBAO_CENTER_Y,
                align = "center",
                roiWidth = STAGE_HOME_RECOVERY_YUANBAO_ROI_WIDTH,
                roiHeight = STAGE_HOME_RECOVERY_YUANBAO_ROI_HEIGHT,
                threshold = STAGE_HOME_RECOVERY_YUANBAO_THRESHOLD,
                logLabel = "白鹄间接路线 鸢报模板"
            )
            if (!isRunning) return true
            if (yuanbaoPoint != null) {
                RunLogger.i("白鹄间接路线识别到鸢报入口，执行右滑后查找览台")
                performStageRecoverySwipeRight()
                delay(STAGE_HOME_RECOVERY_SWIPE_DELAY_MS)
                if (!isRunning) return true
                val postSwipeLantaiPoint = findTemplatePointInVisionRoi(
                    templateName = STAGE_HOME_RECOVERY_LANTAI_TEMPLATE,
                    centerX = STAGE_HOME_RECOVERY_LANTAI_CENTER_X,
                    centerY = STAGE_HOME_RECOVERY_LANTAI_CENTER_Y,
                    align = "center",
                    roiWidth = STAGE_HOME_RECOVERY_LANTAI_ROI_WIDTH,
                    roiHeight = STAGE_HOME_RECOVERY_LANTAI_ROI_HEIGHT,
                    threshold = STAGE_HOME_RECOVERY_LANTAI_THRESHOLD,
                    logLabel = "白鹄间接路线 右滑后览台模板"
                )
                if (!isRunning) return true
                if (postSwipeLantaiPoint != null) {
                    return openLantaiAndContinueToBaiHu(config, postSwipeLantaiPoint, onReady)
                }
                RunLogger.e("白鹄间接路线右滑后未识别到览台，已停止")
                showToast("右滑后未识别到览台，已停止", true)
                stop()
                return true
            }

            if (backAttempts >= STAGE_HOME_RECOVERY_MAX_BACK_STEPS) {
                return false
            }

            RunLogger.i("白鹄间接路线未识别到览台或鸢报，执行返回，第${backAttempts + 1}次")
            accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            backAttempts += 1
            delay(STAGE_HOME_RECOVERY_BACK_DELAY_MS)
        }
        return false
    }

    private suspend fun openLantaiAndContinueToBaiHu(
        config: DirectStageNavigationConfig,
        lantaiPoint: PointF,
        onReady: () -> Unit
    ): Boolean {
        gestureDispatcher.performActionDirect(lantaiPoint.x, lantaiPoint.y, lantaiPoint.x, lantaiPoint.y, true)
        RunLogger.i("白鹄间接路线识别到览台，点击 x=${lantaiPoint.x.toInt()} y=${lantaiPoint.y.toInt()}")
        delay(STAGE_HOME_RECOVERY_POST_LANTAI_DELAY_MS)
        if (!isRunning) return true

        val baihuPoint = findTemplatePointInVisionRoi(
            templateName = STAGE_HOME_RECOVERY_BAIHU_TEMPLATE,
            centerX = STAGE_HOME_RECOVERY_BAIHU_CENTER_X,
            centerY = STAGE_HOME_RECOVERY_BAIHU_CENTER_Y,
            align = "center",
            roiWidth = STAGE_HOME_RECOVERY_BAIHU_ROI_WIDTH,
            roiHeight = STAGE_HOME_RECOVERY_BAIHU_ROI_HEIGHT,
            threshold = STAGE_HOME_RECOVERY_BAIHU_THRESHOLD,
            logLabel = "白鹄间接路线 白鹄模板"
        )
        if (!isRunning) return true
        if (baihuPoint == null) {
            RunLogger.e("白鹄间接路线进入览台后未识别到白鹄，已停止")
            showToast("未识别到白鹄，已停止", true)
            stop()
            return true
        }

        gestureDispatcher.performActionDirect(baihuPoint.x, baihuPoint.y, baihuPoint.x, baihuPoint.y, true)
        RunLogger.i("白鹄间接路线识别到白鹄，点击 x=${baihuPoint.x.toInt()} y=${baihuPoint.y.toInt()}")
        delay(STAGE_HOME_RECOVERY_POST_BAIHU_DELAY_MS)
        if (!isRunning) return true

        return continueStageEntryFlow(config, onReady, tryStartBattleFirst = false)
    }

    private suspend fun continueCaveEntryFlow(
        config: DirectStageNavigationConfig,
        qianwangtaofaPoint: PointF,
        onReady: () -> Unit
    ): Boolean {
        gestureDispatcher.performActionDirect(
            qianwangtaofaPoint.x,
            qianwangtaofaPoint.y,
            qianwangtaofaPoint.x,
            qianwangtaofaPoint.y,
            true
        )
        RunLogger.i(
            "${config.target.description} 自动导航识别到 ${config.entryTemplateRegion.templateName}，点击 x=${qianwangtaofaPoint.x.toInt()} y=${qianwangtaofaPoint.y.toInt()}"
        )
        delay(config.delayAfterEntryClickMs)
        if (!isRunning) return true

        val startBattlePoint = findStartBattleRedPoint()
        if (!isRunning) return true
        if (startBattlePoint == null) {
            RunLogger.e("${config.target.description} 自动导航点击前往讨伐后未识别到开始战斗，已停止")
            showToast("未识别到开始战斗，已停止", true)
            stop()
            return true
        }

        return continueAfterStageStartBattleDetected(
            config = config,
            startBattlePoint = startBattlePoint,
            logPrefix = "${config.target.description} 自动导航",
            onReady = onReady
        )
    }

    private suspend fun continueCaveFromDongkuFlow(
        config: DirectStageNavigationConfig,
        dongkuPoint: PointF,
        onReady: () -> Unit
    ): Boolean {
        gestureDispatcher.performActionDirect(
            dongkuPoint.x,
            dongkuPoint.y,
            dongkuPoint.x,
            dongkuPoint.y,
            true
        )
        RunLogger.i(
            "${config.target.description} 自动导航识别到 ${CAVE_DONGKU_TEMPLATE}，点击 x=${dongkuPoint.x.toInt()} y=${dongkuPoint.y.toInt()}"
        )
        delay(config.delayAfterEntryClickMs)
        if (!isRunning) return true

        val qianwangtaofaPoint = findTemplatePointInVisionRoi(
            templateName = config.entryTemplateRegion.templateName,
            centerX = config.entryTemplateRegion.x,
            centerY = config.entryTemplateRegion.y,
            align = config.entryTemplateRegion.align,
            roiWidth = config.entryTemplateRegion.width,
            roiHeight = config.entryTemplateRegion.height,
            threshold = config.entryTemplateRegion.threshold,
            logLabel = "${config.target.description} 前往讨伐模板"
        )
        if (!isRunning) return true
        if (qianwangtaofaPoint == null) {
            RunLogger.e("${config.target.description} 自动导航点击洞窟后未识别到前往讨伐，已停止")
            showToast("未识别到前往讨伐，已停止", true)
            stop()
            return true
        }

        return continueCaveEntryFlow(config, qianwangtaofaPoint, onReady)
    }

    private suspend fun performCaveIndirectHomeRecovery(
        config: DirectStageNavigationConfig,
        onReady: () -> Unit
    ): Boolean {
        RunLogger.i("${config.target.description} 自动导航直达失败，开始执行主页入口路线")
        var backAttempts = 0
        while (isRunning && backAttempts <= STAGE_HOME_RECOVERY_MAX_BACK_STEPS) {
            val xinzhiPoint = findTemplatePointInVisionRoi(
                templateName = STAGE_HOME_RECOVERY_XINZHI_TEMPLATE,
                centerX = STAGE_HOME_RECOVERY_XINZHI_CENTER_X,
                centerY = STAGE_HOME_RECOVERY_XINZHI_CENTER_Y,
                align = "center",
                roiWidth = STAGE_HOME_RECOVERY_XINZHI_ROI_WIDTH,
                roiHeight = STAGE_HOME_RECOVERY_XINZHI_ROI_HEIGHT,
                threshold = STAGE_HOME_RECOVERY_XINZHI_THRESHOLD,
                logLabel = "${config.target.description} 主页入口 心志模板"
            )
            if (!isRunning) return true
            if (xinzhiPoint != null) {
                return openXinzhiAndContinueToCave(config, xinzhiPoint, onReady)
            }

            delay(STAGE_HOME_RECOVERY_BETWEEN_TEMPLATES_DELAY_MS)
            if (!isRunning) return true

            val lantaiPoint = findTemplatePointInVisionRoi(
                templateName = STAGE_HOME_RECOVERY_LANTAI_TEMPLATE,
                centerX = STAGE_HOME_RECOVERY_LANTAI_CENTER_X,
                centerY = STAGE_HOME_RECOVERY_LANTAI_CENTER_Y,
                align = "center",
                roiWidth = STAGE_HOME_RECOVERY_LANTAI_ROI_WIDTH,
                roiHeight = STAGE_HOME_RECOVERY_LANTAI_ROI_HEIGHT,
                threshold = STAGE_HOME_RECOVERY_LANTAI_THRESHOLD,
                logLabel = "${config.target.description} 主页入口 兰台模板"
            )
            if (!isRunning) return true
            if (lantaiPoint != null) {
                RunLogger.i("${config.target.description} 主页入口识别到兰台，执行右滑后查找心志")
                performStageRecoverySwipeToRight()
                delay(STAGE_HOME_RECOVERY_SWIPE_DELAY_MS)
                if (!isRunning) return true
                val postSwipeXinzhiPoint = findTemplatePointInVisionRoi(
                    templateName = STAGE_HOME_RECOVERY_XINZHI_TEMPLATE,
                    centerX = STAGE_HOME_RECOVERY_XINZHI_CENTER_X,
                    centerY = STAGE_HOME_RECOVERY_XINZHI_CENTER_Y,
                    align = "center",
                    roiWidth = STAGE_HOME_RECOVERY_XINZHI_ROI_WIDTH,
                    roiHeight = STAGE_HOME_RECOVERY_XINZHI_ROI_HEIGHT,
                    threshold = STAGE_HOME_RECOVERY_XINZHI_THRESHOLD,
                    logLabel = "${config.target.description} 右滑后心志模板"
                )
                if (!isRunning) return true
                if (postSwipeXinzhiPoint != null) {
                    return openXinzhiAndContinueToCave(config, postSwipeXinzhiPoint, onReady)
                }
                RunLogger.e("${config.target.description} 右滑后未识别到心志，已停止")
                showToast("右滑后未识别到心志，已停止", true)
                stop()
                return true
            }

            if (backAttempts >= STAGE_HOME_RECOVERY_MAX_BACK_STEPS) {
                return false
            }

            RunLogger.i("${config.target.description} 主页入口未识别到心志或兰台，执行返回，第${backAttempts + 1}次")
            accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            backAttempts += 1
            delay(STAGE_HOME_RECOVERY_BACK_DELAY_MS)
        }
        return false
    }

    private suspend fun openXinzhiAndContinueToCave(
        config: DirectStageNavigationConfig,
        xinzhiPoint: PointF,
        onReady: () -> Unit
    ): Boolean {
        gestureDispatcher.performActionDirect(xinzhiPoint.x, xinzhiPoint.y, xinzhiPoint.x, xinzhiPoint.y, true)
        RunLogger.i("${config.target.description} 主页入口识别到心志，点击 x=${xinzhiPoint.x.toInt()} y=${xinzhiPoint.y.toInt()}")
        delay(STAGE_HOME_RECOVERY_POST_XINZHI_DELAY_MS)
        if (!isRunning) return true

        val lixianPoint = findTemplatePointInVisionRoi(
            templateName = STAGE_HOME_RECOVERY_LIXIAN_TEMPLATE,
            centerX = STAGE_HOME_RECOVERY_LIXIAN_CENTER_X,
            centerY = STAGE_HOME_RECOVERY_LIXIAN_CENTER_Y,
            align = "center",
            roiWidth = STAGE_HOME_RECOVERY_LIXIAN_ROI_WIDTH,
            roiHeight = STAGE_HOME_RECOVERY_LIXIAN_ROI_HEIGHT,
            threshold = STAGE_HOME_RECOVERY_LIXIAN_THRESHOLD,
            logLabel = "${config.target.description} 主页入口 历险模板"
        )
        if (!isRunning) return true
        if (lixianPoint == null) {
            RunLogger.e("${config.target.description} 进入心志后未识别到历险，已停止")
            showToast("未识别到历险，已停止", true)
            stop()
            return true
        }

        gestureDispatcher.performActionDirect(
            lixianPoint.x,
            lixianPoint.y,
            lixianPoint.x,
            lixianPoint.y,
            true
        )
        RunLogger.i("${config.target.description} 主页入口识别到历险，点击 x=${lixianPoint.x.toInt()} y=${lixianPoint.y.toInt()}")
        delay(STAGE_HOME_RECOVERY_POST_LIXIAN_DELAY_MS)
        if (!isRunning) return true

        val dongkuPoint = findCaveDongkuPoint(
            logLabel = "${config.target.description} 主页入口 洞窟模板"
        )
        if (!isRunning) return true
        if (dongkuPoint == null) {
            RunLogger.e("${config.target.description} 进入历险后未识别到洞窟，已停止")
            showToast("未识别到洞窟，已停止", true)
            stop()
            return true
        }

        return continueCaveFromDongkuFlow(config, dongkuPoint, onReady)
    }

    private suspend fun findCaveDongkuPoint(logLabel: String): PointF? {
        val primaryPoint = findTemplatePointInVisionRoi(
            templateName = CAVE_DONGKU_TEMPLATE,
            centerX = CAVE_DONGKU_CENTER_X,
            centerY = CAVE_DONGKU_CENTER_Y,
            align = "center",
            roiWidth = CAVE_DONGKU_ROI_WIDTH,
            roiHeight = CAVE_DONGKU_ROI_HEIGHT,
            threshold = CAVE_DONGKU_THRESHOLD,
            logLabel = logLabel
        )
        if (primaryPoint != null) return primaryPoint

        return findTemplatePointInVisionRoi(
            templateName = CAVE_DONGKU_TEMPLATE_FALLBACK,
            centerX = CAVE_DONGKU_CENTER_X,
            centerY = CAVE_DONGKU_CENTER_Y,
            align = "center",
            roiWidth = CAVE_DONGKU_ROI_WIDTH,
            roiHeight = CAVE_DONGKU_ROI_HEIGHT,
            threshold = CAVE_DONGKU_THRESHOLD,
            logLabel = "$logLabel fallback"
        )
    }

    private fun findCaveDongkuPointFromScreenshot(
        screenshot: Bitmap,
        logLabel: String
    ): PointF? {
        val primaryPoint = findTemplatePointInVisionRoiFromScreenshot(
            screenshot = screenshot,
            templateName = CAVE_DONGKU_TEMPLATE,
            centerX = CAVE_DONGKU_CENTER_X,
            centerY = CAVE_DONGKU_CENTER_Y,
            align = "center",
            roiWidth = CAVE_DONGKU_ROI_WIDTH,
            roiHeight = CAVE_DONGKU_ROI_HEIGHT,
            threshold = CAVE_DONGKU_THRESHOLD,
            logLabel = logLabel
        )
        if (primaryPoint != null) return primaryPoint

        return findTemplatePointInVisionRoiFromScreenshot(
            screenshot = screenshot,
            templateName = CAVE_DONGKU_TEMPLATE_FALLBACK,
            centerX = CAVE_DONGKU_CENTER_X,
            centerY = CAVE_DONGKU_CENTER_Y,
            align = "center",
            roiWidth = CAVE_DONGKU_ROI_WIDTH,
            roiHeight = CAVE_DONGKU_ROI_HEIGHT,
            threshold = CAVE_DONGKU_THRESHOLD,
            logLabel = "$logLabel fallback"
        )
    }

    private suspend fun performRelicIndirectAutoNavigation(
        config: DirectStageNavigationConfig,
        selectionRegion: TemplateRegionConfig,
        onReady: () -> Unit
    ): Boolean {
        RunLogger.i("${config.target.description} 自动导航直达失败，开始执行主页入口路线")
        var backAttempts = 0
        while (isRunning && backAttempts <= STAGE_HOME_RECOVERY_MAX_BACK_STEPS) {
            val digongPoint = findTemplatePointInVisionRoi(
                templateName = STAGE_HOME_RECOVERY_DIGONG_TEMPLATE,
                centerX = STAGE_HOME_RECOVERY_DIGONG_CENTER_X,
                centerY = STAGE_HOME_RECOVERY_DIGONG_CENTER_Y,
                align = "center",
                roiWidth = STAGE_HOME_RECOVERY_DIGONG_ROI_WIDTH,
                roiHeight = STAGE_HOME_RECOVERY_DIGONG_ROI_HEIGHT,
                threshold = STAGE_HOME_RECOVERY_DIGONG_THRESHOLD,
                logLabel = "${config.target.description} 主页入口 地宫模板"
            )
            if (!isRunning) return true
            if (digongPoint != null) {
                return openDigongAndContinueToRelic(config, selectionRegion, digongPoint, onReady)
            }

            delay(STAGE_HOME_RECOVERY_BETWEEN_TEMPLATES_DELAY_MS)
            if (!isRunning) return true

            val yuanbaoPoint = findTemplatePointInVisionRoi(
                templateName = STAGE_HOME_RECOVERY_YUANBAO_TEMPLATE,
                centerX = STAGE_HOME_RECOVERY_YUANBAO_CENTER_X,
                centerY = STAGE_HOME_RECOVERY_YUANBAO_CENTER_Y,
                align = "center",
                roiWidth = STAGE_HOME_RECOVERY_YUANBAO_ROI_WIDTH,
                roiHeight = STAGE_HOME_RECOVERY_YUANBAO_ROI_HEIGHT,
                threshold = STAGE_HOME_RECOVERY_YUANBAO_THRESHOLD,
                logLabel = "${config.target.description} 主页入口 鸢报模板"
            )
            if (!isRunning) return true
            if (yuanbaoPoint != null) {
                RunLogger.i("${config.target.description} 主页入口识别到鸢报，执行左滑后查找地宫")
                performStageRecoverySwipeRight()
                delay(STAGE_HOME_RECOVERY_SWIPE_DELAY_MS)
                if (!isRunning) return true
                val postSwipeDigongPoint = findTemplatePointInVisionRoi(
                    templateName = STAGE_HOME_RECOVERY_DIGONG_TEMPLATE,
                    centerX = STAGE_HOME_RECOVERY_DIGONG_CENTER_X,
                    centerY = STAGE_HOME_RECOVERY_DIGONG_CENTER_Y,
                    align = "center",
                    roiWidth = STAGE_HOME_RECOVERY_DIGONG_ROI_WIDTH,
                    roiHeight = STAGE_HOME_RECOVERY_DIGONG_ROI_HEIGHT,
                    threshold = STAGE_HOME_RECOVERY_DIGONG_THRESHOLD,
                    logLabel = "${config.target.description} 左滑后地宫模板"
                )
                if (!isRunning) return true
                if (postSwipeDigongPoint != null) {
                    return openDigongAndContinueToRelic(config, selectionRegion, postSwipeDigongPoint, onReady)
                }
                RunLogger.e("${config.target.description} 左滑后未识别到地宫，已停止")
                showToast("左滑后未识别到地宫，已停止", true)
                stop()
                return true
            }

            if (backAttempts >= STAGE_HOME_RECOVERY_MAX_BACK_STEPS) {
                return false
            }

            RunLogger.i("${config.target.description} 主页入口未识别到地宫或鸢报，执行返回，第${backAttempts + 1}次")
            accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            backAttempts += 1
            delay(STAGE_HOME_RECOVERY_BACK_DELAY_MS)
        }
        return false
    }

    private suspend fun openDigongAndContinueToRelic(
        config: DirectStageNavigationConfig,
        selectionRegion: TemplateRegionConfig,
        digongPoint: PointF,
        onReady: () -> Unit
    ): Boolean {
        gestureDispatcher.performActionDirect(digongPoint.x, digongPoint.y, digongPoint.x, digongPoint.y, true)
        RunLogger.i("${config.target.description} 主页入口识别到地宫，点击 x=${digongPoint.x.toInt()} y=${digongPoint.y.toInt()}")
        delay(STAGE_HOME_RECOVERY_POST_DIGONG_DELAY_MS)
        if (!isRunning) return true

        val yijiRukouPoint = findTemplatePointInVisionRoi(
            templateName = STAGE_HOME_RECOVERY_YIJI_RUKOU_TEMPLATE,
            centerX = STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_X,
            centerY = STAGE_HOME_RECOVERY_YIJI_RUKOU_CENTER_Y,
            align = "bottom",
            roiWidth = STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_WIDTH,
            roiHeight = STAGE_HOME_RECOVERY_YIJI_RUKOU_ROI_HEIGHT,
            threshold = STAGE_HOME_RECOVERY_YIJI_RUKOU_THRESHOLD,
            logLabel = "${config.target.description} 主页入口 遗迹入口模板"
        )
        if (!isRunning) return true
        if (yijiRukouPoint == null) {
            RunLogger.e("${config.target.description} 进入地宫后未识别到遗迹入口，已停止")
            showToast("未识别到遗迹入口，已停止", true)
            stop()
            return true
        }

        gestureDispatcher.performActionDirect(
            yijiRukouPoint.x,
            yijiRukouPoint.y,
            yijiRukouPoint.x,
            yijiRukouPoint.y,
            true
        )
        RunLogger.i("${config.target.description} 主页入口识别到遗迹入口，点击 x=${yijiRukouPoint.x.toInt()} y=${yijiRukouPoint.y.toInt()}")
        delay(STAGE_HOME_RECOVERY_POST_YIJI_RUKOU_DELAY_MS)
        if (!isRunning) return true

        val selectionPoint = findTemplatePointInVisionRoi(
            templateName = selectionRegion.templateName,
            centerX = selectionRegion.x,
            centerY = selectionRegion.y,
            align = selectionRegion.align,
            roiWidth = selectionRegion.width,
            roiHeight = selectionRegion.height,
            threshold = selectionRegion.threshold,
            logLabel = "${config.target.description} 主页入口 关卡模板"
        )
        if (!isRunning) return true
        if (selectionPoint == null) {
            RunLogger.e("${config.target.description} 进入遗迹入口后未识别到目标关卡，已停止")
            showToast("未识别到目标关卡，已停止", true)
            stop()
            return true
        }

        gestureDispatcher.performActionDirect(
            selectionPoint.x,
            selectionPoint.y,
            selectionPoint.x,
            selectionPoint.y,
            true
        )
        RunLogger.i("${config.target.description} 主页入口识别到 ${selectionRegion.templateName}，点击 x=${selectionPoint.x.toInt()} y=${selectionPoint.y.toInt()}")
        delay(config.delayAfterEntryClickMs)
        if (!isRunning) return true

        return continueStageEntryFlow(config, onReady, tryStartBattleFirst = false)
    }

    private suspend fun performSelectionRecoveryRoute(
        config: DirectStageNavigationConfig,
        selectionRegion: TemplateRegionConfig,
        onReady: () -> Unit
    ): Boolean {
        RunLogger.i("${config.target.description} 自动导航直达失败，开始执行重开恢复路线")
        val selectionPoint = findTemplatePointInVisionRoi(
            templateName = selectionRegion.templateName,
            centerX = selectionRegion.x,
            centerY = selectionRegion.y,
            align = selectionRegion.align,
            roiWidth = selectionRegion.width,
            roiHeight = selectionRegion.height,
            threshold = selectionRegion.threshold,
            logLabel = "${config.target.description} 重开关卡模板"
        ) ?: return false

        if (!isRunning) return true
        gestureDispatcher.performActionDirect(
            selectionPoint.x,
            selectionPoint.y,
            selectionPoint.x,
            selectionPoint.y,
            true
        )
        RunLogger.i(
            "${config.target.description} 重开路线识别到 ${selectionRegion.templateName}，点击 x=${selectionPoint.x.toInt()} y=${selectionPoint.y.toInt()}"
        )
        delay(config.delayAfterEntryClickMs)
        if (!isRunning) return true

        return continueStageEntryFlow(config, onReady, tryStartBattleFirst = false)
    }

    private suspend fun continueStageEntryFlow(
        config: DirectStageNavigationConfig,
        onReady: () -> Unit,
        tryStartBattleFirst: Boolean = true
    ): Boolean {
        if (tryStartBattleFirst) {
            val startBattlePoint = findStartBattleRedPoint()
            if (!isRunning) return true
            if (startBattlePoint != null) {
                return continueAfterStageStartBattleDetected(
                    config = config,
                    startBattlePoint = startBattlePoint,
                    logPrefix = "${config.target.description} 自动导航",
                    onReady = onReady
                )
            }
            delay(STAGE_SCREENSHOT_COOLDOWN_DELAY_MS)
            if (!isRunning) return true
        }

        val entryPoint = findTemplatePointInVisionRoi(
            templateName = config.entryTemplateRegion.templateName,
            centerX = config.entryTemplateRegion.x,
            centerY = config.entryTemplateRegion.y,
            align = config.entryTemplateRegion.align,
            roiWidth = config.entryTemplateRegion.width,
            roiHeight = config.entryTemplateRegion.height,
            threshold = config.entryTemplateRegion.threshold,
            logLabel = "${config.target.description} 进入挑战模板"
        )
        if (!isRunning) return true
        if (entryPoint == null) {
            RunLogger.e("${config.target.description} 间接路线未识别到开始战斗或进入挑战，已停止")
            showToast("${config.target.description} 自动导航失败，已停止", true)
            stop()
            return true
        }

        gestureDispatcher.performActionDirect(entryPoint.x, entryPoint.y, entryPoint.x, entryPoint.y, true)
        RunLogger.i(
            "${config.target.description} 自动导航识别到 ${config.entryTemplateRegion.templateName}，点击 x=${entryPoint.x.toInt()} y=${entryPoint.y.toInt()}"
        )
        delay(config.delayAfterEntryClickMs)
        if (!isRunning) return true

        val postEntryStartBattlePoint = findStartBattleRedPoint()
        if (postEntryStartBattlePoint == null) {
            RunLogger.e("${config.target.description} 自动导航点击进入挑战后未识别到开始战斗，已停止")
            showToast("未识别到开始战斗，已停止", true)
            stop()
            return true
        }

        return continueAfterStageStartBattleDetected(
            config = config,
            startBattlePoint = postEntryStartBattlePoint,
            logPrefix = "${config.target.description} 自动导航进入挑战后",
            onReady = onReady
        )
    }

    private fun performStageRecoverySwipeRight() {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val startX = screenWidth * 0.78f
        val endX = screenWidth * 0.28f
        val y = screenHeight * 0.52f
        val path = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, STAGE_HOME_RECOVERY_SWIPE_DURATION_MS))
            .build()
        accessibilityService.dispatchGesture(gesture, null, null)
    }

    private fun performStageRecoverySwipeToRight() {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val startX = screenWidth * 0.28f
        val endX = screenWidth * 0.78f
        val y = screenHeight * 0.52f
        val path = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, STAGE_HOME_RECOVERY_SWIPE_DURATION_MS))
            .build()
        accessibilityService.dispatchGesture(gesture, null, null)
    }

    private suspend fun performOrangeStarCheck(): Boolean {
        var shapeWithoutGlow: OrangeStarAttemptResult? = null

        repeat(ORANGE_STAR_ATTEMPTS) { index ->
            val result = detectOrangeStarAttempt(index + 1)
            if (!isRunning) return false

            when (result.status) {
                OrangeStarAttemptStatus.HIT -> {
                    val candidate = result.candidate!!
                    RunLogger.i(
                        "橙星检测第${result.attemptIndex}次命中 center=(${candidate.center.x.toInt()},${candidate.center.y.toInt()}) " +
                            "score=${"%.3f".format(Locale.US, candidate.confidence)} " +
                            "glow=${"%.3f".format(Locale.US, candidate.glowScore)} " +
                            "glowRatio=${"%.3f".format(Locale.US, candidate.glowRatio)} " +
                            "shape=${"%.3f".format(Locale.US, candidate.shapeScore)} " +
                            "arm=${"%.3f".format(Locale.US, candidate.armContinuity)} " +
                            "size=${candidate.boundsWidth}x${candidate.boundsHeight}"
                    )
                    showToast("橙星检测命中", false)
                    return true
                }

                OrangeStarAttemptStatus.SHAPE_NO_ORANGE -> {
                    val candidate = result.candidate!!
                    RunLogger.i(
                        "橙星检测第${result.attemptIndex}次检测到观星形状但未见橙色辉光 " +
                            "score=${"%.3f".format(Locale.US, candidate.confidence)} " +
                            "glow=${"%.3f".format(Locale.US, candidate.glowScore)} " +
                            "glowRatio=${"%.3f".format(Locale.US, candidate.glowRatio)} " +
                            "shape=${"%.3f".format(Locale.US, candidate.shapeScore)} " +
                            "arm=${"%.3f".format(Locale.US, candidate.armContinuity)} " +
                            "size=${candidate.boundsWidth}x${candidate.boundsHeight}"
                    )
                    if (shapeWithoutGlow == null) {
                        shapeWithoutGlow = result
                    }
                }

                OrangeStarAttemptStatus.NO_SHAPE -> {
                    RunLogger.i("橙星检测第${result.attemptIndex}次未检测到观星形状")
                }
            }

            if (index < ORANGE_STAR_ATTEMPTS - 1) {
                delay(ORANGE_STAR_ATTEMPT_INTERVAL_MS)
            }
        }

        if (shapeWithoutGlow != null) {
            handleBackRecoveryAndStop(
                reasonLabel = "橙星检测",
                toastMessage = "橙星未命中，已执行一次返回",
                successLog = "橙星检测三次确认未命中，已执行一次全局返回",
                failureLog = "橙星检测未命中后执行全局返回失败"
            )
            return false
        }

        RunLogger.e("未检测到密探观星，请确认检测区域是否正确")
        tryRecoveryTemplateAfterReturn()
        return isRunning
    }

    private suspend fun performDeathCheckBatch(
        slotIndices: List<Int>,
        sharedScreenshot: Bitmap? = null
    ): Boolean {
        RunLogger.i(
            "阵亡检测批量开始 slots=${slotIndices.joinToString(",")} " +
                "sharedScreenshot=${sharedScreenshot?.width ?: 0}x${sharedScreenshot?.height ?: 0}"
        )
        if (slotIndices.isEmpty()) {
            return true
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            RunLogger.e("阵亡检测仅支持 Android 11 及以上")
            return true
        }

        val screenshot = sharedScreenshot ?: captureScreenshotBitmap()
        if (screenshot == null) {
            RunLogger.e("阵亡检测截图失败，请确认系统截图权限是否正常")
            return true
        }

        val results = withContext(Dispatchers.Default) {
            slotIndices.map { slotIndex ->
                detectCharacterDeathFromScreenshot(slotIndex, screenshot)
            }
        }
        if (!isRunning) return false

        for ((index, result) in results.withIndex()) {
            val slotIndex = slotIndices[index]
            if (result == null) {
                RunLogger.e("阵亡检测第${slotIndex}号位失败，请确认检测区域是否正确")
                continue
            }

            RunLogger.i(
                "阵亡检测第${result.slotIndex}号位 meanS=${"%.2f".format(Locale.US, result.meanSaturation)} " +
                    "threshold=${"%.2f".format(Locale.US, DEATH_CHECK_SATURATION_THRESHOLD)} " +
                    "dead=${result.isDead}"
            )

            if (!result.isDead) continue

            handleBackRecoveryAndStop(
                reasonLabel = "阵亡检测",
                toastMessage = "检测到第${result.slotIndex}人阵亡，已执行一次返回",
                successLog = "阵亡检测命中第${result.slotIndex}号位阵亡，已执行一次全局返回",
                failureLog = "阵亡检测命中后执行全局返回失败"
            )
            return false
        }

        return isRunning
    }

    private fun checkAndSwitchTarget(turn: Int, step: Int, onNext: () -> Unit) {
        val tasks = instructionList.filter {
            it.turn == turn &&
                it.step == step &&
                (
                    it.type == InstructionType.TARGET_SWITCH ||
                        it.type == InstructionType.TARGET_SWITCH_LEFT ||
                        it.type == InstructionType.TARGET_SWITCH_RIGHT
                    )
        }
        if (tasks.isEmpty()) {
            onNext()
            return
        }

        val validTasks = tasks.filter { it.value > 0L }
        if (validTasks.isEmpty()) {
            onNext()
            return
        }

        performTargetSwitchTasks(validTasks, 0, onNext)
    }

    private fun performTargetSwitchTasks(
        tasks: List<ScriptInstruction>,
        index: Int,
        onComplete: () -> Unit
    ) {
        if (index >= tasks.size || !isRunning) {
            onComplete()
            return
        }

        val task = tasks[index]
        val directionLabel = when (task.type) {
            InstructionType.TARGET_SWITCH_LEFT -> "左"
            InstructionType.TARGET_SWITCH,
            InstructionType.TARGET_SWITCH_RIGHT -> "右"
            else -> ""
        }
        onHudUpdated("切${directionLabel}目标 x${task.value}", true)
        performTargetClicks(task.type, task.value.toInt()) {
            performTargetSwitchTasks(tasks, index + 1, onComplete)
        }
    }

    private fun performTargetClicks(type: InstructionType, count: Int, onComplete: () -> Unit) {
        if (count <= 0 || !isRunning) {
            onComplete()
            return
        }
        val designX = when (type) {
            InstructionType.TARGET_SWITCH_LEFT -> GameConstants.DESIGN_TARGET_LEFT_X
            InstructionType.TARGET_SWITCH,
            InstructionType.TARGET_SWITCH_RIGHT -> GameConstants.DESIGN_TARGET_X
            else -> GameConstants.DESIGN_TARGET_X
        }
        val x = coordinateManager.getTargetCoordinates(
            designX,
            GameConstants.DESIGN_TARGET_Y_TOP
        ).x
        val y = coordinateManager.getTargetCoordinates(
            designX,
            GameConstants.DESIGN_TARGET_Y_TOP
        ).y

        serviceScope.launch {
            for (i in 0 until count) {
                if (!isRunning) break
                gestureDispatcher.performActionDirect(x, y, x, y, true)
                delay(500)
            }
            if (isRunning) {
                delay(getTargetSwitchSettleDelayMs())
                onComplete()
            }
        }
    }

    private fun resolveDelayDelta(turn: Int, step: Int): Long {
        return instructionList
            .asSequence()
            .filter { it.turn == turn && it.step == step }
            .sumOf { instruction ->
                when (instruction.type) {
                    InstructionType.DELAY_ADD -> instruction.value
                    InstructionType.DELAY_SUBTRACT -> -instruction.value
                    else -> 0L
                }
            }
    }


    private fun getNextTurnFirstAction(): String {
        val nextIndex = currentExecutingRowIndex + 1
        if (nextIndex >= followData.size) return "END"
        return try {
            val actions = parseRowActions(followData[nextIndex])
            if (actions.isNotEmpty()) "${actions[0].stepIndex}${actions[0].command}" else "空"
        } catch (e: Exception) {
            "Err"
        }
    }

    private suspend fun detectOrangeStar(): OrangeStarCandidate? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            RunLogger.e("橙星检测仅支持 Android 11 及以上")
            return null
        }

        val screenshot = captureScreenshotBitmap() ?: return null
        return try {
            withContext(Dispatchers.Default) {
                findOrangeStarCandidate(screenshot)
            }
        } finally {
            if (!screenshot.isRecycled) {
                screenshot.recycle()
            }
        }
    }

    private suspend fun detectOrangeStarAttempt(attemptIndex: Int): OrangeStarAttemptResult {
        val candidate = detectOrangeStar()
        if (candidate == null || candidate.shapeScore < ORANGE_STAR_SHAPE_THRESHOLD) {
            return OrangeStarAttemptResult(
                attemptIndex = attemptIndex,
                status = OrangeStarAttemptStatus.NO_SHAPE
            )
        }

        val isOrangeHit =
            candidate.confidence >= ORANGE_STAR_THRESHOLD &&
                candidate.glowRatio >= ORANGE_STAR_MIN_GLOW_RATIO

        return OrangeStarAttemptResult(
            attemptIndex = attemptIndex,
            status = if (isOrangeHit) {
                OrangeStarAttemptStatus.HIT
            } else {
                OrangeStarAttemptStatus.SHAPE_NO_ORANGE
            },
            candidate = candidate
        )
    }

    private suspend fun tryRecoveryTemplateAfterReturn(): Boolean {
        delay(1000)
        val matchPoint = findTemplatePointInVisionRoi(
            templateName = ORANGE_STAR_RECOVERY_TEMPLATE,
            centerX = ORANGE_STAR_RECOVERY_CENTER_X,
            centerY = ORANGE_STAR_RECOVERY_CENTER_Y,
            align = "center",
            roiWidth = ORANGE_STAR_RECOVERY_ROI_SIZE,
            roiHeight = ORANGE_STAR_RECOVERY_ROI_SIZE,
            threshold = ORANGE_STAR_RECOVERY_THRESHOLD,
            logLabel = "返回补救模板"
        ) ?: run {
            RunLogger.i("返回补救未匹配到模板 ${ORANGE_STAR_RECOVERY_TEMPLATE}")
            return false
        }

        gestureDispatcher.performActionDirect(matchPoint.x, matchPoint.y, matchPoint.x, matchPoint.y, true)
        RunLogger.i(
            "返回补救匹配到模板 ${ORANGE_STAR_RECOVERY_TEMPLATE}，点击 x=${matchPoint.x.toInt()} y=${matchPoint.y.toInt()}"
        )
        showToast("已识别 queding2，已点击", false)
        playRecoveryFeedback()
        delay(300)
        return true
    }

    private suspend fun handleBackRecoveryAndStop(
        reasonLabel: String,
        toastMessage: String,
        successLog: String,
        failureLog: String
    ) {
        val backSuccess = accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        if (!backSuccess) {
            RunLogger.e(failureLog)
        } else {
            RunLogger.i(successLog)
        }
        showToast(toastMessage, false)
        delay(500)
        val confirmed = tryRecoveryTemplateAfterReturn()
        if (!isRunning) {
            return
        }

        if (confirmed && performRecoveryBranchAfterConfirm(reasonLabel)) {
            return
        }

        if (isRunning) {
            RunLogger.i("${reasonLabel}返回后终止本次跟打")
            stop()
        }
    }

    private suspend fun performRecoveryBranchAfterConfirm(reasonLabel: String): Boolean {
        val target = getSelectedStageAutoNavTarget()
        if (target == null) {
            RunLogger.e("${reasonLabel}确认后未配置关卡自动导航，无法执行重开分支")
            return false
        }

        val config = BattleStageNavigationRegistry.getDirectConfig(target)
        if (config == null) {
            RunLogger.e("${reasonLabel}确认后未找到关卡 ${target.description} 的重开配置")
            return false
        }

        delay(2000)
        if (!isRunning) return true

        return when {
            target == BattleStageTarget.BAI_HU -> {
                RunLogger.i("${reasonLabel}确认后进入白鹄重开分支")
                continueStageEntryFlow(config, onReady = { restartFromFirstTurn() }, tryStartBattleFirst = false)
            }
            target.isCaveTarget() && config.recoverySelectionRegion != null -> {
                RunLogger.i("${reasonLabel}确认后进入${target.description}重开分支")
                performCaveRecoveryRoute(
                    config = config,
                    selectionRegion = config.recoverySelectionRegion,
                    onReady = { restartFromFirstTurn() }
                )
            }
            config.recoverySelectionRegion != null -> {
                RunLogger.i("${reasonLabel}确认后进入${target.description}重开分支")
                performSelectionRecoveryRoute(
                    config = config,
                    selectionRegion = config.recoverySelectionRegion,
                    onReady = { restartFromFirstTurn() }
                )
            }
            else -> {
                RunLogger.e("${reasonLabel}确认后未找到 ${target.description} 的重开分支")
                false
            }
        }
    }

    private fun getSelectedStageAutoNavTarget(): BattleStageTarget? {
        val navigationTask = instructionList.firstOrNull {
            it.type == InstructionType.STAGE_AUTO_NAV
        }?.normalized() ?: return null
        return decodeStageAutoNavTarget(navigationTask.value)
    }

    private fun shouldAutoEnterNextCaveFloor(): Boolean {
        val navigationTask = instructionList.firstOrNull {
            it.type == InstructionType.STAGE_AUTO_NAV
        }?.normalized() ?: return false
        val target = decodeStageAutoNavTarget(navigationTask.value) ?: return false
        return target.isCaveTarget() && isStageAutoNavAutoEnterNextFloorEnabled(navigationTask.value)
    }

    private suspend fun continueAfterCaveNextFloorCheck(
        triggerLabel: String,
        onNextTurn: () -> Unit
    ) {
        try {
            val handled = tryAutoEnterNextCaveFloorIfNeeded(triggerLabel = triggerLabel)
            if (!handled && isRunning) {
                onNextTurn()
            }
        } catch (t: Throwable) {
            RunLogger.e("$triggerLabel 下一回合衔接异常", t)
            showToast("进入下一回合异常，已停止", true)
            stop()
        }
    }

    private suspend fun tryAutoEnterNextCaveFloorIfNeeded(
        triggerLabel: String,
        sharedScreenshot: Bitmap? = null
    ): Boolean {
        if (!isRunning || !shouldAutoEnterNextCaveFloor()) return false
        val target = getSelectedStageAutoNavTarget() ?: return false
        val config = BattleStageNavigationRegistry.getDirectConfig(target) ?: return false
        val nextFloorPoint = if (sharedScreenshot != null) {
            withContext(Dispatchers.Default) {
                findNextCaveFloorPointFromScreenshot(
                    screenshot = sharedScreenshot,
                    logLabel = "$triggerLabel 下一层模板"
                )
            }
        } else {
            findNextCaveFloorPoint("$triggerLabel 下一层模板")
        } ?: return false

        gestureDispatcher.performActionDirect(
            nextFloorPoint.x,
            nextFloorPoint.y,
            nextFloorPoint.x,
            nextFloorPoint.y,
            true
        )
        RunLogger.i(
            "$triggerLabel 识别到${CAVE_NEXT_FLOOR_TEMPLATE}，点击 x=${nextFloorPoint.x.toInt()} y=${nextFloorPoint.y.toInt()}"
        )
        delay(config.delayAfterEntryClickMs)
        if (!isRunning) return true

        val startBattlePoint = findStartBattleRedPoint()
        if (!isRunning) return true
        if (startBattlePoint == null) {
            RunLogger.e("$triggerLabel 点击下一层后未识别到开始战斗，已停止")
            showToast("已识别到下一层，但未识别到开始战斗", true)
            stop()
            return true
        }

        gestureDispatcher.performActionDirect(
            startBattlePoint.x,
            startBattlePoint.y,
            startBattlePoint.x,
            startBattlePoint.y,
            true
        )
        RunLogger.i(
            "$triggerLabel 识别到开始战斗，点击 x=${startBattlePoint.x.toInt()} y=${startBattlePoint.y.toInt()}"
        )
        delay(config.delayAfterStartBattleClickMs)
        if (isRunning) {
            restartFromFirstTurn("已进入下一层，重新从第1回合开始")
        }
        return true
    }

    private suspend fun findNextCaveFloorPoint(logLabel: String): PointF? {
        return findTemplatePointInVisionRoi(
            templateName = CAVE_NEXT_FLOOR_TEMPLATE,
            centerX = CAVE_NEXT_FLOOR_CENTER_X,
            centerY = CAVE_NEXT_FLOOR_CENTER_Y,
            align = "bottom",
            roiWidth = CAVE_NEXT_FLOOR_ROI_WIDTH,
            roiHeight = CAVE_NEXT_FLOOR_ROI_HEIGHT,
            threshold = CAVE_NEXT_FLOOR_THRESHOLD,
            logLabel = logLabel
        )
    }

    private fun findNextCaveFloorPointFromScreenshot(
        screenshot: Bitmap,
        logLabel: String
    ): PointF? {
        return findTemplatePointInVisionRoiFromScreenshot(
            screenshot = screenshot,
            templateName = CAVE_NEXT_FLOOR_TEMPLATE,
            centerX = CAVE_NEXT_FLOOR_CENTER_X,
            centerY = CAVE_NEXT_FLOOR_CENTER_Y,
            align = "bottom",
            roiWidth = CAVE_NEXT_FLOOR_ROI_WIDTH,
            roiHeight = CAVE_NEXT_FLOOR_ROI_HEIGHT,
            threshold = CAVE_NEXT_FLOOR_THRESHOLD,
            logLabel = logLabel
        )
    }

    private suspend fun performCaveRecoveryRoute(
        config: DirectStageNavigationConfig,
        selectionRegion: TemplateRegionConfig,
        onReady: () -> Unit
    ): Boolean {
        RunLogger.i("${config.target.description} 重开路线开始识别 ${selectionRegion.templateName}")
        val selectionPoint = findTemplatePointInVisionRoi(
            templateName = selectionRegion.templateName,
            centerX = selectionRegion.x,
            centerY = selectionRegion.y,
            align = selectionRegion.align,
            roiWidth = selectionRegion.width,
            roiHeight = selectionRegion.height,
            threshold = selectionRegion.threshold,
            logLabel = "${config.target.description} 重开关卡模板"
        ) ?: return false

        if (!isRunning) return true
        gestureDispatcher.performActionDirect(
            selectionPoint.x,
            selectionPoint.y,
            selectionPoint.x,
            selectionPoint.y,
            true
        )
        RunLogger.i(
            "${config.target.description} 重开路线识别到 ${selectionRegion.templateName}，点击 x=${selectionPoint.x.toInt()} y=${selectionPoint.y.toInt()}"
        )
        delay(config.delayAfterEntryClickMs)
        if (!isRunning) return true

        val startBattlePoint = findStartBattleRedPoint()
        if (startBattlePoint == null) {
            RunLogger.e("${config.target.description} 重开路线未识别到开始战斗，已停止")
            showToast("未识别到开始战斗，已停止", true)
            stop()
            return true
        }

        return continueAfterStageStartBattleDetected(
            config = config,
            startBattlePoint = startBattlePoint,
            logPrefix = "${config.target.description} 重开路线",
            onReady = onReady
        )
    }

    private suspend fun detectCharacterDeath(slotIndex: Int): DeathCheckResult? {
        if (slotIndex !in 1..DEATH_CHECK_SLOT_COUNT) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            RunLogger.e("阵亡检测仅支持 Android 11 及以上")
            return null
        }

        val screenshot = captureScreenshotBitmap() ?: return null
        return try {
            withContext(Dispatchers.Default) {
                detectCharacterDeathFromScreenshot(slotIndex, screenshot)
            }
        } finally {
            if (!screenshot.isRecycled) {
                screenshot.recycle()
            }
        }
    }

    private fun detectCharacterDeathFromScreenshot(
        slotIndex: Int,
        screenshot: Bitmap
    ): DeathCheckResult? {
        if (slotIndex !in 1..DEATH_CHECK_SLOT_COUNT) return null
        val slotWidth = VISION_BASE_W / DEATH_CHECK_SLOT_COUNT.toFloat()
        val centerX = slotWidth * (slotIndex - 0.5f)
        val centerY = (DEATH_CHECK_TOP_Y + DEATH_CHECK_BOTTOM_Y) / 2f
        val region = buildVisionSearchRegion(
            screenshot = screenshot,
            centerX = centerX,
            centerY = centerY,
            align = "bottom",
            roiWidth = slotWidth,
            roiHeight = DEATH_CHECK_BOTTOM_Y - DEATH_CHECK_TOP_Y
        ) ?: return null

        return try {
            val meanSaturation = computeCenterMeanSaturation(region.bitmap)
            DeathCheckResult(
                slotIndex = slotIndex,
                meanSaturation = meanSaturation,
                isDead = meanSaturation < DEATH_CHECK_SATURATION_THRESHOLD
            )
        } finally {
            if (!region.bitmap.isRecycled) {
                region.bitmap.recycle()
            }
        }
    }

    private suspend fun findTemplatePointInVisionRoi(
        templateName: String,
        centerX: Float,
        centerY: Float,
        align: String,
        roiWidth: Float,
        roiHeight: Float,
        threshold: Float,
        logLabel: String
    ): PointF? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        val screenshot = captureScreenshotBitmap() ?: return null
        return try {
            withContext(Dispatchers.Default) {
                findTemplatePointInVisionRoiFromScreenshot(
                    screenshot = screenshot,
                    templateName = templateName,
                    centerX = centerX,
                    centerY = centerY,
                    align = align,
                    roiWidth = roiWidth,
                    roiHeight = roiHeight,
                    threshold = threshold,
                    logLabel = logLabel
                )
            }
        } finally {
            if (!screenshot.isRecycled) {
                screenshot.recycle()
            }
        }
    }

    private suspend fun findStartBattleRedPoint(): PointF? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val screenshot = captureScreenshotBitmap() ?: return null
        return try {
            withContext(Dispatchers.Default) {
                findStartBattleRedPointFromScreenshot(screenshot, logLowConfidence = true)
            }
        } finally {
            if (!screenshot.isRecycled) {
                screenshot.recycle()
            }
        }
    }

    private fun findTemplatePointInVisionRoiFromScreenshot(
        screenshot: Bitmap,
        templateName: String,
        centerX: Float,
        centerY: Float,
        align: String,
        roiWidth: Float,
        roiHeight: Float,
        threshold: Float,
        logLabel: String
    ): PointF? {
        val template = loadTemplateBitmap(templateName) ?: return null
        val screenshotGameScale = minOf(screenshot.width / VISION_BASE_W, screenshot.height / VISION_BASE_H)
        val scaledWidth = (template.width * screenshotGameScale).toInt().coerceAtLeast(1)
        val scaledHeight = (template.height * screenshotGameScale).toInt().coerceAtLeast(1)
        val scaledTemplate = if (scaledWidth == template.width && scaledHeight == template.height) {
            template
        } else {
            Bitmap.createScaledBitmap(template, scaledWidth, scaledHeight, true)
        }

        try {
            val (realCenterX, realCenterY) = calculateVisionCoordinate(centerX, centerY, align)
            val (displayWidth, displayHeight) = getRealScreenSize()
            val displayToScreenshotX = screenshot.width.toFloat() / displayWidth
            val displayToScreenshotY = screenshot.height.toFloat() / displayHeight
            val screenshotGameScale = minOf(screenshot.width / VISION_BASE_W, screenshot.height / VISION_BASE_H)
            val screenshotCenterX = realCenterX * displayToScreenshotX
            val screenshotCenterY = realCenterY * displayToScreenshotY
            val realRoiWidth = roiWidth * screenshotGameScale
            val realRoiHeight = roiHeight * screenshotGameScale

            val left = (screenshotCenterX - realRoiWidth / 2f).toInt().coerceIn(0, screenshot.width - 1)
            val top = (screenshotCenterY - realRoiHeight / 2f).toInt().coerceIn(0, screenshot.height - 1)
            val width = realRoiWidth.toInt().coerceAtMost(screenshot.width - left).coerceAtLeast(1)
            val height = realRoiHeight.toInt().coerceAtMost(screenshot.height - top).coerceAtLeast(1)

            if (width < scaledTemplate.width || height < scaledTemplate.height) {
                RunLogger.e("$logLabel 区域小于模板尺寸，无法匹配 $templateName")
                return null
            }

            val roiBitmap = Bitmap.createBitmap(screenshot, left, top, width, height)
            try {
                val match = matchTemplate(
                    screenBitmap = roiBitmap,
                    templateBitmap = scaledTemplate,
                    threshold = threshold,
                    logLabel = logLabel,
                    templateName = templateName
                ) ?: return null
                val screenshotMatchX = left + match.x
                val screenshotMatchY = top + match.y
                return PointF(
                    screenshotMatchX * (displayWidth / screenshot.width.toFloat()),
                    screenshotMatchY * (displayHeight / screenshot.height.toFloat())
                )
            } finally {
                if (!roiBitmap.isRecycled) {
                    roiBitmap.recycle()
                }
            }
        } finally {
            if (scaledTemplate !== template && !scaledTemplate.isRecycled) {
                scaledTemplate.recycle()
            }
        }
    }

    private fun findStartBattleRedPointFromScreenshot(
        screenshot: Bitmap,
        logLowConfidence: Boolean
    ): PointF? {
        val match = findRedRegionInVisionRoiFromScreenshot(
            screenshot = screenshot,
            centerX = START_BATTLE_RED_CENTER_X,
            centerY = START_BATTLE_RED_CENTER_Y,
            align = "bottom",
            roiWidth = START_BATTLE_RED_ROI_WIDTH,
            roiHeight = START_BATTLE_RED_ROI_HEIGHT
        ) ?: return null

        if (match.confidence < START_BATTLE_RED_THRESHOLD) {
            if (logLowConfidence) {
                RunLogger.e(
                    "开始战斗红色区域未达阈值 score=${"%.3f".format(Locale.US, match.confidence)} " +
                        "area=${"%.3f".format(Locale.US, match.areaRatio)} " +
                        "fill=${"%.3f".format(Locale.US, match.fillRatio)} " +
                        "red=${"%.3f".format(Locale.US, match.rednessScore)}"
                )
            }
            return null
        }

        return match.center
    }

    private fun findRedRegionInVisionRoiFromScreenshot(
        screenshot: Bitmap,
        centerX: Float,
        centerY: Float,
        align: String,
        roiWidth: Float,
        roiHeight: Float,
        referenceArea: Float? = null,
        excludeNearWhite: Boolean = false,
        areaWeight: Float = 0.45f,
        fillWeight: Float = 0.25f,
        redWeight: Float = 0.30f
    ): RedRegionMatch? {
        val region = buildVisionSearchRegion(
            screenshot = screenshot,
            centerX = centerX,
            centerY = centerY,
            align = align,
            roiWidth = roiWidth,
            roiHeight = roiHeight
        ) ?: return null

        var normalizedBitmap: Bitmap? = null
        return try {
            normalizedBitmap = normalizeBitmapForDetection(region.bitmap)
            val bitmapForDetection = normalizedBitmap ?: region.bitmap
            val localMatch = findRedRegionMatch(
                bitmap = bitmapForDetection,
                referenceAreaOverride = referenceArea,
                excludeNearWhite = excludeNearWhite,
                areaWeight = areaWeight,
                fillWeight = fillWeight,
                redWeight = redWeight
            ) ?: return null
            val (displayWidth, displayHeight) = getRealScreenSize()
            val displayX = (region.left + localMatch.center.x) * (displayWidth / screenshot.width.toFloat())
            val displayY = (region.top + localMatch.center.y) * (displayHeight / screenshot.height.toFloat())
            localMatch.copy(center = PointF(displayX, displayY))
        } finally {
            if (normalizedBitmap != null && !normalizedBitmap.isRecycled) {
                normalizedBitmap.recycle()
            }
            if (!region.bitmap.isRecycled) {
                region.bitmap.recycle()
            }
        }
    }

    private fun normalizeBitmapForDetection(bitmap: Bitmap): Bitmap? {
        return try {
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
            BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())
                ?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: Throwable) {
            try {
                val fallback = Bitmap.createBitmap(
                    bitmap.width.coerceAtLeast(1),
                    bitmap.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                Canvas(fallback).drawBitmap(bitmap, 0f, 0f, null)
                fallback
            } catch (_: Throwable) {
                null
            }
        }
    }

    private suspend fun detectBattleStateOcrFromScreenshot(
        screenshot: Bitmap,
        logLabel: String
    ): BattleStateOcrResult? {
        val region = buildTopRightVisionSearchRegion(
            screenshot = screenshot,
            roiWidth = STAGE_BATTLE_OCR_ROI_WIDTH,
            roiHeight = STAGE_BATTLE_OCR_ROI_HEIGHT
        ) ?: return null
        return try {
            val rawText = recognizeChineseText(region.bitmap) ?: ""
            val normalizedText = rawText.filterNot { it.isWhitespace() }
            val hitChars = listOf('波', '次', '回', '合').filter { normalizedText.contains(it) }
            val turnNumber = parseTurnNumberFromBattleOcr(normalizedText)
            RunLogger.i(
                "$logLabel raw=${formatOcrLogText(rawText)} " +
                    "normalized=${formatOcrLogText(normalizedText)} " +
                    "hits=${if (hitChars.isEmpty()) "无" else hitChars.joinToString("")} " +
                    "count=${hitChars.size} " +
                    "turn=${turnNumber ?: "无"}"
            )
            BattleStateOcrResult(
                rawText = rawText,
                normalizedText = normalizedText,
                hitChars = hitChars,
                turnNumber = turnNumber
            )
        } finally {
            if (!region.bitmap.isRecycled) {
                region.bitmap.recycle()
            }
        }
    }

    private suspend fun recognizeChineseText(bitmap: Bitmap): String? =
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            )
            continuation.invokeOnCancellation {
                recognizer.close()
            }
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text ->
                    recognizer.close()
                    if (!continuation.isCompleted) {
                        continuation.resume(text.text) {}
                    }
                }
                .addOnFailureListener { error ->
                    recognizer.close()
                    RunLogger.e("战斗OCR识别失败: ${error.message}", error)
                    if (!continuation.isCompleted) {
                        continuation.resume(null) {}
                    }
                }
        }

    private fun formatOcrLogText(text: String): String {
        if (text.isEmpty()) return "\"\""
        return "\"" + text.replace("\n", "\\n") + "\""
    }

    private fun parseTurnNumberFromBattleOcr(normalizedText: String): Int? {
        val match = Regex("回合(\\d+)(?:/(\\d+))?").find(normalizedText) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private suspend fun captureScreenshotBitmap(): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            accessibilityService.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                accessibilityService.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        var buffer: android.hardware.HardwareBuffer? = null
                        var hwBitmap: Bitmap? = null
                        try {
                            buffer = result.hardwareBuffer
                            hwBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            val softwareBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            if (!continuation.isCompleted) {
                                continuation.resume(softwareBitmap) {}
                            } else {
                                softwareBitmap?.recycle()
                            }
                        } catch (t: Throwable) {
                            RunLogger.e("截图转换失败", t)
                            if (!continuation.isCompleted) {
                                continuation.resume(null) {}
                            }
                        } finally {
                            hwBitmap?.recycle()
                            buffer?.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        RunLogger.e("截图失败，错误码=$errorCode")
                        if (!continuation.isCompleted) {
                            continuation.resume(null) {}
                        }
                    }
                }
            )
        }

    private fun findOrangeStarCandidate(bitmap: Bitmap): OrangeStarCandidate? {
        val (displayWidth, displayHeight) = getRealScreenSize()
        if (displayWidth <= 0f || displayHeight <= 0f) return null

        val displayToScreenshotX = bitmap.width.toFloat() / displayWidth
        val displayToScreenshotY = bitmap.height.toFloat() / displayHeight

        val gameScale = minOf(displayWidth / VISION_BASE_W, displayHeight / VISION_BASE_H)
        val gameWidth = VISION_BASE_W * gameScale
        val offsetX = (displayWidth - gameWidth) / 2f
        val statusBarHeight = getRawStatusBarHeight() / 2f

        val realCenterX = offsetX + (ORANGE_STAR_CENTER_X * gameScale)
        val realCenterY = statusBarHeight + (ORANGE_STAR_CENTER_Y * gameScale)
        val roiWidth = ORANGE_STAR_ROI_SIZE * gameScale * displayToScreenshotX
        val roiHeight = ORANGE_STAR_ROI_SIZE * gameScale * displayToScreenshotY
        val screenshotCenterX = realCenterX * displayToScreenshotX
        val screenshotCenterY = realCenterY * displayToScreenshotY

        val left = (screenshotCenterX - roiWidth / 2f).toInt().coerceIn(0, bitmap.width - 1)
        val top = (screenshotCenterY - roiHeight / 2f).toInt().coerceIn(0, bitmap.height - 1)
        val width = roiWidth.toInt().coerceAtMost(bitmap.width - left).coerceAtLeast(1)
        val height = roiHeight.toInt().coerceAtMost(bitmap.height - top).coerceAtLeast(1)

        val roiBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
        return try {
            findOrangeStarInRegion(roiBitmap)?.let { candidate ->
                candidate.copy(
                    center = PointF(candidate.center.x + left, candidate.center.y + top)
                )
            }
        } finally {
            if (!roiBitmap.isRecycled) {
                roiBitmap.recycle()
            }
        }
    }

    private fun findOrangeStarInRegion(bitmap: Bitmap): OrangeStarCandidate? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val pixelCount = width * height
        val coreScores = FloatArray(pixelCount)
        val glowScores = FloatArray(pixelCount)
        val signalScores = FloatArray(pixelCount)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val color = bitmap.getPixel(x, y)
                val coreScore = computeStarCoreScore(color)
                val glowScore = computeOrangePixelScore(color)
                val highlightScore = computeStarHighlightScore(color)
                coreScores[index] = coreScore
                glowScores[index] = glowScore
                signalScores[index] = maxOf(coreScore, glowScore * 0.92f, highlightScore * 0.85f)
            }
        }
        val centers = buildStarCenterCandidates(width, height, coreScores, signalScores)
        var best: OrangeStarCandidate? = null
        for (center in centers) {
            val candidate = evaluateStarCandidate(
                width = width,
                height = height,
                coreScores = coreScores,
                glowScores = glowScores,
                signalScores = signalScores,
                centerX = center.first,
                centerY = center.second
            ) ?: continue
            if (best == null || candidate.confidence > best!!.confidence) {
                best = candidate
            }
        }
        return best
    }

    private fun buildStarCenterCandidates(
        width: Int,
        height: Int,
        coreScores: FloatArray,
        signalScores: FloatArray
    ): List<Pair<Int, Int>> {
        data class Candidate(val x: Int, val y: Int, val score: Float)

        val rawCandidates = ArrayList<Candidate>()
        for (y in 4 until height - 4 step 2) {
            for (x in 4 until width - 4 step 2) {
                val core = sampleAverage(coreScores, width, height, x, y, 2)
                val signal = sampleAverage(signalScores, width, height, x, y, 4)
                val score = core * 0.72f + signal * 0.28f
                if (score >= 0.42f) {
                    rawCandidates.add(Candidate(x, y, score))
                }
            }
        }
        if (rawCandidates.isEmpty()) {
            return emptyList()
        }

        rawCandidates.sortByDescending { it.score }
        val selected = ArrayList<Pair<Int, Int>>(16)
        for (candidate in rawCandidates) {
            val duplicate = selected.any { (sx, sy) ->
                val dx = sx - candidate.x
                val dy = sy - candidate.y
                dx * dx + dy * dy <= 36
            }
            if (duplicate) continue
            selected.add(candidate.x to candidate.y)
            if (selected.size >= 16) break
        }
        return selected
    }

    private fun evaluateStarCandidate(
        width: Int,
        height: Int,
        coreScores: FloatArray,
        glowScores: FloatArray,
        signalScores: FloatArray,
        centerX: Int,
        centerY: Int
    ): OrangeStarCandidate? {
        val maxRadius = minOf(
            centerX,
            centerY,
            width - 1 - centerX,
            height - 1 - centerY,
            42
        )
        if (maxRadius < 10) return null

        val coreScore = sampleAverage(coreScores, width, height, centerX, centerY, 3)
        if (coreScore < 0.45f) return null

        var bestShapeScore = 0f
        var bestContinuity = 0f
        var bestHalfSpanX = 0
        var bestHalfSpanY = 0

        for (degree in 0 until 90 step 6) {
            val theta = Math.toRadians(degree.toDouble())
            val armAngles = doubleArrayOf(
                theta,
                theta + Math.PI / 2.0,
                theta + Math.PI,
                theta + Math.PI * 1.5
            )
            val gapAngles = doubleArrayOf(
                theta + Math.PI / 4.0,
                theta + Math.PI * 3.0 / 4.0,
                theta + Math.PI * 5.0 / 4.0,
                theta + Math.PI * 7.0 / 4.0
            )

            val armMeans = FloatArray(4)
            val armContinuities = FloatArray(4)
            val armSpans = IntArray(4)
            for (i in armAngles.indices) {
                var sum = 0f
                var count = 0
                var active = 0
                var span = 0
                for (radius in 4..maxRadius step 2) {
                    val sample = sampleDirectional(signalScores, width, height, centerX, centerY, armAngles[i], radius)
                    sum += sample
                    count += 1
                    if (sample >= 0.22f) {
                        active += 1
                        span = radius
                    }
                }
                armMeans[i] = if (count == 0) 0f else sum / count.toFloat()
                armContinuities[i] = if (count == 0) 0f else active.toFloat() / count.toFloat()
                armSpans[i] = span
            }

            var gapMeanSum = 0f
            var gapCount = 0
            for (angle in gapAngles) {
                for (radius in 5..maxRadius step 2) {
                    gapMeanSum += sampleDirectional(signalScores, width, height, centerX, centerY, angle, radius)
                    gapCount += 1
                }
            }
            val gapMean = if (gapCount == 0) 0f else gapMeanSum / gapCount.toFloat()
            val armMean = armMeans.average().toFloat()
            val armContinuity = armContinuities.average().toFloat()
            val balanceScore = (
                minOf(armMeans[0], armMeans[2]) / maxOf(armMeans[0], armMeans[2], 0.0001f) +
                    minOf(armMeans[1], armMeans[3]) / maxOf(armMeans[1], armMeans[3], 0.0001f)
                ) / 2f
            val contrastScore = ((armMean - gapMean) / maxOf(armMean, 0.0001f)).coerceIn(0f, 1f)
            val spanScore = armSpans.average().toFloat() / maxRadius.toFloat()
            val shapeScore = (
                contrastScore * 0.42f +
                    armContinuity.coerceIn(0f, 1f) * 0.28f +
                    balanceScore.coerceIn(0f, 1f) * 0.18f +
                    spanScore.coerceIn(0f, 1f) * 0.12f
                ).coerceIn(0f, 1f)

            if (shapeScore > bestShapeScore) {
                bestShapeScore = shapeScore
                bestContinuity = armContinuity
                bestHalfSpanX = maxOf(armSpans[0], armSpans[2]).coerceAtLeast(4)
                bestHalfSpanY = maxOf(armSpans[1], armSpans[3]).coerceAtLeast(4)
            }
        }

        if (bestShapeScore < 0.18f) return null

        val glowScore = sampleAnnulusAverage(glowScores, width, height, centerX, centerY, 4, minOf(maxRadius, 24))
        val glowRatio = sampleAnnulusRatio(glowScores, width, height, centerX, centerY, 4, minOf(maxRadius, 26), 0.42f)
        val sizeScore = (maxOf(bestHalfSpanX, bestHalfSpanY).toFloat() / maxRadius.toFloat()).coerceIn(0f, 1f)
        val confidence = (
            coreScore * 0.26f +
                bestShapeScore * 0.39f +
                glowScore * 0.20f +
                glowRatio.coerceIn(0f, 1f) * 0.10f +
                sizeScore * 0.05f
            ).coerceIn(0f, 1f)

        return OrangeStarCandidate(
            center = PointF(centerX.toFloat(), centerY.toFloat()),
            confidence = confidence,
            glowScore = glowScore,
            glowRatio = glowRatio,
            shapeScore = bestShapeScore,
            armContinuity = bestContinuity.coerceIn(0f, 1f),
            boundsWidth = bestHalfSpanX * 2,
            boundsHeight = bestHalfSpanY * 2
        )
    }

    private fun sampleDirectional(
        scores: FloatArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        angle: Double,
        radius: Int
    ): Float {
        val x = centerX + Math.cos(angle).toFloat() * radius
        val y = centerY + Math.sin(angle).toFloat() * radius
        return sampleNearest(scores, width, height, x, y)
    }

    private fun sampleAverage(
        scores: FloatArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        radius: Int
    ): Float {
        var sum = 0f
        var count = 0
        for (y in centerY - radius..centerY + radius) {
            for (x in centerX - radius..centerX + radius) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dx = x - centerX
                val dy = y - centerY
                if (dx * dx + dy * dy > radius * radius) continue
                sum += scores[y * width + x]
                count += 1
            }
        }
        return if (count == 0) 0f else sum / count.toFloat()
    }

    private fun sampleAnnulusAverage(
        scores: FloatArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        innerRadius: Int,
        outerRadius: Int
    ): Float {
        var sum = 0f
        var count = 0
        val innerSq = innerRadius * innerRadius
        val outerSq = outerRadius * outerRadius
        for (y in centerY - outerRadius..centerY + outerRadius) {
            for (x in centerX - outerRadius..centerX + outerRadius) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dx = x - centerX
                val dy = y - centerY
                val distSq = dx * dx + dy * dy
                if (distSq < innerSq || distSq > outerSq) continue
                sum += scores[y * width + x]
                count += 1
            }
        }
        return if (count == 0) 0f else sum / count.toFloat()
    }

    private fun sampleAnnulusRatio(
        scores: FloatArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        innerRadius: Int,
        outerRadius: Int,
        threshold: Float
    ): Float {
        var hit = 0
        var count = 0
        val innerSq = innerRadius * innerRadius
        val outerSq = outerRadius * outerRadius
        for (y in centerY - outerRadius..centerY + outerRadius) {
            for (x in centerX - outerRadius..centerX + outerRadius) {
                if (x !in 0 until width || y !in 0 until height) continue
                val dx = x - centerX
                val dy = y - centerY
                val distSq = dx * dx + dy * dy
                if (distSq < innerSq || distSq > outerSq) continue
                count += 1
                if (scores[y * width + x] >= threshold) {
                    hit += 1
                }
            }
        }
        return if (count == 0) 0f else hit.toFloat() / count.toFloat()
    }

    private fun sampleNearest(
        scores: FloatArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ): Float {
        val safeX = x.toInt().coerceIn(0, width - 1)
        val safeY = y.toInt().coerceIn(0, height - 1)
        return scores[safeY * width + safeX]
    }

    private fun loadTemplateBitmap(fileName: String): Bitmap? {
        val cacheKey = TemplateOverrideStore.cacheKey(accessibilityService, fileName)
        templateCache.get(cacheKey)?.let {
            BitmapFactory.decodeByteArray(it, 0, it.size)?.let { bitmap ->
                return bitmap
            }
            templateCache.remove(cacheKey)
        }
        return try {
            val bytes = TemplateOverrideStore.overrideFile(accessibilityService, fileName)
                .takeIf { it.exists() }
                ?.readBytes()
                ?: accessibilityService.assets.open(fileName).use { it.readBytes() }
            templateCache.put(cacheKey, bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap == null) {
                RunLogger.e("模板解码失败 $fileName")
                templateCache.remove(cacheKey)
            }
            bitmap
        } catch (t: Throwable) {
            RunLogger.e("模板打开失败 $fileName", t)
            null
        }
    }

    private inline fun <T> Mat.use(block: (Mat) -> T): T {
        try {
            return block(this)
        } finally {
            release()
        }
    }

    private fun matchTemplate(
        screenBitmap: Bitmap,
        templateBitmap: Bitmap,
        threshold: Float,
        logLabel: String,
        templateName: String
    ): PointF? {
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
                        RunLogger.i(
                            "$logLabel $templateName 匹配分数=${"%.4f".format(Locale.US, mmLoc.maxVal)} " +
                                "阈值=${"%.2f".format(Locale.US, threshold)}"
                        )
                        if (mmLoc.maxVal >= threshold) {
                            PointF(
                                (mmLoc.maxLoc.x + templateBitmap.width / 2.0).toFloat(),
                                (mmLoc.maxLoc.y + templateBitmap.height / 2.0).toFloat()
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        } finally {
            if (ownsSourceBitmap) {
                sourceBitmap.recycle()
            }
        }
    }

    private data class VisionSearchRegion(
        val bitmap: Bitmap,
        val left: Int,
        val top: Int
    )

    private fun buildTopRightVisionSearchRegion(
        screenshot: Bitmap,
        roiWidth: Float,
        roiHeight: Float
    ): VisionSearchRegion? {
        val screenshotGameScale = minOf(screenshot.width / VISION_BASE_W, screenshot.height / VISION_BASE_H)
        val width = (roiWidth * screenshotGameScale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.width)
        val height = (roiHeight * screenshotGameScale).toInt().coerceAtLeast(1).coerceAtMost(screenshot.height)
        val left = (screenshot.width - width).coerceAtLeast(0)
        val top = 0
        if (width <= 0 || height <= 0) return null
        return VisionSearchRegion(
            bitmap = Bitmap.createBitmap(screenshot, left, top, width, height),
            left = left,
            top = top
        )
    }

    private fun buildVisionSearchRegion(
        screenshot: Bitmap,
        centerX: Float,
        centerY: Float,
        align: String,
        roiWidth: Float,
        roiHeight: Float
    ): VisionSearchRegion? {
        val (realCenterX, realCenterY) = calculateVisionCoordinate(centerX, centerY, align)
        val (displayWidth, displayHeight) = getRealScreenSize()
        val displayToScreenshotX = screenshot.width.toFloat() / displayWidth
        val displayToScreenshotY = screenshot.height.toFloat() / displayHeight
        val screenshotGameScale = minOf(screenshot.width / VISION_BASE_W, screenshot.height / VISION_BASE_H)

        val screenshotCenterX = realCenterX * displayToScreenshotX
        val screenshotCenterY = realCenterY * displayToScreenshotY
        val realRoiWidth = roiWidth * screenshotGameScale
        val realRoiHeight = roiHeight * screenshotGameScale

        val left = (screenshotCenterX - realRoiWidth / 2f).toInt().coerceIn(0, screenshot.width - 1)
        val top = (screenshotCenterY - realRoiHeight / 2f).toInt().coerceIn(0, screenshot.height - 1)
        val width = realRoiWidth.toInt().coerceAtMost(screenshot.width - left).coerceAtLeast(1)
        val height = realRoiHeight.toInt().coerceAtMost(screenshot.height - top).coerceAtLeast(1)
        if (width <= 0 || height <= 0) return null

        return VisionSearchRegion(
            bitmap = Bitmap.createBitmap(screenshot, left, top, width, height),
            left = left,
            top = top
        )
    }

    private fun findRedRegionMatch(
        bitmap: Bitmap,
        referenceAreaOverride: Float? = null,
        excludeNearWhite: Boolean = false,
        areaWeight: Float = 0.45f,
        fillWeight: Float = 0.25f,
        redWeight: Float = 0.30f
    ): RedRegionMatch? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val step = if (width >= 300 || height >= 300) 2 else 1
        val sampleWidth = (width + step - 1) / step
        val sampleHeight = (height + step - 1) / step
        val mask = BooleanArray(sampleWidth * sampleHeight)
        val queue = IntArray(mask.size)
        val redness = FloatArray(mask.size)

        var hasRed = false
        for (sy in 0 until sampleHeight) {
            val y = sy * step
            for (sx in 0 until sampleWidth) {
                val x = sx * step
                val index = sy * sampleWidth + sx
                val redScore = computeRednessScore(
                    color = bitmap.getPixel(x, y),
                    excludeNearWhite = excludeNearWhite
                )
                redness[index] = redScore
                if (redScore > 0f) {
                    mask[index] = true
                    hasRed = true
                }
            }
        }
        if (!hasRed) return null

        var bestCount = 0
        var bestMinX = 0
        var bestMaxX = 0
        var bestMinY = 0
        var bestMaxY = 0
        var bestRednessSum = 0f
        val neighbors = intArrayOf(-1, 0, 1, 0, -1)

        for (sy in 0 until sampleHeight) {
            for (sx in 0 until sampleWidth) {
                val startIndex = sy * sampleWidth + sx
                if (!mask[startIndex]) continue

                var head = 0
                var tail = 0
                queue[tail++] = startIndex
                mask[startIndex] = false

                var count = 0
                var minX = sx
                var maxX = sx
                var minY = sy
                var maxY = sy
                var rednessSum = 0f

                while (head < tail) {
                    val index = queue[head++]
                    val cx = index % sampleWidth
                    val cy = index / sampleWidth
                    count += 1
                    rednessSum += redness[index]
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    for (n in 0 until 4) {
                        val nx = cx + neighbors[n]
                        val ny = cy + neighbors[n + 1]
                        if (nx !in 0 until sampleWidth || ny !in 0 until sampleHeight) continue
                        val nextIndex = ny * sampleWidth + nx
                        if (!mask[nextIndex]) continue
                        mask[nextIndex] = false
                        queue[tail++] = nextIndex
                    }
                }

                if (count > bestCount) {
                    bestCount = count
                    bestMinX = minX
                    bestMaxX = maxX
                    bestMinY = minY
                    bestMaxY = maxY
                    bestRednessSum = rednessSum
                }
            }
        }

        if (bestCount < 12) return null

        val bboxWidth = bestMaxX - bestMinX + 1
        val bboxHeight = bestMaxY - bestMinY + 1
        val bboxArea = (bboxWidth * bboxHeight).coerceAtLeast(1)
        val estimatedPixelArea = bestCount * step * step
        val referenceButtonArea = referenceAreaOverride ?: (300f * 50f)
        val areaRatio = (estimatedPixelArea / referenceButtonArea).coerceIn(0f, 1f)
        val fillRatio = bestCount.toFloat() / bboxArea.toFloat()
        val rednessScore = (bestRednessSum / bestCount.toFloat()).coerceIn(0f, 1f)
        val compactnessScore = ((fillRatio - 0.15f) / 0.85f).coerceIn(0f, 1f)
        val weightSum = (areaWeight + fillWeight + redWeight).coerceAtLeast(0.0001f)
        val confidence = (
            areaRatio * areaWeight +
                compactnessScore * fillWeight +
                rednessScore * redWeight
            ) / weightSum
        val centerX = ((bestMinX + bestMaxX + 1) * step / 2f).coerceIn(0f, (width - 1).toFloat())
        val centerY = ((bestMinY + bestMaxY + 1) * step / 2f).coerceIn(0f, (height - 1).toFloat())
        val finalConfidence = confidence.coerceIn(0f, 1f)
        return RedRegionMatch(
            center = PointF(centerX, centerY),
            confidence = finalConfidence,
            areaRatio = areaRatio,
            fillRatio = fillRatio,
            rednessScore = rednessScore
        )
    }

    private fun computeRednessScore(color: Int, excludeNearWhite: Boolean = false): Float {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        if (r < 95) return 0f
        if (r - g < 20 || r - b < 20) return 0f
        if (g > 185 || b > 185) return 0f

        val redLevel = ((r - 95f) / 160f).coerceIn(0f, 1f)
        val dominance = (((r - maxOf(g, b)) - 20f) / 180f).coerceIn(0f, 1f)
        val saturation = ((255f - (g + b) / 2f) / 255f).coerceIn(0f, 1f)
        return (redLevel * 0.45f + dominance * 0.4f + saturation * 0.15f).coerceIn(0f, 1f)
    }

    private fun computeCenterMeanSaturation(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 1 || height <= 1) return 255f

        val left = (width * 0.2f).toInt().coerceIn(0, width - 1)
        val top = (height * 0.2f).toInt().coerceIn(0, height - 1)
        val right = (width * 0.8f).toInt().coerceIn(left + 1, width)
        val bottom = (height * 0.8f).toInt().coerceIn(top + 1, height)
        val step = if ((right - left) * (bottom - top) >= 40000) 2 else 1
        val hsv = FloatArray(3)
        var sum = 0f
        var count = 0

        for (y in top until bottom step step) {
            for (x in left until right step step) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                sum += hsv[1] * 255f
                count += 1
            }
        }

        return if (count == 0) 255f else sum / count.toFloat()
    }

    private fun computeStarCoreScore(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (brightness < 0.72f) return 0f

        val brightnessScore = ((brightness - 0.72f) / 0.28f).coerceIn(0f, 1f)
        val whitenessScore = (1f - saturation).coerceIn(0f, 1f)
        val warmScore = when {
            saturation <= 0.12f -> 0.90f
            hue in 20f..68f -> 1f
            hue in 0f..20f -> 0.70f
            hue in 68f..90f -> 0.60f
            else -> 0f
        }
        if (warmScore <= 0f) return 0f

        return (
            brightnessScore * 0.45f +
                whitenessScore * 0.35f +
                warmScore * 0.20f
            ).coerceIn(0f, 1f)
    }

    private fun computeOrangePixelScore(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (hue < 20f || hue > 62f) return 0f
        if (saturation < 0.35f || brightness < 0.45f) return 0f

        val hueScore = (1f - kotlin.math.abs(hue - 41f) / 21f).coerceIn(0f, 1f)
        val saturationScore = ((saturation - 0.35f) / 0.65f).coerceIn(0f, 1f)
        val brightnessScore = ((brightness - 0.45f) / 0.55f).coerceIn(0f, 1f)
        return (
            hueScore * 0.50f +
                saturationScore * 0.25f +
                brightnessScore * 0.25f
            ).coerceIn(0f, 1f)
    }

    private fun computeStarHighlightScore(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val brightness = hsv[2]
        if (brightness < 0.58f) return 0f

        val brightnessScore = ((brightness - 0.58f) / 0.42f).coerceIn(0f, 1f)
        val hueScore = when {
            saturation <= 0.18f -> 0.90f
            hue in 15f..80f -> 1f
            hue in 0f..15f -> 0.70f
            hue in 80f..100f -> 0.55f
            else -> 0f
        }
        if (hueScore <= 0f) return 0f

        val saturationScore = when {
            saturation <= 0.18f -> 0.75f
            else -> ((saturation - 0.18f) / 0.82f).coerceIn(0.45f, 1f)
        }

        return (
            brightnessScore * 0.50f +
                hueScore * 0.30f +
                saturationScore * 0.20f
            ).coerceIn(0f, 1f)
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

    private fun calculateVisionCoordinate(baseX: Float, baseY: Float, align: String): Pair<Float, Float> {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val gameScale = minOf(screenWidth / VISION_BASE_W, screenHeight / VISION_BASE_H)
        val gameWidth = VISION_BASE_W * gameScale
        val gameHeight = VISION_BASE_H * gameScale
        val offsetX = (screenWidth - gameWidth) / 2f
        val offsetY = (screenHeight - gameHeight) / 2f
        val statusBarHeight = getRawStatusBarHeight() / 2f
        val realX = offsetX + (baseX * gameScale)
        val realY = when (align.lowercase()) {
            "absolute" -> baseY * (screenHeight / VISION_BASE_H)
            "top" -> statusBarHeight + (baseY * gameScale)
            "bottom" -> screenHeight - ((VISION_BASE_H - baseY) * gameScale)
            else -> offsetY + (baseY * gameScale)
        }
        return Pair(realX, realY)
    }

    private fun getRawStatusBarHeight(): Int {
        val id = accessibilityService.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) accessibilityService.resources.getDimensionPixelSize(id) else 40
    }

    private fun playRecoveryFeedback() {
        runCatching {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            serviceScope.launch {
                delay(250)
                toneGenerator.release()
            }
        }.onFailure {
            RunLogger.e("提示音播放失败", it)
        }

        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = accessibilityService.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                accessibilityService.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(180)
            }
        }.onFailure {
            RunLogger.e("振动触发失败", it)
        }
    }

    private fun restartFromFirstTurn(restartMessage: String = "全灭恢复，重新从第1回合开始") {
        if (!isRunning) return
        delayJob?.cancel()
        actionQueue.clear()
        currentExecutingRowIndex = -1
        lastTurnPauseTriggered = -1
        followData.forEach { it.isExecuting = false }
        onRowUpdated(-1)
        showToast(restartMessage, false)
        processRowAndExecute(0)
    }

    private fun startSafeDelay(delayTime: Long, actionStr: String, isWarning: Boolean, block: () -> Unit) {
        delayJob?.cancel()
        delayJob = serviceScope.launch {
            var remaining = delayTime
            while (remaining > 0) {
                if (!isRunning) return@launch

                val timeStr = String.format(Locale.US, "%.1fs", remaining / 1000f)
                onHudUpdated("$timeStr $actionStr", isWarning)

                val step = when {
                    remaining >= 600L -> 500L
                    else -> minOf(remaining, 200L)
                }
                delay(step)
                remaining -= step
            }
            if (isRunning) block()
        }
    }

    private fun getRandomPointInCircle(centerX: Float, centerY: Float, radius: Float): PointF {
        val r = radius * Math.sqrt(random.nextDouble()).toFloat()
        val theta = random.nextDouble() * 2 * Math.PI
        val dx = r * Math.cos(theta).toFloat()
        val dy = r * Math.sin(theta).toFloat()
        return PointF(centerX + dx, centerY + dy)
    }

    private fun addRandomOffset(baseValue: Float, range: Float): Float {
        return baseValue + (random.nextFloat() - 0.5f) * 2 * range
    }
}

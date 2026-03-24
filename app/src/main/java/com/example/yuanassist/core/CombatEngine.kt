// 檔案路徑：yuanassist/core/CombatEngine.kt
package com.example.yuanassist.core

import android.graphics.PointF
import com.example.yuanassist.model.ActionItem
import com.example.yuanassist.model.InstructionType
import com.example.yuanassist.model.ScriptInstruction
import com.example.yuanassist.model.TurnData
import com.example.yuanassist.utils.AppConfig
import com.example.yuanassist.utils.GameConstants
import com.example.yuanassist.utils.RunLogger
import kotlinx.coroutines.*
import java.util.LinkedList
import java.util.Queue
import java.util.Random

import java.util.concurrent.CopyOnWriteArrayList

class CombatEngine(
    private val serviceScope: CoroutineScope,
    private val coordinateManager: CoordinateManager,
    private val gestureDispatcher: GestureDispatcher,
    private val getConfig: () -> AppConfig,
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
    companion object {
        private val REGEX_PARSE_NUM_ACTION = Regex("(\\d+)([A-Z↑↓圈]+)")
        private val REGEX_PARSE_PURE_ACTION = Regex("^([A-Z↑↓圈]+)$")
    }

    fun start() {
        val appConfig = getConfig()
        isPaused = false
        lastTurnPauseTriggered = -1
        delayJob?.cancel()

        if (followData.isEmpty()) {
            showToast("请先导入数据", false)
            stop()
            return
        }

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
            processRowAndExecute(startIndex)
        }
    }

    fun stop() {
        isRunning = false
        isPaused = false
        lastTurnPauseTriggered = -1
        delayJob?.cancel()
        followData.forEach { it.isExecuting = false }
        onRowUpdated(-1)
        onStateChanged()
    }

    fun togglePauseResume() {
        isPaused = !isPaused
        if (isPaused) {
            delayJob?.cancel()
            showToast("⏸️ 已暂停", false)
            onStateChanged()
            onHudUpdated("已暂停", true) // 🔴 暂停时固定显示
        } else {
            showToast("▶️ 继续运行", false)
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
        if (rowIndex >= followData.size || !isRunning) {
            showToast("跟打结束", false)
            stop()
            return
        }

        currentExecutingRowIndex = rowIndex
        val currentTurnNum = followData[rowIndex].turnNumber
        RunLogger.i("▶ 开始执行第 $currentTurnNum 回合...")

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

            checkAndSwitchTarget(currentTurnNum, 0) {
                executeActionQueue { processRowAndExecute(rowIndex + 1) }
            }
        } catch (e: Exception) {
            turnData.hasConflict = true
            onRowUpdated(rowIndex)
            showToast("第 ${turnData.turnNumber} 回合指令冲突！停止运行", true)
            stop()
        }
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
                startSafeDelay(200, getNextTurnFirstAction(), false) { onComplete() }
                return
            }

            RunLogger.i("等待敌方回合结束 (${appConfig.waitTurn / 1000}秒)...")
            waitForEnemyTurn(onComplete)
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

        val turnDelayIns =
            instructionList.find { it.turn == currentTurn && it.step == 0 && it.type == InstructionType.DELAY_ADD }
        if (turnDelayIns != null) delay += turnDelayIns.value

        val stepDelayIns =
            instructionList.find { it.turn == currentTurn && it.step == currentStep && it.type == InstructionType.DELAY_ADD }
        if (stepDelayIns != null) {
            delay += stepDelayIns.value
            showToast("指令生效：延时增加 ${stepDelayIns.value}ms", false)
        }

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

    private fun waitForEnemyTurn(onNextTurn: () -> Unit) {
        if (!isRunning) return
        val appConfig = getConfig()
        val speedMultiplier = if (appConfig.gameSpeed == 2) 1.5 else 1.0
        val actualWaitTurn = (appConfig.waitTurn * speedMultiplier).toLong()

        val nextStepStr = getNextTurnFirstAction()
        showToast("行动结束，等待 ${actualWaitTurn / 1000} 秒...", false)

        startSafeDelay(actualWaitTurn, nextStepStr, false) {
            if (isRunning) onNextTurn()
        }
    }

    private fun checkAndSwitchTarget(turn: Int, step: Int, onNext: () -> Unit) {
        val task =
            instructionList.find { it.turn == turn && it.step == step && it.type == InstructionType.TARGET_SWITCH }
        if (task != null && task.value > 0) {
            onHudUpdated("切目标 x${task.value}", true)
            performBlueClicks(task.value.toInt()) {
                onNext()
            }
        } else {
            onNext()
        }
    }

    private fun performBlueClicks(count: Int, onComplete: () -> Unit) {
        if (count <= 0 || !isRunning) {
            onComplete()
            return
        }
        val x = coordinateManager.getTargetCoordinates(
            GameConstants.DESIGN_TARGET_X,
            GameConstants.DESIGN_TARGET_Y_TOP
        ).x
        val y = coordinateManager.getTargetCoordinates(
            GameConstants.DESIGN_TARGET_X,
            GameConstants.DESIGN_TARGET_Y_TOP
        ).y

        serviceScope.launch {
            for (i in 0 until count) {
                if (!isRunning) break
                gestureDispatcher.performActionDirect(x, y, x, y, true)
                delay(500)
            }
            if (isRunning) onComplete()
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

    private fun startSafeDelay(delayTime: Long, actionStr: String, isWarning: Boolean, block: () -> Unit) {
        delayJob?.cancel() // 修复隐患4：先取消可能正在运行的旧倒计时，防止多重抢占和错乱
        delayJob = serviceScope.launch {
            var remaining = delayTime
            while (remaining > 0) {
                if (!isRunning) return@launch

                // 格式化为保留一位小数的秒数，如 "1.5s"
                val timeStr = String.format(java.util.Locale.US, "%.1fs", remaining / 1000f)
                // 拼接时间与动作，中间加空格
                onHudUpdated("$timeStr $actionStr", isWarning)

                // 优化2：动态分配步长，大于 0.6s 降低刷新率(500ms)，小于等于 0.6s 加快刷新(200ms)
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

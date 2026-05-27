// 檔案路徑：yuanassist/core/RecordEngine.kt
package com.example.yuanassist.core

import android.text.SpannableStringBuilder
import android.view.MotionEvent
import com.example.yuanassist.model.HistoryAction
import com.example.yuanassist.model.InstructionType
import com.example.yuanassist.model.ScriptInstruction
import com.example.yuanassist.model.TurnData
import com.example.yuanassist.utils.AppConfig
import kotlin.math.abs

class RecordEngine(
    private val coordinateManager: CoordinateManager,
    private val gestureDispatcher: GestureDispatcher,
    private val getConfig: () -> AppConfig, // 動態獲取最新配置
    private val onDataUpdated: (turnIndex: Int) -> Unit, // 通知 UI 更新特定行
    private val onTurnInserted: (turnIndex: Int) -> Unit, // 通知 UI 插入新行
    private val onTurnRemoved: (turnIndex: Int) -> Unit, // 通知 UI 刪除行
    private val onActionRecorded: (actionText: String) -> Unit // 通知 UI 更新 MiniWindow 文字
) {
    val recordData = ArrayList<TurnData>()
    private val undoStack = ArrayList<HistoryAction>()
    private val redoStack = ArrayList<HistoryAction>()

    private var touchStartX = 0f
    private var touchStartY = 0f

    init {
        // 初始化第一回合
        recordData.add(TurnData(1, currentStep = 1))
    }

    fun recordCircleAction(
        charIndex: Int,
        clickX: Float,
        clickY: Float,
        isSimulating: Boolean,
        isFollowMode: Boolean,
        onActionDone: (() -> Unit)? = null
    ): Boolean {
        if (isSimulating || isFollowMode) return false

        val safeCharIndex = charIndex.coerceIn(0, 4)
        val appConfig = getConfig()

        dispatchRecordedAction(
            charIndex = safeCharIndex,
            actionSymbol = "圈",
            actionType = "click",
            startX = clickX,
            startY = clickY,
            endX = clickX,
            endY = clickY,
            recordDelay = appConfig.recordDelay,
            durationMs = appConfig.recordClickDurationMs,
            onActionDone = onActionDone
        )
        return true
    }

    fun recordTargetSwitchInstruction(type: InstructionType): Boolean {
        if (recordData.isEmpty()) return false

        val turnIndex = recordData.lastIndex
        val currentTurn = recordData[turnIndex]
        val step = (currentTurn.currentStep - 1).coerceAtLeast(0)
        val previousInstructions = cloneInstructions(currentTurn.instructions)
        val previousRemark = currentTurn.remark
        val newInstructions = cloneInstructions(currentTurn.instructions).toMutableList().apply {
            add(
                ScriptInstruction(
                    turn = currentTurn.turnNumber,
                    step = step,
                    type = type,
                    value = 1
                ).normalized()
            )
        }
        val newRemark = appendRemark(
            previousRemark,
            buildTargetSwitchRemark(currentTurn, step, type)
        )

        recordInstructionChange(
            turnIndex = turnIndex,
            previousInstructions = previousInstructions,
            newInstructions = newInstructions,
            previousRemark = previousRemark,
            newRemark = newRemark
        )

        currentTurn.instructions.clear()
        currentTurn.instructions.addAll(newInstructions)
        currentTurn.remark = newRemark
        onDataUpdated(turnIndex)
        onActionRecorded(
            when (type) {
                InstructionType.TARGET_SWITCH_LEFT -> "左切目标"
                InstructionType.TARGET_SWITCH_RIGHT, InstructionType.TARGET_SWITCH -> "右切目标"
                else -> type.description
            }
        )
        return true
    }

    fun handleTouch(event: MotionEvent, isSimulating: Boolean, isFollowMode: Boolean) {
        if (isSimulating || isFollowMode) return
        val appConfig = getConfig()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.rawX
                touchStartY = event.rawY
            }

            MotionEvent.ACTION_UP -> {
                val endX = event.rawX
                val endY = event.rawY
                val validAreaTop =
                    coordinateManager.screenHeight * (1 - (appConfig.inputHeightRatio / 100f))

                if (touchStartY >= validAreaTop) {
                    val diffX = endX - touchStartX
                    val diffY = endY - touchStartY
                    var actionSymbol = "A"
                    var actionType = "click"

                    if (abs(diffX) < 20 && abs(diffY) < 20) {
                        actionSymbol = "A"
                        actionType = "click"
                    } else if (diffY < -appConfig.swipeThreshold) {
                        actionSymbol = "↑"
                        actionType = "swipe_up"
                    } else if (diffY > appConfig.swipeThreshold) {
                        actionSymbol = "↓"
                        actionType = "swipe_down"
                    }

                    // 計算列號 (防越界)
                    val relativeX = touchStartX - coordinateManager.gameOffsetX
                    var charIndex = (relativeX / coordinateManager.colWidth).toInt()
                    charIndex = charIndex.coerceIn(0, 4)

                    dispatchRecordedAction(
                        charIndex = charIndex,
                        actionSymbol = actionSymbol,
                        actionType = actionType,
                        startX = touchStartX,
                        startY = touchStartY,
                        endX = if (actionType == "swipe_up" || actionType == "swipe_down") {
                            touchStartX
                        } else {
                            endX
                        },
                        endY = when (actionType) {
                            "swipe_up" -> touchStartY - (appConfig.recordSwipeDistance * coordinateManager.gameScale)
                            "swipe_down" -> touchStartY + (appConfig.recordSwipeDistance * coordinateManager.gameScale)
                            else -> endY
                        },
                        recordDelay = appConfig.recordDelay,
                        durationMs = if (actionType == "click") {
                            appConfig.recordClickDurationMs
                        } else {
                            appConfig.recordSwipeDurationMs
                        }
                    )
                } else {
                    // 區域外點擊 (無 UI 任務)
                    gestureDispatcher.performActionPenetrate(
                        touchStartX, touchStartY, true, 0f, 0f,
                        appConfig.recordDelay, appConfig.recordClickDurationMs, null
                    )
                }
            }
        }
    }

    fun addNewTurn() {
        val newTurnNum = recordData.size + 1
        recordData.add(TurnData(newTurnNum, currentStep = 1))
        undoStack.add(HistoryAction(1, recordData.size - 1))
        redoStack.clear()
        onTurnInserted(recordData.size - 1)
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val action = undoStack.removeAt(undoStack.size - 1)

        if (action.type == 1) {
            if (recordData.isNotEmpty()) {
                val lastIndex = recordData.size - 1
                recordData.removeAt(lastIndex)
                redoStack.add(action)
                onTurnRemoved(lastIndex)
                return true // 撤回了新建回合
            }
        } else if (action.type == 0) {
            if (action.turnIndex < recordData.size) {
                val turnData = recordData[action.turnIndex]
                turnData.characterActions[action.charIndex] = action.previousText
                turnData.currentStep = action.previousStep
                redoStack.add(action)
                onDataUpdated(action.turnIndex)
                return true // 撤回了動作
            }
        } else if (action.type == 2) {
            if (action.turnIndex < recordData.size) {
                val turnData = recordData[action.turnIndex]
                turnData.instructions.clear()
                turnData.instructions.addAll(cloneInstructions(action.previousInstructions))
                turnData.remark = action.previousRemark
                redoStack.add(action)
                onDataUpdated(action.turnIndex)
                return true // 撤回了指令/备注
            }
        }
        return false
    }

    fun clearData() {
        recordData.clear()
        recordData.add(TurnData(1, currentStep = 1))
        undoStack.clear()
        redoStack.clear()
        onDataUpdated(-1) // -1 代表全部刷新
    }

    fun recordAction(
        turnIndex: Int,
        charIndex: Int,
        oldText: CharSequence,
        newText: CharSequence,
        oldStep: Int,
        newStep: Int
    ) {
        val action = HistoryAction(0, turnIndex, charIndex, oldText, newText, oldStep, newStep)
        undoStack.add(action)
        redoStack.clear()
    }

    private fun recordInstructionChange(
        turnIndex: Int,
        previousInstructions: List<ScriptInstruction>,
        newInstructions: List<ScriptInstruction>,
        previousRemark: String,
        newRemark: String
    ) {
        undoStack.add(
            HistoryAction(
                type = 2,
                turnIndex = turnIndex,
                previousRemark = previousRemark,
                newRemark = newRemark,
                previousInstructions = cloneInstructions(previousInstructions),
                newInstructions = cloneInstructions(newInstructions)
            )
        )
        redoStack.clear()
    }

    private fun dispatchRecordedAction(
        charIndex: Int,
        actionSymbol: String,
        actionType: String,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        recordDelay: Long,
        durationMs: Long,
        onActionDone: (() -> Unit)? = null
    ) {
        val uiTask = buildRecordUiTask(charIndex, actionSymbol, onActionDone)
        gestureDispatcher.performActionPenetrate(
            startX,
            startY,
            actionType == "click",
            endX,
            endY,
            recordDelay,
            durationMs,
            uiTask
        )
    }

    private fun buildRecordUiTask(
        charIndex: Int,
        actionSymbol: String,
        onActionDone: (() -> Unit)? = null
    ): () -> Unit = {
        if (recordData.isNotEmpty()) {
            val turnIndex = recordData.size - 1
            val currentTurn = recordData.last()
            val oldText = currentTurn.characterActions[charIndex]
            val oldStep = currentTurn.currentStep
            val newStep = oldStep + 1

            val newText = SpannableStringBuilder(oldText).append("$oldStep$actionSymbol")
            recordAction(turnIndex, charIndex, oldText, newText, oldStep, newStep)

            currentTurn.characterActions[charIndex] = newText
            currentTurn.currentStep = newStep

            onDataUpdated(turnIndex)
            onActionRecorded("$oldStep$actionSymbol")
        }
        onActionDone?.invoke()
    }

    private fun buildTargetSwitchRemark(
        turnData: TurnData,
        step: Int,
        type: InstructionType
    ): String {
        val direction = when (type) {
            InstructionType.TARGET_SWITCH_LEFT -> "左切目标"
            InstructionType.TARGET_SWITCH_RIGHT, InstructionType.TARGET_SWITCH -> "右切目标"
            else -> type.description
        }
        val anchor = if (step == 0) {
            "在1x前"
        } else {
            "在${findStepActionLabel(turnData, step) ?: "${step}x"}后"
        }
        return anchor + direction
    }

    private fun findStepActionLabel(turnData: TurnData, step: Int): String? {
        val pattern = Regex("""(\d+)([A-Z↑↓圈])""")
        for (actionText in turnData.characterActions) {
            val text = actionText.toString()
            pattern.findAll(text).forEach { match ->
                val currentStep = match.groupValues[1].toIntOrNull() ?: return@forEach
                if (currentStep == step) {
                    return match.value
                }
            }
        }
        return null
    }

    private fun appendRemark(original: String, addition: String): String {
        if (addition.isBlank()) return original
        if (original.isBlank()) return addition
        if (original.contains(addition)) return original
        return "$original；$addition"
    }

    private fun cloneInstructions(instructions: List<ScriptInstruction>): List<ScriptInstruction> {
        return instructions.map { it.copy() }
    }
}

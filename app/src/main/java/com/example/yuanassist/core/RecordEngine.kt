// 檔案路徑：yuanassist/core/RecordEngine.kt
package com.example.yuanassist.core

import android.text.SpannableStringBuilder
import android.view.MotionEvent
import com.example.yuanassist.model.HistoryAction
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

                    val uiTask = {
                        if (recordData.isNotEmpty()) {
                            val turnIndex = recordData.size - 1
                            val currentTurn = recordData.last()
                            val oldText = currentTurn.characterActions[charIndex]
                            val oldStep = currentTurn.currentStep
                            val newStep = oldStep + 1

                            val newText =
                                SpannableStringBuilder(oldText).append("$oldStep$actionSymbol")
                            recordAction(turnIndex, charIndex, oldText, newText, oldStep, newStep)

                            currentTurn.characterActions[charIndex] = newText
                            currentTurn.currentStep = newStep

                            onDataUpdated(turnIndex)
                            onActionRecorded("$oldStep$actionSymbol")
                        }
                    }

                    // 執行穿透 (帶上 UI 任務)
                    gestureDispatcher.performActionPenetrate(
                        touchStartX, touchStartY, actionType == "click", endX, endY,
                        appConfig.recordDelay, uiTask
                    )
                } else {
                    // 區域外點擊 (無 UI 任務)
                    gestureDispatcher.performActionPenetrate(
                        touchStartX, touchStartY, true, 0f, 0f,
                        appConfig.recordDelay, null
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
        } else {
            if (action.turnIndex < recordData.size) {
                val turnData = recordData[action.turnIndex]
                turnData.characterActions[action.charIndex] = action.previousText
                turnData.currentStep = action.previousStep
                redoStack.add(action)
                onDataUpdated(action.turnIndex)
                return true // 撤回了動作
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
}
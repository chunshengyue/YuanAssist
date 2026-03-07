package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.example.yuanassist.ui.FloatingUIManager

class GestureDispatcher(
    private val service: AccessibilityService,
    private val uiManager: FloatingUIManager,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private var isSimulating = false

    // 路线 A：直接执行（跟打模式）
    fun performActionDirect(x1: Float, y1: Float, x2: Float, y2: Float, isClick: Boolean) {
        val duration = if (isClick) 50L else 300L
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    // 路线 B：穿透执行（录制模式）
    fun performActionPenetrate(
        x: Float, y: Float,
        isClick: Boolean,
        endX: Float = 0f, endY: Float = 0f,
        recordDelay: Long,
        onActionDone: (() -> Unit)? = null
    ) {
        if (isSimulating) return
        isSimulating = true

        val inputView = uiManager.inputView ?: return
        val params = inputView.layoutParams as? WindowManager.LayoutParams ?: return

        val originalFlags = params.flags
        val originalWidth = params.width
        val originalHeight = params.height

        // 1. 隐藏输入层
        params.flags = originalFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.width = 1
        params.height = 1
        uiManager.windowManager.updateViewLayout(inputView, params)

        handler.postDelayed({
            val path = Path().apply {
                moveTo(x, y)
                if (isClick) lineTo(x, y) else lineTo(endX, endY)
            }
            val duration = if (isClick) 50L else 300L

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()

            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    restoreWindow(params, originalFlags, originalWidth, originalHeight)
                    onActionDone?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    restoreWindow(params, originalFlags, originalWidth, originalHeight)
                }
            }, null)
        }, recordDelay)
    }

    private fun restoreWindow(params: WindowManager.LayoutParams, flags: Int, w: Int, h: Int) {
        handler.post {
            uiManager.inputView?.let {
                params.width = w
                params.height = h
                params.flags = flags
                uiManager.windowManager.updateViewLayout(it, params)
            }
            isSimulating = false
        }
    }
}
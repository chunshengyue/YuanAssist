package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import com.example.yuanassist.ui.FloatingUIManager

class GestureDispatcher(
    private val service: AccessibilityService,
    private val uiManager: FloatingUIManager,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private var isSimulating = false

    /** 手势回调超时时间（毫秒），超时后强制恢复窗口状态 */
    private val gestureTimeoutMs = 3000L

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

        val inputView = uiManager.inputView
        if (inputView == null) {
            isSimulating = false
            return
        }
        val params = inputView.layoutParams as? WindowManager.LayoutParams
        if (params == null) {
            isSimulating = false
            return
        }

        val originalFlags = params.flags
        val originalWidth = params.width
        val originalHeight = params.height

        // 1. 隐藏输入层
        params.flags = originalFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.width = 1
        params.height = 1
        try {
            uiManager.windowManager.updateViewLayout(inputView, params)
        } catch (e: IllegalArgumentException) {
            // inputView 已不在 WindowManager 中
            Log.e("GestureDispatcher", "隐藏输入层失败: ${e.message}")
            isSimulating = false
            return
        }

        handler.postDelayed({
            val path = Path().apply {
                moveTo(x, y)
                if (isClick) lineTo(x, y) else lineTo(endX, endY)
            }
            val duration = if (isClick) 50L else 300L

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()

            // 安全网：超时后强制恢复，防止系统不回调导致永久死锁
            val timeoutRunnable = Runnable {
                if (isSimulating) {
                    Log.e("GestureDispatcher", "手势回调超时，强制恢复窗口")
                    restoreWindow(params, originalFlags, originalWidth, originalHeight)
                }
            }
            handler.postDelayed(timeoutRunnable, gestureTimeoutMs)

            val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.removeCallbacks(timeoutRunnable)
                    restoreWindow(params, originalFlags, originalWidth, originalHeight)
                    onActionDone?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    handler.removeCallbacks(timeoutRunnable)
                    restoreWindow(params, originalFlags, originalWidth, originalHeight)
                }
            }, null)

            // dispatchGesture 返回 false 表示提交失败，回调不会触发
            if (!dispatched) {
                handler.removeCallbacks(timeoutRunnable)
                Log.e("GestureDispatcher", "dispatchGesture 提交失败，强制恢复窗口")
                restoreWindow(params, originalFlags, originalWidth, originalHeight)
            }
        }, recordDelay)
    }

    private fun restoreWindow(params: WindowManager.LayoutParams, flags: Int, w: Int, h: Int) {
        handler.post {
            try {
                uiManager.inputView?.let {
                    params.width = w
                    params.height = h
                    params.flags = flags
                    uiManager.windowManager.updateViewLayout(it, params)
                }
            } catch (e: IllegalArgumentException) {
                Log.e("GestureDispatcher", "恢复窗口失败: ${e.message}")
            }
            isSimulating = false
        }
    }
}
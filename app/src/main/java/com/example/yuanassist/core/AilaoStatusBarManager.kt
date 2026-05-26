package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.yuanassist.R
import com.example.yuanassist.utils.RunLogger
import kotlin.math.max
import kotlin.math.roundToInt

class AilaoStatusBarManager(private val service: AccessibilityService) {

    companion object {
        const val SCRIPT_FILE_NAME = "哀牢15min.json"
        private const val COOLDOWN_MS = 15 * 60 * 1000L
        private const val FLOAT_WINDOW_WIDTH_DP = 132
        private const val STATUS_BAR_WIDTH_DP = 132
        private const val STATUS_BAR_HEIGHT_DP = 50
        private const val STATUS_BAR_TOP_GAP_DP = 2
    }

    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var statusView: View? = null
    private var labelView: TextView? = null
    private var timeView: TextView? = null
    private var cooldownEndsAtMs: Long? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            refreshText()
            if (statusView != null) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    fun showRunning(anchorX: Int, anchorY: Int) {
        cooldownEndsAtMs = null
        ensureView(anchorX, anchorY)
        refreshText()
    }

    fun showCooldown(anchorX: Int, anchorY: Int, startedAtMs: Long = System.currentTimeMillis()) {
        cooldownEndsAtMs = startedAtMs + COOLDOWN_MS
        ensureView(anchorX, anchorY)
        refreshText()
    }

    fun updateAnchor(anchorX: Int, anchorY: Int) {
        val view = statusView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        applyAnchor(params, anchorX, anchorY)
        safelyUpdateViewLayout(view, params, "更新哀牢状态栏位置失败")
    }

    fun hide() {
        cooldownEndsAtMs = null
        removeView()
    }

    private fun ensureView(anchorX: Int, anchorY: Int) {
        val existing = statusView
        if (existing != null) {
            updateAnchor(anchorX, anchorY)
            return
        }

        val view = LayoutInflater.from(service).inflate(R.layout.layout_ailao_status_bar, null)
        labelView = view.findViewById(R.id.tv_ailao_status_label)
        timeView = view.findViewById(R.id.tv_ailao_status_time)
        val density = service.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (STATUS_BAR_WIDTH_DP * density).roundToInt(),
            (STATUS_BAR_HEIGHT_DP * density).roundToInt(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            applyAnchor(this, anchorX, anchorY)
        }
        statusView = view
        windowManager.addView(view, params)
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    private fun applyAnchor(params: WindowManager.LayoutParams, anchorX: Int, anchorY: Int) {
        val density = service.resources.displayMetrics.density
        val xOffset = ((STATUS_BAR_WIDTH_DP - FLOAT_WINDOW_WIDTH_DP) * density / 2f).roundToInt()
        params.x = max(0, anchorX - xOffset)
        params.y = anchorY + (50f * density).roundToInt() + (STATUS_BAR_TOP_GAP_DP * density).roundToInt()
    }

    private fun refreshText() {
        val endsAt = cooldownEndsAtMs
        if (endsAt == null) {
            labelView?.text = "运行中"
            labelView?.gravity = Gravity.CENTER
            labelView?.setPadding(0, 0, 0, 0)
            timeView?.visibility = View.GONE
            return
        }

        val remainingMs = (endsAt - System.currentTimeMillis()).coerceAtLeast(0L)
        labelView?.text = "等待中"
        labelView?.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        labelView?.setPadding((18f * service.resources.displayMetrics.density).roundToInt(), 0, 0, 0)
        timeView?.visibility = View.VISIBLE
        timeView?.text = formatRemaining(remainingMs)
        if (remainingMs == 0L) {
            cooldownEndsAtMs = null
            labelView?.text = "运行中"
            labelView?.gravity = Gravity.CENTER
            labelView?.setPadding(0, 0, 0, 0)
            timeView?.visibility = View.GONE
        }
    }

    private fun formatRemaining(ms: Long): String {
        val totalSeconds = ((ms + 999L) / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun removeView() {
        handler.removeCallbacks(tickRunnable)
        statusView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
            } finally {
                statusView = null
                labelView = null
                timeView = null
            }
        }
    }

    private fun safelyUpdateViewLayout(
        targetView: View,
        params: WindowManager.LayoutParams,
        logLabel: String
    ) {
        try {
            windowManager.updateViewLayout(targetView, params)
        } catch (t: Throwable) {
            RunLogger.e(logLabel, t)
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
}

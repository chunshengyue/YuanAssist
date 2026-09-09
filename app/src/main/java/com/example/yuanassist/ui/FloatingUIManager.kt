package com.example.yuanassist.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.ImageView
import com.example.yuanassist.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class FloatingUIManager(private val context: Context) {
    companion object {
        private const val FLOAT_WINDOW_EDGE_MARGIN_DP = 12
        private const val CONTROL_WINDOW_DEFAULT_WIDTH_DP = 308f
        private const val CONTROL_WINDOW_MIN_WIDTH_DP = 200f
        private const val CONTROL_WINDOW_MIN_HEIGHT_DP = 220f
    }

    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    var controlView: View? = null
    var minimizedView: View? = null
    var inputView: View? = null

    private var lastWindowX = 0
    private var lastWindowY = 100
    private var controlBaseWidthPx = 0
    private var controlBaseHeightPx = 0
    private var controlWindowScale = 1f

    @SuppressLint("ClickableViewAccessibility")
    fun createControlWindow(): View {
        if (controlView != null) return controlView!!
        removeMinimizedWindow()

        val params = WindowManager.LayoutParams(
            dp(CONTROL_WINDOW_DEFAULT_WIDTH_DP),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastWindowX; y = lastWindowY
        }

        val view = LayoutInflater.from(context).inflate(R.layout.layout_control_window, null)
        controlView = view

        val dragHandle = view.findViewById<View>(R.id.tv_drag_handle)
        dragHandle.setOnTouchListener(createDragListener(params, view))
        view.findViewById<View>(R.id.view_resize_handle)
            ?.setOnTouchListener(createResizeListener(params, view))

        windowManager.addView(view, params)
        view.post { syncControlWindowScale(view, params) }
        return view
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createMinimizedWindow(): View {
        if (minimizedView != null) return minimizedView!!

        val density = context.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, (50f * density + 0.5f).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastWindowX; y = lastWindowY
        }

        val view = LayoutInflater.from(context).inflate(R.layout.layout_minimized, null)
        minimizedView = view

        val dragHandle = view.findViewById<ImageView>(R.id.iv_mini_drag_handle)
        dragHandle.setOnTouchListener(createDragListener(params, view))

        windowManager.addView(view, params)
        return view
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createInputWindow(onTouch: (MotionEvent) -> Unit): View {
        if (inputView != null) return inputView!!

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val view = LayoutInflater.from(context).inflate(R.layout.layout_input_area, null)
        view.setOnTouchListener { _, event -> onTouch(event); true }
        inputView = view

        windowManager.addView(view, params)
        return view
    }

    fun removeControlWindow() {
        controlView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
            controlView = null
        }
    }

    fun removeMinimizedWindow() {
        minimizedView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
            minimizedView = null
        }
    }

    fun removeInputWindow() {
        inputView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
            inputView = null
        }
    }

    private fun syncControlWindowScale(targetView: View, params: WindowManager.LayoutParams) {
        val panel = targetView.findViewById<View>(R.id.combat_control_panel) ?: return
        ensureControlBaseSize(panel)
        applyControlWindowScale(targetView, params, controlWindowScale)
    }

    private fun ensureControlBaseSize(panel: View) {
        if (controlBaseWidthPx <= 0 && panel.width > 0) {
            controlBaseWidthPx = panel.width
        }
        if (controlBaseHeightPx <= 0 && panel.height > 0) {
            controlBaseHeightPx = panel.height
        }
    }

    private fun applyControlWindowScale(
        targetView: View,
        params: WindowManager.LayoutParams,
        requestedScale: Float
    ) {
        val panel = targetView.findViewById<View>(R.id.combat_control_panel) ?: return
        ensureControlBaseSize(panel)
        val baseWidth = controlBaseWidthPx.takeIf { it > 0 } ?: panel.width
        val baseHeight = controlBaseHeightPx.takeIf { it > 0 } ?: panel.height
        if (baseWidth <= 0 || baseHeight <= 0) return

        val density = context.resources.displayMetrics.density
        val minWidthPx = (CONTROL_WINDOW_MIN_WIDTH_DP * density + 0.5f).toInt()
        val minHeightPx = (CONTROL_WINDOW_MIN_HEIGHT_DP * density + 0.5f).toInt()
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val marginPx = (FLOAT_WINDOW_EDGE_MARGIN_DP * density).roundToInt()
        val maxWidthPx = (screenWidth - params.x - marginPx).coerceAtLeast(minWidthPx)
        val maxHeightPx = (screenHeight - params.y - marginPx).coerceAtLeast(minHeightPx)

        val minScale = max(
            minWidthPx.toFloat() / baseWidth.toFloat(),
            minHeightPx.toFloat() / baseHeight.toFloat()
        )
        val maxScale = max(
            min(
                maxWidthPx.toFloat() / baseWidth.toFloat(),
                maxHeightPx.toFloat() / baseHeight.toFloat()
            ),
            minScale
        )
        val finalScale = requestedScale.coerceIn(minScale, maxScale)
        val finalWidth = (baseWidth * finalScale).roundToInt().coerceAtLeast(minWidthPx)
        val finalHeight = (baseHeight * finalScale).roundToInt().coerceAtLeast(minHeightPx)

        panel.pivotX = 0f
        panel.pivotY = 0f
        panel.scaleX = finalScale
        panel.scaleY = finalScale

        params.width = if (finalScale < 1f) baseWidth else finalWidth
        params.height = if (finalScale < 1f) baseHeight else finalHeight
        controlWindowScale = finalScale
        windowManager.updateViewLayout(targetView, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createResizeListener(
        params: WindowManager.LayoutParams,
        targetView: View
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var initialWindowWidth = 0
            private var initialWindowHeight = 0
            private var initialScale = 1f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ensureControlBaseSize(targetView.findViewById(R.id.combat_control_panel) ?: return false)
                        initialScale = controlWindowScale
                        initialWindowWidth = (controlBaseWidthPx * initialScale).roundToInt()
                        initialWindowHeight = (controlBaseHeightPx * initialScale).roundToInt()
                        if (initialWindowWidth <= 0 || initialWindowHeight <= 0) return false
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val panel = targetView.findViewById<View>(R.id.combat_control_panel) ?: return true
                        val baseWidth = controlBaseWidthPx.takeIf { it > 0 } ?: panel.width
                        val baseHeight = controlBaseHeightPx.takeIf { it > 0 } ?: panel.height
                        if (baseWidth <= 0 || baseHeight <= 0) return true

                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        val proposedWidth = (initialWindowWidth + deltaX).coerceAtLeast(1f)
                        val proposedHeight = (initialWindowHeight + deltaY).coerceAtLeast(1f)
                        val scaleFromWidth = proposedWidth / baseWidth.toFloat()
                        val scaleFromHeight = proposedHeight / baseHeight.toFloat()
                        val requestedScale = if (abs(scaleFromWidth - initialScale) >= abs(scaleFromHeight - initialScale)) {
                            scaleFromWidth
                        } else {
                            scaleFromHeight
                        }

                        applyControlWindowScale(targetView, params, requestedScale)
                        return true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        return true
                    }
                }
                return false
            }
        }
    }

    private fun dp(value: Float): Int {
        val density = context.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }

    fun moveMinimizedWindowNearTopSafely() {
        val targetView = minimizedView ?: return
        targetView.post {
            val params = targetView.layoutParams as? WindowManager.LayoutParams ?: return@post
            val density = context.resources.displayMetrics.density
            val marginPx = (FLOAT_WINDOW_EDGE_MARGIN_DP * density).roundToInt()
            val metrics = context.resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val viewWidth = targetView.width.takeIf { it > 0 } ?: targetView.measuredWidth
            val viewHeight = targetView.height.takeIf { it > 0 } ?: targetView.measuredHeight
            val maxX = (screenWidth - viewWidth - marginPx).coerceAtLeast(marginPx)
            val maxY = (screenHeight - viewHeight - marginPx).coerceAtLeast(0)

            params.x = params.x.coerceIn(marginPx, maxX)
            params.y = 0.coerceIn(0, maxY)
            lastWindowX = params.x
            lastWindowY = params.y
            windowManager.updateViewLayout(targetView, params)
        }
    }

    // 統一處理拖曳邏輯
    private fun createDragListener(
        params: WindowManager.LayoutParams,
        targetView: View
    ): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var initialX = 0;
            private var initialY = 0
            private var initialTouchX = 0f;
            private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(targetView, params)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        lastWindowX = params.x; lastWindowY = params.y
                        if (abs(event.rawX - initialTouchX) < 10 && abs(event.rawY - initialTouchY) < 10) v.performClick()
                        return true
                    }
                }
                return false
            }
        }
    }
}

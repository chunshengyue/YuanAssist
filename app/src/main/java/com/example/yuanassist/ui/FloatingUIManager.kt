package com.example.yuanassist.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.ImageView
import com.example.yuanassist.R
import kotlin.math.abs

class FloatingUIManager(private val context: Context) {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    var controlView: View? = null
    var minimizedView: View? = null
    var inputView: View? = null

    private var lastWindowX = 0
    private var lastWindowY = 100

    @SuppressLint("ClickableViewAccessibility")
    fun createControlWindow(): View {
        if (controlView != null) return controlView!!
        removeMinimizedWindow()

        val density = context.resources.displayMetrics.density
        val widthPx = (308f * density + 0.5f).toInt()

        val params = WindowManager.LayoutParams(
            widthPx, WindowManager.LayoutParams.WRAP_CONTENT,
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

        windowManager.addView(view, params)
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
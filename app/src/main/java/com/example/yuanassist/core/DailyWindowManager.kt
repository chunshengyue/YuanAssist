package com.example.yuanassist.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.R
import com.google.gson.Gson

class DailyWindowManager(private val service: AccessibilityService) {

    private var engine = AutoTaskEngine(service) // 引入公共引擎
    private var windowManager: WindowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private var currentTaskPlan: DailyTaskPlan? = null

    fun hideWindow() {
        engine.stop() // ✅ 在这里停止引擎
        floatView?.let {
            windowManager.removeView(it)
            floatView = null
        }
    }

    // 2. 把你要测试的 JSON 变成一个死字符串
    private val TEST_JSON = """
{
  "start_task_id": 1,
  "tasks": [
    {
      "id": 1, "action": "CLICK", "delay": 1500,
      "params": { "x": 0.3893, "y": 0.4889, "align": "center" },
      "on_success": 2, "on_fail": -1
    },
    {
      "id": 2, "action": "MATCH_TEMPLATE", "delay": 1500,
      "params": { 
        "template_name": "yuanbao_opencv.png",
        "threshold": 0.75,
        "roi": { 
          "x": 0.0, 
          "y": 0.0, 
          "w": 1.0, 
          "h": 0.5, 
          "align": "absolute" 
        } 
      },
      "on_success": 3, "on_fail": -1
    },
    {
      "id": 3, "action": "CLICK", "delay": 1000,
      "params": { "x": 0.3676, "y": 0.7911, "align": "center" },
      "on_success": 4, "on_fail": -1
    },
    {
      "id": 4, "action": "BACK", "delay": 1500,
      "params": {},
      "on_success": -1, "on_fail": -1
    }
  ]
}
""".trimIndent()

    fun showWindow() {
        if (floatView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        // 初始位置
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        floatView = LayoutInflater.from(service).inflate(R.layout.layout_daily_window, null)

        val btnLoad = floatView!!.findViewById<Button>(R.id.btn_daily_load)
        val btnAction = floatView!!.findViewById<Button>(R.id.btn_daily_action)
        val btnClose = floatView!!.findViewById<Button>(R.id.btn_daily_close)
        val tvTitle = floatView!!.findViewById<TextView>(R.id.tv_daily_title) // 🔴 拿到标题列作为拖曳手柄

        // 载入测试脚本
        btnLoad.setOnClickListener { loadTestJson() }

        // 关闭悬浮窗
        btnClose.setOnClickListener {
            engine.stop() // ✅ 关闭悬浮窗时同步停止引擎
            windowManager.removeView(floatView)
            floatView = null
        }

        // 开始/停止执行
        btnAction.setOnClickListener {
            if (engine.isRunning) {
                engine.stop()
                btnAction.text = "开始执行"
                updateStatus("已手动停止")
            } else {
                if (currentTaskPlan == null) {
                    Toast.makeText(service, "请先读取任务脚本", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                btnAction.text = "停止执行"

                // 呼叫引擎執行
                engine.startPlan(
                    currentTaskPlan!!,
                    onCompleted = { success, errorMsg -> // 🔴 這裡多接收一個 errorMsg
                        handler.post {
                            btnAction.text = "开始执行"
                            // 🔴 顯示具體的錯誤訊息
                            updateStatus(if (success) "任务全部完成" else "异常终止: $errorMsg")
                        }
                    })
            }
        }

        // 🔴 新增：让标题栏支持触摸拖拽
        tvTitle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatView, params)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        view.performClick()
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatView, params)
    }

    private fun loadTestJson() {
        try {
            currentTaskPlan = Gson().fromJson(TEST_JSON, DailyTaskPlan::class.java)
            updateStatus("内置测试脚本加载成功!")
            Toast.makeText(service, "读取成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            updateStatus("JSON 格式错误")
            Toast.makeText(service, "解析失败，请检查格式", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus(text: String) {
        handler.post { floatView?.findViewById<TextView>(R.id.tv_daily_status)?.text = text }
    }
}
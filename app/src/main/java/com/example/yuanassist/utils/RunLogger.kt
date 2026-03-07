package com.example.yuanassist.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object RunLogger {
    private val logs = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun clear() {
        logs.clear()
    }

    fun i(message: String) {
        val time = timeFormat.format(Date())
        val logLine = "[$time] 🟢 $message"
        logs.add(logLine)
        // 可选：同时输出到 Logcat 方便调试
        Log.i("GameAssist_RunLog", logLine)
    }

    fun e(message: String) {
        val time = timeFormat.format(Date())
        val logLine = "[$time] 🔴 错误: $message"
        logs.add(logLine)
        Log.e("GameAssist_RunLog", logLine)
    }

    fun getAllLogs(): String {
        return logs.joinToString("\n")
    }
}
package com.example.yuanassist.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RunLogger {
    private const val TAG = "GameAssist_RunLog"
    private const val MAX_IN_MEMORY = 2000

    private val logs = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    @Synchronized
    fun clear() {
        logs.clear()
    }

    fun i(message: String) {
        append("I", message, null)
    }

    fun e(message: String) {
        append("E", message, null)
    }

    fun e(message: String, throwable: Throwable) {
        append("E", message, throwable)
    }

    @Synchronized
    fun getAllLogs(): String {
        return logs.joinToString("\n")
    }

    @Synchronized
    private fun append(level: String, message: String, throwable: Throwable?) {
        if (shouldSuppress(message)) return
        val time = timeFormat.format(Date())
        val lines = mutableListOf("[$time] [$level] $message")
        if (throwable != null) {
            val throwableMessage = throwable.message ?: "\uFF08\u65E0\u6D88\u606F\uFF09"
            lines += "${throwable.javaClass.simpleName}: $throwableMessage"
            lines += Log.getStackTraceString(throwable).trimEnd()
        }

        lines.forEach { line ->
            logs.add(line)
            while (logs.size > MAX_IN_MEMORY) {
                logs.removeAt(0)
            }
            if (level == "E") {
                Log.e(TAG, line)
            } else {
                Log.i(TAG, line)
            }
        }
    }

    private fun shouldSuppress(message: String): Boolean {
        return false
    }
}

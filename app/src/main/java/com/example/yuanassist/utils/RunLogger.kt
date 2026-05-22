package com.example.yuanassist.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RunLogger {
    private const val TAG = "GameAssist_RunLog"
    private const val MAX_IN_MEMORY = 2000
    private const val LOG_FILE_NAME = "run_logger.log"

    private val logs = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun clear() {
        logs.clear()
        persistAll()
    }

    fun i(message: String) {
        append("I", message, null)
    }

    fun i(module: String, section: String? = null, message: String) {
        append("I", scopedMessage(module, section, message), null)
    }

    fun raw(message: String) {
        appendRaw(message)
    }

    fun e(message: String) {
        append("E", message, null)
    }

    fun e(message: String, throwable: Throwable) {
        append("E", message, throwable)
    }

    fun e(module: String, section: String? = null, message: String) {
        append("E", scopedMessage(module, section, message), null)
    }

    fun e(module: String, section: String? = null, message: String, throwable: Throwable) {
        append("E", scopedMessage(module, section, message), throwable)
    }

    @Synchronized
    fun getAllLogs(): String {
        if (logs.isEmpty()) {
            val persisted = loadPersistedLines()
            if (persisted.isNotEmpty()) {
                logs.addAll(persisted.takeLast(MAX_IN_MEMORY))
            }
        }
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
        persistAll()
    }

    @Synchronized
    private fun appendRaw(message: String) {
        if (shouldSuppress(message)) return
        logs.add(message)
        while (logs.size > MAX_IN_MEMORY) {
            logs.removeAt(0)
        }
        Log.i(TAG, message)
        persistAll()
    }

    private fun shouldSuppress(message: String): Boolean {
        return false
    }

    private fun scopedMessage(module: String, section: String?, message: String): String {
        val cleanModule = module.trim().ifBlank { "其他日志" }
        val cleanSection = section?.trim()?.takeIf { it.isNotBlank() }
        val prefix = if (cleanSection == null) {
            "[$cleanModule]"
        } else {
            "[$cleanModule / $cleanSection]"
        }
        return "$prefix ${message.trim()}"
    }

    @Synchronized
    private fun persistAll() {
        val context = appContext ?: return
        runCatching {
            logFile(context).writeText(logs.joinToString("\n"), Charsets.UTF_8)
        }.onFailure {
            Log.e(TAG, "persist run log failed", it)
        }
    }

    @Synchronized
    private fun loadPersistedLines(): List<String> {
        val context = appContext ?: return emptyList()
        return runCatching {
            val file = logFile(context)
            if (!file.exists()) emptyList() else file.readLines(Charsets.UTF_8)
        }.getOrElse {
            Log.e(TAG, "load persisted run log failed", it)
            emptyList()
        }
    }

    private fun logFile(context: Context): File = File(context.filesDir, LOG_FILE_NAME)
}

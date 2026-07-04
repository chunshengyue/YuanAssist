package com.example.yuanassist.utils

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.example.yuanassist.core.YuanAssistService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class PersistedExceptionLogEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val content: String,
    val createdAt: Long
)

object ExceptionLogStore {
    private const val PREFS_NAME = "exception_log_store"
    private const val KEY_ENTRIES_JSON = "entries_json"
    private const val KEY_ACTIVE_TRACE_ID = "active_trace_id"
    private const val SECTION_TITLE = "无障碍权限相关"

    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<PersistedExceptionLogEntry>>() {}.type

    fun loadEntries(context: Context): List<PersistedExceptionLogEntry> {
        return runCatching {
            loadMutableEntries(context).sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun deleteEntry(context: Context, entryId: String): Boolean {
        return runCatching {
            val entries = loadMutableEntries(context)
            val removed = entries.removeAll { it.id == entryId }
            if (removed) {
                saveEntries(context, entries)
            }
            removed
        }.getOrDefault(false)
    }

    fun beginAccessibilityTrace(context: Context) {
        runCatching {
            val entry = PersistedExceptionLogEntry(
                id = UUID.randomUUID().toString(),
                title = SECTION_TITLE,
                subtitle = "服务连接诊断",
                content = buildTraceHeader(context).joinToString("\n"),
                createdAt = System.currentTimeMillis()
            )
            saveEntries(context, listOf(entry))
            prefs(context).edit().putString(KEY_ACTIVE_TRACE_ID, entry.id).apply()
        }
    }

    fun appendAccessibilityTrace(context: Context, message: String) {
        runCatching {
            updateActiveEntry(context) { entry ->
                val lines = entry.content.lineSequence().toMutableList()
                lines += "- $message"
                entry.copy(content = lines.joinToString("\n"))
            }
        }
    }

    fun recordServiceException(
        context: Context,
        stage: String,
        throwable: Throwable,
        extras: Map<String, String?> = emptyMap()
    ) {
        runCatching {
            ensureActiveTrace(context)
            appendAccessibilityTrace(
                context,
                buildString {
                    append("异常阶段=$stage")
                    extras.mapValues { it.value?.trim().orEmpty() }
                        .filterValues { it.isNotEmpty() }
                        .forEach { (key, value) ->
                            append("，$key=$value")
                        }
                }
            )
            appendAccessibilityTrace(
                context,
                "异常=${throwable.javaClass.name}: ${throwable.message ?: "（无消息）"}"
            )
            updateActiveEntry(context) { entry ->
                val lines = entry.content.lineSequence().toMutableList()
                lines += "【异常堆栈】"
                lines += Log.getStackTraceString(throwable).trimEnd()
                entry.copy(content = lines.joinToString("\n"))
            }
        }
    }

    fun clearActiveTrace(context: Context) {
        prefs(context).edit().remove(KEY_ACTIVE_TRACE_ID).apply()
    }

    private fun loadMutableEntries(context: Context): MutableList<PersistedExceptionLogEntry> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES_JSON, null)
            .orEmpty()
        if (json.isBlank()) return mutableListOf()
        return runCatching {
            gson.fromJson<MutableList<PersistedExceptionLogEntry>>(json, listType) ?: mutableListOf()
        }.getOrDefault(mutableListOf())
    }

    private fun saveEntries(context: Context, entries: List<PersistedExceptionLogEntry>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES_JSON, gson.toJson(entries))
            .apply()
    }

    private fun buildTraceHeader(context: Context): List<String> {
        return listOf(
            "【无障碍权限相关】",
            "Android：${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "悬浮窗权限：${formatOverlayPermission(context)}",
            "无障碍：总开关=${formatAccessibilitySwitch(context)}，本服务=${formatAccessibilityEnabled(context)}",
            "流程："
        )
    }

    private fun ensureActiveTrace(context: Context) {
        val activeId = prefs(context).getString(KEY_ACTIVE_TRACE_ID, null)
        if (activeId.isNullOrBlank()) {
            beginAccessibilityTrace(context)
        }
    }

    private fun updateActiveEntry(
        context: Context,
        updater: (PersistedExceptionLogEntry) -> PersistedExceptionLogEntry
    ) {
        val activeId = prefs(context).getString(KEY_ACTIVE_TRACE_ID, null) ?: return
        val entries = loadMutableEntries(context)
        val index = entries.indexOfFirst { it.id == activeId }
        if (index == -1) return
        entries[index] = updater(entries[index])
        saveEntries(context, entries)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun formatOverlayPermission(context: Context): String {
        return runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                "已开启"
            } else {
                "未开启"
            }
        }.getOrElse {
            "获取失败"
        }
    }

    private fun formatAccessibilitySwitch(context: Context): String {
        return runCatching {
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
            if (enabled) "已开启" else "未开启"
        }.getOrElse {
            "获取失败"
        }
    }

    private fun formatAccessibilityEnabled(context: Context): String {
        return if (isAccessibilityServiceEnabled(context)) "已启用" else "未启用"
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val expectedComponent = ComponentName(context, YuanAssistService::class.java)
            val setting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(setting)
            while (colonSplitter.hasNext()) {
                val componentName = ComponentName.unflattenFromString(colonSplitter.next())
                if (componentName == expectedComponent) {
                    return true
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }
}

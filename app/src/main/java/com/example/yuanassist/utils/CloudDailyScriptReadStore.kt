package com.example.yuanassist.utils

import android.content.Context
import com.example.yuanassist.model.cloud_daily_script

object CloudDailyScriptReadStore {
    private const val PREFS_NAME = "cloud_daily_script_read"
    private const val KEY_READ_ADMIN_SCRIPT_IDS = "read_admin_script_ids"

    fun hasUnreadAdminScriptIds(context: Context, scriptIds: List<String>): Boolean {
        val readIds = readAdminScriptIds(context)
        return scriptIds.any { it.isNotBlank() && it !in readIds }
    }

    fun markAdminScriptIdsRead(context: Context, scriptIds: List<String>) {
        val currentIds = readAdminScriptIds(context)
        val nextIds = currentIds + scriptIds.filter { it.isNotBlank() }
        if (nextIds != currentIds) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_READ_ADMIN_SCRIPT_IDS, nextIds)
                .apply()
        }
    }

    fun hasUnreadAdminScript(context: Context, items: List<cloud_daily_script>): Boolean {
        val readIds = readAdminScriptIds(context)
        return items.any { it.isAdminPublished && it.objectId.orEmpty() !in readIds }
    }

    fun markAdminScriptsRead(context: Context, items: List<cloud_daily_script>) {
        val currentIds = readAdminScriptIds(context)
        val nextIds = currentIds + items
            .filter { it.isAdminPublished }
            .mapNotNull { it.objectId?.takeIf { objectId -> objectId.isNotBlank() } }
        if (nextIds != currentIds) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_READ_ADMIN_SCRIPT_IDS, nextIds)
                .apply()
        }
    }

    private fun readAdminScriptIds(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_READ_ADMIN_SCRIPT_IDS, emptySet())
            .orEmpty()
    }
}

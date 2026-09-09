package com.example.yuanassist.utils

import android.content.Context

object TraditionalModeStore {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_TRADITIONAL_MODE_ENABLED = "traditional_mode_enabled"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TRADITIONAL_MODE_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TRADITIONAL_MODE_ENABLED, enabled)
            .apply()
    }
}

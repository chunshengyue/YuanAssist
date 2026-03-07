package com.example.yuanassist.utils

import android.content.Context
import android.content.SharedPreferences

// 🟢 1. 精简后的配置类，只保留非坐标参数
data class AppConfig(
    val intervalAttack: Long,
    val intervalSkill: Long,
    val waitTurn: Long,
    val startTurn: Int,
    val swipeThreshold: Int,
    val inputHeightRatio: Int,
    val recordDelay: Long,
    val gameSpeed: Int
)

object ConfigManager {
    private const val PREF_NAME = "game_assist_global_config"

    // 默认值
    const val DEF_INTERVAL_ATTACK = 1500L
    const val DEF_INTERVAL_SKILL = 3000L
    const val DEF_WAIT_TURN = 8000L
    const val DEF_START_TURN = 1

    const val DEF_SWIPE_THRESHOLD = 50
    const val DEF_INPUT_HEIGHT = 31
    const val DEF_RECORD_DELAY = 60L
    const val DEF_GAME_SPEED = 3
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // 🟢 2. 读取配置：不再读取坐标
    fun getAllConfig(context: Context): AppConfig {
        val p = getPrefs(context)
        return AppConfig(
            intervalAttack = p.getLong("interval_attack", DEF_INTERVAL_ATTACK),
            intervalSkill = p.getLong("interval_skill", DEF_INTERVAL_SKILL),
            waitTurn = p.getLong("wait_turn", DEF_WAIT_TURN),
            startTurn = p.getInt("start_turn", DEF_START_TURN),
            swipeThreshold = p.getInt("swipe_threshold", DEF_SWIPE_THRESHOLD),
            inputHeightRatio = p.getInt("input_height", DEF_INPUT_HEIGHT),
            recordDelay = p.getLong("record_delay", DEF_RECORD_DELAY),
            gameSpeed = p.getInt("game_speed", DEF_GAME_SPEED)
        )
    }

    // 🟢 3. 保存配置：只保存时间和录制参数
    fun saveSettings(context: Context, config: AppConfig) {
        getPrefs(context).edit().apply {
            putLong("interval_attack", config.intervalAttack)
            putLong("interval_skill", config.intervalSkill)
            putLong("wait_turn", config.waitTurn)
            putInt("start_turn", config.startTurn)
            putInt("swipe_threshold", config.swipeThreshold)
            putInt("input_height", config.inputHeightRatio)
            putLong("record_delay", config.recordDelay)
            putInt("game_speed", config.gameSpeed)
            apply()
        }
    }
}
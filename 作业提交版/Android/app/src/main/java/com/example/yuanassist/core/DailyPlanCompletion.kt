package com.example.yuanassist.core

data class DailyPlanCompletion(
    val success: Boolean,
    val message: String,
    val terminalCode: Int,
    val terminalTaskId: Int,
    val terminalNote: String? = null,
    val cooldownStartedAtMs: Long? = null
)

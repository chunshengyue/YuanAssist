package com.example.yuanassist.model

data class StargazingConfig(
    val totalCount: Int,
    val clickIntervalMs: Long = 800L,
    val lowSpecDelayMs: Long = 0L,
    val debugModeEnabled: Boolean = false
)

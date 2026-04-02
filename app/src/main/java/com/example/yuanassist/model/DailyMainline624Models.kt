package com.example.yuanassist.model

enum class Mainline624GameVariant(val entryTemplateName: String) {
    RU_YUAN("6-24.png"),
    CODE_NAME_YUAN("6-24(1).png")
}

data class Mainline624Config(
    val maxRuns: Int? = null,
    val lowSpecDelayMs: Long = 0L,
    val debugModeEnabled: Boolean = false,
    val gameVariant: Mainline624GameVariant = Mainline624GameVariant.RU_YUAN
)

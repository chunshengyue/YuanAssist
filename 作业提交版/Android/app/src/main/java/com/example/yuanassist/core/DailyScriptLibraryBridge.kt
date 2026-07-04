package com.example.yuanassist.core

data class DailyPlanSelection(
    val fileName: String,
    val jsonContent: String,
    val templateDirPath: String? = null
)

object DailyScriptLibraryBridge {
    var onDailyPlanSelected: ((DailyPlanSelection) -> Unit)? = null
}

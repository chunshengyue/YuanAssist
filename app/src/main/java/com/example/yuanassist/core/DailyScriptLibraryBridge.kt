package com.example.yuanassist.core

object DailyScriptLibraryBridge {
    var onDailyPlanSelected: ((fileName: String, jsonContent: String) -> Unit)? = null
}

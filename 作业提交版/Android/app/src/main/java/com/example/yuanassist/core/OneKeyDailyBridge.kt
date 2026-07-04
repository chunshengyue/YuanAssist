package com.example.yuanassist.core

object OneKeyDailyBridge {
    @Volatile
    var pendingScriptFileNames: List<String>? = null
}

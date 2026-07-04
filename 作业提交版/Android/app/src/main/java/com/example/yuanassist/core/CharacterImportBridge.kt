package com.example.yuanassist.core

import com.example.yuanassist.model.CharacterImportConfig

object CharacterImportBridge {
    @Volatile
    var pendingConfig: CharacterImportConfig? = null
}

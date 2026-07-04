package com.example.yuanassist.model

data class CharacterImportConfig(
    val operationIntervalMs: Long = 2000L,
    val switchRightPoint: CharacterSwitchPoint = CharacterSwitchPoint(1035f / 1080f, 989f / 1920f),
)

data class CharacterSwitchPoint(
    val xRatio: Float,
    val yRatio: Float,
)

data class ImportedCharacterRecord(
    val slot: Int,
    val name: String = "",
    val hp: String = "",
    val attack: String = "",
    val starCount: Int = 0,
    val fates: List<String> = emptyList(),
    val remark: String = "",
)

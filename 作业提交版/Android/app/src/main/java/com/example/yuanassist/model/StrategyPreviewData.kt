package com.example.yuanassist.model

import com.example.yuanassist.ui.UploadTurnItem

data class StrategyPreviewData(
    val title: String,
    val content: String,
    val attackDelay: Long,
    val skillDelay: Long,
    val waitTurn: Long,
    val strategyImageUri: String?,
    val agentType: Int,
    val agentSelection: List<String?>?,
    val agentImageUri: String?,
    val agentTextDesc: String?,
    val tableData: List<UploadTurnItem>?,
    val instructionsJson: String? = null
)
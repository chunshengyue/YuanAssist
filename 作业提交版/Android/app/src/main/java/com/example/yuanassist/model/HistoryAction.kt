// 文件路径：yuanassist/model/HistoryAction.kt
package com.example.yuanassist.model

data class HistoryAction(
    val type: Int, // 0 代表修改动作，1 代表新增回合，2 代表修改指令/备注
    val turnIndex: Int,
    val charIndex: Int = 0,
    val previousText: CharSequence = "",
    val newText: CharSequence = "",
    val previousStep: Int = 0,
    val newStep: Int = 0,
    val previousRemark: String = "",
    val newRemark: String = "",
    val previousInstructions: List<ScriptInstruction> = emptyList(),
    val newInstructions: List<ScriptInstruction> = emptyList()
)

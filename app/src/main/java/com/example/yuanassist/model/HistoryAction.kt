// 文件路径：yuanassist/model/HistoryAction.kt
package com.example.yuanassist.model

data class HistoryAction(
    val type: Int, // 0 代表修改动作，1 代表新增回合
    val turnIndex: Int,
    val charIndex: Int = 0,
    val previousText: CharSequence = "",
    val newText: CharSequence = "",
    val previousStep: Int = 0,
    val newStep: Int = 0
)
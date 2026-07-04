package com.example.yuanassist.model

data class TurnData(
    var turnNumber: Int,
    // 存储5列的操作内容
    var characterActions: Array<CharSequence> = Array(5) { "" },
    // 录制模式下挂在当前回合上的指令
    var instructions: MutableList<ScriptInstruction> = mutableListOf(),
    // 当前执行到了第几步
    var currentStep: Int = 1,
    // 是否有冲突 (显示红色背景)
    var hasConflict: Boolean = false,
    // 是否正在执行 (显示粉色高亮)
    var isExecuting: Boolean = false,
    // 🔴 新增：备注内容
    var remark: String = ""
)

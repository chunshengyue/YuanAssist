package com.example.yuanassist.model

enum class InstructionType(val description: String) {
    DELAY_ADD("增加延时"),
    PAUSE("执行暂停"),
    TARGET_SWITCH("目标切换")
}

data class ScriptInstruction(
    var turn: Int,
    var step: Int,
    var type: InstructionType,
    var value: Long
) {
    override fun toString(): String {
        val stepStr = if (step == 0) "整回合" else "动作$step 后"
        val valueStr = if (type == InstructionType.DELAY_ADD) "+${value}ms" else ""
        return "T$turn $stepStr : ${type.description} $valueStr"
    }
}
data class InstructionJson(
    val turn: Int,
    val step: Int,
    val type: String,
    val value: Long
)
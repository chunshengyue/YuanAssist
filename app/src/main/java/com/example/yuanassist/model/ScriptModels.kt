package com.example.yuanassist.model

enum class InstructionType(val description: String) {
    DELAY_ADD("增加延时"),
    DELAY_SUBTRACT("缩短延时"),
    PAUSE("执行暂停"),
    STAGE_AUTO_NAV("关卡自动导航"),
    ALL_WIPE_CHECK("全灭检测"),
    DEATH_CHECK("阵亡检测"),
    ORANGE_STAR_CHECK("橙星检测"),
    TARGET_SWITCH("向右切换目标"),
    TARGET_SWITCH_LEFT("向左切换目标"),
    TARGET_SWITCH_RIGHT("向右切换目标")
}

data class ScriptInstruction(
    var turn: Int,
    var step: Int,
    var type: InstructionType,
    var value: Long
) {
    override fun toString(): String {
        val stepStr = if (step == 0) "整回合" else "动作$step 后"
        val valueStr = when (type) {
            InstructionType.DELAY_ADD -> "+${value}ms"
            InstructionType.DELAY_SUBTRACT -> "-${value}ms"
            InstructionType.STAGE_AUTO_NAV -> BattleStageTarget.fromCode(value)?.description ?: "未设置关卡"
            InstructionType.TARGET_SWITCH,
            InstructionType.TARGET_SWITCH_LEFT,
            InstructionType.TARGET_SWITCH_RIGHT -> "${value}次"
            InstructionType.DEATH_CHECK -> "第${value}人"
            InstructionType.PAUSE,
            InstructionType.ALL_WIPE_CHECK,
            InstructionType.ORANGE_STAR_CHECK -> ""
        }
        return "T$turn $stepStr : ${type.description} $valueStr"
    }
}
data class InstructionJson(
    val turn: Int,
    val step: Int,
    val type: String,
    val value: Long
)

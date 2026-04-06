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
    TARGET_SWITCH_RIGHT("向右切换目标");

    fun hidesTurnField(): Boolean = this == STAGE_AUTO_NAV

    fun hidesStepField(): Boolean =
        this == STAGE_AUTO_NAV ||
            this == ALL_WIPE_CHECK ||
            this == DEATH_CHECK ||
            this == ORANGE_STAR_CHECK

    fun normalizeTurn(turn: Int): Int = if (this == STAGE_AUTO_NAV) 1 else turn

    fun normalizeStep(step: Int): Int = if (hidesStepField()) 0 else step

    fun formatTiming(turn: Int, step: Int): String {
        val normalizedTurn = normalizeTurn(turn)
        val normalizedStep = normalizeStep(step)
        return when {
            this == STAGE_AUTO_NAV -> "开战前"
            this == ALL_WIPE_CHECK || this == DEATH_CHECK || this == ORANGE_STAR_CHECK -> "第 $normalizedTurn 回合"
            normalizedStep == 0 -> "第 $normalizedTurn 回合 - 整回合"
            else -> "第 $normalizedTurn 回合 - 动作 $normalizedStep 后"
        }
    }
}

data class ScriptInstruction(
    var turn: Int,
    var step: Int,
    var type: InstructionType,
    var value: Long
) {
    fun normalized(): ScriptInstruction {
        val normalizedTurn = type.normalizeTurn(turn)
        val normalizedStep = type.normalizeStep(step)
        return if (normalizedTurn == turn && normalizedStep == step) {
            this
        } else {
            copy(turn = normalizedTurn, step = normalizedStep)
        }
    }

    fun toInstructionJson(): InstructionJson {
        val normalized = normalized()
        return InstructionJson(
            turn = normalized.turn,
            step = normalized.step,
            type = normalized.type.name,
            value = normalized.value
        )
    }

    override fun toString(): String {
        val normalized = normalized()
        val timingText = normalized.type.formatTiming(normalized.turn, normalized.step)
        val valueStr = when (normalized.type) {
            InstructionType.DELAY_ADD -> "+${normalized.value}ms"
            InstructionType.DELAY_SUBTRACT -> "-${normalized.value}ms"
            InstructionType.STAGE_AUTO_NAV -> formatStageAutoNavDisplay(normalized.value)
            InstructionType.TARGET_SWITCH,
            InstructionType.TARGET_SWITCH_LEFT,
            InstructionType.TARGET_SWITCH_RIGHT -> "${normalized.value}次"
            InstructionType.DEATH_CHECK -> "第${normalized.value}人"
            InstructionType.PAUSE,
            InstructionType.ALL_WIPE_CHECK,
            InstructionType.ORANGE_STAR_CHECK -> ""
        }
        val suffix = valueStr.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        return "$timingText : ${normalized.type.description}$suffix"
    }
}

data class InstructionJson(
    val turn: Int,
    val step: Int,
    val type: String,
    val value: Long
) {
    fun normalized(): InstructionJson {
        val instructionType = runCatching { InstructionType.valueOf(type) }.getOrNull() ?: return this
        val normalizedTurn = instructionType.normalizeTurn(turn)
        val normalizedStep = instructionType.normalizeStep(step)
        return if (normalizedTurn == turn && normalizedStep == step) {
            this
        } else {
            copy(turn = normalizedTurn, step = normalizedStep)
        }
    }

    fun toScriptInstructionOrNull(): ScriptInstruction? {
        val instructionType = runCatching { InstructionType.valueOf(type) }.getOrNull() ?: return null
        val normalized = normalized()
        return ScriptInstruction(
            turn = normalized.turn,
            step = normalized.step,
            type = instructionType,
            value = normalized.value
        )
    }
}

fun ScriptInstruction.toDisplaySummary(): String {
    val normalized = normalized()
    val detail = when (normalized.type) {
        InstructionType.DELAY_ADD -> "${normalized.value}ms"
        InstructionType.DELAY_SUBTRACT -> "${normalized.value}ms"
        InstructionType.STAGE_AUTO_NAV -> formatStageAutoNavDisplay(normalized.value)
        InstructionType.DEATH_CHECK -> "第 ${normalized.value} 人"
        InstructionType.TARGET_SWITCH,
        InstructionType.TARGET_SWITCH_LEFT,
        InstructionType.TARGET_SWITCH_RIGHT -> "${normalized.value} 次"
        InstructionType.PAUSE,
        InstructionType.ALL_WIPE_CHECK,
        InstructionType.ORANGE_STAR_CHECK -> null
    }
    return buildString {
        append(normalized.type.formatTiming(normalized.turn, normalized.step))
        append(" | ")
        append(normalized.type.description)
        if (!detail.isNullOrBlank()) {
            append(" | ")
            append(detail)
        }
    }
}

fun InstructionJson.toDisplaySummary(): String {
    return toScriptInstructionOrNull()?.toDisplaySummary()
        ?: "第${turn}回合 第${step}步: [$type] 数值:$value"
}

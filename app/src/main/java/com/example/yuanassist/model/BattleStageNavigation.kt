package com.example.yuanassist.model

enum class BattleStageTarget(val code: Long, val description: String) {
    BAI_HU(1L, "白鹄"),
    DONG_KU_LEFT(2L, "洞窟左"),
    DONG_KU_RIGHT(3L, "洞窟右"),
    YI_JI_ONE(4L, "遗迹一"),
    YI_JI_TWO(5L, "遗迹二"),
    YI_JI_THREE(6L, "遗迹三"),
    YI_JI_FOUR(7L, "遗迹四"),
    YI_JI_FIVE(8L, "遗迹五（暂不可用）"),
    TAI_SHAN_FU(9L, "泰山府");

    companion object {
        fun fromCode(code: Long): BattleStageTarget? = values().firstOrNull { it.code == code }
    }
}

private const val STAGE_AUTO_NAV_CAVE_NEXT_FLOOR_FLAG = 1000L
private const val TAI_SHAN_FU_STAGE_VALUE_BASE = 9000L
private const val TAI_SHAN_FU_STAGE_VALUE_V2_BASE = 910000L
private const val TAI_SHAN_FU_MIN_LEVEL = 1
private const val TAI_SHAN_FU_MAX_LEVEL = 13

data class TaishanFuStageNavigationConfig(
    val topLevel: Int,
    val targetLevel: Int,
)

fun BattleStageTarget.isCaveTarget(): Boolean =
    this == BattleStageTarget.DONG_KU_LEFT || this == BattleStageTarget.DONG_KU_RIGHT

fun BattleStageTarget.isTaishanFuTarget(): Boolean =
    this == BattleStageTarget.TAI_SHAN_FU

fun encodeStageAutoNavValue(
    target: BattleStageTarget,
    autoEnterNextFloor: Boolean,
    taishanFuTopLevel: Int = TAI_SHAN_FU_MIN_LEVEL,
    taishanFuLevel: Int = TAI_SHAN_FU_MIN_LEVEL,
): Long {
    if (target == BattleStageTarget.TAI_SHAN_FU) {
        val topLevel = taishanFuTopLevel.coerceIn(TAI_SHAN_FU_MIN_LEVEL, TAI_SHAN_FU_MAX_LEVEL)
        val targetLevel = taishanFuLevel.coerceIn(TAI_SHAN_FU_MIN_LEVEL, TAI_SHAN_FU_MAX_LEVEL)
        return TAI_SHAN_FU_STAGE_VALUE_V2_BASE + topLevel * 100L + targetLevel
    }
    return target.code + if (target.isCaveTarget() && autoEnterNextFloor) {
        STAGE_AUTO_NAV_CAVE_NEXT_FLOOR_FLAG
    } else {
        0L
    }
}

fun decodeStageAutoNavTarget(value: Long): BattleStageTarget? {
    if (value == BattleStageTarget.TAI_SHAN_FU.code || taishanFuStageConfigFromValue(value) != null) {
        return BattleStageTarget.TAI_SHAN_FU
    }
    val baseValue = if (value >= STAGE_AUTO_NAV_CAVE_NEXT_FLOOR_FLAG) {
        value - STAGE_AUTO_NAV_CAVE_NEXT_FLOOR_FLAG
    } else {
        value
    }
    return BattleStageTarget.fromCode(baseValue)
}

fun taishanFuStageConfigFromValue(value: Long): TaishanFuStageNavigationConfig? {
    val encoded = value - TAI_SHAN_FU_STAGE_VALUE_V2_BASE
    if (encoded >= 0) {
        val topLevel = (encoded / 100L).toInt()
        val targetLevel = (encoded % 100L).toInt()
        if (topLevel in TAI_SHAN_FU_MIN_LEVEL..TAI_SHAN_FU_MAX_LEVEL &&
            targetLevel in topLevel..TAI_SHAN_FU_MAX_LEVEL
        ) {
            return TaishanFuStageNavigationConfig(topLevel, targetLevel)
        }
    }

    val legacyLevel = (value - TAI_SHAN_FU_STAGE_VALUE_BASE).toInt()
        .takeIf { it in TAI_SHAN_FU_MIN_LEVEL..TAI_SHAN_FU_MAX_LEVEL }
    if (legacyLevel != null) {
        return TaishanFuStageNavigationConfig(TAI_SHAN_FU_MIN_LEVEL, legacyLevel)
    }

    return if (value == BattleStageTarget.TAI_SHAN_FU.code) {
        TaishanFuStageNavigationConfig(TAI_SHAN_FU_MIN_LEVEL, TAI_SHAN_FU_MIN_LEVEL)
    } else {
        null
    }
}

fun taishanFuLevelFromValue(value: Long): Int? =
    taishanFuStageConfigFromValue(value)?.targetLevel

fun isStageAutoNavAutoEnterNextFloorEnabled(value: Long): Boolean {
    return value >= STAGE_AUTO_NAV_CAVE_NEXT_FLOOR_FLAG &&
        decodeStageAutoNavTarget(value)?.isCaveTarget() == true
}

fun formatStageAutoNavDisplay(value: Long): String {
    val target = decodeStageAutoNavTarget(value) ?: return "未设置关卡"
    if (target == BattleStageTarget.TAI_SHAN_FU) {
        val config = taishanFuStageConfigFromValue(value)
            ?: TaishanFuStageNavigationConfig(TAI_SHAN_FU_MIN_LEVEL, TAI_SHAN_FU_MIN_LEVEL)
        return "泰山府 顶部${config.topLevel}关 -> 目标${config.targetLevel}关"
    }
    return if (isStageAutoNavAutoEnterNextFloorEnabled(value)) {
        "${target.description} · 自动进入下一层"
    } else {
        target.description
    }
}

data class TemplateRegionConfig(
    val templateName: String,
    val x: Float,
    val y: Float,
    val align: String,
    val width: Float,
    val height: Float,
    val threshold: Float
)

data class DirectStageNavigationConfig(
    val target: BattleStageTarget,
    val recoverySelectionRegion: TemplateRegionConfig? = null,
    val entryTemplateRegion: TemplateRegionConfig,
    val delayAfterEntryClickMs: Long,
    val delayAfterStartBattleClickMs: Long
)

private const val BATTLE_NAV_TEMPLATE_DIR = "pics/战斗版导航/"

object BattleStageNavigationRegistry {
    val supportedTargets: List<BattleStageTarget> = BattleStageTarget.values().toList()

    private val directConfigs: Map<BattleStageTarget, DirectStageNavigationConfig> = mapOf(
        BattleStageTarget.BAI_HU to DirectStageNavigationConfig(
            target = BattleStageTarget.BAI_HU,
            entryTemplateRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "jinrutiaozhan.png",
                x = 768f,
                y = 1560f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 3000L,
            delayAfterStartBattleClickMs = 6000L
        ),
        BattleStageTarget.DONG_KU_LEFT to DirectStageNavigationConfig(
            target = BattleStageTarget.DONG_KU_LEFT,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "qianwangtaofa.png",
                x = 424f,
                y = 1271f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "qianwangtaofa.png",
                x = 424f,
                y = 1271f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 3000L,
            delayAfterStartBattleClickMs = 6000L
        ),
        BattleStageTarget.DONG_KU_RIGHT to DirectStageNavigationConfig(
            target = BattleStageTarget.DONG_KU_RIGHT,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "qianwangtaofa.png",
                x = 911f,
                y = 1275f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "qianwangtaofa.png",
                x = 911f,
                y = 1275f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 3000L,
            delayAfterStartBattleClickMs = 6000L
        ),
        BattleStageTarget.YI_JI_ONE to DirectStageNavigationConfig(
            target = BattleStageTarget.YI_JI_ONE,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "yiji1.png",
                x = 635f,
                y = 514f,
                align = "center",
                width = 200f,
                height = 300f,
                threshold = 0.75f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "jinruzhandou.png",
                x = 539f,
                y = 1381f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 3000L,
            delayAfterStartBattleClickMs = 6000L
        ),
        BattleStageTarget.YI_JI_TWO to DirectStageNavigationConfig(
            target = BattleStageTarget.YI_JI_TWO,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "yiji2.png",
                x = 243f,
                y = 824f,
                align = "center",
                width = 200f,
                height = 300f,
                threshold = 0.75f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "jinruzhandou.png",
                x = 539f,
                y = 1381f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 3000L,
            delayAfterStartBattleClickMs = 6000L
        ),
        BattleStageTarget.YI_JI_THREE to DirectStageNavigationConfig(
            target = BattleStageTarget.YI_JI_THREE,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "yiji3.png",
                x = 832f,
                y = 897f,
                align = "center",
                width = 200f,
                height = 300f,
                threshold = 0.75f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "jinruzhandou.png",
                x = 539f,
                y = 1381f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 3000L,
            delayAfterStartBattleClickMs = 6000L
        ),
        BattleStageTarget.YI_JI_FOUR to DirectStageNavigationConfig(
            target = BattleStageTarget.YI_JI_FOUR,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "yiji4.png",
                x = 761f,
                y = 1652f,
                align = "center",
                width = 200f,
                height = 300f,
                threshold = 0.75f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "jinruzhandou.png",
                x = 539f,
                y = 1381f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 3000L,
            delayAfterStartBattleClickMs = 6000L
        ),
        BattleStageTarget.YI_JI_FIVE to DirectStageNavigationConfig(
            target = BattleStageTarget.YI_JI_FIVE,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "yiji5.png",
                x = 389f,
                y = 1445f,
                align = "center",
                width = 200f,
                height = 300f,
                threshold = 0.75f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "jinruzhandou.png",
                x = 539f,
                y = 1381f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 3000L,
            delayAfterStartBattleClickMs = 6000L
        ),
        BattleStageTarget.TAI_SHAN_FU to DirectStageNavigationConfig(
            target = BattleStageTarget.TAI_SHAN_FU,
            entryTemplateRegion = TemplateRegionConfig(
                templateName = BATTLE_NAV_TEMPLATE_DIR + "jinruzhandou.png",
                x = 539f,
                y = 1381f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 3000L,
            delayAfterStartBattleClickMs = 6000L
        )
    )

    fun getDirectConfig(target: BattleStageTarget): DirectStageNavigationConfig? = directConfigs[target]
}

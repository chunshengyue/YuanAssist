package com.example.yuanassist.model

enum class BattleStageTarget(val code: Long, val description: String) {
    BAI_HU(1L, "白鹄"),
    DONG_KU_LEFT(2L, "洞窟左"),
    DONG_KU_RIGHT(3L, "洞窟右"),
    YI_JI_ONE(4L, "遗迹一"),
    YI_JI_TWO(5L, "遗迹二"),
    YI_JI_THREE(6L, "遗迹三"),
    YI_JI_FOUR(7L, "遗迹四"),
    YI_JI_FIVE(8L, "遗迹五（暂不可用）");

    companion object {
        fun fromCode(code: Long): BattleStageTarget? = values().firstOrNull { it.code == code }
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

object BattleStageNavigationRegistry {
    val supportedTargets: List<BattleStageTarget> = BattleStageTarget.values().toList()

    private val directConfigs: Map<BattleStageTarget, DirectStageNavigationConfig> = mapOf(
        BattleStageTarget.BAI_HU to DirectStageNavigationConfig(
            target = BattleStageTarget.BAI_HU,
            entryTemplateRegion = TemplateRegionConfig(
                templateName = "jinrutiaozhan.png",
                x = 768f,
                y = 1560f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 1000L,
            delayAfterStartBattleClickMs = 4000L
        ),
        BattleStageTarget.DONG_KU_LEFT to DirectStageNavigationConfig(
            target = BattleStageTarget.DONG_KU_LEFT,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = "qianwangtaofa.png",
                x = 424f,
                y = 1271f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = "qianwangtaofa.png",
                x = 424f,
                y = 1271f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 2000L,
            delayAfterStartBattleClickMs = 4000L
        ),
        BattleStageTarget.DONG_KU_RIGHT to DirectStageNavigationConfig(
            target = BattleStageTarget.DONG_KU_RIGHT,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = "qianwangtaofa.png",
                x = 911f,
                y = 1275f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = "qianwangtaofa.png",
                x = 911f,
                y = 1275f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 2000L,
            delayAfterStartBattleClickMs = 4000L
        ),
        BattleStageTarget.YI_JI_ONE to DirectStageNavigationConfig(
            target = BattleStageTarget.YI_JI_ONE,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = "yiji1.png",
                x = 635f,
                y = 514f,
                align = "center",
                width = 200f,
                height = 300f,
                threshold = 0.75f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = "jinruzhandou.png",
                x = 539f,
                y = 1381f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 2000L,
            delayAfterStartBattleClickMs = 4000L
        ),
        BattleStageTarget.YI_JI_TWO to DirectStageNavigationConfig(
            target = BattleStageTarget.YI_JI_TWO,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = "yiji2.png",
                x = 243f,
                y = 824f,
                align = "center",
                width = 200f,
                height = 300f,
                threshold = 0.75f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = "jinruzhandou.png",
                x = 539f,
                y = 1381f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 2000L,
            delayAfterStartBattleClickMs = 4000L
        ),
        BattleStageTarget.YI_JI_THREE to DirectStageNavigationConfig(
            target = BattleStageTarget.YI_JI_THREE,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = "yiji3.png",
                x = 832f,
                y = 897f,
                align = "center",
                width = 200f,
                height = 300f,
                threshold = 0.75f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = "jinruzhandou.png",
                x = 539f,
                y = 1381f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 2000L,
            delayAfterStartBattleClickMs = 4000L
        ),
        BattleStageTarget.YI_JI_FOUR to DirectStageNavigationConfig(
            target = BattleStageTarget.YI_JI_FOUR,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = "yiji4.png",
                x = 761f,
                y = 1652f,
                align = "center",
                width = 200f,
                height = 300f,
                threshold = 0.75f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = "jinruzhandou.png",
                x = 539f,
                y = 1381f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 2000L,
            delayAfterStartBattleClickMs = 4000L
        ),
        BattleStageTarget.YI_JI_FIVE to DirectStageNavigationConfig(
            target = BattleStageTarget.YI_JI_FIVE,
            recoverySelectionRegion = TemplateRegionConfig(
                templateName = "yiji5.png",
                x = 389f,
                y = 1445f,
                align = "center",
                width = 200f,
                height = 300f,
                threshold = 0.75f
            ),
            entryTemplateRegion = TemplateRegionConfig(
                templateName = "jinruzhandou.png",
                x = 539f,
                y = 1381f,
                align = "center",
                width = 300f,
                height = 300f,
                threshold = 0.80f
            ),
            delayAfterEntryClickMs = 2000L,
            delayAfterStartBattleClickMs = 4000L
        )
    )

    fun getDirectConfig(target: BattleStageTarget): DirectStageNavigationConfig? = directConfigs[target]
}

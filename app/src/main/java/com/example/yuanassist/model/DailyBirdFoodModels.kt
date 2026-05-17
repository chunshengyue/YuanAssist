package com.example.yuanassist.model

enum class BirdFoodStopCondition {
    RUN_COUNT,
    RESOURCE_EXHAUSTED,
    DURATION_MINUTES
}

enum class BirdFoodTaskType(
    val scriptFileName: String,
    val displayName: String,
    val hasCooldown: Boolean
) {
    TU_FA_QING_KUANG("tu_fa_qing_kuang.json", "突发情况", false),
    XIAO_DAO_XIAO_XI("xiao_dao_xiao_xi.json", "小道消息", true),
    TA_DE_CHUAN_WEN("ta_de_chuan_wen.json", "他的传闻", false),
    DAI_BAN_GONG_WU("dai_ban_gong_wu.json", "待办公务", false)
}

enum class DaiBanGongWuOption {
    BING_SHU,
    WU_ZHU_QIAN
}

enum class DaiBanGongWuEntry(
    val prefValue: String,
    val taskId: Int,
    val branchValue: String
) {
    LEFT_TOP("left_top", 31, "1-1"),
    RIGHT_TOP("right_top", 32, "1-2"),
    LEFT_BOTTOM("left_bottom", 33, "1-3"),
    RIGHT_BOTTOM("right_bottom", 34, "1-4");

    companion object {
        fun fromTaskId(taskId: Int): DaiBanGongWuEntry? = values().firstOrNull { it.taskId == taskId }
    }
}

enum class TaDeChuanWenOption {
    RUYUAN,
    DAIHAOYUAN
}

data class BirdFoodConfig(
    val selectedTask: BirdFoodTaskType,
    val autoEatEnabled: Boolean,
    val stopCondition: BirdFoodStopCondition,
    val debugModeEnabled: Boolean = false,
    val saveDebugScreenshotsEnabled: Boolean = false,
    val lowSpecDelayMs: Long = 0L,
    val maxRuns: Int? = null,
    val maxDurationMinutes: Int? = null,
    val daiBanGongWuOption: DaiBanGongWuOption = DaiBanGongWuOption.BING_SHU,
    val skippedDaiBanGongWuEntries: Set<DaiBanGongWuEntry> = emptySet(),
    val taDeChuanWenOption: TaDeChuanWenOption = TaDeChuanWenOption.RUYUAN
)

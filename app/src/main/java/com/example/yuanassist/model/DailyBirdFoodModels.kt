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
    TU_FA_QING_KUANG("tu_fa_qing_kuang.json", "突发情况", true),
    XIAO_DAO_XIAO_XI("xiao_dao_xiao_xi.json", "小道消息", true),
    TA_DE_CHUAN_WEN("ta_de_chuan_wen.json", "他的传闻", false),
    DAI_BAN_GONG_WU("dai_ban_gong_wu.json", "待办公务", true)
}

data class BirdFoodConfig(
    val selectedTasks: List<BirdFoodTaskType>,
    val autoEatEnabled: Boolean,
    val stopCondition: BirdFoodStopCondition,
    val debugModeEnabled: Boolean = false,
    val maxRuns: Int? = null,
    val maxDurationMinutes: Int? = null
)

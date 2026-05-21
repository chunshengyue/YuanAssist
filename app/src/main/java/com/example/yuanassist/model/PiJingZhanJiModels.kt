package com.example.yuanassist.model

enum class PiJingZhanJiGameVariant {
    RU_YUAN,
    CODE_NAME_YUAN
}

enum class PiJingZhanJiTaskType(
    val displayName: String,
    val scriptFileName: String,
    val requiredAttributeCount: Int
) {
    MAINLINE_624_ONCE(
        displayName = "一次624（选3属性）",
        scriptFileName = "pi_jing_zhan_ji_624_once.json",
        requiredAttributeCount = 3
    ),
    MAINLINE_624_TWICE(
        displayName = "两次624（跑6属性）",
        scriptFileName = "pi_jing_zhan_ji_624_twice.json",
        requiredAttributeCount = 6
    ),
    MI_TAN_ZENG_LI_30(
        displayName = "密探赠礼30次",
        scriptFileName = "pi_jing_zhan_ji_mi_tan_song_li_30.json",
        requiredAttributeCount = 0
    ),
    XING_NANG(
        displayName = "行囊",
        scriptFileName = "pi_jing_zhan_ji_xing_nang.json",
        requiredAttributeCount = 0
    ),
    YUAN_BAO_26(
        displayName = "鸢报25次",
        scriptFileName = "pi_jing_zhan_ji_yuan_bao_26.json",
        requiredAttributeCount = 0
    ),
    JIA_JU_TI_LI(
        displayName = "家具体力",
        scriptFileName = "pi_jing_zhan_ji_jia_ju_ti_li.json",
        requiredAttributeCount = 0
    ),
    JIA_JU_DA_ZAO(
        displayName = "家具打造",
        scriptFileName = "pi_jing_zhan_ji_jia_ju_da_zao.json",
        requiredAttributeCount = 0
    ),
    CAI_LIAO_HE_CHENG(
        displayName = "材料合成",
        scriptFileName = "pi_jing_zhan_ji_cai_liao_he_cheng.json",
        requiredAttributeCount = 0
    ),
    GUAN_XING_WU_ZHU_QIAN(
        displayName = "观星消耗五铢钱",
        scriptFileName = "",
        requiredAttributeCount = 0
    )
}

enum class PiJingZhanJiStargazingMode {
    NO_MONTH_CARD,
    MONTH_CARD
}

data class PiJingZhanJiTaskConfig(
    val type: PiJingZhanJiTaskType,
    val selectedAttributes: List<String> = emptyList(),
    val stargazingMode: PiJingZhanJiStargazingMode? = null,
    val stargazingValue: Int? = null
)

data class PiJingZhanJiConfig(
    val gameVariant: PiJingZhanJiGameVariant = PiJingZhanJiGameVariant.RU_YUAN,
    val tasks: List<PiJingZhanJiTaskConfig> = emptyList(),
    val lowSpecDelayMs: Long = 0L,
    val debugModeEnabled: Boolean = false,
    val activityDebugModeEnabled: Boolean = false,
    val enableActivityModule: Boolean = true,
    val refreshUnsupportedActivityTask: Boolean = false
)

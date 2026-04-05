package plus.maa.backend.controller.response.copilot

import java.io.Serializable

/**
 * v2 关卡信息，包含四层级（含 game）
 */
data class ArkLevelInfoV2(
    val levelId: String,
    val stageId: String,
    val catOne: String,
    val catTwo: String,
    val catThree: String,
    val name: String,
) : Serializable

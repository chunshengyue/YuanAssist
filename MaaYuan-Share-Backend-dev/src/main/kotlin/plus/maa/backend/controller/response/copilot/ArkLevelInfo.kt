package plus.maa.backend.controller.response.copilot

import java.io.Serializable

/**
 * @author john180
 */
data class ArkLevelInfo(
    val levelId: String,
    val stageId: String,
    val catOne: String,
    val catTwo: String,
    val catThree: String,
    val name: String,
) : Serializable

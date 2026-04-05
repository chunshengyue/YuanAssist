package plus.maa.backend.controller.request.copilot

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.constraints.NotBlank
import plus.maa.backend.repository.entity.Copilot

/**
 * @author LoMu
 * Date  2023-01-10 19:50
 */
data class CopilotDTO(
    @field:NotBlank(message = "关卡名不能为空")
    var stageName: String,
    val difficulty: Int = 0,
    @field:NotBlank(message = "最低要求 maa 版本不可为空")
    val minimumRequired: String,
    val opers: List<Copilot.Operators>? = null,
    val groups: List<Copilot.Groups>? = null,
    val actions: List<Copilot.Action>? = null,
    val simingActions: Map<String, Copilot.SimingAction>? = null,
    val doc: Copilot.Doc? = null,
    @JsonProperty("level_meta")
    @JsonAlias("levelMeta")
    val levelMeta: Copilot.LevelMeta? = null,
    val notification: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CopilotRawDTO(
    @field:NotBlank(message = "关卡名不能为空")
    var stageName: String,
    val difficulty: Int = 0,
    @field:NotBlank(message = "最低要求 maa 版本不可为空")
    val minimumRequired: String,
    val opers: List<Copilot.Operators>? = null,
    val groups: List<Copilot.Groups>? = null,
    @JsonProperty("actions")
    val actionsNode: JsonNode? = null,
    val doc: Copilot.Doc? = null,
    @JsonProperty("level_meta")
    @JsonAlias("levelMeta")
    val levelMeta: Copilot.LevelMeta? = null,
    val notification: Boolean = false,
)

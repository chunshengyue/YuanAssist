package plus.maa.backend.controller.request.copilot

import jakarta.validation.constraints.NotBlank
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import plus.maa.backend.config.validation.JsonSchemaMatch
import plus.maa.backend.config.validation.JsonSchemaMatchValidator
import plus.maa.backend.service.model.CopilotSetStatus

data class CopilotCUDRequest(
    @field:NotBlank(message = "作业内容必填")
    @JsonSchemaMatch(schema = JsonSchemaMatchValidator.COPILOT_SCHEMA_JSON)
    val content: String = "",
    val id: Long? = null,
    val status: CopilotSetStatus = CopilotSetStatus.PUBLIC,
    val metadata: CopilotMetadataRequest? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CopilotMetadataRequest(
    @JsonAlias("sourceType")
    val sourceType: String = "original",
    @JsonAlias("repostAuthor")
    val repostAuthor: String? = null,
    @JsonAlias("repostPlatform")
    val repostPlatform: String? = null,
    @JsonAlias("repostUrl")
    val repostUrl: String? = null,
    // 外挂 tags（固定词表），上传/更新时可选
    @JsonAlias("tags")
    val tags: List<String>? = null,
)

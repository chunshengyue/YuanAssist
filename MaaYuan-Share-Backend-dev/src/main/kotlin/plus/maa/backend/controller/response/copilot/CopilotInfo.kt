package plus.maa.backend.controller.response.copilot

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import plus.maa.backend.service.model.CommentStatus
import plus.maa.backend.service.model.CopilotSetStatus
import java.io.Serializable
import java.time.LocalDateTime

data class CopilotInfo(
    val id: Long,
    val uploadTime: LocalDateTime,
    val uploaderId: String,
    val uploader: String,
    // 去反推化的关卡元信息（可选，旧数据可能为空）
    val stageId: String? = null,
    val name: String? = null,
    val catOne: String? = null,
    val catTwo: String? = null,
    val catThree: String? = null,
    // 外挂标签（多选 AND），与三层分类并列
    val tags: List<String>? = null,
    // 用于前端显示的格式化后的干员信息 [干员名]::[技能]
    val views: Long = 0,
    val hotScore: Double = 0.0,
    var available: Boolean = false,
    var ratingLevel: Int = 0,
    var notEnoughRating: Boolean = false,
    var ratingRatio: Double = 0.0,
    var ratingType: Int = 0,
    val commentsCount: Long = 0,
    val content: String,
    val like: Long = 0,
    val dislike: Long = 0,
    val commentStatus: CommentStatus = CommentStatus.ENABLED,
    val status: CopilotSetStatus = CopilotSetStatus.PUBLIC,
    // 当后端无元数据时返回 null，且显式输出 null（不省略）
    @field:JsonInclude(JsonInclude.Include.ALWAYS)
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val metadata: CopilotMetadataInfo? = null,
) : Serializable

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CopilotMetadataInfo(
    @JsonAlias("sourceType")
    val sourceType: String = "original",
    @JsonAlias("repostAuthor")
    val repostAuthor: String? = null,
    @JsonAlias("repostPlatform")
    val repostPlatform: String? = null,
    @JsonAlias("repostUrl")
    val repostUrl: String? = null,
) : Serializable

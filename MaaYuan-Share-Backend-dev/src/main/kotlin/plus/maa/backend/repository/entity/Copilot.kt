package plus.maa.backend.repository.entity

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import plus.maa.backend.repository.entity.CollectionMeta
import plus.maa.backend.service.model.CommentStatus
import plus.maa.backend.service.model.CopilotSetStatus
import java.io.Serializable
import java.time.LocalDateTime

/**
 * 作业实体（MongoDB）
 * 说明：该实体包含业务查询中会用到的最小字段集合，
 * 以满足编译与运行需求，避免不必要的过度设计。
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
@Document("maa_copilot")
data class Copilot(
    @Id
    var id: String? = null,

    // 业务主键（自增）
    var copilotId: Long? = null,

    // 基本信息
    var stageName: String? = null,
    // 归一后的 stageId（与 stageName 等价，便于明确语义）
    var stageId: String? = null,
    // 去反推化的关卡元信息，冗余存储，便于前端直接展示
    var name: String? = null,
    var catOne: String? = null,
    var catTwo: String? = null,
    var catThree: String? = null,
    var difficulty: Int? = null,
    var minimumRequired: String? = null,
    var levelMeta: LevelMeta? = null,

    // 作者/时间
    var uploaderId: String? = null,
    var uploadTime: LocalDateTime? = null,
    var firstUploadTime: LocalDateTime? = null,

    // 展示与热度
    @Indexed
    var views: Long = 0,
    @Indexed
    var hotScore: Double = 0.0,

    // 评分信息
    var likeCount: Long = 0,
    var dislikeCount: Long = 0,
    var ratingRatio: Double = 0.0,
    var ratingLevel: Int = 0,

    // 状态控制
    var delete: Boolean = false,
    var deleteTime: LocalDateTime? = null,
    var commentStatus: CommentStatus? = CommentStatus.ENABLED,
    var status: CopilotSetStatus = CopilotSetStatus.PUBLIC,
    var notification: Boolean = false,

    // 原始内容（JSON 字符串）
    var content: String? = null,

    // 以下为内容结构（用于反序列化/校验/回显）
    var opers: List<Operators>? = null,
    var groups: List<Groups>? = null,
    var actions: List<Action>? = null,
    var simingActions: Map<String, SimingAction>? = null,
    var doc: Doc? = null,
    var metadata: Metadata? = null,
    // 新增：外挂 tags（多选）；用于列表筛选（AND）
    var tags: List<String>? = null,
): Serializable {
    companion object {
        @JvmStatic
        val META = CollectionMeta(
            { obj: Copilot -> obj.copilotId ?: 0L },
            "copilotId",
            Copilot::class.java,
        )
    }

    // 嵌套类型（根据使用点保留最小字段）
    data class Operators(
        var name: String? = null,
    ) : Serializable

    data class Groups(
        var opers: List<OperationGroup>? = null,
    ) : Serializable

    data class OperationGroup(
        var name: String? = null,
    ) : Serializable

    data class Action(
        var name: String? = null,
    ) : Serializable

    data class SimingAction(
        var name: String? = null,
    ) : Serializable

    data class Doc(
        var title: String? = null,
        var details: String? = null,
    ) : Serializable

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class Metadata(
        var sourceType: String? = null,
        var repostAuthor: String? = null,
        var repostPlatform: String? = null,
        var repostUrl: String? = null,
    ) : Serializable

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class LevelMeta(
        var stageId: String? = null,
        var levelId: String? = null,
        var name: String? = null,
        var catOne: String? = null,
        var catTwo: String? = null,
        var catThree: String? = null,
        var width: Int? = null,
        var height: Int? = null,
    ) : Serializable
}

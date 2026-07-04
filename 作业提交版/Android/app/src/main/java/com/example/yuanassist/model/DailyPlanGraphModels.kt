package com.example.yuanassist.model

data class DailyPlanGraph(
    val startTaskId: Int,
    val taskCount: Int,
    val layoutNodes: List<GraphLayoutNode>,
    val edges: List<GraphEdgeUiModel>,
    val maxLayer: Int,
    val maxRow: Int
)

data class GraphLayoutNode(
    val node: GraphNodeUiModel,
    val layer: Int,
    val row: Int
)

data class GraphNodeUiModel(
    val taskId: Int,
    val action: String,
    val title: String,
    val infoChips: List<String>,
    val detail: NodeDetailUiModel
)

data class GraphEdgeUiModel(
    val fromTaskId: Int,
    val toTaskId: Int?,
    val terminalLabel: String? = null,
    val type: GraphEdgeType,
    val label: String? = null
)

enum class GraphEdgeType {
    SUCCESS,
    FAIL,
    BRANCH
}

data class NodeDetailUiModel(
    val taskId: Int,
    val action: String,
    val delay: Long,
    val onSuccess: Int,
    val onFail: Int,
    val startCooldownOnSuccess: Boolean,
    val summary: String,
    val paramsLines: List<String>,
    val incomingReferences: List<NodeIncomingReference>,
    val rawJson: String,
    val editable: Boolean
)

data class NodeIncomingReference(
    val fromTaskId: Int,
    val kind: String,
    val label: String
)

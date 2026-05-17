package com.example.yuanassist.core

import com.example.yuanassist.model.DailyPlanGraph
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.GraphEdgeType
import com.example.yuanassist.model.GraphEdgeUiModel
import com.example.yuanassist.model.GraphLayoutNode
import com.example.yuanassist.model.GraphNodeUiModel
import com.example.yuanassist.model.NodeDetailUiModel
import com.example.yuanassist.model.NodeIncomingReference
import com.google.gson.GsonBuilder
import java.util.ArrayDeque
import java.util.Locale

object DailyPlanGraphBuilder {

    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    fun build(plan: DailyTaskPlan, editable: Boolean): DailyPlanGraph {
        val tasksById = plan.tasks.associateBy { it.id }
        val incomingMap = buildIncomingMap(plan.tasks)
        val layerById = buildLayers(plan, tasksById)
        val groupedByLayer = layerById.entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, ids) -> ids.sorted() }
        val layoutNodes = groupedByLayer.entries
            .sortedBy { it.key }
            .flatMap { (layer, ids) ->
                ids.mapIndexed { row, taskId ->
                    val task = tasksById.getValue(taskId)
                    GraphLayoutNode(
                        node = buildNode(task, incomingMap[taskId].orEmpty(), editable),
                        layer = layer,
                        row = row
                    )
                }
            }
        val edges = plan.tasks.flatMap { buildEdges(it, tasksById) }
        val maxLayer = layoutNodes.maxOfOrNull { it.layer } ?: 0
        val maxRow = layoutNodes.maxOfOrNull { it.row } ?: 0
        return DailyPlanGraph(
            startTaskId = plan.start_task_id,
            taskCount = plan.tasks.size,
            layoutNodes = layoutNodes,
            edges = edges,
            maxLayer = maxLayer,
            maxRow = maxRow
        )
    }

    fun buildIncomingOnSuccessOrFailRefs(tasks: List<DailyTask>, targetId: Int): List<NodeIncomingReference> {
        val refs = mutableListOf<NodeIncomingReference>()
        tasks.forEach { task ->
            if (task.on_success == targetId) {
                refs += NodeIncomingReference(
                    fromTaskId = task.id,
                    kind = "on_success",
                    label = "#${task.id} on_success -> $targetId"
                )
            }
            if (task.on_fail == targetId) {
                refs += NodeIncomingReference(
                    fromTaskId = task.id,
                    kind = "on_fail",
                    label = "#${task.id} on_fail -> $targetId"
                )
            }
        }
        return refs.sortedBy { it.fromTaskId }
    }

    fun summarizeTask(task: DailyTask): String {
        val params = task.params
        return when (task.action) {
            "CLICK" -> {
                when {
                    params?.ref_task_id != null -> "引用节点 #${params.ref_task_id}"
                    params?.x != null && params.y != null -> {
                        val align = params.align.ifBlank { "center" }
                        "点击 (${formatFloat(params.x)}, ${formatFloat(params.y)}) / $align"
                    }
                    else -> "点击"
                }
            }
            "MATCH_TEMPLATE" -> {
                val templateName = params?.template_name ?: "(未填写模板)"
                val threshold = String.format(Locale.US, "%.2f", params?.threshold ?: 0f)
                val clickLabel = if (params?.click == 1) "点击" else "不点击"
                "$templateName / 阈值 $threshold / $clickLabel"
            }
            "OCR" -> {
                val text = params?.target_chars?.joinToString("")?.ifBlank { "(未填写文字)" }
                    ?: "(未填写文字)"
                val clickLabel = if (params?.click == 1) "点击" else "不点击"
                val minHit = params?.min_hit_count ?: 1
                "OCR \"$text\" / minHit=$minHit / $clickLabel"
            }
            "SET_VAR" -> "${params?.var_name ?: "(变量名空)"} = ${params?.var_value ?: ""}"
            "BACK" -> "返回"
            else -> task.action
        }
    }

    fun displayName(task: DailyTask): String {
        return task.name?.trim().takeUnless { it.isNullOrBlank() } ?: task.action
    }

    fun buildParamsLines(task: DailyTask): List<String> {
        val p = task.params ?: return listOf("params: null")
        val lines = mutableListOf<String>()
        p.x?.let { lines += "x: ${formatFloat(it)}" }
        p.y?.let { lines += "y: ${formatFloat(it)}" }
        p.startX?.let { lines += "startX: ${formatFloat(it)}" }
        p.startY?.let { lines += "startY: ${formatFloat(it)}" }
        p.endX?.let { lines += "endX: ${formatFloat(it)}" }
        p.endY?.let { lines += "endY: ${formatFloat(it)}" }
        p.duration?.let { lines += "duration: $it" }
        if (p.align.isNotBlank()) lines += "align: ${p.align}"
        p.template_name?.let { lines += "template_name: $it" }
        p.target_text?.let { lines += "target_text: $it" }
        if (p.threshold > 0f) lines += "threshold: ${String.format(Locale.US, "%.2f", p.threshold)}"
        p.roi?.let { roi ->
            lines += "roi: x=${formatNullableFloat(roi.x)} y=${formatNullableFloat(roi.y)} " +
                "w=${formatNullableFloat(roi.w)} h=${formatNullableFloat(roi.h)} align=${roi.align}"
        }
        p.button_name?.let { lines += "button_name: $it" }
        p.ref_task_id?.let { lines += "ref_task_id: $it" }
        p.click?.let { lines += "click: $it" }
        p.target_chars?.let { lines += "target_chars: ${it.joinToString("")}" }
        p.min_hit_count?.let { lines += "min_hit_count: $it" }
        p.preprocess?.let { lines += "preprocess: $it" }
        p.var_name?.let { lines += "var_name: $it" }
        p.var_value?.let { lines += "var_value: $it" }
        p.branch_var?.let { lines += "branch_var: $it" }
        p.branch_routes?.takeIf { it.isNotEmpty() }?.let { routes ->
            lines += "branch_routes:"
            routes.toSortedMap().forEach { (key, value) ->
                lines += "  $key -> $value"
            }
        }
        p.fail_branch_var?.let { lines += "fail_branch_var: $it" }
        p.fail_branch_routes?.takeIf { it.isNotEmpty() }?.let { routes ->
            lines += "fail_branch_routes:"
            routes.toSortedMap().forEach { (key, value) ->
                lines += "  $key -> $value"
            }
        }
        p.terminal_note?.let { lines += "terminal_note: $it" }
        return lines.ifEmpty { listOf("params: {}") }
    }

    private fun buildNode(
        task: DailyTask,
        incomingReferences: List<NodeIncomingReference>,
        editable: Boolean
    ): GraphNodeUiModel {
        val summary = summarizeTask(task)
        return GraphNodeUiModel(
            taskId = task.id,
            action = task.action,
            title = "#${task.id} ${displayName(task)}",
            infoChips = buildNodeChips(task),
            detail = NodeDetailUiModel(
                taskId = task.id,
                action = task.action,
                delay = task.delay,
                onSuccess = task.on_success,
                onFail = task.on_fail,
                startCooldownOnSuccess = task.start_cooldown_on_success,
                summary = summary,
                paramsLines = buildParamsLines(task),
                incomingReferences = incomingReferences.sortedBy { it.fromTaskId },
                rawJson = prettyGson.toJson(task),
                editable = editable
            )
        )
    }

    private fun buildNodeChips(task: DailyTask): List<String> {
        val params = task.params
        val chips = mutableListOf<String>()
        chips += "delay ${task.delay}"
        task.name?.trim()?.takeIf { it.isNotBlank() }?.let { chips += "名称 $it" }
        when (task.action) {
            "CLICK" -> {
                when {
                    params?.ref_task_id != null -> chips += "引用 #${params.ref_task_id}"
                    params?.x != null && params.y != null -> {
                        chips += "(${formatFloat(params.x)}, ${formatFloat(params.y)})"
                        if (params.align.isNotBlank()) chips += params.align
                    }
                }
            }
            "MATCH_TEMPLATE" -> {
                params?.template_name?.let { chips += it }
                chips += "阈值 ${String.format(Locale.US, "%.2f", params?.threshold ?: 0f)}"
                chips += if (params?.click == 1) "点击" else "不点击"
            }
            "OCR" -> {
                val text = params?.target_chars?.joinToString("")?.ifBlank { "(空)" } ?: "(空)"
                chips += "OCR $text"
                chips += "minHit ${params?.min_hit_count ?: 1}"
                chips += if (params?.click == 1) "点击" else "不点击"
            }
            "SET_VAR" -> {
                params?.var_name?.let { chips += "变量 $it" }
                params?.var_value?.takeIf { it.isNotBlank() }?.let { chips += it }
            }
            "BACK" -> chips += "返回"
        }
        chips += "success -> ${task.on_success}"
        params?.branch_var?.takeIf { it.isNotBlank() }?.let { chips += "成功分支 $it" }
        chips += "fail -> ${task.on_fail}"
        params?.fail_branch_var?.takeIf { it.isNotBlank() }?.let { chips += "失败分支 $it" }
        return chips
    }

    private fun buildEdges(task: DailyTask, tasksById: Map<Int, DailyTask>): List<GraphEdgeUiModel> {
        val edges = mutableListOf<GraphEdgeUiModel>()
        edges += createJumpEdge(task.id, task.on_success, GraphEdgeType.SUCCESS, "success", tasksById)
        edges += createJumpEdge(task.id, task.on_fail, GraphEdgeType.FAIL, "fail", tasksById)
        task.params?.branch_routes
            ?.toSortedMap()
            ?.forEach { (key, targetId) ->
                edges += createJumpEdge(task.id, targetId, GraphEdgeType.BRANCH, key, tasksById)
            }
        task.params?.fail_branch_routes
            ?.toSortedMap()
            ?.forEach { (key, targetId) ->
                edges += createJumpEdge(task.id, targetId, GraphEdgeType.BRANCH, "fail:$key", tasksById)
            }
        return edges
    }

    private fun createJumpEdge(
        fromTaskId: Int,
        targetId: Int,
        type: GraphEdgeType,
        label: String,
        tasksById: Map<Int, DailyTask>
    ): GraphEdgeUiModel {
        return if (targetId > 0 && tasksById.containsKey(targetId)) {
            GraphEdgeUiModel(
                fromTaskId = fromTaskId,
                toTaskId = targetId,
                type = type,
                label = label
            )
        } else {
            GraphEdgeUiModel(
                fromTaskId = fromTaskId,
                toTaskId = null,
                terminalLabel = terminalLabel(targetId),
                type = type,
                label = label
            )
        }
    }

    private fun buildLayers(plan: DailyTaskPlan, tasksById: Map<Int, DailyTask>): Map<Int, Int> {
        if (tasksById.isEmpty()) return emptyMap()
        val layerById = linkedMapOf<Int, Int>()
        val visited = linkedSetOf<Int>()

        fun bfs(startId: Int, baseLayer: Int) {
            if (!tasksById.containsKey(startId) || visited.contains(startId)) return
            val queue = ArrayDeque<Pair<Int, Int>>()
            queue += startId to baseLayer
            while (queue.isNotEmpty()) {
                val (taskId, layer) = queue.removeFirst()
                if (!visited.add(taskId)) continue
                layerById[taskId] = layer
                val task = tasksById.getValue(taskId)
                neighborIds(task, tasksById).forEach { nextId ->
                    if (!visited.contains(nextId)) {
                        queue += nextId to (layer + 1)
                    }
                }
            }
        }

        val startId = plan.start_task_id.takeIf { tasksById.containsKey(it) } ?: tasksById.keys.minOrNull()!!
        bfs(startId, 0)

        var nextBaseLayer = (layerById.values.maxOrNull() ?: 0) + 1
        tasksById.keys.sorted().forEach { taskId ->
            if (!visited.contains(taskId)) {
                bfs(taskId, nextBaseLayer)
                nextBaseLayer = (layerById.values.maxOrNull() ?: nextBaseLayer) + 1
            }
        }
        return layerById
    }

    private fun neighborIds(task: DailyTask, tasksById: Map<Int, DailyTask>): List<Int> {
        val result = mutableListOf<Int>()
        listOf(task.on_success, task.on_fail).forEach { targetId ->
            if (targetId > 0 && tasksById.containsKey(targetId)) result += targetId
        }
        task.params?.branch_routes
            ?.toSortedMap()
            ?.values
            ?.forEach { targetId ->
                if (targetId > 0 && tasksById.containsKey(targetId)) result += targetId
            }
        task.params?.fail_branch_routes
            ?.toSortedMap()
            ?.values
            ?.forEach { targetId ->
                if (targetId > 0 && tasksById.containsKey(targetId)) result += targetId
            }
        return result.distinct()
    }

    private fun buildIncomingMap(tasks: List<DailyTask>): Map<Int, List<NodeIncomingReference>> {
        val result = linkedMapOf<Int, MutableList<NodeIncomingReference>>()
        fun append(targetId: Int, reference: NodeIncomingReference) {
            if (targetId > 0) {
                result.getOrPut(targetId) { mutableListOf() } += reference
            }
        }

        tasks.forEach { task ->
            append(
                task.on_success,
                NodeIncomingReference(task.id, "on_success", "#${task.id} on_success -> ${task.on_success}")
            )
            append(
                task.on_fail,
                NodeIncomingReference(task.id, "on_fail", "#${task.id} on_fail -> ${task.on_fail}")
            )
            task.params?.branch_routes
                ?.toSortedMap()
                ?.forEach { (key, targetId) ->
                    append(
                        targetId,
                        NodeIncomingReference(task.id, "branch_routes", "#${task.id} branch[$key] -> $targetId")
                    )
                }
            task.params?.fail_branch_routes
                ?.toSortedMap()
                ?.forEach { (key, targetId) ->
                    append(
                        targetId,
                        NodeIncomingReference(task.id, "fail_branch_routes", "#${task.id} fail_branch[$key] -> $targetId")
                    )
                }
            task.params?.ref_task_id?.let { refId ->
                append(
                    refId,
                    NodeIncomingReference(task.id, "ref_task_id", "#${task.id} ref_task_id -> $refId")
                )
            }
        }
        return result
    }

    private fun terminalLabel(targetId: Int): String {
        return when (targetId) {
            -1 -> "END(-1)"
            -2 -> "FAIL(-2)"
            -3 -> "RESOURCE(-3)"
            -4 -> "COOLDOWN(-4)"
            0 -> "0"
            else -> targetId.toString()
        }
    }

    private fun formatFloat(value: Float): String {
        return if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
    }

    private fun formatNullableFloat(value: Float?): String {
        return value?.let(::formatFloat) ?: "null"
    }
}

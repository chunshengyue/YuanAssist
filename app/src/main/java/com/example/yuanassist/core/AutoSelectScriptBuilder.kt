package com.example.yuanassist.core

import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ROI
import com.example.yuanassist.model.TaskParams
import com.example.yuanassist.model.AgentAttr

object AutoSelectScriptBuilder {

    private val X_COLS = floatArrayOf(222f, 437f, 660f, 877f)
    private val Y_ROWS = floatArrayOf(597f, 707f, 922f, 1034f, 1224f)

    private fun getButtonCoordinates(buttonName: String): Pair<Float, Float>? {
        return when (buttonName) {
            "地" -> Pair(X_COLS[0], Y_ROWS[0])
            "水" -> Pair(X_COLS[1], Y_ROWS[0])
            "火" -> Pair(X_COLS[2], Y_ROWS[0])
            "风" -> Pair(X_COLS[3], Y_ROWS[0])

            "阳" -> Pair(X_COLS[0], Y_ROWS[1])
            "阴" -> Pair(X_COLS[1], Y_ROWS[1])

            "破军" -> Pair(X_COLS[0], Y_ROWS[2])
            "龙盾" -> Pair(X_COLS[1], Y_ROWS[2])
            "岐黄" -> Pair(X_COLS[2], Y_ROWS[2])
            "神纪" -> Pair(X_COLS[3], Y_ROWS[2])

            "诡道" -> Pair(X_COLS[0], Y_ROWS[3])

            "重置" -> Pair(X_COLS[1], Y_ROWS[4])
            "确定" -> Pair(X_COLS[2], Y_ROWS[4])
            else -> null
        }
    }

    /**
     * 动态生成自动选人的 JSON 剧本 (DailyTaskPlan)
     */
    fun buildPlan(selectedAgents: Array<String>, agentMap: Map<String, AgentAttr>): DailyTaskPlan {
        val tasks = mutableListOf<DailyTask>()
        var currentTaskId = 1

        fun addTask(action: String, delay: Long, params: TaskParams = TaskParams()): Int {
            val id = currentTaskId++
            tasks.add(
                DailyTask(
                    id = id,
                    action = action,
                    delay = delay,
                    params = params,
                    on_success = id + 1,
                    on_fail = -1
                )
            )
            return id
        }

        fun addDynamicButtonClick(buttonName: String, delay: Long) {
            val coords = getButtonCoordinates(buttonName)
            if (coords != null) {
                addTask(
                    "CLICK", delay,
                    TaskParams(x = coords.first, y = coords.second, align = "center")
                )
            }
        }

        for (i in 0 until 4) {
            addTask("CLICK", 500, TaskParams(x = 150f, y = 700f, align = "top"))
        }

        selectedAgents.forEachIndexed { index, agentName ->
            val attr = agentMap[agentName] ?: return@forEachIndexed

            addTask(
                "MATCH_TEMPLATE", 800, TaskParams(
                    template_name = "btn_filter.png",
                    threshold = 0.8f,
                    roi = ROI(align = "dynamic_filter_bounds")
                )
            )
            addTask("CLICK_LAST_MATCH", 500)

            if (index > 0) {
                addDynamicButtonClick("重置", 600)
            }

            addDynamicButtonClick(attr.element, 600)
            addDynamicButtonClick(attr.profession, 600)
            addDynamicButtonClick("确定", 800)

            addTask(
                "MATCH_TEMPLATE", 1200, TaskParams(
                    template_name = "${agentName}.png",
                    threshold = 0.8f,
                    roi = ROI(align = "dynamic_avatar_bounds")
                )
            )
            addTask("CLICK_LAST_MATCH", 800)

            if (index == 0) {
                addTask("CLICK", 800, TaskParams(x = 150f, y = 700f, align = "top"))
            }
        }

        addTask(
            "CLICK",
            800, TaskParams(
                x = 540f,
                y = 1762.2f,
                align = "absolute"
            )
        )

        if (tasks.isNotEmpty()) {
            val lastTask = tasks.last()
            tasks[tasks.lastIndex] = lastTask.copy(on_success = -1)
        }

        return DailyTaskPlan(start_task_id = 1, tasks = tasks)
    }
}
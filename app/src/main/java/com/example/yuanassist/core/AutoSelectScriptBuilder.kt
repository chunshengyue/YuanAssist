package com.example.yuanassist.core

import com.example.yuanassist.model.AgentAttr
import com.example.yuanassist.model.DailyTask
import com.example.yuanassist.model.DailyTaskPlan
import com.example.yuanassist.model.ROI
import com.example.yuanassist.model.TaskParams
import java.nio.charset.Charset

object AutoSelectScriptBuilder {

    private val xCols = floatArrayOf(222f, 437f, 660f, 877f)
    private val yRows = floatArrayOf(597f, 707f, 922f, 1034f, 1224f)
    private val westernCharset = Charset.forName("windows-1252")

    private const val LABEL_EARTH = "\u5730"
    private const val LABEL_WATER = "\u6c34"
    private const val LABEL_FIRE = "\u706b"
    private const val LABEL_WIND = "\u98ce"
    private const val LABEL_YANG = "\u9633"
    private const val LABEL_YIN = "\u9634"
    private const val LABEL_BREAK_ARMY = "\u7834\u519b"
    private const val LABEL_DRAGON_SHIELD = "\u9f99\u76fe"
    private const val LABEL_QI_HUANG = "\u5c90\u9ec4"
    private const val LABEL_SHEN_JI = "\u795e\u7eaa"
    private const val LABEL_GUI_DAO = "\u8be1\u9053"
    private const val LABEL_RESET = "\u91cd\u7f6e"
    private const val LABEL_CONFIRM = "\u786e\u5b9a"

    private val knownLabels = setOf(
        LABEL_EARTH,
        LABEL_WATER,
        LABEL_FIRE,
        LABEL_WIND,
        LABEL_YANG,
        LABEL_YIN,
        LABEL_BREAK_ARMY,
        LABEL_DRAGON_SHIELD,
        LABEL_QI_HUANG,
        LABEL_SHEN_JI,
        LABEL_GUI_DAO,
        LABEL_RESET,
        LABEL_CONFIRM
    )

    private fun repairLabelIfNeeded(value: String): String {
        val trimmed = value.trim()
        if (trimmed in knownLabels) return trimmed
        return runCatching {
            String(trimmed.toByteArray(westernCharset), Charsets.UTF_8)
        }.getOrDefault(trimmed)
    }

    private fun getButtonCoordinates(buttonName: String): Pair<Float, Float>? {
        return when (repairLabelIfNeeded(buttonName)) {
            LABEL_EARTH -> Pair(xCols[0], yRows[0])
            LABEL_WATER -> Pair(xCols[1], yRows[0])
            LABEL_FIRE -> Pair(xCols[2], yRows[0])
            LABEL_WIND -> Pair(xCols[3], yRows[0])

            LABEL_YANG -> Pair(xCols[0], yRows[1])
            LABEL_YIN -> Pair(xCols[1], yRows[1])

            LABEL_BREAK_ARMY -> Pair(xCols[0], yRows[2])
            LABEL_DRAGON_SHIELD -> Pair(xCols[1], yRows[2])
            LABEL_QI_HUANG -> Pair(xCols[2], yRows[2])
            LABEL_SHEN_JI -> Pair(xCols[3], yRows[2])

            LABEL_GUI_DAO -> Pair(xCols[0], yRows[3])

            LABEL_RESET -> Pair(xCols[1], yRows[4])
            LABEL_CONFIRM -> Pair(xCols[2], yRows[4])
            else -> null
        }
    }

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
            val coords = getButtonCoordinates(buttonName) ?: return
            addTask(
                action = "CLICK",
                delay = delay,
                params = TaskParams(x = coords.first, y = coords.second, align = "center")
            )
        }

        repeat(4) {
            addTask("CLICK", 500, TaskParams(x = 150f, y = 700f, align = "top"))
        }

        selectedAgents.forEachIndexed { index, agentName ->
            val attr = agentMap[agentName] ?: return@forEachIndexed

            addTask(
                "MATCH_TEMPLATE",
                800,
                TaskParams(
                    template_name = "btn_filter.png",
                    threshold = 0.8f,
                    roi = ROI(align = "dynamic_filter_bounds")
                )
            )
            addTask("CLICK_LAST_MATCH", 500)

            if (index > 0) {
                addDynamicButtonClick(LABEL_RESET, 600)
            }

            addDynamicButtonClick(attr.element, 600)
            addDynamicButtonClick(attr.profession, 600)
            addDynamicButtonClick(LABEL_CONFIRM, 800)

            addTask(
                "MATCH_TEMPLATE",
                1200,
                TaskParams(
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
            800,
            TaskParams(
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

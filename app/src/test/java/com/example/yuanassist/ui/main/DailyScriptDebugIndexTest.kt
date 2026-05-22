package com.example.yuanassist.ui.main

import com.example.yuanassist.model.DailyTaskPlan
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DailyScriptDebugIndexTest {

    private val gson = Gson()

    @Test
    fun indexesMatchTemplateNodesWithTemplateDisplayNames() {
        val plan = gson.fromJson(
            """
            {
              "display_name": "突发情况调试",
              "start_task_id": 1,
              "tasks": [
                {
                  "id": 2,
                  "name": "开始调查",
                  "action": "MATCH_TEMPLATE",
                  "delay": 1500,
                  "params": {
                    "template_name": "diaocha.png",
                    "threshold": 0.75,
                    "roi": {"x": 544, "y": 1012, "w": 200, "h": 300, "align": "top"}
                  },
                  "on_success": 3,
                  "on_fail": -2
                },
                {
                  "id": 3,
                  "action": "CLICK",
                  "delay": 100,
                  "params": {"x": 1, "y": 2}
                },
                {
                  "id": 4,
                  "action": "MATCH_TEMPLATE",
                  "delay": 800,
                  "params": {
                    "template_name": "diaocha.png",
                    "threshold": 0.85,
                    "roi": {"x": 540, "y": 1770, "w": 200, "h": 300, "align": "bottom"}
                  }
                }
              ]
            }
            """.trimIndent(),
            DailyTaskPlan::class.java,
        )

        val index = DailyScriptDebugIndex.fromPlan(
            scriptFileName = "tu_fa_qing_kuang.json",
            scriptDisplayName = "突发情况",
            plan = plan,
        )

        assertEquals(listOf("diaocha.png"), index.templateNames)
        assertEquals("突发情况调试", index.scriptDisplayName)
        assertEquals("开始调查", index.displayNameFor("diaocha.png"))
        assertEquals(2, index.nodesFor("diaocha.png").size)
        assertEquals(0.75f, index.thresholdFor("diaocha.png") ?: -1f, 0.001f)
        assertNotNull(index.nodesFor("diaocha.png").first().roi)
    }
}

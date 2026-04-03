package com.example.yuanassist.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object JobStationAssetRepository {

    private const val ASSET_DIR = "strategy"

    data class JobStationListItem(
        val assetFileName: String,
        val title: String,
        val summary: String,
        val stage: String
    )

    data class JobStationDetailData(
        val title: String,
        val summary: String,
        val stage: String,
        val roster: String,
        val sequence: String
    )

    fun loadList(context: Context): List<JobStationListItem> {
        return runCatching {
            context.assets.list(ASSET_DIR)
                ?.filter { it.endsWith(".json", ignoreCase = true) }
                ?.sorted()
                ?.mapNotNull { fileName ->
                    readJson(context, fileName)?.let { root ->
                        JobStationListItem(
                            assetFileName = fileName,
                            title = root.optJSONObject("doc")?.optString("title").orEmpty().ifBlank { fileName },
                            summary = buildSummary(root.optJSONObject("doc"), root),
                            stage = buildStageText(root.optJSONObject("level_meta"), root)
                        )
                    }
                }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    fun loadDetail(context: Context, assetFileName: String?): JobStationDetailData {
        return runCatching {
            val fileName = assetFileName
                ?.takeIf { it.isNotBlank() }
                ?: context.assets.list(ASSET_DIR)
                    ?.filter { it.endsWith(".json", ignoreCase = true) }
                    ?.sorted()
                    ?.firstOrNull()
                ?: return defaultDetailData()

            val root = readJson(context, fileName) ?: return defaultDetailData()
            JobStationDetailData(
                title = root.optJSONObject("doc")?.optString("title").orEmpty().ifBlank { "白鹄作业站" },
                summary = buildSummary(root.optJSONObject("doc"), root),
                stage = buildStageText(root.optJSONObject("level_meta"), root),
                roster = buildRosterText(root.optJSONArray("opers")),
                sequence = buildSequenceText(root.optJSONObject("actions"))
            )
        }.getOrElse {
            defaultDetailData()
        }
    }

    private fun readJson(context: Context, fileName: String): JSONObject? {
        return runCatching {
            val jsonText = context.assets.open("$ASSET_DIR/$fileName").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            }
            JSONObject(jsonText)
        }.getOrNull()
    }

    private fun buildSummary(doc: JSONObject?, root: JSONObject): String {
        val parts = buildList {
            doc?.optString("details")
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
            root.optJSONArray("tags")
                ?.let(::readStringArray)
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(" / ")
                ?.let { add("标签：$it") }
        }
        return parts.joinToString("\n\n").ifBlank {
            "这里放帖子正文摘要或作业说明。"
        }
    }

    private fun buildStageText(levelMeta: JSONObject?, root: JSONObject): String {
        if (levelMeta == null) return "关卡信息待补充"

        val lines = buildList {
            levelMeta.optString("name")
                .takeIf { it.isNotBlank() }
                ?.let { add("关卡：$it") }

            listOf(
                levelMeta.optString("cat_one"),
                levelMeta.optString("cat_two"),
                levelMeta.optString("cat_three")
            )
                .filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" / ")
                ?.let { add("分类：$it") }

            root.optString("stage_name")
                .takeIf { it.isNotBlank() }
                ?.let { add("作业标识：$it") }

            root.optString("level")
                .takeIf { it.isNotBlank() }
                ?.let { add("关卡编号：$it") }

            if (root.has("difficulty")) {
                add("难度：${root.optInt("difficulty")}")
            }
        }

        return lines.joinToString("\n").ifBlank { "关卡信息待补充" }
    }

    private fun buildRosterText(opers: JSONArray?): String {
        val names = opers?.let(::readOperatorNames).orEmpty()
        if (names.isEmpty()) return "阵容信息待补充"
        return names.mapIndexed { index, name -> "${index + 1}. $name" }.joinToString("\n")
    }

    private fun buildSequenceText(actions: JSONObject?): String {
        if (actions == null || actions.length() == 0) {
            return "动作序列待补充"
        }

        val lines = mutableListOf<String>()
        val keys = actions.keys()
        var index = 1
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "作业信息" || key == "抄作业自定义延时") continue

            val node = actions.optJSONObject(key)
            val title = node?.optString("text_doc").orEmpty().ifBlank { key }
            val focus = node?.optString("focus").orEmpty()
            val mode = when {
                node == null -> ""
                node.optString("action").isNotBlank() -> node.optString("action")
                node.optString("recognition").isNotBlank() -> node.optString("recognition")
                else -> "步骤"
            }

            val delay = buildList {
                val postDelay = node?.optLong("post_delay") ?: 0L
                val duration = node?.optLong("duration") ?: 0L
                if (postDelay > 0L) add("后置 ${postDelay}ms")
                if (duration > 0L) add("动作 ${duration}ms")
            }.joinToString(" · ")

            val headline = buildString {
                append(index.toString().padStart(2, '0'))
                append(". ")
                append(title)
                if (mode.isNotBlank()) append("  [$mode]")
            }
            lines += headline

            if (focus.isNotBlank()) {
                lines += "    $focus"
            }
            if (delay.isNotBlank()) {
                lines += "    $delay"
            }

            index++
        }

        return lines.joinToString("\n").ifBlank { "动作序列待补充" }
    }

    private fun readOperatorNames(opers: JSONArray): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until opers.length()) {
            val name = opers.optJSONObject(i)?.optString("name").orEmpty()
            if (name.isNotBlank()) result += name
        }
        return result
    }

    private fun readStringArray(array: JSONArray): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            array.optString(i).takeIf { it.isNotBlank() }?.let(result::add)
        }
        return result
    }

    private fun defaultDetailData(): JobStationDetailData {
        return JobStationDetailData(
            title = "白鹄作业站",
            summary = "这里放帖子正文摘要或作业说明，等待从 assets/strategy 里的 JSON 自动加载。",
            stage = "关卡信息待补充",
            roster = "阵容信息待补充",
            sequence = "动作序列待补充"
        )
    }
}

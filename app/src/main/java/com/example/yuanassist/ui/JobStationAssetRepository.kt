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
        val tags: List<String>,
        val roster: List<String>,
        val author: String = "作者",
        val publishTime: String = "1天前"
    )

    data class JobStationDetailData(
        val title: String,
        val summary: String,
        val stageTags: List<String>,
        val roster: List<OperData>,
        val turns: List<TurnData>,
        val author: String = "作者",
        val sourceType: String = "搬运",
        val originalAuthor: String = "一条桂鱼",
        val originalPlatform: String = "小红书",
        val originalLink: String = "https://www.xiaohongshu.com/",
        val likeCount: String = "1.2w",
        val readCount: String = "10w+"
    )

    data class OperData(
        val name: String,
        val starLevel: Int,
        val hp: Int,
        val attack: Int,
        val discs: List<Int>
    )

    data class TurnData(
        val turnNum: Int,
        val actionCount: Int,
        val slotActions: Map<Int, List<ActionChip>>
    )

    enum class ActionType { ATTACK, ULT, DEFEND, OTHER }

    data class ActionChip(
        val globalOrder: Int,
        val type: ActionType,
        val label: String,
        val actionParamMs: Long? = null
    )

    fun loadList(context: Context): List<JobStationListItem> {
        return runCatching {
            context.assets.list(ASSET_DIR)
                ?.filter { it.endsWith(".json", ignoreCase = true) }
                ?.sorted()
                ?.mapNotNull { fileName ->
                    readJson(context, fileName)?.let { root ->
                        val docTitle = root.optJSONObject("doc")?.optString("title").orEmpty()

                        JobStationListItem(
                            assetFileName = fileName,
                            title = docTitle.ifBlank { fileName.removeSuffix(".json") },
                            tags = buildList {
                                root.optJSONArray("tags")
                                    ?.let(::readStringArray)
                                    ?.filter { it == "如鸢" || it == "代号鸢" }
                                    ?.forEach(::add)

                                root.optJSONObject("level_meta")?.let { levelMeta ->
                                    listOf(
                                        levelMeta.optString("cat_one"),
                                        levelMeta.optString("cat_two")
                                    ).filter { it.isNotBlank() }
                                        .forEach(::add)
                                }
                            }.distinct(),
                            roster = buildList {
                                root.optJSONArray("opers")?.let { array ->
                                    for (i in 0 until array.length()) {
                                        add(array.optJSONObject(i)?.optString("name").orEmpty())
                                    }
                                }
                            }
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
                stageTags = buildStageTags(root.optJSONObject("level_meta"), root),
                roster = parseOpers(root.optJSONArray("opers")),
                turns = parseTurns(root.optJSONObject("actions")),
                originalLink = root.optJSONObject("doc")
                    ?.optString("details")
                    ?.let(::extractUrl)
                    .orEmpty()
            )
        }.getOrElse {
            defaultDetailData()
        }
    }

    private fun parseTurns(actions: JSONObject?): List<TurnData> {
        if (actions == null) return emptyList()

        val turnMap = mutableMapOf<Int, MutableList<Pair<Int, ActionChip>>>()
        val turnActionRegex = Regex("""回合(\d+)行动(\d+)""")
        val keys = actions.keys().asSequence().toList().sortedWith(
            compareBy<String>(
                { key -> turnActionRegex.find(key)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE },
                { key -> turnActionRegex.find(key)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: Int.MAX_VALUE }
            )
        )

        for (key in keys) {
            val node = actions.optJSONObject(key) ?: continue
            val textDoc = node.optString("text_doc").ifBlank { node.optString("focus") }

            val turnMatch = turnActionRegex.find(key) ?: continue
            val turnNum = turnMatch.groupValues[1].toIntOrNull() ?: continue
            val actionOrder = turnMatch.groupValues[2].toIntOrNull() ?: continue

            val actionMatch = Regex("""(\d)(普|大|下)""").find(textDoc)
            val (slot, type, label) = if (actionMatch != null) {
                val slotNum = actionMatch.groupValues[1].toInt()
                val actionType = when (actionMatch.groupValues[2]) {
                    "普" -> ActionType.ATTACK
                    "大" -> ActionType.ULT
                    "下" -> ActionType.DEFEND
                    else -> ActionType.OTHER
                }
                val shortLabel = buildString {
                    append(actionOrder)
                    append(
                        when (actionType) {
                            ActionType.ATTACK -> "A"
                            ActionType.ULT -> "↑"
                            ActionType.DEFEND -> "↓"
                            ActionType.OTHER -> ""
                        }
                    )
                }
                Triple(slotNum, actionType, shortLabel)
            } else {
                Triple(
                    0,
                    ActionType.OTHER,
                    buildOtherActionLabel(node, textDoc, actionOrder)
                )
            }

            val chip = ActionChip(
                globalOrder = actionOrder,
                type = type,
                label = label,
                actionParamMs = extractActionParamMs(node)
            )
            turnMap.getOrPut(turnNum) { mutableListOf() }.add(slot to chip)
        }

        return turnMap.map { (turnNum, actionList) ->
            TurnData(
                turnNum = turnNum,
                actionCount = actionList.size,
                slotActions = actionList.groupBy({ it.first }, { it.second })
            )
        }.sortedBy { it.turnNum }
    }

    private fun extractUrl(details: String): String {
        return Regex("""https?://\S+""").find(details)?.value.orEmpty()
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
        return doc?.optString("details")
            ?.takeIf { it.isNotBlank() }
            ?: "这里放帖子正文摘要或作业说明。"
    }

    private fun buildOtherActionLabel(node: JSONObject, textDoc: String, actionOrder: Int): String {
        val baseText = textDoc.ifBlank { "动作$actionOrder" }
        return if (baseText.contains("等待")) "等待" else baseText
    }

    private fun extractActionParamMs(node: JSONObject): Long? {
        val postDelay = node.optLong("post_delay")
        if (postDelay > 0L) return postDelay

        val duration = node.optLong("duration")
        if (duration > 0L) return duration

        val timeout = node.optLong("timeout")
        if (timeout > 0L) return timeout

        return null
    }

    private fun buildStageTags(levelMeta: JSONObject?, root: JSONObject): List<String> {
        return buildList {
            root.optJSONArray("tags")
                ?.let(::readStringArray)
                ?.filter { it == "如鸢" || it == "代号鸢" }
                ?.forEach(::add)

            listOf(
                levelMeta?.optString("cat_one").orEmpty(),
                levelMeta?.optString("cat_two").orEmpty()
            )
                .filter { it.isNotBlank() }
                .forEach(::add)
        }.distinct().ifEmpty { listOf("关卡信息待补充") }
    }

    private fun readOperatorNames(opers: JSONArray): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until opers.length()) {
            val name = opers.optJSONObject(i)?.optString("name").orEmpty()
            if (name.isNotBlank()) result += name
        }
        return result
    }

    private fun parseOpers(opers: JSONArray?): List<OperData> {
        if (opers == null) return emptyList()

        val result = mutableListOf<OperData>()
        for (i in 0 until opers.length()) {
            val obj = opers.optJSONObject(i) ?: continue

            val discs = mutableListOf<Int>()
            val discsArray = obj.optJSONArray("discs_selected")
            if (discsArray != null) {
                for (j in 0 until discsArray.length()) {
                    discs += discsArray.optInt(j)
                }
            }

            result += OperData(
                name = obj.optString("name"),
                starLevel = obj.optInt("star_level", 0),
                hp = obj.optInt("hp", 0),
                attack = obj.optInt("attack", 0),
                discs = discs
            )
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
            title = "未知作业",
            summary = "暂无说明",
            stageTags = listOf("未知关卡"),
            roster = emptyList(),
            turns = emptyList()
        )
    }
}

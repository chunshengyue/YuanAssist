package com.example.yuanassist.ui

import android.content.Context
import com.example.yuanassist.model.AgentRepository
import com.example.yuanassist.model.BattleStageTarget
import com.example.yuanassist.model.InstructionJson
import com.example.yuanassist.model.InstructionType
import com.example.yuanassist.model.STRATEGY_GAME_DAIHAOYUAN
import com.example.yuanassist.model.STRATEGY_GAME_RUYUAN
import com.example.yuanassist.model.formatStageAutoNavDisplay
import com.example.yuanassist.model.toDisplaySummary
import com.example.yuanassist.model.strategy_detail
import com.example.yuanassist.utils.RunLogger
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max

object JobStationAssetRepository {

    private const val ASSET_DIR = "strategy"
    private const val DEFAULT_ATTACK_DELAY_MS = 2500L
    private const val DEFAULT_SKILL_DELAY_MS = 4000L
    private const val DEFAULT_WAIT_TURN_MS = 8000L
    private val DETAIL_HIGHLIGHT_INSTRUCTION_TYPES = setOf(
        InstructionType.ALL_WIPE_CHECK,
        InstructionType.DEATH_CHECK,
        InstructionType.ORANGE_STAR_CHECK
    )
    @Volatile
    private var maaOperatorDiscCache: Map<String, List<OperatorDiscMeta>>? = null

    enum class JobStationListItemType {
        MAA,
        BMOB
    }

    data class JobStationListItem(
        val type: JobStationListItemType = JobStationListItemType.MAA,
        val copilotId: Long? = null,
        val strategyId: String? = null,
        val assetFileName: String,
        val title: String,
        val tags: List<String>,
        val roster: List<String>,
        val author: String = "作者",
        val publishTime: String = "1天前",
        val publishTimestamp: Long = 0L,
        val hotScore: Long = 0L,
        val gameTag: String = "",
        val categoryTag: String = "",
        val coverUrl: String = "",
        val authorAvatarUrl: String = "",
        val agentsText: String = ""
    )

    fun fromRemoteListItem(copilot: JobStationApiModels.CopilotInfo): JobStationListItem {
        val contentRoot = parseContentRoot(copilot.content)
        val publishTimestamp = parsePublishTimestamp(copilot.uploadTime)
        val title = contentRoot?.optJSONObject("doc")?.optString("title")
            .orEmpty()
            .ifBlank { copilot.name.orEmpty() }
            .ifBlank { "作业 ${copilot.id}" }

        val tags = buildList {
            resolveGameTagFromStrings(copilot.tags.orEmpty())
                .takeIf { it.isNotBlank() }
                ?.let(::add)
            copilot.catOne
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
            add("MaaYuanShare")
        }.distinct()

        val roster = contentRoot?.optJSONArray("opers")
            ?.let(::readOperatorNames)
            .orEmpty()

        return JobStationListItem(
            type = JobStationListItemType.MAA,
            copilotId = copilot.id,
            assetFileName = "",
            title = title,
            tags = tags,
            roster = roster,
            author = copilot.uploader.ifBlank { "作者" },
            publishTime = formatRelativeTime(publishTimestamp, formatRemoteTime(copilot.uploadTime)),
            publishTimestamp = publishTimestamp,
            hotScore = copilot.views,
            gameTag = resolveGameTagFromStrings(copilot.tags.orEmpty()),
            categoryTag = copilot.catOne.orEmpty()
        )
    }

    fun fromBmobListItem(detail: strategy_detail): JobStationListItem {
        val roster = parseBmobRoster(detail)
        val title = detail.title.ifBlank { "未命名攻略" }
        val publishTimestamp = parsePublishTimestamp(detail.createdAt)
        return JobStationListItem(
            type = JobStationListItemType.BMOB,
            strategyId = detail.objectId,
            assetFileName = "",
            title = title,
            tags = buildBmobTags(detail, title),
            roster = roster,
            author = detail.author?.nickname?.takeIf { it.isNotBlank() }
                ?: detail.author?.username?.takeIf { it.isNotBlank() }
                ?: "热心玩家",
            publishTime = formatRelativeTime(publishTimestamp, formatBmobTime(detail.createdAt)),
            publishTimestamp = publishTimestamp,
            hotScore = detail.viewCount?.toLong() ?: 0L,
            gameTag = resolveGameTagFromRuyuan(detail.ruyuan).ifBlank { resolveGameTagFromTitle(title) },
            categoryTag = resolveCategoryTagFromTitle(title),
            coverUrl = detail.coverUrl.orEmpty(),
            authorAvatarUrl = detail.author?.avatarUrl.orEmpty(),
            agentsText = buildBmobAgentsText(detail, roster)
        )
    }

    fun fromRemoteDetailData(copilot: JobStationApiModels.CopilotInfo): JobStationDetailData {
        val contentRoot = parseContentRoot(copilot.content)
        val metadata = copilot.metadata
        val title = contentRoot?.optJSONObject("doc")?.optString("title")
            .orEmpty()
            .ifBlank { copilot.name.orEmpty() }
            .ifBlank { "作业 ${copilot.id}" }
        val summary = buildRemoteSummary(contentRoot)
        val stageTags = buildRemoteStageTags(copilot)
        val roster = parseOpers(contentRoot?.optJSONArray("opers"))
        val turns = parseTurns(contentRoot?.optJSONObject("actions"))
        val importPayload = buildImportPayload(contentRoot)

        RunLogger.i(
            "MaaYuan详情解析 id=${copilot.id} contentJson=${contentRoot != null} title=${title.take(40)} summaryLen=${summary.length} tags=${stageTags.size} roster=${roster.size} turns=${turns.size} importable=${importPayload != null}"
        )
        if (contentRoot == null) {
            RunLogger.e(
                "MaaYuan详情content解析失败 id=${copilot.id} contentSnippet=${copilot.content.take(240)}"
            )
        }

        return JobStationDetailData(
            title = title,
            summary = summary,
            stageTags = stageTags,
            roster = roster,
            turns = turns,
            author = copilot.uploader.ifBlank { "作者" },
            sourceType = formatSourceType(metadata?.sourceType),
            originalAuthor = metadata?.repostAuthor.orEmpty(),
            originalPlatform = metadata?.repostPlatform.orEmpty(),
            originalLink = metadata?.repostUrl.orEmpty(),
            likeCount = formatMetric(copilot.like),
            readCount = formatMetric(copilot.views),
            importPayload = importPayload,
            isFromMaaYuan = true
        )
    }

    fun fromBmobDetailData(detail: strategy_detail): JobStationDetailData {
        val title = detail.title.ifBlank { "未命名攻略" }
        return JobStationDetailData(
            title = title,
            summary = detail.content.orEmpty(),
            stageTags = buildBmobTags(detail, title),
            roster = parseBmobOperData(detail),
            turns = parseBmobTurns(detail.scriptContent, detail.instructions),
            author = resolveBmobAuthorName(detail),
            authorAvatarUrl = detail.author?.avatarUrl.orEmpty(),
            sourceType = "",
            originalAuthor = "",
            originalPlatform = "",
            originalLink = detail.originalPostUrl.orEmpty(),
            likeCount = formatMetric((detail.favoriteCount ?: 0).toLong()),
            readCount = formatMetric((detail.viewCount ?: 0).toLong()),
            importPayload = buildBmobImportPayload(detail),
            strategyImageUrl = detail.strategyImage.orEmpty(),
            agentImageUrl = detail.agentImageUrl.orEmpty()
        )
    }

    data class JobStationDetailData(
        val title: String,
        val summary: String,
        val stageTags: List<String>,
        val roster: List<OperData>,
        val turns: List<TurnData>,
        val author: String = "作者",
        val authorAvatarUrl: String = "",
        val sourceType: String = "搬运",
        val originalAuthor: String = "一条桂鱼",
        val originalPlatform: String = "小红书",
        val originalLink: String = "https://www.xiaohongshu.com/",
        val likeCount: String = "1.2w",
        val readCount: String = "10w+",
        val importPayload: JobStationImportPayload? = null,
        val isFromMaaYuan: Boolean = false,
        val strategyImageUrl: String = "",
        val agentImageUrl: String = ""
    )

    data class JobStationImportPayload(
        val scriptContent: String,
        val configJson: String,
        val instructionsJson: String,
        val agentsJson: String,
        val notice: String
    )

    data class OperData(
        val name: String,
        val starLevel: Int,
        val hp: Int,
        val attack: Int,
        val discs: List<Int>
    )

    data class DiscDisplaySpec(
        val displayName: String,
        val color: String,
        val forbidden: Boolean
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

    private data class OperatorDiscMeta(
        val otName: String,
        val abbreviation: String,
        val color: String
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
                            type = JobStationListItemType.MAA,
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
                            },
                            publishTimestamp = 0L
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

    private fun parseBmobTurns(
        scriptContent: String?,
        instructionsJson: String?
    ): List<TurnData> {
        val turnMap = linkedMapOf<Int, MutableList<Pair<Int, ActionChip>>>()

        parseBmobScriptRows(scriptContent).forEach { (turnNum, actions) ->
            val turnItems = turnMap.getOrPut(turnNum) { mutableListOf() }
            actions.forEachIndexed { index, actionText ->
                parseBmobActionLabels(actionText).forEach { label ->
                    turnItems += (index + 1) to ActionChip(
                        globalOrder = extractActionOrder(label, index + 1),
                        type = resolveActionType(label),
                        label = label
                    )
                }
            }
        }

        parseInstructionChips(instructionsJson).forEach { (turnNum, chips) ->
            val turnItems = turnMap.getOrPut(turnNum) { mutableListOf() }
            chips.forEach { chip ->
                turnItems += 0 to chip
            }
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

    private fun parseContentRoot(content: String): JSONObject? {
        return runCatching {
            JSONObject(content)
        }.getOrNull()
    }

    private fun buildImportPayload(root: JSONObject?): JobStationImportPayload? {
        if (root == null) return null

        val actions = root.optJSONObject("actions") ?: return null
        val turnRows = linkedMapOf<Int, Array<StringBuilder>>()
        val instructions = mutableListOf<InstructionJson>()
        val stepActionRegex = Regex("""^回合(\d+)行动(\d+)$""")
        val orangeDetectionRegex = Regex("""第(\d+)回合橙星检测""")
        val deathDetectionRegex = Regex("""([1-5])号位阵亡检测""")
        val importedTurnStartInstructions = mutableSetOf<String>()
        var hasAllWipeRestart = false

        val customDelayNode = actions.optJSONObject("抄作业自定义延时")
        val attackDelay = customDelayNode?.optString("attack_delay")
            ?.toLongOrNull()
            ?: DEFAULT_ATTACK_DELAY_MS
        val defenseDelay = customDelayNode?.optString("defense_delay")
            ?.toLongOrNull()
            ?: attackDelay
        val skillDelay = customDelayNode?.optString("ult_delay")
            ?.toLongOrNull()
            ?: DEFAULT_SKILL_DELAY_MS
        val sharedActionDelay = maxOf(attackDelay, defenseDelay)
        val stageAutoNavTarget = resolveStageAutoNavTarget(root.optJSONObject("level_meta"))

        val sortedKeys = actions.keys().asSequence().toList().sortedWith(
            compareBy<String>(
                { key -> stepActionRegex.matchEntire(key)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE },
                { key -> stepActionRegex.matchEntire(key)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: Int.MAX_VALUE }
            )
        )

        val lastActionStepByTurn = mutableMapOf<Int, Int>()

        sortedKeys.forEach { key ->
            val node = actions.optJSONObject(key) ?: return@forEach
            val textDoc = node.optString("text_doc").ifBlank { node.optString("focus") }
            val nextTargets = node.optJSONArray("next")?.let(::readStringArray).orEmpty()

            if (
                key.contains("全灭重开") ||
                textDoc.contains("全灭重开") ||
                nextTargets.any { it.contains("全灭重开") }
            ) {
                hasAllWipeRestart = true
            }

            val orangeTurn = orangeDetectionRegex.find(key)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: orangeDetectionRegex.find(textDoc)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (orangeTurn != null) {
                val dedupeKey = "orange_$orangeTurn"
                if (importedTurnStartInstructions.add(dedupeKey)) {
                    instructions += InstructionJson(
                        turn = orangeTurn,
                        step = 0,
                        type = InstructionType.ORANGE_STAR_CHECK.name,
                        value = 0L
                    )
                }
                return@forEach
            }

            val match = stepActionRegex.matchEntire(key) ?: return@forEach
            val turn = match.groupValues[1].toIntOrNull() ?: return@forEach
            val step = match.groupValues[2].toIntOrNull() ?: return@forEach

            val deathSlot = deathDetectionRegex.find(textDoc)?.groupValues?.getOrNull(1)?.toLongOrNull()
            if (deathSlot != null) {
                val dedupeKey = "death_${turn}_$deathSlot"
                if (importedTurnStartInstructions.add(dedupeKey)) {
                    instructions += InstructionJson(
                        turn = turn,
                        step = 0,
                        type = InstructionType.DEATH_CHECK.name,
                        value = deathSlot
                    )
                }
                return@forEach
            }

            val mapped = mapRemoteAction(textDoc)
            val targetSwitchType = mapTargetSwitchInstructionType(textDoc)

            when {
                targetSwitchType != null -> {
                    instructions += InstructionJson(
                        turn = turn,
                        step = lastActionStepByTurn[turn] ?: 0,
                        type = targetSwitchType.name,
                        value = 1L
                    )
                }

                mapped != null -> {
                    val row = turnRows.getOrPut(turn) { Array(5) { StringBuilder() } }
                    val cell = row[mapped.slotIndex]
                    cell.append(step).append(mapped.command)
                    lastActionStepByTurn[turn] = step

                    val importedBaseDelay = when (mapped.command) {
                        "↑" -> skillDelay
                        else -> sharedActionDelay
                    }
                    val rawActionDelay = resolveActionDelay(node)
                    val actionDelay = when {
                        mapped.command == "↑" -> rawActionDelay.takeIf { it > 0L } ?: skillDelay
                        rawActionDelay <= 0L -> sharedActionDelay
                        rawActionDelay == attackDelay || rawActionDelay == defenseDelay -> sharedActionDelay
                        else -> rawActionDelay
                    }
                    val extraDelay = actionDelay - importedBaseDelay
                    if (extraDelay > 0L) {
                        instructions += InstructionJson(
                            turn = turn,
                            step = step,
                            type = InstructionType.DELAY_ADD.name,
                            value = extraDelay
                        )
                    } else if (extraDelay < 0L) {
                        instructions += InstructionJson(
                            turn = turn,
                            step = step,
                            type = InstructionType.DELAY_SUBTRACT.name,
                            value = -extraDelay
                        )
                    }
                }

                isWaitAction(textDoc) -> {
                    val waitDelay = resolveActionDelay(node)
                    if (waitDelay > 0L) {
                        instructions += InstructionJson(
                            turn = turn,
                            step = lastActionStepByTurn[turn] ?: 0,
                            type = InstructionType.DELAY_ADD.name,
                            value = waitDelay
                        )
                    }
                }
            }
        }

        if (turnRows.isEmpty()) return null

        stageAutoNavTarget?.let { target ->
            if (importedTurnStartInstructions.add("stage_nav")) {
                instructions += InstructionJson(
                    turn = 1,
                    step = 0,
                    type = InstructionType.STAGE_AUTO_NAV.name,
                    value = target.code
                )
            }
        }

        if (hasAllWipeRestart) {
            turnRows.keys.sorted().forEach { turn ->
                val dedupeKey = "wipe_$turn"
                if (importedTurnStartInstructions.add(dedupeKey)) {
                    instructions += InstructionJson(
                        turn = turn,
                        step = 0,
                        type = InstructionType.ALL_WIPE_CHECK.name,
                        value = 0L
                    )
                }
            }
        }

        val scriptContent = buildString {
            turnRows.toSortedMap().forEach { (turn, columns) ->
                append(turn).append("回合")
                columns.forEach { builder ->
                    append('\t')
                    append(builder.toString().ifBlank { "-" })
                }
                append('\n')
            }
        }.trimEnd()

        val agentNames = root.optJSONArray("opers")
            ?.let(::readOperatorNames)
            .orEmpty()

        val configJson = Gson().toJson(
            mapOf(
                "intervalAttack" to sharedActionDelay,
                "intervalSkill" to skillDelay,
                "waitTurn" to DEFAULT_WAIT_TURN_MS
            )
        )

        return JobStationImportPayload(
            scriptContent = scriptContent,
            configJson = configJson,
            instructionsJson = Gson().toJson(instructions),
            agentsJson = Gson().toJson(agentNames),
            notice = "导入成功"
        )
    }

    private fun resolveStageAutoNavTarget(levelMeta: JSONObject?): BattleStageTarget? {
        if (levelMeta == null) return null

        val catOne = levelMeta.optString("cat_one").trim()
        val catThree = levelMeta.optString("cat_three").trim()

        return when {
            catOne == "白鹄" -> BattleStageTarget.BAI_HU
            catOne == "洞窟" && catThree.contains("左") -> BattleStageTarget.DONG_KU_LEFT
            catOne == "洞窟" && catThree.contains("右") -> BattleStageTarget.DONG_KU_RIGHT
            catOne == "地宫" && catThree == "遗迹一" -> BattleStageTarget.YI_JI_ONE
            catOne == "地宫" && catThree == "遗迹二" -> BattleStageTarget.YI_JI_TWO
            catOne == "地宫" && catThree == "遗迹三" -> BattleStageTarget.YI_JI_THREE
            catOne == "地宫" && catThree == "遗迹四" -> BattleStageTarget.YI_JI_FOUR
            else -> null
        }
    }

    private data class MappedRemoteAction(
        val slotIndex: Int,
        val command: String
    )

    private fun mapRemoteAction(textDoc: String): MappedRemoteAction? {
        if (textDoc.isBlank()) return null

        val normalized = textDoc.replace("号位", "").replace("行动:", "").trim()
        val basicMatch = Regex("""([1-5])\s*(普|大|下)""").find(normalized)
        if (basicMatch != null) {
            val slot = basicMatch.groupValues[1].toIntOrNull()?.minus(1) ?: return null
            val command = when (basicMatch.groupValues[2]) {
                "普" -> "A"
                "大" -> "↑"
                "下" -> "↓"
                else -> return null
            }
            return MappedRemoteAction(slot, command)
        }

        val spMatch = Regex("""([1-5])\s*(SP|sp|圈)""").find(normalized)
        if (spMatch != null) {
            val slot = spMatch.groupValues[1].toIntOrNull()?.minus(1) ?: return null
            return MappedRemoteAction(slot, "圈")
        }

        return null
    }

    private fun mapTargetSwitchInstructionType(textDoc: String): InstructionType? {
        if (textDoc.isBlank()) return null

        return when {
            textDoc.contains("左侧目标") || textDoc.contains("切换至左侧目标") || textDoc.contains("左目标") ->
                InstructionType.TARGET_SWITCH_LEFT

            textDoc.contains("右侧目标") || textDoc.contains("切换至右侧目标") || textDoc.contains("右目标") ->
                InstructionType.TARGET_SWITCH_RIGHT

            else -> null
        }
    }

    private fun isWaitAction(textDoc: String): Boolean {
        return textDoc.contains("等待")
    }

    private fun resolveActionDelay(node: JSONObject): Long {
        val postDelay = node.optLong("post_delay")
        if (postDelay > 0L) return postDelay

        val duration = node.optLong("duration")
        if (duration > 0L) return duration

        val timeout = node.optLong("timeout")
        if (timeout > 0L) return timeout

        return 0L
    }

    private fun parseBmobScriptRows(scriptContent: String?): List<Pair<Int, List<String>>> {
        val normalized = scriptContent
            ?.replace("\\n", "\n")
            ?.replace("\\t", "\t")
            ?.trim()
            .orEmpty()
        if (normalized.isBlank()) return emptyList()

        val result = mutableListOf<Pair<Int, List<String>>>()
        normalized.lines().forEachIndexed { index, line ->
            val rawLine = line.trimEnd('\r')
            if (rawLine.isBlank()) return@forEachIndexed

            val parts = if (rawLine.contains("\t")) {
                rawLine.split("\t")
            } else {
                rawLine.trim().split(Regex("\\s+"))
            }

            val turnNum = parts.firstOrNull()
                ?.filter { it.isDigit() }
                ?.toIntOrNull()
                ?: (index + 1)
            val startIndex = if (parts.firstOrNull()?.contains("回") == true || parts.firstOrNull()?.all { it.isDigit() } == true) {
                1
            } else {
                0
            }

            val actions = buildList {
                for (i in startIndex until parts.size) {
                    if (size >= 5) break
                    add(parts[i].trim())
                }
                while (size < 5) {
                    add("")
                }
            }
            result += turnNum to actions
        }

        return result
    }

    private fun parseBmobActionLabels(actionText: String): List<String> {
        val normalized = actionText.trim()
        if (normalized.isBlank() || normalized == "-") return emptyList()
        val matches = Regex("""\d+(?:A|↑|↓|圈)""").findAll(normalized).map { it.value }.toList()
        return if (matches.isNotEmpty()) matches else listOf(normalized)
    }

    private fun extractActionOrder(label: String, fallback: Int): Int {
        return Regex("""^(\d+)""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: fallback
    }

    private fun resolveActionType(label: String): ActionType {
        return when {
            label.endsWith("↑") -> ActionType.ULT
            label.endsWith("↓") -> ActionType.DEFEND
            label.endsWith("A") -> ActionType.ATTACK
            else -> ActionType.OTHER
        }
    }

    fun parseHighlightInstructionChips(instructionsJson: String?): Map<Int, List<ActionChip>> {
        return parseInstructionChips(instructionsJson, DETAIL_HIGHLIGHT_INSTRUCTION_TYPES)
    }

    private fun parseInstructionChips(
        instructionsJson: String?,
        allowedTypes: Set<InstructionType>? = null
    ): Map<Int, List<ActionChip>> {
        if (instructionsJson.isNullOrBlank()) return emptyMap()

        val instructions = runCatching {
            Gson().fromJson(instructionsJson, Array<InstructionJson>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())

        return instructions
            .map { it.normalized() }
            .groupBy { it.turn }
            .mapValues { (_, list) ->
                list.sortedWith(compareBy<InstructionJson> { it.step }.thenBy { it.type }).mapNotNull { instruction ->
                    val type = runCatching { InstructionType.valueOf(instruction.type) }.getOrNull()
                    if (type != null && allowedTypes != null && type !in allowedTypes) {
                        return@mapNotNull null
                    }
                    ActionChip(
                        globalOrder = instruction.step,
                        type = ActionType.OTHER,
                        label = formatInstructionChipLabel(type, instruction),
                        actionParamMs = when (type) {
                            InstructionType.DELAY_ADD,
                            InstructionType.DELAY_SUBTRACT -> instruction.value
                            else -> null
                        }
                    )
                }
            }
    }

    private fun formatInstructionChipLabel(
        type: InstructionType?,
        instruction: InstructionJson
    ): String {
        if (type == null) return instruction.type

        return when (type) {
            InstructionType.ALL_WIPE_CHECK -> "全灭检测"
            InstructionType.DEATH_CHECK -> "阵亡检测 · 第${instruction.value}人"
            InstructionType.ORANGE_STAR_CHECK -> "橙星检测"
            InstructionType.TARGET_SWITCH_LEFT -> "切换左侧目标"
            InstructionType.TARGET_SWITCH,
            InstructionType.TARGET_SWITCH_RIGHT -> "切换右侧目标"
            InstructionType.STAGE_AUTO_NAV -> "关卡自动导航 · ${formatStageAutoNavDisplay(instruction.value)}"
            InstructionType.DELAY_ADD -> "增加延时"
            InstructionType.DELAY_SUBTRACT -> "缩短延时"
            InstructionType.PAUSE -> "执行暂停"
        }
    }

    private fun buildSummary(doc: JSONObject?, root: JSONObject): String {
        return doc?.optString("details")
            ?.takeIf { it.isNotBlank() }
            ?: "这里放帖子正文摘要或作业说明。"
    }

    private fun buildRemoteSummary(root: JSONObject?): String {
        return root?.optJSONObject("doc")
            ?.optString("details")
            ?.takeIf { it.isNotBlank() }
            ?: ""
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

    private fun buildRemoteStageTags(copilot: JobStationApiModels.CopilotInfo): List<String> {
        return buildList {
            copilot.tags
                ?.filter { it == "如鸢" || it == "代号鸢" }
                ?.forEach(::add)

            listOf(copilot.catOne, copilot.catTwo)
                .filterNotNull()
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

    private fun parseBmobOperData(detail: strategy_detail): List<OperData> {
        val parsedEntries = parseBmobAgentEntries(detail.agentSelection)
        if (parsedEntries.isNotEmpty()) {
            return parsedEntries.map { entry ->
                OperData(
                    name = entry.name,
                    starLevel = entry.starLevel,
                    hp = 0,
                    attack = 0,
                    discs = entry.discs
                )
            }
        }

        return parseBmobRoster(detail).map { name ->
            OperData(
                name = name,
                starLevel = 0,
                hp = 0,
                attack = 0,
                discs = emptyList()
            )
        }
    }

    private data class BmobAgentEntry(
        val name: String,
        val starLevel: Int,
        val discs: List<Int>
    )

    private fun parseBmobAgentEntries(agentSelectionJson: String?): List<BmobAgentEntry> {
        if (agentSelectionJson.isNullOrBlank()) return emptyList()

        val rawAgents = runCatching {
            Gson().fromJson(agentSelectionJson, Array<String>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())

        return rawAgents.mapNotNull(::parseBmobAgentEntry)
    }

    private fun parseBmobAgentEntry(raw: String?): BmobAgentEntry? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val parts = value.split("-", limit = 2)
        val rawName = parts[0].trim()
        val talents = parts.getOrNull(1)
            ?.split("、", "，", ",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            .orEmpty()

        var displayName = rawName.substringBefore(" ").trim()
        var starLevel = 0
        val prefixMatch = Regex("""^(\d+)(.*)$""").find(displayName)
        if (prefixMatch != null) {
            starLevel = prefixMatch.groupValues[1].toIntOrNull() ?: 0
            displayName = prefixMatch.groupValues[2].trim()
        } else if (rawName.contains(" ")) {
            val suffix = rawName.substringAfter(" ").trim()
            starLevel = when {
                suffix == "觉醒" -> 6
                suffix.contains("★") || suffix.contains("☆") -> suffix.count { it == '★' || it == '☆' }
                else -> 0
            }
        }

        return displayName.takeIf { it.isNotBlank() }?.let {
            BmobAgentEntry(
                name = it,
                starLevel = starLevel,
                discs = talents
            )
        }
    }

    fun resolveMaaDiscDisplaySpec(
        context: Context,
        agentName: String,
        discId: Int
    ): DiscDisplaySpec {
        val forbidden = discId < 0
        val normalizedDiscId = abs(discId)
        if (normalizedDiscId <= 0) {
            return DiscDisplaySpec(
                displayName = "命盘",
                color = "",
                forbidden = forbidden
            )
        }

        val discMeta = getMaaOperatorDiscMap(context)[normalizeOperatorName(agentName)]
            ?.getOrNull(normalizedDiscId - 1)
        val displayName = discMeta?.abbreviation
            ?.takeIf { it.isNotBlank() }
            ?: discMeta?.otName
                ?.takeIf { it.isNotBlank() }
            ?: AgentRepository.AGENT_MAP[normalizeOperatorName(agentName)]
                ?.talents
                ?.get(normalizedDiscId)
                ?.removePrefix("橙")
                ?.removePrefix("紫")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: "命盘$normalizedDiscId"

        return DiscDisplaySpec(
            displayName = displayName,
            color = discMeta?.color.orEmpty(),
            forbidden = forbidden
        )
    }

    private fun getMaaOperatorDiscMap(context: Context): Map<String, List<OperatorDiscMeta>> {
        maaOperatorDiscCache?.let { return it }

        return synchronized(this) {
            maaOperatorDiscCache?.let { return@synchronized it }

            val loaded = runCatching {
                val jsonText = context.assets.open("$ASSET_DIR/operators.json").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
                }
                val operators = JSONObject(jsonText).optJSONArray("OPERATORS") ?: JSONArray()
                buildMap<String, List<OperatorDiscMeta>> {
                    for (i in 0 until operators.length()) {
                        val operator = operators.optJSONObject(i) ?: continue
                        val discs = operator.optJSONArray("discs")
                            ?.let(::parseOperatorDiscs)
                            .orEmpty()
                        if (discs.isEmpty()) continue

                        sequenceOf(
                            operator.optString("name"),
                            operator.optString("alt_name"),
                            operator.optString("alias")
                        )
                            .flatMap { value ->
                                value.split(Regex("""[\s"“”]+"""))
                                    .asSequence()
                            }
                            .map(::normalizeOperatorName)
                            .filter { it.isNotBlank() }
                            .forEach { key ->
                                putIfAbsent(key, discs)
                            }
                    }
                }
            }.getOrDefault(emptyMap())

            maaOperatorDiscCache = loaded
            loaded
        }
    }

    private fun parseOperatorDiscs(discsArray: JSONArray): List<OperatorDiscMeta> {
        val result = mutableListOf<OperatorDiscMeta>()
        for (i in 0 until discsArray.length()) {
            val disc = discsArray.optJSONObject(i) ?: continue
            result += OperatorDiscMeta(
                otName = disc.optString("ot_name").trim(),
                abbreviation = disc.optString("abbreviation").trim(),
                color = disc.optString("color").trim()
            )
        }
        return result
    }

    private fun normalizeOperatorName(rawName: String): String {
        val normalized = rawName.trim()
        return when (normalized) {
            "SP史子眇" -> "SP史子渺"
            else -> normalized
        }
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

    private fun formatMetric(value: Long): String {
        return when {
            value >= 10000 -> String.format("%.1fw", value / 10000f)
            value >= 1000 -> String.format("%.1fk", value / 1000f)
            else -> value.toString()
        }
    }

    private fun formatSourceType(sourceType: String?): String {
        return when (sourceType?.trim()?.lowercase()) {
            "repost" -> "搬运"
            "original" -> "原创"
            else -> ""
        }
    }

    private fun formatRemoteTime(uploadTime: String?): String {
        return uploadTime
            ?.replace("T", " ")
            ?.replace("Z", "")
            ?.take(16)
            ?.takeIf { it.isNotBlank() }
            ?: "最近更新"
    }

    private fun formatBmobTime(createdAt: String?): String {
        return createdAt
            ?.take(16)
            ?.takeIf { it.isNotBlank() }
            ?: "最近更新"
    }

    private fun formatRelativeTime(timestamp: Long, fallback: String): String {
        if (timestamp <= 0L) return fallback

        val deltaMillis = System.currentTimeMillis() - timestamp
        if (deltaMillis <= 0L) return "1小时前"

        val hours = deltaMillis / (60 * 60 * 1000L)
        if (hours < 24L) {
            return "${max(1L, hours)}小时前"
        }

        val days = deltaMillis / (24 * 60 * 60 * 1000L)
        return "${max(1L, days)}天前"
    }

    fun parsePublishTimestamp(rawTime: String?): Long {
        val normalized = rawTime?.trim()?.takeIf { it.isNotBlank() } ?: return 0L

        parseWithPattern(normalized, "yyyy-MM-dd HH:mm:ss")?.let { return it }

        if (normalized.contains('T')) {
            parseWithPattern(
                normalized.substringBefore('.'),
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                TimeZone.getTimeZone("UTC")
            )?.let { return it }
            parseWithPattern(
                normalized.substringBefore('.').removeSuffix("Z"),
                "yyyy-MM-dd'T'HH:mm:ss"
            )?.let { return it }
            parseWithPattern(
                normalized.replace("T", " ").substringBefore('.').removeSuffix("Z"),
                "yyyy-MM-dd HH:mm:ss"
            )?.let { return it }
        }

        return 0L
    }

    private fun parseWithPattern(
        value: String,
        pattern: String,
        timeZone: TimeZone? = null
    ): Long? {
        return runCatching {
            SimpleDateFormat(pattern, Locale.getDefault()).apply {
                isLenient = false
                if (timeZone != null) {
                    this.timeZone = timeZone
                }
            }.parse(value)?.time
        }.getOrNull()
    }

    private fun parseBmobRoster(detail: strategy_detail): List<String> {
        if (!detail.agentSelection.isNullOrBlank()) {
            val parsedRoster = runCatching {
                Gson().fromJson(detail.agentSelection, Array<String>::class.java)
                    .orEmpty()
                    .mapNotNull(::normalizeBmobAgentName)
            }.getOrNull()

            if (parsedRoster != null) {
                return parsedRoster
            }
        }

        return detail.agents
            .split("、", "，", ",", " ")
            .mapNotNull(::normalizeBmobAgentName)
    }

    private fun normalizeBmobAgentName(raw: String?): String? {
        val normalized = raw
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replaceFirst("^\\d+".toRegex(), "")
            ?.substringBefore("-")
            ?.trim()

        return normalized?.takeIf { it.isNotBlank() }
    }

    private fun buildBmobAgentsText(detail: strategy_detail, roster: List<String>): String {
        return roster.joinToString(" ").ifBlank {
            detail.agents.orEmpty().ifBlank { "未配置阵容" }
        }
    }

    private fun buildBmobImportPayload(detail: strategy_detail): JobStationImportPayload? {
        val normalizedScript = detail.scriptContent
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .trim()
        if (normalizedScript.isBlank()) return null

        return JobStationImportPayload(
            scriptContent = normalizedScript,
            configJson = detail.config.orEmpty(),
            instructionsJson = detail.instructions.orEmpty(),
            agentsJson = cleanBmobAgentSelectionJson(detail.agentSelection),
            notice = "导入成功"
        )
    }

    private fun cleanBmobAgentSelectionJson(agentSelectionJson: String?): String {
        if (agentSelectionJson.isNullOrBlank() || agentSelectionJson == "[]") return ""
        return runCatching {
            val rawAgentsList = Gson().fromJson(agentSelectionJson, Array<String>::class.java)
                ?.toList()
                .orEmpty()
            Gson().toJson(rawAgentsList.map { raw ->
                val trimRaw = raw.trim()
                if (trimRaw.isBlank()) {
                    ""
                } else {
                    trimRaw.replaceFirst("^\\d+".toRegex(), "")
                        .substringBefore("-")
                        .trim()
                }
            })
        }.getOrDefault(agentSelectionJson)
    }

    private fun resolveBmobAuthorName(detail: strategy_detail): String {
        return detail.author?.nickname?.takeIf { it.isNotBlank() }
            ?: detail.author?.username?.takeIf { it.isNotBlank() }
            ?: "热心玩家"
    }

    private fun buildBmobTags(detail: strategy_detail, title: String): List<String> {
        return buildList {
            resolveGameTagFromRuyuan(detail.ruyuan)
                .ifBlank { resolveGameTagFromTitle(title) }
                .takeIf { it.isNotBlank() }
                ?.let(::add)

            listOf("地宫", "洞窟", "白鹄", "泰山府").forEach { tag ->
                if (title.contains(tag)) {
                    add(tag)
                }
            }
        }.distinct()
    }

    private fun resolveGameTagFromRuyuan(ruyuan: Int?): String {
        return when (ruyuan) {
            STRATEGY_GAME_RUYUAN -> "如鸢"
            STRATEGY_GAME_DAIHAOYUAN -> "代号鸢"
            else -> ""
        }
    }

    private fun resolveGameTagFromStrings(tags: List<String>): String {
        return when {
            tags.any { it == "如鸢" } -> "如鸢"
            tags.any { it == "代号鸢" } -> "代号鸢"
            else -> ""
        }
    }

    private fun resolveGameTagFromTitle(title: String): String {
        return when {
            title.contains("如鸢") -> "如鸢"
            title.contains("代号鸢") -> "代号鸢"
            else -> ""
        }
    }

    private fun resolveCategoryTagFromTitle(title: String): String {
        return listOf("主线", "白鹄", "洞窟", "兰台", "地宫", "家具", "活动")
            .firstOrNull { title.contains(it) }
            .orEmpty()
    }
}

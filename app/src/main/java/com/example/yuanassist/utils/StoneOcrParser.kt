package com.example.yuanassist.utils

import org.json.JSONObject

data class StoneStat(
    val name: String,
    val level: String,
    val count: Int
)

object StoneOcrParser {

    private const val STONES_PER_ROW = 4
    private enum class RowPhase { LEVEL, NAME }

    val validStoneNames = listOf(
        "武曲", "天机", "破军", "天同", "天梁", "贪狼", "天府", "天相", "太阳", "巨门",
        "太阴", "紫微", "七杀", "廉贞", "解神", "文曲", "红鸾", "文昌", "地劫", "禄存",
        "天马", "擎羊", "右弼", "左辅", "天魁", "天钺", "三台", "天巫", "阴煞", "天刑",
        "天姚", "地空", "铃星", "陀螺", "火星", "天贵", "恩光", "八座"
    )

    private val punctuationRegex = Regex("[,，.。:：;；]")
    private val levelRegex = Regex("^\\d+级$")
    private val stoneNameOrder = validStoneNames.withIndex().associate { it.value to it.index }

    fun buildRows(wordsGroups: List<List<String>>): List<MyStoneRow> {
        return parseWordRows(wordsGroups).map { parsedRow ->
            val cellCount = maxOf(parsedRow.levels.size, parsedRow.names.size)
            val cells = MutableList(cellCount) { cellIndex ->
                MyStoneCell(
                    level = parsedRow.levels.getOrElse(cellIndex) { "" },
                    name = parsedRow.names.getOrElse(cellIndex) { "" }
                )
            }
            MyStoneRow(cells)
        }
    }

    fun formatRawWordsByRow(wordsGroups: List<List<String>>): List<String> {
        return splitRawWordsByRow(wordsGroups).mapIndexed { index, tokens ->
            "第${index + 1}行：${tokens.joinToString(" | ")}"
        }
    }

    fun formatRawJsonByRow(rawEntryGroups: List<List<String>>): List<String> {
        return splitRawEntriesByRow(rawEntryGroups).mapIndexed { index, entries ->
            "第${index + 1}行：${entries.joinToString(" | ")}"
        }
    }

    private fun parseWordRows(wordsGroups: List<List<String>>): List<ParsedTokenRow> {
        val tokens = wordsGroups.flatten()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return parseRows(tokens) { token ->
            ParsedToken(
                level = normalizeLevelIfPresent(token),
                name = normalizeNameIfPresent(token)
            )
        }
    }

    private fun splitRawWordsByRow(wordsGroups: List<List<String>>): List<List<String>> {
        val rawTokens = wordsGroups.flatten()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (rawTokens.isEmpty()) return emptyList()

        return parseRows(rawTokens) { token ->
            ParsedToken(
                level = normalizeLevelIfPresent(token),
                name = normalizeNameIfPresent(token)
            )
        }.map { row ->
            rawTokens.subList(row.startIndex, row.endExclusive)
        }
    }

    private fun splitRawEntriesByRow(rawEntryGroups: List<List<String>>): List<List<String>> {
        val rawEntries = rawEntryGroups.flatten()
            .filter { it.isNotBlank() }
        if (rawEntries.isEmpty()) return emptyList()

        return parseRows(rawEntries) { entry ->
            val word = runCatching {
                JSONObject(entry).optString("words").trim()
            }.getOrDefault("")
            ParsedToken(
                level = normalizeLevelIfPresent(word),
                name = normalizeNameIfPresent(word)
            )
        }.map { row ->
            rawEntries.subList(row.startIndex, row.endExclusive)
        }
    }

    private data class ParsedTokenRow(
        val startIndex: Int,
        val endExclusive: Int,
        val levels: List<String>,
        val names: List<String>
    )

    private data class ParsedToken(
        val level: String?,
        val name: String?
    )

    private fun parseRows(
        rawTokens: List<String>,
        tokenMapper: (String) -> ParsedToken
    ): List<ParsedTokenRow> {
        if (rawTokens.isEmpty()) return emptyList()

        val tokens = rawTokens.map(tokenMapper)
        val rows = mutableListOf<ParsedTokenRow>()
        var index = 0

        while (index < tokens.size) {
            while (index < tokens.size && tokens[index].level == null) {
                index++
            }
            if (index >= tokens.size) break

            val startIndex = index
            val levels = mutableListOf<String>()
            val names = mutableListOf<String>()
            var phase = RowPhase.LEVEL
            var endExclusive = index

            while (index < tokens.size) {
                val token = tokens[index]
                when (phase) {
                    RowPhase.LEVEL -> {
                        when {
                            token.level != null && levels.size < STONES_PER_ROW -> {
                                levels += token.level
                                index++
                                endExclusive = index
                                if (levels.size == STONES_PER_ROW) {
                                    phase = RowPhase.NAME
                                }
                            }

                            token.name != null && levels.size >= STONES_PER_ROW - 1 -> {
                                phase = RowPhase.NAME
                                names += token.name
                                index++
                                endExclusive = index
                                if (names.size == STONES_PER_ROW) {
                                    break
                                }
                            }

                            else -> {
                                index++
                                endExclusive = index
                            }
                        }
                    }

                    RowPhase.NAME -> {
                        when {
                            token.level != null && names.size >= STONES_PER_ROW - 1 -> break
                            token.name != null && names.size < STONES_PER_ROW -> {
                                names += token.name
                                index++
                                endExclusive = index
                                if (names.size == STONES_PER_ROW) {
                                    break
                                }
                            }

                            else -> {
                                index++
                                endExclusive = index
                            }
                        }
                    }
                }
            }

            rows += ParsedTokenRow(
                startIndex = startIndex,
                endExclusive = endExclusive,
                levels = levels.toList(),
                names = names.toList()
            )
        }

        return rows
    }

    fun aggregate(rows: List<MyStoneRow>): List<StoneStat> {
        val counter = linkedMapOf<Pair<String, String>, Int>()

        rows.filter(::isRowResolved).forEach { row ->
            row.cells.forEach { cell ->
                val name = normalizeStoneNameToken(cell.name)
                val level = normalizeLevel(cell.level)
                val key = name to level
                counter[key] = (counter[key] ?: 0) + 1
            }
        }

        return counter.entries
            .sortedWith(
                compareBy<Map.Entry<Pair<String, String>, Int>>(
                    { stoneNameOrder[it.key.first] ?: Int.MAX_VALUE },
                    { levelSortValue(it.key.second) }
                )
            )
            .map { entry ->
                StoneStat(
                    name = entry.key.first,
                    level = entry.key.second,
                    count = entry.value
                )
            }
    }

    fun format(stats: List<StoneStat>): List<String> =
        stats.map { "${it.name} ${it.level} x${it.count}" }

    fun normalizeLevel(level: String): String {
        val cleaned = normalizeLevelToken(level)
        return when {
            cleaned.isEmpty() -> ""
            cleaned == "级" -> "1级"
            cleaned.matches(Regex("^\\d+$")) -> "${cleaned}级"
            cleaned.matches(Regex("^\\d+级?$")) && !cleaned.endsWith("级") -> "${cleaned}级"
            else -> cleaned
        }
    }

    fun normalizeToken(text: String): String =
        punctuationRegex.replace(text, "")
            .replace("\\s+".toRegex(), "")
            .trim()

    private fun normalizeStoneNameToken(text: String): String =
        normalizeToken(text)
            .filter { char -> char.code in 0x4E00..0x9FFF && char != '级' }

    private fun normalizeStoneNameCandidate(text: String): String {
        val normalized = normalizeStoneNameToken(text)
        if (normalized.isEmpty()) return ""
        return normalized.takeIf { candidate ->
            validStoneNames.any { it.contains(candidate) }
        }.orEmpty()
    }

    fun isValidStoneName(name: String): Boolean =
        validStoneNames.contains(normalizeStoneNameToken(name))

    fun isValidLevel(level: String): Boolean {
        val normalized = normalizeLevel(level)
        val levelValue = normalized.removeSuffix("级").toIntOrNull() ?: return false
        return levelRegex.matches(normalized) && levelValue > 0
    }

    fun isRowResolved(row: MyStoneRow): Boolean {
        if (row.cells.isEmpty()) return false
        return row.cells.all { cell ->
            isValidLevel(cell.level) && isValidStoneName(cell.name)
        }
    }

    fun isCellNameValid(cell: MyStoneCell): Boolean {
        val normalizedName = normalizeStoneNameToken(cell.name)
        return normalizedName.isNotEmpty() && isValidStoneName(normalizedName)
    }

    fun looksLikeLevel(text: String): Boolean {
        val cleaned = normalizeLevelToken(text)
        if (cleaned.isEmpty()) return false
        if (cleaned == "级") return true
        val normalized = normalizeLevel(cleaned)
        val levelValue = normalized.removeSuffix("级").toIntOrNull()
        if (levelValue != null && levelValue <= 0) return false
        return cleaned.contains("级") ||
            cleaned.matches(Regex("^\\d+$"))
    }

    private fun normalizeLevelIfPresent(text: String): String? {
        if (!looksLikeLevel(text)) return null
        val normalized = normalizeLevel(text)
        val levelValue = normalized.removeSuffix("级").toIntOrNull() ?: return null
        return normalized.takeIf { levelValue > 0 }
    }

    private fun normalizeNameIfPresent(text: String): String? {
        if (text.contains("级")) return null
        return normalizeStoneNameCandidate(text).takeIf { it.isNotEmpty() }
    }

    private fun normalizeLevelToken(text: String): String =
        normalizeToken(text)
            .replace('I', '1')
            .replace('l', '1')
            .replace('L', '1')
            .replace('|', '1')
            .replace('/', '1')

    private fun levelSortValue(level: String): Int =
        normalizeLevel(level).removeSuffix("级").toIntOrNull() ?: Int.MAX_VALUE
}

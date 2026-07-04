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

    val mainStoneNames = setOf(
        "武曲", "天机", "破军", "天同", "天梁", "贪狼", "天府",
        "天相", "太阳", "巨门", "太阴", "紫微", "七杀", "廉贞"
    )

    val validStoneNames = listOf(
        "武曲", "天机", "破军", "天同", "天梁", "贪狼", "天府", "天相", "太阳", "巨门",
        "太阴", "紫微", "七杀", "廉贞", "解神", "文曲", "红鸾", "文昌", "地劫", "禄存",
        "天马", "擎羊", "右弼", "左辅", "天魁", "天钺", "三台", "天巫", "阴煞", "天刑",
        "天姚", "地空", "铃星", "陀螺", "火星", "天贵", "恩光", "八座"
    )

    private val punctuationRegex = Regex("[,，.。:：;；]")
    private val levelRegex = Regex("^\\d+级$")
    private val stoneNameOrder = validStoneNames.withIndex().associate { it.value to it.index }

    fun buildRows(
        wordsGroups: List<List<String>>,
        stoneType: String = MyStoneStore.TYPE_MAIN,
    ): List<MyStoneRow> {
        return parseWordRows(wordsGroups, stoneType).map { parsedRow ->
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

    fun validStoneNamesForType(stoneType: String): List<String> {
        return if (MyStoneStore.normalizeType(stoneType) == MyStoneStore.TYPE_MAIN) {
            validStoneNames.filter { it in mainStoneNames }
        } else {
            validStoneNames.filterNot { it in mainStoneNames }
        }
    }

    fun matchValidStoneName(
        text: String,
        stoneType: String,
    ): String {
        return resolveStoneNameCandidate(text, stoneType)
    }

    fun resolveStoneNameCandidate(
        text: String,
        stoneType: String,
    ): String {
        val normalized = normalizeStoneNameToken(text)
        if (normalized.isEmpty()) return ""

        val candidates = validStoneNamesForType(stoneType)
        val exact = candidates.filter { it == normalized }
        if (exact.size == 1) return exact.first()

        val contains = candidates.filter { candidate ->
            candidate.contains(normalized) || normalized.contains(candidate)
        }
        if (contains.size == 1) return contains.first()

        val singleCharMatches = if (normalized.length == 1) {
            candidates.filter { candidate -> candidate.contains(normalized) }
        } else {
            emptyList()
        }
        if (singleCharMatches.size == 1) return singleCharMatches.first()

        val oneEditMatches = candidates.filter { candidate ->
            candidate.length == normalized.length && levenshteinDistance(candidate, normalized) == 1
        }
        if (oneEditMatches.size == 1) return oneEditMatches.first()

        return ""
    }

    fun resolveDirectStoneName(
        text: String,
        stoneType: String,
    ): String {
        val normalized = normalizeStoneNameToken(text)
        if (normalized.isEmpty()) return ""
        return validStoneNamesForType(stoneType).singleOrNull { it == normalized }.orEmpty()
    }

    fun splitStoneNameRow(
        text: String,
        stoneType: String,
    ): List<String> = splitStoneNameRow(text, stoneType, allowRepairs = true)

    fun splitDirectStoneNameRow(
        text: String,
        stoneType: String,
    ): List<String> = splitStoneNameRow(text, stoneType, allowRepairs = false)

    private fun splitStoneNameRow(
        text: String,
        stoneType: String,
        allowRepairs: Boolean,
    ): List<String> {
        val normalized = normalizeStoneNameToken(text)
        if (normalized.isEmpty()) {
            return List(STONES_PER_ROW) { "" }
        }

        val candidates = validStoneNamesForType(stoneType)
        val cells = mutableListOf<String>()
        var index = 0

        while (index < normalized.length && cells.size < STONES_PER_ROW) {
            val twoChars = normalized.substring(index, minOf(index + 2, normalized.length))
            val exactMatch = if (twoChars.length == 2) {
                candidates.singleOrNull { it == twoChars }
            } else {
                null
            }
            if (exactMatch != null) {
                cells += exactMatch
                index += 2
                continue
            }

            val singleChar = normalized[index].toString()
            if (allowRepairs) {
                val singleMatches = candidates.filter { it.contains(singleChar) }
                if (singleMatches.size == 1) {
                    cells += singleMatches.first()
                    index += 1
                    continue
                }

                val oneEditMatches = if (twoChars.length == 2) {
                    candidates.filter { candidate ->
                        candidate.length == twoChars.length && levenshteinDistance(candidate, twoChars) == 1
                    }
                } else {
                    emptyList()
                }
                if (oneEditMatches.size == 1) {
                    cells += oneEditMatches.first()
                    index += 2
                    continue
                }
            }

            cells += ""
            index += 1
        }

        return normalizeCellCount(cells)
    }

    fun splitStoneLevelRow(text: String): List<String> {
        return splitStoneLevelRow(text, allowRepairs = true)
    }

    fun splitDirectStoneLevelRow(text: String): List<String> {
        return splitStoneLevelRow(text, allowRepairs = false)
    }

    fun resolveDirectStoneLevel(text: String): String {
        val normalized = normalizeLevelToken(text)
            .filter { it.isDigit() || it == '级' }
        val level = normalized.takeIf { it.matches(Regex("^\\d+级$")) } ?: return ""
        val value = level.removeSuffix("级").toIntOrNull() ?: return ""
        return if (value in 1..60) "${value}级" else ""
    }

    private fun trimLevelSuffixForSplit(text: String): String {
        val index = text.indexOf('级')
        return if (index >= 0) text.substring(0, index + 1) else text
    }

    private fun splitStoneLevelRow(
        text: String,
        allowRepairs: Boolean,
    ): List<String> {
        val normalized = trimLevelSuffixForSplit(normalizeLevelToken(text))
            .filter { it.isDigit() || it == '级' }
        if (normalized.isEmpty()) {
            return List(STONES_PER_ROW) { "" }
        }

        val cells = mutableListOf<String>()
        val pendingDigits = StringBuilder()
        for (char in normalized) {
            if (cells.size >= STONES_PER_ROW) break
            if (char.isDigit()) {
                pendingDigits.append(char)
                continue
            }
            if (char == '级') {
                cells += normalizeLevelCell(pendingDigits.toString(), hasJi = true, allowJiDefault = allowRepairs, trimAfterJi = allowRepairs)
                pendingDigits.clear()
            }
        }

        if (cells.size < STONES_PER_ROW && pendingDigits.isNotEmpty()) {
            cells += splitLevelDigits(pendingDigits.toString(), STONES_PER_ROW - cells.size)
        }

        return normalizeCellCount(cells)
    }

    fun formatRawWordsByRow(
        wordsGroups: List<List<String>>,
        stoneType: String = MyStoneStore.TYPE_MAIN,
    ): List<String> {
        return splitRawWordsByRow(wordsGroups, stoneType).mapIndexed { index, tokens ->
            "第${index + 1}行：${tokens.joinToString(" | ")}"
        }
    }

    fun formatRawJsonByRow(
        rawEntryGroups: List<List<String>>,
        stoneType: String = MyStoneStore.TYPE_MAIN,
    ): List<String> {
        return splitRawEntriesByRow(rawEntryGroups, stoneType).mapIndexed { index, entries ->
            "第${index + 1}行：${entries.joinToString(" | ")}"
        }
    }

    private fun parseWordRows(
        wordsGroups: List<List<String>>,
        stoneType: String,
    ): List<ParsedTokenRow> {
        val tokens = wordsGroups.flatten()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return parseRows(tokens) { token ->
            ParsedToken(
                level = normalizeLevelIfPresent(token),
                name = normalizeNameIfPresent(token, stoneType)
            )
        }
    }

    private fun splitRawWordsByRow(
        wordsGroups: List<List<String>>,
        stoneType: String,
    ): List<List<String>> {
        val rawTokens = wordsGroups.flatten()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (rawTokens.isEmpty()) return emptyList()

        return parseRows(rawTokens) { token ->
            ParsedToken(
                level = normalizeLevelIfPresent(token),
                name = normalizeNameIfPresent(token, stoneType)
            )
        }.map { row ->
            rawTokens.subList(row.startIndex, row.endExclusive)
        }
    }

    private fun splitRawEntriesByRow(
        rawEntryGroups: List<List<String>>,
        stoneType: String,
    ): List<List<String>> {
        val rawEntries = rawEntryGroups.flatten()
            .filter { it.isNotBlank() }
        if (rawEntries.isEmpty()) return emptyList()

        return parseRows(rawEntries) { entry ->
            val word = runCatching {
                JSONObject(entry).optString("words").trim()
            }.getOrDefault("")
            ParsedToken(
                level = normalizeLevelIfPresent(word),
                name = normalizeNameIfPresent(word, stoneType)
            )
        }.map { row ->
            rawEntries.subList(row.startIndex, row.endExclusive)
        }
    }

    private fun normalizeCellCount(cells: List<String>): List<String> {
        val normalized = cells.take(STONES_PER_ROW)
        return if (normalized.size < STONES_PER_ROW) {
            normalized + List(STONES_PER_ROW - normalized.size) { "" }
        } else {
            normalized
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

                            token.name != null -> {
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
                            token.level != null -> break
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

    fun aggregate(
        rows: List<MyStoneRow>,
        stoneType: String = MyStoneStore.TYPE_MAIN,
    ): List<StoneStat> {
        val counter = linkedMapOf<Pair<String, String>, Int>()

        rows.filter { isRowResolved(it, stoneType) }.forEach { row ->
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

    fun isValidStoneName(
        name: String,
        stoneType: String,
    ): Boolean =
        validStoneNamesForType(stoneType).contains(normalizeStoneNameToken(name))

    fun isValidLevel(level: String): Boolean {
        val normalized = normalizeLevel(level)
        val levelValue = normalized.removeSuffix("级").toIntOrNull() ?: return false
        return levelRegex.matches(normalized) && levelValue in 1..60
    }

    fun isRowResolved(
        row: MyStoneRow,
        stoneType: String = MyStoneStore.TYPE_MAIN,
    ): Boolean {
        if (row.cells.isEmpty()) return false
        return row.cells.all { cell ->
            isValidLevel(cell.level) && isValidStoneName(cell.name, stoneType)
        }
    }

    fun isCellNameValid(
        cell: MyStoneCell,
        stoneType: String = MyStoneStore.TYPE_MAIN,
    ): Boolean {
        val normalizedName = normalizeStoneNameToken(cell.name)
        return normalizedName.isNotEmpty() && isValidStoneName(normalizedName, stoneType)
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

    private fun normalizeNameIfPresent(
        text: String,
        stoneType: String,
    ): String? {
        if (text.contains("级")) return null
        return resolveStoneNameCandidate(text, stoneType).takeIf { it.isNotEmpty() }
    }

    private fun normalizeLevelToken(text: String): String =
        normalizeToken(text)
            .replace('I', '1')
            .replace('l', '1')
            .replace('L', '1')
            .replace('|', '1')
            .replace('/', '1')

    private fun normalizeLevelCell(
        digits: String,
        hasJi: Boolean,
        allowJiDefault: Boolean,
        trimAfterJi: Boolean,
    ): String {
        val cleanedDigits = if (trimAfterJi) digits else digits.trimEnd { !it.isDigit() }
        val value = when {
            cleanedDigits.isBlank() && hasJi && allowJiDefault -> 1
            else -> cleanedDigits.toIntOrNull()
        } ?: return ""
        return if (value in 1..60) "${value}级" else ""
    }

    private fun splitLevelDigits(
        digits: String,
        maxCells: Int,
    ): List<String> {
        val cells = mutableListOf<String>()
        var index = 0
        while (index < digits.length && cells.size < maxCells) {
            val twoDigit = digits.substring(index, minOf(index + 2, digits.length))
            val twoValue = twoDigit.toIntOrNull()
            if (twoDigit.length == 2 && twoValue != null && twoValue in 10..60) {
                cells += "${twoValue}级"
                index += 2
                continue
            }

            val oneValue = digits[index].digitToIntOrNull()
            cells += if (oneValue != null && oneValue in 1..9) "${oneValue}级" else ""
            index += 1
        }
        return cells
    }

    private fun levenshteinDistance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val prev = IntArray(right.length + 1) { it }
        val curr = IntArray(right.length + 1)

        for (i in left.indices) {
            curr[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                curr[j + 1] = minOf(
                    minOf(curr[j] + 1, prev[j + 1] + 1),
                    prev[j] + cost
                )
            }
            for (j in prev.indices) {
                prev[j] = curr[j]
            }
        }

        return prev[right.length]
    }

    private fun levelSortValue(level: String): Int =
        normalizeLevel(level).removeSuffix("级").toIntOrNull() ?: Int.MAX_VALUE
}

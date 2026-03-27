package com.example.yuanassist.utils

data class StoneStat(
    val name: String,
    val level: String,
    val count: Int
)

object StoneOcrParser {

    val validStoneNames = listOf(
        "武曲", "天机", "破军", "天同", "天梁", "贪狼", "天府", "天相", "太阳", "巨门",
        "太阴", "紫薇", "七杀", "廉贞", "解神", "文曲", "红鸾", "文昌", "地劫", "禄存",
        "天马", "擎羊", "右弼", "左辅", "天魁", "天钺", "三台", "天巫", "阴煞", "天刑",
        "天姚", "地空", "铃星", "陀螺", "火星", "天贵", "恩光", "八座"
    )

    private val punctuationRegex = Regex("[,，.。:：;；]")
    private val levelRegex = Regex("^\\d+级$")
    private val stoneNameOrder = validStoneNames.withIndex().associate { it.value to it.index }

    fun buildRows(wordsGroups: List<List<String>>): List<MyStoneRow> {
        val tokens = wordsGroups.flatten()
            .map(::normalizeToken)
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) {
            return emptyList()
        }

        val rows = mutableListOf<MyStoneRow>()
        var index = 0

        while (index < tokens.size) {
            val levels = mutableListOf<String>()
            while (index < tokens.size && looksLikeLevel(tokens[index])) {
                levels += normalizeLevel(tokens[index])
                index++
            }

            val names = mutableListOf<String>()
            while (index < tokens.size && !looksLikeLevel(tokens[index])) {
                names += normalizeToken(tokens[index])
                index++
            }

            if (levels.isEmpty() && names.isEmpty()) {
                break
            }

            val cellCount = maxOf(levels.size, names.size)
            val cells = MutableList(cellCount) { cellIndex ->
                MyStoneCell(
                    level = levels.getOrElse(cellIndex) { "" },
                    name = names.getOrElse(cellIndex) { "" }
                )
            }
            rows += MyStoneRow(cells)
        }

        return rows
    }

    fun aggregate(rows: List<MyStoneRow>): List<StoneStat> {
        val counter = linkedMapOf<Pair<String, String>, Int>()

        rows.filter(::isRowResolved).forEach { row ->
            row.cells.forEach { cell ->
                val name = normalizeToken(cell.name)
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
        val cleaned = normalizeToken(level)
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

    fun isValidStoneName(name: String): Boolean =
        validStoneNames.contains(normalizeToken(name))

    fun isValidLevel(level: String): Boolean =
        levelRegex.matches(normalizeLevel(level))

    fun isRowResolved(row: MyStoneRow): Boolean {
        if (row.cells.isEmpty()) return false
        return row.cells.all { cell ->
            isValidLevel(cell.level) && isValidStoneName(cell.name)
        }
    }

    fun isCellNameValid(cell: MyStoneCell): Boolean {
        val normalizedName = normalizeToken(cell.name)
        return normalizedName.isNotEmpty() && isValidStoneName(normalizedName)
    }

    fun looksLikeLevel(text: String): Boolean {
        val cleaned = normalizeToken(text)
        return cleaned == "级" ||
            cleaned.matches(Regex("^\\d+$")) ||
            cleaned.matches(Regex("^\\d+级$"))
    }

    private fun levelSortValue(level: String): Int =
        normalizeLevel(level).removeSuffix("级").toIntOrNull() ?: Int.MAX_VALUE
}

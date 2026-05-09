package com.example.yuanassist.tableocr

object PostProcessor {

    private val digitFixes = mapOf(
        'I' to '1', 'l' to '1', 'O' to '0'
    )
    private val suffixFixes = mapOf(
        'T' to '↑', 't' to '↑',
        'V' to '↓', 'v' to '↓',
        'Y' to '↓', 'y' to '↓',
        'L' to '↓', 'U' to '↓',
        '√' to '↓', '」' to '↓', '』' to '↓',
        '{' to '↑', '\\' to '↓',
        '个' to '↑'
    )

    fun normalizeRoundText(text: String): String {
        val cleaned = text.replace(" ", "").replace("\n", "").replace("\t", "")
        if (cleaned.isEmpty()) return ""

        if (cleaned.endsWith("回合")) {
            val prefix = cleaned.removeSuffix("回合").removePrefix("第")
                .map { ch -> digitFixes[ch] ?: ch }.joinToString("")
            if (prefix.all { it.isDigit() }) {
                return "${prefix}回合"
            }
        }

        val normalized = cleaned.map { ch -> digitFixes[ch] ?: ch }.joinToString("")
        val digitMatch = Regex("(\\d+)").find(normalized)
        if (digitMatch != null) {
            return "${digitMatch.value}回合"
        }
        return ""
    }

    fun normalizeActionText(text: String): String {
        val cleaned = text.replace(" ", "").replace("\n", "").replace("\t", "").replace("/", "")
        if (cleaned.isEmpty()) return ""

        val normalized = cleaned.map { ch ->
            digitFixes[ch] ?: suffixFixes[ch] ?: ch
        }.joinToString("")

        val parsed = ActionParser.parse(normalized)
        if (parsed.isComplete && parsed.text.isNotEmpty()) {
            return parsed.text
        }

        if (normalized.contains("圈") || Regex("\\d+").findAll(normalized).count() >= 2) {
            if (parsed.text.isNotEmpty()) {
                return parsed.text + parsed.fragment
            }
            if (Regex("\\d").containsMatchIn(normalized) &&
                Regex("[A↑↓圈]").containsMatchIn(normalized)) {
                return normalized
            }
        }

        val firstDigit = Regex("10|[1-9]").find(normalized) ?: return ""
        val tail = normalized.substring(firstDigit.range.last + 1)

        return when {
            tail.contains("↑") && tail.contains("A") -> "${firstDigit.value}↑A"
            tail.contains("↑") -> "${firstDigit.value}↑"
            tail == "A" -> "${firstDigit.value}A"
            tail.contains("A") && tail.contains("↓") -> "${firstDigit.value}↓A"
            tail.contains("↓") -> "${firstDigit.value}↓"
            else -> ""
        }
    }
}

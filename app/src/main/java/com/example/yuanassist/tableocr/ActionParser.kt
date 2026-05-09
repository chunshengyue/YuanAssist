package com.example.yuanassist.tableocr

object ActionParser {

    private const val digitPattern = "(?:10|[1-9])"
    private const val suffixPattern = "(?:[A↑↓圈]+)"
    private val singleActionRegex = Regex("^($digitPattern)($suffixPattern)${'$'}")
    private val suffixOnlyRegex = Regex("^$suffixPattern${'$'}")
    private val complexTokenRegex = Regex("$digitPattern$suffixPattern")

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

    fun parse(text: String): ParseResult {
        val compact = text.replace(" ", "").replace("\n", "").replace("\t", "").replace("/", "")
        if (compact.isEmpty()) {
            return ParseResult("", true, "", ConfidenceLevel.HIGH, false)
        }

        // Apply fixes
        val cleaned = repairDigitOneAsUpArrow(compact.map { ch ->
            digitFixes[ch] ?: suffixFixes[ch] ?: ch
        }.joinToString(""))
        val wasFixed = cleaned != compact

        val suffixOnly = suffixOnlyRegex.matchEntire(cleaned)
        if (suffixOnly != null) {
            val level = if (wasFixed) ConfidenceLevel.MEDIUM else ConfidenceLevel.HIGH
            return ParseResult(cleaned, true, "", level, wasFixed)
        }

        // Single action match
        val single = singleActionRegex.matchEntire(cleaned)
        if (single != null) {
            val result = "${single.groupValues[1]}${single.groupValues[2]}"
            val level = if (wasFixed) ConfidenceLevel.MEDIUM else ConfidenceLevel.HIGH
            return ParseResult(result, true, "", level, wasFixed)
        }

        // Complex token findall
        val tokens = complexTokenRegex.findAll(cleaned).map { it.value }.toList()
        if (tokens.isEmpty()) {
            return ParseResult("", false, cleaned, ConfidenceLevel.LOW, wasFixed)
        }

        val joined = tokens.joinToString("")
        val isComplete = joined == cleaned
        val fragment = if (!isComplete) cleaned.substring(joined.length) else ""

        val confidence = when {
            isComplete && !wasFixed -> ConfidenceLevel.HIGH
            isComplete -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

        return ParseResult(joined, isComplete, fragment, confidence, wasFixed)
    }

    fun looksComplex(text: String): Boolean {
        val compact = text.replace(" ", "")
        return compact.contains("圈") || Regex("\\d+").findAll(compact).count() >= 2
    }

    fun extractDigit(text: String): String {
        val match = Regex("10|[1-9]").find(text)
        return match?.value ?: ""
    }

    private fun repairDigitOneAsUpArrow(text: String): String {
        if (!Regex("\\d1(?:\\d|${'$'})").containsMatchIn(text)) return text

        val chars = text.toCharArray()
        for (i in 1 until chars.size) {
            if (chars[i] == '1' && chars[i - 1].isDigit()) {
                chars[i] = '↑'
                val candidate = chars.concatToString()
                if (singleActionRegex.matches(candidate) ||
                    complexTokenRegex.findAll(candidate).joinToString("") { it.value } == candidate) {
                    return candidate
                }
                chars[i] = '1'
            }
        }
        return text
    }

    val singleActionCanonical = Regex("^$digitPattern$suffixPattern${'$'}")
}

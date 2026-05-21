package com.example.yuanassist.core

data class PiJingZhanJiQuizEntry(
    val question: String,
    val answer: String,
    val options: List<String>
)

class PiJingZhanJiQuizMatcher(
    private val entries: List<PiJingZhanJiQuizEntry>,
    private val threshold: Double = 0.5
) {

    fun findEntry(question: String, options: List<String>): PiJingZhanJiQuizEntry? {
        val best = findBestEntry(question, options) ?: return null
        return best.first.takeIf { best.second > threshold }
    }

    fun findAnswer(question: String, options: List<String>): String? {
        return findEntry(question, options)?.answer
    }

    fun chooseOptionIndex(entry: PiJingZhanJiQuizEntry, options: List<String>): Int? {
        chooseConfidentIndex(entry.answer, options)?.let { return it }

        val cleanAnswer = cleanText(entry.answer)
        val excludedIndices = mutableSetOf<Int>()
        val wrongOptions = entry.options.filter { cleanText(it) != cleanAnswer }
        wrongOptions.forEach { wrongOption ->
            chooseConfidentIndex(wrongOption, options, excludedIndices)?.let { excludedIndices += it }
        }

        val candidateIndices = options.indices.filter { it !in excludedIndices }
        if (candidateIndices.isEmpty()) return null
        if (candidateIndices.size == 1) return candidateIndices.first()
        return candidateIndices.maxByOrNull { index ->
            similarity(cleanText(options[index]), cleanAnswer)
        }
    }

    private fun findBestEntry(question: String, options: List<String>): Pair<PiJingZhanJiQuizEntry, Double>? {
        val cleanQuestion = cleanText(question)
        val cleanOptions = options.map(::cleanText).filter { it.isNotBlank() }
        val inputText = buildCombinedText(cleanQuestion, cleanOptions)
        return entries
            .map { entry ->
                val entryQuestion = cleanText(entry.question).take(25)
                val entryText = buildCombinedText(entryQuestion, entry.options.map(::cleanText))
                val questionScore = similarity(cleanQuestion, entryQuestion)
                val fullScore = similarity(inputText, entryText)
                entry to maxOf(questionScore, fullScore)
            }
            .maxByOrNull { it.second }
    }

    private fun chooseConfidentIndex(
        target: String,
        options: List<String>,
        excludedIndices: Set<Int> = emptySet()
    ): Int? {
        val cleanTarget = cleanText(target)
        if (cleanTarget.isBlank()) return null
        val scored = options.mapIndexedNotNull { index, option ->
            if (index in excludedIndices) return@mapIndexedNotNull null
            index to similarity(cleanText(option), cleanTarget)
        }.sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return null
        val secondScore = scored.getOrNull(1)?.second ?: 0.0
        return if (best.second > threshold && best.second - secondScore > 0.05) best.first else null
    }

    private fun buildCombinedText(question: String, options: List<String>): String =
        question + " " + options.sorted().joinToString(" ")

    private fun cleanText(value: String): String =
        value.filter { !it.isWhitespace() && it !in PUNCTUATION }

    private fun similarity(left: String, right: String): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        val distances = Array(left.length + 1) { row -> IntArray(right.length + 1) { col -> if (row == 0) col else 0 } }
        for (row in distances.indices) distances[row][0] = row
        for (i in 1..left.length) {
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                distances[i][j] = minOf(
                    distances[i - 1][j] + 1,
                    distances[i][j - 1] + 1,
                    distances[i - 1][j - 1] + cost
                )
            }
        }
        val maxLength = maxOf(left.length, right.length).coerceAtLeast(1)
        return 1.0 - distances[left.length][right.length].toDouble() / maxLength
    }

    private companion object {
        private val PUNCTUATION = setOf('，', '。', '？', '?', '！', '!', '、', '：', ':', '；', ';', '“', '”', '"', '\'')
    }
}

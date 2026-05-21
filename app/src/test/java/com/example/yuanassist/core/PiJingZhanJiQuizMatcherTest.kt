package com.example.yuanassist.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PiJingZhanJiQuizMatcherTest {

    @Test
    fun matchesAnswerWhenQuestionAndOptionsContainOcrNoise() {
        val matcher = PiJingZhanJiQuizMatcher(
            entries = listOf(
                PiJingZhanJiQuizEntry(
                    question = "以下哪位密探擅长机关术",
                    answer = "太史慈",
                    options = listOf("杨修", "太史慈", "阿蝉", "陈登")
                )
            )
        )

        val result = matcher.findAnswer(
            question = "以下哪位密探擅长机关术?",
            options = listOf("杨修", "太史慈", "阿婵", "陈登")
        )

        assertEquals("太史慈", result)
    }

    @Test
    fun returnsNullWhenBestMatchIsTooWeak() {
        val matcher = PiJingZhanJiQuizMatcher(
            entries = listOf(
                PiJingZhanJiQuizEntry(
                    question = "以下哪位密探擅长机关术",
                    answer = "太史慈",
                    options = listOf("杨修", "太史慈", "阿蝉", "陈登")
                )
            )
        )

        val result = matcher.findAnswer(
            question = "完全不同的问题",
            options = listOf("甲", "乙", "丙", "丁")
        )

        assertNull(result)
    }
}

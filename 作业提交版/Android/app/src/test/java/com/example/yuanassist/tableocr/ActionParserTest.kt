package com.example.yuanassist.tableocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionParserTest {

    @Test
    fun parse_keepsRepeatedSuffixesInSingleToken() {
        val result = ActionParser.parse("5↓↓↓")

        assertEquals("5↓↓↓", result.text)
        assertTrue(result.isComplete)
        assertEquals("", result.fragment)
    }

    @Test
    fun parse_keepsMultipleTokensWithRepeatedSuffixes() {
        val result = ActionParser.parse("3A8↓9↓")

        assertEquals("3A8↓9↓", result.text)
        assertTrue(result.isComplete)
        assertEquals("", result.fragment)
    }

    @Test
    fun parse_keepsCircleAndTenTokens() {
        val result = ActionParser.parse("2圈3A9A1↑10A")

        assertEquals("2圈3A9A1↑10A", result.text)
        assertTrue(result.isComplete)
        assertEquals("", result.fragment)
    }

    @Test
    fun parse_dropsSlashFromRareSlashSuffix() {
        val result = ActionParser.parse("2↑/A")

        assertEquals("2↑A", result.text)
        assertTrue(result.isComplete)
        assertEquals("", result.fragment)
    }

    @Test
    fun parse_mapsChineseGeToUpArrow() {
        val result = ActionParser.parse("3A5A6圈7个")

        assertEquals("3A5A6圈7↑", result.text)
        assertTrue(result.isComplete)
        assertEquals("", result.fragment)
    }

    @Test
    fun extractDigit_keepsActionDigitsAboveFive() {
        assertEquals("6", ActionParser.extractDigit("6"))
        assertEquals("9", ActionParser.extractDigit("9"))
    }

    @Test
    fun parse_repairsOneAfterDigitAsUpArrowWhenItCompletesTokens() {
        val result = ActionParser.parse("5A71")

        assertEquals("5A7↑", result.text)
        assertTrue(result.isComplete)
        assertEquals("", result.fragment)
    }

    @Test
    fun parse_acceptsSuffixOnlyActions() {
        val a = ActionParser.parse("A")
        assertEquals("A", a.text)
        assertTrue(a.isComplete)

        val up = ActionParser.parse("↑")
        assertEquals("↑", up.text)
        assertTrue(up.isComplete)
    }
}

package com.example.yuanassist.tableocr

import org.junit.Assert.assertEquals
import org.junit.Test

class PostProcessorTest {

    @Test
    fun normalizeActionText_keepsRepeatedArrows() {
        assertEquals("5↓↓↓", PostProcessor.normalizeActionText("5↓↓↓"))
    }

    @Test
    fun normalizeActionText_dropsSlash() {
        assertEquals("2↑A", PostProcessor.normalizeActionText("2↑/A"))
    }

    @Test
    fun normalizeActionText_mapsChineseGeToUpArrow() {
        assertEquals("3A5A6圈7↑", PostProcessor.normalizeActionText("3A5A6圈7个"))
    }

    @Test
    fun normalizeActionText_repairsOneAfterDigitAsUpArrow() {
        assertEquals("5A7↑", PostProcessor.normalizeActionText("5A71"))
    }

    @Test
    fun normalizeActionText_keepsSuffixOnlyActions() {
        assertEquals("A", PostProcessor.normalizeActionText("A"))
        assertEquals("↑", PostProcessor.normalizeActionText("↑"))
    }
}

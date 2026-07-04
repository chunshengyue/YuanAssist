package com.example.yuanassist.tableocr

import org.junit.Assert.assertEquals
import org.junit.Test

class GridDetectorTest {

    @Test
    fun buildCellBoxes_skipsTallPortraitHeaderRow() {
        val structure = TableStructure(
            rowLines = listOf(0, 360, 460, 560),
            colLines = listOf(0, 100, 200, 300, 400, 500, 600)
        )

        val boxes = GridDetector.buildCellBoxes(structure)

        assertEquals(12, boxes.size)
        assertEquals(CellBox(row = 0, col = 0, x = 0, y = 360, w = 100, h = 100), boxes.first())
    }

    @Test
    fun buildCellBoxes_keepsRegularFirstRow() {
        val structure = TableStructure(
            rowLines = listOf(0, 100, 200, 300),
            colLines = listOf(0, 100, 200)
        )

        val boxes = GridDetector.buildCellBoxes(structure)

        assertEquals(6, boxes.size)
        assertEquals(CellBox(row = 0, col = 0, x = 0, y = 0, w = 100, h = 100), boxes.first())
    }
}

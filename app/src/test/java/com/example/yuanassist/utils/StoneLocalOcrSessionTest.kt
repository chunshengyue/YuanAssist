package com.example.yuanassist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoneLocalOcrSessionTest {

    @Test
    fun `cluster columns falls back to four stable centers`() {
        val centers = StonePaddleLocalRecognizer.clusterColumnsForTest(
            imageWidth = 1080,
            xCenters = listOf(138f, 139f, 352f, 356f, 570f, 782f, 786f)
        )

        assertEquals(4, centers.size)
        assertEquals(listOf(138, 354, 570, 784), centers.map { it.toInt() })
    }

    @Test
    fun `build row tokens keeps levels before names for each detected row`() {
        val row = StonePaddleLocalRecognizer.buildWordGroupForTest(
            cells = listOf(
                StonePaddleLocalRecognizer.DebugCellToken(level = "60级", name = "天府"),
                StonePaddleLocalRecognizer.DebugCellToken(level = "57级", name = "武曲"),
                StonePaddleLocalRecognizer.DebugCellToken(level = "", name = ""),
                StonePaddleLocalRecognizer.DebugCellToken(level = "1级", name = "太阳"),
            )
        )

        assertEquals(
            listOf("60级", "57级", "1级", "天府", "武曲", "太阳"),
            row
        )
    }

    @Test
    fun `level row heuristic accepts repeated ji or enough digits`() {
        assertTrue(StonePaddleLocalRecognizer.isLevelRowTextForTest("10级20级30级40级"))
        assertTrue(StonePaddleLocalRecognizer.isLevelRowTextForTest("10 20 30 40"))
        assertFalse(StonePaddleLocalRecognizer.isLevelRowTextForTest("武曲天机破军"))
    }

    @Test
    fun `row centers are padded to inferred row count even near image bottom`() {
        val centers = StonePaddleLocalRecognizer.buildRowCentersForTest(
            firstCenterY = 1200f,
            spacing = 500f,
            rowCount = 8,
            imageHeight = 1600,
        )

        assertEquals(8, centers.size)
        assertEquals(1599f, centers.last(), 0.01f)
    }

    @Test
    fun `wide image with one detected row stays single row`() {
        val rowCount = StonePaddleLocalRecognizer.resolveRowCountForTest(
            imageWidth = 1200,
            imageHeight = 300,
            levelSeedCount = 1,
            nameSeedCount = 1,
            inferredLevelCount = 4,
            inferredNameCount = 4,
        )

        assertEquals(1, rowCount)
    }

    @Test
    fun `name row heuristic requires three stone dictionary chars`() {
        assertEquals(
            8,
            StonePaddleLocalRecognizer.countStoneCharHitsForTest(
                text = "武曲天机破军天同",
                stoneType = MyStoneStore.TYPE_MAIN
            )
        )
        assertTrue(
            StonePaddleLocalRecognizer.isNameRowTextForTest(
                text = "武曲天机破军天同",
                stoneType = MyStoneStore.TYPE_MAIN
            )
        )
        assertFalse(
            StonePaddleLocalRecognizer.isNameRowTextForTest(
                text = "测试文本",
                stoneType = MyStoneStore.TYPE_MAIN
            )
        )
    }

    @Test
    fun `stone name matcher accepts unique single char anchor but rejects ambiguous ones`() {
        assertEquals("天同", StoneOcrParser.resolveStoneNameCandidate("同", MyStoneStore.TYPE_MAIN))
        assertEquals("", StoneOcrParser.resolveStoneNameCandidate("天", MyStoneStore.TYPE_MAIN))
    }

    @Test
    fun `stone name matcher repairs one wrong character when result is unique`() {
        assertEquals("破军", StoneOcrParser.resolveStoneNameCandidate("破x", MyStoneStore.TYPE_MAIN))
        assertEquals("天机", StoneOcrParser.resolveStoneNameCandidate("夭机", MyStoneStore.TYPE_MAIN))
    }

    @Test
    fun `stone name row split prefers exact anchors and unique single char matches`() {
        assertEquals(
            listOf("天同", "破军", "", "天机"),
            StoneOcrParser.splitStoneNameRow("同破军天天机", MyStoneStore.TYPE_MAIN)
        )
    }

    @Test
    fun `stone level row split treats ji as hard separator`() {
        assertEquals(
            listOf("60级", "60级", "60级", "60级"),
            StoneOcrParser.splitStoneLevelRow("60级60级60级60")
        )
        assertEquals(
            listOf("60级", "60级", "1级", "60级"),
            StoneOcrParser.splitStoneLevelRow("60级60级级60级")
        )
    }

    @Test
    fun `stone level row split accepts bare valid digits and rejects out of range values`() {
        assertEquals(
            listOf("60级", "60级", "60级", "60级"),
            StoneOcrParser.splitStoneLevelRow("60606060")
        )
        assertEquals(
            listOf("", "60级", "", ""),
            StoneOcrParser.splitStoneLevelRow("99级60级")
        )
    }

    @Test
    fun `direct level recognition only accepts explicit valid levels`() {
        assertEquals("60级", StoneOcrParser.resolveDirectStoneLevel("60级"))
        assertEquals("", StoneOcrParser.resolveDirectStoneLevel("级"))
        assertEquals("", StoneOcrParser.resolveDirectStoneLevel("99级"))
        assertEquals("", StoneOcrParser.resolveDirectStoneLevel("60"))
    }

    @Test
    fun `direct name recognition only accepts exact dictionary names`() {
        assertEquals("天同", StoneOcrParser.resolveDirectStoneName("天同", MyStoneStore.TYPE_MAIN))
        assertEquals("", StoneOcrParser.resolveDirectStoneName("同", MyStoneStore.TYPE_MAIN))
        assertEquals("", StoneOcrParser.resolveDirectStoneName("夭机", MyStoneStore.TYPE_MAIN))
    }

    @Test
    fun `support stone aggregate rejects main stone names`() {
        val rows = listOf(
            MyStoneRow(
                mutableListOf(
                    MyStoneCell(level = "60级", name = "武曲")
                )
            ),
            MyStoneRow(
                mutableListOf(
                    MyStoneCell(level = "60级", name = "文曲")
                )
            )
        )

        val stats = StoneOcrParser.aggregate(rows, MyStoneStore.TYPE_SUPPORT)

        assertEquals(listOf("文曲"), stats.map { it.name })
    }

    @Test
    fun `row merge prefers direct split over repaired whole when both are valid and different`() {
        val directWhole = listOf("", "60级", "", "")
        val directSplit = listOf("60级", "", "", "")
        val repairWhole = listOf("60级", "60级", "", "")
        val repairSplit = listOf("60级", "60级", "", "")

        val merged = StonePaddleLocalRecognizer.mergeCellsForTest(
            directWholeCells = directWhole,
            directSplitCells = directSplit,
            repairWholeCells = repairWhole,
            repairSplitCells = repairSplit,
            kind = "level",
        )

        assertEquals(listOf("60级", "60级", "", ""), merged)
    }

    @Test
    fun `paired text rows build four ordered cells`() {
        val rows = StonePaddleLocalRecognizer.pairRowsForTest(
            imageWidth = 1080,
            imageHeight = 1600,
            levelRows = listOf(
                StonePaddleLocalRecognizer.DebugDetectedRow(
                    text = "10级20级30级40级",
                    left = 120,
                    top = 180,
                    right = 940,
                    bottom = 220
                )
            ),
            nameRows = listOf(
                StonePaddleLocalRecognizer.DebugDetectedRow(
                    text = "武曲天机破军天同",
                    left = 110,
                    top = 310,
                    right = 950,
                    bottom = 350
                )
            )
        )

        assertEquals(1, rows.size)
        assertEquals(4, rows.first().cells.size)
        assertEquals(listOf(0, 1, 2, 3), rows.first().cells.map { it.columnIndex })
        assertTrue(rows.first().cells.zipWithNext().all { (left, right) -> left.left < right.left })
        assertTrue(rows.first().bottom > rows.first().top)
    }

    @Test
    fun `fallback row source is used when preferred source is empty`() {
        val rows = StonePaddleLocalRecognizer.mergeRowSourcesForTest(
            preferred = emptyList(),
            fallback = listOf(
                StonePaddleLocalRecognizer.DebugDetectedRow(
                    text = "10级20级30级40级",
                    left = 120,
                    top = 180,
                    right = 940,
                    bottom = 220
                )
            ),
            kind = "level"
        )

        assertEquals(1, rows.size)
        assertEquals("10级20级30级40级", rows.first().text)
    }

    @Test
    fun `failure message includes candidate summary`() {
        val message = StonePaddleLocalRecognizer.buildFailureMessageForTest(
            levelRowsRaw = emptyList(),
            levelRowsPreprocessed = listOf(
                StonePaddleLocalRecognizer.DebugDetectedRow(
                    text = "10级20级30级40级",
                    left = 100,
                    top = 120,
                    right = 900,
                    bottom = 150
                )
            ),
            nameRowsRaw = emptyList(),
            nameRowsPreprocessed = emptyList(),
            levelRawLines = listOf("10级2O级3O级"),
            levelPreprocessedLines = listOf("10级20级30级40级"),
            nameRawLines = listOf("武曲天机", "测试文本"),
            namePreprocessedLines = emptyList(),
        )

        assertTrue(message.contains("等级行"))
        assertTrue(message.contains("preprocessed=1"))
        assertTrue(message.contains("10级20级30级40级"))
        assertTrue(message.contains("名字行"))
        assertTrue(message.contains("rawLines="))
        assertTrue(message.contains("digits="))
        assertTrue(message.contains("stoneHits="))
    }

    @Test
    fun `paddle boxes cluster into candidate rows`() {
        val rows = StonePaddleLocalRecognizer.clusterDetectedBoxesForTest(
            boxes = listOf(
                Rect(100, 100, 180, 140),
                Rect(210, 104, 290, 144),
                Rect(320, 98, 400, 142),
                Rect(120, 220, 210, 260),
            )
        )

        assertEquals(2, rows.size)
        assertTrue(rows.first().top < rows.last().top)
        assertTrue(rows.first().right > rows.first().left)
    }
}

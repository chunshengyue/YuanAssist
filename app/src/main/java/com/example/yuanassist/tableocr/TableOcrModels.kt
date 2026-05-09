package com.example.yuanassist.tableocr

data class CellBox(val row: Int, val col: Int, val x: Int, val y: Int, val w: Int, val h: Int)

data class TableStructure(
    val rowLines: List<Int>,
    val colLines: List<Int>,
    val width: Int = 0,
    val height: Int = 0
)

data class LineSegment(
    val top: Int,
    val bottom: Int,
    val height: Int,
    val pixelDensity: Float,
    val isNoise: Boolean = false
)

data class RowResult(val roundLabel: String, val actions: List<String>)

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }

data class ParseResult(
    val text: String,
    val isComplete: Boolean,
    val fragment: String = "",
    val confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
    val wasFixed: Boolean = false
)

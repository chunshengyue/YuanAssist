package com.example.yuanassist.utils

data class StoneLocalOcrSession(
    val wordGroups: List<List<String>>,
    val rawLogLines: List<String>,
    val strategyUsed: String,
    val rows: List<MyStoneRow> = emptyList(),
)

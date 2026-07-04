package com.example.yuanassist.core

import com.example.yuanassist.model.AgentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterImportFateScorerTest {
    @Test
    fun zhangKaiFatesOutrankSingleSharedFate() {
        val rawFates = listOf(
            "支能触发持续\n伤害",
            "初始能量+2",
            "迅步潜影",
        )

        val zhangKai = CharacterImportFateScorer.scoreAgent(
            rawFates,
            AgentRepository.AGENT_MAP.getValue("张闿").talents.values,
        )
        val jiaXu = CharacterImportFateScorer.scoreAgent(
            rawFates,
            AgentRepository.AGENT_MAP.getValue("贾诩").talents.values,
        )

        assertEquals(3, zhangKai.hitCount)
        assertEquals(1, jiaXu.hitCount)
        assertTrue(zhangKai.fateScore > jiaXu.fateScore)
    }

    @Test
    fun latinLettersDoNotReduceChineseFateMatch() {
        val result = CharacterImportFateScorer.scoreAgent(
            listOf("De\n霜重鼓寒"),
            AgentRepository.AGENT_MAP.getValue("刘豹").talents.values,
        )

        assertEquals("橙霜重鼓寒", result.correctedFates.single()?.displayText)
        assertEquals(1f, result.correctedFates.single()?.score ?: 0f, 0.001f)
    }

    @Test
    fun zhangMiaoUniqueFatesSurviveCommonEnergyFate() {
        val result = CharacterImportFateScorer.scoreAgent(
            listOf("无晴却有晴", "初始能量+3", "风属性密探强\n花"),
            AgentRepository.AGENT_MAP.getValue("张邈").talents.values,
        )

        assertEquals(3, result.hitCount)
        assertTrue(result.uniqueHitCount >= 2)
        assertTrue(result.fateScore >= 0.85f)
    }
}

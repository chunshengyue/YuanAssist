package com.example.yuanassist.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterImportInferenceDeciderTest {

    @Test
    fun `accepts same surname candidate when fate evidence is strong`() {
        val accepted = CharacterImportInferenceDecider.shouldAccept(
            rawName = "张阀",
            bestName = "张闿",
            bestTotalScore = 0.66f,
            bestFateScore = 0.67f,
            bestFateHitCount = 2,
            bestUniqueFateHitCount = 0,
            bestUniqueFateScore = 0f,
            secondTotalScore = 0.62f,
            correctedName = null,
        )

        assertTrue(accepted)
    }

    @Test
    fun `accepts candidate when any character overlaps and fate evidence is strong`() {
        val accepted = CharacterImportInferenceDecider.shouldAccept(
            rawName = "阿闿",
            bestName = "张闿",
            bestTotalScore = 0.65f,
            bestFateScore = 0.67f,
            bestFateHitCount = 2,
            bestUniqueFateHitCount = 0,
            bestUniqueFateScore = 0f,
            secondTotalScore = 0.61f,
            correctedName = null,
        )

        assertTrue(accepted)
    }

    @Test
    fun `rejects weak inference when fate evidence is not enough`() {
        val accepted = CharacterImportInferenceDecider.shouldAccept(
            rawName = "张阀",
            bestName = "张闿",
            bestTotalScore = 0.58f,
            bestFateScore = 0.55f,
            bestFateHitCount = 1,
            bestUniqueFateHitCount = 0,
            bestUniqueFateScore = 0f,
            secondTotalScore = 0.54f,
            correctedName = null,
        )

        assertFalse(accepted)
    }
}

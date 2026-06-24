package at.posselt.pfrpg2e.companion

import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionLevelTest {

    @Test
    fun testClampCompanionLevel_low() {
        assertEquals(1, clampCompanionLevel(0))
        assertEquals(1, clampCompanionLevel(-5))
    }

    @Test
    fun testClampCompanionLevel_high() {
        assertEquals(20, clampCompanionLevel(21))
        assertEquals(20, clampCompanionLevel(100))
    }

    @Test
    fun testClampCompanionLevel_valid() {
        assertEquals(1, clampCompanionLevel(1))
        assertEquals(10, clampCompanionLevel(10))
        assertEquals(20, clampCompanionLevel(20))
    }

    @Test
    fun testApplyCompanionXp_noLevelUp() {
        val result = applyCompanionXp(currentLevel = 1, currentXp = 500, gainedXp = 200)
        assertEquals(1, result.newLevel)
        assertEquals(700, result.newXp)
        assertEquals(0, result.levelsGained)
    }

    @Test
    fun testApplyCompanionXp_oneLevelUp() {
        val result = applyCompanionXp(currentLevel = 1, currentXp = 600, gainedXp = 500)
        assertEquals(2, result.newLevel)
        assertEquals(100, result.newXp)
        assertEquals(1, result.levelsGained)
    }

    @Test
    fun testApplyCompanionXp_multipleLevelUps() {
        val result = applyCompanionXp(currentLevel = 1, currentXp = 0, gainedXp = 3500)
        assertEquals(4, result.newLevel)
        assertEquals(500, result.newXp)
        assertEquals(3, result.levelsGained)
    }

    @Test
    fun testApplyCompanionXp_capsAtMaxLevel() {
        val result = applyCompanionXp(currentLevel = 19, currentXp = 500, gainedXp = 2000)
        assertEquals(20, result.newLevel)
        assertEquals(0, result.newXp)
        assertEquals(1, result.levelsGained)
    }

    @Test
    fun testApplyCompanionXp_atMaxLevel_noOverflow() {
        val result = applyCompanionXp(currentLevel = 20, currentXp = 0, gainedXp = 5000)
        assertEquals(20, result.newLevel)
        assertEquals(0, result.newXp)
        assertEquals(0, result.levelsGained)
    }

    @Test
    fun testApplyCompanionXp_negativeGainedXp_isFlooredToZero() {
        val result = applyCompanionXp(currentLevel = 5, currentXp = 100, gainedXp = -200)
        assertEquals(5, result.newLevel)
        assertEquals(100, result.newXp)
        assertEquals(0, result.levelsGained)
    }

    @Test
    fun testApplyCompanionXp_exactly1000Xp_singleLevel() {
        val result = applyCompanionXp(currentLevel = 1, currentXp = 0, gainedXp = 1000)
        assertEquals(2, result.newLevel)
        assertEquals(0, result.newXp)
        assertEquals(1, result.levelsGained)
    }

    @Test
    fun testApplyCompanionXp_maxLevelConsumesAllXp() {
        val result = applyCompanionXp(currentLevel = 20, currentXp = 999, gainedXp = 1000)
        assertEquals(20, result.newLevel)
        assertEquals(0, result.newXp)
        assertEquals(0, result.levelsGained)
    }
}

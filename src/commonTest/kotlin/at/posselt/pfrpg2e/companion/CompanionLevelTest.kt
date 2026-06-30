package at.posselt.pfrpg2e.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    // ── Expedition XP suppression (companion-leveling opt-out) ──────────────

    @Test
    fun testAccruedExpeditionXp_enabled_awardsXp() {
        assertEquals(80, accruedExpeditionXp(xpAwarded = 80, levelingEnabled = true))
    }

    @Test
    fun testAccruedExpeditionXp_disabled_awardsZero() {
        assertEquals(0, accruedExpeditionXp(xpAwarded = 80, levelingEnabled = false))
    }

    @Test
    fun testShouldOfferLevelUp_suppressedWhenLevelingOff() {
        // Would cross the threshold, but leveling is disabled -> no offer.
        assertFalse(
            shouldOfferLevelUp(
                levelingEnabled = false,
                isNpc = false,
                currentLevel = 1,
                currentXp = 900,
                xpAwarded = 200,
            )
        )
    }

    @Test
    fun testShouldOfferLevelUp_suppressedForNpc() {
        assertFalse(
            shouldOfferLevelUp(
                levelingEnabled = true,
                isNpc = true,
                currentLevel = 1,
                currentXp = 900,
                xpAwarded = 200,
            )
        )
    }

    @Test
    fun testShouldOfferLevelUp_whenCrossingThreshold() {
        assertTrue(
            shouldOfferLevelUp(
                levelingEnabled = true,
                isNpc = false,
                currentLevel = 1,
                currentXp = 900,
                xpAwarded = 200,
            )
        )
    }

    @Test
    fun testShouldOfferLevelUp_notWhenBelowThreshold() {
        assertFalse(
            shouldOfferLevelUp(
                levelingEnabled = true,
                isNpc = false,
                currentLevel = 1,
                currentXp = 100,
                xpAwarded = 200,
            )
        )
    }

    @Test
    fun testShouldOfferLevelUp_notAtMaxLevel() {
        assertFalse(
            shouldOfferLevelUp(
                levelingEnabled = true,
                isNpc = false,
                currentLevel = 20,
                currentXp = 999,
                xpAwarded = 1000,
            )
        )
    }

    // ── Personal-quest reward (idempotent apply) ────────────────────────────

    @Test
    fun testApplyPersonalQuestReward_appliesInfluenceAndXpOnce() {
        val outcome = applyPersonalQuestReward(
            status = "active",
            currentInfluence = 4,
            influenceReward = 2,
            currentLevel = 1,
            currentXp = 900,
            questXp = 200,
            levelingEnabled = true,
        )
        assertTrue(outcome.applied)
        assertEquals("completed", outcome.newStatus)
        assertEquals(6, outcome.newInfluence)
        assertEquals(2, outcome.levelResult.newLevel)
        assertEquals(100, outcome.levelResult.newXp)
        assertEquals(1, outcome.levelResult.levelsGained)
    }

    @Test
    fun testApplyPersonalQuestReward_idempotentWhenAlreadyCompleted() {
        // A non-active quest is a no-op so re-running the reward never double-applies.
        val outcome = applyPersonalQuestReward(
            status = "completed",
            currentInfluence = 6,
            influenceReward = 2,
            currentLevel = 2,
            currentXp = 100,
            questXp = 200,
            levelingEnabled = true,
        )
        assertFalse(outcome.applied)
        assertEquals("completed", outcome.newStatus)
        assertEquals(6, outcome.newInfluence)
        assertEquals(2, outcome.levelResult.newLevel)
        assertEquals(100, outcome.levelResult.newXp)
        assertEquals(0, outcome.levelResult.levelsGained)
    }

    @Test
    fun testApplyPersonalQuestReward_suppressesXpWhenLevelingOff() {
        // Influence still applies and the quest completes, but no XP is granted.
        val outcome = applyPersonalQuestReward(
            status = "active",
            currentInfluence = 4,
            influenceReward = 2,
            currentLevel = 1,
            currentXp = 900,
            questXp = 200,
            levelingEnabled = false,
        )
        assertTrue(outcome.applied)
        assertEquals("completed", outcome.newStatus)
        assertEquals(6, outcome.newInfluence)
        assertEquals(1, outcome.levelResult.newLevel)
        assertEquals(900, outcome.levelResult.newXp)
        assertEquals(0, outcome.levelResult.levelsGained)
    }

    // ── Expedition reward double-apply guard ────────────────────────────────

    @Test
    fun testCanApplyExpeditionReward_allowsFreshExpedition() {
        assertTrue(canApplyExpeditionReward(rewardApplied = false, status = "awaitingResolution"))
    }

    @Test
    fun testCanApplyExpeditionReward_blocksAlreadyApplied() {
        assertFalse(canApplyExpeditionReward(rewardApplied = true, status = "awaitingResolution"))
    }

    @Test
    fun testCanApplyExpeditionReward_blocksResolved() {
        assertFalse(canApplyExpeditionReward(rewardApplied = false, status = "resolved"))
    }
}

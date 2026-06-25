package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpeditionResolverEngineTest {

    @Test
    fun testCriticalSuccess_standardTier() {
        val result = ExpeditionResolverEngine.resolve(
            baseXp = 80,
            baseInfluence = 1,
            tier = "standard",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
        )
        assertEquals(80, result.xpAwarded) // 80 * 1.0
        assertEquals(1, result.influenceDelta)
        assertEquals("major", result.lootTier)
        assertTrue(result.injuryConditions.isEmpty())
        assertEquals(0, result.factionStandingDelta)
    }

    @Test
    fun testSuccess_standardTier() {
        val result = ExpeditionResolverEngine.resolve(
            baseXp = 80,
            baseInfluence = 1,
            tier = "standard",
            degree = DegreeOfSuccess.SUCCESS,
        )
        assertEquals(80, result.xpAwarded)
        assertEquals(1, result.influenceDelta)
        assertEquals("moderate", result.lootTier)
        assertTrue(result.injuryConditions.isEmpty())
    }

    @Test
    fun testFailure_standardTier() {
        val result = ExpeditionResolverEngine.resolve(
            baseXp = 30,
            baseInfluence = 1,
            tier = "standard",
            degree = DegreeOfSuccess.FAILURE,
        )
        assertEquals(30, result.xpAwarded)
        assertEquals(0, result.influenceDelta)
        assertEquals("none", result.lootTier)
        assertTrue(result.injuryConditions.isEmpty())
        assertEquals("lost time, returned whole", result.gmNotes)
    }

    @Test
    fun testCriticalFailure_standardTier() {
        val result = ExpeditionResolverEngine.resolve(
            baseXp = 10,
            baseInfluence = 1,
            tier = "standard",
            degree = DegreeOfSuccess.CRITICAL_FAILURE,
        )
        assertEquals(10, result.xpAwarded)
        assertEquals(0, result.influenceDelta) // anti-spiral: no subtraction
        assertEquals("none", result.lootTier)
        assertEquals(2, result.injuryConditions.size)
        assertTrue(result.injuryConditions.contains("fatigued"))
        assertTrue(result.injuryConditions.contains("wounded"))
    }

    @Test
    fun testRoutineTier_multiplier() {
        val result = ExpeditionResolverEngine.resolve(
            baseXp = 80,
            baseInfluence = 1,
            tier = "routine",
            degree = DegreeOfSuccess.SUCCESS,
        )
        assertEquals(60, result.xpAwarded) // 80 * 0.75 = 60
    }

    @Test
    fun testPerilousTier_multiplier() {
        val result = ExpeditionResolverEngine.resolve(
            baseXp = 80,
            baseInfluence = 1,
            tier = "perilous",
            degree = DegreeOfSuccess.SUCCESS,
        )
        assertEquals(120, result.xpAwarded) // 80 * 1.5 = 120
    }

    @Test
    fun testRoutineTier_criticalFailure() {
        val result = ExpeditionResolverEngine.resolve(
            baseXp = 10,
            baseInfluence = 1,
            tier = "routine",
            degree = DegreeOfSuccess.CRITICAL_FAILURE,
        )
        assertEquals(7, result.xpAwarded) // 10 * 0.75 = 7 (truncated)
        assertEquals(0, result.influenceDelta)
        assertTrue(result.injuryConditions.contains("fatigued"))
    }

    @Test
    fun testTierMultiplier() {
        assertEquals(0.75, ExpeditionResolverEngine.tierMultiplier("routine"))
        assertEquals(1.0, ExpeditionResolverEngine.tierMultiplier("standard"))
        assertEquals(1.5, ExpeditionResolverEngine.tierMultiplier("perilous"))
        assertEquals(1.0, ExpeditionResolverEngine.tierMultiplier("unknown"))
    }

    @Test
    fun testCriticalSuccess_factionStandingPreserved() {
        // Diplomacy expeditions can set faction standing delta
        val result = ExpeditionResolverEngine.resolve(
            baseXp = 80,
            baseInfluence = 2,
            tier = "standard",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
        )
        // The base resolver returns 0 for faction standing — the impure wrapper adds it for diplomacy
        assertEquals(0, result.factionStandingDelta)
    }
}

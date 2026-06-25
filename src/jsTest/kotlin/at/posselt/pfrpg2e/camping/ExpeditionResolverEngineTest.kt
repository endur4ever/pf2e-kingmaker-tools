package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpeditionResolverEngineTest {

    @Test
    fun testCriticalSuccess_standardTier() {
        val result = ExpeditionResolverEngine.resolve(
            baseInfluence = 1,
            tier = "standard",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
        )
        assertEquals(120, result.xpAwarded) // per-degree base 120 * 1.0
        assertEquals(1, result.influenceDelta)
        assertEquals("major", result.lootTier)
        assertTrue(result.injuryConditions.isEmpty())
        assertEquals(0, result.factionStandingDelta)
        assertEquals("", result.gmNotes)
    }

    @Test
    fun testSuccess_standardTier() {
        val result = ExpeditionResolverEngine.resolve(
            baseInfluence = 1,
            tier = "standard",
            degree = DegreeOfSuccess.SUCCESS,
        )
        assertEquals(80, result.xpAwarded) // per-degree base 80 * 1.0
        assertEquals(1, result.influenceDelta)
        assertEquals("moderate", result.lootTier)
        assertTrue(result.injuryConditions.isEmpty())
        assertEquals("", result.gmNotes)
    }

    @Test
    fun testFailure_standardTier() {
        val result = ExpeditionResolverEngine.resolve(
            baseInfluence = 1,
            tier = "standard",
            degree = DegreeOfSuccess.FAILURE,
        )
        assertEquals(30, result.xpAwarded) // per-degree base 30 * 1.0 (anti-death-spiral)
        assertEquals(0, result.influenceDelta)
        assertEquals("none", result.lootTier)
        assertTrue(result.injuryConditions.isEmpty())
        assertEquals("lost time, returned whole", result.gmNotes)
    }

    @Test
    fun testCriticalFailure_standardTier() {
        val result = ExpeditionResolverEngine.resolve(
            baseInfluence = 1,
            tier = "standard",
            degree = DegreeOfSuccess.CRITICAL_FAILURE,
        )
        assertEquals(10, result.xpAwarded) // per-degree base 10 * 1.0 (anti-death-spiral)
        assertEquals(0, result.influenceDelta) // anti-spiral: no subtraction
        assertEquals("none", result.lootTier)
        assertEquals(2, result.injuryConditions.size)
        assertTrue(result.injuryConditions.contains("fatigued"))
        assertTrue(result.injuryConditions.contains("wounded"))
        assertEquals("Seeds a narrative hook for the GM", result.gmNotes)
    }

    @Test
    fun testPerilousTier_perDegreeXp() {
        // ×1.5 on each per-degree base
        assertEquals(180, ExpeditionResolverEngine.resolve(1, "perilous", DegreeOfSuccess.CRITICAL_SUCCESS).xpAwarded)
        assertEquals(120, ExpeditionResolverEngine.resolve(1, "perilous", DegreeOfSuccess.SUCCESS).xpAwarded)
        assertEquals(45, ExpeditionResolverEngine.resolve(1, "perilous", DegreeOfSuccess.FAILURE).xpAwarded)
        assertEquals(15, ExpeditionResolverEngine.resolve(1, "perilous", DegreeOfSuccess.CRITICAL_FAILURE).xpAwarded)
    }

    @Test
    fun testRoutineTier_success_multiplier() {
        val result = ExpeditionResolverEngine.resolve(
            baseInfluence = 1,
            tier = "routine",
            degree = DegreeOfSuccess.SUCCESS,
        )
        assertEquals(60, result.xpAwarded) // 80 * 0.75 = 60
    }

    @Test
    fun testRoutineTier_failure_roundsHalfUp() {
        val result = ExpeditionResolverEngine.resolve(
            baseInfluence = 1,
            tier = "routine",
            degree = DegreeOfSuccess.FAILURE,
        )
        assertEquals(23, result.xpAwarded) // 30 * 0.75 = 22.5 -> rounded (ties toward +inf) = 23
    }

    @Test
    fun testRoutineTier_criticalFailure_roundsHalfUp() {
        val result = ExpeditionResolverEngine.resolve(
            baseInfluence = 1,
            tier = "routine",
            degree = DegreeOfSuccess.CRITICAL_FAILURE,
        )
        assertEquals(8, result.xpAwarded) // 10 * 0.75 = 7.5 -> rounded (ties toward +inf) = 8
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
        // Diplomacy expeditions can set faction standing delta in the impure wrapper;
        // the base resolver returns 0.
        val result = ExpeditionResolverEngine.resolve(
            baseInfluence = 2,
            tier = "standard",
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
        )
        assertEquals(0, result.factionStandingDelta)
        assertEquals(2, result.influenceDelta) // raw baseInfluence, no clamping
    }
}

package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncounterResolverEngineTest {

    @Test
    fun testCriticalSuccess() {
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 25,
            stealthDc = 15,
            degree = DegreeOfSuccess.CRITICAL_SUCCESS
        )
        assertEquals(15, result.attackerStealthDc)
        assertEquals(25, result.watcherPerceptionRoll)
        assertEquals(120.0f, result.distanceToEnemy)
        assertTrue(result.appliedConditions.isEmpty())
        assertEquals("Revealed", result.ambusherState)
        assertTrue(result.gmNotes.contains("detects the enemy early"))
    }

    @Test
    fun testSuccess() {
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS
        )
        assertEquals(15, result.attackerStealthDc)
        assertEquals(18, result.watcherPerceptionRoll)
        assertEquals(60.0f, result.distanceToEnemy)
        assertEquals(1, result.appliedConditions.size)
        assertEquals("prone", result.appliedConditions[0])
        assertEquals("Revealed", result.ambusherState)
        assertTrue(result.gmNotes.contains("Standard encounter start") || result.gmNotes.contains("wake up but start prone"))
    }

    @Test
    fun testFailure() {
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 12,
            stealthDc = 15,
            degree = DegreeOfSuccess.FAILURE
        )
        assertEquals(15, result.attackerStealthDc)
        assertEquals(12, result.watcherPerceptionRoll)
        assertEquals(60.0f, result.distanceToEnemy)
        assertEquals(2, result.appliedConditions.size)
        assertTrue(result.appliedConditions.contains("unconscious"))
        assertTrue(result.appliedConditions.contains("prone"))
        assertEquals("Hidden", result.ambusherState)
        assertTrue(result.gmNotes.contains("successfully ambushes") || result.gmNotes.contains("remain asleep"))
    }

    @Test
    fun testCriticalFailure() {
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 4,
            stealthDc = 15,
            degree = DegreeOfSuccess.CRITICAL_FAILURE
        )
        assertEquals(15, result.attackerStealthDc)
        assertEquals(4, result.watcherPerceptionRoll)
        assertEquals(15.0f, result.distanceToEnemy)
        assertEquals(2, result.appliedConditions.size)
        assertTrue(result.appliedConditions.contains("unconscious"))
        assertTrue(result.appliedConditions.contains("prone"))
        assertEquals("Hidden", result.ambusherState)
        assertTrue(result.gmNotes.contains("severely ambushed"))
    }
}

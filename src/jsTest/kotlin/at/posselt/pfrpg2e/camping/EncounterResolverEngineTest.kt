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

    // Defense-state fixtures
    
    @Test
    fun testSetAlarmsCriticalSuccessAddsPerceptionBonus() {
        val defenseState = CampDefenseState(
            alarmsDegree = "criticalSuccess",
            camouflageDegree = null,
            trapsDegree = null,
            undeadGuardiansActive = null
        )
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 15,  // Would be FAILURE (15 vs DC 15 = success)
            stealthDc = 15,
            degree = DegreeOfSuccess.FAILURE,  // Base roll is failure
            defenseState = defenseState
        )
        // With +4 from alarms, effective roll = 19, which should be SUCCESS against DC 15
        // But the degree is still based on the original roll - we're just adding to roll
        assertEquals(19, result.watcherPerceptionRoll)  // 15 + 4
        assertTrue(result.defenseContributions.any { it.contains("Set Alarms") })
        assertTrue(result.defenseContributions.any { it.contains("+4 Perception") })
    }

    @Test
    fun testSetAlarmsSuccessAddsPerceptionBonus() {
        val defenseState = CampDefenseState(
            alarmsDegree = "success",
            camouflageDegree = null,
            trapsDegree = null,
            undeadGuardiansActive = null
        )
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 10,
            stealthDc = 15,
            degree = DegreeOfSuccess.FAILURE,
            defenseState = defenseState
        )
        assertEquals(12, result.watcherPerceptionRoll)  // 10 + 2
        assertTrue(result.defenseContributions.any { it.contains("Set Alarms") })
        assertTrue(result.defenseContributions.any { it.contains("+2 Perception") })
    }

    @Test
    fun testCamouflageCriticalSuccessWorsensDistanceOneBand() {
        val defenseState = CampDefenseState(
            alarmsDegree = null,
            camouflageDegree = "criticalSuccess",
            trapsDegree = null,
            undeadGuardiansActive = null
        )
        // Critical Success base distance = 120, camouflage CS worsens by 60 -> 60
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 25,
            stealthDc = 15,
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            defenseState = defenseState
        )
        assertEquals(60.0f, result.distanceToEnemy)  // 120 - 60
        assertTrue(result.defenseContributions.any { it.contains("Camouflage Campsite") })
        assertTrue(result.defenseContributions.any { it.contains("worsened by one band") })
    }

    @Test
    fun testCamouflageSuccessWorsensDistanceHalfBand() {
        val defenseState = CampDefenseState(
            alarmsDegree = null,
            camouflageDegree = "success",
            trapsDegree = null,
            undeadGuardiansActive = null
        )
        // Success base distance = 60, camouflage S worsens by 15 -> 45
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,
            defenseState = defenseState
        )
        assertEquals(45.0f, result.distanceToEnemy)  // 60 - 15
        assertTrue(result.defenseContributions.any { it.contains("Camouflage Campsite") })
        assertTrue(result.defenseContributions.any { it.contains("half band") })
    }

    @Test
    fun testCamouflageCriticalFailureImprovesAmbusherDistance() {
        val defenseState = CampDefenseState(
            alarmsDegree = null,
            camouflageDegree = "criticalFailure",
            trapsDegree = null,
            undeadGuardiansActive = null
        )
        // Failure base distance = 60, camouflage CF improves by 60 -> 120 (capped)
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 12,
            stealthDc = 15,
            degree = DegreeOfSuccess.FAILURE,
            defenseState = defenseState
        )
        assertEquals(120.0f, result.distanceToEnemy)  // 60 + 60 = 120 (capped at 120)
        assertTrue(result.defenseContributions.any { it.contains("Camouflage Campsite") })
        assertTrue(result.defenseContributions.any { it.contains("improved by one band") })
    }

    @Test
    fun testSetTrapsCriticalSuccessAddsContribution() {
        val defenseState = CampDefenseState(
            alarmsDegree = null,
            camouflageDegree = null,
            trapsDegree = "criticalSuccess",
            undeadGuardiansActive = null
        )
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,
            defenseState = defenseState
        )
        assertTrue(result.defenseContributions.any { it.contains("Set Traps") })
        assertTrue(result.defenseContributions.any { it.contains("4d6 damage") })
    }

    @Test
    fun testSetTrapsSuccessAddsContribution() {
        val defenseState = CampDefenseState(
            alarmsDegree = null,
            camouflageDegree = null,
            trapsDegree = "success",
            undeadGuardiansActive = null
        )
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,
            defenseState = defenseState
        )
        assertTrue(result.defenseContributions.any { it.contains("Set Traps") })
        assertTrue(result.defenseContributions.any { it.contains("2d6 damage") })
    }

    @Test
    fun testSetTrapsFailureNoContribution() {
        val defenseState = CampDefenseState(
            alarmsDegree = null,
            camouflageDegree = null,
            trapsDegree = "failure",
            undeadGuardiansActive = null
        )
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,
            defenseState = defenseState
        )
        assertTrue(result.defenseContributions.none { it.contains("Set Traps") })
    }

    @Test
    fun testUndeadGuardiansActiveAddsContribution() {
        val defenseState = CampDefenseState(
            alarmsDegree = null,
            camouflageDegree = null,
            trapsDegree = null,
            undeadGuardiansActive = true
        )
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,
            defenseState = defenseState
        )
        assertTrue(result.defenseContributions.any { it.contains("Undead Guardians") })
        assertTrue(result.defenseContributions.any { it.contains("Extra watcher-equivalent") })
    }

    @Test
    fun testUndeadGuardiansInactiveNoContribution() {
        val defenseState = CampDefenseState(
            alarmsDegree = null,
            camouflageDegree = null,
            trapsDegree = null,
            undeadGuardiansActive = false
        )
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,
            defenseState = defenseState
        )
        assertTrue(result.defenseContributions.none { it.contains("Undead Guardians") })
    }

    @Test
    fun testMultipleDefensesCombined() {
        val defenseState = CampDefenseState(
            alarmsDegree = "success",
            camouflageDegree = "criticalSuccess",
            trapsDegree = "success",
            undeadGuardiansActive = true
        )
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 15,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,
            defenseState = defenseState
        )
        // +2 from alarms = 17 effective roll
        assertEquals(17, result.watcherPerceptionRoll)
        // Camouflage CS worsens SUCCESS distance (60) by 60 -> 0, clamped to 15 min
        assertEquals(15.0f, result.distanceToEnemy)
        assertEquals(4, result.defenseContributions.size)
    }
}

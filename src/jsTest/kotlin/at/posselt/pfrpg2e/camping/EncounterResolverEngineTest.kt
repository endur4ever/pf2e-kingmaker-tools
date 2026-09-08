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
        assertTrue(result.gmNotes.contains("camping.encounterResolution.gmNotes.criticalSuccess"))
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
        assertTrue(result.gmNotes.contains("camping.encounterResolution.gmNotes.success"))
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
        assertTrue(result.gmNotes.contains("camping.encounterResolution.gmNotes.failure"))
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
        assertTrue(result.gmNotes.contains("camping.encounterResolution.gmNotes.criticalFailure"))
    }

    // ── Set Alarms ──────────────────────────────────────────────────────────────────────────────
    // The bonus is applied to the watch roll's DC by the rest flow BEFORE rolling (that's what
    // makes it change the outcome); the resolver itself only reports the contribution and must
    // NOT inflate the displayed roll.

    @Test
    fun alarmsPerceptionBonusMapping() {
        assertEquals(4, alarmsPerceptionBonus(DegreeOfSuccess.CRITICAL_SUCCESS))
        assertEquals(2, alarmsPerceptionBonus(DegreeOfSuccess.SUCCESS))
        assertEquals(0, alarmsPerceptionBonus(DegreeOfSuccess.FAILURE))
        assertEquals(-2, alarmsPerceptionBonus(DegreeOfSuccess.CRITICAL_FAILURE))
        assertEquals(0, alarmsPerceptionBonus(null))
    }

    @Test
    fun alarmsAddContributionWithoutInflatingTheDisplayedRoll() {
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,
            defenseState = CampDefenseState(alarmsDegree = DegreeOfSuccess.CRITICAL_SUCCESS)
        )
        assertEquals(18, result.watcherPerceptionRoll)  // the REAL roll, not roll + 4
        assertTrue(result.defenseContributions.any { it.contains("camping.encounterResolution.setAlarms.bonus") && it.contains("bonus=4") })
    }

    @Test
    fun alarmsCriticalFailureReportsThePenalty() {
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 12,
            stealthDc = 15,
            degree = DegreeOfSuccess.FAILURE,
            defenseState = CampDefenseState(alarmsDegree = DegreeOfSuccess.CRITICAL_FAILURE)
        )
        assertTrue(result.defenseContributions.any { it.contains("camping.encounterResolution.setAlarms.penalty") && it.contains("bonus=-2") })
    }

    // ── Camouflage Campsite ─────────────────────────────────────────────────────────────────────
    // Larger distance always favors the party: a defense the party invested in pushes the
    // ambusher OUT (the original cut had the sign inverted).

    @Test
    fun camouflageCriticalSuccessPushesAmbusherAFullBandOut() {
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,  // base 60 ft
            defenseState = CampDefenseState(camouflageDegree = DegreeOfSuccess.CRITICAL_SUCCESS)
        )
        assertEquals(120.0f, result.distanceToEnemy)  // 60 + 60
        assertTrue(result.defenseContributions.any { it.contains("camping.encounterResolution.camouflage.") })
    }

    @Test
    fun camouflageSuccessPushesAmbusherHalfABandOut() {
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 12,
            stealthDc = 15,
            degree = DegreeOfSuccess.FAILURE,  // base 60 ft
            defenseState = CampDefenseState(camouflageDegree = DegreeOfSuccess.SUCCESS)
        )
        assertEquals(75.0f, result.distanceToEnemy)  // 60 + 15
    }

    @Test
    fun camouflageCriticalFailureLetsTheAmbusherSlipCloser() {
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,  // base 60 ft
            defenseState = CampDefenseState(camouflageDegree = DegreeOfSuccess.CRITICAL_FAILURE)
        )
        assertEquals(15.0f, result.distanceToEnemy)  // 60 - 60, clamped to the 15 ft floor
        assertTrue(result.defenseContributions.any { it.contains("camping.encounterResolution.camouflage.criticalFailure") })
    }

    @Test
    fun camouflageDistanceClampsToTheBands() {
        // already at 120 (crit success watch) — camouflage cannot push past the ceiling
        val atCeiling = EncounterResolverEngine.resolve(
            watcherRoll = 25,
            stealthDc = 15,
            degree = DegreeOfSuccess.CRITICAL_SUCCESS,
            defenseState = CampDefenseState(camouflageDegree = DegreeOfSuccess.CRITICAL_SUCCESS)
        )
        assertEquals(120.0f, atCeiling.distanceToEnemy)
        // already at 15 (crit failure watch) — a botched camouflage cannot go below the floor
        val atFloor = EncounterResolverEngine.resolve(
            watcherRoll = 4,
            stealthDc = 15,
            degree = DegreeOfSuccess.CRITICAL_FAILURE,
            defenseState = CampDefenseState(camouflageDegree = DegreeOfSuccess.CRITICAL_FAILURE)
        )
        assertEquals(15.0f, atFloor.distanceToEnemy)
    }

    // ── Set Traps ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun trapsContributeOnSuccessAndCritOnly() {
        fun trapsLines(degree: DegreeOfSuccess) = EncounterResolverEngine.resolve(
            watcherRoll = 12,
            stealthDc = 15,
            degree = DegreeOfSuccess.FAILURE,
            defenseState = CampDefenseState(trapsDegree = degree)
        ).defenseContributions.filter { it.contains("camping.encounterResolution.setTraps.") }

        assertTrue(trapsLines(DegreeOfSuccess.CRITICAL_SUCCESS).any { it.contains("setTraps.criticalSuccess") })
        assertTrue(trapsLines(DegreeOfSuccess.SUCCESS).any { it.contains("setTraps.success") })
        assertTrue(trapsLines(DegreeOfSuccess.FAILURE).isEmpty())
        assertTrue(trapsLines(DegreeOfSuccess.CRITICAL_FAILURE).isEmpty())
    }

    // ── Undead Guardians ────────────────────────────────────────────────────────────────────────

    @Test
    fun undeadGuardiansContributeOnlyWhenActive() {
        fun guardianLines(active: Boolean?) = EncounterResolverEngine.resolve(
            watcherRoll = 12,
            stealthDc = 15,
            degree = DegreeOfSuccess.FAILURE,
            defenseState = CampDefenseState(undeadGuardiansActive = active)
        ).defenseContributions.filter { it.contains("camping.encounterResolution.undeadGuardians") }

        assertEquals(1, guardianLines(true).size)
        assertTrue(guardianLines(false).isEmpty())
        assertTrue(guardianLines(null).isEmpty())
    }

    // ── combinations ────────────────────────────────────────────────────────────────────────────

    @Test
    fun multipleDefensesAllContribute() {
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,
            defenseState = CampDefenseState(
                alarmsDegree = DegreeOfSuccess.SUCCESS,
                camouflageDegree = DegreeOfSuccess.SUCCESS,
                trapsDegree = DegreeOfSuccess.SUCCESS,
                undeadGuardiansActive = true,
            )
        )
        assertEquals(4, result.defenseContributions.size)
        assertEquals(75.0f, result.distanceToEnemy)  // 60 + 15 from camouflage
    }

    @Test
    fun noDefenseStateMeansNoContributions() {
        val result = EncounterResolverEngine.resolve(
            watcherRoll = 18,
            stealthDc = 15,
            degree = DegreeOfSuccess.SUCCESS,
        )
        assertTrue(result.defenseContributions.isEmpty())
    }
}

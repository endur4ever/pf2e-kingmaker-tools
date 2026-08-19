package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.actor.Proficiency
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubsistYieldTest {
    private fun yield(
        degree: DegreeOfSuccess,
        forager: Boolean = false,
        cloak: Boolean = false,
        greaterCloak: Boolean = false,
        proficiency: Proficiency = Proficiency.UNTRAINED,
    ) = calculateProvisions(
        isForager = forager,
        hasCoyoteCloak = cloak,
        hasCoyoteCloakGreat = greaterCloak,
        degree = degree,
        survivalProficiency = proficiency,
    )

    @Test
    fun failingToSubsistYieldsNothing() {
        assertEquals(0, yield(DegreeOfSuccess.FAILURE))
        assertEquals(0, yield(DegreeOfSuccess.CRITICAL_FAILURE))
        // ...and a forager who fails still starves; the feat raises yields, not the floor.
        assertEquals(0, yield(DegreeOfSuccess.FAILURE, forager = true, proficiency = Proficiency.LEGENDARY))
    }

    @Test
    fun anOrdinarySuccessFeedsExactlyOne() {
        assertEquals(1, yield(DegreeOfSuccess.SUCCESS))
    }

    @Test
    fun aNonForagerCriticalSuccessYieldsTwoNotOne() {
        // The success bonus falls back to 0 for a non-forager but the CRITICAL bonus falls back to
        // 1, so a crit is 1 + 1. Collapsing both fallbacks to 0 -- the obvious "cleanup" -- would
        // silently make a critical success worth the same as a plain one.
        assertEquals(2, yield(DegreeOfSuccess.CRITICAL_SUCCESS))
    }

    @Test
    fun untrainedAndTrainedForagersYieldTheSame() {
        // The table is not linear in rank; these two ranks are deliberately identical, so a
        // formula-based rewrite would change real yields.
        assertEquals(
            yield(DegreeOfSuccess.SUCCESS, forager = true, proficiency = Proficiency.UNTRAINED),
            yield(DegreeOfSuccess.SUCCESS, forager = true, proficiency = Proficiency.TRAINED),
        )
        assertEquals(5, yield(DegreeOfSuccess.SUCCESS, forager = true, proficiency = Proficiency.TRAINED))
        assertEquals(
            yield(DegreeOfSuccess.CRITICAL_SUCCESS, forager = true, proficiency = Proficiency.UNTRAINED),
            yield(DegreeOfSuccess.CRITICAL_SUCCESS, forager = true, proficiency = Proficiency.TRAINED),
        )
    }

    @Test
    fun foragerSuccessScalesWithProficiencyAboveExpert() {
        assertEquals(9, yield(DegreeOfSuccess.SUCCESS, forager = true, proficiency = Proficiency.EXPERT))
        assertEquals(17, yield(DegreeOfSuccess.SUCCESS, forager = true, proficiency = Proficiency.MASTER))
        assertEquals(33, yield(DegreeOfSuccess.SUCCESS, forager = true, proficiency = Proficiency.LEGENDARY))
    }

    @Test
    fun theCoyoteCloakMultipliesOnlyTheCriticalBonusNotTheBaseProvision() {
        // 1 + (8 * 2) = 17, NOT (1 + 8) * 2 = 18. The base provision is outside the multiplier.
        assertEquals(17, yield(DegreeOfSuccess.CRITICAL_SUCCESS, forager = true, cloak = true, proficiency = Proficiency.TRAINED))
        assertEquals(33, yield(DegreeOfSuccess.CRITICAL_SUCCESS, forager = true, greaterCloak = true, proficiency = Proficiency.TRAINED))
    }

    @Test
    fun theGreaterCloakSupersedesThePlainOne() {
        // Both invested must not stack into an 8x multiplier.
        assertEquals(
            yield(DegreeOfSuccess.CRITICAL_SUCCESS, forager = true, greaterCloak = true, proficiency = Proficiency.TRAINED),
            yield(DegreeOfSuccess.CRITICAL_SUCCESS, forager = true, cloak = true, greaterCloak = true, proficiency = Proficiency.TRAINED),
        )
    }

    @Test
    fun theCloakDoesNothingOnANonCriticalSuccess() {
        assertEquals(
            yield(DegreeOfSuccess.SUCCESS, forager = true, proficiency = Proficiency.TRAINED),
            yield(DegreeOfSuccess.SUCCESS, forager = true, greaterCloak = true, proficiency = Proficiency.TRAINED),
        )
    }

    @Test
    fun onlyANonZeroYieldFeedsTheActor() {
        // This is the meal loop's join: zero provisions means the night was spent hungry.
        assertFalse(subsistFedActor(0))
        assertTrue(subsistFedActor(1))
        assertTrue(subsistFedActor(33))
    }
}

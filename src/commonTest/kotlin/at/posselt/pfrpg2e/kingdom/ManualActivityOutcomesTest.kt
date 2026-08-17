package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualActivityOutcomesTest {
    // ── Hire Adventurers ────────────────────────────────────────────────────────────────────────
    @Test
    fun hireAdventurersCostEscalatesAfterAFailure() {
        assertEquals(1, hireAdventurersRdCost(baseRdCost = 1, hasFailedThisEvent = false))
        assertEquals(2, hireAdventurersRdCost(baseRdCost = 1, hasFailedThisEvent = true))
    }

    // ── Decadent Feasts unrest shield ───────────────────────────────────────────────────────────
    @Test
    fun unrestShieldNegatesOneEffectThenIsConsumed() {
        val r = applyUnrestShield(unrestGain = 3, shieldActive = true)
        assertEquals(0, r.netUnrestGain)
        assertTrue(r.shieldConsumed)
    }

    @Test
    fun unrestPassesThroughWithoutAShield() {
        val r = applyUnrestShield(unrestGain = 3, shieldActive = false)
        assertEquals(3, r.netUnrestGain)
        assertFalse(r.shieldConsumed)
    }

    @Test
    fun shieldIsNotTrippedByANonIncrease() {
        val r = applyUnrestShield(unrestGain = 0, shieldActive = true)
        assertEquals(0, r.netUnrestGain)
        assertFalse(r.shieldConsumed)  // preserved for a real unrest increase later
    }

    // ── Irrigation plague flat check ────────────────────────────────────────────────────────────
    @Test
    fun irrigationFlatCheckDcRisesWithCritFailedHexes() {
        assertEquals(0, irrigationPlagueFlatCheckDc(critFailedIrrigationHexes = 0))  // no check
        assertEquals(4, irrigationPlagueFlatCheckDc(critFailedIrrigationHexes = 1))  // base
        assertEquals(5, irrigationPlagueFlatCheckDc(critFailedIrrigationHexes = 2))
        assertEquals(7, irrigationPlagueFlatCheckDc(critFailedIrrigationHexes = 4))
    }

    @Test
    fun oneSpoiledHexUsesTheActivitysPrintedDc() {
        // The rule reads "attempt a flat DC 4 check ... increases by 1 for each hex that contains a
        // critically failed attempt". Base 4 is the single-hex case, so DC 4 is actually reachable;
        // reading it as 4 + count would mean the printed number never appears in play.
        assertEquals(IRRIGATION_PLAGUE_FLAT_CHECK_BASE_DC, irrigationPlagueFlatCheckDc(1))
        assertEquals(4, irrigationPlagueFlatCheckDc(1))
    }

    @Test
    fun eachFurtherSpoiledHexRaisesTheDc() {
        assertEquals(5, irrigationPlagueFlatCheckDc(2))
        assertEquals(8, irrigationPlagueFlatCheckDc(5))
    }

    @Test
    fun noSpoiledHexesMeansNoCheckRuns() {
        assertEquals(0, irrigationPlagueFlatCheckDc(0))
        assertEquals(0, irrigationPlagueFlatCheckDc(-1))
    }

    @Test
    fun hireAdventurersCostsDoubleOnlyAfterAFailureAgainstThatEvent() {
        assertEquals(1, hireAdventurersRdCost(baseRdCost = 1, hasFailedThisEvent = false))
        assertEquals(HIRE_ADVENTURERS_ESCALATED_RD_COST, hireAdventurersRdCost(baseRdCost = 1, hasFailedThisEvent = true))
        // The escalated cost is a flat 2 Resource Dice, not a doubling of whatever the base was.
        assertEquals(2, hireAdventurersRdCost(baseRdCost = 3, hasFailedThisEvent = true))
    }
}

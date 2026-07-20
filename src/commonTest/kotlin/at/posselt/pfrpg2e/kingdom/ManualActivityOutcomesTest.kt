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
}

package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StarvationTrackTest {
    // ── threshold ───────────────────────────────────────────────────────────────────────────────
    @Test
    fun thresholdIsOnePlusConModWithAFloorOfOne() {
        assertEquals(4, starvationThresholdDays(constitutionModifier = 3))
        assertEquals(1, starvationThresholdDays(constitutionModifier = 0))
        assertEquals(1, starvationThresholdDays(constitutionModifier = -3))  // never below 1
    }

    // ── counter ─────────────────────────────────────────────────────────────────────────────────
    @Test
    fun goingUnfedIncrementsAndBeingFedResets() {
        val start = StarvationState(daysWithoutFood = 2)
        assertEquals(3, tickStarvation(start, fed = false).daysWithoutFood)
        assertEquals(0, tickStarvation(start, fed = true).daysWithoutFood)
    }

    // ── severity bands ──────────────────────────────────────────────────────────────────────────
    @Test
    fun withinThresholdThereIsNoEffect() {
        // con mod 0 -> threshold 1; days 0 and 1 are still NONE
        assertEquals(StarvationSeverity.NONE, starvationSeverity(0, 0))
        assertEquals(StarvationSeverity.NONE, starvationSeverity(1, 0))
    }

    @Test
    fun pastThresholdIsFatiguedThenWorsening() {
        // con mod 1 -> threshold 2
        assertEquals(StarvationSeverity.NONE, starvationSeverity(2, 1))
        assertEquals(StarvationSeverity.FATIGUED, starvationSeverity(3, 1))
        assertEquals(StarvationSeverity.FATIGUED, starvationSeverity(4, 1))  // up to 2x threshold
        assertEquals(StarvationSeverity.WORSENING, starvationSeverity(5, 1))
    }

    // ── offer trigger ───────────────────────────────────────────────────────────────────────────
    @Test
    fun crossingIntoAWorseBandTriggersTheOffer() {
        // con mod 0 -> threshold 1: day 1 (NONE) -> day 2 (FATIGUED) crosses
        assertTrue(crossedStarvationThreshold(previousDaysWithoutFood = 1, newDaysWithoutFood = 2, constitutionModifier = 0))
        // FATIGUED -> WORSENING also crosses
        assertTrue(crossedStarvationThreshold(previousDaysWithoutFood = 2, newDaysWithoutFood = 3, constitutionModifier = 0))
    }

    @Test
    fun stayingInTheSameBandDoesNotRetrigger() {
        // con mod 3 -> threshold 4: days 5 -> 6 are both FATIGUED
        assertFalse(crossedStarvationThreshold(previousDaysWithoutFood = 5, newDaysWithoutFood = 6, constitutionModifier = 3))
    }

    @Test
    fun beingFedDoesNotTriggerAnOffer() {
        // FATIGUED (day 3, con 0) -> fed (day 0, NONE): severity improves, no offer
        assertFalse(crossedStarvationThreshold(previousDaysWithoutFood = 3, newDaysWithoutFood = 0, constitutionModifier = 0))
    }
}

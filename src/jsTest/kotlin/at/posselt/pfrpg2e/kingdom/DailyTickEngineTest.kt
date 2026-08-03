package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyTickEngineTest {

    // ── Travel ETA ticking ─────────────────────────────────────────────

    @Test
    fun testEtaDecrementsByOneDay() {
        val result = DailyTickEngine.tickTravelEta(eta = 3, days = 1)
        assertEquals(2, result.newEta)
        assertTrue(result.traveling)
        assertFalse(result.arrived)
    }

    @Test
    fun testEtaDecrementsByMultipleDays() {
        val result = DailyTickEngine.tickTravelEta(eta = 5, days = 3)
        assertEquals(2, result.newEta)
        assertTrue(result.traveling)
        assertFalse(result.arrived)
    }

    @Test
    fun testEtaReachingZeroArrives() {
        val result = DailyTickEngine.tickTravelEta(eta = 1, days = 1)
        assertNull(result.newEta, "Arrived companions have no remaining ETA")
        assertFalse(result.traveling)
        assertTrue(result.arrived)
    }

    @Test
    fun testEtaOvershootArrives() {
        // Advancing more days than the remaining ETA still arrives exactly once.
        val result = DailyTickEngine.tickTravelEta(eta = 2, days = 7)
        assertNull(result.newEta)
        assertFalse(result.traveling)
        assertTrue(result.arrived)
    }

    @Test
    fun testNullEtaIsNotTraveling() {
        val result = DailyTickEngine.tickTravelEta(eta = null, days = 1)
        assertNull(result.newEta)
        assertFalse(result.traveling)
        assertFalse(result.arrived, "No ETA means there is nothing to arrive at")
    }

    @Test
    fun testZeroOrNegativeDaysCoercedToOne() {
        // Defensive: a non-positive day count should still advance a single day
        // rather than freezing or moving travel backwards.
        val result = DailyTickEngine.tickTravelEta(eta = 3, days = 0)
        assertEquals(2, result.newEta)
        assertTrue(result.traveling)
    }

    @Test
    fun testDefaultDaysIsOne() {
        val result = DailyTickEngine.tickTravelEta(eta = 4)
        assertEquals(3, result.newEta)
    }

    @Test
    fun testSequentialTravelTicksReachArrival() {
        var eta: Int? = 3
        // day 1
        var result = DailyTickEngine.tickTravelEta(eta, days = 1)
        eta = result.newEta
        assertEquals(2, eta)
        assertFalse(result.arrived)
        // day 2
        result = DailyTickEngine.tickTravelEta(eta, days = 1)
        eta = result.newEta
        assertEquals(1, eta)
        assertFalse(result.arrived)
        // day 3 — arrival
        result = DailyTickEngine.tickTravelEta(eta, days = 1)
        assertNull(result.newEta)
        assertTrue(result.arrived)
        assertFalse(result.traveling)
    }

    // ── Expedition day ticking ─────────────────────────────────────────

    @Test
    fun testExpeditionDecrementsByOneDay() {
        val result = DailyTickEngine.tickExpedition(daysRemaining = 3, days = 1)
        assertEquals(2, result.newDaysRemaining)
        assertFalse(result.completed)
    }

    @Test
    fun testExpeditionReachingZeroCompletes() {
        val result = DailyTickEngine.tickExpedition(daysRemaining = 1, days = 1)
        assertEquals(0, result.newDaysRemaining)
        assertTrue(result.completed)
    }

    @Test
    fun testExpeditionOvershootCompletesOnce() {
        // Advancing more days than the remaining still completes exactly once.
        val result = DailyTickEngine.tickExpedition(daysRemaining = 3, days = 5)
        assertEquals(0, result.newDaysRemaining)
        assertTrue(result.completed)
    }

    @Test
    fun testExpeditionAlreadyCompletedNotRecompleted() {
        // Once at 0, a subsequent tick must NOT re-fire completion.
        val result = DailyTickEngine.tickExpedition(daysRemaining = 0, days = 1)
        assertEquals(0, result.newDaysRemaining)
        assertFalse(result.completed, "Already-completed expedition must not re-complete")
    }

    @Test
    fun testExpeditionZeroOrNegativeDaysCoercedToOne() {
        val result = DailyTickEngine.tickExpedition(daysRemaining = 3, days = 0)
        assertEquals(2, result.newDaysRemaining)
        assertFalse(result.completed)
    }

    @Test
    fun testExpeditionDefaultDaysIsOne() {
        val result = DailyTickEngine.tickExpedition(daysRemaining = 4)
        assertEquals(3, result.newDaysRemaining)
    }

    @Test
    fun testExpeditionSequentialTicksReachCompletion() {
        var daysRemaining = 3
        // day 1
        var result = DailyTickEngine.tickExpedition(daysRemaining, days = 1)
        daysRemaining = result.newDaysRemaining
        assertEquals(2, daysRemaining)
        assertFalse(result.completed)
        // day 2
        result = DailyTickEngine.tickExpedition(daysRemaining, days = 1)
        daysRemaining = result.newDaysRemaining
        assertEquals(1, daysRemaining)
        assertFalse(result.completed)
        // day 3 — completion
        result = DailyTickEngine.tickExpedition(daysRemaining, days = 1)
        assertEquals(0, result.newDaysRemaining)
        assertTrue(result.completed)
        // day 4 — no re-completion
        result = DailyTickEngine.tickExpedition(daysRemaining = result.newDaysRemaining, days = 1)
        assertEquals(0, result.newDaysRemaining)
        assertFalse(result.completed)
    }

    // ── Personal Quest deadline ticking ─────────────────────────────────

    @Test
    fun testPersonalQuest_noDeadline() {
        val result = DailyTickEngine.tickPersonalQuest(status = "active", turnsRemaining = null, days = 1)
        assertNull(result.newTurnsRemaining)
        assertEquals("active", result.newStatus)
        assertFalse(result.failed)
    }

    @Test
    fun testPersonalQuest_inactiveQuest() {
        val result = DailyTickEngine.tickPersonalQuest(status = "completed", turnsRemaining = 3, days = 1)
        assertEquals(3, result.newTurnsRemaining)
        assertEquals("completed", result.newStatus)
        assertFalse(result.failed)
    }

    @Test
    fun testPersonalQuest_decrementsTurns() {
        val result = DailyTickEngine.tickPersonalQuest(status = "active", turnsRemaining = 3, days = 1)
        assertEquals(2, result.newTurnsRemaining)
        assertEquals("active", result.newStatus)
        assertFalse(result.failed)
    }

    @Test
    fun testPersonalQuest_reachesZeroAndFails() {
        val result = DailyTickEngine.tickPersonalQuest(status = "active", turnsRemaining = 1, days = 1)
        assertEquals(0, result.newTurnsRemaining)
        assertEquals("failed", result.newStatus)
        assertTrue(result.failed)
    }

    @Test
    fun testPersonalQuest_overshootFails() {
        val result = DailyTickEngine.tickPersonalQuest(status = "active", turnsRemaining = 2, days = 5)
        assertEquals(0, result.newTurnsRemaining)
        assertEquals("failed", result.newStatus)
        assertTrue(result.failed)
    }
}

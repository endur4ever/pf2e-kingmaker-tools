package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnarchyActivityGatingTest {

    // ── isInAnarchy ────────────────────────────────────────────────────

    @Test
    fun isInAnarchy_exactThreshold() {
        assertTrue(isInAnarchy(currentUnrest = 20, anarchyAt = 20))
    }

    @Test
    fun isInAnarchy_aboveThreshold() {
        assertTrue(isInAnarchy(currentUnrest = 25, anarchyAt = 20))
    }

    @Test
    fun isInAnarchy_belowThreshold() {
        assertFalse(isInAnarchy(currentUnrest = 19, anarchyAt = 20))
    }

    @Test
    fun isInAnarchy_zeroUnrest() {
        assertFalse(isInAnarchy(currentUnrest = 0, anarchyAt = 20))
    }

    @Test
    fun isInAnarchy_highAnarchyAt() {
        assertFalse(isInAnarchy(currentUnrest = 20, anarchyAt = 30))
        assertTrue(isInAnarchy(currentUnrest = 30, anarchyAt = 30))
    }

    // ── activityAllowedDuringAnarchy ───────────────────────────────────

    @Test
    fun quellUnrestAllowedDuringAnarchy() {
        assertTrue(activityAllowedDuringAnarchy("quell-unrest"))
    }

    @Test
    fun collectTaxesNotAllowedDuringAnarchy() {
        assertFalse(activityAllowedDuringAnarchy("collect-taxes"))
    }

    @Test
    fun randomActivityNotAllowedDuringAnarchy() {
        assertFalse(activityAllowedDuringAnarchy("build-road"))
    }

    @Test
    fun emptyActivityIdNotAllowedDuringAnarchy() {
        assertFalse(activityAllowedDuringAnarchy(""))
    }
}

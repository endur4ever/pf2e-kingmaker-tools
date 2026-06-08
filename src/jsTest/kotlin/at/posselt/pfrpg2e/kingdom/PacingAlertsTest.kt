package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.PacingAlertSeverity
import at.posselt.pfrpg2e.kingdom.data.PacingAlertType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PacingAlertsTest {
    private fun defaultSettings(): KingdomSettings = js("({})").unsafeCast<KingdomSettings>()

    @Test
    fun levelMismatchWithinRangeProducesNoAlert() {
        assertNull(evaluateLevelMismatch(kingdomLevel = 5, targetLevel = 6, range = 2, turn = 1))
    }

    @Test
    fun levelMismatchWarningVsCritical() {
        val warn = evaluateLevelMismatch(kingdomLevel = 9, targetLevel = 5, range = 2, turn = 3)
        assertNotNull(warn)
        assertEquals(PacingAlertType.LEVEL_MISMATCH.value, warn.type)
        assertEquals(PacingAlertSeverity.WARNING.value, warn.severity)   // diff 4 == range*2

        val crit = evaluateLevelMismatch(kingdomLevel = 12, targetLevel = 5, range = 2, turn = 3)
        assertNotNull(crit)
        assertEquals(PacingAlertSeverity.CRITICAL.value, crit.severity)  // diff 7 > range*2
    }

    @Test
    fun stagnationThresholds() {
        assertNull(evaluateStagnation(turnsSinceUnrestChange = 5, maxTurnGap = 10, turn = 1))
        assertEquals(PacingAlertSeverity.WARNING.value, evaluateStagnation(10, 10, 1)!!.severity)
        assertEquals(PacingAlertSeverity.CRITICAL.value, evaluateStagnation(20, 10, 1)!!.severity)
    }

    @Test
    fun turnGapThreshold() {
        assertNull(evaluateTurnGap(turnsSinceLastEvent = 9, maxTurnGap = 10, turn = 1))
        assertNotNull(evaluateTurnGap(10, 10, 1))
    }

    @Test
    fun aggregateProducesAllTriggeredAlerts() {
        val metrics = PacingMetrics(
            kingdomLevel = 12, targetLevel = 5,
            turnsSinceUnrestChange = 12, turnsSinceLastEvent = 12, turn = 4,
        )
        // defaults: range 2, gap 10 -> level mismatch + stagnation + turn gap
        assertEquals(3, evaluatePacingAlerts(metrics, defaultSettings()).size)
    }

    @Test
    fun healthyCampaignProducesNoAlerts() {
        val metrics = PacingMetrics(
            kingdomLevel = 5, targetLevel = 5,
            turnsSinceUnrestChange = 2, turnsSinceLastEvent = 2, turn = 1,
        )
        assertEquals(0, evaluatePacingAlerts(metrics, defaultSettings()).size)
    }
}

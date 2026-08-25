package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.PacingAlertSeverity
import at.posselt.pfrpg2e.kingdom.data.PacingAlertType
import at.posselt.pfrpg2e.kingdom.loot.RealizedLootInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealizedLootPacingTest {
    private fun input(implied: Int, party: Int = 5, turn: Int = 12) =
        RealizedLootInput(impliedWealthLevel = implied, partyLevel = party, turn = turn)

    @Test
    fun withinToleranceIsSilentAndBeingPoorNeverAlerts() {
        assertNull(evaluateRealizedLootImbalance(input(implied = 7), range = 2), "diff 2 == range")
        assertNull(evaluateRealizedLootImbalance(input(implied = 5), range = 2))
        assertNull(evaluateRealizedLootImbalance(input(implied = 1), range = 2), "under-rewarded is not this alert")
    }

    @Test
    fun pastToleranceWarnsAndPastDoubleIsCritical() {
        val warn = evaluateRealizedLootImbalance(input(implied = 8), range = 2)!!
        assertEquals(PacingAlertSeverity.WARNING.value, warn.severity)
        assertEquals(PacingAlertType.REALIZED_LOOT_IMBALANCE.value, warn.type)
        assertEquals("treasure-ledger", warn.relatedEntityId)
        assertEquals(PacingAlertSeverity.WARNING.value, evaluateRealizedLootImbalance(input(implied = 9), 2)!!.severity)
        assertEquals(
            PacingAlertSeverity.CRITICAL.value,
            evaluateRealizedLootImbalance(input(implied = 10), 2)!!.severity,
            "diff 5 > range*2",
        )
    }

    @Test
    fun itIsADISTINCTAlertTypeFromTheSettlementProxy() {
        val realized = evaluateRealizedLootImbalance(input(implied = 10), 2)!!
        val settlement = evaluateLootImbalance(itemAccessLevel = 10, partyLevel = 5, range = 2, turn = 12)!!
        assertTrue(realized.type != settlement.type, "two tracks, two types -- they must coexist")
        assertTrue(realized.id != settlement.id, "and distinct ids, or one would overwrite the other")
    }

    @Test
    fun theTrackFiresOncePerCrossing() {
        val first = trackRealizedLootImbalance(input(implied = 8), range = 2, previousSeverity = null)
        assertEquals(PacingAlertSeverity.WARNING.value, first.severity)
        assertTrue(first.alert != null, "the crossing fires")

        val again = trackRealizedLootImbalance(input(implied = 8), range = 2, previousSeverity = first.severity)
        assertNull(again.alert, "unchanged severity stays quiet")

        val escalated = trackRealizedLootImbalance(input(implied = 12), range = 2, previousSeverity = first.severity)
        assertEquals(PacingAlertSeverity.CRITICAL.value, escalated.severity)
        assertTrue(escalated.alert != null, "an escalation is a new crossing")

        val recovered = trackRealizedLootImbalance(input(implied = 5), range = 2, previousSeverity = escalated.severity)
        assertNull(recovered.severity)
        assertNull(recovered.alert)
    }
}

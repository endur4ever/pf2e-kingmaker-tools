package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the write half of the caravan delivery history: mapping a resolved tick event onto a
 * history entry, and the Raw<->model round trip that persists it.
 */
class ShipmentHistoryDataTest {
    private fun event(kind: CaravanEventKind, rd: Int = 0) = CaravanEvent(
        kind = kind,
        summary = "20 Lumber to Pitax",
        partnerName = "Pitax",
        bonusResourceDice = rd,
    )

    @Test
    fun aDeliveryRecordsItsResourceDice() {
        val e = caravanEventToHistory(event(CaravanEventKind.DELIVERED, rd = 3), turn = 12)!!
        assertEquals(ShipmentOutcome.DELIVERED, e.outcome)
        assertEquals("Pitax", e.partner)
        assertEquals(12, e.turn)
        assertEquals(3, e.rdGained)
    }

    @Test
    fun aDeliveryThatEarnedNoDiceRecordsNullNotZero() {
        // null means "this was not a sale"; 0 would read as "a sale that earned nothing".
        assertNull(caravanEventToHistory(event(CaravanEventKind.DELIVERED), turn = 1)!!.rdGained)
    }

    @Test
    fun aRaidIsRecordedAsRaided() {
        assertEquals(
            ShipmentOutcome.RAIDED,
            caravanEventToHistory(event(CaravanEventKind.RAIDED), turn = 4)!!.outcome,
        )
    }

    @Test
    fun aLostCaravanIsRaidedNotRecalled() {
        // RECALLED means the GM pulled it back. Recording a disaster as a GM decision would
        // misreport what happened to the cargo.
        assertEquals(
            ShipmentOutcome.RAIDED,
            caravanEventToHistory(event(CaravanEventKind.LOST), turn = 4)!!.outcome,
        )
    }

    @Test
    fun theRawRoundTripPreservesEveryField() {
        val entry = ShipmentHistoryEntry(
            turn = 9, partner = "Mivon", cargo = "10 Ore", outcome = ShipmentOutcome.RECALLED, rdGained = null,
        )
        val back = entry.toRaw().toModel()
        assertEquals(entry, back)
    }

    @Test
    fun anUnknownStoredOutcomeIsDroppedRatherThanThrowing() {
        // History written by a newer build must not break the whole caravan board.
        val raw = RawShipmentHistoryEntry(
            turn = 1, partner = "x", cargo = "y", outcome = "teleported", rdGained = null,
        )
        assertNull(raw.toModel())
    }
}

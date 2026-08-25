package at.posselt.pfrpg2e.kingdom.pings

import at.posselt.pfrpg2e.kingdom.RawShipmentHistoryEntry
import at.posselt.pfrpg2e.kingdom.data.RawQuest
import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Covers the feed adapter's source mappings and its refusal to invent data it does not have. */
class PingsAdapterTest {
    private fun record(turn: Int, timestamp: String?, playerNotes: String?, notes: String? = null): RawTurnRecord {
        val r = unsafeJso<dynamic>()
        r.turn = turn
        r.timestamp = timestamp
        r.playerNotes = playerNotes
        r.notes = notes
        return r.unsafeCast<RawTurnRecord>()
    }

    @Test
    fun gmOnlyNotesNeverReachTheFeed() {
        // The leak case: a record with GM prose but a blank player gazette contributes NOTHING --
        // the adapter reads playerNotes only, never notes.
        val out = feedFromTurnHistory(
            arrayOf(record(5, "2026-08-01T10:00:00Z", playerNotes = "", notes = "SECRET clock advanced")),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun aGazetteTurnBecomesOneItemWithAStableId() {
        val out = feedFromTurnHistory(arrayOf(record(5, "2026-08-01T10:00:00Z", "The realm prospered.")))
        assertEquals(1, out.size)
        assertEquals("turn-5", out[0].id)
        assertEquals(PINGS_KEY_TURN_GAZETTE, out[0].labelKey)
        assertTrue(out[0].playerSafe)
    }

    @Test
    fun anUnparseableTimestampDropsTheRowRatherThanInventingATime() {
        assertTrue(feedFromTurnHistory(arrayOf(record(5, "not a date", "notes"))).isEmpty())
        assertTrue(feedFromTurnHistory(arrayOf(record(5, null, "notes"))).isEmpty())
    }

    private fun shipment(turn: Int, outcome: String): RawShipmentHistoryEntry {
        val s = unsafeJso<dynamic>()
        s.turn = turn
        s.partner = "Pitax"
        s.cargo = "20 Lumber"
        s.outcome = outcome
        return s.unsafeCast<RawShipmentHistoryEntry>()
    }

    @Test
    fun shipmentsBorrowTheirTurnRecordsClockAndSkipOrphans() {
        val stamps = mapOf(5 to 1000.0)
        val out = feedFromShipments(arrayOf(shipment(5, "raided"), shipment(9, "raided")), stamps)
        assertEquals(1, out.size, "turn 9 has no record: skipped, never given a fake time")
        assertEquals(1000.0, out[0].occurredAtMillis)
        assertEquals(PINGS_KEY_CARAVAN_RAIDED, out[0].labelKey)
    }

    @Test
    fun anUnknownShipmentOutcomeIsSkipped() {
        assertTrue(feedFromShipments(arrayOf(shipment(5, "teleported")), mapOf(5 to 1000.0)).isEmpty())
    }

    @Test
    fun twoShipmentsOnOneTurnGetDistinctIds() {
        val out = feedFromShipments(arrayOf(shipment(5, "raided"), shipment(5, "delivered")), mapOf(5 to 1000.0))
        assertEquals(2, out.map { it.id }.distinct().size, "dismissing one must not dismiss the other")
    }

    @Test
    fun onlyCompletedQuestsWithATimestampFeed() {
        fun quest(id: String, status: String, updatedAt: Double?): RawQuest {
            val q = unsafeJso<dynamic>()
            q.id = id
            q.title = "Moon Radish Soup"
            q.status = status
            q.updatedAt = updatedAt
            return q.unsafeCast<RawQuest>()
        }
        val out = feedFromQuests(arrayOf(quest("a", "completed", 2000.0), quest("b", "active", 2000.0), quest("c", "completed", null)))
        assertEquals(listOf("quest-a"), out.map { it.id })
    }
}

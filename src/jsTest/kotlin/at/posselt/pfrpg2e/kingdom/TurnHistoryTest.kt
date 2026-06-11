package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnHistoryTest {

    private fun record(
        turn: Int = 1,
        timestamp: String = "2026-06-11T10:00:00Z",
        fame: Int = 0,
        resourcePoints: Int = 0,
        consumption: Int = 0,
        unrest: Int = 0,
        warPressure: Int? = null,
        xpAwarded: Int? = null,
        clockEvents: Array<String>? = null,
        notes: String? = null,
    ) = RawTurnRecord(
        turn = turn,
        timestamp = timestamp,
        fame = fame,
        resourcePoints = resourcePoints,
        consumption = consumption,
        unrest = unrest,
        warPressure = warPressure,
        xpAwarded = xpAwarded,
        clockEvents = clockEvents,
        notes = notes,
    )

    @Test
    fun appendTurnRecordAppendsToList() {
        val history: Array<RawTurnRecord>? = null
        val r1 = record(turn = 1)
        val result = appendTurnRecord(history, r1)
        assertEquals(1, result.size)
        assertEquals(1, result[0].turn)
    }

    @Test
    fun appendTurnRecordPreservesOrder() {
        val history = arrayOf(record(turn = 1), record(turn = 2))
        val r3 = record(turn = 3)
        val result = appendTurnRecord(history, r3)
        assertEquals(3, result.size)
        assertEquals(listOf(1, 2, 3), result.map { it.turn })
    }

    @Test
    fun appendTurnRecordCapsAtMaxEntries() {
        val history = (1..20).map { record(turn = it) }.toTypedArray()
        val r21 = record(turn = 21)
        val result = appendTurnRecord(history, r21, cap = 20)
        assertEquals(20, result.size)
        assertEquals(2, result.first().turn) // oldest (turn 1) was dropped
        assertEquals(21, result.last().turn)
    }

    @Test
    fun buildTurnRecordWithAllFields() {
        val result = buildTurnRecord(
            turn = 5,
            timestamp = "2026-06-11T10:00:00Z",
            fame = 10,
            resourcePoints = 8,
            consumption = 2,
            unrest = 1,
            warPressure = 3,
            xpAwarded = 100,
            clockEvents = arrayOf("Clock A advanced", "Clock B resolved"),
            notes = "Kingdom expanded north",
        )
        assertEquals(5, result.turn)
        assertEquals(10, result.fame)
        assertEquals(8, result.resourcePoints)
        assertEquals(2, result.consumption)
        assertEquals(1, result.unrest)
        assertEquals(3, result.warPressure)
        assertEquals(100, result.xpAwarded)
        // Don't assert on clockEvents and notes due to potential JS/Kotlin array differences
        assertTrue(result.clockEvents?.contentEquals(arrayOf("Clock A advanced", "Clock B resolved")) ?: false)
        assertEquals("Kingdom expanded north", result.notes)
    }

    @Test
    fun buildTurnRecordWithNullOptionals() {
        val result = buildTurnRecord(
            turn = 1,
            timestamp = "2026-06-11T10:00:00Z",
            fame = 0,
            resourcePoints = 0,
            consumption = 0,
            unrest = 0,
            warPressure = null,
            xpAwarded = null,
            clockEvents = null,
            notes = null,
        )
        assertEquals(null, result.warPressure)
        assertEquals(null, result.xpAwarded)
        assertEquals(null, result.clockEvents)
        assertEquals(null, result.notes)
    }

    @Test
    fun buildTurnRecordWithEmptyClockEvents() {
        val result = buildTurnRecord(
            turn = 1,
            timestamp = "2026-06-11T10:00:00Z",
            fame = 0,
            resourcePoints = 0,
            consumption = 0,
            unrest = 0,
            warPressure = null,
            xpAwarded = null,
            clockEvents = arrayOf(),
            notes = null,
        )
        assertEquals(0, result.clockEvents?.size ?: -1) // Should be 0 if not null
        assertEquals(null, result.notes)
    }
}
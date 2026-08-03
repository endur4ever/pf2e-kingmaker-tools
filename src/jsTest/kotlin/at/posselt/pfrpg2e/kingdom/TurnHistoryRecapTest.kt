package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnHistoryRecapTest {
    private fun record(
        turn: Int,
        fame: Int,
        rp: Int,
        unrest: Int,
        warPressure: Int? = null,
        xpAwarded: Int? = null,
        notes: String? = null,
    ): RawTurnRecord = buildTurnRecord(
        turn = turn,
        timestamp = "2026-07-08T00:00:00Z",
        fame = fame,
        resourcePoints = rp,
        consumption = 0,
        unrest = unrest,
        warPressure = warPressure,
        xpAwarded = xpAwarded,
        notes = notes,
    )

    @Test
    fun nullWhenNoHistory() {
        assertNull(computeLastTurnRecap(null))
        assertNull(computeLastTurnRecap(emptyArray()))
    }

    @Test
    fun deltasComputedFromPreviousRecord() {
        val history = arrayOf(
            record(turn = 1, fame = 1, rp = 10, unrest = 2, warPressure = 5),
            record(turn = 2, fame = 3, rp = 6, unrest = 1, warPressure = 9, xpAwarded = 40, notes = "Activities: Claim | Expansion: 1 hex"),
        )
        val r = computeLastTurnRecap(history)!!
        assertEquals(2, r.turn)
        assertEquals(2, r.fameDelta); assertEquals(3, r.fameNow)          // 3 - 1
        assertEquals(-4, r.rpDelta); assertEquals(6, r.rpNow)             // 6 - 10
        assertEquals(-1, r.unrestDelta); assertEquals(1, r.unrestNow)     // 1 - 2
        assertTrue(r.hasWarPressure)
        assertEquals(4, r.warPressureDelta); assertEquals(9, r.warPressureNow) // 9 - 5
        assertEquals(40, r.xpAwarded)
        assertEquals("Activities: Claim | Expansion: 1 hex", r.notes)
    }

    @Test
    fun firstRecordHasZeroDeltas() {
        val r = computeLastTurnRecap(arrayOf(record(turn = 1, fame = 3, rp = 10, unrest = 2, warPressure = 5)))!!
        assertEquals(1, r.turn)
        assertEquals(0, r.fameDelta); assertEquals(3, r.fameNow)
        assertEquals(0, r.rpDelta); assertEquals(0, r.unrestDelta)
        assertEquals(0, r.warPressureDelta); assertEquals(5, r.warPressureNow)
    }

    @Test
    fun noWarPressureWhenNull() {
        val r = computeLastTurnRecap(arrayOf(record(turn = 1, fame = 1, rp = 1, unrest = 1, warPressure = null)))!!
        assertFalse(r.hasWarPressure)
        assertEquals(0, r.warPressureDelta)
        assertEquals(0, r.warPressureNow)
    }

    @Test
    fun recapsTheMostRecentTurnOnly() {
        val history = arrayOf(
            record(turn = 5, fame = 0, rp = 0, unrest = 0),
            record(turn = 6, fame = 0, rp = 0, unrest = 0),
            record(turn = 7, fame = 2, rp = 2, unrest = 2),
        )
        assertEquals(7, computeLastTurnRecap(history)!!.turn)
    }
}

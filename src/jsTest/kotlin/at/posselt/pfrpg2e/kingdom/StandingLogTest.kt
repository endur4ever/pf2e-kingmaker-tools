package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.MAX_STANDING_LOG_ENTRIES
import at.posselt.pfrpg2e.kingdom.data.RawFactionStandingEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class StandingLogTest {
    private fun entry(turn: Int) = RawFactionStandingEntry(
        turn = turn,
        delta = 1,
        reason = "kingdom.factionStanding.drift",
    )

    @Test
    fun appendingToAnAbsentLogStartsOne() {
        val log = appendStandingEntry(null, entry(1))
        assertEquals(1, log.size)
        assertEquals(1, log[0].turn)
    }

    @Test
    fun appendingKeepsOrderOldestFirst() {
        var log = appendStandingEntry(null, entry(1))
        log = appendStandingEntry(log, entry(2))
        assertEquals(listOf(1, 2), log.map { it.turn })
    }

    @Test
    fun theLogStopsGrowingAtTheCap() {
        // Every drift tick appends, so without this a long campaign carries thousands of entries in
        // every save of the kingdom flag.
        var log: Array<RawFactionStandingEntry>? = null
        repeat(MAX_STANDING_LOG_ENTRIES + 40) { i -> log = appendStandingEntry(log, entry(i + 1)) }
        val final = log!!
        assertEquals(MAX_STANDING_LOG_ENTRIES, final.size)
        // The oldest 40 are gone; the newest is still last.
        assertEquals(41, final.first().turn)
        assertEquals(MAX_STANDING_LOG_ENTRIES + 40, final.last().turn)
    }
}

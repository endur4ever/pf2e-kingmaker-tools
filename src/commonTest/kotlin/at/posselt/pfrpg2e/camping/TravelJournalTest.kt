package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals

class TravelJournalTest {
    private fun hex(date: String, key: String) =
        TravelJournalEntry(worldDate = date, kind = TravelJournalKind.ENTERED_HEX, hexKey = key)

    @Test
    fun appendKeepsChronologicalOrder() {
        val j = appendTravelJournalEntry(listOf(hex("d1", "0101")), hex("d2", "0102"))
        assertEquals(listOf("0101", "0102"), j.map { it.hexKey })
    }

    @Test
    fun appendOverCapPrunesOldest() {
        val full = (1..200).map { hex("d$it", it.toString()) }
        val j = appendTravelJournalEntry(full, hex("d201", "201"), cap = 200)
        assertEquals(200, j.size)
        assertEquals("2", j.first().hexKey)     // "1" pruned
        assertEquals("201", j.last().hexKey)
    }

    @Test
    fun nonPositiveCapEmptiesJournal() {
        assertEquals(emptyList(), appendTravelJournalEntry(listOf(hex("d1", "0101")), hex("d2", "0102"), cap = 0))
    }

    @Test
    fun kindCountsCoverEveryKind() {
        val journal = listOf(
            hex("d1", "0101"),
            hex("d1", "0102"),
            TravelJournalEntry("d1", TravelJournalKind.ENCOUNTER, hexKey = "0102"),
            TravelJournalEntry("d2", TravelJournalKind.REST),
        )
        val counts = travelJournalKindCounts(journal)
        assertEquals(2, counts[TravelJournalKind.ENTERED_HEX])
        assertEquals(1, counts[TravelJournalKind.ENCOUNTER])
        assertEquals(1, counts[TravelJournalKind.REST])
        assertEquals(0, counts[TravelJournalKind.MEAL])
        assertEquals(0, counts[TravelJournalKind.ACTIVITY])
    }

    @Test
    fun hexesVisitedAreDistinctInFirstSeenOrder() {
        val journal = listOf(
            hex("d1", "0101"),
            hex("d1", "0102"),
            hex("d2", "0101"),  // revisit
            TravelJournalEntry("d2", TravelJournalKind.ENCOUNTER, hexKey = "0103"),  // not an ENTERED_HEX
        )
        assertEquals(listOf("0101", "0102"), hexesVisited(journal))
    }
}

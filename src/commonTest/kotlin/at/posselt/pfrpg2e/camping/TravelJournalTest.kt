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

    @Test
    fun stayingPutDoesNotRecordAnotherHexEntry() {
        val journal = listOf(hex("d1", "0101"))
        assertEquals(false, shouldRecordHexEntry(journal, "0101"))
        assertEquals(true, shouldRecordHexEntry(journal, "0102"))
    }

    @Test
    fun anEmptyJournalAlwaysRecordsTheFirstHex() {
        assertEquals(true, shouldRecordHexEntry(emptyList(), "0101"))
    }

    @Test
    fun anEncounterInBetweenDoesNotMakeAStationaryPartyLookLikeItMoved() {
        // Compares against the last ENTERED_HEX, not the last entry of any kind -- otherwise
        // rolling an encounter while camped would let the very next hook re-log the same hex.
        val journal = listOf(
            hex("d1", "0101"),
            TravelJournalEntry("d1", TravelJournalKind.ENCOUNTER, hexKey = "0101"),
        )
        assertEquals(false, shouldRecordHexEntry(journal, "0101"))
    }

    @Test
    fun aRestBetweenDoesNotMakeTheSameHexLookNew() {
        // The distinguishing case for "last ENTERED_HEX" vs "last entry of any kind": a REST
        // carries no hexKey at all, so comparing against the last entry would see null, decide the
        // party had moved, and re-log the hex they are still standing in every single morning.
        val journal = listOf(
            hex("d1", "0101"),
            TravelJournalEntry("d1", TravelJournalKind.REST),
        )
        assertEquals(false, shouldRecordHexEntry(journal, "0101"))
    }

    @Test
    fun anEncounterElsewhereDoesNotResetTheHexComparison() {
        // Same trap with a non-null but DIFFERENT hexKey on the intervening entry.
        val journal = listOf(
            hex("d1", "0101"),
            TravelJournalEntry("d1", TravelJournalKind.ENCOUNTER, hexKey = "0102"),
        )
        assertEquals(false, shouldRecordHexEntry(journal, "0101"))
    }

    @Test
    fun backtrackingToAPreviousHexIsStillRecorded() {
        // Only CONSECUTIVE repeats are suppressed; a party that returns somewhere really did travel.
        val journal = listOf(hex("d1", "0101"), hex("d1", "0102"))
        assertEquals(true, shouldRecordHexEntry(journal, "0101"))
    }

    private val labels = mapOf(
        TravelJournalKind.ENTERED_HEX to "Entered Hex",
        TravelJournalKind.ENCOUNTER to "Encounter",
        TravelJournalKind.REST to "Rest",
        TravelJournalKind.ACTIVITY to "Activity",
        TravelJournalKind.MEAL to "Meal",
    )

    @Test
    fun theDaySummaryOmitsKindsThatDidNotHappen() {
        // A note listing every kind at zero is noise; only what actually happened belongs in it.
        val summary = travelJournalDaySummary(listOf(hex("d1", "0101")), labels)
        assertEquals("Entered Hex: 1, Hexes: 1", summary)
    }

    @Test
    fun theDaySummaryCountsDistinctHexesNotHexEntries() {
        val journal = listOf(hex("d1", "0101"), hex("d1", "0102"), hex("d1", "0101"))
        assertEquals("Entered Hex: 3, Hexes: 2", travelJournalDaySummary(journal, labels))
    }

    @Test
    fun anEmptyJournalProducesNoSummaryLineAtAll() {
        // Lets the caller skip writing a calendar note rather than writing an empty one.
        assertEquals("", travelJournalDaySummary(emptyList(), labels))
    }

    @Test
    fun aDayWithNoHexesStillSummarisesWhatHappened() {
        val journal = listOf(TravelJournalEntry("d1", TravelJournalKind.REST))
        assertEquals("Rest: 1", travelJournalDaySummary(journal, labels))
    }
}

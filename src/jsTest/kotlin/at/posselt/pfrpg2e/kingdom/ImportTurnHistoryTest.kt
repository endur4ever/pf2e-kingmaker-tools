package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import at.posselt.pfrpg2e.kingdom.dialogs.applyTurnHistoryImport
import at.posselt.pfrpg2e.kingdom.dialogs.toRaw
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the write half of the workbook backfill: turning parsed rows into records and merging them
 * into existing history. The parse and merge DECISIONS are unit-tested in commonTest; this is the
 * part that actually replaces a GM's data, so it gets its own cover.
 */
class ImportTurnHistoryTest {
    private fun existing(turn: Int, fame: Int = 0, notes: String? = null): RawTurnRecord =
        unsafeJso<dynamic> {
            this.turn = turn
            this.timestamp = "2026-01-01"
            this.fame = fame
            this.resourcePoints = 0
            this.consumption = 0
            this.unrest = 0
            this.notes = notes
        }.unsafeCast<RawTurnRecord>()

    @Test
    fun anImportedRowReplacesTheSameTurn() {
        val result = applyTurnHistoryImport(
            existing = listOf(existing(1, fame = 1), existing(2, fame = 2)),
            imported = listOf(ParsedTurnRow(turn = 2, fame = 99)),
            keptTurns = setOf(1, 2),
        )
        assertEquals(2, result.size)
        assertEquals(1, result[0].fame, "the untouched turn must keep its original record")
        assertEquals(99, result[1].fame, "the imported row must win on a collision")
    }

    @Test
    fun existingTurnsNotInTheImportAreKept() {
        val result = applyTurnHistoryImport(
            existing = listOf(existing(1), existing(5)),
            imported = listOf(ParsedTurnRow(turn = 3)),
            keptTurns = setOf(1, 3, 5),
        )
        assertEquals(listOf(1, 3, 5), result.map { it.turn })
    }

    @Test
    fun theResultIsTurnSortedRegardlessOfInputOrder() {
        // Analytics charts the array in order; an unsorted history would plot the series wrong.
        val result = applyTurnHistoryImport(
            existing = listOf(existing(9), existing(2)),
            imported = listOf(ParsedTurnRow(turn = 5), ParsedTurnRow(turn = 1)),
            keptTurns = setOf(1, 2, 5, 9),
        )
        assertEquals(listOf(1, 2, 5, 9), result.map { it.turn })
    }

    @Test
    fun turnsOutsideTheAgreedPlanAreDropped() {
        // keptTurns is what the confirm dialog told the GM would survive the cap. Writing anything
        // else would mean the dialog lied about what it was going to do.
        val result = applyTurnHistoryImport(
            existing = listOf(existing(1), existing(2)),
            imported = listOf(ParsedTurnRow(turn = 3)),
            keptTurns = setOf(2, 3),
        )
        assertEquals(listOf(2, 3), result.map { it.turn })
    }

    @Test
    fun conversionCarriesEveryParsedFieldAcross() {
        val raw = ParsedTurnRow(
            turn = 7, fame = 3, resourcePoints = 40, consumption = 5, unrest = 2,
            xpAwarded = 120, notes = "Founded Tuskwater",
        ).toRaw()
        assertEquals(7, raw.turn)
        assertEquals(3, raw.fame)
        assertEquals(40, raw.resourcePoints)
        assertEquals(5, raw.consumption)
        assertEquals(2, raw.unrest)
        assertEquals(120, raw.xpAwarded)
        assertEquals("Founded Tuskwater", raw.notes)
    }

    @Test
    fun aSparseRowConvertsWithoutInventingValues() {
        // The workbook may not have every column; xpAwarded stays null rather than becoming 0, so
        // Analytics can tell "no XP recorded" apart from "zero XP awarded".
        val raw = ParsedTurnRow(turn = 4).toRaw()
        assertEquals(4, raw.turn)
        assertNull(raw.xpAwarded)
        assertNull(raw.notes)
        assertEquals("", raw.timestamp, "imported rows carry no real timestamp")
    }

    @Test
    fun importingIntoEmptyHistoryWorks() {
        val result = applyTurnHistoryImport(
            existing = emptyList(),
            imported = listOf(ParsedTurnRow(turn = 2), ParsedTurnRow(turn = 1)),
            keptTurns = setOf(1, 2),
        )
        assertEquals(listOf(1, 2), result.map { it.turn })
    }
}

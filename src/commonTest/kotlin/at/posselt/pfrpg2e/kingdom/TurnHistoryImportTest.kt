package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TurnHistoryImportTest {
    @Test
    fun parsesDefaultOrderedTsvRows() {
        val text = "1\t2\t30\t5\t1\t100\tfirst turn\n2\t3\t25\t6\t2\t120\t"
        val result = parseTurnRows(text)
        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.records.size)
        assertEquals(ParsedTurnRow(turn = 1, fame = 2, resourcePoints = 30, consumption = 5, unrest = 1, xpAwarded = 100, notes = "first turn"), result.records[0])
        assertEquals(2, result.records[1].turn)
        assertEquals(null, result.records[1].notes)  // blank notes -> null
    }

    @Test
    fun autoDetectsAndSkipsHeaderRow() {
        val text = "Turn,Fame,RP,Unrest\n1,2,30,4\n2,3,25,5"
        val result = parseTurnRows(text)
        assertEquals(2, result.records.size)
        // Header remapped columns (RP -> resourcePoints, and no consumption column present -> default 0).
        assertEquals(30, result.records[0].resourcePoints)
        assertEquals(4, result.records[0].unrest)
        assertEquals(0, result.records[0].consumption)
    }

    @Test
    fun skipsBlankLinesAndTrimsCarriageReturns() {
        val text = "1\t2\t3\t4\t5\r\n\n\n2\t2\t3\t4\t5\r"
        val result = parseTurnRows(text)
        assertEquals(2, result.records.size)
        assertEquals(listOf(1, 2), result.records.map { it.turn })
    }

    @Test
    fun toleratesThousandsSeparatorsInNumbers() {
        val text = "1\t2\t1,000\t5\t1"
        val result = parseTurnRows(text)
        assertEquals(1000, result.records.single().resourcePoints)
    }

    @Test
    fun reportsNonNumericTurnWithRowNumberAndDropsRow() {
        val text = "1\t2\t30\nbad\t3\t25\n3\t4\t20"
        val result = parseTurnRows(text)
        assertEquals(listOf(1, 3), result.records.map { it.turn })
        assertEquals(1, result.errors.size)
        assertEquals(2, result.errors.single().rowNumber)  // physical line 2 is the bad row
        assertTrue(result.errors.single().message.contains("bad"))
    }

    @Test
    fun raggedRowsDefaultMissingColumns() {
        // Only turn + fame present; the rest of the mapped columns are absent -> defaults.
        val text = "5\t7"
        val row = parseTurnRows(text).records.single()
        assertEquals(5, row.turn)
        assertEquals(7, row.fame)
        assertEquals(0, row.resourcePoints)
        assertEquals(null, row.xpAwarded)
    }

    @Test
    fun mergePlanReportsCollisionsAndCapTrim() {
        val plan = planTurnHistoryMerge(
            existingTurns = listOf(1, 2, 3),
            importedTurns = listOf(2, 3, 4, 5),
            cap = 4,
        )
        assertEquals(listOf(2, 3), plan.collisions)          // 2,3 exist in both
        assertEquals(listOf(2, 3, 4, 5), plan.keptTurns)     // union {1..5} capped to newest 4
        assertEquals(1, plan.trimmed)                        // turn 1 trimmed
    }

    @Test
    fun mergePlanKeepsEverythingUnderCap() {
        val plan = planTurnHistoryMerge(existingTurns = listOf(1), importedTurns = listOf(2, 3), cap = 100)
        assertEquals(emptyList(), plan.collisions)
        assertEquals(listOf(1, 2, 3), plan.keptTurns)
        assertEquals(0, plan.trimmed)
    }
}

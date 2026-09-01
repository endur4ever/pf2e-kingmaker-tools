package at.posselt.pfrpg2e.kingdom

import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettlementLifeHistoryTest {
    private fun record(turn: Int, templateId: String = "market-day", applied: Boolean? = false, names: Array<String> = arrayOf("Svetlana")) =
        unsafeJso<dynamic> {
            recordId = "life-a-$turn-$templateId"
            this.templateId = templateId
            this.turn = turn
            castNpcIds = arrayOf("n1")
            castNames = names
            hookKind = "rp-delta"
            hookMagnitude = 1
            hookApplied = applied
        }.unsafeCast<RawSettlementLifeEventRecord>()

    @Test
    fun newestFirstAndWindowedToTheViewSize() {
        val rows = lifeHistoryRows("Tusk Hold", (1..30).map { record(it) }.toTypedArray())
        assertEquals(LIFE_HISTORY_VIEW_ROWS, rows.size)
        assertEquals(30, rows.first().turn)
        assertTrue(rows.map { it.turn }.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun theLineIsRebuiltFromTheCastInSlotOrder() {
        val row = lifeHistoryRows("Tusk Hold", arrayOf(record(4))).single()
        // market-day's single slot is "trader"; the stored name must land in it, not in a raw key
        assertTrue("Svetlana" in row.line, row.line)
        assertTrue("Tusk Hold" in row.line, row.line)
        assertTrue("{" !in row.line, "unfilled placeholder in ${row.line}")
    }

    @Test
    fun aRecordWhoseTemplateLeftTheCatalogStillRenders() {
        // a chronicle that already happened must not vanish because a JSON file was deleted
        val row = lifeHistoryRows("Tusk Hold", arrayOf(record(2, templateId = "retired-event"))).single()
        assertEquals("retired-event", row.name)
        assertTrue("Svetlana" in row.line)
    }

    @Test
    fun anEmptyOrAbsentHistoryIsAnEmptyTableNotACrash() {
        assertEquals(0, lifeHistoryRows("Tusk Hold", null).size)
        assertEquals(0, lifeHistoryRows("Tusk Hold", emptyArray()).size)
    }
}

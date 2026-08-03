package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpeditionChronicleViewTest {
    private fun e(turn: Int, name: String, summary: String = "did a thing") =
        ChronicleEntry(turn = turn, companionName = name, summary = summary)

    @Test
    fun groupsAreNewestTurnFirst() {
        val groups = groupChronicleByTurn(listOf(e(1, "Amiri"), e(3, "Ekun"), e(2, "Linzi")))
        assertEquals(listOf(3, 2, 1), groups.map { it.turn })
    }

    @Test
    fun entriesInTheSameTurnAreGroupedTogetherPreservingOrder() {
        val groups = groupChronicleByTurn(listOf(e(2, "Amiri"), e(2, "Ekun"), e(1, "Linzi")))
        val turn2 = groups.first { it.turn == 2 }
        assertEquals(listOf("Amiri", "Ekun"), turn2.entries.map { it.companionName })
    }

    @Test
    fun emptyChronicleYieldsNoGroups() {
        val groups = groupChronicleByTurn(emptyList())
        assertEquals(emptyList(), groups)
        assertEquals(0, chronicleEntryCount(groups))
    }

    @Test
    fun entryCountSumsAcrossTurns() {
        val groups = groupChronicleByTurn(listOf(e(1, "A"), e(1, "B"), e(2, "C")))
        assertEquals(3, chronicleEntryCount(groups))
        assertEquals(2, groups.size)
    }
}

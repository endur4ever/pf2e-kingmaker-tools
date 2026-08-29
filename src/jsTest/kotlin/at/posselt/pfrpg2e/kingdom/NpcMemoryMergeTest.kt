package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.structures.RawNpcEntry
import at.posselt.pfrpg2e.kingdom.structures.RawNpcMemoryEntry
import at.posselt.pfrpg2e.kingdom.structures.RawPopulationRoster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NpcMemoryMergeTest {
    private fun npc(id: String, tracked: Boolean? = null, score: Int? = null, entries: Int = 0) =
        RawNpcEntry(id = id, name = id, occupation = "Merchant").also {
            it.memoryTracked = tracked
            it.attitudeScore = score
            it.memoryLog = (1..entries).map { n ->
                RawNpcMemoryEntry(ruleId = "unrest-spike", turn = n, delta = -2, subject = null)
            }.toTypedArray()
        }

    @Test
    fun topUpKeepsExistingLedgersAndLeavesNewcomersEmpty() {
        val existing = RawPopulationRoster(npcs = arrayOf(npc("a", tracked = true, score = -7, entries = 2)))
        // the model round trip returns the same resident stripped, plus a new one, reordered
        val rebuilt = RawPopulationRoster(npcs = arrayOf(npc("new"), npc("a")))
        val merged = mergeNpcMemoryFields(rebuilt, existing)
        val byId = merged.npcs!!.associateBy { it.id }
        assertEquals(true, byId.getValue("a").memoryTracked)
        assertEquals(-7, byId.getValue("a").attitudeScore)
        assertEquals(2, byId.getValue("a").memoryLog?.size)
        assertNull(byId.getValue("new").memoryTracked)
        assertEquals(0, byId.getValue("new").memoryLog?.size)
    }

    @Test
    fun anEmptyPredecessorRosterChangesNothing() {
        val rebuilt = RawPopulationRoster(npcs = arrayOf(npc("a")))
        assertEquals(1, mergeNpcMemoryFields(rebuilt, null).npcs?.size)
        assertEquals(1, mergeNpcMemoryFields(rebuilt, RawPopulationRoster()).npcs?.size)
    }
}

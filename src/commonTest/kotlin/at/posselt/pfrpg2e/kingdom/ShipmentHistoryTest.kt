package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals

class ShipmentHistoryTest {
    private fun entry(turn: Int, outcome: ShipmentOutcome = ShipmentOutcome.DELIVERED) =
        ShipmentHistoryEntry(turn = turn, partner = "Pitax", cargo = "3 lumber", outcome = outcome)

    @Test
    fun appendUnderCapKeepsEverythingNewestLast() {
        val h = appendShipmentHistory(listOf(entry(1)), entry(2))
        assertEquals(listOf(1, 2), h.map { it.turn })
    }

    @Test
    fun appendOverCapDropsTheOldest() {
        val full = (1..50).map { entry(it) }
        val h = appendShipmentHistory(full, entry(51), cap = 50)
        assertEquals(50, h.size)
        assertEquals(2, h.first().turn)   // turn 1 pruned
        assertEquals(51, h.last().turn)   // newest retained
    }

    @Test
    fun nonPositiveCapEmptiesHistory() {
        assertEquals(emptyList(), appendShipmentHistory(listOf(entry(1)), entry(2), cap = 0))
    }

    @Test
    fun deliveredEntriesCanCarryResourceDice() {
        val e = ShipmentHistoryEntry(3, "Mivon", "2 ore", ShipmentOutcome.DELIVERED, rdGained = 4)
        val h = appendShipmentHistory(emptyList(), e)
        assertEquals(4, h.single().rdGained)
    }

    @Test
    fun outcomeCountsCoverEveryOutcome() {
        val history = listOf(
            entry(1, ShipmentOutcome.DELIVERED),
            entry(2, ShipmentOutcome.DELIVERED),
            entry(3, ShipmentOutcome.RAIDED),
        )
        val counts = shipmentOutcomeCounts(history)
        assertEquals(2, counts[ShipmentOutcome.DELIVERED])
        assertEquals(1, counts[ShipmentOutcome.RAIDED])
        assertEquals(0, counts[ShipmentOutcome.RECALLED])
    }
}

package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchPlanTest {
    private fun camper(uuid: String, perception: Int) = WatchCamper(uuid, perception)

    // ── suggestion ──────────────────────────────────────────────────────────────────────────────
    @Test
    fun everyCamperIsAssignedExactlyOnce() {
        val campers = listOf(
            camper("a", 9), camper("b", 7), camper("c", 5), camper("d", 3), camper("e", 1),
        )
        val slots = suggestWatchOrder(campers, slotCount = 3)
        assertEquals(3, slots.size)
        assertEquals(setOf("a", "b", "c", "d", "e"), slots.flatten().toSet())
        assertEquals(5, slots.flatten().size)  // no duplicates
    }

    @Test
    fun bestWatchersSpreadAcrossDistinctSlots() {
        // top-3 perception (9, 7, 5) each land in a different slot on the first snake pass
        val campers = listOf(
            camper("best", 9), camper("second", 7), camper("third", 5),
            camper("d", 3), camper("e", 2), camper("f", 1),
        )
        val slots = suggestWatchOrder(campers, slotCount = 3)
        val topSlots = listOf("best", "second", "third").map { top ->
            slots.indexOfFirst { top in it }
        }
        assertEquals(3, topSlots.toSet().size)
    }

    @Test
    fun snakeOrderPairsWeakWithStrong() {
        // 4 campers, 2 slots: snake deals a->0, b->1, then c->1, d->0
        val slots = suggestWatchOrder(
            listOf(camper("a", 8), camper("b", 6), camper("c", 4), camper("d", 2)),
            slotCount = 2,
        )
        assertEquals(listOf("a", "d"), slots[0])
        assertEquals(listOf("b", "c"), slots[1])
    }

    @Test
    fun slotSizesDifferByAtMostOne() {
        val campers = (1..7).map { camper("c$it", it) }
        val slots = suggestWatchOrder(campers, slotCount = 3)
        val sizes = slots.map { it.size }
        assertTrue(sizes.max() - sizes.min() <= 1, "sizes were $sizes")
    }

    @Test
    fun degenerateInputsAreSafe() {
        assertEquals(emptyList(), suggestWatchOrder(listOf(camper("a", 1)), slotCount = 0))
        assertEquals(listOf(emptyList(), emptyList()), suggestWatchOrder(emptyList(), slotCount = 2))
    }

    // ── validation ──────────────────────────────────────────────────────────────────────────────
    @Test
    fun fullValidAssignmentIsClean() {
        val v = validateWatchAssignments(
            nonExemptUuids = listOf("a", "b", "c"),
            slots = listOf(listOf("a"), listOf("b", "c")),
        )
        assertTrue(v.isClean)
    }

    @Test
    fun unassignedEmptyAndDuplicateAreAllReported() {
        val v = validateWatchAssignments(
            nonExemptUuids = listOf("a", "b", "c"),
            slots = listOf(listOf("a", "a"), emptyList(), listOf("b")),
        )
        assertEquals(listOf("c"), v.unassignedUuids)
        assertEquals(listOf(1), v.emptySlotIndices)
        assertEquals(listOf("a"), v.duplicateUuids)
        assertTrue(!v.isClean)
    }

    @Test
    fun suggestionAlwaysValidates() {
        val campers = (1..6).map { camper("c$it", 7 - it) }
        val slots = suggestWatchOrder(campers, slotCount = 3)
        val v = validateWatchAssignments(campers.map { it.uuid }, slots)
        assertTrue(v.isClean)
    }

    // ── slot time ranges ────────────────────────────────────────────────────────────────────────
    @Test
    fun slotRangesUseTheRestingDivisionWithRemainderInTheLastSlot() {
        // 8h watch, 3 slots: slotDuration = 9600s; the same division resting uses for encounters
        assertEquals(0 to 9600, watchSlotOffsetRange(28800, 3, 0))
        assertEquals(9600 to 19200, watchSlotOffsetRange(28800, 3, 1))
        assertEquals(19200 to 28800, watchSlotOffsetRange(28800, 3, 2))
        // uneven division: last slot absorbs the remainder
        assertEquals(6666 to 10000, watchSlotOffsetRange(10000, 3, 2))
    }

    @Test
    fun degenerateRangesReturnNull() {
        assertNull(watchSlotOffsetRange(0, 3, 0))
        assertNull(watchSlotOffsetRange(3600, 0, 0))
        assertNull(watchSlotOffsetRange(3600, 2, 2))  // index out of range
        assertNull(watchSlotOffsetRange(3600, 2, -1))
    }
}

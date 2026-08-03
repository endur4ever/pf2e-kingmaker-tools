package at.posselt.pfrpg2e.camping

/**
 * Pure logic for the watch panel (card t_209ec03a): the watch-order suggestion heuristic, the
 * non-blocking assignment validation, and the per-slot time-range math. The CampingSheet wires
 * these into the Set Watches section; the drag/drop assignment path is untouched.
 */

/** A camper eligible for watch duty (already filtered by `actorUuidsNotKeepingWatch` in jsMain). */
data class WatchCamper(
    val uuid: String,
    val perceptionModifier: Int,
)

/**
 * Suggest a watch order: every camper is assigned to exactly one of [slotCount] slots, spreading
 * the best Perception evenly across the night.
 *
 * Heuristic (deliberately simple, no optimality claims): sort campers by Perception descending and
 * deal them out in snake order (slot 0..n-1, then n-1..0, ...). The first pass puts the strongest
 * watchers in distinct slots; the return pass pairs the weakest with the strongest, keeping each
 * slot's total roughly level. Slot sizes differ by at most one; ties keep input order (stable sort)
 * so repeated clicks produce the same suggestion.
 *
 * Returns one uuid-list per slot; empty when [slotCount] <= 0.
 */
fun suggestWatchOrder(campers: List<WatchCamper>, slotCount: Int): List<List<String>> {
    if (slotCount <= 0) return emptyList()
    val slots = List(slotCount) { mutableListOf<String>() }
    val sorted = campers.sortedByDescending { it.perceptionModifier }
    sorted.forEachIndexed { i, camper ->
        val round = i / slotCount
        val pos = i % slotCount
        val slotIndex = if (round % 2 == 0) pos else slotCount - 1 - pos
        slots[slotIndex] += camper.uuid
    }
    return slots
}

/** Non-blocking issues with the current watch assignments, for the warning row. */
data class WatchValidation(
    /** Non-exempt campers assigned to no slot. */
    val unassignedUuids: List<String>,
    /** Zero-based indices of slots with nobody on duty. */
    val emptySlotIndices: List<Int>,
    /** Campers assigned to more than one slot. */
    val duplicateUuids: List<String>,
) {
    val isClean: Boolean
        get() = unassignedUuids.isEmpty() && emptySlotIndices.isEmpty() && duplicateUuids.isEmpty()
}

/**
 * Validate [slots] against the [nonExemptUuids] expected to stand watch: everyone should appear in
 * exactly one slot and no slot should be empty. Advisory only — nothing blocks on this. Uuids in
 * slots that are not in [nonExemptUuids] (departed/exempt leftovers) are ignored here; result lists
 * preserve input order.
 */
fun validateWatchAssignments(
    nonExemptUuids: List<String>,
    slots: List<List<String>>,
): WatchValidation {
    val assigned = slots.flatten()
    val assignedSet = assigned.toSet()
    val counts = assigned.groupingBy { it }.eachCount()
    return WatchValidation(
        unassignedUuids = nonExemptUuids.filter { it !in assignedSet },
        emptySlotIndices = slots.mapIndexedNotNull { i, slot -> if (slot.isEmpty()) i else null },
        duplicateUuids = nonExemptUuids.filter { (counts[it] ?: 0) > 1 },
    )
}

/**
 * The second-offset range `[start, end)` a watch slot covers within the night. Uses the SAME
 * division the rest flow uses to map a random encounter to its on-duty slot
 * (`slotDuration = watchDurationSeconds / slotCount`, see resting/Resting.kt) so the display can
 * never disagree with the mechanics; the last slot absorbs the integer-division remainder.
 * Returns null when there is nothing to divide.
 */
fun watchSlotOffsetRange(watchDurationSeconds: Int, slotCount: Int, index: Int): Pair<Int, Int>? {
    if (watchDurationSeconds <= 0 || slotCount <= 0 || index !in 0 until slotCount) return null
    val slotDuration = watchDurationSeconds / slotCount
    val start = slotDuration * index
    val end = if (index == slotCount - 1) watchDurationSeconds else slotDuration * (index + 1)
    return start to end
}

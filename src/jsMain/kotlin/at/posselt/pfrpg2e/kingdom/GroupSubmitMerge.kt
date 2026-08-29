package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawGroup

/**
 * Rebuild the kingdom's group array from a sheet submit without losing the fields the sheet does
 * not render as inputs.
 *
 * The kingdom sheet's DataModel declares only the six editable group fields, and the form only
 * carries those six, so [submitted] entries always come back with `standing`, `standingLog` and
 * `allianceLevel` missing. Assigning that array straight onto the kingdom therefore erased a
 * faction's whole attitude history every time anyone saved the sheet -- including the war-standing
 * deltas and log entries this subsystem writes.
 *
 * Groups are matched positionally while the two arrays are the same length, which is the normal
 * case and the only one that survives a RENAME: the submitted row still holds the old row's
 * history even though its name just changed.
 *
 * When the lengths differ the form is stale -- another client deleted or added a group between
 * render and submit -- and position no longer means anything. Falling back to a name lookup there
 * stops a deleted faction being resurrected carrying its neighbour's standing and log, which is the
 * very data loss this function exists to prevent, arriving by a different route.
 */
fun mergeSubmittedGroups(
    submitted: Array<RawGroup>,
    existing: Array<RawGroup>,
): Array<RawGroup> {
    val aligned = submitted.size == existing.size
    return submitted.mapIndexed { index, group ->
        val previous = if (aligned) existing.getOrNull(index) else existing.find { it.name == group.name }
        RawGroup.copy(
            group,
            standing = previous?.standing,
            standingLog = previous?.standingLog,
            allianceLevel = previous?.allianceLevel,
            // the agenda is engine-owned state the form never renders: dropping it here was
            // the standing-history wipe all over again, one field later
            agenda = previous?.agenda,
        )
    }.toTypedArray()
}

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
 * Groups are matched positionally: the sheet renders them in kingdom order with index-addressed
 * field names (`groups.$index.name`), and adding or deleting a group goes through its own
 * data-action handler that persists and re-renders, so the two arrays stay aligned across a submit.
 */
fun mergeSubmittedGroups(
    submitted: Array<RawGroup>,
    existing: Array<RawGroup>,
): Array<RawGroup> =
    submitted.mapIndexed { index, group ->
        val previous = existing.getOrNull(index)
        RawGroup.copy(
            group,
            standing = previous?.standing,
            standingLog = previous?.standingLog,
            allianceLevel = previous?.allianceLevel,
        )
    }.toTypedArray()

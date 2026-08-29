package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.MilestoneChoice

/**
 * Rebuild the kingdom's milestone choices from a sheet submit without losing the fields the sheet
 * does not render as inputs.
 *
 * The sheet DataModel declares only `id`, `completed` and `enabled`, so submitted rows always come
 * back with `offerDismissed` and `awardedOnTurn` missing, and assigning that array straight onto
 * the kingdom erased both on every save. `offerDismissed` is the flag that stops a level-triggered
 * deed re-offering forever, so losing it silently resurrected every offer the GM had refused --
 * the precise failure that flag exists to prevent. This is the third field-wipe of this shape in
 * this repo (see [mergeSubmittedGroups] and [mergeNpcMemoryFields]); the cause is always a schema
 * that renders a subset and a submit that assigns the whole.
 *
 * Matched by **id**: milestone ids are stable and unique, and the form neither reorders nor
 * renames them. An id with no predecessor is a genuinely new choice and keeps its blank state.
 */
fun mergeSubmittedMilestones(
    submitted: Array<MilestoneChoice>,
    existing: Array<MilestoneChoice>,
): Array<MilestoneChoice> {
    val previous = existing.associateBy { it.id }
    if (previous.isEmpty()) return submitted
    return submitted.map { choice ->
        val prior = previous[choice.id] ?: return@map choice
        MilestoneChoice.copy(
            choice,
            offerDismissed = prior.offerDismissed,
            awardedOnTurn = prior.awardedOnTurn,
        )
    }.toTypedArray()
}

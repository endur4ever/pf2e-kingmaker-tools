package at.posselt.pfrpg2e.kingdom.structures

/**
 * Carry engine-owned state from the LIVE settlement onto a copy submitted by a dialog.
 *
 * Inspect Settlement copies the RawSettlement when it OPENS and hands that whole copy back on
 * Save; the sheet then replaces the live row with it wholesale. Anything an End Turn wrote in
 * between -- a life-event record, a siege razing a structure, a GM resolving a digest row -- is
 * silently reverted by a Save that never touched those fields. The same wipe class as the
 * faction agendas and the milestone offers (see mergeSubmittedGroups / mergeSubmittedMilestones):
 * the dialog renders none of these, so the dialog's copy can never be the authority on them.
 *
 * The roster is NOT carried: the dialog edits it, so the submitted roster is the newer one.
 */
fun carrySettlementEngineState(live: RawSettlement?, submitted: RawSettlement): RawSettlement {
    if (live == null) return submitted
    submitted.lifeEventHistory = live.lifeEventHistory
    submitted.destroyedStructureIds = live.destroyedStructureIds
    return submitted
}

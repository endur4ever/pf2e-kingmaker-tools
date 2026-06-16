package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.setAppFlag

/**
 * Per-turn tracking of which kingdom activities have been performed, so the Turn tab can show
 * how many activities remain in each capped phase (leadership, civic, region, army, commerce).
 *
 * The counts are stored on the existing `turn-wizard-state` actor flag under `activitiesPerformed`,
 * shaped as `{ [activityId: string]: number }` (how many times the activity was attempted this turn).
 * The flag is reset at End Turn, see [clearPerformedActivities].
 */
private const val TURN_WIZARD_STATE = "turn-wizard-state"

/** Returns the per-activity performed counts for the current turn, keyed by activity id. */
fun KingdomActor.getPerformedActivities(): Map<String, Int> {
    val state = getAppFlag<KingdomActor, dynamic>(TURN_WIZARD_STATE) ?: return emptyMap()
    val performed = state.activitiesPerformed ?: return emptyMap()
    val result = mutableMapOf<String, Int>()
    val keys = js("Object.keys")(performed).unsafeCast<Array<String>>()
    for (key in keys) {
        result[key] = performed[key].unsafeCast<Int>()
    }
    return result
}

private fun readOrCreateState(actor: KingdomActor): dynamic {
    val state = actor.getAppFlag<KingdomActor, dynamic>(TURN_WIZARD_STATE) ?: js("{}")
    if (state.activitiesPerformed == null) {
        state.activitiesPerformed = js("{}")
    }
    return state
}

/** Increments the performed count for [activityId] (called when its check is rolled). */
suspend fun KingdomActor.recordActivityPerformed(activityId: String) {
    val state = readOrCreateState(this)
    val current = state.activitiesPerformed[activityId].unsafeCast<Int?>() ?: 0
    state.activitiesPerformed[activityId] = current + 1
    setAppFlag(TURN_WIZARD_STATE, state)
}

/** Toggles [activityId] between performed once (1) and not performed (0) for manual tracking. */
suspend fun KingdomActor.toggleActivityPerformed(activityId: String) {
    val state = readOrCreateState(this)
    val current = state.activitiesPerformed[activityId].unsafeCast<Int?>() ?: 0
    state.activitiesPerformed[activityId] = if (current > 0) 0 else 1
    setAppFlag(TURN_WIZARD_STATE, state)
}

/** Clears all performed counts, preserving any other turn-wizard state. Called at End Turn. */
suspend fun KingdomActor.clearPerformedActivities() {
    val state = getAppFlag<KingdomActor, dynamic>(TURN_WIZARD_STATE) ?: return
    state.activitiesPerformed = js("{}")
    setAppFlag(TURN_WIZARD_STATE, state)
}

/**
 * Sums per-activity performed counts into per-phase totals using an activity id -> phase map.
 * Activities with no known phase are ignored. Pure function to keep it unit-testable.
 */
fun sumPerformedByPhase(
    performedByActivityId: Map<String, Int>,
    phaseByActivityId: Map<String, String>,
): Map<String, Int> {
    val result = mutableMapOf<String, Int>()
    for ((activityId, count) in performedByActivityId) {
        val phase = phaseByActivityId[activityId] ?: continue
        result[phase] = (result[phase] ?: 0) + count
    }
    return result
}

package at.posselt.pfrpg2e.kingdom.pressure

import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawScheduledPressure

/**
 * Resolve-condition polling (plan SS5): POLLED at read time against the linked record, never
 * hooked -- the quest and threat systems know nothing about schedules. Shared by the daily tick
 * (suspend firing) and the Deadlines section (show "resolved"), so the two can never disagree.
 * A ref that matches nothing resolves to false: the schedule keeps firing rather than silently
 * concluding on a typo.
 */
fun isPressureResolved(raw: RawScheduledPressure, kingdom: KingdomData): Boolean {
    val ref = raw.resolveConditionRef ?: return false
    return when (raw.resolveConditionKind) {
        "questCompleted" -> kingdom.quests?.any { it.id == ref && it.status == "completed" } == true
        "threatResolved" -> kingdom.warThreats?.any {
            it.id == ref && (it.status != WarThreatStatus.ACTIVE.value || it.peaceSettled == true)
        } == true
        else -> false
    }
}

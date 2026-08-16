package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess

/**
 * Per-activity usage state for the kingdom-activity timeout/lockout + escalating-DC rules
 * (RAW: several leadership activities lock out for N turns on Failure/Critical Failure, and
 * Clandestine Business / Request Foreign Aid (V&K) raise their DC each consecutive turn used).
 *
 * Pure model + logic so it is unit-testable in commonTest; the jsMain layer converts to/from
 * the persisted [at.posselt.pfrpg2e.kingdom.RawActivityBlock] array.
 *
 * - [lockedUntilTurn]: the activity is unavailable while `kingdom.currentTurn < lockedUntilTurn`.
 *   Because it is compared against the live turn number, locks self-expire — no per-turn
 *   decrement is needed.
 * - [dcBump]: the current DC increase in effect for an escalating-DC activity. Ticked at end of
 *   turn: +[ESCALATING_DC_STEP] if used this turn, else −[ESCALATING_DC_DECAY] (floored at 0).
 * - [usedThisTurn]: whether an escalating-DC activity was used during the current turn (drives
 *   the tick above; reset to false each tick).
 */
data class ActivityUsage(
    val activityId: String,
    val lockedUntilTurn: Int? = null,
    val dcBump: Int = 0,
    val usedThisTurn: Boolean = false,
)

/** Turns an activity is locked out for, keyed by the triggering degree of success. */
private data class TimeoutRule(val failure: Int? = null, val criticalFailure: Int? = null)

private val timeoutRules: Map<String, TimeoutRule> = mapOf(
    "false-victory" to TimeoutRule(failure = 1, criticalFailure = 6),
    "garrison-army" to TimeoutRule(criticalFailure = 4),
    "preventative-measures" to TimeoutRule(criticalFailure = 1),
    "process-hidden-fees" to TimeoutRule(failure = 1, criticalFailure = 1),
    "supplementary-hunting" to TimeoutRule(failure = 1, criticalFailure = 1),
    "supernatural-solution" to TimeoutRule(criticalFailure = 2),
    // 3, not 2: the activity's own criticalFailure text says "for the next 3 Kingdom Turns".
    "send-diplomatic-envoy" to TimeoutRule(criticalFailure = 3),
)

/** Activities whose DC climbs each consecutive Kingdom turn they are used. */
val escalatingDcActivities: Set<String> = setOf("clandestine-business", "request-foreign-aid-vk")

const val ESCALATING_DC_STEP = 2
const val ESCALATING_DC_DECAY = 1

/** Number of turns [activityId] is locked out for on [degree], or null if that degree has no timeout. */
fun activityTimeoutTurns(activityId: String, degree: DegreeOfSuccess): Int? {
    val rule = timeoutRules[activityId] ?: return null
    return when (degree) {
        DegreeOfSuccess.FAILURE -> rule.failure
        DegreeOfSuccess.CRITICAL_FAILURE -> rule.criticalFailure
        else -> null
    }
}

fun activityEscalatesDc(activityId: String): Boolean = activityId in escalatingDcActivities

/** True when [activityId] has any timeout or escalating-DC behaviour worth tracking. */
fun activityTracksUsage(activityId: String): Boolean =
    activityId in timeoutRules || activityEscalatesDc(activityId)

private fun List<ActivityUsage>.find(activityId: String): ActivityUsage? =
    firstOrNull { it.activityId == activityId }

/** The turn on which [activityId] becomes available again, or null if it is not locked. */
fun activityLockedUntil(usages: List<ActivityUsage>, activityId: String): Int? =
    usages.find(activityId)?.lockedUntilTurn

/** Whether [activityId] is currently locked out (given the live [currentTurn]). */
fun isActivityLocked(usages: List<ActivityUsage>, activityId: String, currentTurn: Int): Boolean {
    val until = activityLockedUntil(usages, activityId) ?: return false
    return currentTurn < until
}

/** The DC increase currently in effect for [activityId] (0 when not escalated). */
fun activityDcBump(usages: List<ActivityUsage>, activityId: String): Int =
    usages.find(activityId)?.dcBump ?: 0

/**
 * Record that [activityId] was performed on [currentTurn] with [degree]. Writes the lockout
 * (available again on `currentTurn + timeout + 1`, so a 1-turn timeout blocks exactly the next
 * turn) and flags escalating-DC activities as used-this-turn. Returns the state unchanged for
 * activities that track no usage.
 */
fun recordActivityUse(
    usages: List<ActivityUsage>,
    activityId: String,
    degree: DegreeOfSuccess,
    currentTurn: Int,
): List<ActivityUsage> {
    if (!activityTracksUsage(activityId)) return usages
    val timeout = activityTimeoutTurns(activityId, degree)
    val escalates = activityEscalatesDc(activityId)
    val existing = usages.find(activityId)
    val updated = (existing ?: ActivityUsage(activityId)).copy(
        // Assigned from the NEW degree unconditionally, so a superseding result can RELEASE a lock
        // and not merely tighten one. Previously any non-failure degree returned the list
        // untouched, which meant a Fame reroll -- the whole point of which is to undo a critical
        // failure -- left the six-turn lockout it had just erased standing, and a GM upgrading a
        // degree could never shorten one either.
        lockedUntilTurn = if (timeout != null) currentTurn + timeout + 1 else null,
        usedThisTurn = if (escalates) true else (existing?.usedThisTurn ?: false),
    )
    val others = usages.filter { it.activityId != activityId }
    // Drop an entry that now records nothing rather than persisting an empty husk.
    return if (updated.lockedUntilTurn == null && updated.dcBump == 0 && !updated.usedThisTurn) {
        others
    } else {
        others + updated
    }
}

/**
 * End-of-turn tick: adjust each escalating-DC bump (+[ESCALATING_DC_STEP] if used this turn,
 * else −[ESCALATING_DC_DECAY] floored at 0), reset used-this-turn, and drop entries that have
 * both expired their lock and decayed to a 0 bump. [nextTurn] is the turn being ticked into.
 */
fun tickActivityUsages(usages: List<ActivityUsage>, nextTurn: Int): List<ActivityUsage> =
    usages.mapNotNull { u ->
        val newBump = if (u.usedThisTurn) {
            u.dcBump + ESCALATING_DC_STEP
        } else {
            (u.dcBump - ESCALATING_DC_DECAY).coerceAtLeast(0)
        }
        val stillLocked = u.lockedUntilTurn != null && nextTurn < u.lockedUntilTurn
        val next = u.copy(
            dcBump = newBump,
            usedThisTurn = false,
            lockedUntilTurn = if (stillLocked) u.lockedUntilTurn else null,
        )
        if (next.lockedUntilTurn == null && next.dcBump == 0) null else next
    }

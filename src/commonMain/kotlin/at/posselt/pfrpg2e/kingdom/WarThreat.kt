package at.posselt.pfrpg2e.kingdom

import kotlinx.js.JsPlainObject

/**
 * Minimal war threat snapshot for pure trigger detection logic.
 * Mirrors the relevant fields of [RawWarThreat] but lives in commonMain
 * so it can be used from commonTest without JS interop.
 */
@JsPlainObject
external interface WarThreatSnapshot {
    val id: String
    val name: String
    var escalationLevel: Int
    var maxEscalation: Int
    var eta: Int?
    var pauseOnExpiry: Boolean
    var status: String
    var triggeredTurn: Int?
    var offerConsumed: Boolean?
}

/**
 * Detects threats that were newly triggered during a tick.
 *
 * A threat is "newly triggered" if:
 * - It was ACTIVE before the tickWarThreatStatus before the tick
 * - Its [triggeredTurn] was null before the tick
 * - Its [triggeredTurn] is non-null after the tick (and equals [currentTurn])
 * - Its [offerConsumed] is not true (idempotency guard)
 *
 * This captures both hard expiry (status -> EXPIRED) and soft-pause
 * (status stays ACTIVE with [pauseOnExpiry] == true) cases.
 *
 * @param before Threat snapshots before [tickWarThreats] was called.
 * @param after Threat snapshots after [tickWarThreats] was called.
 * @param currentTurn The turn number being ticked into.
 * @return Threats from [after] that were newly triggered this tick.
 */
fun detectNewlyTriggeredThreats(
    before: Array<WarThreatSnapshot>,
    after: Array<WarThreatSnapshot>,
    currentTurn: Int,
): List<WarThreatSnapshot> {
    val beforeMap = before.associateBy { it.id }
    return after.filter { threat ->
        val beforeThreat = beforeMap[threat.id]
        if (beforeThreat == null) return@filter false // New threat added this tick (not a trigger)
        val wasActive = beforeThreat.status == "active"
        val wasTriggered = beforeThreat.triggeredTurn != null
        val isNowTriggered = threat.triggeredTurn != null
        val alreadyConsumed = threat.offerConsumed == true
        wasActive && !wasTriggered && isNowTriggered && threat.triggeredTurn == currentTurn && !alreadyConsumed
    }
}
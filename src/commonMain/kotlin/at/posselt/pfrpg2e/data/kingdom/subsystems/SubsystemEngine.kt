package at.posselt.pfrpg2e.data.kingdom.subsystems

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.toCamelCase

/**
 * The generic point-pool math behind the PF2e Influence and Research subsystems (GM Core). One
 * engine, two skins: an influence NPC and a research project are both point pools with degree-of-
 * success rules, trait adjustments and thresholds that fire exactly once when crossed.
 */

/**
 * The four degrees of success, persisted as camelCase values; fromString returns null for
 * anything else, which the jsMain adapter logs and skips rather than throwing.
 */
enum class SubsystemOutcome : ValueEnum {
    CRITICAL_SUCCESS,
    SUCCESS,
    FAILURE,
    CRITICAL_FAILURE;

    companion object {
        fun fromString(value: String) = fromCamelCase<SubsystemOutcome>(value)
    }

    override val value: String
        get() = toCamelCase()
}

/** Points per degree of success. The PF2e Influence default: +2 / +1 / 0 / -1. */
data class PointRule(
    val criticalSuccess: Int = 2,
    val success: Int = 1,
    val failure: Int = 0,
    val criticalFailure: Int = -1,
)

/** A resistance (delta < 0) or weakness (delta > 0) keyed by a trait label. */
data class SubsystemTrait(val label: String, val delta: Int)

/** One PC's running contribution to a pool. */
data class ParticipantPoints(val uuid: String, val points: Int)

/** Base points for an outcome, before trait adjustments. */
fun pointsForOutcome(outcome: SubsystemOutcome, rule: PointRule): Int = when (outcome) {
    SubsystemOutcome.CRITICAL_SUCCESS -> rule.criticalSuccess
    SubsystemOutcome.SUCCESS -> rule.success
    SubsystemOutcome.FAILURE -> rule.failure
    SubsystemOutcome.CRITICAL_FAILURE -> rule.criticalFailure
}

/**
 * Applies matched resistances and weaknesses to a base GAIN.
 *
 * Resistances can grind a positive gain down to zero but never turn it into a loss -- a
 * resistance is the NPC being hard to charm, not actively souring. A LOSS (crit fail) passes
 * through untouched: the traits modify what you win, not what you fumble. Weaknesses stack on
 * top of a gain normally.
 */
fun adjustForTraits(basePoints: Int, matched: List<SubsystemTrait>): Int {
    if (basePoints <= 0) return basePoints
    val adjusted = basePoints + matched.sumOf { it.delta }
    return maxOf(adjusted, 0)
}

/** The new pool total and the signed delta that ACTUALLY landed, for the check log. */
data class PointApplication(val newTotal: Int, val appliedDelta: Int)

/**
 * One recorded check against the pool: clamp(previous + adjusted, min 0). The applied delta is
 * `newTotal - previous`, so a crit-fail at zero logs 0, not -1 -- the log records what happened
 * to the pool, not what the dice deserved.
 */
fun applyCheck(
    previous: Int,
    outcome: SubsystemOutcome,
    rule: PointRule,
    matched: List<SubsystemTrait>,
): PointApplication {
    val adjusted = adjustForTraits(pointsForOutcome(outcome, rule), matched)
    val newTotal = maxOf(previous + adjusted, 0)
    return PointApplication(newTotal = newTotal, appliedDelta = newTotal - previous)
}

/**
 * Which thresholds are NEWLY crossed moving previous -> next, so an offer fires exactly once per
 * threshold -- the crossing-tick contract shouldFireWarThreat established. Movement DOWN never
 * fires (losing points does not un-earn a revealed secret), and re-crossing after a dip does not
 * re-fire because the caller marks fired thresholds on the row.
 */
fun newlyCrossedThresholds(previous: Int, next: Int, thresholds: List<Int>): List<Int> =
    if (next <= previous) emptyList()
    else thresholds.filter { it in (previous + 1)..next }.sorted()

/** The participant list with [uuid]'s points moved by [delta]; a new PC gets a fresh row. */
fun applyParticipantDelta(
    participants: List<ParticipantPoints>,
    uuid: String,
    delta: Int,
): List<ParticipantPoints> =
    if (participants.any { it.uuid == uuid }) {
        participants.map { if (it.uuid == uuid) it.copy(points = it.points + delta) else it }
    } else {
        participants + ParticipantPoints(uuid = uuid, points = delta)
    }

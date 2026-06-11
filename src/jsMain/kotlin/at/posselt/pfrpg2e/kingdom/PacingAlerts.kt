package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.PacingAlertSeverity
import at.posselt.pfrpg2e.kingdom.data.PacingAlertType
import at.posselt.pfrpg2e.kingdom.data.RawPacingAlert
import kotlin.math.abs

/**
 * Pure balance/pacing evaluation logic (roadmap #13). Advisory only — these
 * functions observe campaign metrics and emit [RawPacingAlert]s; they never
 * mutate game state. No Foundry dependencies, so fully unit-testable; the turn
 * tick + sheet panel (later phases) gather the metrics and surface the alerts.
 *
 * Alert `message` holds the type's i18n key for the UI/chat to localize.
 */

// ── KingdomSettings threshold accessors (nullable fields, back-compat) ──

fun KingdomSettings.pacingMinUnrestDelta(): Int = pacingAlertMinUnrestDelta ?: 1
fun KingdomSettings.pacingMaxTurnGap(): Int = pacingAlertMaxTurnGap ?: 10
fun KingdomSettings.pacingLevelMismatchRange(): Int = pacingAlertLevelMismatchRange ?: 2
fun KingdomSettings.pacingLootImbalanceEnabled(): Boolean = pacingAlertLootImbalanceEnabled != false

/** A fixed campaign chapter level to pace against, or null to track the party's average. */
fun KingdomSettings.pacingChapterTargetLevel(): Int? = pacingAlertChapterTargetLevel?.takeIf { it > 0 }

/** Loot-imbalance tolerance; falls back to the level-mismatch range when not set. */
fun KingdomSettings.pacingLootImbalanceRange(): Int = pacingAlertLootImbalanceRange ?: pacingLevelMismatchRange()

private fun alert(
    type: PacingAlertType,
    severity: PacingAlertSeverity,
    turn: Int,
    relatedEntityId: String? = null,
): RawPacingAlert = RawPacingAlert(
    id = "pacing-${type.value}-$turn",
    type = type.value,
    severity = severity.value,
    message = type.i18nKey,
    turnCreated = turn,
    relatedEntityId = relatedEntityId,
)

/** Kingdom level too far from the chapter/threat target level. */
fun evaluateLevelMismatch(kingdomLevel: Int, targetLevel: Int, range: Int, turn: Int): RawPacingAlert? {
    val diff = abs(kingdomLevel - targetLevel)
    if (diff <= range) return null
    val severity = if (diff > range * 2) PacingAlertSeverity.CRITICAL else PacingAlertSeverity.WARNING
    return alert(PacingAlertType.LEVEL_MISMATCH, severity, turn)
}

/** Unrest hasn't moved for too many turns — the campaign is stagnating. */
fun evaluateStagnation(turnsSinceUnrestChange: Int, maxTurnGap: Int, turn: Int): RawPacingAlert? {
    if (turnsSinceUnrestChange < maxTurnGap) return null
    val severity = if (turnsSinceUnrestChange >= maxTurnGap * 2) PacingAlertSeverity.CRITICAL else PacingAlertSeverity.WARNING
    return alert(PacingAlertType.STAGNATION, severity, turn)
}

/** Too many turns without a campaign-altering event. */
fun evaluateTurnGap(turnsSinceLastEvent: Int, maxTurnGap: Int, turn: Int): RawPacingAlert? {
    if (turnsSinceLastEvent < maxTurnGap) return null
    return alert(PacingAlertType.TURN_GAP, PacingAlertSeverity.WARNING, turn)
}

/** Result of advancing the unrest-stagnation tracker by one turn. */
data class StagnationTrack(
    val turnsSinceUnrestChange: Int,
    val alert: RawPacingAlert?,
)

/**
 * Advance the unrest-stagnation tracker by one turn. Emits an alert only when the
 * counter *first* reaches the warning ([maxTurnGap]) or critical (2×[maxTurnGap])
 * threshold, so the kingdom doesn't spam a pacing advisory every turn it stays static.
 * The counter resets to 0 whenever unrest moves by at least [minDelta].
 */
fun trackUnrestStagnation(
    previousUnrest: Int?,
    currentUnrest: Int,
    previousCount: Int?,
    maxTurnGap: Int,
    minDelta: Int,
    turn: Int,
): StagnationTrack {
    val stagnant = previousUnrest != null && abs(currentUnrest - previousUnrest) < minDelta
    val count = if (stagnant) (previousCount ?: 0) + 1 else 0
    val alert = if (count == maxTurnGap || count == maxTurnGap * 2) {
        evaluateStagnation(count, maxTurnGap, turn)
    } else {
        null
    }
    return StagnationTrack(count, alert)
}

/**
 * Emit a turn-gap alert only the turn [turnsSinceLastEvent] first reaches the
 * warning threshold ([maxTurnGap]) and again at 2× for a reminder — so a long
 * event drought doesn't re-warn on every event check. Returns null otherwise.
 * Intended to be called at the single site that increments the counter.
 */
fun trackTurnGap(turnsSinceLastEvent: Int, maxTurnGap: Int, turn: Int): RawPacingAlert? {
    if (turnsSinceLastEvent != maxTurnGap && turnsSinceLastEvent != maxTurnGap * 2) return null
    return evaluateTurnGap(turnsSinceLastEvent, maxTurnGap, turn)
}

/** Result of evaluating a persistent (state-based) pacing condition one turn. */
data class PacingStateTrack(
    /** Current severity to persist (null when the condition is clear). */
    val severity: String?,
    /** Non-null only when the condition newly appears or escalates — fire-once. */
    val alert: RawPacingAlert?,
)

/** Fire a state-based [evaluated] alert only when its severity changes from [previousSeverity]. */
private fun trackSeverityChange(evaluated: RawPacingAlert?, previousSeverity: String?): PacingStateTrack {
    val currentSeverity = evaluated?.severity
    val shouldFire = evaluated != null && currentSeverity != previousSeverity
    return PacingStateTrack(
        severity = currentSeverity,
        alert = if (shouldFire) evaluated else null,
    )
}

/**
 * Compare the kingdom level to the party's level and fire only when the mismatch
 * *changes* — newly appears, or escalates (warning → critical) — so a persistent
 * gap doesn't re-warn every turn. [previousSeverity] is the last persisted state;
 * when the gap closes the returned severity is null so a later recurrence fires again.
 */
fun trackLevelMismatch(
    kingdomLevel: Int,
    partyLevel: Int,
    range: Int,
    previousSeverity: String?,
    turn: Int,
): PacingStateTrack =
    trackSeverityChange(evaluateLevelMismatch(kingdomLevel, partyLevel, range, turn), previousSeverity)

/** Settlements grant item access far above the party's level — breaks wealth-by-level. */
fun evaluateLootImbalance(itemAccessLevel: Int, partyLevel: Int, range: Int, turn: Int): RawPacingAlert? {
    val diff = itemAccessLevel - partyLevel
    if (diff <= range) return null
    val severity = if (diff > range * 2) PacingAlertSeverity.CRITICAL else PacingAlertSeverity.WARNING
    return alert(PacingAlertType.LOOT_IMBALANCE, severity, turn)
}

/** Like [trackLevelMismatch] but for settlement item access (fires only on severity change). */
fun trackLootImbalance(
    itemAccessLevel: Int,
    partyLevel: Int,
    range: Int,
    previousSeverity: String?,
    turn: Int,
): PacingStateTrack =
    trackSeverityChange(evaluateLootImbalance(itemAccessLevel, partyLevel, range, turn), previousSeverity)

/** Snapshot of the metrics the pacing system evaluates each turn. */
data class PacingMetrics(
    val kingdomLevel: Int,
    val targetLevel: Int,
    val turnsSinceUnrestChange: Int,
    val turnsSinceLastEvent: Int,
    val turn: Int,
)

fun evaluatePacingAlerts(metrics: PacingMetrics, settings: KingdomSettings): Array<RawPacingAlert> =
    listOfNotNull(
        evaluateLevelMismatch(metrics.kingdomLevel, metrics.targetLevel, settings.pacingLevelMismatchRange(), metrics.turn),
        evaluateStagnation(metrics.turnsSinceUnrestChange, settings.pacingMaxTurnGap(), metrics.turn),
        evaluateTurnGap(metrics.turnsSinceLastEvent, settings.pacingMaxTurnGap(), metrics.turn),
    ).toTypedArray()

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

fun KingdomSettings.pacingMinUnrestDelta(): Int = pacingAlertMinUnrestDelta ?: 5
fun KingdomSettings.pacingMaxTurnGap(): Int = pacingAlertMaxTurnGap ?: 10
fun KingdomSettings.pacingLevelMismatchRange(): Int = pacingAlertLevelMismatchRange ?: 2
fun KingdomSettings.pacingLootImbalanceEnabled(): Boolean = pacingAlertLootImbalanceEnabled != false

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

package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawScheduledPressure
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.pressure.PayloadKind
import at.posselt.pfrpg2e.kingdom.pressure.Recurrence
import at.posselt.pfrpg2e.kingdom.pressure.isPressureResolved
import at.posselt.pfrpg2e.kingdom.pressure.nextFiringDay
import at.posselt.pfrpg2e.kingdom.pressure.payloadLabelFor
import at.posselt.pfrpg2e.utils.t
import js.objects.recordOf
import kotlinx.js.JsPlainObject

/**
 * Deadlines rows on the Campaign tab (plan SS8). GM-ONLY BY DATA: schedules leak unarrived
 * pressure by construction, so the builder returns null for players -- the template conditional
 * is layout, never authorization.
 */
@Suppress("unused")
@JsPlainObject
external interface DeadlineRowContext {
    val id: String
    val name: String
    val countdownLabel: String
    val recurrenceLabel: String
    val payloadLabel: String
    /** Localized resolve-link description; null when the schedule has no link. */
    val resolveLabel: String?
    val resolved: Boolean
    val inactive: Boolean
}

/** Literal keys in a when -- dynamic assembly is invisible to the i18n guard (plan SS8). */
private fun recurrenceLabelFor(recurrence: Recurrence?): String = when (recurrence) {
    Recurrence.NONE -> t("kingdom.deadlines.recurrence.none")
    Recurrence.DAILY -> t("kingdom.deadlines.recurrence.daily")
    Recurrence.WEEKLY -> t("kingdom.deadlines.recurrence.weekly")
    Recurrence.MONTHLY -> t("kingdom.deadlines.recurrence.monthly")
    null -> t("kingdom.deadlines.recurrence.unknown")
}

private fun resolveLabelFor(kind: String?): String? = when (kind) {
    "questCompleted" -> t("kingdom.deadlines.resolve.questCompleted")
    "threatResolved" -> t("kingdom.deadlines.resolve.threatResolved")
    else -> null
}

fun buildDeadlinesContext(
    isGM: Boolean,
    kingdom: KingdomData,
    currentDay: Int,
): Array<DeadlineRowContext>? {
    if (!isGM) return null
    val pressures = kingdom.scheduledPressures ?: return emptyArray()
    return pressures.map { raw ->
        val model = raw.toModel()
        val resolved = isPressureResolved(raw, kingdom)
        val next = model?.let { nextFiringDay(it, afterDay = currentDay, resolved = resolved) }
        val countdown = when {
            model == null -> t("kingdom.deadlines.countdown.unknown")
            !raw.active -> t("kingdom.deadlines.countdown.inactive")
            resolved -> t("kingdom.deadlines.countdown.resolved")
            next != null -> t(
                "kingdom.deadlines.countdown.inDays",
                recordOf("days" to (next - currentDay).toString()),
            )
            else -> t("kingdom.deadlines.countdown.ended")
        }
        DeadlineRowContext(
            id = raw.id,
            name = raw.name,
            countdownLabel = countdown,
            recurrenceLabel = recurrenceLabelFor(model?.recurrence),
            payloadLabel = payloadLabelFor(PayloadKind.fromValue(raw.payloadKind)),
            resolveLabel = resolveLabelFor(raw.resolveConditionKind),
            resolved = resolved,
            inactive = !raw.active,
        )
    }.toTypedArray()
}

package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.PacingAlertSeverity
import at.posselt.pfrpg2e.kingdom.data.RawPacingAlert

/**
 * Display view-models for the Balance & Pacing Alerts panel (roadmap #13).
 *
 * [buildPacingAlertView] parses the persisted [RawPacingAlert] history into clean,
 * template-ready models: newest-first ordering, per-severity counts, and a
 * critical flag for styling. Pure + testable; the sheet section context
 * ([at.posselt.pfrpg2e.kingdom.sheet.contexts.buildPacingAlertContext]) localizes
 * the labels and the template consumes the result.
 */

data class PacingAlertItemView(
    val id: String,
    val type: String,
    val severity: String,
    /** i18n key the context layer localizes into human-readable text. */
    val messageKey: String,
    val turnCreated: Int,
    val isCritical: Boolean,
    val relatedEntityId: String? = null,
)

data class PacingAlertView(
    val hasAlerts: Boolean,
    val warningCount: Int,
    val criticalCount: Int,
    val alerts: List<PacingAlertItemView>,
)

fun buildPacingAlertView(alerts: Array<RawPacingAlert>?): PacingAlertView {
    val items = (alerts ?: emptyArray())
        .sortedByDescending { it.turnCreated }
        .map {
            PacingAlertItemView(
                id = it.id,
                type = it.type,
                severity = it.severity,
                messageKey = it.message,
                turnCreated = it.turnCreated,
                isCritical = it.severity == PacingAlertSeverity.CRITICAL.value,
                relatedEntityId = it.relatedEntityId,
            )
        }
    return PacingAlertView(
        hasAlerts = items.isNotEmpty(),
        warningCount = items.count { it.severity == PacingAlertSeverity.WARNING.value },
        criticalCount = items.count { it.isCritical },
        alerts = items,
    )
}

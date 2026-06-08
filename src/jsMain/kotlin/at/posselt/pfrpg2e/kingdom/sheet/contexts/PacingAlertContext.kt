package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.PacingAlertView
import at.posselt.pfrpg2e.kingdom.data.PacingAlertSeverity
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

/**
 * Handlebars context for the Balance & Pacing Alerts sheet section (roadmap #13),
 * built from the pure [PacingAlertView] (which the tests cover). Localizes the
 * alert message + severity label here; the template reads the fields directly.
 */

@JsPlainObject
external interface PacingAlertItemContext {
    val id: String
    val type: String
    val severity: String
    val severityLabel: String
    val message: String
    val turnCreated: Int
    val isCritical: Boolean
}

@JsPlainObject
external interface PacingAlertContext {
    val hasAlerts: Boolean
    val warningCount: Int
    val criticalCount: Int
    val alerts: Array<PacingAlertItemContext>
}

fun buildPacingAlertContext(view: PacingAlertView): PacingAlertContext {
    val alerts = view.alerts.map { item ->
        PacingAlertItemContext(
            id = item.id,
            type = item.type,
            severity = item.severity,
            severityLabel = PacingAlertSeverity.fromString(item.severity)?.let { t(it.i18nKey) } ?: item.severity,
            message = t(item.messageKey),
            turnCreated = item.turnCreated,
            isCritical = item.isCritical,
        )
    }.toTypedArray()
    return PacingAlertContext(
        hasAlerts = view.hasAlerts,
        warningCount = view.warningCount,
        criticalCount = view.criticalCount,
        alerts = alerts,
    )
}

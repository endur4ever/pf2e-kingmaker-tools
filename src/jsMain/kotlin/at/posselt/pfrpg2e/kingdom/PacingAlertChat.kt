package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.PacingAlertSeverity
import at.posselt.pfrpg2e.kingdom.data.RawPacingAlert
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t

/**
 * Post a single balance/pacing advisory ([RawPacingAlert]) to chat (roadmap #13).
 * Shared by every generation site (End Turn stagnation, event-check turn gap, …)
 * so the localization + template wiring lives in one place. The alert's `message`
 * holds an i18n key; the severity label is resolved from [PacingAlertSeverity].
 */
suspend fun postPacingAlertChat(alert: RawPacingAlert) {
    val context = js("{}")
    context.message = t(alert.message)
    context.severity = alert.severity
    context.severityLabel = PacingAlertSeverity.fromString(alert.severity)?.let { t(it.i18nKey) } ?: alert.severity
    context.relatedEntityId = alert.relatedEntityId
    postChatTemplate(
        templatePath = "chatmessages/pacing-alert.hbs",
        templateContext = context,
    )
}

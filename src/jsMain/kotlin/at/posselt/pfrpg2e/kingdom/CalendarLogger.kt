package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.utils.escapeHtml
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.postChatMessage
import js.objects.recordOf
import com.foundryvtt.core.helpers.SimpleCalendarDate
import com.foundryvtt.core.helpers.simpleCalendarOrNull
import kotlinx.coroutines.await

/**
 * Log an expedition's launch to the calendar so the GM has an in-fiction "back on day X" signal.
 * Logged at the current date with a "returns in N days" note (no fragile future-date arithmetic);
 * no-ops gracefully when no Simple Calendar integration is present.
 */
suspend fun logExpeditionLaunched(expedition: RawCompanionExpedition, companions: Array<RawCharacter>) {
    val nameByKey = companions.associateBy({ it.actorUuid ?: it.name }, { it.name })
    val names = expedition.companionIds.mapNotNull { nameByKey[it] }.joinToString(", ").ifBlank { "Companions" }
    logToCalendar(
        title = "Expedition: ${escapeHtml(expedition.title)}",
        content = "${escapeHtml(names)} set out; expected back in ${expedition.totalDays} days.",
    )

    // Sendoff chat beat
    val escapeTitle = escapeHtml(expedition.title)
    val escapeNames = escapeHtml(names)
    val sendoffMessage = t(
        "kingdom.expeditions.sendoff",
        recordOf("names" to escapeNames, "title" to escapeTitle, "days" to expedition.totalDays.toString())
    )
    if (expedition.visibleToPlayers) {
        postChatMessage(sendoffMessage)
    } else {
        val gmUserIds = com.foundryvtt.core.game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
        postChatMessage(sendoffMessage, whisper = gmUserIds)
    }
}

suspend fun logToCalendar(title: String, content: String, date: SimpleCalendarDate? = null) {
    // Gracefully no-op when no Simple Calendar integration is present (Seasons & Stars without the
    // compat bridge, or no calendar module). Touching the bare global would throw a ReferenceError.
    val api = simpleCalendarOrNull()?.api ?: return
    val targetDate = date ?: api.getCurrentDate()
    try {
        api.addNote(
            title = title,
            content = content,
            startDate = targetDate,
            endDate = targetDate,
            allDay = true,
            repeats = 0,
            categories = arrayOf("Kingmaker")
        ).await()
    } catch (e: Exception) {
        console.error("Failed to add calendar note: ", e)
    }
}

package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.clearDepartingCompanionsFromCamp
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.utils.escapeHtml
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.postChatMessage
import js.objects.recordOf
import com.foundryvtt.core.helpers.SimpleCalendarDate
import com.foundryvtt.core.helpers.simpleCalendarOrNull
import com.foundryvtt.core.game
import kotlinx.coroutines.await

/**
 * Log an expedition's launch to the calendar so the GM has an in-fiction "back on day X" signal.
 * Logged at the current date with a "returns in N days" note (no fragile future-date arithmetic);
 * no-ops gracefully when no Simple Calendar integration is present.
 *
 * Also performs departure housekeeping: it is the one seam every launch path (KingdomSheet,
 * both ChatButtons offers, CompanionProfileDialog) already calls after persisting the kingdom, so
 * the departing companions' camp assignments are cleared here (see
 * [at.posselt.pfrpg2e.camping.clearDepartingCompanionsFromCamp]).
 */
suspend fun logExpeditionLaunched(expedition: RawCompanionExpedition, companions: Array<RawCharacter>) {
    val nameByKey = companions.associateBy({ it.actorUuid ?: it.name }, { it.name })
    val names = expedition.companionIds.mapNotNull { nameByKey[it] }.joinToString(", ")
        .ifBlank { t("kingdom.expeditions.companionsFallback") }
    val days = expedition.totalDays

    // Departing companions keep no stale camp assignments behind (activities, watches, meals).
    val departing = expedition.companionIds.mapNotNull { cid ->
        companions.find { (it.actorUuid ?: it.name) == cid }
            ?.let { c -> c.actorUuid?.let { uuid -> uuid to c.name } }
    }
    clearDepartingCompanionsFromCamp(game, departing)

    // The calendar note is rendered as HTML by Simple Calendar — escape there only.
    logToCalendar(
        title = "Expedition: ${escapeHtml(expedition.title)}",
        content = "${escapeHtml(names)} set out; expected back in $days days.",
    )

    // Sendoff chat beat: raw values — postChatMessage escapes the final message exactly
    // once (pre-escaping double-escaped '&' in names). Public when visibleToPlayers.
    val sendoffMessage = t(
        "kingdom.expeditions.sendoff",
        recordOf("names" to names, "title" to expedition.title, "days" to days),
    )
    if (expedition.visibleToPlayers) {
        postChatMessage(sendoffMessage)
    } else {
        val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
        if (gmUserIds.isNotEmpty()) {
            postChatMessage(sendoffMessage, whisper = gmUserIds)
        }
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

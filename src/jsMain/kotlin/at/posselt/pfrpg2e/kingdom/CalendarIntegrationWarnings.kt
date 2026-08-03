package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.evaluateCalendarNoteSupport
import at.posselt.pfrpg2e.settings.Pfrpg2eKingdomCampingWeatherSettings
import at.posselt.pfrpg2e.shouldWarnAboutMissingCalendarBridge
import at.posselt.pfrpg2e.utils.isFirstGM
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.helpers.simpleCalendarOrNull
import js.objects.recordOf

/**
 * Warns the GM about calendar-integration misconfigurations that would otherwise fail silently.
 *
 * Background: our calendar notes ([logToCalendar]) and the rest-time world-clock advance both depend
 * on a working calendar integration. [logToCalendar] talks to the `SimpleCalendar` global — provided
 * by the Simple Calendar module or, for Seasons & Stars, by the separate *Simple Calendar
 * Compatibility Bridge*. Seasons & Stars users who never install that bridge get no notes and no hint
 * why; misconfigured S&S calendars also make the rest time-advance reject console-only. These helpers
 * surface both as one-time, GM-only chat warnings (deduped via a persisted world setting).
 *
 * ## Native Seasons & Stars notes path (investigated, deliberately not taken)
 * The card asked whether S&S exposes a notes API usable directly from [logToCalendar] as a preferred
 * path. Findings: the *documented, stable* integration surface both Simple Calendar and Seasons &
 * Stars publish for third-party modules IS the `SimpleCalendar` global — that is exactly what the
 * Compatibility Bridge exists to provide. S&S's own note storage lives behind its calendar manager
 * internals, which are not a documented public "add a dated note" API equivalent to
 * `SimpleCalendarApi.addNote`. Per the card's guidance ("if it requires patching internals, DON'T"),
 * we ship detection-only and steer the GM to the bridge rather than reaching into S&S internals that
 * a future S&S release could rename or remove. If S&S later publishes a first-class notes API, wire
 * it into [logToCalendar] as the preferred path with the bridge as fallback.
 */

private const val SEASONS_AND_STARS_MODULE_ID = "seasons-and-stars"
private const val COMPAT_BRIDGE_MODULE_ID = "foundryvtt-simple-calendar-compat"

/** Keys for the one-time-warning dedup set. */
private const val WARNING_MISSING_BRIDGE = "missing-compat-bridge"
private const val WARNING_TIME_ADVANCE_FAILED = "time-advance-failed"

private fun Game.isModuleActive(id: String): Boolean =
    modules.get(id)?.active == true

private fun dismissedWarnings(): Set<String> =
    Pfrpg2eKingdomCampingWeatherSettings.getDismissedCalendarWarnings()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

private suspend fun markWarningShown(key: String) {
    val updated = (dismissedWarnings() + key).joinToString(",")
    Pfrpg2eKingdomCampingWeatherSettings.setDismissedCalendarWarnings(updated)
}

/**
 * Posts [html] as a GM-whispered chat message exactly once per world for the given [key], recording
 * the key so it never re-fires. Only the acting (first) GM posts — that client both has write access
 * to the world setting and avoids duplicate posts from other connected GM clients.
 */
private suspend fun Game.postOneTimeGmWarning(key: String, html: String) {
    if (!isFirstGM()) return
    if (key in dismissedWarnings()) return
    val gmUserIds = users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    postChatMessage(html, isHtml = true, whisper = gmUserIds)
    markWarningShown(key)
}

/**
 * On ready: if Seasons & Stars is active but the Simple Calendar Compatibility Bridge is missing,
 * warn the GM once that calendar notes are being silently skipped and how to fix it. No-ops when a
 * calendar integration is present or when no calendar module is installed at all.
 */
suspend fun Game.warnIfCalendarNotesUnsupported() {
    val support = evaluateCalendarNoteSupport(
        seasonsStarsActive = isModuleActive(SEASONS_AND_STARS_MODULE_ID),
        simpleCalendarPresent = simpleCalendarOrNull() != null,
    )
    val alreadyWarned = WARNING_MISSING_BRIDGE in dismissedWarnings()
    if (!shouldWarnAboutMissingCalendarBridge(support, alreadyWarned)) return
    val message = t(
        "chatMessages.calendarBridgeMissing",
        recordOf(
            "packageId" to COMPAT_BRIDGE_MODULE_ID,
            "url" to "https://foundryvtt.com/packages/$COMPAT_BRIDGE_MODULE_ID",
        ),
    )
    postOneTimeGmWarning(WARNING_MISSING_BRIDGE, message)
}

/**
 * Fire-and-forget: elevate a rejected rest-time `game.time.advance(...)` from console-only to a
 * one-time GM chat warning. The usual cause is a misconfigured Seasons & Stars calendar (the
 * "Calendar not found" fallback), which otherwise leaves the GM with no feedback that the world clock
 * never moved.
 */
suspend fun Game.warnCalendarTimeAdvanceFailed() {
    postOneTimeGmWarning(WARNING_TIME_ADVANCE_FAILED, t("chatMessages.calendarTimeAdvanceFailed"))
}

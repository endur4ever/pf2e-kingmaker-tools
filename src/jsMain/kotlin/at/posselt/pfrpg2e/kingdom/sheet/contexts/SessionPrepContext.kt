package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.SessionPrepEntry
import at.posselt.pfrpg2e.kingdom.SessionPrepView
import at.posselt.pfrpg2e.kingdom.TurnRecentEntry
import kotlinx.js.JsPlainObject

/**
 * Handlebars context for the Session Prep & Recap Dashboard sheet section (roadmap #10),
 * built from the pure [SessionPrepView] (which the tests cover). This layer only reshapes
 * the view into a JS-plain object the template can read directly; all localization lives in
 * the template via `localizeKM`, so this stays free of i18n side effects.
 */

@JsPlainObject
external interface SessionPrepEntryContext {
    val id: String
    val name: String
    val detail: String
    val turnsRemaining: Int?
    val hasTurns: Boolean
    val hasDetail: Boolean
}

@JsPlainObject
external interface TurnRecentEntryContext {
    val turn: Int
    val timestamp: String
    val fame: Int
    val resourcePoints: Int
    val consumption: Int
    val unrest: Int
    val xpAwarded: Int?
    val hasXpAwarded: Boolean
    val clockEvents: Array<String>?
    val hasClockEvents: Boolean
    val warPressure: Int?
    val hasWarPressure: Boolean
    val notes: String?
    val hasNotes: Boolean
}

@JsPlainObject
external interface SessionPrepContext {
    val openQuests: Array<SessionPrepEntryContext>
    val activeClocks: Array<SessionPrepEntryContext>
    val unresolvedEvents: Array<SessionPrepEntryContext>
    val hexHooks: Array<SessionPrepEntryContext>
    val companionMoments: Array<SessionPrepEntryContext>
    val recentTurns: Array<TurnRecentEntryContext>
    val isGM: Boolean
    val hasAnything: Boolean
    val totalCount: Int
}

private fun List<SessionPrepEntry>.toContexts(): Array<SessionPrepEntryContext> =
    map { entry ->
        SessionPrepEntryContext(
            id = entry.id,
            name = entry.name,
            detail = entry.detail,
            turnsRemaining = entry.turnsRemaining,
            hasTurns = entry.turnsRemaining != null,
            hasDetail = entry.detail.isNotBlank(),
        )
    }.toTypedArray()

private fun List<TurnRecentEntry>.toTurnContexts(): Array<TurnRecentEntryContext> =
    map { entry ->
        TurnRecentEntryContext(
            turn = entry.turn,
            timestamp = entry.timestamp,
            fame = entry.fame,
            resourcePoints = entry.resourcePoints,
            consumption = entry.consumption,
            unrest = entry.unrest,
            xpAwarded = entry.xpAwarded,
            hasXpAwarded = entry.xpAwarded != null,
            clockEvents = entry.clockEvents,
            hasClockEvents = !entry.clockEvents.isNullOrEmpty(),
            warPressure = entry.warPressure,
            hasWarPressure = entry.warPressure != null,
            notes = entry.notes,
            hasNotes = !entry.notes.isNullOrBlank(),
        )
    }.toTypedArray()

fun buildSessionPrepContext(view: SessionPrepView): SessionPrepContext =
    SessionPrepContext(
        openQuests = view.openQuests.toContexts(),
        activeClocks = view.activeClocks.toContexts(),
        unresolvedEvents = view.unresolvedEvents.toContexts(),
        hexHooks = view.hexHooks.toContexts(),
        companionMoments = view.companionMoments.toContexts(),
        recentTurns = view.recentTurns.toTurnContexts(),
        isGM = view.isGM,
        hasAnything = view.hasAnything,
        totalCount = view.totalCount,
    )

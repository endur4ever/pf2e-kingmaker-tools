package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.ClosedVoteLine
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
    /** Pending-encounter rows: true once creatures are curated; false still opens the dialog. */
    val canStage: Boolean
}

@JsPlainObject
external interface ClosedVoteLineContext {
    val question: String
    val winnerLabel: String?
    val winnerCount: Int
    val totalBallots: Int
    val isTie: Boolean
    /** False for a tie AND for a vote nobody answered -- both are failures to decide, and the
     *  template must not print an empty winner slot for either. */
    val hasWinner: Boolean
    val hasLinks: Boolean
    /** Pre-joined "12, 15": Handlebars cannot join, and the recap line interpolates one string. */
    val linkedTurnsCsv: String
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
    val level: Int?
    val size: Int?
    val ruinCorruption: Int?
    val ruinCrime: Int?
    val ruinDecay: Int?
    val ruinStrife: Int?
    val closedVotes: Array<ClosedVoteLineContext>
    val hasClosedVotes: Boolean
    /** True for the one turn a vote's consequence chip jumped to; drives the flash highlight. */
    val isHighlighted: Boolean
}

@JsPlainObject
external interface SessionPrepContext {
    val openQuests: Array<SessionPrepEntryContext>
    val activeClocks: Array<SessionPrepEntryContext>
    val unresolvedEvents: Array<SessionPrepEntryContext>
    val hexHooks: Array<SessionPrepEntryContext>
    val companionMoments: Array<SessionPrepEntryContext>
    val companionExpeditions: Array<SessionPrepEntryContext>
    val recentTurns: Array<TurnRecentEntryContext>
    val pendingEncounters: Array<SessionPrepEntryContext>
    val isGM: Boolean
    val hasAnything: Boolean
    val totalCount: Int

    /** Null for players AND when the forecast could not be built — absence removes the panel. */
    val forecast: ForecastPanelContext?
}

private fun List<SessionPrepEntry>.toContexts(): Array<SessionPrepEntryContext> =
    map { entry ->
        SessionPrepEntryContext(
            id = entry.id,
            name = entry.name,
            detail = entry.detail,
            turnsRemaining = entry.turnsRemaining,
            hasTurns = entry.turnsRemaining != null,
            canStage = entry.canStage,
            hasDetail = entry.detail.isNotBlank(),
        )
    }.toTypedArray()

private fun List<TurnRecentEntry>.toTurnContexts(highlightTurn: Int?): Array<TurnRecentEntryContext> =
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
            level = entry.level,
            size = entry.size,
            ruinCorruption = entry.ruinCorruption,
            ruinCrime = entry.ruinCrime,
            ruinDecay = entry.ruinDecay,
            ruinStrife = entry.ruinStrife,
            isHighlighted = highlightTurn != null && entry.turn == highlightTurn,
            hasClosedVotes = entry.closedVotes.isNotEmpty(),
            closedVotes = entry.closedVotes.map { line ->
                ClosedVoteLineContext(
                    question = line.question,
                    winnerLabel = line.winnerLabel,
                    winnerCount = line.winnerCount,
                    totalBallots = line.totalBallots,
                    isTie = line.isTie,
                    hasWinner = line.winnerLabel != null,
                    hasLinks = line.linkedTurns.isNotEmpty(),
                    linkedTurnsCsv = line.linkedTurns.joinToString(", "),
                )
            }.toTypedArray(),
        )
    }.toTypedArray()

fun buildSessionPrepContext(
    view: SessionPrepView,
    forecast: ForecastPanelContext? = null,
    highlightTurn: Int? = null,
): SessionPrepContext =
    SessionPrepContext(
        forecast = forecast,
        openQuests = view.openQuests.toContexts(),
        activeClocks = view.activeClocks.toContexts(),
        unresolvedEvents = view.unresolvedEvents.toContexts(),
        hexHooks = view.hexHooks.toContexts(),
        companionMoments = view.companionMoments.toContexts(),
        companionExpeditions = view.companionExpeditions.toContexts(),
        recentTurns = view.recentTurns.toTurnContexts(highlightTurn),
        pendingEncounters = view.pendingEncounters.toContexts(),
        isGM = view.isGM,
        hasAnything = view.hasAnything,
        totalCount = view.totalCount,
    )

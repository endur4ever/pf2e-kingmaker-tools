package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.campaign.CampaignClock
import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.data.RawQuest
import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord

/**
 * Session Prep & Recap Dashboard (roadmap #10).
 *
 * [buildSessionPrepView] aggregates existing, read-only kingdom data into clean,
 * template-ready GM-prep lists: open quests, active campaign clocks, unresolved
 * kingdom events, hex-content hooks, and active companion moments. Pure +
 * unit-testable; the sheet section context
 * ([at.posselt.pfrpg2e.kingdom.sheet.contexts.buildSessionPrepContext]) wraps the
 * result for Handlebars and the template renders the structured lists. Narrative
 * prose generation is a deliberate future additive layer (design Decision 5), so
 * this stage only aggregates structured data and never writes anything.
 *
 * When [isGM] is false the view is filtered to player-safe content only: GM-facing
 * deadlines (campaign clocks), unresolved events, and hidden hex content are
 * withheld, and companion moments collapse to the ones flagged visible to players.
 */

/** One row in a session-prep section. [detail] is plain human data (giver, hex key, type). */
data class SessionPrepEntry(
    val id: String,
    val name: String,
    val detail: String = "",
    /** When non-null, the template renders "N turns remaining". */
    val turnsRemaining: Int? = null,
)

data class TurnRecentEntry(
    val turn: Int,
    val timestamp: String,
    val fame: Int,
    val resourcePoints: Int,
    val consumption: Int,
    val unrest: Int,
    val xpAwarded: Int?,
    val clockEvents: Array<String>?,
    val warPressure: Int?,
    val notes: String?,
)

data class SessionPrepView(
    val openQuests: List<SessionPrepEntry>,
    val activeClocks: List<SessionPrepEntry>,
    val unresolvedEvents: List<SessionPrepEntry>,
    val hexHooks: List<SessionPrepEntry>,
    val companionMoments: List<SessionPrepEntry>,
    val recentTurns: List<TurnRecentEntry>,
    val isGM: Boolean,
) {
    val totalCount: Int
        get() = openQuests.size + activeClocks.size + unresolvedEvents.size +
            hexHooks.size + companionMoments.size + recentTurns.size

    val hasAnything: Boolean
        get() = totalCount > 0
}

private const val STATUS_COMPLETED = "completed"
private const val STATUS_ACTIVE = "active"

private fun buildOpenQuests(quests: Array<RawQuest>?): List<SessionPrepEntry> =
    (quests ?: emptyArray())
        .filter { it.status != STATUS_COMPLETED }
        .map { quest ->
            SessionPrepEntry(
                id = quest.id,
                name = quest.title,
                detail = quest.giver,
            )
        }

private fun buildActiveClocks(clocks: Array<CampaignClock>): List<SessionPrepEntry> =
    clocks
        .filter { it.active && !it.expired }
        .map { clock ->
            SessionPrepEntry(
                id = clock.id,
                name = clock.label,
                turnsRemaining = clock.turnsRemaining,
            )
        }

private fun buildUnresolvedEvents(events: Array<dynamic>?): List<SessionPrepEntry> =
    // Elements are already `dynamic` (persisted CampaignKingdomEvent shapes), so read
    // their fields directly — calling `.asDynamic()` on a dynamic value emits a real
    // (nonexistent) method call at runtime.
    (events ?: emptyArray())
        .filter { (it.status as? String) == STATUS_ACTIVE }
        .map { event ->
            val name = (event.name as? String)
                ?: (event.title as? String)
                ?: ""
            SessionPrepEntry(
                id = (event.id as? String) ?: "",
                name = name,
            )
        }

private fun buildHexHooks(hexContents: Array<RawHexContent>?, isGM: Boolean): List<SessionPrepEntry> =
    (hexContents ?: emptyArray())
        .filter { isGM || HexContentVisibility.fromString(it.visibility) != HexContentVisibility.HIDDEN }
        .map { hex ->
            SessionPrepEntry(
                id = hex.id,
                name = hex.name,
                detail = hex.hexKey,
            )
        }

private fun buildCompanionMoments(
    companionQuests: Array<CompanionPersonalQuest>?,
    isGM: Boolean,
): List<SessionPrepEntry> =
    (companionQuests ?: emptyArray())
        .filter { it.status == STATUS_ACTIVE }
        .filter { isGM || it.visibleToPlayers }
        .map { quest ->
            SessionPrepEntry(
                id = quest.id,
                name = quest.title,
                detail = quest.companionId,
                turnsRemaining = quest.turnsRemaining,
            )
        }

fun buildSessionPrepView(
    quests: Array<RawQuest>?,
    clocks: Array<CampaignClock>,
    events: Array<dynamic>?,
    hexContents: Array<RawHexContent>?,
    companionQuests: Array<CompanionPersonalQuest>?,
    isGM: Boolean,
    turnHistory: Array<RawTurnRecord>? = null,
): SessionPrepView = SessionPrepView(
    openQuests = buildOpenQuests(quests),
    // Campaign clocks + unresolved events are GM-facing prep; withheld from players.
    activeClocks = if (isGM) buildActiveClocks(clocks) else emptyList(),
    unresolvedEvents = if (isGM) buildUnresolvedEvents(events) else emptyList(),
    hexHooks = buildHexHooks(hexContents, isGM),
    companionMoments = buildCompanionMoments(companionQuests, isGM),
    // Recent turns are GM-only, like activeClocks.
    recentTurns = if (isGM) buildRecentTurns(turnHistory) else emptyList(),
    isGM = isGM,
)

private fun buildRecentTurns(turnHistory: Array<RawTurnRecord>?): List<TurnRecentEntry> =
    (turnHistory ?: emptyArray())
        .takeLast(10)
        .reversed()
        .map { record ->
            TurnRecentEntry(
                turn = record.turn,
                timestamp = record.timestamp,
                fame = record.fame,
                resourcePoints = record.resourcePoints,
                consumption = record.consumption,
                unrest = record.unrest,
                xpAwarded = record.xpAwarded,
                clockEvents = record.clockEvents,
                warPressure = record.warPressure,
                notes = record.notes,
            )
        }

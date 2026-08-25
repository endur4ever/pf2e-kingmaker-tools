package at.posselt.pfrpg2e.kingdom.pings

/**
 * Pure whisper-card composition for Player Pings
 * (`docs/plans/2026-07-09-plan-player-pings.md` SS3.1, SS5.2).
 *
 * The jsMain adapter derives lines from real sources and resolves per-user ownership; this layer
 * only decides WHICH lines a user's card carries, in what order, and whether a card exists at
 * all. Prose stays template-based: a line carries an i18n key + args, never generated text.
 */

/** SS5.2's enumerated line kinds. Kinds whose data source is absent are simply never emitted. */
enum class PingLineKind(val value: String) {
    LEADER_CHECK_PENDING("leaderCheckPending"),
    LEADERSHIP_SLOTS("leadershipSlots"),
    EXPEDITION_RETURNING("expeditionReturning"),
    QUEST_DUE("questDue"),
    PETITION_DUE("petitionDue"),
}

data class WhisperLine(
    val kind: PingLineKind,
    val labelKey: String,
    val labelArgs: Map<String, String> = emptyMap(),
    /** "sheet-tab" for v1; the value is a MainNavEntry value carried opaquely. */
    val jumpKind: String? = null,
    val jumpValue: String? = null,
)

data class WhisperCard(
    val turn: Int,
    val lines: List<WhisperLine>,
    val alreadyReady: Boolean,
)

data class WhisperInputs(
    val turn: Int,
    /** Role ids this user owns (empty for spectators). Resolved per TARGET user, never isOwner. */
    val ownedRoles: Set<String>,
    /** Owned roles with an unresolved required check. No data source yet -- adapter passes empty. */
    val pendingCheckRoles: List<String> = emptyList(),
    /** Kingdom-wide leadership activities left (SS3.3 -- a shared, honest number). Null = unknown. */
    val leadershipSlotsRemaining: Int? = null,
    val expeditionLines: List<WhisperLine> = emptyList(),
    val questLines: List<WhisperLine> = emptyList(),
    val petitionLines: List<WhisperLine> = emptyList(),
    /** The turn this user marked themselves ready for (from their own flag). */
    val readyForTurn: Int? = null,
)

/** SS5.2: hard cap so fan-out sources can never produce a wall-of-text card. */
const val WHISPER_LINE_CAP = 8

const val PINGS_KEY_LEADER_CHECK = "kingdom.pings.card.leaderCheck"
const val PINGS_KEY_SLOTS = "kingdom.pings.card.slotsRemaining"

/**
 * Null when there is nothing to say -- a spectator or an all-quiet turn gets NO card, never an
 * empty one. Order is priority order (personal first), and the cap truncates the tail:
 * leader-check > slots > expeditions > quests > petitions.
 *
 * The slots line needs BOTH an owned role and remaining > 0: the number is kingdom-wide (SS3.3),
 * so it is only relevant to users who can actually spend a slot, and "0 remaining" is noise, not
 * a nudge. [WhisperCard.alreadyReady] is a strict turn match -- readiness for any other turn says
 * nothing about this one (self-expiring, no cleanup).
 */
fun composeWhisper(inputs: WhisperInputs): WhisperCard? {
    val lines = buildList {
        inputs.pendingCheckRoles
            .filter { it in inputs.ownedRoles }
            .forEach { role ->
                add(
                    WhisperLine(
                        kind = PingLineKind.LEADER_CHECK_PENDING,
                        labelKey = PINGS_KEY_LEADER_CHECK,
                        labelArgs = mapOf("role" to role),
                        jumpKind = "sheet-tab",
                        jumpValue = "turn",
                    )
                )
            }
        val remaining = inputs.leadershipSlotsRemaining
        if (inputs.ownedRoles.isNotEmpty() && remaining != null && remaining > 0) {
            add(
                WhisperLine(
                    kind = PingLineKind.LEADERSHIP_SLOTS,
                    labelKey = PINGS_KEY_SLOTS,
                    labelArgs = mapOf("remaining" to remaining.toString()),
                    jumpKind = "sheet-tab",
                    jumpValue = "turn",
                )
            )
        }
        addAll(inputs.expeditionLines)
        addAll(inputs.questLines)
        addAll(inputs.petitionLines)
    }.take(WHISPER_LINE_CAP)
    if (lines.isEmpty()) return null
    return WhisperCard(
        turn = inputs.turn,
        lines = lines,
        alreadyReady = inputs.readyForTurn == inputs.turn,
    )
}

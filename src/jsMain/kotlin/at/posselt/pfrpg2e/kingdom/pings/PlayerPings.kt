package at.posselt.pfrpg2e.kingdom.pings

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.kingdom.ActivityCapCalculator
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getPerformedActivities
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.pressure.DAYS_PER_MONTH
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.setAppFlag
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.fromUuidOfTypes
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import com.foundryvtt.core.documents.User
import com.foundryvtt.pf2e.actor.PF2ECharacter
import com.foundryvtt.pf2e.actor.PF2ENpc
import js.objects.recordOf
import kotlinx.js.JsPlainObject
import org.w3c.dom.get

/**
 * Turn-open whisper cards (`docs/plans/2026-07-09-plan-player-pings.md` SS3.2, SS3.4, SS4.1).
 *
 * Composed on the GM's client and whispered per player, so ownership must be resolved per TARGET
 * user from the actors' ownership records -- NEVER via `isOwner`, which is relative to the local
 * (GM) client and would ship the GM's answer to everyone (the war-threat baked-isGM lesson).
 */

private const val OWNERSHIP_OWNER = 3

private fun ownershipLevelFor(ownership: AnyObject, userId: String): Int =
    (ownership[userId] as? Int) ?: (ownership["default"] as? Int) ?: 0

/** Role ids (Leader enum values) whose leader actor this user owns. Missing actors contribute nothing. */
suspend fun ownedRolesFor(user: User, kingdom: KingdomData): Set<String> {
    val userId = user.id ?: return emptySet()
    val leaders = kingdom.leaders
    val roles = listOf(
        Leader.RULER to leaders.ruler, Leader.COUNSELOR to leaders.counselor,
        Leader.EMISSARY to leaders.emissary, Leader.GENERAL to leaders.general,
        Leader.MAGISTER to leaders.magister, Leader.TREASURER to leaders.treasurer,
        Leader.VICEROY to leaders.viceroy, Leader.WARDEN to leaders.warden,
    )
    return roles.mapNotNull { (role, leaderValue) ->
        val uuid = leaderValue.uuid ?: return@mapNotNull null
        val actor = fromUuidOfTypes(uuid, PF2ECharacter::class, PF2ENpc::class)
            ?: return@mapNotNull null
        val ownership = actor.asDynamic().ownership.unsafeCast<AnyObject?>() ?: return@mapNotNull null
        if (ownershipLevelFor(ownership, userId) >= OWNERSHIP_OWNER) role.value else null
    }.toSet()
}

const val PINGS_KEY_EXPEDITION_RETURNING = "kingdom.pings.card.expeditionReturning"
const val PINGS_KEY_EXPEDITION_AWAITING = "kingdom.pings.card.expeditionAwaiting"

/**
 * Expedition lines are kingdom-wide but PLAYER-SAFE: only `visibleToPlayers` rows, reusing the
 * expeditions surface's own filter (SS5.3). "Returning this turn" = in progress with at most one
 * month of days left (expeditions tick daily; turns are monthly). Quest/petition lines are
 * existence-gated OUT of v1: RawQuest has no deadline field and the petition inbox is unbuilt.
 */
fun expeditionWhisperLines(expeditions: Array<RawCompanionExpedition>?): List<WhisperLine> =
    (expeditions ?: emptyArray())
        .filter { it.visibleToPlayers }
        .mapNotNull { exp ->
            when {
                exp.status == "awaitingResolution" -> WhisperLine(
                    kind = PingLineKind.EXPEDITION_RETURNING,
                    labelKey = PINGS_KEY_EXPEDITION_AWAITING,
                    labelArgs = mapOf("title" to exp.title),
                    jumpKind = "sheet-tab",
                    jumpValue = "expeditions",
                )
                exp.status == "inProgress" && exp.daysRemaining <= DAYS_PER_MONTH -> WhisperLine(
                    kind = PingLineKind.EXPEDITION_RETURNING,
                    labelKey = PINGS_KEY_EXPEDITION_RETURNING,
                    labelArgs = mapOf("title" to exp.title, "days" to exp.daysRemaining.toString()),
                    jumpKind = "sheet-tab",
                    jumpValue = "expeditions",
                )
                else -> null
            }
        }

/** Kingdom-wide leadership activities left this turn (SS3.3 -- honest shared number). */
fun leadershipSlotsRemaining(game: Game, actor: KingdomActor, kingdom: KingdomData): Int? {
    var leadershipCap = 2
    var leadershipCapWithTownhall = 3
    try {
        val settings = game.settings.pfrpg2eKingdomCampingWeather
        leadershipCap = settings.getLeadershipActivityCap()
        leadershipCapWithTownhall = settings.getLeadershipActivityCapWithTownhall()
    } catch (e: Throwable) {
        // unit-test environments have no settings registry; RAW defaults stand
    }
    val caps = ActivityCapCalculator.calculate(
        kingdom,
        actor.getPerformedActivities(),
        leadershipCap = leadershipCap,
        leadershipCapWithTownhall = leadershipCapWithTownhall,
    )
    val leadership = caps.caps.firstOrNull { it.phase == "leadership" } ?: return null
    return (leadership.maximum - leadership.current).coerceAtLeast(0)
}

@Suppress("unused")
@JsPlainObject
external interface PlayerPingLineContext {
    val text: String
    val jumpValue: String?
    val jumpLabel: String?
}

@Suppress("unused")
@JsPlainObject
external interface PlayerPingsCardContext {
    val title: String
    val actorUuid: String
    val turn: Int
    val lines: Array<PlayerPingLineContext>
    val readyLabel: String
    val alreadyReady: Boolean
}

fun whisperCardContext(card: WhisperCard, actorUuid: String): PlayerPingsCardContext =
    PlayerPingsCardContext(
        title = t("kingdom.pings.card.title", recordOf("turn" to card.turn.toString())),
        actorUuid = actorUuid,
        turn = card.turn,
        lines = card.lines.map { line ->
            val args = if (line.kind == PingLineKind.LEADER_CHECK_PENDING) {
                // the pure core carries the role id; the UI shows the localized role name
                val localizedRole = line.labelArgs["role"]
                    ?.let { Leader.fromString(it) }
                    ?.let { t(it) }
                    ?: line.labelArgs["role"].orEmpty()
                line.labelArgs + ("role" to localizedRole)
            } else {
                line.labelArgs
            }
            PlayerPingLineContext(
                text = t(line.labelKey, recordOf(*args.map { (k, v) -> k to v }.toTypedArray())),
                jumpValue = line.jumpValue.takeIf { line.jumpKind == "sheet-tab" },
                jumpLabel = line.jumpValue?.let { t("kingdom.pings.card.view") },
            )
        }.toTypedArray(),
        readyLabel = if (card.alreadyReady) t("kingdom.pings.card.readyDone") else t("kingdom.pings.card.ready"),
        alreadyReady = card.alreadyReady,
    )

/**
 * Fires at TURN OPEN, the same seam as postLastTurnRecap, idempotent per turn via the
 * "lastPlayerPingsTurn" app flag (the exact lastRecapTurn pattern). GM-composed; each PLAYER user
 * gets at most one private card; users with nothing to hear (composeWhisper == null) get none.
 * The GM is never whispered -- postLastTurnRecap already serves the GM.
 */
suspend fun postPlayerPings(game: Game, actor: KingdomActor) {
    if (!game.user.isGM) return
    val kingdom = actor.getKingdom() ?: return
    val turn = kingdom.currentTurn ?: 0
    if (actor.getAppFlag<KingdomActor, Int?>("lastPlayerPingsTurn") == turn) return
    val expeditionLines = expeditionWhisperLines(kingdom.companionExpeditions)
    val slotsRemaining = leadershipSlotsRemaining(game, actor, kingdom)
    for (user in game.users.filter { !it.isGM }) {
        val userId = user.id ?: continue
        val card = composeWhisper(
            WhisperInputs(
                turn = turn,
                ownedRoles = ownedRolesFor(user, kingdom),
                leadershipSlotsRemaining = slotsRemaining,
                expeditionLines = expeditionLines,
                readyForTurn = user.pingsReadyForTurn(),
            )
        ) ?: continue
        postChatTemplate(
            templatePath = "chatmessages/player-pings.hbs",
            templateContext = whisperCardContext(card, actor.uuid),
            whisper = arrayOf(userId),
        )
    }
    actor.setAppFlag("lastPlayerPingsTurn", turn)
}

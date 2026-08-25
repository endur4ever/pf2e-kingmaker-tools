package at.posselt.pfrpg2e.kingdom.pings

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.RawShipmentHistoryEntry
import at.posselt.pfrpg2e.kingdom.data.RawExpeditionChronicleEntry
import at.posselt.pfrpg2e.kingdom.data.RawQuest
import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.setAppFlag
import com.foundryvtt.core.documents.User
import kotlinx.js.JsPlainObject
import kotlin.js.Date

/**
 * jsMain adapter for the player pings feed (`docs/plans/2026-07-09-plan-player-pings.md`, phase 2).
 *
 * Every source read here is player-safe BY CONSTRUCTION, which is what lets each item carry
 * playerSafe = true: turn records contribute only [RawTurnRecord.playerNotes] — the gazette that
 * already strips GM-only segments — and never [RawTurnRecord.notes]; shipment history, the
 * expedition chronicle and completed quests are all player-visible boards. The pure pipeline's
 * playerSafe filter stays the enforcement seam regardless, so a future source wired wrongly is
 * dropped rather than leaked.
 */
const val PINGS_KEY_TURN_GAZETTE = "kingdom.pings.turnGazette"
const val PINGS_KEY_CARAVAN_RAIDED = "kingdom.pings.caravanRaided"
const val PINGS_KEY_CARAVAN_DELIVERED = "kingdom.pings.caravanDelivered"
const val PINGS_KEY_CARAVAN_RECALLED = "kingdom.pings.caravanRecalled"
const val PINGS_KEY_EXPEDITION_RESOLVED = "kingdom.pings.expeditionResolved"
const val PINGS_KEY_QUEST_COMPLETED = "kingdom.pings.questCompleted"

private fun parseIsoMillis(iso: String?): Double? =
    iso?.let { Date.parse(it).takeIf { millis -> !millis.isNaN() } }

/** Turn-number -> that turn record's wall-clock stamp, the shared clock for turn-granular rows. */
fun turnTimestamps(history: Array<RawTurnRecord>?): Map<Int, Double> =
    history.orEmpty().mapNotNull { record ->
        parseIsoMillis(record.timestamp)?.let { record.turn to it }
    }.toMap()

fun feedFromTurnHistory(history: Array<RawTurnRecord>?): List<FeedItem> =
    history.orEmpty().mapNotNull { record ->
        val notes = record.playerNotes?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val millis = parseIsoMillis(record.timestamp) ?: return@mapNotNull null
        FeedItem(
            id = "turn-${record.turn}",
            occurredAtMillis = millis,
            labelKey = PINGS_KEY_TURN_GAZETTE,
            labelArgs = mapOf("turn" to record.turn.toString(), "notes" to notes),
            target = "sessionPrep",
        )
    }

/**
 * Shipment rows carry a turn, not a wall clock; each maps to its turn record's timestamp so it
 * sorts against everything else. A row whose turn has no record is SKIPPED — inventing a time for
 * it would give it a fake position in the feed.
 */
fun feedFromShipments(
    history: Array<RawShipmentHistoryEntry>?,
    timestamps: Map<Int, Double>,
): List<FeedItem> =
    history.orEmpty().mapIndexedNotNull { index, entry ->
        val millis = timestamps[entry.turn] ?: return@mapIndexedNotNull null
        val labelKey = when (entry.outcome) {
            "raided" -> PINGS_KEY_CARAVAN_RAIDED
            "delivered" -> PINGS_KEY_CARAVAN_DELIVERED
            "recalled" -> PINGS_KEY_CARAVAN_RECALLED
            else -> return@mapIndexedNotNull null
        }
        FeedItem(
            id = "shipment-${entry.turn}-$index",
            occurredAtMillis = millis,
            labelKey = labelKey,
            labelArgs = mapOf("partner" to entry.partner, "cargo" to entry.cargo),
            target = "turn",
        )
    }

fun feedFromChronicle(entries: Array<RawExpeditionChronicleEntry>?): List<FeedItem> =
    entries.orEmpty().mapNotNull { entry ->
        val millis = parseIsoMillis(entry.appliedAt) ?: return@mapNotNull null
        FeedItem(
            id = "expedition-${entry.turn}-${entry.title}",
            occurredAtMillis = millis,
            labelKey = PINGS_KEY_EXPEDITION_RESOLVED,
            labelArgs = mapOf("title" to entry.title, "companions" to entry.companionNames),
            target = "expeditions",
        )
    }

fun feedFromQuests(quests: Array<RawQuest>?): List<FeedItem> =
    quests.orEmpty().mapNotNull { quest ->
        if (quest.status != "completed") return@mapNotNull null
        val millis = quest.updatedAt ?: return@mapNotNull null
        FeedItem(
            id = "quest-${quest.id}",
            occurredAtMillis = millis,
            labelKey = PINGS_KEY_QUEST_COMPLETED,
            labelArgs = mapOf("title" to quest.title),
            target = "quests",
        )
    }

/** The full player-safe feed, unsorted — [unreadFeed] owns ordering and the cursor. */
fun buildPlayerFeed(kingdom: KingdomData): List<FeedItem> {
    val stamps = turnTimestamps(kingdom.turnHistory)
    return feedFromTurnHistory(kingdom.turnHistory) +
        feedFromShipments(kingdom.shipmentHistory, stamps) +
        feedFromChronicle(kingdom.expeditionChronicle) +
        feedFromQuests(kingdom.quests)
}

/**
 * Persisted per-user cursor (`playerPings` flag on the User document — never on kingdom data,
 * which is one shared blob every client sees and any player can write).
 */
@JsPlainObject
external interface RawPingsCursor {
    var lastSeenAtMillis: Double?
    var dismissedIds: Array<String>?
    /** The kingdom turn this user marked themselves Ready for (plan SS2.2); null = not ready. */
    var readyForTurn: Int?
}

fun User.pingsCursor(): SeenCursor {
    val raw = getAppFlag<User, RawPingsCursor?>("playerPings")
    return SeenCursor(
        lastSeenAtMillis = raw?.lastSeenAtMillis,
        dismissedIds = raw?.dismissedIds?.toSet() ?: emptySet(),
    )
}

suspend fun User.savePingsCursor(cursor: SeenCursor) {
    // The readiness field SHARES this flag: read-merge-write so saving the cursor can never
    // silently drop a Ready click (and vice versa in [savePingsReady]).
    val existing = getAppFlag<User, RawPingsCursor?>("playerPings")
    setAppFlag(
        "playerPings",
        RawPingsCursor(
            lastSeenAtMillis = cursor.lastSeenAtMillis,
            dismissedIds = cursor.dismissedIds.toTypedArray(),
            readyForTurn = existing?.readyForTurn,
        ),
    )
}

fun User.pingsReadyForTurn(): Int? =
    getAppFlag<User, RawPingsCursor?>("playerPings")?.readyForTurn

suspend fun User.savePingsReady(turn: Int) {
    val existing = getAppFlag<User, RawPingsCursor?>("playerPings")
    setAppFlag(
        "playerPings",
        RawPingsCursor(
            lastSeenAtMillis = existing?.lastSeenAtMillis,
            dismissedIds = existing?.dismissedIds,
            readyForTurn = turn,
        ),
    )
}

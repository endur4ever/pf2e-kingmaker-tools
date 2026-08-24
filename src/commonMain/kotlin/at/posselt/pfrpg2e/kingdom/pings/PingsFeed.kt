package at.posselt.pfrpg2e.kingdom.pings

/**
 * Pure core of the per-user unread feed and readiness strip of the player-pings plan
 * (`docs/plans/2026-07-09-plan-player-pings.md`, feature b plus the readiness half of feature a).
 *
 * Per-user state -- the last-seen cursor, dismissed ids, readiness -- lives on Foundry User flags
 * (plan §2) and is read and written only by the jsMain layer; this core computes and stores
 * nothing, so the feed is a pure render-time projection: opening the sheet is what refreshes a
 * badge -- no tick, no socket, no push. The runtime records feeding it (RawTurnRecord,
 * RawExpeditionChronicleEntry, RawQuest, shipmentHistory rows) are jsMain-only types, so
 * [FeedItem] is a pure mirror the phase-2 jsMain adapters map into before calling in.
 */

/**
 * One already-timestamped durable happening, mirrored into pure data (the phase-2 jsMain adapters
 * own the mapping from each Raw record type).
 *
 * [occurredAtMillis] is epoch millis, the single cursor axis. Sources stamped only at TURN
 * granularity -- caravan rows come from `kingdom.shipmentHistory`, which carries a turn number and
 * no wall clock -- are mapped by the adapter to the timestamp of the turn record bearing the same
 * turn (plan §2.3), so a whole turn's items legitimately share one instant. [playerSafe] is the
 * privacy enforcement seam: the adapter resolves it from each source's own player-safety filter
 * (never re-derived here), and the derivation drops unsafe rows before any other logic, because
 * players see only their own feed and GM-only data must never leak. [target] names where the
 * item's jump button navigates; the pure core carries it opaquely and never interprets it.
 * [labelKey] and [labelArgs] reference an i18n template -- prose stays template-based, never
 * generated.
 */
data class FeedItem(
    val id: String,
    val occurredAtMillis: Double,
    val labelKey: String,
    val labelArgs: Map<String, String> = emptyMap(),
    val target: String,
    val playerSafe: Boolean = true,
)

/**
 * One user's read state, persisted as a User flag by the jsMain layer (plan §2.2).
 *
 * Both fields default to "never seen anything": User flags have no migration mechanism, so an
 * absent flag must decode to a sensible default, and the sensible default for a first-time reader
 * is the full backlog -- a null [lastSeenAtMillis] means nothing has been seen, not that nothing
 * is unread.
 */
data class SeenCursor(
    val lastSeenAtMillis: Double? = null,
    val dismissedIds: Set<String> = emptySet(),
)

/**
 * Everything unread for one user, newest first.
 *
 * The pipeline runs in this order, and the order is load-bearing:
 * 1. Drop items with `playerSafe == false` FIRST -- the leak guard runs before any other logic so
 *    no later refactor can accidentally reorder it after a step that returns early; whatever else
 *    changes downstream, a GM-only row can never survive into a player's feed.
 * 2. Drop ids in [SeenCursor.dismissedIds] -- a per-item snooze that holds regardless of where
 *    the cursor sits.
 * 3. Keep only items whose [FeedItem.occurredAtMillis] is STRICTLY greater than
 *    [SeenCursor.lastSeenAtMillis]; a null cursor keeps everything, so a first-time reader sees
 *    the full backlog. An item stamped exactly at the cursor is SEEN, not unread: turn-granularity
 *    sources give a whole turn's items one shared timestamp, and marking the sheet seen must
 *    clear the whole turn rather than leave its tail unread forever.
 * 4. Sort by [FeedItem.occurredAtMillis] descending, stable by input order on ties, so the
 *    equal-stamped items of one turn keep the order their sources reported them in -- the same
 *    records always render the same panel.
 */
fun unreadFeed(items: List<FeedItem>, cursor: SeenCursor): List<FeedItem> {
    val lastSeen = cursor.lastSeenAtMillis
    return items
        .filter { it.playerSafe }
        .filterNot { it.id in cursor.dismissedIds }
        .filter { lastSeen == null || it.occurredAtMillis > lastSeen }
        .sortedByDescending { it.occurredAtMillis }
}

/**
 * The bell-badge count. Delegates to [unreadFeed] rather than re-running the filters itself so
 * the count and the panel can never disagree -- a second copy of the chain would be a second copy
 * of the privacy filter, and two copies drift.
 */
fun unreadCount(items: List<FeedItem>, cursor: SeenCursor): Int = unreadFeed(items, cursor).size

/**
 * One user's readiness declaration for one specific turn, mirrored from the per-user
 * `readyForTurn` User flag by the phase-2 adapter. The turn is part of the record, not implied,
 * because readiness only means anything relative to the turn it was declared for.
 */
data class TurnReadiness(
    val userId: String,
    val turn: Int,
    val ready: Boolean,
)

/**
 * The GM's "players ready" strip. ADVISORY ONLY -- nothing blocks End Turn on this; the wizard's
 * commit gate stays driven solely by the activity caps (plan §4.2, §6.2).
 *
 * Every id in [userIds] appears in the map, so the strip renders "waiting on ..." without a
 * missing-key branch -- a user with no record is waiting, not absent from the roster. An entry is
 * `true` only when a record for that user has `turn == currentTurn` AND `ready`: a stale turn's
 * readiness NEVER carries forward, because a player ready for turn 11 said nothing about turn 12
 * -- the flag self-expires by comparison, needing no cleanup. The latest record for a user and
 * the current turn in list order wins, so a player who un-readies after clicking Ready is
 * honoured.
 */
fun readinessStrip(
    readiness: List<TurnReadiness>,
    currentTurn: Int,
    userIds: List<String>,
): Map<String, Boolean> =
    userIds.associateWith { userId ->
        readiness.lastOrNull { it.userId == userId && it.turn == currentTurn }?.ready ?: false
    }

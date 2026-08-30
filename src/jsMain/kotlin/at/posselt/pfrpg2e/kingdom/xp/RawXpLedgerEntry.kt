package at.posselt.pfrpg2e.kingdom.xp

import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.actor.partyMembers
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.setAppFlag
import com.foundryvtt.core.Game
import kotlinx.js.JsPlainObject

/**
 * Storage for the party XP ledger (`docs/plans/2026-07-09-plan-xp-ledger.md` section 4).
 *
 * The ledger is about the PARTY, so it lives on the party actor as a module flag rather than on
 * the kingdom. That is also why there is no Migration: module migrations walk kingdom data, an
 * absent flag reads as an empty ledger, and there is nothing to back-fill.
 */
@JsPlainObject
external interface RawXpLedgerEntry {
    var id: String
    var turn: Int
    /** ISO string, the same shape RawTurnRecord stores time in. */
    var timestamp: String
    /** XpSourceKind.value; an unrecognised kind drops out at the boundary rather than throwing. */
    var sourceKind: String
    /** Hex key, quest id, expedition id -- the double-count key. */
    var sourceRef: String
    var proposedAmount: Int
    /** What the GM actually confirmed; null while the offer is unanswered. */
    var grantedAmount: Int?
    /** XpOfferStatus.value. */
    var status: String
    var note: String?
}

const val XP_LEDGER_FLAG = "xpLedger"

/** Null for a stored kind or status this build does not know: the row is skipped, never thrown on. */
fun RawXpLedgerEntry.toModel(): XpLedgerEntry? {
    val kind = XpSourceKind.fromValue(sourceKind) ?: return null
    val state = XpOfferStatus.fromValue(status) ?: return null
    return XpLedgerEntry(
        id = id,
        turn = turn,
        timestamp = timestamp,
        sourceKind = kind,
        sourceRef = sourceRef,
        proposedAmount = proposedAmount,
        grantedAmount = grantedAmount,
        status = state,
        note = note,
    )
}

fun XpLedgerEntry.toRaw(): RawXpLedgerEntry = RawXpLedgerEntry(
    id = id,
    turn = turn,
    timestamp = timestamp,
    sourceKind = sourceKind.value,
    sourceRef = sourceRef,
    proposedAmount = proposedAmount,
    grantedAmount = grantedAmount,
    status = status.value,
    note = note,
)

/** The stored ledger as models, oldest first; unparseable rows are skipped. */
fun CampingActor.xpLedger(): List<XpLedgerEntry> =
    getAppFlag<CampingActor, Array<RawXpLedgerEntry>?>(XP_LEDGER_FLAG)
        ?.mapNotNull { it.toModel() }
        ?: emptyList()

/**
 * THE write funnel. It carries unparseable rows THROUGH: a row this build cannot read is a row a
 * newer build wrote, and dropping it on every save would quietly delete a future version's
 * history -- the same drop-from-evaluation-is-not-drop-from-storage rule the rumor funnel follows.
 */
suspend fun CampingActor.updateXpLedger(block: (List<XpLedgerEntry>) -> List<XpLedgerEntry>) {
    val stored = getAppFlag<CampingActor, Array<RawXpLedgerEntry>?>(XP_LEDGER_FLAG) ?: emptyArray()
    val known = stored.mapNotNull { it.toModel() }
    val unknown = stored.filter { it.toModel() == null }
    val next = block(known).map { it.toRaw() }
    setAppFlag(XP_LEDGER_FLAG, (unknown + next).toTypedArray())
}

/** The party actor the ledger hangs off; null in a world with no camping/party actor. */
fun Game.xpLedgerActor(): CampingActor? = getCampingActors().firstOrNull()

/**
 * A party member's lifetime XP, DERIVED: PF2e stores progress within the current level
 * (`system.details.xp.value`) plus the level, not a running total. Using the character's own
 * `xp.max` as the per-level threshold keeps this honest under a house rule that changes it.
 *
 * Reconciliation compares this against the ledger and reports the gap; combat XP and hand edits
 * never pass through the ledger, so drift is expected and never corrected.
 */
fun CampingActor.partyLifetimeXp(): Int {
    val members = partyMembers()
    val member = members.firstOrNull() ?: return 0
    val level = member.system.details.level.value
    val threshold = member.system.details.xp.max
    val within = member.system.details.xp.value
    return (level - 1).coerceAtLeast(0) * threshold + within
}

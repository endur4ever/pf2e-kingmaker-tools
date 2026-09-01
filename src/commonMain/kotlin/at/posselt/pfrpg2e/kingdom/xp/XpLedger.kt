package at.posselt.pfrpg2e.kingdom.xp

/**
 * Pure core of the party XP ledger (`docs/plans/2026-07-09-plan-xp-ledger.md`).
 *
 * This ledger is **party (PC) XP only**. Party XP and kingdom XP are two separate currencies with
 * two separate award paths — PC XP lives on `PF2ECharacter.system.details.xp` and is granted
 * through `updateXP`, kingdom XP lives on `KingdomData.xp` and is granted through
 * `gainXp`/`levelUp` — and conflating them is the failure mode the plan exists to avoid:
 * confirming a hex-clear award through the kingdom path would level up the *kingdom*. Nothing in
 * this file reads or writes kingdom XP; `MilestoneOffers.kt` owns that side.
 */

enum class XpSourceKind(val value: String) {
    HEX_RECONNOITERED("hexReconnoitered"), SITE_CLEARED("siteCleared"),
    QUEST_COMPLETED("questCompleted"), EXPEDITION_RESOLVED("expeditionResolved"),
    RP_ENCOUNTER("rpEncounter"), MANUAL("manual");

    companion object {
        fun fromValue(value: String?): XpSourceKind? = entries.find { it.value == value }
    }
}

enum class XpOfferStatus(val value: String) {
    OFFERED("offered"), CONFIRMED("confirmed"), DISMISSED("dismissed");

    companion object {
        fun fromValue(value: String?): XpOfferStatus? = entries.find { it.value == value }
    }
}

/**
 * One observed beat, from offer through answer.
 *
 * [timestamp] is an ISO string the **caller** supplies: no clock in here keeps every function
 * deterministic, and `RawTurnRecord` already stores time exactly this way. [sourceRef] — hex key,
 * quest id, expedition id — is the double-count key [proposeEntry] guards on. [grantedAmount] is
 * what the GM actually confirmed, null while unanswered: the amount is editable at confirm time,
 * so the ledger records what was granted, never merely what was proposed.
 */
data class XpLedgerEntry(
    val id: String,
    val turn: Int,
    val timestamp: String,
    val sourceKind: XpSourceKind,
    val sourceRef: String,
    val proposedAmount: Int,
    val grantedAmount: Int? = null,
    val status: XpOfferStatus = XpOfferStatus.OFFERED,
    val note: String? = null,
)

/** Cap on **answered** (confirmed or dismissed) entries, in the same shape as
 *  `appendShipmentHistory`: the stored flag must stay bounded, but only answered history is
 *  trimmable — an unanswered offer is pending work. */
const val XP_LEDGER_CAP = 500

/**
 * [candidate], unless this exact beat is already in the ledger — then null.
 *
 * The guard is the `(sourceKind, sourceRef)` pair in **any** status: a hex cleared, re-populated
 * by the GM and cleared again reports the same key, and a dismissed offer must not re-propose —
 * awarding twice for one sourceRef is the failure this returns null for. The same sourceRef under
 * a different kind is a different beat and passes. [XpSourceKind.MANUAL] candidates always pass:
 * a GM who genuinely wants a second award adds one deliberately, and it shows up as such in the
 * history.
 */
fun proposeEntry(existing: List<XpLedgerEntry>, candidate: XpLedgerEntry): XpLedgerEntry? {
    if (candidate.sourceKind == XpSourceKind.MANUAL) return candidate
    val duplicate = existing.any {
        it.sourceKind == candidate.sourceKind && it.sourceRef == candidate.sourceRef
    }
    return if (duplicate) null else candidate
}

/**
 * Append [entry], trimming the oldest **answered** entries once they exceed [cap].
 *
 * Offered entries are never pruned, no matter how many there are: an unanswered offer is pending
 * XP the party earned, and silently dropping it loses the award. If offers alone ever exceed the
 * cap that is a bug in the offer generator, not a pruning problem.
 */
fun appendEntry(
    existing: List<XpLedgerEntry>,
    entry: XpLedgerEntry,
    cap: Int = XP_LEDGER_CAP,
): List<XpLedgerEntry> {
    val all = existing + entry
    val answered = all.filter { it.status != XpOfferStatus.OFFERED }
    if (answered.size <= cap) return all
    val drop = answered.take(answered.size - cap).toSet()
    return all.filterNot { it in drop }
}

/**
 * Sum of [XpLedgerEntry.grantedAmount] over confirmed entries.
 *
 * Never [XpLedgerEntry.proposedAmount]: the confirm row's amount field is editable, and an
 * edited-down confirm must not report the proposed figure. A confirmed entry that somehow lacks a
 * granted amount contributes zero rather than borrowing the proposal.
 */
fun confirmedTotal(entries: List<XpLedgerEntry>): Int =
    entries.filter { it.status == XpOfferStatus.CONFIRMED }.sumOf { it.grantedAmount ?: 0 }

/**
 * Per-source-kind granted totals for the ledger header, confirmed entries only.
 *
 * Offered and dismissed entries granted nothing, so they contribute nothing; a kind with no
 * confirmed entries is absent rather than zero, because the header lists where XP actually came
 * from, not every kind that exists.
 */
fun totalsByKind(entries: List<XpLedgerEntry>): Map<XpSourceKind, Int> =
    entries.filter { it.status == XpOfferStatus.CONFIRMED }
        .groupBy { it.sourceKind }
        .mapValues { (_, entriesOfKind) -> entriesOfKind.sumOf { it.grantedAmount ?: 0 } }

/** What the ledger says was granted, against what a PC actually holds. */
data class XpReconciliation(val ledgerTotal: Int, val actualLifetimeXp: Int, val drift: Int)

/**
 * Reconcile the ledger against [actualLifetimeXp].
 *
 * Drift is expected and not an error — combat XP and hand edits never pass through the ledger —
 * so this reports a number, never a correction: positive drift is the steady state of a party
 * that fights, and nothing here writes anything back.
 */
fun reconcile(entries: List<XpLedgerEntry>, actualLifetimeXp: Int): XpReconciliation {
    val ledgerTotal = confirmedTotal(entries)
    return XpReconciliation(
        ledgerTotal = ledgerTotal,
        actualLifetimeXp = actualLifetimeXp,
        drift = actualLifetimeXp - ledgerTotal,
    )
}

// ── Default award table (plan section 3, signed off 2026-09-01) ───────────────────────────────
//
// PF2e accomplishment XP. These are DEFAULTS ONLY: every offer is GM-confirmed and its amount is
// editable at confirm time, so a default that does not fit a particular beat costs one edit --
// the ledger records what was actually granted, never merely what was proposed.

const val XP_AWARD_HEX_RECONNOITERED = 10
const val XP_AWARD_SITE_MINOR = 10
const val XP_AWARD_SITE_MODERATE = 30
const val XP_AWARD_SITE_MAJOR = 80
const val XP_AWARD_QUEST_SMALL = 30
const val XP_AWARD_QUEST_MAJOR = 80
const val XP_AWARD_EXPEDITION = 30
const val XP_AWARD_RP_ENCOUNTER = 30

/** How significant a beat was, where the source data can tell us. */
enum class XpBeatTier { MINOR, MODERATE, MAJOR }

/**
 * The proposed award for one beat.
 *
 * A cleared site defaults to MODERATE because nothing in the hex data records difficulty -- the
 * GM raises or lowers it on the card rather than the module guessing from a type name it cannot
 * interpret.
 */
fun defaultXpAward(kind: XpSourceKind, tier: XpBeatTier = XpBeatTier.MODERATE): Int = when (kind) {
    XpSourceKind.HEX_RECONNOITERED -> XP_AWARD_HEX_RECONNOITERED
    XpSourceKind.SITE_CLEARED -> when (tier) {
        XpBeatTier.MINOR -> XP_AWARD_SITE_MINOR
        XpBeatTier.MODERATE -> XP_AWARD_SITE_MODERATE
        XpBeatTier.MAJOR -> XP_AWARD_SITE_MAJOR
    }
    XpSourceKind.QUEST_COMPLETED -> if (tier == XpBeatTier.MAJOR) XP_AWARD_QUEST_MAJOR else XP_AWARD_QUEST_SMALL
    XpSourceKind.EXPEDITION_RESOLVED -> XP_AWARD_EXPEDITION
    XpSourceKind.RP_ENCOUNTER -> XP_AWARD_RP_ENCOUNTER
    // a hand-entered row carries the GM's own number; there is no default to propose
    XpSourceKind.MANUAL -> 0
}

/** The entries still awaiting an answer, oldest first -- the digest's contents. */
fun pendingOffers(entries: List<XpLedgerEntry>): List<XpLedgerEntry> =
    entries.filter { it.status == XpOfferStatus.OFFERED }

/**
 * [id] answered: confirmed with [granted], or dismissed when [granted] is null.
 *
 * Only an OFFERED entry can be answered, so a second click on a card already resolved -- or on a
 * stale card in chat scrollback -- is a no-op rather than a second grant.
 */
fun answerEntry(entries: List<XpLedgerEntry>, id: String, granted: Int?): List<XpLedgerEntry> =
    entries.map { entry ->
        if (entry.id != id || entry.status != XpOfferStatus.OFFERED) entry
        else if (granted == null) entry.copy(status = XpOfferStatus.DISMISSED)
        else entry.copy(status = XpOfferStatus.CONFIRMED, grantedAmount = granted)
    }

package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.data.kingdom.Contribution
import at.posselt.pfrpg2e.data.kingdom.ContributionKind
import at.posselt.pfrpg2e.data.kingdom.DeedCategory
import at.posselt.pfrpg2e.data.kingdom.PcRenown
import at.posselt.pfrpg2e.data.kingdom.TurnTally
import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import kotlinx.js.JsPlainObject

/**
 * Persisted shapes for PC renown, its lifetime tallies, the open turn's deed log and the per-turn
 * contribution rows (`docs/plans/2026-07-09-plan-renown-spotlight.md` §2.2 and §2.3).
 *
 * Two rules shape every interface below, and both exist because these objects live in a Foundry
 * flag that a world older than this build wrote and a world newer than this build may write next.
 *
 * FIRST: every field is nullable. A `@JsPlainObject` is not validated on read — a missing property
 * is `undefined`, and a non-null Kotlin declaration over `undefined` is a lie the compiler will
 * happily let a caller dereference. Declaring the identity fields nullable costs one `?: return
 * null` per converter and makes a half-written row impossible to mistake for a whole one.
 *
 * SECOND: enums are STRINGS here and only here. `ContributionKind`, `DeedCategory` and `Leader`
 * all live in commonMain and all answer null rather than throwing for a value they do not
 * recognise, so a row written by a newer catalog drops out of one tick instead of taking the tick
 * down with it. That is why the converters below return null generously: renown is personal
 * colour, and no amount of it is worth a broken End Turn.
 *
 * The model side is `RenownEngine.kt`, which is already written and is NOT re-declared here. Every
 * type these converters produce -- [PcRenown], [Contribution], [TurnTally], [ContributionKind],
 * [DeedCategory] -- is imported from it.
 */

/**
 * ONE row per PC per turn, the Spotlight's unit of comparison.
 *
 * Deliberately keyed by actor ALONE: role- and category-scoped counts are lifetime counters on
 * [RawPcRenown] instead, because keying this row by actor x role x category would multiply the
 * per-turn record by sixteen for questions the Spotlight never asks. The buckets are DISJOINT --
 * the seam bumps exactly one field per deed, so a critical success lands in [crits] and not also
 * in [checks].
 */
@JsPlainObject
external interface RawTurnContribution {
    var actorUuid: String?

    /** Denormalised so the Spotlight line renders without a UUID lookup, and still renders after
     *  the actor is deleted. Never the source of truth for identity: that is [actorUuid]. */
    var actorName: String?
    var checks: Int?
    var crits: Int?
    var critFails: Int?
    var activities: Int?
    var events: Int?
}

/**
 * One leadership role's LIFETIME tally for one PC.
 *
 * [role] is a `Leader.value` (ruler … warden). Stored as an array of rows rather than as an object
 * keyed by role because a `@JsPlainObject` with dynamic keys is neither typeable nor indexable
 * here -- the same reason `RawLeaders` is eight named properties.
 *
 * [successes] INCLUDES critical successes, matching `applyDeedCounters` in the engine, which bumps
 * both counters for one crit. Reading the two as disjoint would undercount a role by every crit
 * ever rolled in it.
 */
@JsPlainObject
external interface RawRoleTally {
    var role: String?
    var successes: Int?
    var crits: Int?
}

/**
 * One deed category's LIFETIME tally for one PC. [category] is a `DeedCategory.value`
 * (diplomatic | martial | other), derived at the seam from the rolled kingdom skill.
 */
@JsPlainObject
external interface RawCategoryTally {
    var category: String?
    var activities: Int?
    var crits: Int?
}

/**
 * A PC's personal standing with ONE faction.
 *
 * [factionName] is free text matching `RawGroup.name`, because a faction has no id. [renown] is
 * signed and token-scale, soft-capped an order of magnitude inside the kingdom standing range: it
 * is a different number from `RawGroup.standing` and nothing may ever fold one into the other
 * (plan §2.4).
 */
@JsPlainObject
external interface RawPcFactionRenown {
    var factionName: String?
    var renown: Int?
}

/**
 * One PC's cumulative renown ledger, the persisted mirror of [PcRenown].
 *
 * The lifetime counters are not a cache of the turn history: the history is capped at a hundred
 * records and its rows are keyed by actor alone, so a catalog condition that asked "fifteen
 * Treasurer successes" would quietly stop being answerable in a long campaign. They are maintained
 * in O(1) at the seam, which has the [Leader] and the [DeedCategory] already in scope.
 *
 * [lastOfferedTurn] and [actorName] have no counterpart on [PcRenown] and never will: the model is
 * the renown arithmetic, and those two are offer bookkeeping and display denormalisation. They are
 * carried across the boundary as explicit parameters of [toRaw] rather than smuggled into the pure
 * core.
 */
@JsPlainObject
external interface RawPcRenown {
    var actorUuid: String?
    var actorName: String?

    /** 0..100 populace renown. Null reads as zero: unknown and unremarkable are the same thing. */
    var populace: Int?
    var factionRenown: Array<RawPcFactionRenown>?

    /** Earned epithet ids from the commonMain catalog. Unknown ids are KEPT, not dropped: an
     *  honour granted by a GM under an older catalog stays granted, it simply resolves to no
     *  perk. */
    var epithets: Array<String>?

    /** Granted purchase-access tier, 0..2. Null reads as zero, i.e. no perk. */
    var purchaseAccessTier: Int?

    /** Last turn an epithet or perk offer was emitted for this PC, so End Turn cannot re-offer. */
    var lastOfferedTurn: Int?
    var lifetimeCrits: Int?
    var lifetimeCritFails: Int?
    var lifetimeActivities: Int?
    var lifetimeEvents: Int?
    var roleTallies: Array<RawRoleTally>?
    var categoryTallies: Array<RawCategoryTally>?
}

/**
 * One deed credited during the OPEN turn, kept so a re-roll can REPLACE its own earlier result
 * rather than double-counting it (plan §3.2).
 *
 * [populaceApplied] and [factionApplied] are the deltas that actually LANDED after clamping, not
 * the deltas the kind is nominally worth. That distinction is the whole point of the row: at the
 * 0, 100 and ±40 boundaries a credit is partly or wholly swallowed, and re-deriving the reversal
 * from [kind] would refund renown that was never granted.
 *
 * Cleared at End Turn along with the turn's contribution rows, so it stays bounded by one turn's
 * worth of rolls and is never written into a turn record.
 */
@JsPlainObject
external interface RawRenownDeed {
    /** Minted at the roll and carried through re-rolls in the roll-meta dataset. */
    var deedId: String?
    var actorUuid: String?

    /** A `ContributionKind.value`. */
    var kind: String?

    /** A `Leader.value`. */
    var role: String?

    /** A `DeedCategory.value`. */
    var category: String?
    var factionName: String?
    var populaceApplied: Int?
    var factionApplied: Int?
}

/** The populace movement this deed actually credited; null reads as zero, so a row written before
 *  the field existed reverses to a no-op instead of to a refund. */
val RawRenownDeed.appliedPopulace: Int
    get() = populaceApplied ?: 0

/** The per-faction movement this deed actually credited. Null reads as zero, as above. */
val RawRenownDeed.appliedFaction: Int
    get() = factionApplied ?: 0

/** Null when the row carries no usable actor identity -- a tally credited to nobody can neither
 *  win the Spotlight nor be shown, so it is dropped rather than shown as an anonymous winner. */
fun RawTurnContribution.toModel(): TurnTally? {
    val actorUuid = actorUuid?.takeIf { it.isNotBlank() } ?: return null
    return TurnTally(
        actorUuid = actorUuid,
        actorName = actorName?.takeIf { it.isNotBlank() },
        checks = checks ?: 0,
        crits = crits ?: 0,
        critFails = critFails ?: 0,
        activities = activities ?: 0,
        events = events ?: 0,
    )
}

fun TurnTally.toRaw(): RawTurnContribution =
    RawTurnContribution(
        actorUuid = actorUuid,
        actorName = actorName,
        checks = checks,
        crits = crits,
        critFails = critFails,
        activities = activities,
        events = events,
    )

/** Stored rows as models, dropping any that carry no actor. Empty when never written. */
fun Array<RawTurnContribution>?.toTurnTallies(): List<TurnTally> =
    this?.mapNotNull { it.toModel() }.orEmpty()

/**
 * Null when the row carries no usable actor identity.
 *
 * Individual tally rows are dropped INDIVIDUALLY when their role or category string is one this
 * build does not know, rather than dropping the whole ledger: losing one unreadable counter costs
 * a PC one epithet condition, while losing the ledger costs them every epithet they have earned.
 */
fun RawPcRenown.toModel(): PcRenown? {
    val actorUuid = actorUuid?.takeIf { it.isNotBlank() } ?: return null
    val roles = roleTallies
        ?.mapNotNull { row -> row.role?.let { Leader.fromString(it) }?.let { leader -> leader to row } }
        .orEmpty()
    val categories = categoryTallies
        ?.mapNotNull { row -> DeedCategory.fromValue(row.category)?.let { category -> category to row } }
        .orEmpty()
    return PcRenown(
        actorUuid = actorUuid,
        populace = populace ?: 0,
        factionRenown = factionRenown
            ?.mapNotNull { row ->
                row.factionName?.takeIf { it.isNotBlank() }?.let { name -> name to (row.renown ?: 0) }
            }
            .orEmpty()
            .toCounterMap(),
        epithets = epithets?.filter { it.isNotBlank() }?.toSet().orEmpty(),
        purchaseAccessTier = purchaseAccessTier ?: 0,
        lifetimeCrits = lifetimeCrits ?: 0,
        lifetimeCritFails = lifetimeCritFails ?: 0,
        lifetimeActivities = lifetimeActivities ?: 0,
        lifetimeEvents = lifetimeEvents ?: 0,
        roleSuccesses = roles.map { (leader, row) -> leader to (row.successes ?: 0) }.toCounterMap(),
        roleCrits = roles.map { (leader, row) -> leader to (row.crits ?: 0) }.toCounterMap(),
        categoryActivities = categories.map { (category, row) -> category to (row.activities ?: 0) }.toCounterMap(),
        categoryCrits = categories.map { (category, row) -> category to (row.crits ?: 0) }.toCounterMap(),
    )
}

/**
 * The persisted row for a ledger, plus the two fields the pure core deliberately does not carry.
 *
 * Rows are emitted in a STABLE order (roles and categories by their persisted value, factions and
 * epithets alphabetically) so writing an unchanged ledger twice produces byte-identical JSON. The
 * module's preview-then-commit discipline compares turns that were evaluated twice, and a map
 * iteration order leaking into the flag would show a spurious diff on every one of them.
 */
fun PcRenown.toRaw(
    // NO DEFAULTS on purpose: neither value exists on the pure model, so a bare toRaw() would
    // compile and silently write null over a real one. For lastOfferedTurn that re-offers every
    // epithet the PC already accepted, every turn -- exactly what the field exists to prevent.
    // Same shape as TreasureLedgerEntry.toRaw(sourceName) and DowntimeProject.toRaw(...).
    actorName: String?,
    lastOfferedTurn: Int?,
): RawPcRenown =
    RawPcRenown(
        actorUuid = actorUuid,
        actorName = actorName,
        populace = populace,
        factionRenown = factionRenown.entries
            .sortedBy { it.key }
            .map { RawPcFactionRenown(factionName = it.key, renown = it.value) }
            .toTypedArray(),
        epithets = epithets.sorted().toTypedArray(),
        purchaseAccessTier = purchaseAccessTier,
        lastOfferedTurn = lastOfferedTurn,
        lifetimeCrits = lifetimeCrits,
        lifetimeCritFails = lifetimeCritFails,
        lifetimeActivities = lifetimeActivities,
        lifetimeEvents = lifetimeEvents,
        roleTallies = (roleSuccesses.keys + roleCrits.keys)
            .sortedBy { it.value }
            .map {
                RawRoleTally(
                    role = it.value,
                    successes = roleSuccesses[it] ?: 0,
                    crits = roleCrits[it] ?: 0,
                )
            }
            .toTypedArray(),
        categoryTallies = (categoryActivities.keys + categoryCrits.keys)
            .sortedBy { it.value }
            .map {
                RawCategoryTally(
                    category = it.value,
                    activities = categoryActivities[it] ?: 0,
                    crits = categoryCrits[it] ?: 0,
                )
            }
            .toTypedArray(),
    )

/** Stored ledgers as models, dropping any that carry no actor. Empty when never written. */
fun Array<RawPcRenown>?.toRenownModels(): List<PcRenown> =
    this?.mapNotNull { it.toModel() }.orEmpty()

/**
 * Null when [kind], [role] or [category] is a value this build does not recognise.
 *
 * All three are required rather than defaulted, because this value's only consumer is the
 * reversal path: a deed reverted under a GUESSED role or category would decrement a counter the
 * original deed never incremented, and the ledger would drift permanently. An unreversible deed
 * merely leaves the original credit standing, which is the far cheaper failure.
 */
fun RawRenownDeed.toModel(): Contribution? {
    val kind = ContributionKind.fromValue(kind) ?: return null
    val leader = role?.let { Leader.fromString(it) } ?: return null
    val category = DeedCategory.fromValue(category) ?: return null
    return Contribution(
        kind = kind,
        leader = leader,
        category = category,
        factionName = factionName?.takeIf { it.isNotBlank() },
    )
}

/**
 * The persisted deed row for one credited [Contribution].
 *
 * [populaceApplied] and [factionApplied] are the caller's `AccrualResult` values, NOT the nominal
 * deltas for the kind -- see [RawRenownDeed].
 */
fun Contribution.toRaw(
    deedId: String,
    actorUuid: String,
    populaceApplied: Int,
    factionApplied: Int,
): RawRenownDeed =
    RawRenownDeed(
        deedId = deedId,
        actorUuid = actorUuid,
        kind = kind.value,
        role = leader.value,
        category = category.value,
        factionName = factionName,
        populaceApplied = populaceApplied,
        factionApplied = factionApplied,
    )

/**
 * Sums duplicate keys and drops the zeroes.
 *
 * Summing rather than last-wins because a ledger that somehow holds two rows for one role is a
 * damaged tally, and adding them is the only reading that keeps a PC's total honest. Dropping
 * zeroes matches the engine, which never stores a zero-valued counter, so a ledger that round
 * trips through this boundary compares equal to the one the engine produced.
 */
private fun <K : Any> List<Pair<K, Int>>.toCounterMap(): Map<K, Int> {
    val merged = mutableMapOf<K, Int>()
    for ((key, value) in this) {
        merged[key] = (merged[key] ?: 0) + value
    }
    return merged.filterValues { it != 0 }
}

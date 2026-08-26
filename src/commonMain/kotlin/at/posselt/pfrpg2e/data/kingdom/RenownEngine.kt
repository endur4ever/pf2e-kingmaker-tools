package at.posselt.pfrpg2e.data.kingdom

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader

/**
 * Pure core of PC renown, epithets and the Spotlight of the Turn
 * (`docs/plans/2026-07-09-plan-renown-spotlight.md`, §2.2 and §3.1).
 *
 * Renown is deliberately a SECOND, much smaller economy than faction standing, and the whole point
 * of keeping it here is that it can never quietly become a first one: nothing in this file reads or
 * writes a kingdom's `RawGroup.standing` (§2.4), the personal per-faction number clamps to a soft
 * cap far inside the kingdom standing range, and the only mechanical consequence it can produce is
 * the closed two-element [RenownPerk] set. If a later change gives this file a path into a DC, a
 * roll or a resource, that is the rule it broke.
 *
 * Everything below is a plain value type mirroring the jsMain `Raw*` carriers, exactly as
 * `FactionRelations.kt` works on `Int` rather than on `RawGroup`; the jsMain adapter owns the
 * mapping, including collapsing the all-nullable Raw fields onto these defaults (a null populace is
 * zero, an absent faction row is no renown). There is no clock and no randomness anywhere: the
 * module's preview-then-commit discipline requires that evaluating a turn twice yields byte-identical
 * output, so anything time-like is a caller-supplied parameter.
 *
 * The epithet CATALOG is deliberately NOT here. Its ids, prose and thresholds are authored content
 * (plan Appendix) and the repo owner's call, so [evaluateEpithets] takes the table as a parameter
 * and this file ships only the evaluator. No invented epithet can reach a save through it.
 */

/** Populace renown is a 0..100 personal reputation. Never negative: a PC cannot be less famous than
 *  unknown, and a negative floor would make the "feared, not loved" epithets unreachable by making
 *  every fumble cheaper the deeper you already are. */
const val MIN_POPULACE_RENOWN = 0
const val MAX_POPULACE_RENOWN = 100

/** Personal per-faction renown clamps to ±this. It is an order of magnitude tighter than
 *  `MAX_FACTION_STANDING` on purpose (§2.4): the two numbers look alike in a UI, so the engine keeps
 *  them visibly different in range to make a confusion of the two obvious rather than subtle. */
const val FACTION_RENOWN_SOFT_CAP = 40

/**
 * How a contribution was earned. The persisted [value] strings are what `RawRenownDeed.kind`
 * stores, so [fromValue] answers null rather than throwing for anything unrecognised: a deed row
 * written by a newer version must drop out of an older tick, not take the tick down with it.
 */
enum class ContributionKind(val value: String) {
    CHECK_SUCCESS("checkSuccess"),
    CHECK_CRIT("checkCrit"),
    CHECK_FAILURE("checkFailure"),
    CHECK_CRIT_FAIL("checkCritFail"),
    ACTIVITY("activity"),
    EVENT_RESOLVED("eventResolved"),
    PETITION_ANSWERED("petitionAnswered");

    companion object {
        fun fromValue(value: String?): ContributionKind? = entries.find { it.value == value }
    }
}

/**
 * The coarse bucket a deed falls into, derived from the kingdom skill the check was rolled with.
 * Persisted on `RawRenownDeed.category` and `RawCategoryTally.category`, hence the same
 * null-for-unknown [fromValue] contract as [ContributionKind].
 */
enum class DeedCategory(val value: String) {
    DIPLOMATIC("diplomatic"),
    MARTIAL("martial"),
    OTHER("other");

    companion object {
        fun fromValue(value: String?): DeedCategory? = entries.find { it.value == value }
    }
}

/**
 * Which flavour line the Spotlight renders. The [value] strings are the leaf i18n keys under
 * `kingdom.renown.spotlight`, kept in the enum so the literal-`when` key resolver mandated by §4.3
 * has one obvious thing to switch on.
 */
enum class SpotlightKind(val value: String) {
    BUSIEST("busiest"),
    CRIT_STAR("critStar"),
    BLUNDERER("blunderer"),
    DIPLOMAT("diplomat"),
    EVENT_HERO("eventHero");

    companion object {
        fun fromValue(value: String?): SpotlightKind? = entries.find { it.value == value }
    }
}

/**
 * Buckets a rolled skill for the epithet conditions.
 *
 * There is no `DIPLOMACY` kingdom skill, so the diplomatic bucket is the statecraft, politics,
 * trade and intrigue group, and the martial bucket is warfare plus defense. Deliberately coarse: its only
 * consumer is a handful of catalog conditions, and a finer taxonomy would have to be re-litigated
 * every time a homebrew skill appears. Anything unrecognised falls to [DeedCategory.OTHER] rather
 * than being dropped, so an unbucketed skill still earns its populace renown.
 */
fun deedCategoryFor(skill: KingdomSkill): DeedCategory = when (skill) {
    KingdomSkill.STATECRAFT, KingdomSkill.POLITICS, KingdomSkill.TRADE, KingdomSkill.INTRIGUE ->
        DeedCategory.DIPLOMATIC
    KingdomSkill.WARFARE, KingdomSkill.DEFENSE ->
        DeedCategory.MARTIAL
    else ->
        DeedCategory.OTHER
}

/**
 * One PC's cumulative renown ledger, the pure mirror of `RawPcRenown`.
 *
 * The lifetime counters are what make the authored catalog evaluable at all: its conditions ask
 * role- and category-scoped LIFETIME questions, and no aggregate of the per-turn contribution rows
 * can answer them, because those rows are keyed by actor alone and the turn history is capped. They
 * are maintained in O(1) at the seam, which already has the [Leader] and the [DeedCategory] in
 * scope.
 *
 * Zero-valued map entries are never stored: absent and zero mean the same thing to the adapter, and
 * normalising here is what lets [revertRenown] return a map equal to the one [accrueRenown] started
 * from instead of one littered with zeroed keys.
 */
data class PcRenown(
    val actorUuid: String,
    val populace: Int = 0,
    val factionRenown: Map<String, Int> = emptyMap(),
    val epithets: Set<String> = emptySet(),
    val purchaseAccessTier: Int = 0,
    val lifetimeCrits: Int = 0,
    val lifetimeCritFails: Int = 0,
    val lifetimeActivities: Int = 0,
    val lifetimeEvents: Int = 0,
    val roleSuccesses: Map<Leader, Int> = emptyMap(),
    val roleCrits: Map<Leader, Int> = emptyMap(),
    val categoryActivities: Map<DeedCategory, Int> = emptyMap(),
    val categoryCrits: Map<DeedCategory, Int> = emptyMap(),
)

/**
 * One credited deed as the engine sees it.
 *
 * Bundled into a value rather than spread over five parameters because the same deed has to be
 * handed back to [revertRenown] later: a re-roll re-enters the seam and must undo precisely what it
 * did the first time, and a struct that round-trips through the persisted deed row cannot drift the
 * way five positional arguments can.
 *
 * [factionName] is free text matching `RawGroup.name` because a faction has no id. It is non-null
 * only for negotiation-flavoured activities where a group was actually picked; every other deed
 * credits populace renown alone.
 */
data class Contribution(
    val kind: ContributionKind,
    val leader: Leader,
    val category: DeedCategory = DeedCategory.OTHER,
    val factionName: String? = null,
)

/** The populace and per-faction movement one [ContributionKind] is worth before clamping. */
data class RenownDelta(val populace: Int, val faction: Int)

/**
 * The renown one deed is worth, and the single source of truth for accrual.
 *
 * The numbers are small on purpose (§2.4): a very active PC should gain roughly fifteen to
 * twenty-five populace renown across a busy turn, against catalog thresholds that sit at thirty and
 * above, so an epithet reads as several turns of sustained work rather than one lucky evening. A
 * plain failure is worth nothing but is still a kind of its own, because the Spotlight tallies it
 * even though the ledger does not move.
 */
fun renownDeltaFor(kind: ContributionKind): RenownDelta = when (kind) {
    ContributionKind.CHECK_CRIT -> RenownDelta(populace = 3, faction = 2)
    ContributionKind.CHECK_SUCCESS -> RenownDelta(populace = 1, faction = 1)
    ContributionKind.CHECK_FAILURE -> RenownDelta(populace = 0, faction = 0)
    ContributionKind.CHECK_CRIT_FAIL -> RenownDelta(populace = -2, faction = -1)
    ContributionKind.ACTIVITY -> RenownDelta(populace = 1, faction = 0)
    ContributionKind.EVENT_RESOLVED -> RenownDelta(populace = 2, faction = 0)
    ContributionKind.PETITION_ANSWERED -> RenownDelta(populace = 2, faction = 0)
}

/**
 * A new ledger plus the deltas that ACTUALLY landed after clamping.
 *
 * The applied numbers are the whole reason this is not just a [PcRenown]: the adapter persists them
 * on the deed row, so a re-roll reverses exactly what was credited even when the original crit was
 * swallowed by the 100 ceiling or the ±40 soft cap. Recomputing the delta at reversal time from the
 * kind alone would silently refund renown that was never granted.
 */
data class AccrualResult(
    val renown: PcRenown,
    val populaceApplied: Int,
    val factionApplied: Int,
)

/**
 * Applies one deed to a PC's ledger and reports what landed.
 *
 * Returns a new [PcRenown]; the input is never touched, because the seam accrues and reverts inside
 * one turn and a mutated input would make a reverted re-roll unreconstructable.
 *
 * A blank or empty [Contribution.factionName] is treated as absent rather than as a faction called
 * "": free-text group names come out of a form, and an empty one would otherwise mint a nameless
 * faction row that no UI could ever label or clear.
 *
 * A populace value already outside 0..100 (a GM adjustment, legacy data) is pulled into range by
 * the same clamp as every other result, and the applied delta — always `after - before` — records
 * the whole of that move. That is what lets [revertRenown] put the odd value back afterwards
 * instead of stranding the PC at the ceiling.
 */
fun accrueRenown(current: PcRenown, deed: Contribution): AccrualResult {
    val delta = renownDeltaFor(deed.kind)
    val populace = (current.populace + delta.populace).coerceIn(MIN_POPULACE_RENOWN, MAX_POPULACE_RENOWN)
    val (factionRenown, factionApplied) = applyFactionDelta(current.factionRenown, deed.factionName, delta.faction)
    return AccrualResult(
        renown = applyDeedCounters(current, deed, sign = 1).copy(
            populace = populace,
            factionRenown = factionRenown,
        ),
        populaceApplied = populace - current.populace,
        factionApplied = factionApplied,
    )
}

/**
 * The exact inverse of [accrueRenown] for one already-recorded deed.
 *
 * Takes the stored applied deltas rather than re-deriving them from [deed], which is what makes a
 * re-roll exact at the 0, 100 and ±40 boundaries: the credit that was clamped away must not be
 * refunded.
 *
 * Deliberately does NOT re-clamp the populace result. Clamping a subtraction would make the reversal
 * lossy in precisely the case it exists for, and the value being restored was in range when it was
 * recorded.
 *
 * Epithets, the purchase-access tier and the actor identity are NEVER touched. An epithet is a
 * GM-confirmed honour rather than a derived value, so no re-roll may take one back; the tier is
 * likewise granted by hand.
 */
fun revertRenown(
    current: PcRenown,
    deed: Contribution,
    populaceApplied: Int,
    factionApplied: Int,
): PcRenown {
    val factionName = deed.factionName?.takeIf { it.isNotBlank() }
    val factionRenown = if (factionName == null || factionApplied == 0) {
        current.factionRenown
    } else {
        current.factionRenown.withFactionRenown(factionName, (current.factionRenown[factionName] ?: 0) - factionApplied)
    }
    return applyDeedCounters(current, deed, sign = -1).copy(
        populace = current.populace - populaceApplied,
        factionRenown = factionRenown,
    )
}

/**
 * The lifetime bookkeeping for one deed, in ONE table read forwards by [accrueRenown] with
 * `sign = 1` and backwards by [revertRenown] with `sign = -1`.
 *
 * Written once rather than twice on purpose: two hand-mirrored copies are exactly the kind of code
 * that drifts the day a counter is added, and a drifted reversal leaves a PC permanently ahead of
 * their own deeds.
 *
 * A critical success counts as a success in its role as well as a crit, matching the persisted
 * `RawRoleTally.successes` contract ("critical successes included"). A plain failure and an answered
 * petition move no counter at all; they exist as kinds for the per-turn tally and the populace
 * ledger respectively.
 */
private fun applyDeedCounters(renown: PcRenown, deed: Contribution, sign: Int): PcRenown =
    when (deed.kind) {
        ContributionKind.CHECK_CRIT -> renown.copy(
            lifetimeCrits = (renown.lifetimeCrits + sign).coerceAtLeast(0),
            roleSuccesses = renown.roleSuccesses.withCounter(deed.leader, sign),
            roleCrits = renown.roleCrits.withCounter(deed.leader, sign),
            categoryCrits = renown.categoryCrits.withCounter(deed.category, sign),
        )
        ContributionKind.CHECK_SUCCESS -> renown.copy(
            roleSuccesses = renown.roleSuccesses.withCounter(deed.leader, sign),
        )
        ContributionKind.CHECK_CRIT_FAIL -> renown.copy(
            lifetimeCritFails = (renown.lifetimeCritFails + sign).coerceAtLeast(0),
        )
        ContributionKind.ACTIVITY -> renown.copy(
            lifetimeActivities = (renown.lifetimeActivities + sign).coerceAtLeast(0),
            categoryActivities = renown.categoryActivities.withCounter(deed.category, sign),
        )
        ContributionKind.EVENT_RESOLVED -> renown.copy(
            lifetimeEvents = (renown.lifetimeEvents + sign).coerceAtLeast(0),
        )
        ContributionKind.CHECK_FAILURE, ContributionKind.PETITION_ANSWERED -> renown
    }

/**
 * Moves one counter key. Floors at zero and drops the key when it lands there, so reverting a deed
 * the ledger never recorded is a no-op instead of minting a negative lifetime count, and so an
 * accrue-then-revert pair returns the very map it started from.
 */
private fun <K> Map<K, Int>.withCounter(key: K, delta: Int): Map<K, Int> {
    if (delta == 0) return this
    val next = ((this[key] ?: 0) + delta).coerceAtLeast(0)
    return if (next == 0) this - key else this + (key to next)
}

/** Personal faction renown is signed, so only an exact zero drops the row here. */
private fun Map<String, Int>.withFactionRenown(name: String, value: Int): Map<String, Int> =
    if (value == 0) this - name else this + (name to value)

private fun applyFactionDelta(
    current: Map<String, Int>,
    factionName: String?,
    delta: Int,
): Pair<Map<String, Int>, Int> {
    val name = factionName?.takeIf { it.isNotBlank() } ?: return current to 0
    if (delta == 0) return current to 0
    val before = current[name] ?: 0
    val after = (before + delta).coerceIn(-FACTION_RENOWN_SOFT_CAP, FACTION_RENOWN_SOFT_CAP)
    return current.withFactionRenown(name, after) to (after - before)
}

/**
 * Everything an authored epithet condition is allowed to read.
 *
 * Every field is a fold of a [PcRenown] the caller already holds, which is what keeps epithet
 * evaluation off the turn history entirely: the history is capped, so a condition that counted
 * across it would quietly stop being true in a long campaign.
 *
 * [factionRenown] empty means no personal standing is known with anyone, which every faction-named
 * condition must treat as "not met" rather than as zero-and-therefore-below-a-negative-threshold.
 */
data class EpithetContext(
    val roleSuccesses: Map<Leader, Int> = emptyMap(),
    val roleCrits: Map<Leader, Int> = emptyMap(),
    val categoryActivities: Map<DeedCategory, Int> = emptyMap(),
    val critSuccesses: Int = 0,
    val critFails: Int = 0,
    val activities: Int = 0,
    val eventsResolved: Int = 0,
    val holdsRulerRole: Boolean = false,
    val populace: Int = 0,
    val factionRenown: Map<String, Int> = emptyMap(),
)

/**
 * Folds a PC's ledger into the context its conditions read.
 *
 * [holdsRulerRole] is the one thing renown cannot know about itself and so must be supplied: the
 * caller resolves the Ruler slot's actor and compares it to this PC.
 *
 * [populace] defaults to the PC's own populace renown, which is the reading the rest of the plan
 * uses (the deltas in [renownDeltaFor] credit a person, not a realm). It stays a parameter because
 * one line of the plan describes the same field as a realm-wide number the adapter sources from
 * kingdom state; a caller that settles on that reading passes it without this function having
 * guessed on their behalf.
 */
fun epithetContextFor(
    renown: PcRenown,
    holdsRulerRole: Boolean,
    populace: Int = renown.populace,
): EpithetContext = EpithetContext(
    roleSuccesses = renown.roleSuccesses,
    roleCrits = renown.roleCrits,
    categoryActivities = renown.categoryActivities,
    critSuccesses = renown.lifetimeCrits,
    critFails = renown.lifetimeCritFails,
    activities = renown.lifetimeActivities,
    eventsResolved = renown.lifetimeEvents,
    holdsRulerRole = holdsRulerRole,
    populace = populace,
    factionRenown = renown.factionRenown,
)

/**
 * The closed set of things an epithet may grant. Two members, and no more: renown that could hand
 * out kingdom bonuses, XP, RP or check modifiers would be the second standing economy §2.4 exists
 * to prevent.
 */
sealed interface RenownPerk {
    /** Raises the item purchase LEVEL a settlement can offer by [levels]. Not a price discount:
     *  the settlement inspector renders levels and no prices at all, so a discount would have had
     *  no consumer. */
    data class PurchaseAccess(val levels: Int) : RenownPerk

    /** Seeds an invitation quest offer, from [factionName] when the epithet names one. */
    data class Invitation(val factionName: String? = null) : RenownPerk
}

/**
 * One authored catalog condition.
 *
 * A `fun interface` rather than a raw lambda type so the table reads as data, and it receives the
 * entry's own [factionNames] instead of closing over a private copy: the faction-keyed epithets
 * match free text against `RawGroup.name`, and a table whose names live in one place is a table
 * whose dead entries can be spotted by reading it.
 */
fun interface EpithetCondition {
    fun isMet(context: EpithetContext, factionNames: List<String>): Boolean
}

/**
 * One row of the authored epithet catalog.
 *
 * The catalog itself is content, not engine, so it is passed in (see the file header). [id] is the
 * stable key persisted on `RawPcRenown.epithets` and resolved to prose through a literal `when`;
 * [perk] is null for the honours that are pure colour, which most of them are.
 */
data class EpithetDefinition(
    val id: String,
    val condition: EpithetCondition,
    val perk: RenownPerk? = null,
    val factionNames: List<String> = emptyList(),
)

/** A newly-earned epithet and the perk, if any, its offer should carry. */
data class EpithetAward(val epithetId: String, val perk: RenownPerk?)

/**
 * Which catalog entries this PC newly qualifies for.
 *
 * Held epithets are skipped, which is the entire "fires once" mechanism: the awarding caller writes
 * the id onto [PcRenown.epithets], and every later evaluation at or above the same threshold finds
 * it there and stays silent. Nothing is time-based, so re-running End Turn cannot re-offer.
 *
 * Results keep catalog order so the offers a GM sees arrive in the order the table was authored,
 * and an id can be awarded at most once per call even if the table repeats it — a duplicated row is
 * a content bug, and the failure mode of double-granting an honour is worse than of ignoring the
 * copy. A repeated id whose earlier row did not qualify is still allowed to fire on a later row,
 * because the two rows are different conditions.
 */
fun evaluateEpithets(
    renown: PcRenown,
    ctx: EpithetContext,
    catalog: List<EpithetDefinition> = EPITHET_CATALOG,
): List<EpithetAward> {
    val awarded = mutableSetOf<String>()
    val awards = mutableListOf<EpithetAward>()
    for (entry in catalog) {
        if (entry.id in renown.epithets) continue
        if (!entry.condition.isMet(ctx, entry.factionNames)) continue
        if (!awarded.add(entry.id)) continue
        awards.add(EpithetAward(epithetId = entry.id, perk = entry.perk))
    }
    return awards
}

/**
 * The perk an epithet id grants, or null when it grants none OR when the catalog has never heard of
 * it. An unknown id answers null rather than throwing because ids come out of a save file: a stale
 * epithet from an older catalog must degrade to a chip with no perk, not break the offer pass.
 */
fun perkForEpithet(epithetId: String, catalog: List<EpithetDefinition> = EPITHET_CATALOG): RenownPerk? =
    catalog.firstOrNull { it.id == epithetId }?.perk

/**
 * The highest personal renown this PC holds with any of [names], or null when none of them has a
 * recorded standing.
 *
 * Null rather than zero is the load-bearing part: a faction-named condition on an empty ledger must
 * read as "not met", and a zero default would make a negative-threshold nemesis epithet fire for a
 * PC who has never met the faction. Matching is case- and whitespace-insensitive because the key is
 * a GM-typed group name.
 */
fun EpithetContext.bestFactionRenown(names: List<String>): Int? =
    matchingFactionRenown(names).maxOrNull()

/** The lowest personal renown among [names], for the nemesis-shaped conditions. Null as in
 *  [bestFactionRenown], and for the same reason. */
fun EpithetContext.worstFactionRenown(names: List<String>): Int? =
    matchingFactionRenown(names).minOrNull()

private fun EpithetContext.matchingFactionRenown(names: List<String>): List<Int> {
    val wanted = names.mapNotNull { name -> name.trim().lowercase().takeIf { it.isNotEmpty() } }.toSet()
    if (wanted.isEmpty()) return emptyList()
    return factionRenown.entries
        .filter { it.key.trim().lowercase() in wanted }
        .map { it.value }
}

/** Highest purchase-access tier the closed perk set defines: tier 1 grants one extra settlement
 *  item purchase level, tier 2 grants two. PROVISIONAL — whether tier 2 should also cap at one
 *  level is an open question for the repo owner, which is why the mapping lives in exactly one
 *  function. */
const val MAX_PURCHASE_ACCESS_TIER = 2

/** Extra item purchase levels a granted tier is worth. Clamped rather than validated because the
 *  tier is a stored integer a GM dialog can set: an out-of-range value degrades to the nearest
 *  legal grant instead of throwing inside a settlement render. */
fun purchaseAccessLevelsForTier(tier: Int): Int = tier.coerceIn(0, MAX_PURCHASE_ACCESS_TIER)

/**
 * The purchase-level nudge a settlement gets, given every PC's granted tier.
 *
 * The bonus is settlement-scoped, not per-shopper: the inspector has no viewer and no buyer in
 * scope, and the realm's shops stocking better goods because someone the realm reveres lives there
 * is both the legible reading and the one with no exploit surface, since a GM granted every tier by
 * hand. Nobody at all yields zero.
 */
fun settlementPurchaseAccessLevels(tiers: List<Int>): Int =
    tiers.maxOfOrNull { purchaseAccessLevelsForTier(it) } ?: 0

/**
 * One PC's per-turn contribution counts, the pure mirror of `RawTurnContribution`.
 *
 * The buckets are DISJOINT: the seam bumps exactly one field per deed, so a critical success lands
 * in [crits] and not also in [checks]. Reading them as nested would make a crit worth four points
 * in the Spotlight weighting instead of three.
 */
data class TurnTally(
    val actorUuid: String,
    val actorName: String? = null,
    val checks: Int = 0,
    val crits: Int = 0,
    val critFails: Int = 0,
    val activities: Int = 0,
    val events: Int = 0,
)

/** Spotlight weights (§3.1). Crits are worth the most because they are the rarest thing a PC can
 *  choose to keep doing; a resolved event outranks a routine activity because a crisis had to
 *  happen first. */
const val SPOTLIGHT_CRIT_WEIGHT = 3
const val SPOTLIGHT_EVENT_WEIGHT = 2
const val SPOTLIGHT_ACTIVITY_WEIGHT = 1
const val SPOTLIGHT_CHECK_WEIGHT = 1

/** How many critical failures it takes before the light-hearted "worst of the turn" line becomes
 *  available at all. Rationing it is a tone rule, and it lives in code so it cannot be softened by
 *  whoever writes the string. */
const val BLUNDERER_CRIT_FAIL_THRESHOLD = 2

/**
 * The turn's achievement score for one PC. Negative counts are corrupt data (a mangled read-back,
 * a bad import) and are floored at zero so a broken row can neither win the Spotlight nor drag a
 * real contributor below someone who did nothing.
 */
fun spotlightScore(tally: TurnTally): Int =
    tally.crits.coerceAtLeast(0) * SPOTLIGHT_CRIT_WEIGHT +
        tally.events.coerceAtLeast(0) * SPOTLIGHT_EVENT_WEIGHT +
        tally.activities.coerceAtLeast(0) * SPOTLIGHT_ACTIVITY_WEIGHT +
        tally.checks.coerceAtLeast(0) * SPOTLIGHT_CHECK_WEIGHT

/**
 * Everything the PC actually did this turn, fumbles included.
 *
 * Unlike [spotlightScore] this counts effort rather than achievement, because it is what the
 * "never stopped working (N actions)" line reports and a botched check still cost the character
 * their turn.
 */
fun spotlightActionCount(tally: TurnTally): Int =
    tally.checks.coerceAtLeast(0) +
        tally.crits.coerceAtLeast(0) +
        tally.critFails.coerceAtLeast(0) +
        tally.activities.coerceAtLeast(0) +
        tally.events.coerceAtLeast(0)

/** The chosen Spotlight subject, its flavour and the number that line renders. */
data class SpotlightPick(
    val actorUuid: String,
    val actorName: String?,
    val kind: SpotlightKind,
    val count: Int,
)

/**
 * Picks the single Spotlight subject for a turn, or null when nobody did anything.
 *
 * Order of decisions, all of which are load-bearing:
 *
 * 1. Rows with no actions at all are dropped first, so a PC listed with an all-zero tally cannot be
 *    crowned busiest with a count of zero.
 * 2. The top scorer is the maximum of [spotlightScore], tie-broken by ascending `actorUuid`. The
 *    tie-break exists purely for determinism: the same turn re-rendered must name the same PC, and
 *    input order is not stable across a save round-trip.
 * 3. [SpotlightKind.BLUNDERER] is then considered, and it is RATIONED rather than scored. It needs
 *    [BLUNDERER_CRIT_FAIL_THRESHOLD] fumbles AND a PC who is not the turn's top scorer — nobody
 *    gets called a fool for the turn they carried. That tone rule lives here, in the pure function,
 *    rather than in whoever writes the i18n string. When several PCs qualify the unluckiest wins,
 *    tie-broken by uuid again. [allowBlunderer] turns the whole line off for tables that would
 *    rather not name anyone at all.
 * 4. Otherwise the top scorer's flavour is whichever weighted component STRICTLY dominates the rest
 *    of their score: crits, then events, then the generic busiest line. Strictly, because "on fire
 *    — 1 critical success!" for a PC who merely rolled one crit among many plain checks reads as a
 *    tin ear rather than as praise.
 *
 * [SpotlightKind.DIPLOMAT] is never returned here: a turn tally carries no category breakdown at
 * all (the persisted row is keyed by actor alone), so nothing in this input can distinguish a
 * diplomat from any other busy PC. The arm exists for a caller that has category context to hand.
 *
 * Fully deterministic: no clock, no randomness, no dependence on input order beyond the explicit
 * tie-breaks.
 */
fun spotlightOfTheTurn(
    tallies: List<TurnTally>,
    allowBlunderer: Boolean = true,
): SpotlightPick? {
    val active = tallies.filter { spotlightActionCount(it) > 0 }
    val top = active
        .sortedWith(compareByDescending<TurnTally> { spotlightScore(it) }.thenBy { it.actorUuid })
        .firstOrNull()
        ?: return null
    if (allowBlunderer) {
        val blunderer = active
            .filter { it.actorUuid != top.actorUuid && it.critFails >= BLUNDERER_CRIT_FAIL_THRESHOLD }
            .sortedWith(compareByDescending<TurnTally> { it.critFails }.thenBy { it.actorUuid })
            .firstOrNull()
        if (blunderer != null) {
            return SpotlightPick(
                actorUuid = blunderer.actorUuid,
                actorName = blunderer.actorName,
                kind = SpotlightKind.BLUNDERER,
                count = blunderer.critFails,
            )
        }
    }
    val crits = top.crits.coerceAtLeast(0)
    val events = top.events.coerceAtLeast(0)
    val critWeight = crits * SPOTLIGHT_CRIT_WEIGHT
    val eventWeight = events * SPOTLIGHT_EVENT_WEIGHT
    val restWeight = spotlightScore(top) - critWeight - eventWeight
    return when {
        critWeight > eventWeight && critWeight > restWeight ->
            SpotlightPick(top.actorUuid, top.actorName, SpotlightKind.CRIT_STAR, crits)
        eventWeight > critWeight && eventWeight > restWeight ->
            SpotlightPick(top.actorUuid, top.actorName, SpotlightKind.EVENT_HERO, events)
        else ->
            SpotlightPick(top.actorUuid, top.actorName, SpotlightKind.BUSIEST, spotlightActionCount(top))
    }
}

/**
 * The twelve authored epithets (plan Appendix). This is a FIXED table, not a parameter and not a
 * data file: the plan's open question about catalog ownership was decided in favour of a Kotlin
 * table so the conditions can be real expressions over [EpithetContext] rather than a stringly
 * mini-language nobody can typo-check.
 *
 * Only ids, conditions, perks and faction names live here. Labels and hints are i18n keys
 * (`kingdom.renown.epithet.*`) resolved by a literal `when` in the jsMain layer -- commonMain
 * carries no prose.
 *
 * Entries 2, 6 and 12 key off free-text `RawGroup.name`, so a campaign that renamed those
 * factions simply never earns them; that is the documented consequence of matching on names the
 * GM types, not a bug to paper over.
 */
val EPITHET_CATALOG: List<EpithetDefinition> = listOf(
    EpithetDefinition(
        id = "bridgeBuilder",
        condition = { ctx, _ ->
            ctx.populace >= 30 && (ctx.categoryActivities[DeedCategory.DIPLOMATIC] ?: 0) >= 5
        },
        perk = RenownPerk.Invitation(),
    ),
    EpithetDefinition(
        id = "restovsHammer",
        condition = { ctx, names -> (ctx.bestFactionRenown(names) ?: Int.MIN_VALUE) >= 25 },
        factionNames = listOf("Restov", "Swordlords"),
    ),
    EpithetDefinition(
        id = "theUntiring",
        condition = { ctx, _ -> ctx.activities >= 20 },
        perk = RenownPerk.PurchaseAccess(1),
    ),
    EpithetDefinition(
        id = "coinCounter",
        condition = { ctx, _ -> (ctx.roleSuccesses[Leader.TREASURER] ?: 0) >= 15 },
        perk = RenownPerk.PurchaseAccess(2),
    ),
    EpithetDefinition(
        id = "theIronhand",
        // feared, not loved: high standing REACHED THROUGH failure, so both halves are required
        condition = { ctx, _ -> ctx.populace >= 40 && ctx.critFails >= 3 },
    ),
    EpithetDefinition(
        id = "feyFriend",
        condition = { ctx, names -> (ctx.bestFactionRenown(names) ?: Int.MIN_VALUE) >= 25 },
        perk = RenownPerk.Invitation("Narlmarches"),
        factionNames = listOf("Narlmarches"),
    ),
    EpithetDefinition(
        id = "theUnshaken",
        condition = { ctx, _ -> ctx.eventsResolved >= 5 },
    ),
    EpithetDefinition(
        id = "wardenOfTheMarches",
        condition = { ctx, _ -> (ctx.roleSuccesses[Leader.WARDEN] ?: 0) >= 10 },
    ),
    EpithetDefinition(
        id = "theSilverTongue",
        condition = { ctx, _ -> (ctx.roleCrits[Leader.EMISSARY] ?: 0) >= 8 },
        perk = RenownPerk.Invitation(),
    ),
    EpithetDefinition(
        id = "peoplesChampion",
        condition = { ctx, _ -> ctx.populace >= 50 },
        perk = RenownPerk.PurchaseAccess(2),
    ),
    EpithetDefinition(
        id = "theKingmaker",
        condition = { ctx, _ -> ctx.populace >= 75 && ctx.holdsRulerRole },
    ),
    EpithetDefinition(
        id = "scourgeOfPitax",
        // the NEMESIS entry: worst, not best -- a PC hated by Pitax earns it, and a PC who never
        // met Pitax must not, which is why worstFactionRenown returns null rather than 0
        condition = { ctx, names -> (ctx.worstFactionRenown(names) ?: Int.MAX_VALUE) <= -25 },
        factionNames = listOf("Pitax"),
    ),
)

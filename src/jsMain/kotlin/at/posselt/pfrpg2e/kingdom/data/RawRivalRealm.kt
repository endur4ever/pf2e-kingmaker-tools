package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.data.kingdom.RivalGrowthProfile
import at.posselt.pfrpg2e.data.kingdom.RivalHeadlinePool
import at.posselt.pfrpg2e.data.kingdom.RivalStandingRow
import at.posselt.pfrpg2e.data.kingdom.StatGrowth
import kotlinx.js.JsPlainObject

/** `growthMode` value for the flat-accrual growth that ships now. Also the value assumed when the
 *  stored string is absent or unrecognised, so an unknown mode degrades to normal growth. */
const val RIVAL_GROWTH_MODE_FLAT = "flat"

/** `growthMode` value reserved for the parent Faction Agenda Engine (plan section 3.6). Flat
 *  accrual is suppressed for a realm in this mode; until the parent lands the branch is dormant. */
const val RIVAL_GROWTH_MODE_AGENDA_DRIVEN = "agenda-driven"

/**
 * Persisted shape of one rival realm
 * (`docs/plans/2026-07-09-plan-rival-realms.md` section 2.1).
 *
 * A rival realm is a SCOREBOARD row, not a simulated kingdom: three integers the table can watch
 * creep upward, plus the GM dials that move them. Rows live in a top-level `KingdomData.rivalRealms`
 * array rather than nested on `RawGroup` (section 2.2), because a kingdom has many groups and only
 * two to four rival realms, and `RawGroup` is read on the trade and caravan hot paths.
 *
 * [factionRef] is a SOFT foreign key to `RawGroup.name`, the same by-name link
 * `RawWarThreat.enemyFactionName` and `RawCaravan.partnerName` already use. There is no cascade: if
 * the GM renames or deletes the group the row survives, the UI shows an unlinked hint, and
 * `atWar` / standing lookups fall back to neutral.
 *
 * EVERY field is nullable, [id] and [factionRef] included. A Foundry flag is not a typed record:
 * a row can arrive from an older build, from a hand-edited world, or half-written by an update that
 * failed midway, and a Kotlin non-null `Int` backed by `undefined` reads as a silent NaN that
 * poisons arithmetic instead of raising anything catchable. Nullable everywhere plus defaulting at
 * the converter is the only shape where a damaged row costs ONE row instead of the whole End Turn
 * tick, which is exactly what [toModel] enforces by returning null for a row with no identity.
 *
 * Enum-shaped fields ([growthMode], [headlinePool], [growthProfile]) persist as STRINGS and are
 * converted at this boundary. Every one of them falls back rather than throwing: an unrecognised
 * pool falls through to `poolFor`, an unrecognised mode to flat growth, an unknown profile id to
 * the per-stat dials and then to dormant.
 */
@JsPlainObject
external interface RawRivalRealm {
    /** Stable id (UUID) so UI edits and offers target the right realm. Null only on a damaged row,
     *  which [toModel] drops. */
    var id: String?

    /** Soft foreign key to `RawGroup.name` -- the faction this realm IS. */
    var factionRef: String?

    /** Realm size, parallels `kingdom.size`. Null reads as 0. */
    var size: Int?

    /** Fame / infamy score. Null reads as 0. */
    var fame: Int?

    /** Number of standing armies. A SCALAR headcount, never deployable army tokens. Null reads as 0. */
    var armyCount: Int?

    /** Per-turn size increment, fractional-friendly: 0.5 is one hex every two turns. Null means
     *  "no GM override", so the named growth profile decides. */
    var sizeGrowthPerTurn: Double?

    /** Per-turn fame increment. Null means "no GM override". */
    var fameGrowthPerTurn: Double?

    /** Per-turn army increment. Null means "no GM override". */
    var armyGrowthPerTurn: Double?

    /** Carried fractional size progress, so fractional dials are lossless across turns. Null reads
     *  as 0.0; `growStat` sanitizes anything corrupt (NaN, negative) back to zero. */
    var sizeAccrual: Double?

    /** Carried fractional fame progress. Null reads as 0.0. */
    var fameAccrual: Double?

    /** Carried fractional army progress. Null reads as 0.0. */
    var armyAccrual: Double?

    /** Chapter preset id from `data/rival-growth-profiles/`, e.g. "pitax-wartime" (section 3.4).
     *  Resolves the three per-turn rates; the per-stat dials above override it individually. An id
     *  no longer shipped resolves to nothing and the realm falls back to its dials, then dormant. */
    var growthProfile: String?

    /** [RIVAL_GROWTH_MODE_FLAT] (default) or [RIVAL_GROWTH_MODE_AGENDA_DRIVEN]. */
    var growthMode: String?

    /** GM kill-switch: true means this realm does not grow this turn. Pausing preserves the carried
     *  accruals, so a realm paused mid-fraction resumes exactly where it stopped. */
    var pauseGrowth: Boolean?

    /** Place-name token interpolated into headlines, e.g. "the Branthlend Mountains". */
    var borderRegion: String?

    /** [armyCount] at which a war-threat OFFER fires while the linked group is at war. Null
     *  disables the offer entirely. */
    var warArmyThreshold: Int?

    /** Idempotency guard: the [armyCount] at which the last war offer fired. The next offer is only
     *  allowed once the army has grown PAST this, so a re-tick over the same turn cannot re-offer. */
    var lastWarOfferArmyCount: Int?

    /** Optional GM override for the headline template pool, spelled as a [RivalHeadlinePool] value.
     *  Null (or unrecognised) means derive the pool from the stat that grew. */
    var headlinePool: String?
}

/**
 * True only for an exact [RIVAL_GROWTH_MODE_AGENDA_DRIVEN] match.
 *
 * Written as a positive test against the non-default value on purpose: flat growth is what every
 * realm does today, so absent, empty, misspelled and legacy values must all land on flat rather
 * than accidentally suppressing a realm's growth forever with no visible cause.
 */
fun RawRivalRealm.isAgendaDriven(): Boolean = growthMode == RIVAL_GROWTH_MODE_AGENDA_DRIVEN

/** The GM kill-switch, read so that an absent flag means "growing". */
fun RawRivalRealm.isGrowthPaused(): Boolean = pauseGrowth == true

/**
 * The GM's headline-pool override, or null when there is none.
 *
 * Null for an unrecognised stored value as well as an absent one, because both mean the same thing
 * to the caller: fall back to `poolFor(stat, atWar)`. A typo in the flag costs a flavour choice,
 * never a thrown exception inside the turn tick.
 */
fun RawRivalRealm.headlinePoolOverride(): RivalHeadlinePool? = RivalHeadlinePool.fromValue(headlinePool)

/**
 * The realm's resolved per-turn rates, following the plan's resolution order (section 3.4):
 * a per-stat GM dial wins, else the [named] profile's rate, else dormant.
 *
 * Resolution is PER STAT, not per realm: a GM who overrides only `armyGrowthPerTurn` keeps the
 * profile's size and fame rates. Resolving the whole profile as a unit would silently zero the two
 * stats the GM did not touch.
 *
 * Rates are passed through untouched, including negatives and non-finite values, because `growStat`
 * already ignores everything that is not a positive finite rate. Filtering here as well would
 * duplicate that contract in a second place where it could drift.
 */
fun RawRivalRealm.toGrowthProfile(named: RivalGrowthProfile? = null): RivalGrowthProfile {
    val base = named ?: RivalGrowthProfile()
    return RivalGrowthProfile(
        sizePerTurn = sizeGrowthPerTurn ?: base.sizePerTurn,
        famePerTurn = fameGrowthPerTurn ?: base.famePerTurn,
        armyPerTurn = armyGrowthPerTurn ?: base.armyPerTurn,
    )
}

/**
 * [toGrowthProfile] against the shipped preset table, keyed by [RawRivalRealm.growthProfile].
 *
 * A profile id that is absent from [profiles] -- a preset removed between builds, a hand-typed id --
 * resolves to no named profile at all, which leaves the realm on its own dials and then dormant.
 * A dormant rival is a visible, harmless failure; a thrown lookup would abort the tick.
 */
fun RawRivalRealm.resolveGrowthProfile(profiles: Map<String, RivalGrowthProfile>): RivalGrowthProfile =
    toGrowthProfile(growthProfile?.let { profiles[it] })

/**
 * The standings row for this realm, or null if the row has no identity.
 *
 * A row missing [RawRivalRealm.id] or [RawRivalRealm.factionRef] is DROPPED rather than rendered:
 * without an id the sheet's edit and delete buttons would target nothing, and without a faction ref
 * there is no group to read `atWar` or a name from. Every other field defaults, so a row that lost
 * only its stats shows as a realm at zero instead of vanishing.
 *
 * [label] is passed in rather than read from the row because the display name belongs to the linked
 * `RawGroup`; it falls back to the faction ref so an unlinked realm still names itself.
 *
 * `score` is deliberately left to [RivalStandingRow]'s own default so the rival rows cannot be built
 * with a score inconsistent with their stats. The PLAYER row is not built here at all -- it is
 * assembled by the adapter from kingdom fields (plan section 9 question 1) and is the only row that
 * may carry a derived score.
 */
fun RawRivalRealm.toModel(label: String? = null): RivalStandingRow? {
    val ref = factionRef?.takeIf { it.isNotBlank() } ?: return null
    if (id?.isNotBlank() != true) return null
    return RivalStandingRow(
        label = label?.takeIf { it.isNotBlank() } ?: ref,
        size = size ?: 0,
        fame = fame ?: 0,
        armyCount = armyCount ?: 0,
        isPlayer = false,
    )
}

/**
 * Builds the persisted row for a NEWLY tracked realm from a standings row.
 *
 * LOSSY BY DESIGN, and only safe for creation: [RivalStandingRow] is a view projection that carries
 * no dials, no accruals and no war-offer state, so using this to write a ticked realm back would
 * silently erase every GM setting on it. Write-back goes through [withGrowth] and
 * [withWarOfferRecorded], which copy the existing row.
 *
 * Never call this on the player row. The player is a computed row in the standings table, and
 * persisting it would put the players' own kingdom into the rival array as its own competitor.
 *
 * The new row starts with no dials at all: absent means dormant, so a realm added mid-session sits
 * still until the GM chooses a preset or dials it, instead of quietly growing behind their back.
 */
fun RivalStandingRow.toRaw(
    id: String,
    factionRef: String,
    growthProfile: String? = null,
): RawRivalRealm =
    RawRivalRealm(
        id = id,
        factionRef = factionRef,
        size = size,
        fame = fame,
        armyCount = armyCount,
        growthProfile = growthProfile,
        growthMode = RIVAL_GROWTH_MODE_FLAT,
    )

/**
 * Writes one turn of growth back onto the row: the three new stat values and the three carried
 * accruals, everything else preserved.
 *
 * Copies the original instead of rebuilding it so the GM's dials, thresholds, border region and any
 * field a later build adds all survive a tick untouched. Carrying the accruals back is what makes
 * fractional dials lossless: dropping them would restart every fraction each turn and a 0.33 dial
 * would never award a single point.
 */
fun RawRivalRealm.withGrowth(
    sizeGrowth: StatGrowth,
    fameGrowth: StatGrowth,
    armyGrowth: StatGrowth,
): RawRivalRealm =
    RawRivalRealm.copy(
        this,
        size = sizeGrowth.value,
        sizeAccrual = sizeGrowth.accrual,
        fame = fameGrowth.value,
        fameAccrual = fameGrowth.accrual,
        armyCount = armyGrowth.value,
        armyAccrual = armyGrowth.accrual,
    )

/**
 * Stamps the army count a war-threat offer just fired at, so the same offer cannot be posted again
 * until the rival's army grows past it.
 *
 * Stored as the count rather than a boolean because the guard has to survive a rival that keeps
 * mobilizing: a flag would either latch forever after the first offer or re-fire every single turn
 * the realm stayed above its threshold.
 */
fun RawRivalRealm.withWarOfferRecorded(atArmyCount: Int): RawRivalRealm =
    RawRivalRealm.copy(this, lastWarOfferArmyCount = atArmyCount)

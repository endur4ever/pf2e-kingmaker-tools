package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.RivalGrowthProfile
import at.posselt.pfrpg2e.data.kingdom.RivalHeadlinePool
import at.posselt.pfrpg2e.data.kingdom.RivalStat
import at.posselt.pfrpg2e.data.kingdom.growStat
import at.posselt.pfrpg2e.data.kingdom.headlineTemplateIndex
import at.posselt.pfrpg2e.data.kingdom.poolFor
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.data.RawRivalRealm
import at.posselt.pfrpg2e.utils.t
import js.objects.recordOf

/**
 * Monthly rival-realm growth
 * (`docs/plans/2026-07-09-plan-rival-realms.md` SS3.7). Runs inside the End Turn tick, never on
 * the daily clock: rival SCALE is a kingdom-scale quantity that has to move in lockstep with the
 * player's own size and level, and a rival growing daily against a player growing monthly would
 * make the standings jitter for no reason.
 *
 * Pure by delegation -- every arithmetic decision lives in commonMain `RivalRealms.kt`, so a
 * preview and a commit of the same turn produce byte-identical output. This layer only reads the
 * Raw rows, resolves the profile, and turns the results into headline data.
 */

/** One realm's growth this tick: the updated row plus whichever stat actually gained a point. */
data class RivalGrowthResult(
    val realm: RawRivalRealm,
    val move: RivalMove?,
)

/**
 * A headline-worthy change. [pool] and [templateIndex] pick the prose variant deterministically,
 * so the same turn always tells the same story -- the module's preview/commit parity rule.
 */
data class RivalMove(
    val factionRef: String,
    val stat: RivalStat,
    val amount: Int,
    val pool: RivalHeadlinePool,
    val templateIndex: Int,
    val newValue: Int,
    /** The realm's border region, carried so the headline can say WHERE ("near {place}"). */
    val place: String? = null,
)

/** Profile resolution: an explicit per-realm rate overrides the preset, and an unknown preset id
 *  falls back to no growth rather than throwing -- a typo must not take down the End Turn tick. */
private fun RawRivalRealm.resolveProfile(profiles: Map<String, RivalGrowthProfile>): RivalGrowthProfile {
    val preset = growthProfile?.let { profiles[it] }
    return RivalGrowthProfile(
        sizePerTurn = sizeGrowthPerTurn ?: preset?.sizePerTurn ?: 0.0,
        famePerTurn = fameGrowthPerTurn ?: preset?.famePerTurn ?: 0.0,
        armyPerTurn = armyGrowthPerTurn ?: preset?.armyPerTurn ?: 0.0,
    )
}

/**
 * Advance one realm by one turn.
 *
 * At most ONE headline per realm per turn, chosen size > army > fame: a turn that grew three
 * stats is still one beat in the gazette, and size is the change a table actually feels.
 * `agenda-driven` realms skip flat accrual entirely (SS3.6) -- their stats come from the Faction
 * Agenda engine when that lands, and until then the branch is dormant rather than double-growing.
 */
fun growRivalRealm(
    realm: RawRivalRealm,
    profiles: Map<String, RivalGrowthProfile>,
    turn: Int,
    atWar: Boolean,
): RivalGrowthResult {
    val paused = realm.pauseGrowth == true || realm.growthMode == "agenda-driven"
    val profile = realm.resolveProfile(profiles)

    val size = growStat(realm.size ?: 0, realm.sizeAccrual ?: 0.0, profile.sizePerTurn, paused)
    val fame = growStat(realm.fame ?: 0, realm.fameAccrual ?: 0.0, profile.famePerTurn, paused)
    val army = growStat(realm.armyCount ?: 0, realm.armyAccrual ?: 0.0, profile.armyPerTurn, paused)

    val updated = RawRivalRealm.copy(
        realm,
        size = size.value,
        sizeAccrual = size.accrual,
        fame = fame.value,
        fameAccrual = fame.accrual,
        armyCount = army.value,
        armyAccrual = army.accrual,
    )

    // A realm with no factionRef has nothing to headline ABOUT -- the reference is what names the
    // rival in the prose -- so it still grows, it just stays out of the gazette.
    val factionRef = realm.factionRef
    val gained: Triple<RivalStat, Int, Int>? = when {
        size.incremented > 0 -> Triple(RivalStat.SIZE, size.incremented, size.value)
        army.incremented > 0 -> Triple(RivalStat.ARMY, army.incremented, army.value)
        fame.incremented > 0 -> Triple(RivalStat.FAME, fame.incremented, fame.value)
        else -> null
    }
    val move = if (factionRef != null && gained != null) {
        val pool = poolFor(gained.first, atWar)
        RivalMove(
            factionRef = factionRef,
            stat = gained.first,
            amount = gained.second,
            pool = pool,
            templateIndex = headlineTemplateIndex(turn, factionRef, gained.first, pool.poolSize),
            newValue = gained.third,
            place = realm.borderRegion,
        )
    } else {
        null
    }
    return RivalGrowthResult(realm = updated, move = move)
}

/**
 * Advance every realm. Order is preserved so the persisted array does not churn, and the moves
 * come back in the same order for a stable gazette line.
 *
 * [groups] supplies each realm's war state by soft name match -- the link is `factionRef` ->
 * `RawGroup.name`, deliberately not an id, so a renamed group simply reads as not-at-war rather
 * than breaking the tick.
 */
fun advanceAllRivals(
    realms: Array<RawRivalRealm>?,
    groups: Array<RawGroup>?,
    profiles: Map<String, RivalGrowthProfile>,
    turn: Int,
): Pair<Array<RawRivalRealm>, List<RivalMove>> {
    val rows = realms ?: emptyArray()
    if (rows.isEmpty()) return emptyArray<RawRivalRealm>() to emptyList()
    val warByName = (groups ?: emptyArray())
        .associate { it.name.trim().lowercase() to it.atWar }
    val moves = mutableListOf<RivalMove>()
    val updated = rows.map { realm ->
        val atWar = realm.factionRef
            ?.let { warByName[it.trim().lowercase()] }
            ?: false
        val result = growRivalRealm(realm, profiles, turn, atWar)
        result.move?.let { moves.add(it) }
        result.realm
    }.toTypedArray()
    return updated to moves
}

/**
 * Prose for one move. The key is assembled from an ENUM's value plus a bounded index, and every
 * possible key is spelled out literally in the `when` below so `check_i18n_keys.py` can see them
 * -- a `t("prefix.$pool$index")` would be invisible to the guard and would ship as a raw key.
 */
fun localizeRivalHeadline(move: RivalMove): String {
    val data = recordOf(
        "rival" to move.factionRef,
        "n" to move.amount.toString(),
        "place" to (move.place?.takeIf { it.isNotBlank() }
            ?: t("kingdom.rivalRealms.borderFallback")),
    )
    return when (move.pool) {
        RivalHeadlinePool.EXPAND -> when (move.templateIndex) {
            0 -> t("kingdom.rivalRealms.headline.expand1", data)
            1 -> t("kingdom.rivalRealms.headline.expand2", data)
            else -> t("kingdom.rivalRealms.headline.expand3", data)
        }
        RivalHeadlinePool.FAME -> when (move.templateIndex) {
            0 -> t("kingdom.rivalRealms.headline.fame1", data)
            else -> t("kingdom.rivalRealms.headline.fame2", data)
        }
        RivalHeadlinePool.ARMY -> when (move.templateIndex) {
            0 -> t("kingdom.rivalRealms.headline.army1", data)
            else -> t("kingdom.rivalRealms.headline.army2", data)
        }
        RivalHeadlinePool.ARMY_WAR -> when (move.templateIndex) {
            0 -> t("kingdom.rivalRealms.headline.armyWar1", data)
            else -> t("kingdom.rivalRealms.headline.armyWar2", data)
        }
    }
}

package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.data.kingdom.RivalGrowthProfile
import at.posselt.pfrpg2e.data.kingdom.RivalStandingRow
import at.posselt.pfrpg2e.data.kingdom.rankStandings
import at.posselt.pfrpg2e.data.kingdom.rivalPowerScore
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.data.RawRivalRealm
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface RivalStandingRowContext {
    val id: String?            // null for the player row
    val label: String
    val score: Int
    val size: Int
    val fame: Int
    val armyCount: Int
    val rank: Int
    val isPlayer: Boolean
    val linked: Boolean        // factionRef resolves to a real group
    val growthSummary: String? // GM-only; null for players
}

@Suppress("unused")
@JsPlainObject
external interface RivalRealmsContext {
    val rows: Array<RivalStandingRowContext>
    /** False when no rivals are tracked. The player row makes rows.length >= 1 ALWAYS, so a
     *  template gating its empty-state on rows.length would never show it. */
    val hasRivals: Boolean
    val isGM: Boolean
}

/** "0.5" not "0.5000000000000001": trim binary noise before it reaches a table cell. */
private fun rate(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100
    val asInt = rounded.toInt()
    return if (rounded == asInt.toDouble()) asInt.toString() else rounded.toString()
}

/**
 * The GM-only growth cell, e.g. "Size +0.33/t · Armies +0.5/t (pitax-wartime)". Rates come from
 * the same explicit-override-beats-preset resolution the engine uses, so the summary shows what
 * will actually happen next End Turn, not just what the dial is set to.
 */
private fun growthSummary(realm: RawRivalRealm, profiles: Map<String, RivalGrowthProfile>): String {
    val preset = realm.growthProfile?.let { profiles[it] }
    val parts = listOfNotNull(
        (realm.sizeGrowthPerTurn ?: preset?.sizePerTurn ?: 0.0)
            .takeIf { it > 0.0 }?.let { "Size +${rate(it)}/t" },
        (realm.fameGrowthPerTurn ?: preset?.famePerTurn ?: 0.0)
            .takeIf { it > 0.0 }?.let { "Fame +${rate(it)}/t" },
        (realm.armyGrowthPerTurn ?: preset?.armyPerTurn ?: 0.0)
            .takeIf { it > 0.0 }?.let { "Armies +${rate(it)}/t" },
    )
    val dials = if (parts.isEmpty()) "—" else parts.joinToString(" · ")
    return realm.growthProfile?.let { "$dials ($it)" } ?: dials
}

/**
 * Ranks the players' kingdom against its rivals and shapes the rows for the standings table.
 *
 * The player row scores WITHOUT an army term: the kingdom has no single army count (plan section 9
 * question 1 leaves the derivation open and itself offers "rank on size + fame" as the fallback),
 * and feeding a guessed count into the composite would move the player's rank on a number nobody
 * chose. The Armies cell shows 0 until that question is answered.
 *
 * [linked] uses the same trim+lowercase soft match the engine uses for the war split, so the table
 * flags exactly the rows whose growth has silently degraded to peacetime.
 */
fun buildRivalRealmsContext(
    rivals: Array<RawRivalRealm>?,
    groups: Array<RawGroup>,
    profiles: Map<String, RivalGrowthProfile>,
    playerLabel: String,
    playerSize: Int,
    playerFame: Int,
    isGM: Boolean,
): RivalRealmsContext {
    val rows = rivals ?: emptyArray()
    val groupNames = groups.map { it.name.trim().lowercase() }.toSet()
    // sources is index-aligned with pure (null = the player row); after ranking reorders the pure
    // rows, each ranked row is matched back to the first unconsumed equal input. Matching by a key
    // like factionRef would collide when two rivals share a name (or both are unlinked) and hand
    // the edit/delete buttons the OTHER row's id; rank-stripped equality plus first-unconsumed is
    // exact even for identical twins, because rankStandings' sort is stable
    val sources: List<RawRivalRealm?> = rows.toList() + listOf(null)
    val pure = rows.map { realm ->
        RivalStandingRow(
            label = realm.factionRef ?: "?",
            size = realm.size ?: 0,
            fame = realm.fame ?: 0,
            armyCount = realm.armyCount ?: 0,
        )
    } + RivalStandingRow(
        label = playerLabel,
        size = playerSize,
        fame = playerFame,
        armyCount = 0,
        score = rivalPowerScore(playerSize, playerFame, 0),
        isPlayer = true,
    )
    val unconsumed = pure.indices.toMutableList()
    return RivalRealmsContext(
        isGM = isGM,
        hasRivals = rows.isNotEmpty(),
        rows = rankStandings(pure).map { row ->
            val sourceIndex = unconsumed.first { pure[it] == row.copy(rank = pure[it].rank) }
            unconsumed.remove(sourceIndex)
            val realm = sources[sourceIndex]
            RivalStandingRowContext(
                id = realm?.id,
                label = row.label,
                score = row.score,
                size = row.size,
                fame = row.fame,
                armyCount = row.armyCount,
                rank = row.rank,
                isPlayer = row.isPlayer,
                linked = row.isPlayer ||
                        realm?.factionRef?.trim()?.lowercase()?.let { it in groupNames } == true,
                growthSummary = if (isGM && realm != null) growthSummary(realm, profiles) else null,
            )
        }.toTypedArray(),
    )
}

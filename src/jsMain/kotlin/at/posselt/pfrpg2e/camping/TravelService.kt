package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.regions.Terrain
import at.posselt.pfrpg2e.fromCamelCase
import kotlin.math.roundToLong

/**
 * Prices a chosen route in Travel activities — the unit hexploration actually uses.
 *
 * This used to invent its own scale: a flat 1 hour per hex, terrain as additive hours, and the
 * party's Speed in FEET divided by 24 (which is miles-per-day at Speed 30 in PF2e's travel table,
 * so the units did not even agree). At Speed 25 that priced a plains hex at 0.96 h when the rules
 * make it a full Travel activity — roughly five times too fast, and inconsistent with the
 * hexploration counter on the same sheet.
 *
 * Now: each hex entered costs 1-3 Travel activities (see [travelActivityCost]), and the duration
 * is that count times the length of one hexploration activity, which the sheet derives from the
 * party's activities per day. Party Speed therefore enters exactly where the rules put it —
 * activities per day — instead of through an invented multiplier.
 *
 * Travel is only one hexploration activity. Reconnoitering a hex costs the same again and is a
 * SEPARATE activity, so it is deliberately not part of a route estimate.
 */
class TravelService(
    private val hexContents: Map<String, HexContent>,
    private val weatherModifier: Double = 1.0,
    /**
     * Extra Travel degrees for a hex with a river and no bridge. RAW has no crossing cost, so this
     * comes from the `travelCostRiverNoBridgeAdditional` setting and is 0 unless the GM opts in.
     */
    private val riverNoBridgeExtraDegrees: Int = 0,
    /** Hex keys holding a settlement with Paved Streets, when that house rule is enabled. */
    private val pavedSettlementHexKeys: Set<String> = emptySet(),
) {
    /**
     * @param path hex keys start..goal inclusive; the starting hex is not charged, since the party
     *   is already standing in it
     * @param secondsPerActivity length of one hexploration activity (the 8-hour exploration day
     *   divided by the party's activities per day, already scaled for hex size)
     */
    fun calculateRoute(
        path: List<String>,
        secondsPerActivity: Double,
        getTerrain: ((String) -> Terrain?)? = null,
        getFeatures: ((String) -> List<String>)? = null,
    ): TravelRoute {
        val resolveTerrain = getTerrain ?: { hexKey ->
            try {
                val hexObj = com.foundryvtt.kingmaker.kingmaker.region.hexes.find { it.key.toString() == hexKey }
                hexObj?.zone?.terrain?.let { fromCamelCase<Terrain>(it) }
            } catch (e: Throwable) {
                null
            }
        }

        val resolveFeatures = getFeatures ?: { hexKey ->
            try {
                kingmakerHexFeatures(hexKey)
            } catch (e: Throwable) {
                emptyList()
            }
        }

        var totalActivities = 0
        for (hexKey in path.drop(1)) {
            val features = resolveFeatures(hexKey)
            val unbridgedRiver = "river" in features && "bridge" !in features
            totalActivities += travelActivityCost(
                difficulty = terrainDifficulty(resolveTerrain(hexKey)),
                hasRoad = "road" in features,
                riverExtraDegrees = if (unbridgedRiver) riverNoBridgeExtraDegrees else 0,
                extraDegrees = hexContents[hexKey]?.travelModifier ?: 0,
                pavedSettlement = hexKey in pavedSettlementHexKeys,
            )
        }

        // Weather stretches the day rather than changing what the terrain costs, so it scales the
        // duration and leaves the activity count — which is what the rules count — intact.
        val seconds = totalActivities * secondsPerActivity * weatherModifier

        return TravelRoute(
            startHex = path.firstOrNull() ?: "",
            endHex = path.lastOrNull() ?: "",
            path = path.toList(),
            totalCost = totalActivities.toDouble(),
            estimatedDurationSeconds = seconds.roundToLong(),
        )
    }
}

private fun kingmakerHexFeatures(hexKey: String): List<String> =
    com.foundryvtt.kingmaker.kingmaker.state.hexes[hexKey]
        ?.features
        ?.mapNotNull { it.type }
        ?: emptyList()

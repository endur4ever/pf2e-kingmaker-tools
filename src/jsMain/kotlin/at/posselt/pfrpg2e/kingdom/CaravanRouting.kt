package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.routing.TravelProvider
import at.posselt.pfrpg2e.camping.routing.TravelPlan
import at.posselt.pfrpg2e.camping.routing.TravelRoute
import at.posselt.pfrpg2e.camping.routing.TravelRouter
import at.posselt.pfrpg2e.data.regions.Terrain
import kotlin.math.ceil

/**
 * Caravan routing for the commodity market. Reuses the camping route planner
 * ([TravelRouter], Dijkstra over the hex grid) to find the shortest realm-map route between
 * two hexes, and converts that route's cost into whole kingdom turns of travel.
 *
 * The grid topology comes from a [TravelProvider] — `KingmakerHexGridProvider` in production, or a
 * hand-built fake in tests — so the routing/ETA logic here is fully unit-testable.
 */

/** How much route cost a caravan covers in one kingdom turn. Pacing knob; ~3 hexes per turn. */
const val CARAVAN_COST_PER_TURN: Double = 3.0

/**
 * Extra multiplier on the shipping fee when a shipment originates from or is delivered to the party's
 * current map position (a moving target) instead of a fixed settlement/faction hub. The convenience of
 * a caravan chasing the party down costs a premium.
 */
const val CARAVAN_PARTY_SURCHARGE: Double = 1.5

/**
 * Maximum total Bulk a caravan of each size can carry in one shipment. Light caravans are fast and cheap
 * but small; heavy caravans are slow and pricey but haul a lot. Total shipment Bulk is
 * `parseBulk(itemBulk) * quantity`.
 */
const val CARAVAN_LIGHT_BULK_CAPACITY: Int = 20
const val CARAVAN_MEDIUM_BULK_CAPACITY: Int = 60
const val CARAVAN_HEAVY_BULK_CAPACITY: Int = 150

/** Max Bulk a caravan of [caravanType] ("light"|"medium"|"heavy") can carry; unknown types use medium. */
fun caravanBulkCapacity(caravanType: String): Int = when (caravanType) {
    "light" -> CARAVAN_LIGHT_BULK_CAPACITY
    "heavy" -> CARAVAN_HEAVY_BULK_CAPACITY
    else -> CARAVAN_MEDIUM_BULK_CAPACITY
}

/** Converts a route cost into whole kingdom turns of travel (minimum 1). */
fun caravanEtaTurns(routeCost: Double, costPerTurn: Double = CARAVAN_COST_PER_TURN): Int =
    if (routeCost <= 0.0) 1 else maxOf(1, ceil(routeCost / costPerTurn).toInt())

/**
 * Caravan travel plan with terrain and infrastructure modifiers.
 * Roads reduce cost (RAW-ish: -1 per hex), unbridged rivers add cost (+1), terrain applies multipliers.
 * These values mirror the camping travel system's defaults.
 */
fun caravanTravelPlan(): TravelPlan = TravelPlan(
    partySpeedMultiplier = 1.0,
    terrainModifiers = mapOf(
        Terrain.PLAINS to 0.0,
        Terrain.HILLS to 0.5,
        Terrain.FOREST to 1.0,
        Terrain.MOUNTAIN to 2.0,
        Terrain.SWAMP to 1.5,
        Terrain.DESERT to 1.0,
        Terrain.AQUATIC to 2.0,
        Terrain.URBAN to -0.5,
        Terrain.DUNGEON to 2.0,
    ),
    infrastructureModifiers = mapOf(
        "road" to -1.0,
        "river" to 1.0,
        "bridge" to 0.0,
    ),
    weatherModifier = 1.0,
)

/**
 * Shortest map route between two realm hexes, or null if the destination is unreachable.
 * The underlying planner reports an unreachable destination as an infinite-cost route, so that
 * is normalized to null here.
 */
fun computeCaravanRoute(
    provider: TravelProvider,
    originHexKey: String,
    destHexKey: String,
    plan: TravelPlan = caravanTravelPlan(),
): TravelRoute? = TravelRouter(provider).calculateRoute(originHexKey, destHexKey, plan)
    ?.takeIf { it.totalCost.isFinite() }

/** Convenience: shortest-route ETA in kingdom turns, or null if unreachable. */
fun computeCaravanEtaTurns(
    provider: TravelProvider,
    originHexKey: String,
    destHexKey: String,
    costPerTurn: Double = CARAVAN_COST_PER_TURN,
): Int? = computeCaravanRoute(provider, originHexKey, destHexKey)
    ?.let { caravanEtaTurns(it.totalCost, costPerTurn) }

/** Maps PF2e bulk strings ("L", "-", numeric) to Double values. */
fun parseBulk(bulkStr: String): Double {
    val clean = bulkStr.trim().uppercase()
    if (clean == "L") return 0.1
    if (clean == "-" || clean == "0" || clean.isEmpty()) return 0.0
    return clean.toDoubleOrNull() ?: 1.0
}


package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.HexGridProvider
import at.posselt.pfrpg2e.camping.TravelPlan
import at.posselt.pfrpg2e.camping.TravelRoute
import at.posselt.pfrpg2e.camping.TravelRouteService
import kotlin.math.ceil

/**
 * Caravan routing for the commodity market. Reuses the camping route planner
 * ([TravelRouteService], Dijkstra over the hex grid) to find the shortest realm-map route between
 * two hexes, and converts that route's cost into whole kingdom turns of travel.
 *
 * The grid topology comes from a [HexGridProvider] — `KingmakerHexGridProvider` in production, or a
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
 * Default caravan travel plan: plain per-hex cost, no party/terrain/weather bonuses yet.
 * Road/river cost modifiers are a later refinement (see docs/plans caravan plan).
 */
fun caravanTravelPlan(): TravelPlan = TravelPlan(
    partySpeedMultiplier = 1.0,
    terrainModifiers = emptyMap(),
    infrastructureModifiers = emptyMap(),
    weatherModifier = 1.0,
)

/**
 * Shortest map route between two realm hexes, or null if the destination is unreachable.
 * The underlying planner reports an unreachable destination as an infinite-cost route, so that
 * is normalized to null here.
 */
fun computeCaravanRoute(
    provider: HexGridProvider,
    originHexKey: String,
    destHexKey: String,
    plan: TravelPlan = caravanTravelPlan(),
): TravelRoute? = TravelRouteService(provider).calculateRoute(originHexKey, destHexKey, plan)
    ?.takeIf { it.totalCost.isFinite() }

/** Convenience: shortest-route ETA in kingdom turns, or null if unreachable. */
fun computeCaravanEtaTurns(
    provider: HexGridProvider,
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


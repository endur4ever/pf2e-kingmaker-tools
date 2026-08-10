package at.posselt.pfrpg2e.camping.routing

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.regions.Terrain
import kotlin.math.roundToLong

/**
 * Pure Kotlin travel router using Dijkstra's algorithm.
 * No Foundry dependencies — fully testable in commonMain.
 *
 * The router takes a [TravelProvider] for hex topology/content and a [TravelPlan] for
 * cost modifiers, and returns a [TravelRoute] with the optimal path and total cost.
 */
/**
 * Cost of stepping into hex [to]. Supplied so path SELECTION can use the same model that prices
 * the finished route — otherwise the router optimises one cost function while the UI reports
 * another, and it will happily route around a hex that is actually cheap.
 */
fun interface TravelEdgeCost {
    fun cost(provider: TravelProvider, to: String, plan: TravelPlan): Double
}

class TravelRouter(
    private val provider: TravelProvider,
    /**
     * Defaults to the additive modifier model, which is what kingdom caravan routing is calibrated
     * against (fractional per-terrain costs converted to turns by caravanEtaTurns). The camping
     * route planner passes the hexploration Travel-activity model instead.
     */
    private val edgeCost: TravelEdgeCost? = null,
) {

    /**
     * Calculates the optimal travel route from start to end using Dijkstra's algorithm.
     * Returns null if no path exists.
     */
    fun calculateRoute(
        startKey: String,
        endKey: String,
        plan: TravelPlan = DefaultTravelPlan
    ): TravelRoute? {
        if (startKey == endKey) {
            return TravelRoute(
                startHex = startKey,
                endHex = endKey,
                path = listOf(startKey),
                totalCost = 0.0,
                estimatedDurationSeconds = 0L
            )
        }

        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String?>()
        val queue = mutableSetOf<String>()

        distances[startKey] = 0.0
        queue.add(startKey)

        while (queue.isNotEmpty()) {
            val current = queue.minByOrNull { distances[it] ?: Double.POSITIVE_INFINITY } ?: break
            if (current == endKey) break
            queue.remove(current)

            val currentDist = distances[current] ?: Double.POSITIVE_INFINITY
            if (currentDist == Double.POSITIVE_INFINITY) break

            for (neighbor in provider.getAdjacentHexKeys(current)) {
                if (neighbor !in queue && neighbor != current) {
                    val weight = edgeCost?.cost(provider, neighbor, plan)
                        ?: calculateEdgeWeight(current, neighbor, plan)
                    val alt = currentDist + weight

                    if (alt < (distances[neighbor] ?: Double.POSITIVE_INFINITY)) {
                        distances[neighbor] = alt
                        previous[neighbor] = current
                        queue.add(neighbor)
                    }
                }
            }
        }

        // Reconstruct path
        if ((distances[endKey] ?: Double.POSITIVE_INFINITY) == Double.POSITIVE_INFINITY) return null

        val path = mutableListOf<String>()
        var curr: String? = endKey
        while (curr != null && curr != startKey) {
            path.add(0, curr)
            curr = previous[curr]
        }
        path.add(0, startKey)

        val totalCost = distances[endKey] ?: Double.POSITIVE_INFINITY
        val durationSeconds = (totalCost * 3600).roundToLong() // 1 cost unit = 1 hour

        return TravelRoute(
            startHex = startKey,
            endHex = endKey,
            path = path.toList(),
            totalCost = totalCost,
            estimatedDurationSeconds = durationSeconds
        )
    }

    /**
     * Calculates the cost to move from one hex to an adjacent hex.
     * Pure function — no side effects, fully deterministic.
     */
    private fun calculateEdgeWeight(from: String, to: String, plan: TravelPlan): Double {
        // Terrain modifier
        val terrain = provider.getTerrainForHex(to)
        var cost = 1.0 + (terrain?.let { plan.terrainModifiers[it] ?: 0.0 } ?: 0.0)

        // Hex content travel modifier (e.g., difficult terrain features)
        for (content in provider.getContentForHex(to)) {
            content.travelModifier?.let { cost += it.toDouble() }
        }

        // Infrastructure modifiers (roads, rivers, bridges)
        val features = provider.getFeaturesForHex(to)
        val hasBridge = features.contains("bridge")

        for (feature in features) {
            when (feature) {
                "river" -> {
                    if (!hasBridge) {
                        cost += plan.infrastructureModifiers["river"] ?: 1.0
                    }
                }
                "road" -> {
                    cost += plan.infrastructureModifiers["road"] ?: -1.0
                }
                "bridge" -> {
                    // Bridge itself has no cost; it only negates river penalty
                }
                else -> {
                    cost += plan.infrastructureModifiers[feature] ?: 0.0
                }
            }
        }

        // Weather multiplier (applied to total edge cost)
        cost *= plan.weatherModifier

        // Party speed (higher multiplier = faster = lower cost)
        cost /= plan.partySpeedMultiplier.coerceAtLeast(0.1)

        return if (cost <= 0) 0.01 else cost
    }
}

/**
 * Default travel plan with no modifiers (1.0 speed, no terrain/infrastructure/weather effects).
 */
val DefaultTravelPlan = TravelPlan()

/**
 * Travel plan optimized for caravan routing (plain per-hex cost, no modifiers).
 * Used by kingdom caravan system.
 */
val CaravanTravelPlan = TravelPlan(
    partySpeedMultiplier = 1.0,
    terrainModifiers = emptyMap(),
    infrastructureModifiers = emptyMap(),
    weatherModifier = 1.0
)
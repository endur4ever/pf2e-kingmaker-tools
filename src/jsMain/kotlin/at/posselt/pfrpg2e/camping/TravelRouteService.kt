package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.utils.toMap
import kotlin.math.roundToLong

/**
 * Provides access to hex grid topology and content.
 */
interface HexGridProvider {
    fun getAdjacentHexKeys(hexKey: String): List<String>
    fun getContentForHex(hexKey: String): List<HexContent>
}

/**
 * Service for calculating travel routes and costs between hexes.
 */
class TravelRouteService(private val gridProvider: HexGridProvider) {

    /**
     * Calculates a [TravelRoute] from start to end using Dijkstra's algorithm.
     */
    fun calculateRoute(startKey: String, endKey: String, plan: TravelPlan): TravelRoute? {
        val distances = mutableMapOf<String, Double>().withDefault { Double.POSITIVE_INFINITY }
        val previous = mutableMapOf<String, String?>()
        val queue = mutableSetOf<String>()

        distances[startKey] = 0.0
        queue.add(startKey)

        while (queue.isNotEmpty()) {
            val current = queue.minByOrNull { distances.getValue(it) } ?: break
            if (current == endKey) break
            queue.remove(current)

            for (neighbor in gridProvider.getAdjacentHexKeys(current)) {
                if (neighbor !in queue && neighbor != current) { // Added check to prevent self-loop
                    val weight = calculateEdgeWeight(current, neighbor, plan)
                    val alt = distances.getValue(current) + weight
                    
                    if (alt < distances.getValue(neighbor)) {
                        distances[neighbor] = alt
                        previous[neighbor] = current
                        queue.add(neighbor)
                    }
                }
            }
        }

        // Reconstruct path
        val path = mutableListOf<String>()
        var curr: String? = endKey
        while (curr != null && curr != startKey) {
            path.add(0, curr)
            curr = previous[curr]
        }

        if (path.isEmpty() && startKey != endKey) return null

        val totalCost = distances.getValue(endKey)
        // For simplicity in this skeleton, duration is cost * constant factor
        val durationSeconds = (totalCost * 3600).roundToLong()

        return TravelRoute(
            startHex = startKey,
            endHex = endKey,
            path = (listOf(startKey) + path).toList(),
            totalCost = totalCost,
            estimatedDurationSeconds = durationSeconds
        )
    }

    private fun calculateEdgeWeight(from: String, to: String, plan: TravelPlan): Double {
        val contents = gridProvider.getContentForHex(to)
        var weight = 1.0 // Base weight for one hex traversal

        // Apply terrain/infrastructure modifiers from the plan
        (plan.terrainModifiers as Map<String, Double>).forEach { (type, mod) ->
            if (contents.any { it.type.name == type }) {
                weight += mod
            }
        }
        
        (plan.infrastructureModifiers as Map<String, Double>).forEach { (type, mod) ->
             // In a real implementation, this would check actual road/bridge presence via features
        }

        weight *= plan.weatherModifier
        weight /= plan.partySpeedMultiplier

        return if (weight <= 0) 0.01 else weight
    }
}

package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.regions.Terrain
import at.posselt.pfrpg2e.fromCamelCase

class TravelService(
    private val hexContents: Map<String, HexContent>,
    private val terrainModifiers: Map<Terrain, Double> = emptyMap(),
    private val infrastructureModifiers: Map<String, Double> = emptyMap(),
    private val weatherModifier: Double = 1.0
) {
    /**
     * Calculates the travel route based on a path of hex keys.
     */
    fun calculateRoute(
        path: List<String>,
        partySpeedMultiplier: Double,
        getTerrain: ((String) -> Terrain?)? = null,
        getFeatures: ((String) -> List<String>)? = null
    ): TravelRoute {
        var totalCost = 0.0
        
        val resolveTerrain = getTerrain ?: { hexKey ->
            try {
                val hexObj = com.foundryvtt.kingmaker.kingmaker.region.hexes.find { it.key.toString() == hexKey }
                val terrainName = hexObj?.zone?.terrain
                terrainName?.let { fromCamelCase<Terrain>(it) }
            } catch (e: Throwable) {
                null
            }
        }

        val resolveFeatures = getFeatures ?: { hexKey ->
            try {
                val hexState = com.foundryvtt.kingmaker.kingmaker.state.hexes[hexKey]
                hexState?.features?.mapNotNull { it.type } ?: emptyList()
            } catch (e: Throwable) {
                emptyList()
            }
        }

        for (hexKey in path) {
            val content = hexContents[hexKey]
            var hexCost = 1.0
            
            // Apply travelModifier from HexContent if it exists
            content?.travelModifier?.let {
                hexCost += it.toDouble()
            }
            
            // Apply terrain modifier
            val terrain = resolveTerrain(hexKey)
            if (terrain != null) {
                hexCost += terrainModifiers[terrain] ?: 0.0
            }
            
            // Apply infrastructure modifiers
            val features = resolveFeatures(hexKey)
            val hasBridge = features.contains("bridge")
            features.forEach { featureType ->
                if (featureType == "river") {
                    if (!hasBridge) {
                        hexCost += infrastructureModifiers["river"] ?: 1.0
                    }
                } else if (featureType == "road") {
                    hexCost += infrastructureModifiers["road"] ?: -1.0
                } else if (featureType != "bridge") {
                    hexCost += infrastructureModifiers[featureType] ?: 0.0
                }
            }
            
            totalCost += hexCost
        }

        // Apply weather modifier to the total cost (as a multiplier)
        totalCost *= weatherModifier
        
        // Adjust by party speed (higher multiplier = faster travel -> lower time/cost)
        val finalCost = totalCost / partySpeedMultiplier.coerceAtLeast(0.1)

        return TravelRoute(
            startHex = path.firstOrNull() ?: "",
            endHex = path.lastOrNull() ?: "",
            path = path.toTypedArray(),
            totalCost = finalCost,
            estimatedDurationSeconds = (finallyTime(finalCost)).toLong()
        )
    }

    private fun finallyTime(cost: Double): Double {
        // 1 cost unit = 3600 seconds (1 hour).
        return cost * 3600.0
    }
}

// Helper for JS interoperability in Kotlin/JS
private fun Double.toDouble(): Double = this

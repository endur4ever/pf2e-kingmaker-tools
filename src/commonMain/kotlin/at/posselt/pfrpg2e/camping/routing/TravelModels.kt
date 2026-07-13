package at.posselt.pfrpg2e.camping.routing

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.regions.Terrain

/**
 * Represents a calculated travel route.
 */
data class TravelRoute(
    val startHex: String,
    val endHex: String,
    val path: List<String>,
    val totalCost: Double,
    val estimatedDurationSeconds: Long
)

/**
 * Represents the parameters used for travel cost calculation.
 */
data class TravelPlan(
    val partySpeedMultiplier: Double = 1.0,
    val terrainModifiers: Map<Terrain, Double> = emptyMap(),
    val infrastructureModifiers: Map<String, Double> = emptyMap(),
    val weatherModifier: Double = 1.0
)
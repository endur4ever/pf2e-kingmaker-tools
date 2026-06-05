package at.posselt.pfrpg2e.camping

/**
 * Represents the parameters used to calculate travel costs for a specific group or session.
 */
data class TravelPlan(
    val partySpeedMultiplier: Double,
    val terrainModifiers: Map<String, Double>, // Key is TerrainType string
    val infrastructureModifiers: Map<String, Double>, // Key is InfrastructureType string (e.g., "road", "bridge")
    val weatherModifier: Double
)

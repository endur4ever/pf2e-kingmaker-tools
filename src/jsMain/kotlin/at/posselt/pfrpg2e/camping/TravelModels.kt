package at.posselt.pfrpg2e.camping

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
 * Represents the parameters used for calculation.
 */
class TravelPlan(
    val partySpeedMultiplier: Double,
    val terrainModifiers: Map<String, Double>,
    val infrastructureModifiers: Map<String, Double>,
    val weatherModifier: Double
)

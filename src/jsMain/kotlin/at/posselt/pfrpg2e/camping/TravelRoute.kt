package at.posselt.pfrpg2e.camping

/**
 * Represents a path taken through a set of hexes with calculated travel costs.
 */
data class TravelRoute(
    val startHexKey: String,
    val endHexKey: String,
    val path: List<String>,
    val totalCost: Double,
    val estimatedDurationSeconds: Long
)

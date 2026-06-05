package at.posselt.pfrpg2e.camping

import js.objects.ReadonlyRecord

/**
 * Represents a calculated travel route.
 */
data class TravelRoute(
    val startHex: String,
    val endHex: String,
    val path: Array<String>,
    val totalCost: Double,
    val estimatedDurationSeconds: Long
)

/**
 * Represents the parameters used for calculation.
 */
class TravelPlan(
    val partySpeedMultiplier: Double,
    val terrainModifiers: ReadonlyRecord<String, Double>,
    val infrastructureModifiers: ReadonlyRecord<String, Double>,
    val weatherModifier: Double
)

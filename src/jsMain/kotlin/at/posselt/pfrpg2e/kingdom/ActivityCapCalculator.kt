package at.posselt.pfrpg2e.kingdom

data class ActivityCapsResult(
    val caps: List<ActivityCap>,
    val totalPerformed: Int,
    val totalAllowed: Int,
    val hasAnyOverCap: Boolean
)

data class ActivityCap(
    val phase: String,
    val current: Int,
    val maximum: Int,
    val isOverCap: Boolean
)

object ActivityCapCalculator {
    fun calculate(kingdom: KingdomData, performedCounts: Map<String, Int> = emptyMap()): ActivityCapsResult {
        val settings = kingdom.settings
        val settlements = kingdom.settlements ?: emptyArray()
        val hexContents = kingdom.hexContents ?: emptyArray()
        
        // Leadership cap: base 2 + 1 if increaseLeadershipActivities bonus
        val leadershipMax = if (settings.asDynamic().increaseLeadershipActivities == true) 3 else 2
        
        // Civic cap: number of settlements
        val civicMax = settlements.size
        
        // Region cap: number of claimed hexes (simplified - in reality would check for region activities)
        val regionMax = hexContents.size
        
        // Army cap: number of armies (from consumption.armies)
        val armyMax = kingdom.consumption.armies
        
        // Commerce cap: always 1 (Collect Taxes is the only commerce activity)
        val commerceMax = 1
        
        // Current counts from performed activities or default to 0
        val leadershipPerformed = performedCounts["leadership"] ?: 0
        val civicPerformed = performedCounts["civic"] ?: 0
        val regionPerformed = performedCounts["region"] ?: 0
        val armyPerformed = performedCounts["army"] ?: 0
        val commercePerformed = performedCounts["commerce"] ?: 0
        
        val caps = listOf(
            ActivityCap("leadership", leadershipPerformed, leadershipMax, leadershipPerformed > leadershipMax),
            ActivityCap("civic", civicPerformed, civicMax, civicPerformed > civicMax),
            ActivityCap("region", regionPerformed, regionMax, regionPerformed > regionMax),
            ActivityCap("army", armyPerformed, armyMax, armyPerformed > armyMax),
            ActivityCap("commerce", commercePerformed, commerceMax, commercePerformed > commerceMax)
        )
        
        val totalPerformed = leadershipPerformed + civicPerformed + regionPerformed + armyPerformed + commercePerformed
        val totalAllowed = leadershipMax + civicMax + regionMax + armyMax + commerceMax
        val hasAnyOverCap = caps.any { it.isOverCap }
        
        return ActivityCapsResult(
            caps = caps,
            totalPerformed = totalPerformed,
            totalAllowed = totalAllowed,
            hasAnyOverCap = hasAnyOverCap
        )
    }
}
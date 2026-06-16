package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.leaders.LeaderType
import at.posselt.pfrpg2e.kingdom.data.RawLeaderValues

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

/**
 * Counts the distinct PCs holding a non-vacant leadership role. RAW: "each PC in a leadership role
 * may attempt up to 2 (or 3) Leadership activities" — so the cap scales with the number of PC
 * leaders, not a flat number. Only PCs count (NPC leaders cannot perform kingdom activities). A PC
 * assigned to two roles is counted once (deduped by actor uuid); a non-vacant PC role with no actor
 * linked still counts as one leader, matching the "Vacant Positions" UI where the GM declares which
 * roles are filled.
 */
fun countPcLeaders(kingdom: KingdomData): Int {
    val leaders = kingdom.asDynamic().leaders
    if (leaders == null) return 0
    val pcRoles = arrayOf(
        "ruler", "counselor", "emissary", "general", "magister", "treasurer", "viceroy", "warden",
    )
        .map { leaders[it] }
        .filter { it != null }
        .map { it.unsafeCast<RawLeaderValues>() }
        .filter { it.type == LeaderType.PC.value && it.vacant != true }
    val distinctAssigned = pcRoles.mapNotNull { it.uuid }.toSet().size
    val unassigned = pcRoles.count { it.uuid == null }
    return distinctAssigned + unassigned
}

object ActivityCapCalculator {
    fun calculate(
        kingdom: KingdomData,
        performedCounts: Map<String, Int> = emptyMap(),
        leadershipCap: Int = 2,
        leadershipCapWithTownhall: Int = 3,
    ): ActivityCapsResult {
        val settings = kingdom.settings
        val settlements = kingdom.settlements ?: emptyArray()
        val hexContents = kingdom.hexContents ?: emptyArray()

        // RAW: each PC leader may attempt [leadershipCap] Leadership activities per turn (default 2),
        // rising to [leadershipCapWithTownhall] (default 3) when the capital has a Town Hall/Castle/
        // Palace (the increaseLeadershipActivities structure bonus). The total scales with the number
        // of PC leaders — leadershipCap/leadershipCapWithTownhall are the PER-PC-LEADER allotment.
        val perLeader = if (settings.asDynamic().increaseLeadershipActivities == true)
            leadershipCapWithTownhall
        else
            leadershipCap
        val leadershipMax = countPcLeaders(kingdom) * perLeader

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
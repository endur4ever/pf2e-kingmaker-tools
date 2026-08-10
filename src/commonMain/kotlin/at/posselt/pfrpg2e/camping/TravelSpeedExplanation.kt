package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.calculateHexplorationActivities

/** A party member's contribution to the party's travel speed. */
data class MemberSpeed(
    val name: String,
    val speedFeet: Int,
)

/**
 * Everything needed to explain, in the UI, where the route planner's travel speed came from.
 *
 * @param partySpeedFeet the party's travel Speed in feet
 * @param slowest the member(s) whose Speed sets it — PF2e takes the MINIMUM across members, so
 *   these are the ones actually holding the party back (plural on a tie)
 * @param baselineFeet the Speed the route planner treats as "normal pace"
 * @param multiplier partySpeedFeet / baselineFeet — route hours are divided by this
 * @param hexplorationActivitiesPerDay what the same Speed buys under the Kingmaker exploration
 *   table, which is a SEPARATE calculation the hexploration budget uses
 */
data class TravelSpeedBreakdown(
    val partySpeedFeet: Int,
    val slowest: List<MemberSpeed>,
    val baselineFeet: Int,
    val multiplier: Double,
    val hexplorationActivitiesPerDay: Double,
)

/** The route planner's "normal pace" reference Speed. */
const val TRAVEL_SPEED_BASELINE_FEET = 24

/**
 * Explains the party travel speed the route planner uses.
 *
 * The party Speed itself is not computed here — PF2e derives it in PartySystemData
 * (`movement.speeds.travel.value = Math.min(...members.map { it.system.movement.speeds.travel.value })`,
 * where each member's travel Speed is their land Speed re-derived in the "travel" domain). This
 * function takes that number plus the member Speeds so the UI can name WHICH member is setting it,
 * and derives the pace multiplier the route cost is divided by.
 *
 * [members] may be empty (a party with no members, or speeds that could not be read); the
 * breakdown then simply has no "slowest" attribution.
 */
fun explainTravelSpeed(
    partySpeedFeet: Int,
    members: List<MemberSpeed> = emptyList(),
    baselineFeet: Int = TRAVEL_SPEED_BASELINE_FEET,
): TravelSpeedBreakdown {
    val safeBaseline = if (baselineFeet > 0) baselineFeet else TRAVEL_SPEED_BASELINE_FEET
    // Only members actually at the party speed are holding it back; a tie names all of them.
    val slowest = members.filter { it.speedFeet == partySpeedFeet }
    return TravelSpeedBreakdown(
        partySpeedFeet = partySpeedFeet,
        slowest = slowest,
        baselineFeet = safeBaseline,
        multiplier = partySpeedFeet.toDouble() / safeBaseline.toDouble(),
        hexplorationActivitiesPerDay = calculateHexplorationActivities(partySpeedFeet),
    )
}

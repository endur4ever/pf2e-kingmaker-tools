package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.calculateHexplorationActivities

/** A party member's contribution to the party's travel speed. */
data class MemberSpeed(
    val name: String,
    val speedFeet: Int,
)

/**
 * Everything needed to explain, in the UI, where the route planner's travel numbers come from.
 *
 * @param partySpeedFeet the party's travel Speed in feet
 * @param slowest the member(s) whose Speed sets it — PF2e takes the MINIMUM across members, so
 *   these are the ones actually holding the party back (plural on a tie)
 * @param hexplorationActivitiesPerDay what that Speed buys under the hexploration table, which is
 *   the ONLY way Speed affects travel: a route costs Travel activities, and this is how many of
 *   them the party can spend per day
 */
data class TravelSpeedBreakdown(
    val partySpeedFeet: Int,
    val slowest: List<MemberSpeed>,
    val hexplorationActivitiesPerDay: Double,
)

/** Fallback party Speed when the actor's cannot be read — the commonest PC Speed. */
const val DEFAULT_PARTY_SPEED_FEET = 25

/**
 * Explains the party travel speed the route planner uses.
 *
 * The party Speed itself is not computed here — PF2e derives it in PartySystemData
 * (`movement.speeds.travel.value = Math.min(...members.map { it.system.movement.speeds.travel.value })`,
 * where each member's travel Speed is their land Speed re-derived in the "travel" domain). This
 * function takes that number plus the member Speeds so the UI can name WHICH member is setting it,
 * and reports the activities per day it buys.
 *
 * [members] may be empty (a party with no members, or speeds that could not be read); the
 * breakdown then simply has no "slowest" attribution.
 */
fun explainTravelSpeed(
    partySpeedFeet: Int,
    members: List<MemberSpeed> = emptyList(),
): TravelSpeedBreakdown {
    // Only members actually at the party speed are holding it back; a tie names all of them.
    val slowest = members.filter { it.speedFeet == partySpeedFeet }
    return TravelSpeedBreakdown(
        partySpeedFeet = partySpeedFeet,
        slowest = slowest,
        hexplorationActivitiesPerDay = calculateHexplorationActivities(partySpeedFeet),
    )
}

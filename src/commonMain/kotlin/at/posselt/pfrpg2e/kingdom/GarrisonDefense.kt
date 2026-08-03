package at.posselt.pfrpg2e.kingdom

/**
 * Pure garrison-defense math. A war threat that targets settlement S should let armies garrisoned at
 * S actually help: they join the defense, they blunt a sack, and (in a settlement with a Garrison
 * structure) they fight a little harder. Today `garrisonedSettlementId` only feeds display fields.
 *
 * This is the deterministic core; wiring the defenders into `createArmyBattle`, reducing the siege
 * offer card's structure count, and surfacing the AC bonus in the ResolveBattle rows is the deferred
 * jsMain work. Builds on [calculateSiegeDamage].
 *
 * See card t_35bb2e42.
 */

/** A minimal view of one army's garrison assignment (mapped from the army deployment in jsMain). */
data class GarrisonAssignment(
    val armyId: String,
    val garrisonedSettlementId: String?,
)

/** Ids of the armies garrisoned at [settlementId], in input order. These join the defender side. */
fun garrisonedArmyIdsFor(
    settlementId: String,
    assignments: List<GarrisonAssignment>,
): List<String> =
    assignments.filter { it.garrisonedSettlementId == settlementId }.map { it.armyId }

/** Add the garrisoned armies to the battle's defender side, de-duplicated, preserving order. */
fun withGarrisonDefenders(
    defenderArmyIds: List<String>,
    garrisonedArmyIds: List<String>,
): List<String> =
    (defenderArmyIds + garrisonedArmyIds).distinct()

/** How many structures a present garrison saves from a sack. */
const val GARRISON_SIEGE_REDUCTION = 1

/**
 * Siege damage with a garrison factored in: identical to [calculateSiegeDamage] but a present
 * garrison spares [GARRISON_SIEGE_REDUCTION] structure (never below 0), and unrest is recomputed
 * from the reduced count. A garrison does not turn a bloodless assault negative — the single point of
 * unrest for the attack itself still stands.
 */
fun siegeDamageWithGarrison(
    threatEscalation: Int,
    maxEscalation: Int,
    defensiveStructureCount: Int,
    garrisonPresent: Boolean,
): SiegeDamage {
    val base = calculateSiegeDamage(threatEscalation, maxEscalation, defensiveStructureCount)
    if (!garrisonPresent) return base
    val destroyed = (base.structuresDestroyed - GARRISON_SIEGE_REDUCTION).coerceAtLeast(0)
    val unrest = 1 + destroyed.coerceAtMost(MAX_SIEGE_STRUCTURES_DESTROYED)
    return SiegeDamage(structuresDestroyed = destroyed, unrestGain = unrest)
}

/** The defensive bonus a garrisoned army fights with: +1 only in a settlement that has a Garrison. */
const val GARRISON_STRUCTURE_DEFENSE_BONUS = 1

/** The battle defense bonus for an army garrisoned in a settlement that has a Garrison structure. */
fun garrisonDefensiveBonus(inSettlementWithGarrisonStructure: Boolean): Int =
    if (inSettlementWithGarrisonStructure) GARRISON_STRUCTURE_DEFENSE_BONUS else 0

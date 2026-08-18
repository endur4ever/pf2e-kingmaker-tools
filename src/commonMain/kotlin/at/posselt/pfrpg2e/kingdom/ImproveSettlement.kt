package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementSizeType
import at.posselt.pfrpg2e.data.kingdom.settlements.settlementSizeData

/**
 * Pure prerequisite/cost math for the "Improve Settlement (Civic, Downtime)" house-rule activity
 * (docs/house-rules.md): spend commodities + RP to upgrade a NON-capital settlement to the next
 * settlement type, which sets a fixed item-purchase level. The impure surfaces (activity JSON,
 * canUpgradeNonCapital gate, GM-confirmed upgrade offer) consume these; the numbers live here so
 * they stay pinned to the doc and unit-tested.
 */

/** Activity id of the house-rule Improve Settlement activity. */
const val IMPROVE_SETTLEMENT_ACTIVITY = "improve-settlement"

/** Resources consumed to upgrade a settlement to a given tier. */
data class ImproveSettlementCost(
    val stone: Int,
    val ore: Int,
    val lumber: Int,
    val luxuries: Int,
    val rp: Int,
)

/** The next settlement tier up, or null when already at the maximum (Metropolis). */
fun nextSettlementTier(current: SettlementSizeType): SettlementSizeType? = when (current) {
    SettlementSizeType.VILLAGE -> SettlementSizeType.TOWN
    SettlementSizeType.TOWN -> SettlementSizeType.CITY
    SettlementSizeType.CITY -> SettlementSizeType.METROPOLIS
    SettlementSizeType.METROPOLIS -> null
}

/** Cost to upgrade TO [target]; null for VILLAGE (never an upgrade target). */
fun improveSettlementCost(target: SettlementSizeType): ImproveSettlementCost? = when (target) {
    SettlementSizeType.TOWN -> ImproveSettlementCost(stone = 5, ore = 5, lumber = 5, luxuries = 1, rp = 10)
    SettlementSizeType.CITY -> ImproveSettlementCost(stone = 10, ore = 10, lumber = 10, luxuries = 3, rp = 25)
    SettlementSizeType.METROPOLIS -> ImproveSettlementCost(stone = 20, ore = 20, lumber = 20, luxuries = 5, rp = 50)
    SettlementSizeType.VILLAGE -> null
}

/** Fixed item-purchase level a tier grants per the house rule (Town 3 / City 9 / Metropolis 15). */
fun improveSettlementPurchaseLevel(target: SettlementSizeType): Int = when (target) {
    SettlementSizeType.TOWN -> 3
    SettlementSizeType.CITY -> 9
    SettlementSizeType.METROPOLIS -> 15
    SettlementSizeType.VILLAGE -> 1
}

/** The kingdom level a tier requires, from the shared settlement-size table. */
fun requiredKingdomLevelFor(tier: SettlementSizeType): Int =
    settlementSizeData.first { it.type == tier }.requiredKingdomLevel

/**
 * Whether Improve Settlement can be applied: the settlement is not the capital, is below the maximum
 * tier, the kingdom is high enough level for that tier, and it can pay the tier's commodity + RP cost.
 *
 * The level check is the doc's "(kingdom level restrictions still apply)". It was previously deferred
 * to "the existing settlement-size logic", but nothing on this path re-checked it: the predicate
 * happily approved a Village to Town upgrade at kingdom level 1, where the shared size table requires
 * level 3.
 */
fun canImproveSettlement(
    currentTier: SettlementSizeType,
    isCapital: Boolean,
    kingdomLevel: Int,
    availableStone: Int,
    availableOre: Int,
    availableLumber: Int,
    availableLuxuries: Int,
    availableRp: Int,
): Boolean {
    if (isCapital) return false
    val next = nextSettlementTier(currentTier) ?: return false
    if (kingdomLevel < requiredKingdomLevelFor(next)) return false
    val cost = improveSettlementCost(next) ?: return false
    return availableStone >= cost.stone &&
        availableOre >= cost.ore &&
        availableLumber >= cost.lumber &&
        availableLuxuries >= cost.luxuries &&
        availableRp >= cost.rp
}

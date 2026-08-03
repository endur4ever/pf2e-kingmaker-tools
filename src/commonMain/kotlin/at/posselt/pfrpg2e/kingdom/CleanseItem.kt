package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.getLevelBasedDC

/** The "incredibly hard" difficulty adjustment (PF2e: +10 on the level-based DC). */
private const val INCREDIBLY_HARD_ADJUSTMENT = 10

/**
 * Derived requirements for the Cleanse Item leadership activity (house-rules.md): a kingdom-scale
 * Magic counteract to remove a curse from an item.
 *
 * @param counteractLevel The kingdom's counteract level — kingdom level halved, rounded up.
 * @param dc The check DC — an incredibly-hard DC for the item's level (the item-level DC + 10).
 * @param luxuryCost Luxuries required to prepare the ritual, by item level band.
 * @param requiredStructure The lowest structure that qualifies to prepare the ritual.
 */
data class CleanseItemPlan(
    val counteractLevel: Int,
    val dc: Int,
    val luxuryCost: Int,
    val requiredStructure: CleanseItemStructure,
)

/** Structures that can prepare the Cleanse Item ritual, in ascending capability. */
enum class CleanseItemStructure(val value: String) {
    SHRINE("shrine"),
    TEMPLE("temple"),
    CATHEDRAL("cathedral");

    val i18nKey: String get() = "activities.cleanse-item.structure.$value"
}

/**
 * Derives the Cleanse Item requirements for an item of [itemLevel] in a kingdom of [kingdomLevel].
 * Pure — the dialog reads these numbers; tests pin them to the house-rules table:
 *
 *  - counteract level = ceil(kingdomLevel / 2)
 *  - DC = incredibly-hard DC for the item level (getLevelBasedDC(itemLevel) + 10)
 *  - 1-5: 1 Luxury, Shrine · 6-10: 2 Luxuries, Temple · 11-15: 4 Luxuries, Temple · 16+: 8, Cathedral
 */
fun cleanseItemPlan(itemLevel: Int, kingdomLevel: Int): CleanseItemPlan {
    val level = itemLevel.coerceAtLeast(0)
    val (luxuryCost, structure) = when {
        level <= 5 -> 1 to CleanseItemStructure.SHRINE
        level <= 10 -> 2 to CleanseItemStructure.TEMPLE
        level <= 15 -> 4 to CleanseItemStructure.TEMPLE
        else -> 8 to CleanseItemStructure.CATHEDRAL
    }
    return CleanseItemPlan(
        counteractLevel = (kingdomLevel.coerceAtLeast(1) + 1) / 2,
        dc = getLevelBasedDC(level) + INCREDIBLY_HARD_ADJUSTMENT,
        luxuryCost = luxuryCost,
        requiredStructure = structure,
    )
}

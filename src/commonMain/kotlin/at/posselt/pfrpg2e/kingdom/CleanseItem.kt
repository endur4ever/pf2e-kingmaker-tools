package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.getLevelBasedDC

/**
 * Derived requirements for the Cleanse Item leadership activity (house-rules.md): a kingdom-scale
 * Magic counteract to remove a curse from an item.
 *
 * @param counteractLevel The kingdom's counteract level — kingdom level halved, rounded up.
 * @param dc The check DC — the counteract DC for the item's level.
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
 *  - DC = the level-based DC for the ITEM's level — PF2e's counteract DC against a level-N item.
 *
 *    Deliberately NOT the "incredibly hard" (+10) reading the prose in house-rules.md invited. The
 *    kingdom Control DC is 14 + level + level/3 + size, i.e. the SAME base as getLevelBasedDC, so
 *    this makes cleansing an item of the kingdom's own level exactly as hard as a routine kingdom
 *    check, and scales naturally either side of that. A flat +10 on top made it the hardest thing a
 *    kingdom could attempt, which overpriced an activity meant to be usable.
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
        dc = getLevelBasedDC(level),
        luxuryCost = luxuryCost,
        requiredStructure = structure,
    )
}

package at.posselt.pfrpg2e.camping

/**
 * Relative weights (0-100 each) controlling how often each encounter category is
 * rolled (roadmap #11). Weights are relative, not percentages — [normalized]
 * converts them to a probability distribution that sums to 1.0.
 */
data class CategoryWeights(
    val combat: Int = 30,
    val rp: Int = 15,
    val rumor: Int = 15,
    val merchant: Int = 10,
    val disease: Int = 5,
    val faction: Int = 10,
    val weather: Int = 10,
    val lore: Int = 5,
) {
    val total: Int get() = combat + rp + rumor + merchant + disease + faction + weather + lore

    fun weightFor(category: EncounterCategory): Int = when (category) {
        EncounterCategory.COMBAT -> combat
        EncounterCategory.RP -> rp
        EncounterCategory.RUMOR -> rumor
        EncounterCategory.MERCHANT -> merchant
        EncounterCategory.DISEASE -> disease
        EncounterCategory.FACTION -> faction
        EncounterCategory.WEATHER -> weather
        EncounterCategory.LORE -> lore
    }

    fun normalized(): Map<EncounterCategory, Double> {
        val t = total.toDouble().coerceAtLeast(1.0)
        return EncounterCategory.entries.associateWith { weightFor(it) / t }
    }

    /**
     * Select a category for a uniform [roll] in `[0, 1)`, walking the weighted
     * cumulative distribution in enum order. Categories with weight 0 are never
     * selected. Falls back to COMBAT when all weights are 0.
     */
    fun pickCategory(roll: Double): EncounterCategory {
        if (total <= 0) return EncounterCategory.COMBAT
        val r = roll.coerceIn(0.0, 1.0) * total
        var cumulative = 0
        for (c in EncounterCategory.entries) {
            cumulative += weightFor(c)
            if (r < cumulative) return c
        }
        return EncounterCategory.entries.last { weightFor(it) > 0 }
    }
}

/**
 * Roadmap #11: a Combat encounter is suppressed when the region opts into hex
 * filtering and the party is in a claimed + cleared hex. Non-combat categories
 * (rumors, merchants, lore, …) are never suppressed.
 */
fun shouldSuppressEncounter(
    category: EncounterCategory,
    regionSuppressesClearedHex: Boolean,
    hexClaimedAndCleared: Boolean,
): Boolean = category == EncounterCategory.COMBAT &&
    regionSuppressesClearedHex &&
    hexClaimedAndCleared

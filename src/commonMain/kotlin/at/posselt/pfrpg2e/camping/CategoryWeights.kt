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
}

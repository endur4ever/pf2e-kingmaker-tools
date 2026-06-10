package at.posselt.pfrpg2e.camping

/**
 * The eight categories a random encounter can resolve to (roadmap #11).
 *
 * A region's category proxy table routes a roll to one of these categories; the
 * category then selects the per-category roll table configured for the region.
 */
enum class EncounterCategory(val value: String, val iconClass: String) {
    COMBAT("combat", "fa-solid fa-skull"),
    RP("rp", "fa-solid fa-comments"),
    RUMOR("rumor", "fa-solid fa-scroll"),
    MERCHANT("merchant", "fa-solid fa-store"),
    DISEASE("disease", "fa-solid fa-virus"),
    FACTION("faction", "fa-solid fa-flag"),
    WEATHER("weather", "fa-solid fa-cloud-bolt"),
    LORE("lore", "fa-solid fa-book");

    companion object {
        /**
         * Resolve a proxy-table result to a category. Exact match first; if that
         * fails, a lenient whole-token match so rows like "Combat Encounter" or
         * "Rumor (minor)" still resolve (substrings like "folklore" do not).
         */
        fun fromString(value: String): EncounterCategory? {
            val normalized = value.trim().lowercase()
            entries.firstOrNull { it.value == normalized }?.let { return it }
            val tokens = normalized.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
            return entries.firstOrNull { it.value in tokens }
        }

        fun allCategories(): List<EncounterCategory> = entries.toList()
    }
}

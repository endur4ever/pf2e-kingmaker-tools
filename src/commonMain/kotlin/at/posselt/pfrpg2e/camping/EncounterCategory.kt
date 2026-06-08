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
        fun fromString(value: String): EncounterCategory? =
            entries.firstOrNull { it.value == value.trim().lowercase() }

        fun allCategories(): List<EncounterCategory> = entries.toList()
    }
}

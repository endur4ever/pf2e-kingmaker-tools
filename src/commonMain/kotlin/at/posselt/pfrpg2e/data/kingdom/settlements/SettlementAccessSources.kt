package at.posselt.pfrpg2e.data.kingdom.settlements

/**
 * One structure group and the settlement access it grants.
 *
 * @param structureIds base structure ids (already `-vk`-stripped) that grant this benefit group
 * @param structurePrefix matches any base id starting with this, for the tavern tiers
 * @param benefits the bare benefit ids, whose i18n keys are `kingdom.class.<id>` / `kingdom.crafting.<id>`
 */
data class AccessSource(
    val structureIds: Set<String> = emptySet(),
    val structurePrefix: String? = null,
    val benefits: List<String>,
) {
    fun matches(baseId: String): Boolean =
        baseId in structureIds || (structurePrefix != null && baseId.startsWith(structurePrefix))
}

/**
 * The single source of truth for which structures grant which class trainers.
 *
 * This table used to exist twice: once here in bare-id form (unit-tested, and what the quest-grant
 * union consumes) and once again inside InspectSettlement as pre-composed display strings. Two
 * copies of a lookup table drift, and these already differed in how they aggregated structure names.
 * Order is the display order.
 */
val TRAINER_ACCESS_SOURCES: List<AccessSource> = listOf(
    AccessSource(setOf("shrine"), benefits = listOf("cleric", "oracle")),
    AccessSource(setOf("library"), benefits = listOf("investigator", "thaumaturge", "psychic")),
    AccessSource(setOf("alchemy-laboratory"), benefits = listOf("alchemist", "gunslinger", "inventor")),
    AccessSource(structurePrefix = "tavern-", benefits = listOf("bard")),
    AccessSource(setOf("arcanists-tower"), benefits = listOf("wizard", "witch", "sorcerer", "magus")),
    AccessSource(setOf("garrison"), benefits = listOf("fighter", "barbarian", "champion", "monk")),
    AccessSource(setOf("sacred-grove"), benefits = listOf("druid", "kineticist", "summoner", "ranger")),
    AccessSource(setOf("thieves-guild"), benefits = listOf("rogue")),
    AccessSource(setOf("pier"), benefits = listOf("swashbuckler")),
)

/** The single source of truth for which structures grant which crafting access. Order is display order. */
val CRAFTING_ACCESS_SOURCES: List<AccessSource> = listOf(
    AccessSource(setOf("smithy", "foundry"), benefits = listOf("metallic")),
    AccessSource(setOf("stonemason"), benefits = listOf("runes")),
    AccessSource(setOf("tannery"), benefits = listOf("leather")),
    AccessSource(setOf("arcanists-tower"), benefits = listOf("scrollsWandsStaves")),
    AccessSource(setOf("luxury-store"), benefits = listOf("amuletsRings")),
    AccessSource(setOf("library"), benefits = listOf("tomes")),
    AccessSource(setOf("alchemy-laboratory"), benefits = listOf("alchemical")),
    AccessSource(setOf("lumberyard"), benefits = listOf("wooden")),
    AccessSource(setOf("specialized-artisan"), benefits = listOf("other")),
)

/** The benefit ids granted by [sources] given the settlement's `-vk`-stripped structure ids. */
fun accessBenefitsFor(sources: List<AccessSource>, baseIds: Set<String>): List<String> =
    sources.filter { source -> baseIds.any { source.matches(it) } }
        .flatMap { it.benefits }
        .distinct()

package at.posselt.pfrpg2e.data.kingdom.settlements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettlementAccessSourcesTest {
    @Test
    fun aTavernOfAnyTierGrantsBard() {
        // Matched by prefix, so all four tiers count without listing them.
        listOf("tavern-dive", "tavern-popular", "tavern-luxury", "tavern-world-class").forEach { tier ->
            assertEquals(listOf("bard"), accessBenefitsFor(TRAINER_ACCESS_SOURCES, setOf(tier)))
        }
    }

    @Test
    fun benefitsFromSeveralStructuresAccumulateInTableOrder() {
        val benefits = accessBenefitsFor(TRAINER_ACCESS_SOURCES, setOf("pier", "shrine"))
        // Shrine precedes pier in the table, and the table order IS the display order.
        assertEquals(listOf("cleric", "oracle", "swashbuckler"), benefits)
    }

    @Test
    fun oneStructureCanGrantBothATrainerAndCraftingAccess() {
        val ids = setOf("library")
        assertEquals(listOf("investigator", "thaumaturge", "psychic"), accessBenefitsFor(TRAINER_ACCESS_SOURCES, ids))
        assertEquals(listOf("tomes"), accessBenefitsFor(CRAFTING_ACCESS_SOURCES, ids))
    }

    @Test
    fun eitherOfASharedGroupsStructuresGrantsIt() {
        // Smithy and foundry both grant metallic, and holding both must not list it twice.
        assertEquals(listOf("metallic"), accessBenefitsFor(CRAFTING_ACCESS_SOURCES, setOf("smithy")))
        assertEquals(listOf("metallic"), accessBenefitsFor(CRAFTING_ACCESS_SOURCES, setOf("foundry")))
        assertEquals(listOf("metallic"), accessBenefitsFor(CRAFTING_ACCESS_SOURCES, setOf("smithy", "foundry")))
    }

    @Test
    fun aSettlementWithNoRelevantStructuresGrantsNothing() {
        assertEquals(emptyList(), accessBenefitsFor(TRAINER_ACCESS_SOURCES, setOf("town-hall")))
        assertEquals(emptyList(), accessBenefitsFor(CRAFTING_ACCESS_SOURCES, emptySet()))
    }

    @Test
    fun everyBenefitIdIsOneWithALocalizationKey() {
        // The benefit ids ARE the i18n key leaves (kingdom.class.<id> / kingdom.crafting.<id>), and
        // the i18n guard skips dynamic keys, so it cannot catch a new id that has no key. This test
        // is that guard: adding an id here means adding its key to every lang file.
        val expectedTrainers = setOf(
            "alchemist", "barbarian", "bard", "champion", "cleric", "druid", "fighter", "gunslinger",
            "inventor", "investigator", "kineticist", "magus", "monk", "oracle", "psychic", "ranger",
            "rogue", "sorcerer", "summoner", "swashbuckler", "thaumaturge", "witch", "wizard",
        )
        val expectedCrafting = setOf(
            "alchemical", "amuletsRings", "leather", "metallic", "other", "runes",
            "scrollsWandsStaves", "tomes", "wooden",
        )
        assertEquals(expectedTrainers, TRAINER_ACCESS_SOURCES.flatMap { it.benefits }.toSet())
        assertEquals(expectedCrafting, CRAFTING_ACCESS_SOURCES.flatMap { it.benefits }.toSet())
    }

    @Test
    fun everySourceGrantsSomething() {
        assertTrue(TRAINER_ACCESS_SOURCES.all { it.benefits.isNotEmpty() })
        assertTrue(CRAFTING_ACCESS_SOURCES.all { it.benefits.isNotEmpty() })
        // Every row matches on something, or it is unreachable dead data.
        assertTrue((TRAINER_ACCESS_SOURCES + CRAFTING_ACCESS_SOURCES).all {
            it.structureIds.isNotEmpty() || it.structurePrefix != null
        })
    }
}

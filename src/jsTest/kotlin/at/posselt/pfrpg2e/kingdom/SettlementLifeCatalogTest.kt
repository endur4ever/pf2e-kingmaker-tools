package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.settlementlife.LifeEventHookKind
import at.posselt.pfrpg2e.kingdom.structures.structures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shipped life-event catalog against the shipped STRUCTURE catalog.
 *
 * A template naming a structure id that does not exist is silently un-fireable (or un-boostable),
 * and no schema can catch it -- the schema only knows the field is a string. The plan's own
 * appendix named "tavern", which is not an id: the shipped set has tavern-dive, tavern-popular,
 * tavern-luxury and tavern-world-class.
 */
class SettlementLifeCatalogTest {
    private fun baseStructureIds(): Set<String> =
        structures.mapNotNull { it.id?.removeSuffix("-vk") }.toSet()

    private fun namedStructureIds(): List<Pair<String, String>> =
        rawSettlementLifeTemplates().flatMap { template ->
            val required = (template.requiresStructures ?: emptyArray()).toList()
            val weighted = (template.structureWeights ?: emptyArray()).flatMap { it.anyOf.toList() }
            (required + weighted).map { template.id to it }
        }

    @Test
    fun everyStructureIdNamedByATemplateExists() {
        val known = baseStructureIds()
        assertTrue(known.isNotEmpty(), "no structures loaded -- the guard would pass vacuously")
        val missing = namedStructureIds().filter { (_, id) -> id !in known }
        assertTrue(missing.isEmpty(), "templates name unknown structure ids: $missing")
    }

    @Test
    fun everyTemplateHookIsInTheClosedSet() {
        rawSettlementLifeTemplates().forEach { template ->
            val kind = template.hook?.kind
            if (kind != null) {
                assertTrue(
                    LifeEventHookKind.fromValue(kind) != null,
                    "template ${template.id} names hook kind '$kind', which is outside the closed set",
                )
            }
        }
    }

    @Test
    fun templateIdsAreUniqueAndTheCatalogMaps() {
        val raw = rawSettlementLifeTemplates()
        assertTrue(raw.isNotEmpty())
        assertEquals(raw.size, raw.map { it.id }.toSet().size, "duplicate template ids")
        // the mapped view must not silently lose templates
        assertEquals(raw.size, settlementLifeTemplates().size)
    }

    @Test
    fun mappedTemplatesKeepTheirWeightingData() {
        val feast = settlementLifeTemplates().single { it.id == "midwinter-feast" }
        assertEquals(10, feast.weight)
        assertEquals(2.0, feast.seasonWeights["winter"])
        assertEquals(0.5, feast.seasonWeights["spring"])
        assertEquals(LifeEventHookKind.RP, feast.hookKind)
        assertEquals(2, feast.castSlots.size)
        val theft = settlementLifeTemplates().single { it.id == "guild-theft" }
        assertEquals(listOf("thieves-guild"), theft.requiresStructures)
        assertEquals(LifeEventHookKind.UNREST, theft.hookKind)
    }
}

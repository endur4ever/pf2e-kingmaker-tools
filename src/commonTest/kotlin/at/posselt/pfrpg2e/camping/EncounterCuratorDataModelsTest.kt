package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.math.abs

class EncounterCategoryTest {
    @Test
    fun fromStringResolvesEachValue() {
        EncounterCategory.allCategories().forEach { c ->
            assertEquals(c, EncounterCategory.fromString(c.value))
        }
    }

    @Test
    fun fromStringIsCaseAndWhitespaceInsensitive() {
        assertEquals(EncounterCategory.COMBAT, EncounterCategory.fromString("  Combat "))
        assertEquals(EncounterCategory.RUMOR, EncounterCategory.fromString("RUMOR"))
    }

    @Test
    fun fromStringReturnsNullForUnknown() {
        assertNull(EncounterCategory.fromString("not-a-category"))
    }

    @Test
    fun thereAreEightCategoriesEachWithAnIcon() {
        val all = EncounterCategory.allCategories()
        assertEquals(8, all.size)
        assertTrue(all.all { it.iconClass.startsWith("fa-") })
    }
}

class CategoryWeightsTest {
    @Test
    fun defaultWeightsTotalToOneHundred() {
        assertEquals(100, CategoryWeights().total)
    }

    @Test
    fun normalizedSumsToOne() {
        val sum = CategoryWeights().normalized().values.sum()
        assertTrue(abs(sum - 1.0) < 1e-9, "normalized weights should sum to 1.0, was $sum")
    }

    @Test
    fun normalizedCoversAllCategories() {
        assertEquals(EncounterCategory.allCategories().toSet(), CategoryWeights().normalized().keys)
    }

    @Test
    fun weightForMatchesField() {
        val w = CategoryWeights(combat = 42, lore = 7)
        assertEquals(42, w.weightFor(EncounterCategory.COMBAT))
        assertEquals(7, w.weightFor(EncounterCategory.LORE))
    }

    @Test
    fun zeroTotalDoesNotDivideByZero() {
        val zero = CategoryWeights(0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(0, zero.total)
        // coerceAtLeast(1.0) guards the division
        assertEquals(0.0, zero.normalized().values.sum())
    }
}

class CuratorValueModelsTest {
    @Test
    fun rumorDefaults() {
        val r = Rumor(text = "Bandits near Oleg's")
        assertFalse(r.isQuestHook)
        assertFalse(r.isConverted)
        assertNull(r.convertedQuestId)
    }

    @Test
    fun merchantStockDefaults() {
        val m = MerchantStock(name = "Wandering Tinker")
        assertTrue(m.stockItems.isEmpty())
        assertEquals(0, m.stockRemaining)
        assertEquals("", m.uuid)
    }

    @Test
    fun questTemplateReferenceDefaults() {
        val q = QuestTemplateReference(templateId = "t1", name = "Lost Caravan")
        assertEquals(1, q.recommendedLevel)
        assertTrue(q.sourceEventTraits.isEmpty())
    }
}

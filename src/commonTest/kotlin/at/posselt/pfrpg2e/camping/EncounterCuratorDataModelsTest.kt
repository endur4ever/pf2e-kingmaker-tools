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
    fun fromStringResolvesLenientWholeTokenMatch() {
        assertEquals(EncounterCategory.COMBAT, EncounterCategory.fromString("Combat Encounter"))
        assertEquals(EncounterCategory.RUMOR, EncounterCategory.fromString("Rumor (minor)"))
        assertEquals(EncounterCategory.LORE, EncounterCategory.fromString("A bit of Lore"))
        // substrings must NOT match — only whole tokens
        assertNull(EncounterCategory.fromString("explore the folklore"))
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

class CategoryRoutingTest {
    // default weights: combat=30, rp=15, rumor=15, merchant=10, disease=5, faction=10, weather=10, lore=5 (total 100)
    private val w = CategoryWeights()

    @Test
    fun rollZeroPicksFirstWeightedCategory() {
        assertEquals(EncounterCategory.COMBAT, w.pickCategory(0.0))
    }

    @Test
    fun rollLandsInCorrectBucket() {
        assertEquals(EncounterCategory.COMBAT, w.pickCategory(0.29))   // [0,30)
        assertEquals(EncounterCategory.RP, w.pickCategory(0.40))       // [30,45)
        assertEquals(EncounterCategory.RUMOR, w.pickCategory(0.50))    // [45,60)
        assertEquals(EncounterCategory.LORE, w.pickCategory(0.99))     // last bucket
    }

    @Test
    fun zeroWeightCategoriesAreNeverPicked() {
        val onlyRp = CategoryWeights(combat = 0, rp = 100, rumor = 0, merchant = 0, disease = 0, faction = 0, weather = 0, lore = 0)
        assertEquals(EncounterCategory.RP, onlyRp.pickCategory(0.0))
        assertEquals(EncounterCategory.RP, onlyRp.pickCategory(0.999))
    }

    @Test
    fun allZeroWeightsFallBackToCombat() {
        assertEquals(EncounterCategory.COMBAT, CategoryWeights(0, 0, 0, 0, 0, 0, 0, 0).pickCategory(0.5))
    }

    @Test
    fun curatorGateEngagesWhenWeightsConfiguredWithoutProxyTable() {
        val weights = CategoryWeights(combat = 30)
        assertTrue(isEncounterCuratorActive(hasProxyTable = false, weightsTotal = weights.total))
        assertEquals(CuratorActiveReason.ACTIVE_WEIGHTS, decideCuratorStatus(hasProxyTable = false, weightsTotal = weights.total))
    }

    @Test
    fun curatorGateEngagesWhenProxyTableConfiguredWithZeroWeights() {
        assertFalse(isEncounterCuratorActive(hasProxyTable = false, weightsTotal = 0))
        assertEquals(CuratorActiveReason.INACTIVE, decideCuratorStatus(hasProxyTable = false, weightsTotal = 0))

        assertTrue(isEncounterCuratorActive(hasProxyTable = true, weightsTotal = 0))
        assertEquals(CuratorActiveReason.ACTIVE_PROXY_TABLE, decideCuratorStatus(hasProxyTable = true, weightsTotal = 0))
    }

    @Test
    fun curatorGateEngagesWithBothConfigured() {
        val weights = CategoryWeights(combat = 20, rp = 10)
        assertTrue(isEncounterCuratorActive(hasProxyTable = true, weightsTotal = weights.total))
        assertEquals(CuratorActiveReason.ACTIVE_BOTH, decideCuratorStatus(hasProxyTable = true, weightsTotal = weights.total))
    }

    @Test
    fun curatorGateRemainsInactiveWhenNeitherConfigured() {
        assertFalse(isEncounterCuratorActive(hasProxyTable = false, weightsTotal = 0))
        assertEquals(CuratorActiveReason.INACTIVE, decideCuratorStatus(hasProxyTable = false, weightsTotal = 0))
    }
}

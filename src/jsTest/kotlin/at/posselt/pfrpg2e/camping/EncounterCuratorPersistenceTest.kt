package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals

/** Round-trip tests for the JS-interop Raw <-> commonMain converters (roadmap #11). */
class EncounterCuratorPersistenceTest {
    @Test
    fun categoryWeightsRoundTrip() {
        val w = CategoryWeights(combat = 30, rp = 15, rumor = 15, merchant = 10, disease = 5, faction = 10, weather = 10, lore = 5)
        assertEquals(w, w.toRaw().toModel())
    }

    @Test
    fun rumorRoundTrip() {
        val r = Rumor(
            text = "Bandits massing near Oleg's",
            isQuestHook = true,
            questTemplateId = "tmpl-1",
            questTemplateName = "Clear the Bandits",
            location = "Hex 0203",
            sourceRegion = "Greenbelt",
            isConverted = true,
            convertedQuestId = "quest-9",
        )
        assertEquals(r, r.toRaw().toModel())
    }

    @Test
    fun rumorMinimalRoundTrip() {
        val r = Rumor(text = "A whisper in the dark")
        assertEquals(r, r.toRaw().toModel())
    }

    @Test
    fun merchantStockRoundTrip() {
        val m = MerchantStock(
            name = "Wandering Tinker",
            stockItems = listOf(StockItem("Healing Potion", 5), StockItem("Cold Iron Dagger", 12)),
            stockRemaining = 3,
            stockMax = 4,
            greeting = "Wares for the weary!",
            region = "Greenbelt",
            uuid = "Item.abc",
        )
        assertEquals(m, m.toRaw().toModel())
    }

    @Test
    fun stockItemRoundTrip() {
        val s = StockItem("Antidote", 2)
        assertEquals(s, s.toRaw().toModel())
    }
}

package at.posselt.pfrpg2e.kingdom.loot

import at.posselt.pfrpg2e.kingdom.data.RawLootManifestEntry
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.data.toRaw
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RawLootManifestTest {
    private fun raw(
        name: String? = "Blade",
        itemUuid: String? = null,
        packRef: String? = null,
        quantity: Int? = null,
        gpValue: Double? = null,
        cursed: Boolean? = null,
    ): RawLootManifestEntry {
        val obj = js("{}").unsafeCast<RawLootManifestEntry>()
        obj.itemUuid = itemUuid
        obj.packRef = packRef
        obj.name = name
        obj.quantity = quantity
        obj.gpValue = gpValue
        obj.cursed = cursed
        return obj
    }

    @Test
    fun decodeDefaultsMirrorThePureModelsContract() {
        val model = raw(quantity = null, gpValue = null, cursed = null).toModel()!!
        assertEquals(1, model.qty, "null quantity means one copy")
        assertEquals(0.0, model.gpValue)
        assertEquals(false, model.cursed)
    }

    @Test
    fun identityFallsBackFromNameToUuidToPackRef() {
        assertEquals("Item.abc", raw(name = null, itemUuid = "Item.abc").toModel()!!.name)
        assertEquals("pack.x.y", raw(name = "", itemUuid = null, packRef = "pack.x.y").toModel()!!.name)
        assertEquals(
            "Item.abc",
            raw(name = null, itemUuid = "Item.abc", packRef = "pack.x.y").toModel()!!.name,
            "the resolvable UUID outranks the fallback pack ref",
        )
        assertNull(raw(name = null).toModel(), "a row with no identity at all is dropped, not thrown on")
    }

    @Test
    fun ledgerEntryRoundTripsThroughRaw() {
        val entry = buildLedgerEntry(
            id = "loot-1815-1",
            turn = 12,
            sourceHexKey = "1815",
            items = listOf(
                LootManifestItem(name = "Blade", qty = 2, gpValue = 30.0, cursed = true),
                LootManifestItem(name = "Gems", qty = 1, gpValue = 15.5),
            ),
        )
        val back = entry.toRaw(sourceName = "Bandit Camp").toModel()
        assertEquals(entry, back)
        assertEquals(75.5, back.totalGp, 1e-9)
        assertEquals(1, back.cursedCount)
    }
}

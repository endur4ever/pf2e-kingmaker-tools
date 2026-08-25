package at.posselt.pfrpg2e.kingdom.loot

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.data.RawLootManifestEntry
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildTreasureLedgerContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LootAwardTest {
    private fun manifestRow(name: String, cursed: Boolean = false): RawLootManifestEntry {
        val obj = js("{}").unsafeCast<RawLootManifestEntry>()
        obj.name = name
        obj.quantity = 1
        obj.gpValue = 10.0
        obj.cursed = cursed
        return obj
    }

    private fun content(
        manifest: Array<RawLootManifestEntry>?,
        awarded: Boolean? = null,
    ): RawHexContent {
        val obj = js("{}").unsafeCast<RawHexContent>()
        obj.id = "hc1"
        obj.hexKey = "1815"
        obj.name = "Bandit Camp"
        obj.lootManifest = manifest
        obj.manifestAwarded = awarded
        return obj
    }

    @Test
    fun onlyAPreppedUnawardedHexOffersTreasure() {
        assertTrue(hasUnawardedManifest(content(arrayOf(manifestRow("Blade")))))
        assertFalse(hasUnawardedManifest(content(arrayOf(manifestRow("Blade")), awarded = true)), "awarded never re-offers")
        assertFalse(hasUnawardedManifest(content(emptyArray())), "an empty manifest is nothing to award")
        assertFalse(hasUnawardedManifest(content(null)))
        assertTrue(hasUnawardedManifest(content(arrayOf(manifestRow("Blade")), awarded = false)), "an explicit false still offers")
    }

    private fun kingdomWithLedger(vararg entries: TreasureLedgerEntry): KingdomData {
        val k = js("{}").unsafeCast<KingdomData>()
        k.treasureLedger = entries.map { entry ->
            val obj = js("{}").unsafeCast<at.posselt.pfrpg2e.kingdom.data.RawTreasureLedgerEntry>()
            obj.id = entry.id
            obj.turn = entry.turn
            obj.sourceHexKey = entry.sourceHexKey
            obj.sourceName = if (entry.id == "named") "Bandit Camp" else null
            obj.itemNames = entry.itemNames.toTypedArray()
            obj.totalGp = entry.totalGp
            obj.cursedCount = entry.cursedCount
            obj
        }.toTypedArray()
        return k
    }

    private fun entry(id: String, turn: Int, gp: Double, cursed: Int = 0) =
        TreasureLedgerEntry(id, turn, "1815", listOf("Blade"), gp, cursed)

    @Test
    fun theLedgerIsGmOnlyByData() {
        assertNull(buildTreasureLedgerContext(isGM = false, kingdom = kingdomWithLedger(entry("a", 1, 10.0))))
    }

    @Test
    fun rowsReadNewestFirstAndTotalTheWholeLedger() {
        val ctx = buildTreasureLedgerContext(
            isGM = true,
            kingdom = kingdomWithLedger(entry("old", 1, 100.0), entry("new", 9, 50.0)),
        )!!
        assertEquals(listOf("new", "old"), ctx.rows.map { it.id }, "newest first: the GM's question is what they just got")
        assertTrue(ctx.totalLabel.contains("150"), "total sums every entry")
        assertTrue(ctx.hasRows)
        assertFalse(ctx.atCap)
    }

    @Test
    fun sourceFallsBackToTheHexKeyWhenTheNameWasNeverCaptured() {
        val ctx = buildTreasureLedgerContext(
            isGM = true,
            kingdom = kingdomWithLedger(entry("named", 1, 10.0), entry("anon", 2, 10.0)),
        )!!
        val byId = ctx.rows.associateBy { it.id }
        assertEquals("Bandit Camp", byId["named"]?.source)
        assertEquals("1815", byId["anon"]?.source, "an entry from a deleted hex still reads")
    }

    @Test
    fun cursedRowsAreFlaggedAndAnEmptyLedgerIsNotNull() {
        val ctx = buildTreasureLedgerContext(
            isGM = true,
            kingdom = kingdomWithLedger(entry("clean", 1, 10.0), entry("hexed", 2, 10.0, cursed = 2)),
        )!!
        val byId = ctx.rows.associateBy { it.id }
        assertFalse(byId["clean"]!!.hasCursed)
        assertTrue(byId["hexed"]!!.hasCursed)
        assertEquals(2, byId["hexed"]!!.cursedCount)

        val empty = buildTreasureLedgerContext(isGM = true, kingdom = js("{}").unsafeCast<KingdomData>())!!
        assertEquals(0, empty.rows.size)
        assertFalse(empty.hasRows, "an empty ledger renders its empty state, not nothing")
    }
}

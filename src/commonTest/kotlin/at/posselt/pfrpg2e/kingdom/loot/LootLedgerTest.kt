package at.posselt.pfrpg2e.kingdom.loot

import kotlin.test.Test
import kotlin.test.assertEquals

/** Covers the pure manifest-totals and ledger cases of §7.1 of the loot-manifests plan. */
class LootLedgerTest {
    private fun item(
        name: String = "Longsword",
        qty: Int = 1,
        gpValue: Double = 10.0,
        cursed: Boolean = false,
    ) = LootManifestItem(name = name, qty = qty, gpValue = gpValue, cursed = cursed)

    private fun entry(
        id: String,
        turn: Int,
        totalGp: Double,
    ) = TreasureLedgerEntry(
        id = id, turn = turn, sourceHexKey = "12.4", itemNames = listOf("Longsword"),
        totalGp = totalGp, cursedCount = 0,
    )

    @Test
    fun totalsMultiplyQuantityByPerUnitGpAcrossMixedRows() {
        // The plan's own arithmetic fixture: 3 × 15.0 + 1 × 40.5 = 85.5.
        val totals = manifestTotals(listOf(item(qty = 3, gpValue = 15.0), item(qty = 1, gpValue = 40.5)))
        assertEquals(85.5, totals.totalGp, 1e-9)
        assertEquals(0, totals.cursedCount)
    }

    @Test
    fun aNegativeQuantityClampsToZeroAndCannotSubtractTreasure() {
        // A corrupt row must not make the award look like the party got POORER.
        val totals = manifestTotals(listOf(item(qty = -2, gpValue = 100.0), item(qty = 2, gpValue = 5.0)))
        assertEquals(10.0, totals.totalGp, 1e-9)
        // Zero is the in-range edge of the clamp: it contributes nothing and stays legal.
        assertEquals(0.0, manifestTotals(listOf(item(qty = 0, gpValue = 100.0))).totalGp, 1e-9)
    }

    @Test
    fun aCursedItemWithQuantityThreeCountsOnceNotThrice() {
        // The Cleanse Item cross-link points at an item, not at each copy of it.
        val totals = manifestTotals(
            listOf(
                item(name = "Cursed Blade", qty = 3, gpValue = 1.0, cursed = true),
                item(name = "Plain Shield", qty = 2, gpValue = 1.0),
                item(name = "Cursed Ring", qty = 1, gpValue = 1.0, cursed = true),
            )
        )
        assertEquals(2, totals.cursedCount)
    }

    @Test
    fun anEmptyManifestTotalsToZeroGpAndZeroCursed() {
        val totals = manifestTotals(emptyList())
        assertEquals(0.0, totals.totalGp, 1e-9)
        assertEquals(0, totals.cursedCount)
    }

    @Test
    fun buildLedgerEntryKeepsManifestNameOrderAndCarriesTheTotals() {
        // The ledger table should read in the order the GM prepped the hex, not sorted.
        val items = listOf(
            item(name = "Zeta", qty = 1, gpValue = 5.0),
            item(name = "Alpha", qty = 2, gpValue = 10.0, cursed = true),
            item(name = "Middle", qty = 1, gpValue = 0.5),
        )
        val built = buildLedgerEntry(id = "loot-12.4-1", turn = 7, sourceHexKey = "12.4", items = items)
        assertEquals(listOf("Zeta", "Alpha", "Middle"), built.itemNames)
        assertEquals(25.5, built.totalGp, 1e-9)
        assertEquals(1, built.cursedCount)
        assertEquals("loot-12.4-1", built.id)
        assertEquals(7, built.turn)
        assertEquals("12.4", built.sourceHexKey)
    }

    @Test
    fun appendingUpToTheCapLeavesEveryEntryUntouched() {
        val existing = listOf(entry("e1", 1, 10.0), entry("e2", 2, 20.0))
        val out = appendLedgerEntry(existing, entry("e3", 3, 30.0), cap = 3)
        assertEquals(listOf("e1", "e2", "e3"), out.map { it.id })
    }

    @Test
    fun appendingPastTheCapTrimsTheOldestEntryFirst() {
        val existing = listOf(entry("e1", 1, 10.0), entry("e2", 2, 20.0), entry("e3", 3, 30.0))
        val out = appendLedgerEntry(existing, entry("e4", 4, 40.0), cap = 3)
        assertEquals(listOf("e2", "e3", "e4"), out.map { it.id })
    }

    @Test
    fun theDefaultCapIsTwoHundredEntries() {
        // Pin the literal, not the constant against itself -- a tautology survives any cap change.
        assertEquals(200, TREASURE_LEDGER_CAP)
        val existing = (1..TREASURE_LEDGER_CAP).map { entry("e" + it, it, 1.0) }
        val out = appendLedgerEntry(existing, entry("new", TREASURE_LEDGER_CAP + 1, 1.0))
        assertEquals(TREASURE_LEDGER_CAP, out.size)
        assertEquals("e2", out.first().id)
        assertEquals("new", out.last().id)
    }

    @Test
    fun realizedGpIncludesBothEndpointTurnsAndExcludesTheTurnsJustOutside() {
        val ledger = (1..5).map { entry("e" + it, turn = it, totalGp = it * 10.0) }
        // Turns 2, 3 and 4 are inside; turns 1 and 5 sit one turn outside each endpoint.
        assertEquals(90.0, realizedGpInWindow(ledger, fromTurn = 2, toTurn = 4), 1e-9)
        // A single-turn window is a valid closed window, not an empty one.
        assertEquals(30.0, realizedGpInWindow(ledger, fromTurn = 3, toTurn = 3), 1e-9)
    }

    @Test
    fun anInvertedWindowAndAnEmptyLedgerBothRealizeZeroGp() {
        val ledger = listOf(entry("e1", 3, 30.0))
        assertEquals(0.0, realizedGpInWindow(ledger, fromTurn = 4, toTurn = 2), 1e-9)
        assertEquals(0.0, realizedGpInWindow(emptyList(), fromTurn = 0, toTurn = 100), 1e-9)
    }
}

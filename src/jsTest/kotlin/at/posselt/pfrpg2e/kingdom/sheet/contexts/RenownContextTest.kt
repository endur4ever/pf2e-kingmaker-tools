package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.data.kingdom.settlementPurchaseAccessLevels
import at.posselt.pfrpg2e.kingdom.data.RawPcFactionRenown
import at.posselt.pfrpg2e.kingdom.data.RawPcRenown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenownContextTest {
    private fun row(
        uuid: String = "pc-1",
        populace: Int = 40,
        epithets: Array<String>? = arrayOf("theUntiring"),
        tier: Int? = 1,
        factions: Array<RawPcFactionRenown>? = null,
    ): RawPcRenown {
        val obj = js("{}").unsafeCast<RawPcRenown>()
        obj.actorUuid = uuid
        obj.actorName = "Alice"
        obj.populace = populace
        obj.epithets = epithets
        obj.purchaseAccessTier = tier
        obj.factionRenown = factions
        return obj
    }

    private fun faction(name: String, value: Int): RawPcFactionRenown {
        val obj = js("{}").unsafeCast<RawPcFactionRenown>()
        obj.factionName = name
        obj.renown = value
        return obj
    }

    private fun build(isGM: Boolean, isOwn: Boolean, rows: Array<RawPcRenown>? = arrayOf(row())) =
        buildRenownCardContext(
            pcs = listOf(RenownCardPc(actorUuid = "pc-1", name = "Alice", roleLabel = "Ruler", isOwn = isOwn)),
            renownRows = rows,
            isGM = isGM,
            epithetLabel = { id -> "label:$id" },
            factionLabel = { name, value -> "$name $value" },
        )

    @Test
    fun theGmSeesEveryNumber() {
        val card = build(isGM = true, isOwn = false).pcs.single()
        assertEquals(40, card.populace)
        assertEquals(40, card.populacePct)
        assertEquals(1, card.purchaseAccessTier)
        assertTrue(card.hasNumbers)
    }

    @Test
    fun aPlayerSeesTheirOwnNumbersInFull() {
        val card = build(isGM = false, isOwn = true).pcs.single()
        assertEquals(40, card.populace)
        assertEquals(1, card.purchaseAccessTier)
    }

    @Test
    fun anotherPlayersNumbersAreABSENTNotZero() {
        // the load-bearing rule: players own the party actor, so anything that reaches the
        // context is readable whatever the template renders. Absence is the gate.
        val card = build(isGM = false, isOwn = false).pcs.single()
        assertNull(card.populace)
        assertNull(card.populacePct)
        assertNull(card.purchaseAccessTier)
        assertNull(card.factionRenown)
        assertTrue(!card.hasNumbers)
    }

    @Test
    fun epithetsStayVisibleToEveryoneBecauseTheyArePublicHonours() {
        val card = build(isGM = false, isOwn = false).pcs.single()
        assertEquals(listOf("label:theUntiring"), card.epithets.map { it.label })
        assertTrue(card.hasEpithets)
    }

    @Test
    fun aPcWithNoLedgerStillGetsACard() {
        val card = build(isGM = true, isOwn = true, rows = emptyArray()).pcs.single()
        assertEquals("Alice", card.name)
        assertEquals(0, card.populace, "no renown yet is a true thing to show")
        assertTrue(!card.hasEpithets)
    }

    @Test
    fun theBarIsAPercentageOfTheCeilingAndNeverOverflows() {
        assertEquals(0, build(isGM = true, isOwn = true, rows = arrayOf(row(populace = 0))).pcs.single().populacePct)
        assertEquals(100, build(isGM = true, isOwn = true, rows = arrayOf(row(populace = 100))).pcs.single().populacePct)
        // a hand-edited world can hold an out-of-range value; the bar must not exceed the track
        assertEquals(100, build(isGM = true, isOwn = true, rows = arrayOf(row(populace = 400))).pcs.single().populacePct)
        assertEquals(0, build(isGM = true, isOwn = true, rows = arrayOf(row(populace = -20))).pcs.single().populacePct)
    }

    @Test
    fun namelessFactionChipsAreDroppedRatherThanRenderedBlank() {
        val rows = arrayOf(row(factions = arrayOf(faction("Pitax", 12), faction("  ", 5))))
        val chips = build(isGM = true, isOwn = true, rows = rows).pcs.single().factionRenown!!
        assertEquals(listOf("Pitax"), chips.map { it.factionName })
    }

    @Test
    fun theSettlementPerkTakesTheBestTierInTheParty() {
        // settlement-scoped by decision: the dialog has no shopper, so the realm's best reputation
        // is what opens the doors
        assertEquals(0, settlementPurchaseAccessLevels(emptyList()))
        assertEquals(0, settlementPurchaseAccessLevels(listOf(0, 0)))
        assertEquals(2, settlementPurchaseAccessLevels(listOf(1, 2, 0)))
        assertEquals(2, settlementPurchaseAccessLevels(listOf(9)), "clamped to the max tier")
    }
}

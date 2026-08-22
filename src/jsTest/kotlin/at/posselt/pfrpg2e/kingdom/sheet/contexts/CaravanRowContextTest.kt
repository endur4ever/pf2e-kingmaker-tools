package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.data.RawCaravan
import at.posselt.pfrpg2e.kingdom.data.RawCaravanShipment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers what the caravan board rows expose, which was previously only a prose summary. */
class CaravanRowContextTest {
    private fun caravan(
        id: String = "c1",
        status: String = "inTransit",
        partnerName: String? = "Pitax",
    ) = RawCaravan(
        id = id,
        kind = "commodity",
        originHexKey = "1.1",
        destHexKey = "5.5",
        originLabel = "Capital",
        destLabel = "Pitax Market",
        partnerName = partnerName,
        cargoCommodity = "lumber",
        cargoAmount = 20,
        cargoRp = null,
        etaTurns = 4,
        turnsRemaining = 2,
        status = status,
    )

    private fun shipment(status: String = "inTransit") = RawCaravanShipment(
        id = "s1",
        itemName = "Longsword",
        itemQuantity = 2,
        itemLevel = 3,
        itemBulk = "1",
        itemPriceGp = 15.0,
        originHexKey = "1.1",
        destHexKey = "5.5",
        originLabel = "Capital",
        destLabel = "Restov",
        caravanType = "medium",
        goldCost = 5.0,
        etaTurns = 3,
        turnsRemaining = 1,
        path = arrayOf("1.1", "2.2"),
        currentHexKey = "2.2",
        status = status,
    )

    @Test
    fun onlyCaravansStillInTransitAppearOnTheBoard() {
        // A delivered caravan lingering on the board would look like cargo still at risk.
        val rows = arrayOf(caravan(id = "a"), caravan(id = "b", status = "delivered"))
            .toCaravanRowContexts()
        assertEquals(listOf("a"), rows.map { it.id })
    }

    @Test
    fun theTradePartnerIsShownWhenThereIsOne() {
        assertEquals("Pitax", caravanRows().single().partner)
    }

    @Test
    fun aCaravanWithNoPartnerFallsBackToItsDestination() {
        // Internal hauls have no diplomatic partner; the row must still say where it is going.
        assertEquals("Pitax Market", caravanRows(partnerName = null).single().partner)
    }

    @Test
    fun anUnresolvableRouteShowsNoRaidDcRatherThanAWrongOne() {
        assertNull(caravanRows().single().raidDc)
    }

    @Test
    fun theResolvedRaidDcReachesTheRow() {
        val rows = arrayOf(caravan()).toCaravanRowContexts { 13 }
        assertEquals(13, rows.single().raidDc)
    }

    @Test
    fun theRowCarriesBothRemainingAndTotalTurns() {
        // The board shows progress as remaining/total, so dropping either makes it meaningless.
        val row = caravanRows().single()
        assertEquals(2, row.turnsRemaining)
        assertEquals(4, row.etaTurns)
    }

    @Test
    fun theCargoIsExposedSeparatelyFromTheRouteSummary() {
        val row = caravanRows().single()
        assertTrue(row.cargo.contains("20"), "cargo should carry the amount, was ${row.cargo}")
        assertTrue(row.summary.contains("Capital"), "summary should still carry the route")
    }

    @Test
    fun aShipmentIsAddressedToItsDestinationNotAPartner() {
        assertEquals("Restov", arrayOf(shipment()).toShipmentRowContexts().single().partner)
    }

    @Test
    fun onlyShipmentsStillInTransitAppearOnTheBoard() {
        assertTrue(arrayOf(shipment(status = "delivered")).toShipmentRowContexts().isEmpty())
    }

    @Test
    fun theResolvedShipmentRaidDcReachesTheRow() {
        assertEquals(9, arrayOf(shipment()).toShipmentRowContexts { 9 }.single().raidDc)
    }

    private fun caravanRows(partnerName: String? = "Pitax") =
        arrayOf(caravan(partnerName = partnerName)).toCaravanRowContexts()
}

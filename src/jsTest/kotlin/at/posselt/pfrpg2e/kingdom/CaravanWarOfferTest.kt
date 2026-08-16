package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawCaravan
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaravanWarOfferTest {
    private fun caravan(
        id: String,
        partner: String?,
        status: String = "inTransit",
        commodity: String? = null,
        amount: Int = 0,
        rp: Int? = null,
    ) = RawCaravan(
        id = id, kind = if (rp != null) "buyFromPartner" else "sellToPartner",
        originHexKey = "0,0", destHexKey = "1,1", originLabel = "Home", destLabel = "There",
        partnerName = partner, cargoCommodity = commodity, cargoAmount = amount, cargoRp = rp,
        etaTurns = 3, turnsRemaining = 2, status = status,
    )

    private fun kingdom(vararg caravans: RawCaravan): KingdomData =
        unsafeJso<dynamic> {
            this.caravans = arrayOf(*caravans)
            commodities = unsafeJso<dynamic> {
                now = unsafeJso<dynamic> { food = 0; lumber = 0; stone = 0; ore = 0; luxuries = 0 }
            }
            resourcePoints = unsafeJso<dynamic> { now = 0 }
        }.unsafeCast<KingdomData>()

    @Test
    fun onlyShipmentsStillOnTheRoadToThatPartnerAreOffered() {
        val k = kingdom(
            caravan("a", "Pitax"),
            caravan("b", "Pitax", status = "delivered"),
            caravan("c", "Mivon"),
            caravan("d", null),
        )
        assertEquals(listOf("a"), k.caravansInTransitTo(setOf("Pitax")).map { it.id })
    }

    @Test
    fun recallingASaleBringsItsCommoditiesHome() {
        val k = kingdom(caravan("a", "Pitax", commodity = "lumber", amount = 7))

        assertTrue(k.recallCaravan("a"))

        assertEquals("recalled", k.caravans!![0].status)
        assertEquals(7, k.commodities.now.lumber)
        // The tick ignores anything not inTransit, so the recall also stops it advancing.
        assertEquals(emptyList(), k.caravansInTransitTo(setOf("Pitax")).map { it.id })
    }

    @Test
    fun recallingAPurchaseRefundsItsResourcePoints() {
        val k = kingdom(caravan("a", "Pitax", rp = 12))

        assertTrue(k.recallCaravan("a"))

        assertEquals(12, k.resourcePoints.now)
        assertEquals("recalled", k.caravans!![0].status)
    }

    @Test
    fun aShipmentCannotBeRecalledTwice() {
        // Offer cards persist in chat; a second click must not pay the cargo out again.
        val k = kingdom(caravan("a", "Pitax", commodity = "ore", amount = 4))

        assertTrue(k.recallCaravan("a"))
        assertFalse(k.recallCaravan("a"))

        assertEquals(4, k.commodities.now.ore)
    }

    @Test
    fun recallingAnUnknownShipmentDoesNothing() {
        val k = kingdom(caravan("a", "Pitax"))
        assertFalse(k.recallCaravan("nope"))
    }
}

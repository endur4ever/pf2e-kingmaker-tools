package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shipments and caravans travel the same routes, so their raid DCs must move together. These pin
 * the parity that an inline duplicate of the formula used to break.
 */
class ShipmentRaidDcTest {
    private fun safety(claimed: Double, roaded: Boolean = false) =
        CaravanRouteSafety(claimedFraction = claimed, fullyRoadedThroughClaimed = roaded)

    @Test
    fun aFullyRoadedClaimedRouteEarnsTheRoadDiscount() {
        // The whole point of paying to road a trade route; shipments used to be excluded.
        val roaded = shipmentRaidDc(safety(1.0, roaded = true))
        val unroaded = shipmentRaidDc(safety(1.0, roaded = false))
        assertEquals(unroaded - 1, roaded)
    }

    @Test
    fun aWildernessRouteIsFiveHarderThanABuiltOutTradeRoad() {
        assertEquals(5, shipmentRaidDc(safety(0.0)) - shipmentRaidDc(safety(1.0, roaded = true)))
    }

    @Test
    fun itMatchesACaravanOnTheSameRouteWithNoPartner() {
        listOf(safety(0.0), safety(0.5), safety(1.0), safety(1.0, roaded = true)).forEach { s ->
            assertEquals(
                caravanRaidDc(CARAVAN_BASE_RAID_DC, null, false, s.claimedFraction, s.fullyRoadedThroughClaimed),
                shipmentRaidDc(s),
                "shipment and caravan disagree for $s",
            )
        }
    }

    @Test
    fun partnerStandingNeverAppliesToAShipment() {
        // Shipments go to a settlement, not a diplomatic partner, so a friendly neighbour must not
        // make an internal item delivery safer the way it does for a commodity caravan.
        val s = safety(0.5)
        val friendlyCaravan = caravanRaidDc(
            baseDc = CARAVAN_BASE_RAID_DC,
            partnerStanding = 6,
            atWar = false,
            claimedFraction = s.claimedFraction,
            fullyRoadedThroughClaimed = s.fullyRoadedThroughClaimed,
        )
        assertEquals(friendlyCaravan + 3, shipmentRaidDc(s))
    }

    @Test
    fun theDcNeverDropsBelowFive() {
        assertTrue(shipmentRaidDc(safety(1.0, roaded = true), baseDc = 6) >= 5)
    }
}

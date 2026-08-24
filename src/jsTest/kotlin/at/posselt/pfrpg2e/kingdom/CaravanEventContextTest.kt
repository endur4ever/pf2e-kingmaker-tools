package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawCaravan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A CaravanEvent has to carry enough context to be narrated without parsing its own summary string.
 * The Meanwhile digest scores a raid as "lost out of carried" and names the route, and neither the
 * cargo size nor the endpoints were reachable from the event before.
 */
class CaravanEventContextTest {
    private fun caravan(amount: Int = 4, turnsRemaining: Int = 1) = RawCaravan(
        id = "c1",
        kind = "sellToPartner",
        originHexKey = "6019",
        destHexKey = "4021",
        originLabel = "Tuskwater",
        destLabel = "Pitax",
        partnerName = "Pitax",
        cargoCommodity = "lumber",
        cargoAmount = amount,
        cargoRp = null,
        etaTurns = 3,
        turnsRemaining = turnsRemaining,
        status = "inTransit",
    )

    private fun tick(amount: Int, turnsRemaining: Int, raidRoll: Int) = tickCaravans(
        listOf(
            CaravanTickInput(
                caravan(amount = amount, turnsRemaining = turnsRemaining),
                raidDc = 11,
                raidRoll = raidRoll,
                rdPerCommodity = 1.0,
            ),
        ),
    ).events.single()

    @Test
    fun aRaidReportsTheCargoItWasCarryingBeforeTheRaid() {
        // The denominator for "how bad was this": losing 2 of 10 is not losing 2 of 2. Reading the
        // post-tick amount here would understate every raid.
        val e = tick(amount = 10, turnsRemaining = 3, raidRoll = 1)
        assertEquals(CaravanEventKind.RAIDED, e.kind)
        assertEquals(10, e.cargoAmount, "cargoAmount must be the pre-raid figure")
        assertEquals(true, e.cargoLost in 1..10)
    }

    @Test
    fun aRaidNamesTheRouteAndTheCommodity() {
        val e = tick(amount = 10, turnsRemaining = 3, raidRoll = 1)
        assertEquals("Tuskwater", e.originLabel)
        assertEquals("Pitax", e.destLabel)
        assertEquals("lumber", e.cargoCommodity, "raid beats need the commodity; it used to be null")
    }

    @Test
    fun aDeliveryAlsoCarriesRouteAndCargo() {
        val e = tick(amount = 4, turnsRemaining = 1, raidRoll = 20)
        assertEquals(CaravanEventKind.DELIVERED, e.kind)
        assertEquals("Tuskwater", e.originLabel)
        assertEquals("Pitax", e.destLabel)
        assertEquals("lumber", e.cargoCommodity)
        assertEquals(4, e.cargoAmount)
    }

    @Test
    fun everyEventNamesItsPartner() {
        // The buy-side delivery previously built its event without a partnerName at all.
        listOf(tick(4, 1, 20), tick(10, 3, 1)).forEach {
            assertNotNull(it.partnerName, "event of kind ${it.kind} lost its partner")
        }
    }
}

package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawCaravan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun caravan(
    id: String = "c1",
    kind: String = "sellToPartner",
    commodity: String? = "lumber",
    amount: Int = 4,
    turnsRemaining: Int = 1,
    status: String = "inTransit",
): RawCaravan = RawCaravan(
    id = id,
    kind = kind,
    originHexKey = "6019",
    destHexKey = "4021",
    originLabel = "6.19",
    destLabel = "Pitax",
    partnerName = "Pitax",
    cargoCommodity = commodity,
    cargoAmount = amount,
    cargoRp = null,
    etaTurns = 3,
    turnsRemaining = turnsRemaining,
    status = status,
)

class CaravanTickTest {

    @Test
    fun `sell caravan delivers bonus resource dice on arrival`() {
        // raidRoll >= raidDc => no raid; arrives (turnsRemaining 1 -> 0); 4 commodities x 1 RD = 4
        val result = tickCaravans(
            listOf(CaravanTickInput(caravan(amount = 4, turnsRemaining = 1), raidDc = 11, raidRoll = 20, rdPerCommodity = 1.0))
        )
        assertEquals(4, result.bonusResourceDice)
        assertTrue(result.remaining.isEmpty(), "delivered caravan leaves the in-transit list")
        assertEquals(CaravanEventKind.DELIVERED, result.events.single().kind)
    }

    @Test
    fun `in-transit caravan advances and stays`() {
        val result = tickCaravans(
            listOf(CaravanTickInput(caravan(turnsRemaining = 3), raidDc = 11, raidRoll = 20, rdPerCommodity = 1.0))
        )
        assertEquals(0, result.bonusResourceDice)
        assertEquals(1, result.remaining.size)
        assertEquals(2, result.remaining.single().turnsRemaining)
    }

    @Test
    fun `raid reduces cargo and the delivered value`() {
        // raidRoll < raidDc => raided; 4 -> lose 1 -> 3 delivered; arrives -> 3 RD
        val result = tickCaravans(
            listOf(CaravanTickInput(caravan(amount = 4, turnsRemaining = 1), raidDc = 15, raidRoll = 5, rdPerCommodity = 1.0))
        )
        assertEquals(3, result.bonusResourceDice)
        assertEquals(CaravanEventKind.DELIVERED, result.events.single().kind)
        assertEquals(1, result.events.single().cargoLost)
    }

    @Test
    fun `caravan whose cargo is fully lost is reported lost and removed`() {
        val result = tickCaravans(
            listOf(CaravanTickInput(caravan(amount = 1, turnsRemaining = 1), raidDc = 20, raidRoll = 1, rdPerCommodity = 1.0))
        )
        assertEquals(0, result.bonusResourceDice)
        assertTrue(result.remaining.isEmpty())
        assertEquals(CaravanEventKind.LOST, result.events.single().kind)
    }

    @Test
    fun `settlement transfer delivers commodities not resource dice`() {
        val result = tickCaravans(
            listOf(
                CaravanTickInput(
                    caravan(kind = "settlementTransfer", commodity = "stone", amount = 6, turnsRemaining = 1),
                    raidDc = 11, raidRoll = 20, rdPerCommodity = 1.0,
                )
            )
        )
        assertEquals(0, result.bonusResourceDice)
        assertEquals(6, result.deliveredCommodities["stone"])
    }

    @Test
    fun `raid dc rises at war and falls with friendly standing and claimed routes`() {
        assertEquals(11, caravanRaidDc(11, partnerStanding = 0, atWar = false, claimedFraction = 0.0))
        assertEquals(15, caravanRaidDc(11, partnerStanding = 0, atWar = true, claimedFraction = 0.0))
        assertEquals(7, caravanRaidDc(11, partnerStanding = 8, atWar = false, claimedFraction = 0.0)) // -4 from standing/2
        assertEquals(7, caravanRaidDc(11, partnerStanding = 0, atWar = false, claimedFraction = 1.0)) // -4 from claimed
    }

    @Test
    fun `sale rate scales with standing and alliance tier`() {
        assertEquals(1.0, caravanRdPerCommodity(standing = 0, allianceLevel = null))
        assertEquals(2.0, caravanRdPerCommodity(standing = 0, allianceLevel = "tribute"))
        assertEquals(1.5, caravanRdPerCommodity(standing = 0, allianceLevel = "alliance"))
    }

    @Test
    fun `raid loss is a quarter rounded with a floor of one`() {
        assertEquals(1, caravanRaidLoss(4))
        assertEquals(1, caravanRaidLoss(1))
        assertEquals(2, caravanRaidLoss(8))
    }

    @Test
    fun `purchase cost is RP per commodity discounted by standing and treaty`() {
        assertEquals(8, caravanPurchaseCost("lumber", 4, standing = 0, allianceLevel = null)) // 4 x 2 RP
        assertEquals(8, caravanPurchaseCost("luxuries", 2, standing = 0, allianceLevel = null)) // 2 x 4 RP
        assertEquals(6, caravanPurchaseCost("lumber", 4, standing = 0, allianceLevel = "tribute")) // 8 x 0.75
    }

    @Test
    fun `price multiplier discounts for allies and marks up for hostiles`() {
        assertEquals(1.0, caravanPriceMultiplier(0, null))
        assertEquals(0.75, caravanPriceMultiplier(0, "tribute"))
        assertTrue(caravanPriceMultiplier(-8, null) > 1.0, "hostile partners charge more")
    }

    @Test
    fun `buy caravan delivers purchased commodities on arrival`() {
        val result = tickCaravans(
            listOf(
                CaravanTickInput(
                    caravan(kind = "buyFromPartner", commodity = "ore", amount = 2, turnsRemaining = 1),
                    raidDc = 11, raidRoll = 20, rdPerCommodity = 1.0,
                )
            )
        )
        assertEquals(0, result.bonusResourceDice)
        assertEquals(2, result.deliveredCommodities["ore"])
    }
}

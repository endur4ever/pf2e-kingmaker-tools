package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawCaravanShipment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

private fun shipment(
    id: String = "s1",
    itemName: String = "Longsword",
    itemQuantity: Int = 2,
    itemLevel: Int = 3,
    itemBulk: String = "1",
    itemPriceGp: Double = 15.0,
    originHexKey: String = "1001",
    destHexKey: String = "1004",
    caravanType: String = "medium",
    goldCost: Double = 20.0,
    etaTurns: Int = 3,
    turnsRemaining: Int = 3,
    path: Array<String> = arrayOf("1001", "1002", "1003", "1004"),
    currentHexKey: String = "1001",
    status: String = "inTransit",
): RawCaravanShipment = RawCaravanShipment(
    id = id,
    itemName = itemName,
    itemQuantity = itemQuantity,
    itemLevel = itemLevel,
    itemBulk = itemBulk,
    itemPriceGp = itemPriceGp,
    originHexKey = originHexKey,
    destHexKey = destHexKey,
    originLabel = "Origin",
    destLabel = "Dest",
    caravanType = caravanType,
    goldCost = goldCost,
    etaTurns = etaTurns,
    turnsRemaining = turnsRemaining,
    path = path,
    currentHexKey = currentHexKey,
    status = status,
)

class CaravanShipmentTest {

    @Test
    fun parseBulkMapsValuesCorrectly() {
        assertEquals(0.1, parseBulk("L"))
        assertEquals(0.1, parseBulk("l"))
        assertEquals(0.0, parseBulk("-"))
        assertEquals(0.0, parseBulk("0"))
        assertEquals(0.0, parseBulk(""))
        assertEquals(1.0, parseBulk("1"))
        assertEquals(2.5, parseBulk("2.5"))
        assertEquals(1.0, parseBulk("invalid"))
    }

    @Test
    fun upfrontCostCalculatesCorrectly() {
        // medium caravan: base cost 10.0, route cost 4.0, bulk 1.0, quantity 2.
        // total bulk = 2.0.
        // fee = (10.0 * 4.0) + (2.0 * 0.5 * 4.0) = 40.0 + 4.0 = 44.0.
        val baseCostPerType = 10.0
        val routeCost = 4.0
        val totalBulk = parseBulk("1") * 2
        val fee = (baseCostPerType * routeCost) + (totalBulk * 0.5 * routeCost)
        assertEquals(44.0, fee)
    }

    @Test
    fun caravanEtaScalesWithTypeSpeed() {
        // Route cost = 6.0
        // Light (speed 4.0) -> eta = ceil(6.0 / 4.0) = 2
        assertEquals(2, caravanEtaTurns(6.0, 4.0))
        // Medium (speed 3.0) -> eta = ceil(6.0 / 3.0) = 2
        assertEquals(2, caravanEtaTurns(6.0, 3.0))
        // Heavy (speed 2.0) -> eta = ceil(6.0 / 2.0) = 3
        assertEquals(3, caravanEtaTurns(6.0, 2.0))
    }

    @Test
    fun inTransitShipmentTicksAndAdvancesAlongPath() {
        // s1: eta = 3, remaining = 3, path size = 4
        // first tick: elapsed = 1. progressIdx = 4 * 1/3 = 1.33 -> 1. nextHex = path[1] = "1002"
        val input = ShipmentTickInput(
            shipment = shipment(etaTurns = 3, turnsRemaining = 3, currentHexKey = "1001"),
            raidDc = 15,
            raidRoll = 20, // no raid
        )
        val result = tickShipments(listOf(input))
        assertEquals(1, result.remaining.size)
        assertTrue(result.delivered.isEmpty())
        val rem = result.remaining.single()
        assertEquals(2, rem.turnsRemaining)
        assertEquals("1002", rem.currentHexKey)
    }

    @Test
    fun shipmentDeliveredOnFinalTurn() {
        // s1: eta = 3, remaining = 1, path size = 4
        // elapsed = 3. progressIdx = 4 * 3/3 = 3 (last index). nextHex = path[3] = "1004"
        val input = ShipmentTickInput(
            shipment = shipment(etaTurns = 3, turnsRemaining = 1, currentHexKey = "1003"),
            raidDc = 15,
            raidRoll = 20, // no raid
        )
        val result = tickShipments(listOf(input))
        assertTrue(result.remaining.isEmpty())
        assertEquals(1, result.delivered.size)
        val del = result.delivered.single()
        assertEquals(0, del.turnsRemaining)
        assertEquals("1004", del.currentHexKey)
        assertEquals("delivered", del.status)
    }

    @Test
    fun shipmentRaidReducesQuantity() {
        // s1: quantity = 4, turnsRemaining = 2. Raid roll < DC -> loses 25% (1 item) -> 3 remaining.
        val input = ShipmentTickInput(
            shipment = shipment(itemQuantity = 4, etaTurns = 3, turnsRemaining = 2),
            raidDc = 15,
            raidRoll = 5,
        )
        val result = tickShipments(listOf(input))
        assertEquals(1, result.remaining.size)
        val rem = result.remaining.single()
        assertEquals(3, rem.itemQuantity)
        assertEquals(CaravanEventKind.RAIDED, result.events.single().kind)
        assertEquals(1, result.events.single().cargoLost)
    }

    @Test
    fun shipmentLostWhenQuantityDropsToZero() {
        // s1: quantity = 1. Raid roll < DC -> loses 25% (min 1) -> 0 remaining -> lost.
        val input = ShipmentTickInput(
            shipment = shipment(itemQuantity = 1, etaTurns = 3, turnsRemaining = 2),
            raidDc = 15,
            raidRoll = 5,
        )
        val result = tickShipments(listOf(input))
        assertTrue(result.remaining.isEmpty())
        assertTrue(result.delivered.isEmpty())
        assertEquals(CaravanEventKind.LOST, result.events.single().kind)
        assertEquals(1, result.events.single().cargoLost)
    }
}

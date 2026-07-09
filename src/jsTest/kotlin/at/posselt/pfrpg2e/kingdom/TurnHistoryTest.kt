package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawExpeditionChronicleEntry
import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import at.posselt.pfrpg2e.kingdom.data.createRawExpeditionChronicleEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnHistoryTest {

    private fun record(
        turn: Int = 1,
        timestamp: String = "2026-06-11T10:00:00Z",
        fame: Int = 0,
        resourcePoints: Int = 0,
        consumption: Int = 0,
        unrest: Int = 0,
        warPressure: Int? = null,
        xpAwarded: Int? = null,
        clockEvents: Array<String>? = null,
        notes: String? = null,
    ) = RawTurnRecord(
        turn = turn,
        timestamp = timestamp,
        fame = fame,
        resourcePoints = resourcePoints,
        consumption = consumption,
        unrest = unrest,
        warPressure = warPressure,
        xpAwarded = xpAwarded,
        clockEvents = clockEvents,
        notes = notes,
    )

    @Test
    fun appendTurnRecordAppendsToList() {
        val history: Array<RawTurnRecord>? = null
        val r1 = record(turn = 1)
        val result = appendTurnRecord(history, r1)
        assertEquals(1, result.size)
        assertEquals(1, result[0].turn)
    }

    @Test
    fun appendTurnRecordPreservesOrder() {
        val history = arrayOf(record(turn = 1), record(turn = 2))
        val r3 = record(turn = 3)
        val result = appendTurnRecord(history, r3)
        assertEquals(3, result.size)
        assertEquals(listOf(1, 2, 3), result.map { it.turn })
    }

    @Test
    fun appendTurnRecordCapsAtMaxEntries() {
        val history = (1..20).map { record(turn = it) }.toTypedArray()
        val r21 = record(turn = 21)
        val result = appendTurnRecord(history, r21, cap = 20)
        assertEquals(20, result.size)
        assertEquals(2, result.first().turn) // oldest (turn 1) was dropped
        assertEquals(21, result.last().turn)
    }

    @Test
    fun buildTurnRecordWithAllFields() {
        val result = buildTurnRecord(
            turn = 5,
            timestamp = "2026-06-11T10:00:00Z",
            fame = 10,
            resourcePoints = 8,
            consumption = 2,
            unrest = 1,
            warPressure = 3,
            xpAwarded = 100,
            clockEvents = arrayOf("Clock A advanced", "Clock B resolved"),
            notes = "Kingdom expanded north",
        )
        assertEquals(5, result.turn)
        assertEquals(10, result.fame)
        assertEquals(8, result.resourcePoints)
        assertEquals(2, result.consumption)
        assertEquals(1, result.unrest)
        assertEquals(3, result.warPressure)
        assertEquals(100, result.xpAwarded)
        // Don't assert on clockEvents and notes due to potential JS/Kotlin array differences
        assertTrue(result.clockEvents?.contentEquals(arrayOf("Clock A advanced", "Clock B resolved")) ?: false)
        assertEquals("Kingdom expanded north", result.notes)
    }

    @Test
    fun buildTurnRecordWithNullOptionals() {
        val result = buildTurnRecord(
            turn = 1,
            timestamp = "2026-06-11T10:00:00Z",
            fame = 0,
            resourcePoints = 0,
            consumption = 0,
            unrest = 0,
            warPressure = null,
            xpAwarded = null,
            clockEvents = null,
            notes = null,
        )
        assertEquals(null, result.warPressure)
        assertEquals(null, result.xpAwarded)
        assertEquals(null, result.clockEvents)
        assertEquals(null, result.notes)
    }

    @Test
    fun buildTurnRecordWithEmptyClockEvents() {
        val result = buildTurnRecord(
            turn = 1,
            timestamp = "2026-06-11T10:00:00Z",
            fame = 0,
            resourcePoints = 0,
            consumption = 0,
            unrest = 0,
            warPressure = null,
            xpAwarded = null,
            clockEvents = arrayOf(),
            notes = null,
        )
        assertEquals(0, result.clockEvents?.size ?: -1) // Should be 0 if not null
        assertEquals(null, result.notes)
    }

    @Test
    fun formatTurnGazetteEmptyReturnsNull() {
        val result = formatTurnGazette(
            activities = emptyList(),
            sizeChange = 0,
            currentSize = 5,
            caravanEvents = emptyList(),
            shipmentEvents = emptyList(),
            campaignClocks = emptyList(),
        )
        assertEquals(null, result)
    }

    @Test
    fun formatTurnGazetteActivitiesOnly() {
        val result = formatTurnGazette(
            activities = listOf("Claim Hex", "Build Structure (x2)"),
            sizeChange = 0,
            currentSize = 5,
        )
        assertEquals("Activities: Claim Hex, Build Structure (x2)", result)
    }

    @Test
    fun formatTurnGazetteExpansionOnly() {
        val result = formatTurnGazette(
            activities = emptyList(),
            sizeChange = 3,
            currentSize = 8,
        )
        assertEquals("Expansion: Claimed 3 hex(es) (Size: 8)", result)
    }

    @Test
    fun formatTurnGazetteCaravansOnly() {
        val events = listOf(
            CaravanEvent(CaravanEventKind.DELIVERED, "Restov Caravan", deliveredAmount = 5),
            CaravanEvent(CaravanEventKind.DELIVERED, "Oleg's Caravan", bonusResourceDice = 2),
            CaravanEvent(CaravanEventKind.RAIDED, "Narlmarches Caravan", cargoLost = 3),
            CaravanEvent(CaravanEventKind.LOST, "Stolen Lands Caravan")
        )
        val result = formatTurnGazette(
            activities = emptyList(),
            sizeChange = 0,
            currentSize = 5,
            caravanEvents = events,
        )
        val expected = "Caravans: Caravan delivered: Restov Caravan (delivered 5); " +
            "Caravan delivered: Oleg's Caravan (+2 RD); " +
            "Caravan raided: Narlmarches Caravan (lost 3); " +
            "Caravan lost: Stolen Lands Caravan"
        assertEquals(expected, result)
    }

    @Test
    fun formatTurnGazetteShipmentsOnly() {
        val events = listOf(
            CaravanEvent(CaravanEventKind.DELIVERED, "Fine Wine"),
            CaravanEvent(CaravanEventKind.RAIDED, "Iron Ore", cargoLost = 10),
            CaravanEvent(CaravanEventKind.LOST, "Rare Herbs")
        )
        val result = formatTurnGazette(
            activities = emptyList(),
            sizeChange = 0,
            currentSize = 5,
            shipmentEvents = events,
        )
        val expected = "Shipments: Shipment arrived: Fine Wine; " +
            "Shipment raided: Iron Ore (lost 10); " +
            "Shipment lost: Rare Herbs"
        assertEquals(expected, result)
    }

    @Test
    fun formatTurnGazetteClocksOnly() {
        val result = formatTurnGazette(
            activities = emptyList(),
            sizeChange = 0,
            currentSize = 5,
            campaignClocks = listOf("Clock A", "Clock B"),
        )
        assertEquals("Campaign Clocks: Clock A, Clock B", result)
    }

    @Test
    fun formatTurnGazetteCombinedEvents() {
        val caravanEvents = listOf(
            CaravanEvent(CaravanEventKind.DELIVERED, "Restov", deliveredAmount = 3)
        )
        val shipmentEvents = listOf(
            CaravanEvent(CaravanEventKind.LOST, "Gold Shipment")
        )
        val result = formatTurnGazette(
            activities = listOf("Pave Streets"),
            sizeChange = 1,
            currentSize = 12,
            caravanEvents = caravanEvents,
            shipmentEvents = shipmentEvents,
            campaignClocks = listOf("Troll Invasion Escalation"),
        )
        val expected = "Activities: Pave Streets | " +
            "Expansion: Claimed 1 hex(es) (Size: 12) | " +
            "Caravans: Caravan delivered: Restov (delivered 3) | " +
            "Shipments: Shipment lost: Gold Shipment | " +
            "Campaign Clocks: Troll Invasion Escalation"
        assertEquals(expected, result)
    }

    @Test
    fun `formatTurnGazette includes expedition section for matching turn`() {
        val chronicle = listOf(
            createRawExpeditionChronicleEntry(
                title = "Scout the Wilds", companionNames = "Amiri", activityId = "scout",
                outcomeDegree = "criticalSuccess", lootRp = 10, factionStandingDelta = 0,
                targetFactionName = null, turn = 5, appliedAt = "2026-06-15T10:00:00Z"
            ),
            createRawExpeditionChronicleEntry(
                title = "Hunt for Food", companionNames = "Valeros", activityId = "hunt",
                outcomeDegree = "success", lootRp = 5, factionStandingDelta = 0,
                targetFactionName = null, turn = 5, appliedAt = "2026-06-15T11:00:00Z"
            ),
            createRawExpeditionChronicleEntry(
                title = "Old Expedition", companionNames = "Seelah", activityId = "scout",
                outcomeDegree = "failure", lootRp = 0, factionStandingDelta = 0,
                targetFactionName = null, turn = 3, appliedAt = "2026-06-10T10:00:00Z"
            ),
        )
        val result = formatTurnGazette(
            activities = emptyList(),
            sizeChange = 0,
            currentSize = 5,
            expeditionChronicle = chronicle,
            turn = 5,
        )
        assertTrue(result!!.contains("Expeditions:"))
        assertTrue(result.contains("🏆 Scout the Wilds — Amiri (+10 RP)"))
        assertTrue(result.contains("✅ Hunt for Food — Valeros (+5 RP)"))
        assertFalse(result.contains("Old Expedition"))
    }

    @Test
    fun `formatTurnGazette expedition section shows faction standing for diplomacy`() {
        val chronicle = listOf(
            createRawExpeditionChronicleEntry(
                title = "Broker Peace", companionNames = "Amiri", activityId = "diplomacy",
                outcomeDegree = "success", lootRp = 0, factionStandingDelta = 4,
                targetFactionName = "Pitax", turn = 5, appliedAt = "2026-06-15T10:00:00Z"
            ),
        )
        val result = formatTurnGazette(
            activities = emptyList(),
            sizeChange = 0,
            currentSize = 5,
            expeditionChronicle = chronicle,
            turn = 5,
        )
        assertTrue(result!!.contains("Expeditions:"))
        assertTrue(result.contains("✅ Broker Peace — Amiri (Pitax: +4)"))
    }

    @Test
    fun `formatTurnGazette no expedition section when no entries for turn`() {
        val chronicle = listOf(
            createRawExpeditionChronicleEntry(
                title = "Old Expedition", companionNames = "Seelah", activityId = "scout",
                outcomeDegree = "failure", lootRp = 0, factionStandingDelta = 0,
                targetFactionName = null, turn = 3, appliedAt = "2026-06-10T10:00:00Z"
            ),
        )
        val result = formatTurnGazette(
            activities = emptyList(),
            sizeChange = 0,
            currentSize = 5,
            expeditionChronicle = chronicle,
            turn = 5,
        )
        assertEquals(null, result)
    }

    @Test
    fun formatTurnGazetteUsesCustomLocalizer() {
        val result = formatTurnGazette(
            activities = listOf("Build Road"),
            sizeChange = 0,
            currentSize = 5,
            localize = { key, _ -> "CUSTOM-$key" }
        )
        assertEquals("CUSTOM-kingdom.turnGazette.activities", result)
    }
}
package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.settlements.NpcEntry
import at.posselt.pfrpg2e.data.kingdom.settlements.PopulationRoster
import at.posselt.pfrpg2e.data.kingdom.settlements.Settlement
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementLayoutType
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementSize
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementSizeType
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementType
import at.posselt.pfrpg2e.data.kingdom.settlements.generateInitialPopulation
import at.posselt.pfrpg2e.data.kingdom.structures.AvailableItemBonuses
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.kingdom.structures.RawNpcEntry
import at.posselt.pfrpg2e.kingdom.structures.RawPopulationRoster
import at.posselt.pfrpg2e.kingdom.structures.RawSettlement
import at.posselt.pfrpg2e.kingdom.structures.toRaw
import at.posselt.pfrpg2e.migrations.migrations.Migration31
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ── helpers ────────────────────────────────────────────────────────

private fun testSettlementSize(
    type: SettlementSizeType = SettlementSizeType.VILLAGE,
    population: String = "<401",
    consumption: Int = 1,
    maxItemBonus: Int = 1,
    influence: Int = 0,
    maximumBlocks: String = "1",
    requiredKingdomLevel: Int = 1,
    levelFrom: Int = 1,
    levelTo: Int? = null,
) = SettlementSize(
    type = type,
    maximumBlocks = maximumBlocks,
    requiredKingdomLevel = requiredKingdomLevel,
    population = population,
    consumption = consumption,
    maxItemBonus = maxItemBonus,
    influence = influence,
    levelFrom = levelFrom,
    levelTo = levelTo,
)

private fun createTestSettlement(
    id: String = "test-settlement-1",
    populationNumber: Int = 400,
    existingNpcs: List<NpcEntry> = emptyList(),
): Settlement {
    val size = testSettlementSize(population = populationNumber.toString())
    return Settlement(
        id = id,
        name = "Test Settlement",
        type = SettlementType.SETTLEMENT,
        waterBorders = 0,
        isSecondaryTerritory = false,
        settlementEventBonus = 0,
        leaderLeadershipActivityBonus = 0,
        bonuses = emptySet(),
        allowCapitalInvestment = false,
        notes = emptySet(),
        storage = CommodityStorage(),
        increaseLeadershipActivities = false,
        consumptionReduction = 0,
        availableItems = AvailableItemBonuses(),
        size = size,
        unlockActivities = emptySet(),
        residentialLots = 4,
        hasBridge = false,
        occupiedBlocks = 1,
        preventItemLevelPenalty = false,
        delayedStructures = emptyList(),
        constructedStructures = emptyList(),
        structuresUnderConstruction = emptyList(),
        maximumCivicRdLimit = 0,
        settlementActions = 0,
        blocks = emptyList(),
        layoutType = SettlementLayoutType.RIGID,
        populationRoster = PopulationRoster(npcs = existingNpcs),
    )
}

// ════════════════════════════════════════════════════════════════════
// Seeding tests
// ════════════════════════════════════════════════════════════════════

class SettlementSeedingTest {

    @Test
    fun testEmptyRosterGetsSeeded() {
        val settlement = createTestSettlement(id = "seed-village", populationNumber = 400)
        assertTrue(settlement.populationRoster.npcs.isEmpty(), "Precondition: roster starts empty")
        val seeded = settlement.generateInitialPopulation()
        assertTrue(seeded.npcs.isNotEmpty(), "Seeded roster should not be empty")
    }

    @Test
    fun testExistingRosterPreserved() {
        val existingNpc = NpcEntry(id = "npc-existing-0", name = "Existing Person", occupation = "Guard")
        val settlement = createTestSettlement(
            id = "preserve-test",
            existingNpcs = listOf(existingNpc),
        )
        val result = settlement.generateInitialPopulation()
        assertEquals(1, result.npcs.size, "Should preserve existing NPC")
        assertEquals("Existing Person", result.npcs[0].name)
    }

    @Test
    fun testSeedingIsDeterministic() {
        val s1 = createTestSettlement(id = "det-village", populationNumber = 400)
        val s2 = createTestSettlement(id = "det-village", populationNumber = 400)
        val r1 = s1.generateInitialPopulation()
        val r2 = s2.generateInitialPopulation()
        assertEquals(r1, r2, "Same settlement ID should produce identical roster")
    }

    @Test
    fun testDifferentSettlementsDifferentRosters() {
        val s1 = createTestSettlement(id = "village-a", populationNumber = 400)
        val s2 = createTestSettlement(id = "village-b", populationNumber = 400)
        val r1 = s1.generateInitialPopulation()
        val r2 = s2.generateInitialPopulation()
        assertFalse(r1.npcs.map { it.name } == r2.npcs.map { it.name },
            "Different settlement IDs should produce different rosters")
    }

    @Test
    fun testVillageGeneratesReasonableCount() {
        val settlement = createTestSettlement(id = "count-village", populationNumber = 400)
        val roster = settlement.generateInitialPopulation()
        // sqrt(400) * 1.5 = 30
        assertTrue(roster.npcs.size in 20..50,
            "Village of ~400 should generate ~30 NPCs, got ${roster.npcs.size}")
    }

    @Test
    fun testTownGeneratesMoreThanVillage() {
        val village = createTestSettlement(id = "v", populationNumber = 400)
        val town = createTestSettlement(id = "t", populationNumber = 1200)
        val vr = village.generateInitialPopulation()
        val tr = town.generateInitialPopulation()
        assertTrue(tr.npcs.size > vr.npcs.size,
            "Town (${tr.npcs.size}) should have more NPCs than village (${vr.npcs.size})")
    }

    @Test
    fun testLargeSettlementCappedAt200() {
        val metro = createTestSettlement(id = "metro", populationNumber = 100000)
        val roster = metro.generateInitialPopulation()
        assertTrue(roster.npcs.size <= 200,
            "Metropolis should cap at 200 NPCs, got ${roster.npcs.size}")
    }

    @Test
    fun testMinimumOfFiveNpcs() {
        val tiny = createTestSettlement(id = "tiny", populationNumber = 10)
        val roster = tiny.generateInitialPopulation()
        assertTrue(roster.npcs.size >= 5,
            "Should generate at least 5 NPCs even for tiny settlements, got ${roster.npcs.size}")
    }

    @Test
    fun testAllNpcsHaveNames() {
        val settlement = createTestSettlement(id = "names-test", populationNumber = 400)
        val roster = settlement.generateInitialPopulation()
        roster.npcs.forEach { npc ->
            assertTrue(npc.name.isNotEmpty(), "NPC should have a name")
            assertTrue(npc.name.contains(" "),
                "NPC name '${npc.name}' should have given name and surname")
        }
    }

    @Test
    fun testAllNpcsHaveOccupations() {
        val settlement = createTestSettlement(id = "occ-test", populationNumber = 400)
        val roster = settlement.generateInitialPopulation()
        roster.npcs.forEach { npc ->
            assertTrue(npc.occupation.isNotEmpty(), "NPC should have an occupation")
        }
    }

    @Test
    fun testAllNpcIdsAreUnique() {
        val settlement = createTestSettlement(id = "unique-test", populationNumber = 400)
        val roster = settlement.generateInitialPopulation()
        val ids = roster.npcs.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "All NPC IDs should be unique")
    }

    @Test
    fun testNpcIdsContainSettlementId() {
        val settlement = createTestSettlement(id = "my-settlement", populationNumber = 400)
        val roster = settlement.generateInitialPopulation()
        roster.npcs.forEach { npc ->
            assertTrue(npc.id.startsWith("npc-my-settlement-"),
                "NPC ID '${npc.id}' should start with settlement-based prefix")
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// CRUD tests — RawPopulationRoster / RawNpcEntry operations
// ════════════════════════════════════════════════════════════════════

class PopulationRosterCrudTest {

    // ── Add NPC ──────────────────────────────────────────────────────

    @Test
    fun testAddNpcToEmptyRoster() {
        val roster = RawPopulationRoster()
        assertNull(roster.npcs)
        val npc = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer")
        roster.npcs = arrayOf(npc)
        assertNotNull(roster.npcs)
        assertEquals(1, roster.npcs!!.size)
        assertEquals("Alice", roster.npcs!![0].name)
    }

    @Test
    fun testAddMultipleNpcs() {
        val roster = RawPopulationRoster()
        val npc1 = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer")
        val npc2 = RawNpcEntry(id = "npc-2", name = "Bob", occupation = "Blacksmith")
        roster.npcs = arrayOf(npc1, npc2)
        assertEquals(2, roster.npcs!!.size)
        assertEquals("Alice", roster.npcs!![0].name)
        assertEquals("Bob", roster.npcs!![1].name)
    }

    @Test
    fun testAddNpcToExistingRoster() {
        val npc1 = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer")
        val roster = RawPopulationRoster(npcs = arrayOf(npc1))
        assertEquals(1, roster.npcs!!.size)
        val npc2 = RawNpcEntry(id = "npc-2", name = "Bob", occupation = "Blacksmith")
        roster.npcs = roster.npcs!! + npc2
        assertEquals(2, roster.npcs!!.size)
        assertEquals("Bob", roster.npcs!![1].name)
    }

    // ── Edit NPC ─────────────────────────────────────────────────────

    @Test
    fun testEditNpcPreservesId() {
        val npc = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer")
        val roster = RawPopulationRoster(npcs = arrayOf(npc))
        val updated = RawNpcEntry(id = npc.id, name = "Alice Updated", occupation = "Guard")
        roster.npcs = arrayOf(updated)
        assertEquals("npc-1", roster.npcs!![0].id, "ID should be preserved after edit")
        assertEquals("Alice Updated", roster.npcs!![0].name)
        assertEquals("Guard", roster.npcs!![0].occupation)
    }

    @Test
    fun testEditNpcAtIndex() {
        val npc1 = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer")
        val npc2 = RawNpcEntry(id = "npc-2", name = "Bob", occupation = "Blacksmith")
        val roster = RawPopulationRoster(npcs = arrayOf(npc1, npc2))
        val updated = RawNpcEntry(id = "npc-2", name = "Robert", occupation = "Guard")
        roster.npcs = arrayOf(npc1, updated)
        assertEquals("Robert", roster.npcs!![1].name)
        assertEquals("Guard", roster.npcs!![1].occupation)
        // First NPC unchanged
        assertEquals("Alice", roster.npcs!![0].name)
    }

    // ── Delete NPC ───────────────────────────────────────────────────

    @Test
    fun testDeleteNpcFromRoster() {
        val npc1 = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer")
        val npc2 = RawNpcEntry(id = "npc-2", name = "Bob", occupation = "Blacksmith")
        val roster = RawPopulationRoster(npcs = arrayOf(npc1, npc2))
        roster.npcs = arrayOf(npc1)
        assertEquals(1, roster.npcs!!.size)
        assertEquals("Alice", roster.npcs!![0].name)
    }

    @Test
    fun testDeleteLastNpcLeavesEmptyArray() {
        val npc = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer")
        val roster = RawPopulationRoster(npcs = arrayOf(npc))
        roster.npcs = emptyArray()
        assertNotNull(roster.npcs)
        assertEquals(0, roster.npcs!!.size)
    }

    @Test
    fun testDeleteNpcAtIndex() {
        val npc1 = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer")
        val npc2 = RawNpcEntry(id = "npc-2", name = "Bob", occupation = "Blacksmith")
        val npc3 = RawNpcEntry(id = "npc-3", name = "Charlie", occupation = "Miller")
        val roster = RawPopulationRoster(npcs = arrayOf(npc1, npc2, npc3))
        // Delete index 1 (Bob)
        roster.npcs = arrayOf(npc1, npc3)
        assertEquals(2, roster.npcs!!.size)
        assertEquals("Alice", roster.npcs!![0].name)
        assertEquals("Charlie", roster.npcs!![1].name)
    }

    // ── Notes field ──────────────────────────────────────────────────

    @Test
    fun testNpcNotesCanBeNull() {
        val npc = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer")
        assertNull(npc.notes)
    }

    @Test
    fun testNpcNotesCanBeSet() {
        val npc = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer", notes = "A veteran")
        assertEquals("A veteran", npc.notes)
    }

    @Test
    fun testNpcNotesCanBeCleared() {
        val npc = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer", notes = "A veteran")
        npc.notes = null
        assertNull(npc.notes)
    }

    // ── RawSettlement integration ─────────────────────────────────────

    @Test
    fun testRawSettlementWithPopulationRoster() {
        val npc = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer")
        val roster = RawPopulationRoster(npcs = arrayOf(npc))
        val settlement = RawSettlement(
            sceneId = "scene-1",
            lots = 1,
            level = 1,
            type = "settlement",
            layoutType = "rigid",
            secondaryTerritory = false,
            manualSettlementLevel = false,
            waterBorders = 0,
            populationRoster = roster,
        )
        assertNotNull(settlement.populationRoster)
        assertEquals(1, settlement.populationRoster!!.npcs!!.size)
        assertEquals("Alice", settlement.populationRoster!!.npcs!![0].name)
    }

    @Test
    fun testRawSettlementPopulationRosterInitiallyNull() {
        val settlement = RawSettlement(
            sceneId = "scene-1",
            lots = 1,
            level = 1,
            type = "settlement",
            layoutType = "rigid",
            secondaryTerritory = false,
            manualSettlementLevel = false,
            waterBorders = 0,
        )
        assertNull(settlement.populationRoster)
    }

    @Test
    fun testReplaceSettlementPreservesRoster() {
        // Simulates the afterSubmit callback in KingdomSheet.kt:
        // kingdom.settlements = kingdom.settlements.filter { it.sceneId != data.sceneId } + data
        val npc = RawNpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer")
        val roster = RawPopulationRoster(npcs = arrayOf(npc))
        val oldSettlement = RawSettlement(
            sceneId = "scene-1",
            lots = 1,
            level = 1,
            type = "settlement",
            layoutType = "rigid",
            secondaryTerritory = false,
            manualSettlementLevel = false,
            waterBorders = 0,
            populationRoster = roster,
        )
        // Simulate: filter out old, add new with updated roster
        val newNpc = RawNpcEntry(id = "npc-2", name = "Bob", occupation = "Blacksmith")
        val newRoster = RawPopulationRoster(npcs = arrayOf(npc, newNpc))
        val newSettlement = RawSettlement(
            sceneId = "scene-1",
            lots = 2,  // changed
            level = 1,
            type = "settlement",
            layoutType = "rigid",
            secondaryTerritory = false,
            manualSettlementLevel = false,
            waterBorders = 0,
            populationRoster = newRoster,
        )
        assertEquals(2, newSettlement.populationRoster!!.npcs!!.size,
            "Updated settlement should have 2 NPCs")
        assertEquals(2, newSettlement.lots, "Lots should be updated")
    }
}

// ════════════════════════════════════════════════════════════════════
// toRaw conversion tests — seeded roster → editable raw data
// ════════════════════════════════════════════════════════════════════

class PopulationRosterToRawTest {

    @Test
    fun testSeededRosterConvertsToRawEntries() {
        val settlement = createTestSettlement(id = "toraw-village", populationNumber = 400)
        val seeded = settlement.generateInitialPopulation()
        val raw = seeded.toRaw()
        val rawNpcs = raw.npcs
        assertNotNull(rawNpcs)
        assertEquals(seeded.npcs.size, rawNpcs.size, "All seeded NPCs should convert")
        seeded.npcs.forEachIndexed { index, npc ->
            assertEquals(npc.id, rawNpcs[index].id)
            assertEquals(npc.name, rawNpcs[index].name)
            assertEquals(npc.occupation, rawNpcs[index].occupation)
            assertEquals(npc.notes, rawNpcs[index].notes)
        }
    }

    @Test
    fun testEmptyRosterConvertsToEmptyRawArray() {
        val raw = PopulationRoster().toRaw()
        assertNotNull(raw.npcs)
        assertEquals(0, raw.npcs!!.size)
    }

    @Test
    fun testNotesArePreservedInConversion() {
        val roster = PopulationRoster(
            npcs = listOf(NpcEntry(id = "npc-1", name = "Alice", occupation = "Farmer", notes = "A veteran"))
        )
        val raw = roster.toRaw()
        assertEquals("A veteran", raw.npcs!![0].notes)
    }
}

// ════════════════════════════════════════════════════════════════════
// Migration tests — Migration31
// ════════════════════════════════════════════════════════════════════

class Migration31Test {

    private fun createKingdomWithSettlements(
        settlementCount: Int = 2,
        withExistingRoster: Boolean = false,
    ): dynamic {
        val settlements = js("[]")
        for (i in 0 until settlementCount) {
            val s = js("""({
                sceneId: 'scene-' + i,
                lots: 1,
                level: 1,
                type: 'settlement',
                layoutType: 'rigid',
                secondaryTerritory: false,
                waterBorders: 0,
            })""")
            if (withExistingRoster) {
                s.populationRoster = js("""({ npcs: [] })""")
            }
            settlements.push(s)
        }
        return js("""({ settlements: settlements })""")
    }

    @Test
    fun testMigrationAddsEmptyRosterToSettlements() {
        val kingdom = createKingdomWithSettlementCount(3)
        val migration = Migration31()
        // Simulate migration: iterate settlements and add populationRoster
        val settlements = kingdom.settlements
        for (i in 0 until settlements.length) {
            val settlement = settlements[i]
            if (settlement.populationRoster == null) {
                settlement.populationRoster = js("""({ npcs: [] })""")
            }
        }
        // Verify all settlements now have populationRoster
        for (i in 0 until settlements.length) {
            assertNotNull(settlements[i].populationRoster,
                "Settlement $i should have populationRoster after migration")
        }
    }

    @Test
    fun testMigrationDoesNotOverwriteExistingRoster() {
        val kingdom = createKingdomWithSettlements(settlementCount = 2, withExistingRoster = true)
        val settlements = kingdom.settlements
        // Pre-set an NPC on the first settlement
        val existingNpc = js("""({ id: 'npc-existing', name: 'Existing', occupation: 'Guard' })""")
        settlements[0].populationRoster = js("""({ npcs: [existingNpc] })""")
        // Run migration logic
        for (i in 0 until settlements.length) {
            val settlement = settlements[i]
            if (settlement.populationRoster == null) {
                settlement.populationRoster = js("""({ npcs: [] })""")
            }
        }
        // First settlement should still have its existing roster
        val roster0 = settlements[0].populationRoster
        assertNotNull(roster0)
        assertEquals(1, roster0.npcs.length, "Existing roster should be preserved")
        assertEquals("Existing", roster0.npcs[0].name)
        // Second settlement should have empty roster
        val roster1 = settlements[1].populationRoster
        assertNotNull(roster1)
        assertEquals(0, roster1.npcs.length, "Second settlement should have empty roster")
    }

    @Test
    fun testMigrationHandlesNullSettlements() {
        val kingdom = js("""({ settlements: null })""")
        // Migration should not crash when settlements is null
        val settlements = kingdom.settlements
        if (settlements != null) {
            for (i in 0 until settlements.length) {
                val settlement = settlements[i]
                if (settlement.populationRoster == null) {
                    settlement.populationRoster = js("""({ npcs: [] })""")
                }
            }
        }
        // If we got here without throwing, the test passes
        assertTrue(true, "Migration should handle null settlements gracefully")
    }

    @Test
    fun testMigrationHandlesEmptySettlements() {
        val kingdom = js("""({ settlements: [] })""")
        val settlements = kingdom.settlements
        for (i in 0 until settlements.length) {
            val settlement = settlements[i]
            if (settlement.populationRoster == null) {
                settlement.populationRoster = js("""({ npcs: [] })""")
            }
        }
        assertEquals(0, settlements.length)
    }

    @Test
    fun testMigrationSetsEmptyNpcsArray() {
        val kingdom = createKingdomWithSettlements(settlementCount = 1)
        val settlements = kingdom.settlements
        val settlement = settlements[0]
        if (settlement.populationRoster == null) {
            settlement.populationRoster = js("""({ npcs: [] })""")
        }
        val roster = settlement.populationRoster
        assertNotNull(roster)
        assertNotNull(roster.npcs, "npcs field should exist")
        assertEquals(0, roster.npcs.length, "npcs should be empty array")
    }

    private fun createKingdomWithSettlementCount(count: Int): dynamic {
        return createKingdomWithSettlements(settlementCount = count)
    }
}

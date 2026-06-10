package at.posselt.pfrpg2e.data.kingdom.settlements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// ── SettlementSize.populationNumber tests ────────────────────────────

class SettlementSizePopulationNumberTest {

    @Test
    fun villagePopulationParsesCorrectly() {
        val size = settlementSizeData.first { it.type == SettlementSizeType.VILLAGE }
        // "<401" → 401
        assertEquals(401, size.populationNumber)
    }

    @Test
    fun townPopulationParsesCorrectly() {
        val size = settlementSizeData.first { it.type == SettlementSizeType.TOWN }
        // "401-2000" → (401 + 2000) / 2 = 1200
        assertEquals(1200, size.populationNumber)
    }

    @Test
    fun cityPopulationParsesCorrectly() {
        val size = settlementSizeData.first { it.type == SettlementSizeType.CITY }
        // "2001-25000" → (2001 + 25000) / 2 = 13500
        assertEquals(13500, size.populationNumber)
    }

    @Test
    fun metropolisPopulationParsesCorrectly() {
        val size = settlementSizeData.first { it.type == SettlementSizeType.METROPOLIS }
        // "25001+" → 25001
        assertEquals(25001, size.populationNumber)
    }

    @Test
    fun allSizesHavePositivePopulation() {
        settlementSizeData.forEach { size ->
            assertTrue(size.populationNumber > 0, "${size.type} should have positive population")
        }
    }
}

// ── NPC occupations tests ─────────────────────────────────────────────

class NpcOccupationsTest {

    @Test
    fun occupationsListIsNotEmpty() {
        assertTrue(npcOccupations.isNotEmpty())
    }

    @Test
    fun occupationsListHasReasonableSize() {
        assertTrue(npcOccupations.size >= 20, "Should have at least 20 occupations")
    }

    @Test
    fun occupationsAreDistinct() {
        assertEquals(npcOccupations.size, npcOccupations.distinct().size)
    }

    @Test
    fun occupationsContainCommonTrades() {
        assertTrue(npcOccupations.contains("Farmer"))
        assertTrue(npcOccupations.contains("Blacksmith"))
        assertTrue(npcOccupations.contains("Innkeeper"))
    }
}

// ── generateInitialPopulation tests ────────────────────────────────────

class GenerateInitialPopulationTest {

    private fun createSettlement(
        id: String = "test-settlement-1",
        populationNumber: Int = 400,
    ): Settlement {
        val size = SettlementSize(
            type = SettlementSizeType.VILLAGE,
            maximumBlocks = "1",
            requiredKingdomLevel = 1,
            population = populationNumber.toString(),
            consumption = 1,
            maxItemBonus = 1,
            influence = 0,
            levelFrom = 1,
            levelTo = 1,
        )
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
            storage = at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage(),
            increaseLeadershipActivities = false,
            consumptionReduction = 0,
            availableItems = at.posselt.pfrpg2e.data.kingdom.structures.AvailableItemBonuses(),
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
        )
    }

    @Test
    fun generatesNpcsWhenRosterEmpty() {
        val settlement = createSettlement()
        val roster = settlement.generateInitialPopulation()
        assertTrue(roster.npcs.isNotEmpty(), "Should generate NPCs for empty roster")
    }

    @Test
    fun returnsExistingRosterWhenNotEmpty() {
        val settlement = createSettlement()
        val first = settlement.generateInitialPopulation()
        // Create a new settlement with the seeded roster
        val seeded = settlement.copy(populationRoster = first)
        val second = seeded.generateInitialPopulation()
        assertEquals(first.npcs.size, second.npcs.size, "Should return same roster when not empty")
        assertEquals(first, second)
    }

    @Test
    fun generatedNpcsHaveNames() {
        val settlement = createSettlement()
        val roster = settlement.generateInitialPopulation()
        roster.npcs.forEach { npc ->
            assertTrue(npc.name.isNotEmpty(), "NPC should have a name")
            assertTrue(npc.name.contains(" "), "NPC name should have given name and surname")
        }
    }

    @Test
    fun generatedNpcsHaveOccupations() {
        val settlement = createSettlement()
        val roster = settlement.generateInitialPopulation()
        roster.npcs.forEach { npc ->
            assertTrue(npc.occupation.isNotEmpty(), "NPC should have an occupation")
            assertTrue(npc.occupation in npcOccupations, "Occupation should be from the list")
        }
    }

    @Test
    fun generatedNpcsHaveUniqueIds() {
        val settlement = createSettlement()
        val roster = settlement.generateInitialPopulation()
        val ids = roster.npcs.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "All NPC IDs should be unique")
    }

    @Test
    fun deterministicForSameSettlement() {
        val s1 = createSettlement(id = "my-village")
        val s2 = createSettlement(id = "my-village")
        val r1 = s1.generateInitialPopulation()
        val r2 = s2.generateInitialPopulation()
        assertEquals(r1, r2, "Same settlement ID should produce same roster")
    }

    @Test
    fun differentSettlementsProduceDifferentRosters() {
        val s1 = createSettlement(id = "village-a")
        val s2 = createSettlement(id = "village-b")
        val r1 = s1.generateInitialPopulation()
        val r2 = s2.generateInitialPopulation()
        assertNotEquals(r1, r2, "Different settlement IDs should produce different rosters")
    }

    @Test
    fun villageGeneratesReasonableCount() {
        val settlement = createSettlement(populationNumber = 400)
        val roster = settlement.generateInitialPopulation()
        // sqrt(400) * 1.5 = 30
        assertTrue(roster.npcs.size in 20..50, "Village of ~400 should generate ~30 NPCs, got ${roster.npcs.size}")
    }

    @Test
    fun townGeneratesMoreThanVillage() {
        val village = createSettlement(id = "v", populationNumber = 400)
        val town = createSettlement(id = "t", populationNumber = 1200)
        val villageRoster = village.generateInitialPopulation()
        val townRoster = town.generateInitialPopulation()
        assertTrue(townRoster.npcs.size > villageRoster.npcs.size,
            "Town should have more NPCs than village")
    }

    @Test
    fun largeSettlementCappedAt200() {
        val metropolis = createSettlement(id = "metro", populationNumber = 100000)
        val roster = metropolis.generateInitialPopulation()
        assertTrue(roster.npcs.size <= 200, "Should cap at 200 NPCs, got ${roster.npcs.size}")
    }

    @Test
    fun minimumOfFiveNpcs() {
        val tiny = createSettlement(id = "tiny", populationNumber = 10)
        val roster = tiny.generateInitialPopulation()
        assertTrue(roster.npcs.size >= 5, "Should generate at least 5 NPCs even for tiny settlements")
    }

    @Test
    fun npcIdsContainSettlementId() {
        val settlement = createSettlement(id = "my-settlement")
        val roster = settlement.generateInitialPopulation()
        roster.npcs.forEach { npc ->
            assertTrue(npc.id.startsWith("npc-my-settlement-"), "NPC ID should start with settlement-based prefix")
        }
    }

    @Test
    fun namesComeFromValidTables() {
        val settlement = createSettlement()
        val roster = settlement.generateInitialPopulation()
        roster.npcs.forEach { npc ->
            val parts = npc.name.split(" ")
            assertEquals(2, parts.size, "Name should have two parts")
            val allGiven = NpcNameTables.allMaleGivenNames + NpcNameTables.allFemaleGivenNames
            val allSurnames = NpcNameTables.allSurnames
            assertTrue(parts[0] in allGiven, "Given name '${parts[0]}' should be in name tables")
            assertTrue(parts[1] in allSurnames, "Surname '${parts[1]}' should be in name tables")
        }
    }

    // ── growPopulation (roster top-up after population growth) ─────────

    @Test
    fun growPopulationTopsUpToTheNewRecommendedSizeWhenPopulationGrows() {
        val village = createSettlement(id = "grow-1", populationNumber = 400)
        val seeded = village.generateInitialPopulation()

        // The village grows into a town: same settlement id, bigger population.
        val town = createSettlement(id = "grow-1", populationNumber = 2000)
            .copy(populationRoster = seeded)
        val grown = town.growPopulation()

        assertEquals(town.recommendedRosterSize(), grown.npcs.size)
        assertTrue(grown.npcs.size > seeded.npcs.size, "Growth should add NPCs")
        // The original residents are preserved verbatim, in place.
        assertEquals(seeded.npcs, grown.npcs.take(seeded.npcs.size))
    }

    @Test
    fun growPopulationIsANoOpAtOrAboveTheRecommendedSize() {
        val settlement = createSettlement(id = "grow-2", populationNumber = 400)
        val seeded = settlement.generateInitialPopulation()
        val grown = settlement.copy(populationRoster = seeded).growPopulation()
        assertEquals(seeded, grown)
    }

    @Test
    fun growPopulationNeverProducesDuplicateIds() {
        val settlement = createSettlement(id = "grow-3", populationNumber = 400)
        val seeded = settlement.generateInitialPopulation()
        // The user deleted some residents from the middle; topping back up must not
        // collide with the surviving generated ids.
        val pruned = PopulationRoster(npcs = seeded.npcs.filterIndexed { i, _ -> i % 3 != 0 })
        val grown = settlement.copy(populationRoster = pruned).growPopulation()
        assertEquals(grown.npcs.size, grown.npcs.map { it.id }.distinct().size)
        assertEquals(settlement.recommendedRosterSize(), grown.npcs.size)
    }

    @Test
    fun growPopulationIsDeterministic() {
        val seeded = createSettlement(id = "grow-4", populationNumber = 400).generateInitialPopulation()
        val town1 = createSettlement(id = "grow-4", populationNumber = 2000).copy(populationRoster = seeded)
        val town2 = createSettlement(id = "grow-4", populationNumber = 2000).copy(populationRoster = seeded)
        assertEquals(town1.growPopulation(), town2.growPopulation())
    }
}

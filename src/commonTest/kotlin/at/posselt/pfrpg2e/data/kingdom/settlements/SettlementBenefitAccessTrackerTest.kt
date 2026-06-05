package at.posselt.pfrpg2e.data.kingdom.settlements

import at.posselt.pfrpg2e.data.kingdom.structures.AvailableItemBonuses
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.data.kingdom.structures.Structure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettlementBenefitAccessTrackerTest {

    private fun createSettlement(
        sizeType: SettlementSizeType,
        constructedStructures: List<Structure> = emptyList()
    ): Settlement {
        val size = SettlementSize(
            type = sizeType,
            maximumBlocks = "1",
            requiredKingdomLevel = 1,
            population = "100",
            consumption = 1,
            maxItemBonus = 1,
            influence = 0,
            levelFrom = 1,
            levelTo = 1,
        )
        return Settlement(
            id = "test-settlement",
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
            constructedStructures = constructedStructures,
            structuresUnderConstruction = emptyList(),
            maximumCivicRdLimit = 0,
            settlementActions = 0,
            blocks = emptyList(),
            layoutType = SettlementLayoutType.RIGID,
        )
    }

    private fun mockStructure(id: String, name: String): Structure {
        return Structure(
            id = id,
            uuid = "mock-uuid-$id",
            actorUuid = "mock-actor-uuid-$id",
            name = name,
        )
    }

    @Test
    fun testItemPurchaseLevels() {
        val village = createSettlement(SettlementSizeType.VILLAGE)
        assertEquals(1, village.itemPurchaseLevel)

        val town = createSettlement(SettlementSizeType.TOWN)
        assertEquals(3, town.itemPurchaseLevel)

        val city = createSettlement(SettlementSizeType.CITY)
        assertEquals(9, city.itemPurchaseLevel)

        val metropolis = createSettlement(SettlementSizeType.METROPOLIS)
        assertEquals(15, metropolis.itemPurchaseLevel)
    }

    @Test
    fun testTrainersAggregation() {
        val settlementEmpty = createSettlement(SettlementSizeType.TOWN)
        assertTrue(settlementEmpty.trainers.isEmpty())

        // Add Shrine and Library
        val structures = listOf(
            mockStructure("shrine", "Shrine"),
            mockStructure("library-vk", "Library (V&K)"),
            mockStructure("tavern-dive", "Dive Tavern")
        )
        val settlement = createSettlement(SettlementSizeType.TOWN, structures)
        val expectedTrainers = listOf("cleric", "oracle", "investigator", "thaumaturge", "psychic", "bard")
        
        assertEquals(expectedTrainers.size, settlement.trainers.size)
        expectedTrainers.forEach { trainer ->
            assertTrue(settlement.trainers.contains(trainer), "Should contain $trainer")
        }
    }

    @Test
    fun testCraftingAccessAggregation() {
        val settlementEmpty = createSettlement(SettlementSizeType.TOWN)
        assertTrue(settlementEmpty.craftingAccess.isEmpty())

        // Add Smithy, Tannery, and Lumberyard
        val structures = listOf(
            mockStructure("smithy", "Smithy"),
            mockStructure("foundry", "Foundry"), // foundry should not duplicate metallic
            mockStructure("tannery", "Tannery"),
            mockStructure("lumberyard", "Lumberyard")
        )
        val settlement = createSettlement(SettlementSizeType.TOWN, structures)
        val expectedCrafting = listOf("metallic", "leather", "wooden")

        assertEquals(expectedCrafting.size, settlement.craftingAccess.size)
        expectedCrafting.forEach { crafting ->
            assertTrue(settlement.craftingAccess.contains(crafting), "Should contain $crafting")
        }
    }
}

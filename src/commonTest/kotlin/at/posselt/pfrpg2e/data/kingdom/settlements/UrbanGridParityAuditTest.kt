package at.posselt.pfrpg2e.data.kingdom.settlements

import at.posselt.pfrpg2e.data.kingdom.structures.AvailableItemBonuses
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Parity-audit tests for Urban Grid formulas.
 * Each test maps to a COVERED or UNKNOWN row in the audit report.
 */
private fun createSettlementForAudit(
    residentialLots: Int = 4,
    occupiedBlocks: Int = 0,
    waterBorders: Int = 0,
    hasBridge: Boolean = false,
) = Settlement(
    id = "audit",
    name = "Audit Settlement",
    type = SettlementType.SETTLEMENT,
    waterBorders = waterBorders,
    isSecondaryTerritory = false,
    settlementEventBonus = 0,
    leaderLeadershipActivityBonus = 0,
    bonuses = emptySet(),
    allowCapitalInvestment = false,
    notes = emptySet(),
    storage = CommodityStorage(ore = 0, food = 0, lumber = 0, stone = 0, luxuries = 0),
    increaseLeadershipActivities = false,
    consumptionReduction = 0,
    availableItems = AvailableItemBonuses(),
    size = SettlementSize(
        type = SettlementSizeType.VILLAGE,
        maximumBlocks = "1",
        requiredKingdomLevel = 1,
        population = "<401",
        consumption = 1,
        maxItemBonus = 1,
        influence = 0,
        levelFrom = 1,
        levelTo = 1,
    ),
    unlockActivities = emptySet(),
    residentialLots = residentialLots,
    hasBridge = hasBridge,
    occupiedBlocks = occupiedBlocks,
    preventItemLevelPenalty = false,
    delayedStructures = emptyList(),
    constructedStructures = emptyList(),
    structuresUnderConstruction = emptyList(),
    maximumCivicRdLimit = 0,
    settlementActions = 0,
    blocks = emptyList(),
    layoutType = SettlementLayoutType.RIGID,
)

class UrbanGridParityAuditTest {

    // --- BlockTerrain enum (U1) ---

    @Test
    fun blockTerrainContainsAllSevenTypes() {
        val values = BlockTerrain.entries
        assertEquals(7, values.size)
        assertTrue(values.contains(BlockTerrain.LAND))
        assertTrue(values.contains(BlockTerrain.UNPAVED))
        assertTrue(values.contains(BlockTerrain.PAVED))
        assertTrue(values.contains(BlockTerrain.WATER))
        assertTrue(values.contains(BlockTerrain.BRIDGE))
        assertTrue(values.contains(BlockTerrain.WOOD_WALL))
        assertTrue(values.contains(BlockTerrain.STONE_WALL))
    }

    // --- UrbanGrid.lotsBorderingWater mirrors totalWaterLots (U10) ---

    @Test
    fun urbanGridLotsBorderingWaterEqualsTotalWaterLots() {
        val grid = UrbanGrid(
            blockA = BlockGrid(
                topLeft = BlockTerrain.WATER,
                topRight = BlockTerrain.WATER,
            ),
            blockC = BlockGrid(
                bottomLeft = BlockTerrain.WATER,
            ),
        )
        assertEquals(3, grid.totalWaterLots)
        assertEquals(grid.totalWaterLots, grid.lotsBorderingWater)
        assertEquals(3, grid.lotsBorderingWater)
    }

    @Test
    fun urbanGridLotsBorderingWaterZeroWhenNoWater() {
        val grid = UrbanGrid()
        assertEquals(0, grid.totalWaterLots)
        assertEquals(0, grid.lotsBorderingWater)
    }

    // --- SettlementEdges.waterBorders (U14) ---

    @Test
    fun settlementEdgesWaterBordersCountsOnlyWaterEdges() {
        val edges = SettlementEdges(
            north = UrbanGridEdge(hasWater = true),
            east = UrbanGridEdge(hasBridge = true),
            south = UrbanGridEdge(hasWater = true),
            west = UrbanGridEdge(hasWoodWall = true),
        )
        assertEquals(2, edges.waterBorders)
    }

    @Test
    fun settlementEdgesWaterBordersMaxFour() {
        val edges = SettlementEdges(
            north = UrbanGridEdge(hasWater = true),
            east = UrbanGridEdge(hasWater = true),
            south = UrbanGridEdge(hasWater = true),
            west = UrbanGridEdge(hasWater = true),
        )
        assertEquals(4, edges.waterBorders)
    }

    // --- isOvercrowded (S7) ---

    @Test
    fun isOvercrowdedFalseWhenOccupiedBlocksLessThanResidentialLots() {
        val settlement = createSettlementForAudit(
            residentialLots = 4,
            occupiedBlocks = 2,
        )
        assertFalse(settlement.isOvercrowded)
    }

    @Test
    fun isOvercrowdedFalseWhenOccupiedBlocksEqualsResidentialLots() {
        val settlement = createSettlementForAudit(
            residentialLots = 4,
            occupiedBlocks = 4,
        )
        assertFalse(settlement.isOvercrowded)
    }

    @Test
    fun isOvercrowdedTrueWhenOccupiedBlocksExceedsResidentialLots() {
        val settlement = createSettlementForAudit(
            residentialLots = 4,
            occupiedBlocks = 5,
        )
        assertTrue(settlement.isOvercrowded)
    }

    @Test
    fun isOvercrowdedTrueWithZeroResidentialLotsAndAnyOccupied() {
        val settlement = createSettlementForAudit(
            residentialLots = 0,
            occupiedBlocks = 1,
        )
        assertTrue(settlement.isOvercrowded)
    }

    // --- lacksBridge (S8) ---

    @Test
    fun lacksBridgeFalseWhenWaterBordersBelowFour() {
        val settlement = createSettlementForAudit(
            waterBorders = 3,
            hasBridge = false,
        )
        assertFalse(settlement.lacksBridge)
    }

    @Test
    fun lacksBridgeFalseWhenWaterBordersFourButBridgePresent() {
        val settlement = createSettlementForAudit(
            waterBorders = 4,
            hasBridge = true,
        )
        assertFalse(settlement.lacksBridge)
    }

    @Test
    fun lacksBridgeTrueWhenWaterBordersFourAndNoBridge() {
        val settlement = createSettlementForAudit(
            waterBorders = 4,
            hasBridge = false,
        )
        assertTrue(settlement.lacksBridge)
    }

    @Test
    fun lacksBridgeTrueWhenWaterBordersExceedsFourAndNoBridge() {
        val settlement = createSettlementForAudit(
            waterBorders = 5,
            hasBridge = false,
        )
        assertTrue(settlement.lacksBridge)
    }

    // --- BlockGrid.bridgeCount (U4) ---

    @Test
    fun blockGridBridgeCountAllBridge() {
        val grid = BlockGrid(
            topLeft = BlockTerrain.BRIDGE,
            topRight = BlockTerrain.BRIDGE,
            bottomLeft = BlockTerrain.BRIDGE,
            bottomRight = BlockTerrain.BRIDGE,
        )
        assertEquals(4, grid.bridgeCount)
        assertFalse(grid.isLand)
    }

    // --- UrbanGrid.occupiedBlocks max (U11) ---

    @Test
    fun urbanGridOccupiedBlocksMaxNine() {
        val grid = UrbanGrid(
            blockA = BlockGrid(topLeft = BlockTerrain.PAVED),
            blockB = BlockGrid(topLeft = BlockTerrain.PAVED),
            blockC = BlockGrid(topLeft = BlockTerrain.PAVED),
            blockD = BlockGrid(topLeft = BlockTerrain.PAVED),
            blockE = BlockGrid(topLeft = BlockTerrain.PAVED),
            blockF = BlockGrid(topLeft = BlockTerrain.PAVED),
            blockG = BlockGrid(topLeft = BlockTerrain.PAVED),
            blockH = BlockGrid(topLeft = BlockTerrain.PAVED),
            blockI = BlockGrid(topLeft = BlockTerrain.PAVED),
        )
        assertEquals(9, grid.occupiedBlocks)
    }

    // --- Settlement.edges water border integration ---

    @Test
    fun settlementEdgesWaterBordersInfluencesLacksBridge() {
        // Settlement with 4 water borders and no bridge should lack bridge
        val settlement = createSettlementForAudit(
            waterBorders = 4,
            hasBridge = false,
        )
        assertTrue(settlement.lacksBridge)
    }
}

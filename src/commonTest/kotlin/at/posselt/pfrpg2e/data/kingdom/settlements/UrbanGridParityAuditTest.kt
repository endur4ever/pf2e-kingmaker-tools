package at.posselt.pfrpg2e.data.kingdom.settlements

import at.posselt.pfrpg2e.data.kingdom.KingdomSkill
import at.posselt.pfrpg2e.data.kingdom.structures.AvailableItemBonuses
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.data.kingdom.structures.GroupedStructureBonus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    // --- Settlement.level cap at 20 (S0) ---

    @Test
    fun levelMinimumIsOne() {
        val settlement = createSettlementForAudit(occupiedBlocks = 0)
        assertEquals(1, settlement.level)
    }

    @Test
    fun levelEqualsOccupiedBlocks() {
        val settlement = createSettlementForAudit(occupiedBlocks = 5)
        assertEquals(5, settlement.level)
    }

    @Test
    fun levelCapsAtTwenty() {
        val settlement = createSettlementForAudit(occupiedBlocks = 25)
        assertEquals(20, settlement.level)
    }

    @Test
    fun levelExactlyTwentyWhenOccupiedBlocksIsTwenty() {
        val settlement = createSettlementForAudit(occupiedBlocks = 20)
        assertEquals(20, settlement.level)
    }

    // --- BlockGrid.wallCount (U5 / U6) ---

    @Test
    fun blockGridWallCountMixedStoneAndWood() {
        val grid = BlockGrid(
            topLeft = BlockTerrain.STONE_WALL,
            topRight = BlockTerrain.WOOD_WALL,
            bottomLeft = BlockTerrain.LAND,
            bottomRight = BlockTerrain.STONE_WALL,
        )
        assertEquals(3, grid.wallCount)
    }

    @Test
    fun blockGridStoneWallCountOnly() {
        val grid = BlockGrid(
            topLeft = BlockTerrain.STONE_WALL,
            topRight = BlockTerrain.STONE_WALL,
            bottomLeft = BlockTerrain.LAND,
            bottomRight = BlockTerrain.LAND,
        )
        assertEquals(2, grid.stoneWallCount)
        assertEquals(0, grid.woodWallCount)
    }

    @Test
    fun blockGridWoodWallCountOnly() {
        val grid = BlockGrid(
            topLeft = BlockTerrain.WOOD_WALL,
            topRight = BlockTerrain.LAND,
            bottomLeft = BlockTerrain.WOOD_WALL,
            bottomRight = BlockTerrain.WOOD_WALL,
        )
        assertEquals(0, grid.stoneWallCount)
        assertEquals(3, grid.woodWallCount)
    }

    // --- BlockGrid.pavedCount / UrbanGrid.totalPavedLots (U8 / U12) ---

    @Test
    fun blockGridPavedCountCorrect() {
        val grid = BlockGrid(
            topLeft = BlockTerrain.PAVED,
            topRight = BlockTerrain.PAVED,
            bottomLeft = BlockTerrain.LAND,
            bottomRight = BlockTerrain.WATER,
        )
        assertEquals(2, grid.pavedCount)
    }

    @Test
    fun urbanGridTotalPavedLotsSumsAllBlocks() {
        val grid = UrbanGrid(
            blockA = BlockGrid(topLeft = BlockTerrain.PAVED, topRight = BlockTerrain.PAVED),
            blockB = BlockGrid(topLeft = BlockTerrain.PAVED),
            blockC = BlockGrid(bottomRight = BlockTerrain.PAVED),
        )
        assertEquals(4, grid.totalPavedLots)
    }

    @Test
    fun urbanGridTotalPavedLotsZeroByDefault() {
        val grid = UrbanGrid()
        assertEquals(0, grid.totalPavedLots)
    }

    // --- Settlement.highestUniqueBonuses dedup (S10) ---

    @Test
    fun highestUniqueBonusesKeepsMaxPerSkillActivityPair() {
        val settlement = createSettlementForAudit().copy(
            bonuses = setOf(
                GroupedStructureBonus(
                    structureNames = setOf("Farm"),
                    skill = KingdomSkill.AGRICULTURE,
                    activity = null,
                    value = 2,
                    locatedIn = "audit",
                ),
                GroupedStructureBonus(
                    structureNames = setOf("Ranch"),
                    skill = KingdomSkill.AGRICULTURE,
                    activity = null,
                    value = 3,
                    locatedIn = "audit",
                ),
                GroupedStructureBonus(
                    structureNames = setOf("Mill"),
                    skill = KingdomSkill.AGRICULTURE,
                    activity = null,
                    value = 1,
                    locatedIn = "audit",
                ),
            ),
        )
        val unique = settlement.highestUniqueBonuses
        assertEquals(1, unique.size)
        assertEquals(3, unique.first().value)
    }

    @Test
    fun highestUniqueBonusesSeparateGroupsForDifferentSkills() {
        val settlement = createSettlementForAudit().copy(
            bonuses = setOf(
                GroupedStructureBonus(
                    structureNames = setOf("Farm"),
                    skill = KingdomSkill.AGRICULTURE,
                    activity = null,
                    value = 2,
                    locatedIn = "audit",
                ),
                GroupedStructureBonus(
                    structureNames = setOf("Theater"),
                    skill = KingdomSkill.ARTS,
                    activity = null,
                    value = 1,
                    locatedIn = "audit",
                ),
            ),
        )
        val unique = settlement.highestUniqueBonuses
        assertEquals(2, unique.size)
    }

    @Test
    fun highestUniqueBonusesGroupsByActivityWhenPresent() {
        val settlement = createSettlementForAudit().copy(
            bonuses = setOf(
                GroupedStructureBonus(
                    structureNames = setOf("Museum"),
                    skill = KingdomSkill.ARTS,
                    activity = "rest-and-relax",
                    value = 3,
                    locatedIn = "audit",
                ),
                GroupedStructureBonus(
                    structureNames = setOf("Theater"),
                    skill = KingdomSkill.ARTS,
                    activity = null,
                    value = 1,
                    locatedIn = "audit",
                ),
            ),
        )
        val unique = settlement.highestUniqueBonuses
        assertEquals(2, unique.size)
    }

    // --- SettlementDetailsMatrix: every row has non-empty label (S11) ---

    @Test
    fun allMatrixRowsHaveNonEmptyLabels() {
        for (row in settlementDetailsMatrixRows) {
            assertTrue(row.label.isNotEmpty(), "Row label must not be empty")
        }
    }

    @Test
    fun matrixBonusForHeaderRowMatchesSkillOnlyBonuses() {
        val settlement = createSettlementForAudit().copy(
            bonuses = setOf(
                GroupedStructureBonus(
                    structureNames = setOf("Farm"),
                    skill = KingdomSkill.AGRICULTURE,
                    activity = null,
                    value = 2,
                    locatedIn = "audit",
                ),
            ),
        )
        val row = settlementDetailsMatrixRows.first { it.label == "Agriculture" }
        assertEquals(2, settlement.matrixBonusFor(row))
    }

    @Test
    fun matrixBonusForSkillQualifiedRowMatchesSkillAndActivity() {
        val settlement = createSettlementForAudit().copy(
            bonuses = setOf(
                GroupedStructureBonus(
                    structureNames = setOf("Farm"),
                    skill = KingdomSkill.AGRICULTURE,
                    activity = null,
                    value = 1,
                    locatedIn = "audit",
                ),
                GroupedStructureBonus(
                    structureNames = setOf("Irrigation"),
                    skill = KingdomSkill.AGRICULTURE,
                    activity = "establish-farmland",
                    value = 3,
                    locatedIn = "audit",
                ),
            ),
        )
        val row = settlementDetailsMatrixRows.first { it.label == "Establish Farmland" }
        assertEquals(3, settlement.matrixBonusFor(row))
    }

    @Test
    fun matrixBonusForReturnsNullWhenNoMatch() {
        val settlement = createSettlementForAudit()
        val row = settlementDetailsMatrixRows.first { it.label == "Agriculture" }
        assertNull(settlement.matrixBonusFor(row))
    }

    @Test
    fun matrixBonusForActivityOnlyRowMatchesByActivityRegardlessOfSkill() {
        val settlement = createSettlementForAudit().copy(
            bonuses = setOf(
                GroupedStructureBonus(
                    structureNames = setOf("Yard"),
                    skill = KingdomSkill.ENGINEERING,
                    activity = "build-structure",
                    value = 4,
                    locatedIn = "audit",
                ),
            ),
        )
        val row = settlementDetailsMatrixRows.first { it.label == "Build Structure" }
        assertEquals(4, settlement.matrixBonusFor(row))
    }
}

package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.regions.Terrain
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Travel activity costs, per the hexploration rules plus the module's documented river house rule.
 */
class TravelActivityCostTest {
    @Test
    fun terrainMapsToTheRulesDifficultyCategories() {
        assertEquals(TerrainDifficulty.OPEN, terrainDifficulty(Terrain.PLAINS))
        assertEquals(TerrainDifficulty.OPEN, terrainDifficulty(Terrain.URBAN))
        assertEquals(TerrainDifficulty.DIFFICULT, terrainDifficulty(Terrain.FOREST))
        assertEquals(TerrainDifficulty.DIFFICULT, terrainDifficulty(Terrain.HILLS))
        assertEquals(TerrainDifficulty.DIFFICULT, terrainDifficulty(Terrain.DESERT))
        assertEquals(TerrainDifficulty.GREATER_DIFFICULT, terrainDifficulty(Terrain.MOUNTAIN))
        assertEquals(TerrainDifficulty.GREATER_DIFFICULT, terrainDifficulty(Terrain.SWAMP))
    }

    @Test
    fun unknownTerrainIsTreatedAsOpen() {
        assertEquals(TerrainDifficulty.OPEN, terrainDifficulty(null))
    }

    @Test
    fun baseCostIsOneTwoOrThreeByDifficulty() {
        assertEquals(1, travelActivityCost(TerrainDifficulty.OPEN))
        assertEquals(2, travelActivityCost(TerrainDifficulty.DIFFICULT))
        assertEquals(3, travelActivityCost(TerrainDifficulty.GREATER_DIFFICULT))
    }

    @Test
    fun aRoadMakesTerrainOneStepBetter() {
        assertEquals(2, travelActivityCost(TerrainDifficulty.GREATER_DIFFICULT, hasRoad = true))
        assertEquals(1, travelActivityCost(TerrainDifficulty.DIFFICULT, hasRoad = true))
    }

    @Test
    fun aRoadNeverMakesAHexFreeOrNegative() {
        assertEquals(1, travelActivityCost(TerrainDifficulty.OPEN, hasRoad = true))
    }

    @Test
    fun theConfiguredRiverSurchargeIsAdded() {
        assertEquals(2, travelActivityCost(TerrainDifficulty.OPEN, riverExtraDegrees = 1))
        assertEquals(3, travelActivityCost(TerrainDifficulty.DIFFICULT, riverExtraDegrees = 1))
    }

    @Test
    fun theHouseRuleCeilingOfThreeApplies() {
        // "up to a maximum of 3" — greater difficult plus a river surcharge is 4 uncapped.
        assertEquals(3, travelActivityCost(TerrainDifficulty.GREATER_DIFFICULT, riverExtraDegrees = 1))
        assertEquals(
            3,
            travelActivityCost(TerrainDifficulty.GREATER_DIFFICULT, riverExtraDegrees = 1, extraDegrees = 5),
        )
    }

    @Test
    fun roadAndRiverCombine() {
        // A bridged road through forest is open; the same forest road with an unbridged river is
        // back up one degree.
        assertEquals(1, travelActivityCost(TerrainDifficulty.DIFFICULT, hasRoad = true))
        assertEquals(2, travelActivityCost(TerrainDifficulty.DIFFICULT, hasRoad = true, riverExtraDegrees = 1))
    }

    @Test
    fun contentModifiersCanOnlyPushWithinTheLegalRange() {
        assertEquals(1, travelActivityCost(TerrainDifficulty.OPEN, extraDegrees = -5))
        assertEquals(3, travelActivityCost(TerrainDifficulty.OPEN, extraDegrees = 9))
    }

    @Test
    fun theRiverSurchargeIsZeroByDefaultBecauseRawHasNoCrossingCost() {
        // RAW covers travelling ALONG water, not across it, so the surcharge is opt-in via the
        // travelCostRiverNoBridgeAdditional setting.
        assertEquals(1, travelActivityCost(TerrainDifficulty.OPEN))
        assertEquals(2, travelActivityCost(TerrainDifficulty.DIFFICULT))
    }

    @Test
    fun pavedStreetsReduceASettlementHexToOneActivity() {
        // House rule: "Hexes containing a settlement reduce their Travel cost to 1 if you've
        // constructed Paved Streets" — it overrides terrain, rivers and content alike.
        assertEquals(
            1,
            travelActivityCost(
                TerrainDifficulty.GREATER_DIFFICULT,
                riverExtraDegrees = 1,
                extraDegrees = 2,
                pavedSettlement = true,
            ),
        )
    }

    @Test
    fun withoutPavedStreetsASettlementHexPaysNormalTerrain() {
        assertEquals(3, travelActivityCost(TerrainDifficulty.GREATER_DIFFICULT, pavedSettlement = false))
    }
}

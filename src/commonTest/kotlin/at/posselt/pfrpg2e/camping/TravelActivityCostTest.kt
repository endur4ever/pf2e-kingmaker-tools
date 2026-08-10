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
    fun anUnbridgedRiverAddsOneDegree() {
        assertEquals(2, travelActivityCost(TerrainDifficulty.OPEN, unbridgedRiver = true))
        assertEquals(3, travelActivityCost(TerrainDifficulty.DIFFICULT, unbridgedRiver = true))
    }

    @Test
    fun theHouseRuleCeilingOfThreeApplies() {
        // "up to a maximum of 3" — greater difficult plus a river is 4 uncapped.
        assertEquals(3, travelActivityCost(TerrainDifficulty.GREATER_DIFFICULT, unbridgedRiver = true))
        assertEquals(
            3,
            travelActivityCost(TerrainDifficulty.GREATER_DIFFICULT, unbridgedRiver = true, extraDegrees = 5),
        )
    }

    @Test
    fun roadAndRiverCombine() {
        // A bridged road through forest is open; the same forest road with an unbridged river is
        // back up one degree.
        assertEquals(1, travelActivityCost(TerrainDifficulty.DIFFICULT, hasRoad = true))
        assertEquals(2, travelActivityCost(TerrainDifficulty.DIFFICULT, hasRoad = true, unbridgedRiver = true))
    }

    @Test
    fun contentModifiersCanOnlyPushWithinTheLegalRange() {
        assertEquals(1, travelActivityCost(TerrainDifficulty.OPEN, extraDegrees = -5))
        assertEquals(3, travelActivityCost(TerrainDifficulty.OPEN, extraDegrees = 9))
    }
}

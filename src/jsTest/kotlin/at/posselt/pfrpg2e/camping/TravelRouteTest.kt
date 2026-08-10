package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.data.regions.Terrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Routes are priced in Travel activities, the unit hexploration actually uses, and converted to a
 * duration with the length of one hexploration activity. EIGHT_HOURS is the activity length for a
 * party with a single activity per day (Speed 15-25), which keeps these numbers easy to read.
 */
class TravelRouteTest {
    private val eightHours = 8.0 * 60 * 60

    private fun service(
        hexContents: Map<String, HexContent> = emptyMap(),
        weatherModifier: Double = 1.0,
        riverNoBridgeExtraDegrees: Int = 0,
        pavedSettlementHexKeys: Set<String> = emptySet(),
    ) = TravelService(
        hexContents = hexContents,
        weatherModifier = weatherModifier,
        riverNoBridgeExtraDegrees = riverNoBridgeExtraDegrees,
        pavedSettlementHexKeys = pavedSettlementHexKeys,
    )

    @Test
    fun testOpenTerrainCostsOneTravelActivityPerHex() {
        val route = service().calculateRoute(
            path = listOf("start", "a", "b"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() },
        )
        assertEquals(2.0, route.totalCost, "two open hexes entered = 2 Travel activities")
        assertEquals((2 * eightHours).toLong(), route.estimatedDurationSeconds)
    }

    @Test
    fun testDifficultAndGreaterDifficultTerrainCostMore() {
        // RAW: difficult terrain requires 2 Travel activities, greater difficult requires 3.
        val difficult = service().calculateRoute(
            path = listOf("start", "forest"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.FOREST },
            getFeatures = { emptyList() },
        )
        assertEquals(2.0, difficult.totalCost)

        val greater = service().calculateRoute(
            path = listOf("start", "mountain"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.MOUNTAIN },
            getFeatures = { emptyList() },
        )
        assertEquals(3.0, greater.totalCost)
        assertTrue(greater.totalCost > difficult.totalCost)
    }

    @Test
    fun testRoadMakesTerrainOneStepBetter() {
        // RAW: "Traveling along a road uses a terrain type one step better than the surrounding
        // terrain" — so a road through forest travels as open terrain.
        val route = service().calculateRoute(
            path = listOf("start", "forestRoad"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.FOREST },
            getFeatures = { listOf("road") },
        )
        assertEquals(1.0, route.totalCost)
    }

    @Test
    fun testRoadNeverCostsLessThanOpenTerrain() {
        val route = service().calculateRoute(
            path = listOf("start", "plainsRoad"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { listOf("road") },
        )
        assertEquals(1.0, route.totalCost, "a road across plains is still a Travel activity, not free")
    }

    @Test
    fun testUnbridgedRiverAddsTheConfiguredSurchargeAndABridgeNegatesIt() {
        // House rule (docs/house-rules.md), opt-in via travelCostRiverNoBridgeAdditional:
        // "Increase the Travel activity cost by 1 degree (up to a maximum of 3) if PCs are
        // travelling through a hex with a river and no bridge".
        val river = service(riverNoBridgeExtraDegrees = 1).calculateRoute(
            path = listOf("start", "river"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { listOf("river") },
        )
        assertEquals(2.0, river.totalCost)

        val bridged = service(riverNoBridgeExtraDegrees = 1).calculateRoute(
            path = listOf("start", "river"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { listOf("river", "bridge") },
        )
        assertEquals(1.0, bridged.totalCost, "a bridge removes the crossing cost")
    }

    @Test
    fun testCostIsCappedAtThreeActivities() {
        // Greater difficult terrain plus an unbridged river would be 4 without the house rule's
        // "up to a maximum of 3" ceiling, which nothing previously enforced.
        val route = service(riverNoBridgeExtraDegrees = 1).calculateRoute(
            path = listOf("start", "swampRiver"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.SWAMP },
            getFeatures = { listOf("river") },
        )
        assertEquals(3.0, route.totalCost)
    }

    @Test
    fun testHexContentModifierCountsAsExtraDegrees() {
        val contents = mapOf(
            "hazard" to HexContent(
                id = "h",
                hexKey = "hazard",
                type = HexContentType.CUSTOM,
                name = "Rockslide",
                visibility = HexContentVisibility.DISCOVERED,
                travelModifier = 1,
            ),
        )
        val route = service(hexContents = contents).calculateRoute(
            path = listOf("start", "hazard"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() },
        )
        assertEquals(2.0, route.totalCost)
    }

    @Test
    fun testAFasterPartyCoversTheSameRouteInLessTime() {
        // Same activity count; Speed changes how long ONE activity takes, which is where the
        // rules put party Speed. 2 activities/day (Speed 30-40) halves the activity length.
        val path = listOf("start", "a", "b")
        val slow = service().calculateRoute(
            path = path,
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() },
        )
        val fast = service().calculateRoute(
            path = path,
            secondsPerActivity = eightHours / 2,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() },
        )
        assertEquals(slow.totalCost, fast.totalCost, "the terrain cost does not change with Speed")
        assertEquals(slow.estimatedDurationSeconds / 2, fast.estimatedDurationSeconds)
    }

    @Test
    fun testWeatherStretchesTheDurationButNotTheActivityCount() {
        val path = listOf("start", "a")
        val sunny = service().calculateRoute(
            path = path,
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() },
        )
        val rainy = service(weatherModifier = 1.5).calculateRoute(
            path = path,
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() },
        )
        assertEquals(sunny.totalCost, rainy.totalCost)
        assertEquals((sunny.estimatedDurationSeconds * 1.5).toLong(), rainy.estimatedDurationSeconds)
    }

    @Test
    fun testStartingHexIsNotCharged() {
        // The party already stands in the first hex, so entering it costs nothing.
        val route = service().calculateRoute(
            path = listOf("a", "b"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.MOUNTAIN },
            getFeatures = { emptyList() },
        )
        assertEquals(3.0, route.totalCost, "only hex b is entered")
    }

    @Test
    fun testGoingNowhereCostsNothing() {
        for (path in listOf(emptyList(), listOf("a"))) {
            val route = service().calculateRoute(
                path = path,
                secondsPerActivity = eightHours,
                getTerrain = { Terrain.PLAINS },
                getFeatures = { emptyList() },
            )
            assertEquals(0.0, route.totalCost)
            assertEquals(0L, route.estimatedDurationSeconds)
        }
    }

    @Test
    fun testRiverCostsNothingExtraUnlessTheHouseRuleIsEnabled() {
        // Default is RAW: crossing a river is not a Travel surcharge.
        val route = service().calculateRoute(
            path = listOf("start", "river"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { listOf("river") },
        )
        assertEquals(1.0, route.totalCost)
    }

    @Test
    fun testPavedSettlementHexCostsOneActivity() {
        // House rule: a settlement hex with Paved Streets drops to 1 Travel activity even in
        // greater difficult terrain.
        val route = service(pavedSettlementHexKeys = setOf("town")).calculateRoute(
            path = listOf("start", "town"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.MOUNTAIN },
            getFeatures = { emptyList() },
        )
        assertEquals(1.0, route.totalCost)
    }

    @Test
    fun testUnpavedSettlementHexStillPaysItsTerrain() {
        val route = service().calculateRoute(
            path = listOf("start", "town"),
            secondsPerActivity = eightHours,
            getTerrain = { Terrain.MOUNTAIN },
            getFeatures = { emptyList() },
        )
        assertEquals(3.0, route.totalCost)
    }
}

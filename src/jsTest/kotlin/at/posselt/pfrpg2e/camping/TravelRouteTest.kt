package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.regions.Terrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TravelRouteTest {

    @Test
    fun testRouteThroughForestCostsMoreThanRoad() {
        val hexContents = emptyMap<String, HexContent>()
        val terrainModifiers = mapOf(Terrain.FOREST to 1.0)
        val infrastructureModifiers = mapOf("road" to -1.0)
        val service = TravelService(
            hexContents = hexContents,
            terrainModifiers = terrainModifiers,
            infrastructureModifiers = infrastructureModifiers
        )

        // 1. Forest path
        val forestRoute = service.calculateRoute(
            path = listOf("startHex", "forestHex"),
            partySpeedMultiplier = 1.0,
            getTerrain = { if (it == "forestHex") Terrain.FOREST else Terrain.PLAINS },
            getFeatures = { emptyList() }
        )
        // Forest cost = 1.0 base + 1.0 forest = 2.0
        assertEquals(2.0, forestRoute.totalCost)

        // 2. Road path
        val roadRoute = service.calculateRoute(
            path = listOf("startHex", "roadHex"),
            partySpeedMultiplier = 1.0,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { if (it == "roadHex") listOf("road") else emptyList() }
        )
        // Road cost = 1.0 base + 0.0 plains - 1.0 road = 0.0
        assertEquals(0.0, roadRoute.totalCost)

        assertTrue(forestRoute.totalCost > roadRoute.totalCost)
    }

    @Test
    fun testRiverCrossingWithoutBridgeAppliesPenalty() {
        val hexContents = emptyMap<String, HexContent>()
        val infrastructureModifiers = mapOf("river" to 1.0)
        val service = TravelService(
            hexContents = hexContents,
            infrastructureModifiers = infrastructureModifiers
        )

        // 1. River without bridge
        val riverRoute = service.calculateRoute(
            path = listOf("startHex", "riverHex"),
            partySpeedMultiplier = 1.0,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { if (it == "riverHex") listOf("river") else emptyList() }
        )
        // Cost = 1.0 base + 1.0 river = 2.0
        assertEquals(2.0, riverRoute.totalCost)

        // 2. River with bridge
        val bridgeRoute = service.calculateRoute(
            path = listOf("startHex", "bridgeHex"),
            partySpeedMultiplier = 1.0,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { if (it == "bridgeHex") listOf("river", "bridge") else emptyList() }
        )
        // Cost = 1.0 base + 0.0 (river ignored due to bridge) = 1.0
        assertEquals(1.0, bridgeRoute.totalCost)

        assertTrue(riverRoute.totalCost > bridgeRoute.totalCost)
    }

    @Test
    fun testSlowPartySpeedIncreasesDuration() {
        val hexContents = emptyMap<String, HexContent>()
        val service = TravelService(hexContents = hexContents)

        // Fast party
        val fastRoute = service.calculateRoute(
            path = listOf("startHex", "hex1"),
            partySpeedMultiplier = 2.0,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() }
        )
        // Cost = 1.0 base / 2.0 multiplier = 0.5. Duration = 0.5 * 3600 = 1800
        assertEquals(0.5, fastRoute.totalCost)
        assertEquals(1800L, fastRoute.estimatedDurationSeconds)

        // Slow party
        val slowRoute = service.calculateRoute(
            path = listOf("startHex", "hex1"),
            partySpeedMultiplier = 0.5,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() }
        )
        // Cost = 1.0 base / 0.5 multiplier = 2.0. Duration = 2.0 * 3600 = 7200
        assertEquals(2.0, slowRoute.totalCost)
        assertEquals(7200L, slowRoute.estimatedDurationSeconds)

        assertTrue(slowRoute.estimatedDurationSeconds > fastRoute.estimatedDurationSeconds)
    }

    @Test
    fun testWeatherImpactIncreasesCost() {
        val hexContents = emptyMap<String, HexContent>()
        
        // Sunny weather (modifier = 1.0)
        val sunnyService = TravelService(hexContents = hexContents, weatherModifier = 1.0)
        val sunnyRoute = sunnyService.calculateRoute(
            path = listOf("startHex", "hex1"),
            partySpeedMultiplier = 1.0,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() }
        )
        assertEquals(1.0, sunnyRoute.totalCost)

        // Heavy rain/snow (modifier = 1.5)
        val rainService = TravelService(hexContents = hexContents, weatherModifier = 1.5)
        val rainRoute = rainService.calculateRoute(
            path = listOf("startHex", "hex1"),
            partySpeedMultiplier = 1.0,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() }
        )
        assertEquals(1.5, rainRoute.totalCost)

        assertTrue(rainRoute.totalCost > sunnyRoute.totalCost)
    }

    @Test
    fun testStartingHexIsNotCharged() {
        // The party is already standing in the first hex of the path, so it costs nothing to
        // "enter" it. Billing it added an extra hex of travel time to every single route.
        val service = TravelService(hexContents = emptyMap())

        val oneStep = service.calculateRoute(
            path = listOf("a", "b"),
            partySpeedMultiplier = 1.0,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() },
        )
        assertEquals(1.0, oneStep.totalCost, "A -> B is ONE hex of travel")

        val twoSteps = service.calculateRoute(
            path = listOf("a", "b", "c"),
            partySpeedMultiplier = 1.0,
            getTerrain = { Terrain.PLAINS },
            getFeatures = { emptyList() },
        )
        assertEquals(2.0, twoSteps.totalCost, "A -> B -> C is TWO hexes of travel")
    }

    @Test
    fun testGoingNowhereCostsNothing() {
        val service = TravelService(hexContents = emptyMap())
        for (path in listOf(emptyList(), listOf("a"))) {
            val route = service.calculateRoute(
                path = path,
                partySpeedMultiplier = 1.0,
                getTerrain = { Terrain.PLAINS },
                getFeatures = { emptyList() },
            )
            assertEquals(0.0, route.totalCost, "a route that never leaves its hex is free")
            assertEquals(0L, route.estimatedDurationSeconds)
        }
    }

    @Test
    fun testCostAgreesWithTheRouterForTheSamePath() {
        // TravelRouter's Dijkstra sums edge weights into each destination, i.e. it already
        // excludes the start. TravelService must total the same thing for the same path, or the
        // route the planner CHOOSES and the hours it REPORTS come from different cost models.
        val terrainByHex = mapOf("a" to Terrain.PLAINS, "b" to Terrain.FOREST, "c" to Terrain.HILLS)
        val terrainModifiers = mapOf(Terrain.PLAINS to 0.0, Terrain.FOREST to 1.0, Terrain.HILLS to 2.0)
        val service = TravelService(
            hexContents = emptyMap(),
            terrainModifiers = terrainModifiers,
        )
        val route = service.calculateRoute(
            path = listOf("a", "b", "c"),
            partySpeedMultiplier = 1.0,
            getTerrain = { terrainByHex[it] },
            getFeatures = { emptyList() },
        )
        // Entering b: 1 + 1 (forest). Entering c: 1 + 2 (hills). Start a: free.
        assertEquals(5.0, route.totalCost)
    }
}

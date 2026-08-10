package at.posselt.pfrpg2e.camping.routing

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.data.regions.Terrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Characterization tests for the unified TravelRouter.
 * These tests capture the current behavior of TravelService (used by camping sheet)
 * to ensure the refactor is behavior-preserving.
 */
class TravelRouterCharacterizationTest {

    private class MockProvider(
        private val adjacencies: Map<String, List<String>>,
        private val contents: Map<String, List<HexContent>>,
        private val terrains: Map<String, Terrain>,
        private val features: Map<String, List<String>>
    ) : TravelProvider {
        override fun getAdjacentHexKeys(hexKey: String): List<String> = adjacencies[hexKey] ?: emptyList()
        override fun getContentForHex(hexKey: String): List<HexContent> = contents[hexKey] ?: emptyList()
        override fun getTerrainForHex(hexKey: String): Terrain? = terrains[hexKey]
        override fun getFeaturesForHex(hexKey: String): List<String> = features[hexKey] ?: emptyList()
    }

    private fun content(id: String, type: HexContentType, travelModifier: Int? = null): HexContent =
        HexContent(
            id = id,
            hexKey = "key-$id",
            type = type,
            name = "Name-$id",
            visibility = HexContentVisibility.DISCOVERED,
            travelModifier = travelModifier
        )

    @Test
    fun `simple straight path has base cost per hex`() {
        val provider = MockProvider(
            adjacencies = mapOf("A" to listOf("B"), "B" to listOf("C")),
            contents = emptyMap(),
            terrains = emptyMap(),
            features = emptyMap()
        )
        val router = TravelRouter(provider)
        val plan = TravelPlan()

        val route = router.calculateRoute("A", "C", plan)

        assertNotNull(route)
        assertEquals(listOf("A", "B", "C"), route.path)
        // 2 edges × 1.0 base cost = 2.0
        assertEquals(2.0, route.totalCost)
    }

    @Test
    fun `forest terrain adds terrain modifier`() {
        val provider = MockProvider(
            adjacencies = mapOf("A" to listOf("B"), "B" to listOf("C")),
            contents = emptyMap(),
            terrains = mapOf("B" to Terrain.FOREST),
            features = emptyMap()
        )
        val router = TravelRouter(provider)
        val plan = TravelPlan(terrainModifiers = mapOf(Terrain.FOREST to 1.0))

        val route = router.calculateRoute("A", "C", plan)

        assertNotNull(route)
        // A->B: base 1.0 + forest 1.0 = 2.0
        // B->C: base 1.0 = 1.0
        // Total = 3.0
        assertEquals(3.0, route.totalCost)
    }

    @Test
    fun `road feature reduces cost`() {
        val provider = MockProvider(
            adjacencies = mapOf("A" to listOf("B"), "B" to listOf("C")),
            contents = emptyMap(),
            terrains = emptyMap(),
            features = mapOf("B" to listOf("road"))
        )
        val router = TravelRouter(provider)
        val plan = TravelPlan(infrastructureModifiers = mapOf("road" to -1.0))

        val route = router.calculateRoute("A", "C", plan)

        assertNotNull(route)
        // A->B: base 1.0 + road (-1.0) = 0.0 (floored to 0.01)
        // B->C: base 1.0 = 1.0
        // Total = 1.01
        assertTrue(route.totalCost > 1.0 && route.totalCost < 1.1)
    }

    @Test
    fun `river feature adds cost unless bridge present`() {
        val provider = MockProvider(
            adjacencies = mapOf("A" to listOf("B"), "B" to listOf("C")),
            contents = emptyMap(),
            terrains = emptyMap(),
            features = mapOf("B" to listOf("river"))
        )
        val router = TravelRouter(provider)
        val plan = TravelPlan(infrastructureModifiers = mapOf("river" to 1.0))

        val route = router.calculateRoute("A", "C", plan)

        assertNotNull(route)
        // A->B: base 1.0 + river 1.0 = 2.0
        // B->C: base 1.0 = 1.0
        // Total = 3.0
        assertEquals(3.0, route.totalCost)
    }

    @Test
    fun `river with bridge does not add river cost`() {
        val provider = MockProvider(
            adjacencies = mapOf("A" to listOf("B"), "B" to listOf("C")),
            contents = emptyMap(),
            terrains = emptyMap(),
            features = mapOf("B" to listOf("river", "bridge"))
        )
        val router = TravelRouter(provider)
        val plan = TravelPlan(infrastructureModifiers = mapOf("river" to 1.0))

        val route = router.calculateRoute("A", "C", plan)

        assertNotNull(route)
        // A->B: base 1.0 (river negated by bridge) = 1.0
        // B->C: base 1.0 = 1.0
        // Total = 2.0
        assertEquals(2.0, route.totalCost)
    }

    @Test
    fun `weather multiplier applies to total`() {
        val provider = MockProvider(
            adjacencies = mapOf("A" to listOf("B")),
            contents = emptyMap(),
            terrains = emptyMap(),
            features = emptyMap()
        )
        val router = TravelRouter(provider)
        val plan = TravelPlan(weatherModifier = 1.5)

        val route = router.calculateRoute("A", "B", plan)

        assertNotNull(route)
        // base 1.0 * 1.5 weather = 1.5
        assertEquals(1.5, route.totalCost)
    }

    @Test
    fun `party speed multiplier divides cost`() {
        val provider = MockProvider(
            adjacencies = mapOf("A" to listOf("B")),
            contents = emptyMap(),
            terrains = emptyMap(),
            features = emptyMap()
        )
        val router = TravelRouter(provider)

        // Fast party (2.0 multiplier = half cost)
        val fastPlan = TravelPlan(partySpeedMultiplier = 2.0)
        val fastRoute = router.calculateRoute("A", "B", fastPlan)
        assertEquals(0.5, fastRoute!!.totalCost)

        // Slow party (0.5 multiplier = double cost)
        val slowPlan = TravelPlan(partySpeedMultiplier = 0.5)
        val slowRoute = router.calculateRoute("A", "B", slowPlan)
        assertEquals(2.0, slowRoute!!.totalCost)
    }

    @Test
    fun `hex content travel modifier adds to base cost`() {
        val provider = MockProvider(
            adjacencies = mapOf("A" to listOf("B")),
            contents = mapOf("B" to listOf(content("difficult", HexContentType.CUSTOM, travelModifier = 2))),
            terrains = emptyMap(),
            features = emptyMap()
        )
        val router = TravelRouter(provider)
        val plan = TravelPlan()

        val route = router.calculateRoute("A", "B", plan)

        assertNotNull(route)
        // base 1.0 + travelModifier 2 = 3.0
        assertEquals(3.0, route.totalCost)
    }

    @Test
    fun `dijkstra picks cheaper path through plains over forest`() {
        // Diamond: A -> B -> D (plains) vs A -> C -> D (forest)
        val provider = MockProvider(
            adjacencies = mapOf(
                "A" to listOf("B", "C"),
                "B" to listOf("A", "D"),
                "C" to listOf("A", "D"),
                "D" to listOf("B", "C")
            ),
            contents = emptyMap(),
            terrains = mapOf("C" to Terrain.FOREST), // C is forest
            features = emptyMap()
        )
        val router = TravelRouter(provider)
        val plan = TravelPlan(terrainModifiers = mapOf(Terrain.FOREST to 2.0))

        val route = router.calculateRoute("A", "D", plan)

        assertNotNull(route)
        // Path A-B-D: 1.0 + 1.0 = 2.0 (both plains)
        // Path A-C-D: 1.0 + 3.0 + 1.0 = 5.0 (C is forest: base 1 + forest 2 = 3)
        assertEquals(listOf("A", "B", "D"), route.path)
        assertEquals(2.0, route.totalCost)
    }

    @Test
    fun `unreachable destination returns null`() {
        val provider = MockProvider(
            adjacencies = mapOf("A" to listOf("B"), "B" to listOf("A"), "Z" to emptyList()),
            contents = emptyMap(),
            terrains = emptyMap(),
            features = emptyMap()
        )
        val router = TravelRouter(provider)
        val plan = TravelPlan()

        val route = router.calculateRoute("A", "Z", plan)

        assertNull(route)
    }

    @Test
    fun `start equals end returns zero-cost route`() {
        val provider = MockProvider(
            adjacencies = emptyMap(),
            contents = emptyMap(),
            terrains = emptyMap(),
            features = emptyMap()
        )
        val router = TravelRouter(provider)
        val plan = TravelPlan()

        val route = router.calculateRoute("A", "A", plan)

        assertNotNull(route)
        assertEquals(listOf("A"), route.path)
        assertEquals(0.0, route.totalCost)
        assertEquals(0L, route.estimatedDurationSeconds)
    }
}
/**
 * The router lets a caller supply the cost model so path SELECTION can match how the finished
 * route is PRICED. Without it the camping planner optimised additive hour-ish weights while
 * pricing in Travel activities, and would route around a hex that was actually cheap.
 */
class TravelRouterEdgeCostStrategyTest {
    private class MockProvider(
        private val adjacencies: Map<String, List<String>>,
    ) : TravelProvider {
        override fun getAdjacentHexKeys(hexKey: String): List<String> = adjacencies[hexKey] ?: emptyList()
        override fun getContentForHex(hexKey: String): List<HexContent> = emptyList()
        override fun getTerrainForHex(hexKey: String): Terrain? = null
        override fun getFeaturesForHex(hexKey: String): List<String> = emptyList()
    }

    // A -> cheap -> G costs 2; A -> pricey -> G costs 20 under the custom model.
    private val provider = MockProvider(
        mapOf(
            "A" to listOf("pricey", "cheap"),
            "cheap" to listOf("G"),
            "pricey" to listOf("G"),
        ),
    )

    @Test
    fun `a supplied cost model drives both the chosen path and its total`() {
        val router = TravelRouter(provider) { _, to, _ -> if (to == "pricey") 10.0 else 1.0 }

        val route = router.calculateRoute("A", "G", TravelPlan())

        assertNotNull(route)
        assertEquals(listOf("A", "cheap", "G"), route.path)
        assertEquals(2.0, route.totalCost)
    }

    @Test
    fun `without a cost model the built-in additive weights are used unchanged`() {
        // Caravan routing relies on this default staying put.
        val route = TravelRouter(provider).calculateRoute("A", "G", TravelPlan())

        assertNotNull(route)
        assertEquals(2.0, route.totalCost, "two edges at the 1.0 base cost")
    }
}

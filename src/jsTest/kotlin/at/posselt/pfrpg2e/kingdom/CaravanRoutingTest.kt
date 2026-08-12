package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.routing.TravelProvider
import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.regions.Terrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTravelProvider(
    private val adjacency: Map<String, List<String>>,
    private val contents: Map<String, List<HexContent>> = emptyMap(),
    private val terrains: Map<String, Terrain> = emptyMap(),
    private val features: Map<String, List<String>> = emptyMap()
) : TravelProvider {
    override fun getAdjacentHexKeys(hexKey: String): List<String> = adjacency[hexKey] ?: emptyList()
    override fun getContentForHex(hexKey: String): List<HexContent> = contents[hexKey] ?: emptyList()
    override fun getTerrainForHex(hexKey: String): Terrain? = terrains[hexKey]
    override fun getFeaturesForHex(hexKey: String): List<String> = features[hexKey] ?: emptyList()
}

class CaravanRoutingTest {

    @Test
    fun `eta is at least one turn`() {
        assertEquals(1, caravanEtaTurns(0.0, costPerTurn = 3.0))
        assertEquals(1, caravanEtaTurns(3.0, costPerTurn = 3.0))
    }

    @Test
    fun `eta rounds up partial turns`() {
        assertEquals(2, caravanEtaTurns(4.0, costPerTurn = 3.0))
        assertEquals(2, caravanEtaTurns(6.0, costPerTurn = 3.0))
        assertEquals(3, caravanEtaTurns(7.0, costPerTurn = 3.0))
        assertEquals(3, caravanEtaTurns(9.0, costPerTurn = 3.0))
    }

    @Test
    fun `routes the shortest path along a line of hexes`() {
        // a - b - c - d  (3 edges)
        val adj = mapOf(
            "a" to listOf("b"),
            "b" to listOf("a", "c"),
            "c" to listOf("b", "d"),
            "d" to listOf("c"),
        )
        val route = computeCaravanRoute(FakeTravelProvider(adj), "a", "d")
        assertNotNull(route)
        assertEquals(listOf("a", "b", "c", "d"), route.path)
        assertEquals(3.0, route.totalCost)
    }

    @Test
    fun `picks the cheaper branch in a diamond`() {
        // a -> b -> e (2) vs a -> c -> d -> e (3); shortest is 2
        val adj = mapOf(
            "a" to listOf("b", "c"),
            "b" to listOf("a", "e"),
            "c" to listOf("a", "d"),
            "d" to listOf("c", "e"),
            "e" to listOf("b", "d"),
        )
        val eta = computeCaravanEtaTurns(FakeTravelProvider(adj), "a", "e", costPerTurn = 1.0)
        assertEquals(2, eta) // cost 2 over 1-per-turn = 2 turns
    }

    @Test
    fun `unreachable destination returns null`() {
        val adj = mapOf("a" to listOf("b"), "b" to listOf("a"), "z" to emptyList())
        assertNull(computeCaravanRoute(FakeTravelProvider(adj), "a", "z"))
        assertNull(computeCaravanEtaTurns(FakeTravelProvider(adj), "a", "z"))
    }

    @Test
    fun `road feature reduces route cost`() {
        // a - b - c, with road on b
        val adj = mapOf(
            "a" to listOf("b"),
            "b" to listOf("a", "c"),
            "c" to listOf("b"),
        )
        val provider = FakeTravelProvider(
            adjacency = adj,
            features = mapOf("b" to listOf("road"))
        )
        val route = computeCaravanRoute(provider, "a", "c")
        assertNotNull(route)
        // a->b: base 1.0 + road -1.0 = 0.01 (floored)
        // b->c: base 1.0 = 1.0
        // Total ~1.01 (less than 2.0 for no road)
        assertTrue(route!!.totalCost < 2.0)
        assertTrue(route.totalCost > 1.0)
    }

    @Test
    fun `river feature adds cost unless bridge present`() {
        // a - b - c, with river on b
        val adj = mapOf(
            "a" to listOf("b"),
            "b" to listOf("a", "c"),
            "c" to listOf("b"),
        )
        val providerNoBridge = FakeTravelProvider(
            adjacency = adj,
            features = mapOf("b" to listOf("river"))
        )
        val providerWithBridge = FakeTravelProvider(
            adjacency = adj,
            features = mapOf("b" to listOf("river", "bridge"))
        )

        val routeNoBridge = computeCaravanRoute(providerNoBridge, "a", "c")
        val routeWithBridge = computeCaravanRoute(providerWithBridge, "a", "c")

        assertNotNull(routeNoBridge)
        assertNotNull(routeWithBridge)
        // Without bridge: a->b = 1.0 + river 1.0 = 2.0, b->c = 1.0, total = 3.0
        assertEquals(3.0, routeNoBridge!!.totalCost)
        // With bridge: a->b = 1.0 (river negated), b->c = 1.0, total = 2.0
        assertEquals(2.0, routeWithBridge!!.totalCost)
    }

    @Test
    fun `terrain modifier applies to route cost`() {
        // a - b - c, with forest on b
        val adj = mapOf(
            "a" to listOf("b"),
            "b" to listOf("a", "c"),
            "c" to listOf("b"),
        )
        val provider = FakeTravelProvider(
            adjacency = adj,
            terrains = mapOf("b" to Terrain.FOREST)
        )
        val route = computeCaravanRoute(provider, "a", "c")
        assertNotNull(route)
        // a->b: base 1.0 + forest 1.0 = 2.0
        // b->c: base 1.0 = 1.0
        // Total = 3.0
        assertEquals(3.0, route!!.totalCost)
    }

    @Test
    fun `roaded route cheaper than identical unroaded route`() {
        // Two parallel paths: a - b - d (road on b) vs a - c - d (no road)
        val adj = mapOf(
            "a" to listOf("b", "c"),
            "b" to listOf("a", "d"),
            "c" to listOf("a", "d"),
            "d" to listOf("b", "c"),
        )
        val providerRoaded = FakeTravelProvider(
            adjacency = adj,
            features = mapOf("b" to listOf("road"))
        )
        val providerUnroaded = FakeTravelProvider(adjacency = adj)

        val routeRoaded = computeCaravanRoute(providerRoaded, "a", "d")
        val routeUnroaded = computeCaravanRoute(providerUnroaded, "a", "d")

        assertNotNull(routeRoaded)
        assertNotNull(routeUnroaded)
        assertTrue(routeRoaded!!.totalCost < routeUnroaded!!.totalCost)
    }
}

/**
 * Companion expedition ETAs route through the same provider and cost model as caravans, so the
 * roads a kingdom pays RP for shorten expeditions too. Previously this was straight-line cube
 * distance, which cannot see terrain, roads, rivers or bridges at all.
 */
class ExpeditionRouteEtaTest {
    // Two parallel two-hop routes from a to d: via b, or via c.
    private val adjacency = mapOf(
        "a" to listOf("b", "c"),
        "b" to listOf("a", "d"),
        "c" to listOf("a", "d"),
        "d" to listOf("b", "c"),
    )

    @Test
    fun `a roaded route takes no longer than an unroaded one and costs less`() {
        val roaded = FakeTravelProvider(adjacency, features = mapOf("b" to listOf("road"), "d" to listOf("road")))
        val plain = FakeTravelProvider(adjacency)

        val roadedCost = expeditionRouteCost(arrayOf("a"), "d", roaded)
        val plainCost = expeditionRouteCost(arrayOf("a"), "d", plain)

        assertNotNull(roadedCost)
        assertNotNull(plainCost)
        assertTrue(roadedCost!! < plainCost!!, "roads must make the trip cheaper")
        assertTrue(
            expeditionTravelDaysTo(arrayOf("a"), "d", roaded) <=
                expeditionTravelDaysTo(arrayOf("a"), "d", plain),
        )
    }

    @Test
    fun `rough terrain lengthens an expedition`() {
        val plain = FakeTravelProvider(adjacency)
        val mountains = FakeTravelProvider(
            adjacency,
            terrains = mapOf("b" to Terrain.MOUNTAIN, "c" to Terrain.MOUNTAIN, "d" to Terrain.MOUNTAIN),
        )

        val plainDays = expeditionTravelDaysTo(arrayOf("a"), "d", plain)
        val mountainDays = expeditionTravelDaysTo(arrayOf("a"), "d", mountains)

        assertTrue(mountainDays > plainDays, "mountains must take longer than open ground")
    }

    @Test
    fun `the nearest of several origins wins`() {
        // "c" is adjacent to "d"; "a" is two hops away.
        val provider = FakeTravelProvider(adjacency)
        val fromBoth = expeditionRouteCost(arrayOf("a", "c"), "d", provider)
        val fromFar = expeditionRouteCost(arrayOf("a"), "d", provider)

        assertNotNull(fromBoth)
        assertNotNull(fromFar)
        assertTrue(fromBoth!! < fromFar!!)
    }

    @Test
    fun `an unreachable destination has no routed cost`() {
        val provider = FakeTravelProvider(adjacency)
        assertNull(expeditionRouteCost(arrayOf("a"), "zzz", provider))
    }

    @Test
    fun `travelling nowhere costs no days`() {
        val provider = FakeTravelProvider(adjacency)
        assertEquals(0, expeditionTravelDaysTo(arrayOf("a"), "a", provider))
        assertEquals(0, expeditionTravelDaysTo(arrayOf("a"), null, provider))
    }
}

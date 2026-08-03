package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TravelRoutingTest {
    /** A tiny explicit graph: directed edges with costs, symmetric where declared. */
    private class MapGraph(private val edges: Map<String, Map<String, Double>>) : TravelGraph {
        override fun neighbors(hex: String): List<String> = edges[hex]?.keys?.toList() ?: emptyList()
        override fun edgeCost(from: String, to: String): Double =
            edges[from]?.get(to) ?: Double.POSITIVE_INFINITY
    }

    // ── edge-cost function ──────────────────────────────────────────────────────────────────────
    @Test
    fun defaultEdgeInputsCostBaseOnly() {
        assertEquals(1.0, travelEdgeCost(TravelEdgeInputs()))
        assertEquals(2.5, travelEdgeCost(TravelEdgeInputs(baseCost = 2.5)))
    }

    @Test
    fun multipliersCompose() {
        val cost = travelEdgeCost(
            TravelEdgeInputs(baseCost = 2.0, terrainMultiplier = 1.5, weatherMultiplier = 2.0),
        )
        assertEquals(6.0, cost)
    }

    @Test
    fun roadsCheapenAndUnbridgedRiversCostMore() {
        assertEquals(0.5, travelEdgeCost(TravelEdgeInputs(roadMultiplier = 0.5)))
        assertEquals(3.0, travelEdgeCost(TravelEdgeInputs(riverMultiplier = 3.0)))
    }

    @Test
    fun costIsNeverNegative() {
        assertEquals(0.0, travelEdgeCost(TravelEdgeInputs(baseCost = -5.0)))
    }

    // ── Dijkstra ────────────────────────────────────────────────────────────────────────────────
    @Test
    fun startEqualsGoalIsTrivial() {
        val g = MapGraph(mapOf("a" to mapOf("b" to 1.0)))
        val path = shortestTravelPath(g, "a", "a")
        assertEquals(TravelPath(listOf("a"), 0.0), path)
    }

    @Test
    fun straightLineSumsEdgeCosts() {
        val g = MapGraph(mapOf(
            "a" to mapOf("b" to 1.0),
            "b" to mapOf("c" to 2.0),
        ))
        val path = shortestTravelPath(g, "a", "c")
        assertEquals(listOf("a", "b", "c"), path?.hexes)
        assertEquals(3.0, path?.totalCost)
    }

    @Test
    fun prefersTheCheaperDetourOverTheDirectExpensiveEdge() {
        // a->b direct costs 10; a->x->y->b costs 1+1+1 = 3
        val g = MapGraph(mapOf(
            "a" to mapOf("b" to 10.0, "x" to 1.0),
            "x" to mapOf("y" to 1.0),
            "y" to mapOf("b" to 1.0),
        ))
        val path = shortestTravelPath(g, "a", "b")
        assertEquals(listOf("a", "x", "y", "b"), path?.hexes)
        assertEquals(3.0, path?.totalCost)
    }

    @Test
    fun unreachableGoalReturnsNull() {
        val g = MapGraph(mapOf("a" to mapOf("b" to 1.0)))  // nothing leads to "z"
        assertNull(shortestTravelPath(g, "a", "z"))
    }

    @Test
    fun negativeEdgesAreIgnoredNotFollowed() {
        // the -5 "shortcut" is skipped, so the only route is the positive one
        val g = MapGraph(mapOf(
            "a" to mapOf("b" to 4.0, "trap" to -5.0),
            "trap" to mapOf("b" to 1.0),
        ))
        val path = shortestTravelPath(g, "a", "b")
        assertEquals(listOf("a", "b"), path?.hexes)
        assertTrue(path!!.totalCost == 4.0)
    }
}

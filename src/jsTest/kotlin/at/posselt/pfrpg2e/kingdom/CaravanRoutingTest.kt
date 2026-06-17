package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.HexGridProvider
import at.posselt.pfrpg2e.data.hex.HexContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class FakeHexGridProvider(private val adjacency: Map<String, List<String>>) : HexGridProvider {
    override fun getAdjacentHexKeys(hexKey: String): List<String> = adjacency[hexKey] ?: emptyList()
    override fun getContentForHex(hexKey: String): List<HexContent> = emptyList()
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
        val route = computeCaravanRoute(FakeHexGridProvider(adj), "a", "d")
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
        val eta = computeCaravanEtaTurns(FakeHexGridProvider(adj), "a", "e", costPerTurn = 1.0)
        assertEquals(2, eta) // cost 2 over 1-per-turn = 2 turns
    }

    @Test
    fun `unreachable destination returns null`() {
        val adj = mapOf("a" to listOf("b"), "b" to listOf("a"), "z" to emptyList())
        assertNull(computeCaravanRoute(FakeHexGridProvider(adj), "a", "z"))
        assertNull(computeCaravanEtaTurns(FakeHexGridProvider(adj), "a", "z"))
    }
}

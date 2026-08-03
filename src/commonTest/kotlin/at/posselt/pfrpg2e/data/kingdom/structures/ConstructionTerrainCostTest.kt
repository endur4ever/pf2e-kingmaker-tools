package at.posselt.pfrpg2e.data.kingdom.structures

import kotlin.test.Test
import kotlin.test.assertEquals

class ConstructionTerrainCostTest {

    private fun construction(
        lumber: Int = 4,
        luxuries: Int = 2,
        ore: Int = 6,
        stone: Int = 8,
        rp: Int = 10,
    ) = Construction(
        lumber = lumber,
        luxuries = luxuries,
        ore = ore,
        stone = stone,
        rp = rp,
    )

    @Test
    fun `terrainCostMultiplier returns 1_0 for null terrain`() {
        assertEquals(1.0, construction().terrainCostMultiplier(null))
    }

    @Test
    fun `terrainCostMultiplier returns 1_0 for unknown terrain`() {
        assertEquals(1.0, construction().terrainCostMultiplier("desert"))
    }

    @Test
    fun `terrainCostMultiplier returns 1_5 for forest`() {
        assertEquals(1.5, construction().terrainCostMultiplier("forest"))
    }

    @Test
    fun `terrainCostMultiplier returns 1_0 for plains`() {
        assertEquals(1.0, construction().terrainCostMultiplier("plains"))
    }

    @Test
    fun `terrainCostMultiplier returns 1_5 for swamp`() {
        assertEquals(1.5, construction().terrainCostMultiplier("swamp"))
    }

    @Test
    fun `terrainCostMultiplier returns 1_5 for mountains`() {
        assertEquals(1.5, construction().terrainCostMultiplier("mountains"))
    }

    @Test
    fun `withTerrainCost returns same construction for null terrain`() {
        val c = construction()
        val result = c.withTerrainCost(null)
        assertEquals(c, result)
    }

    @Test
    fun `withTerrainCost returns same construction for plains`() {
        val c = construction()
        val result = c.withTerrainCost("plains")
        assertEquals(c, result)
    }

    @Test
    fun `withTerrainCost applies 1_5x for forest`() {
        val c = construction(lumber = 10, luxuries = 4, ore = 6, stone = 8)
        val result = c.withTerrainCost("forest")
        assertEquals(15, result.lumber)
        assertEquals(6, result.luxuries)
        assertEquals(9, result.ore)
        assertEquals(12, result.stone)
        assertEquals(10, result.rp)  // RP unchanged
    }

    @Test
    fun `withTerrainCost applies 1_5x for mountains`() {
        val c = construction(lumber = 10, luxuries = 4, ore = 6, stone = 8)
        val result = c.withTerrainCost("mountains")
        assertEquals(15, result.lumber)
        assertEquals(6, result.luxuries)
        assertEquals(9, result.ore)
        assertEquals(12, result.stone)
        assertEquals(10, result.rp)  // RP unchanged
    }

    @Test
    fun `withTerrainCost applies 1_5x for swamp`() {
        val c = construction(lumber = 10, luxuries = 4, ore = 6, stone = 8)
        val result = c.withTerrainCost("swamp")
        assertEquals(15, result.lumber)
        assertEquals(6, result.luxuries)
        assertEquals(9, result.ore)
        assertEquals(12, result.stone)
        assertEquals(10, result.rp)  // RP unchanged
    }
}

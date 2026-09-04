package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.camping.routing.kingmakerTerrain
import at.posselt.pfrpg2e.data.regions.Terrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KingmakerTerrainTest {
    @Test
    fun everyIdTheServedKingmakerTerrainTableShipsResolves() {
        // ids read from the served pf2e-kingmaker 2.3.x TERRAIN table (2026-09-04). fromCamelCase
        // matched none of the last three, so mountain hexes priced as open ground.
        assertEquals(Terrain.PLAINS, kingmakerTerrain("plains"))
        assertEquals(Terrain.FOREST, kingmakerTerrain("forest"))
        assertEquals(Terrain.HILLS, kingmakerTerrain("hills"))
        assertEquals(Terrain.SWAMP, kingmakerTerrain("swamp"))
        assertEquals(Terrain.MOUNTAIN, kingmakerTerrain("mountains"), "the enum spells it MOUNTAIN")
        assertEquals(Terrain.SWAMP, kingmakerTerrain("wetlands"), "no WETLANDS constant exists")
        assertEquals(Terrain.AQUATIC, kingmakerTerrain("lake"), "no LAKE constant exists")
    }

    @Test
    fun paddingAndCaseAreTolerated() {
        assertEquals(Terrain.MOUNTAIN, kingmakerTerrain("  Mountains "))
    }

    @Test
    fun anUnknownIdIsNullRatherThanAWrongTerrain() {
        assertNull(kingmakerTerrain("tundra"))
        assertNull(kingmakerTerrain(""))
    }
}

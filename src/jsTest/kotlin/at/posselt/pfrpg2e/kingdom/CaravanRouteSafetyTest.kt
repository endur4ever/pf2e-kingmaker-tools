package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.routing.TravelProvider
import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.regions.Terrain
import kotlin.test.Test
import kotlin.test.assertEquals

class CaravanRouteSafetyTest {

    class FakeTravelProvider : TravelProvider {
        val adjacency = mapOf(
            "1" to listOf("2"),
            "2" to listOf("1", "3"),
            "3" to listOf("2")
        )

        override fun getAdjacentHexKeys(hexKey: String): List<String> {
            return adjacency[hexKey] ?: emptyList()
        }

        override fun getContentForHex(hexKey: String): List<HexContent> = emptyList()

        override fun getTerrainForHex(hexKey: String): Terrain? = null

        override fun getFeaturesForHex(hexKey: String): List<String> = emptyList()
    }

    @Test
    fun testClaimedFractionCalculation() {
        val provider = FakeTravelProvider()
        val route = computeCaravanRoute(provider, "1", "3")
        // The path should be ["1", "2", "3"]
        assertEquals(listOf("1", "2", "3"), route?.path)

        // Mock state for hexes: "1" is claimed, "2" is not, "3" is cleared
        val mockHexes = mapOf(
            "1" to js("{ claimed: true, cleared: false }"),
            "2" to js("{ claimed: false, cleared: false }"),
            "3" to js("{ claimed: false, cleared: true }")
        )

        val path = route!!.path
        val safeCount = path.count { key ->
            val hs = mockHexes[key]
            hs.claimed == true || hs.cleared == true
        }

        val claimedFraction = safeCount.toDouble() / path.size
        assertEquals(2.0 / 3.0, claimedFraction)

        // DC calculation check: base 11, claimed fraction 2/3 (0.66)
        // dc = baseDc - (claimedFraction * 4).roundToInt()
        // dc = 11 - (0.66 * 4).roundToInt() = 11 - 3 = 8
        val dc = caravanRaidDc(CARAVAN_BASE_RAID_DC, partnerStanding = 0, atWar = false, claimedFraction = claimedFraction)
        assertEquals(8, dc)
    }
}

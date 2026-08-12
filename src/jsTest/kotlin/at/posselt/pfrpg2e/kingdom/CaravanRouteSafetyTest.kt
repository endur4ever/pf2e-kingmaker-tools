package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.routing.TravelProvider
import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.regions.Terrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

/**
 * A fully built-out trade road — every hex both held by the kingdom and roaded — is safer than the
 * same route through claimed but trackless country. That is what makes paying to connect
 * settlements by road worth the RP.
 */
class CaravanRoadSafetyTest {
    private fun hexes(vararg pairs: Pair<Boolean, Boolean>) =
        pairs.map { (safe, roaded) -> RouteHexSafety(safe = safe, roaded = roaded) }

    @Test
    fun aFullyRoadedClaimedRouteEarnsTheBonus() {
        val safety = caravanRouteSafety(hexes(true to true, true to true, true to true))
        assertEquals(1.0, safety.claimedFraction)
        assertTrue(safety.fullyRoadedThroughClaimed)
    }

    @Test
    fun oneUnroadedHexBreaksIt() {
        val safety = caravanRouteSafety(hexes(true to true, true to false, true to true))
        assertEquals(1.0, safety.claimedFraction, "still fully claimed")
        assertFalse(safety.fullyRoadedThroughClaimed, "but the road is not continuous")
    }

    @Test
    fun oneUnclaimedHexBreaksItEvenIfRoaded() {
        val safety = caravanRouteSafety(hexes(true to true, false to true))
        assertTrue(safety.claimedFraction < 1.0)
        assertFalse(safety.fullyRoadedThroughClaimed)
    }

    @Test
    fun anEmptyRouteIsNeitherClaimedNorRoaded() {
        val safety = caravanRouteSafety(emptyList())
        assertEquals(0.0, safety.claimedFraction)
        assertFalse(safety.fullyRoadedThroughClaimed)
    }

    @Test
    fun theRoadBonusStacksOnTopOfTheFullClaimedBonus() {
        // Claimed-only: -4. Claimed AND roaded throughout: -5. A raid triggers when the d20 rolls
        // UNDER the DC, so lower is safer.
        val wilderness = caravanRaidDc(20, partnerStanding = 0, atWar = false, claimedFraction = 0.0)
        val claimed = caravanRaidDc(20, partnerStanding = 0, atWar = false, claimedFraction = 1.0)
        val roaded = caravanRaidDc(
            20,
            partnerStanding = 0,
            atWar = false,
            claimedFraction = 1.0,
            fullyRoadedThroughClaimed = true,
        )
        assertEquals(20, wilderness)
        assertEquals(16, claimed)
        assertEquals(15, roaded)
        assertTrue(roaded < claimed, "a built road must be safer than trackless claimed land")
    }

    @Test
    fun theRoadBonusStillRespectsTheDcFloor() {
        val dc = caravanRaidDc(
            5,
            partnerStanding = 20,
            atWar = false,
            claimedFraction = 1.0,
            fullyRoadedThroughClaimed = true,
        )
        assertEquals(5, dc, "the DC never drops below its floor")
    }
}

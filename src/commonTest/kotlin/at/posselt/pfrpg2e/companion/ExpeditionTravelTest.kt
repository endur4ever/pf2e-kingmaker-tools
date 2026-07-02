package at.posselt.pfrpg2e.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpeditionTravelTest {

    @Test
    fun `hexCubeDistance is zero for the same hex and counts steps between cubes`() {
        assertEquals(0, hexCubeDistance(0, 0, 0, 0, 0, 0))
        // One step along +q/-s.
        assertEquals(1, hexCubeDistance(0, 0, 0, 1, 0, -1))
        // Three steps: (0,0,0) -> (2,-1,-1) is max(|2|,|1|,|1|) = 2... verified by formula (2+1+1)/2 = 2.
        assertEquals(2, hexCubeDistance(0, 0, 0, 2, -1, -1))
        // Symmetric.
        assertEquals(
            hexCubeDistance(3, -2, -1, -1, 2, -1),
            hexCubeDistance(-1, 2, -1, 3, -2, -1),
        )
    }

    @Test
    fun `expeditionTravelDays is ceiling division at 2 hexes per day`() {
        assertEquals(0, expeditionTravelDays(0))
        assertEquals(1, expeditionTravelDays(1))
        assertEquals(1, expeditionTravelDays(2))
        assertEquals(2, expeditionTravelDays(3))
        assertEquals(2, expeditionTravelDays(4))
        assertEquals(3, expeditionTravelDays(5))
    }

    @Test
    fun `expeditionTravelDays guards zero pace and negative distance`() {
        assertEquals(0, expeditionTravelDays(-3))
        assertEquals(0, expeditionTravelDays(5, hexesPerDay = 0))
        assertEquals(5, expeditionTravelDays(5, hexesPerDay = 1))
    }

    @Test
    fun `expeditionTotalDays adds round-trip travel to the tier base`() {
        // standard base 3 + 2 travel days each way = 7 ("expected back" includes the return leg)
        assertEquals(7, expeditionTotalDays(tierBaseDays = 3, travelDaysOneWay = 2))
        // no destination -> tier-flat
        assertEquals(3, expeditionTotalDays(tierBaseDays = 3, travelDaysOneWay = 0))
        // negative travel coerced
        assertEquals(2, expeditionTotalDays(tierBaseDays = 2, travelDaysOneWay = -1))
    }

    @Test
    fun `formatHexKeyLabel decodes i-1000-j keys and rejects non-numeric`() {
        assertEquals("5.12", formatHexKeyLabel("5012"))
        assertEquals("205.3", formatHexKeyLabel("205003"))
        assertEquals("0.7", formatHexKeyLabel("7"))
        assertNull(formatHexKeyLabel("not-a-key"))
    }
}

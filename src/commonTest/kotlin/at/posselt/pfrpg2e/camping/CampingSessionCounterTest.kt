package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CampingSessionCounterTest {
    @Test
    fun eachCompletedSessionAdvancesTheCounter() {
        assertEquals(1, nextCampingSessionId(0))
        assertEquals(2, nextCampingSessionId(1))
    }

    @Test
    fun campingDataSavedBeforeTheCounterExistedStartsAtOne() {
        assertEquals(1, nextCampingSessionId(null))
    }

    @Test
    fun consecutiveSessionsNeverShareAnIdentity() {
        // The once-per-session cap compares the recorded id to the current one; if two consecutive
        // sessions could produce the same id, a companion would stay locked into the next session.
        var id: Int? = null
        val seen = mutableListOf<Int>()
        repeat(5) {
            id = nextCampingSessionId(id)
            seen.add(id!!)
        }
        assertEquals(seen.size, seen.toSet().size)
        assertNotEquals(seen[0], seen[1])
    }

    @Test
    fun noCampingDataMeansNoSessionToCapAgainst() {
        // Null propagates so an interaction outside camping is uncapped rather than permanently
        // blocked against a session identity that does not exist.
        assertNull(campingSessionIdOf(null))
        assertEquals("3", campingSessionIdOf(3))
    }
}

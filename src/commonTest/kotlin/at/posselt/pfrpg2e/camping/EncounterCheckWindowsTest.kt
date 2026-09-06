package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FOUR_HOURS = 4 * 3600

class EncounterCheckWindowsTest {
    @Test
    fun aSingleCheckSpansTheWholeNight() {
        val windows = encounterCheckWindows(8 * 3600, intervalSeconds = null)
        assertEquals(1, windows.size)
        assertEquals(1, windows[0].first)
        assertTrue(windows[0].last < 8 * 3600)
    }

    @Test
    fun aNightTooShortToDrawFromYieldsNoWindows() {
        // Random.nextInt(from, until) throws when from >= until; these used to reach it
        for (seconds in 0..2) {
            assertEquals(emptyList(), encounterCheckWindows(seconds, intervalSeconds = null), "night of $seconds s")
            assertEquals(emptyList(), encounterCheckWindows(seconds, intervalSeconds = FOUR_HOURS), "night of $seconds s")
        }
    }

    @Test
    fun everyWindowHasAnInteriorMomentToDraw() {
        for (seconds in listOf(3, 100, 3599, FOUR_HOURS, FOUR_HOURS + 1, 8 * 3600, 30001)) {
            encounterCheckWindows(seconds, FOUR_HOURS).forEach { w ->
                assertTrue(w.first <= w.last, "empty window $w for a night of $seconds s")
                assertTrue(w.last < seconds, "window $w runs past the night of $seconds s")
            }
        }
    }

    @Test
    fun anExactMultipleGivesOneWindowPerInterval() {
        assertEquals(2, encounterCheckWindows(8 * 3600, FOUR_HOURS).size)
        assertEquals(3, encounterCheckWindows(12 * 3600, FOUR_HOURS).size)
    }

    @Test
    fun theTailOfAnUnevenNightIsStillChecked() {
        // 8.5h: flooring gave 2 windows and left the last half hour -- the final watch slot --
        // unchecked on every such night
        val windows = encounterCheckWindows((8.5 * 3600).toInt(), FOUR_HOURS)
        assertEquals(3, windows.size)
        assertTrue(windows.last().first >= 2 * FOUR_HOURS, "the tail window must cover the remainder")
    }

    @Test
    fun aTailTooShortToDrawFromIsDroppedRatherThanThrown() {
        val windows = encounterCheckWindows(2 * FOUR_HOURS + 1, FOUR_HOURS)
        assertEquals(2, windows.size, "a one-second tail has no interior moment and must be omitted")
    }
}

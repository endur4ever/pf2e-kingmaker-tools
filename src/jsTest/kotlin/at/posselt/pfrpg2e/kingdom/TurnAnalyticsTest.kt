package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnAnalyticsTest {

    private fun testRecord(
        turn: Int,
        unrest: Int = 0,
        fame: Int = 0,
        resourcePoints: Int = 0,
        consumption: Int = 0,
        level: Int? = null,
        size: Int? = null,
        ruinCorruption: Int? = null
    ): RawTurnRecord = buildTurnRecord(
        turn = turn,
        timestamp = "2026-06-16T19:00:00Z",
        fame = fame,
        resourcePoints = resourcePoints,
        consumption = consumption,
        unrest = unrest,
        level = level,
        size = size,
        ruinCorruption = ruinCorruption
    )

    @Test
    fun testExtractSeries() {
        val history = arrayOf(
            testRecord(1, unrest = 2, fame = 10, level = 1),
            testRecord(2, unrest = 4, fame = 12, level = null),
            testRecord(3, unrest = 1, fame = 15, level = 2)
        )

        val unrestSeries = extractSeries(history, "unrest", limit = null)
        assertEquals(3, unrestSeries.size)
        assertEquals(Pair(1, 2.0), unrestSeries[0])
        assertEquals(Pair(2, 4.0), unrestSeries[1])
        assertEquals(Pair(3, 1.0), unrestSeries[2])

        // Check nullable filtering
        val levelSeries = extractSeries(history, "level", limit = null)
        assertEquals(2, levelSeries.size)
        assertEquals(Pair(1, 1.0), levelSeries[0])
        assertEquals(Pair(3, 2.0), levelSeries[1])

        // Check limit/windowing
        val windowedUnrest = extractSeries(history, "unrest", limit = 2)
        assertEquals(2, windowedUnrest.size)
        assertEquals(Pair(2, 4.0), windowedUnrest[0])
        assertEquals(Pair(3, 1.0), windowedUnrest[1])
    }

    @Test
    fun testSummarizeSeries() {
        val empty = emptyList<Pair<Int, Double>>()
        assertNull(summarizeSeries(empty))

        val series = listOf(
            1 to 10.0,
            2 to 20.0,
            3 to 30.0
        )
        val summary = summarizeSeries(series)
        assertNotNull(summary)
        assertEquals(10.0, summary.min)
        assertEquals(30.0, summary.max)
        assertEquals(30.0, summary.current)
        assertEquals(20.0, summary.mean)
        assertEquals(20.0, summary.deltaFromStart)

        val flat = listOf(1 to 5.0, 2 to 5.0)
        val flatSummary = summarizeSeries(flat)
        assertNotNull(flatSummary)
        assertEquals(5.0, flatSummary.min)
        assertEquals(5.0, flatSummary.max)
        assertEquals(5.0, flatSummary.current)
        assertEquals(5.0, flatSummary.mean)
        assertEquals(0.0, flatSummary.deltaFromStart)
    }

    @Test
    fun testMapSeriesToCoordinates() {
        val empty = emptyList<Pair<Int, Double>>()
        assertTrue(mapSeriesToCoordinates(empty, 400.0, 150.0).isEmpty())

        // Linear series
        val series = listOf(
            1 to 10.0, // min turn, min val
            2 to 20.0,
            3 to 30.0  // max turn, max val
        )

        val coords = mapSeriesToCoordinates(series, width = 400.0, height = 150.0, padding = 10.0)
        assertEquals(3, coords.size)

        // turn 1, value 10: x should be padding (10), y should be padding + innerHeight (140)
        assertEquals(10.0, coords[0].x)
        assertEquals(140.0, coords[0].y)

        // turn 3, value 30: x should be padding + innerWidth (390), y should be padding (10)
        assertEquals(390.0, coords[2].x)
        assertEquals(10.0, coords[2].y)

        // turn 2, value 20 (middle): x = 200, y = 75
        assertEquals(200.0, coords[1].x)
        assertEquals(75.0, coords[1].y)

        // Flat series check (no divide by zero)
        val flatSeries = listOf(
            1 to 15.0,
            2 to 15.0
        )
        val flatCoords = mapSeriesToCoordinates(flatSeries, width = 400.0, height = 150.0, padding = 10.0)
        assertEquals(2, flatCoords.size)
        assertEquals(10.0, flatCoords[0].x)
        assertEquals(75.0, flatCoords[0].y) // y maps to vertical center
        assertEquals(390.0, flatCoords[1].x)
        assertEquals(75.0, flatCoords[1].y)
    }
}

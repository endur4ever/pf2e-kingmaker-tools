package at.posselt.pfrpg2e.kingdom.sheet.contexts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the analytics series filtering logic (GM vs player view).
 * Mirrors the logic in KingdomSheet.kt around line 3027-3030.
 */
class AnalyticsContextTest {

    private val allMetrics = listOf(
        "unrest" to "kingdom.analytics.unrest",
        "resourcePoints" to "kingdom.analytics.resourcePoints",
        "consumption" to "kingdom.analytics.consumption",
        "fame" to "kingdom.analytics.fame",
        "xpAwarded" to "kingdom.analytics.xpAwarded",
        "warPressure" to "kingdom.analytics.warPressure",
        "pressurePerTurn" to "kingdom.analytics.pressurePerTurn",
        "level" to "kingdom.analytics.level",
        "size" to "kingdom.analytics.size",
        "ruinCorruption" to "kingdom.analytics.ruinCorruption",
        "ruinCrime" to "kingdom.analytics.ruinCrime",
        "ruinDecay" to "kingdom.analytics.ruinDecay",
        "ruinStrife" to "kingdom.analytics.ruinStrife"
    )

    private val playerSafeKeys = setOf("unrest", "fame", "resourcePoints", "size", "level")

    @Test
    fun `GM gets all metrics`() {
        val filtered = filterAnalyticsMetricsForUser(allMetrics, isGM = true)
        assertEquals(allMetrics.size, filtered.size)
        // Verify all expected keys are present
        val keys = filtered.map { it.first }.toSet()
        assertTrue("unrest" in keys)
        assertTrue("resourcePoints" in keys)
        assertTrue("consumption" in keys)
        assertTrue("fame" in keys)
        assertTrue("xpAwarded" in keys)
        assertTrue("warPressure" in keys)
        assertTrue("pressurePerTurn" in keys)
        assertTrue("level" in keys)
        assertTrue("size" in keys)
        assertTrue("ruinCorruption" in keys)
        assertTrue("ruinCrime" in keys)
        assertTrue("ruinDecay" in keys)
        assertTrue("ruinStrife" in keys)
    }

    @Test
    fun `Player gets only safe metrics`() {
        val filtered = filterAnalyticsMetricsForUser(allMetrics, isGM = false)
        // Player-safe: unrest, fame, resourcePoints, size, level = 5 metrics
        assertEquals(5, filtered.size)
        val keys = filtered.map { it.first }.toSet()
        assertTrue("unrest" in keys)
        assertTrue("fame" in keys)
        assertTrue("resourcePoints" in keys)
        assertTrue("size" in keys)
        assertTrue("level" in keys)
        // Excluded: consumption, xpAwarded, warPressure, pressurePerTurn, ruinCorruption, ruinCrime, ruinDecay, ruinStrife
        assertTrue("consumption" !in keys)
        assertTrue("xpAwarded" !in keys)
        assertTrue("warPressure" !in keys)
        assertTrue("pressurePerTurn" !in keys)
        assertTrue("ruinCorruption" !in keys)
        assertTrue("ruinCrime" !in keys)
        assertTrue("ruinDecay" !in keys)
        assertTrue("ruinStrife" !in keys)
    }

    private fun filterMetrics(isGM: Boolean): List<Pair<String, String>> {
        return if (isGM) allMetrics else allMetrics.filter { (key, _) -> key in playerSafeKeys }
    }

    @Test
    fun `players get no target level band`() {
        // The level SERIES is player-safe, but the band around it is derived from the GM's
        // pacing-alert settings -- a line telling the party what level the GM expects them to be.
        assertNull(analyticsLevelTarget(isGM = false, chapterTargetLevel = 7, avgPartyLevel = 5))
        assertNull(analyticsLevelTarget(isGM = false, chapterTargetLevel = null, avgPartyLevel = 5))
    }

    @Test
    fun `the GM band prefers the configured chapter target`() {
        assertEquals(7, analyticsLevelTarget(isGM = true, chapterTargetLevel = 7, avgPartyLevel = 5))
    }

    @Test
    fun `the GM band falls back to the party average when unset`() {
        assertEquals(5, analyticsLevelTarget(isGM = true, chapterTargetLevel = null, avgPartyLevel = 5))
        assertNull(analyticsLevelTarget(isGM = true, chapterTargetLevel = null, avgPartyLevel = null))
    }
}

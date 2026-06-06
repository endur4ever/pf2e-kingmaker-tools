package at.posselt.pfrpg2e.companion

import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionInfluenceTest {

    @Test
    fun `max influence is 12 per Kingmaker Companion Guide`() {
        assertEquals(12, MAX_COMPANION_INFLUENCE)
    }

    @Test
    fun `clampInfluence floors at 0 and caps at 12`() {
        assertEquals(0, clampInfluence(-5))
        assertEquals(0, clampInfluence(0))
        assertEquals(8, clampInfluence(8))
        assertEquals(12, clampInfluence(12))
        assertEquals(12, clampInfluence(99))
    }

    @Test
    fun `influenceBarPercent scales against the cap of 12`() {
        assertEquals(0, influenceBarPercent(0))
        assertEquals(50, influenceBarPercent(6))
        assertEquals(100, influenceBarPercent(12))
        assertEquals(100, influenceBarPercent(20))
    }

    @Test
    fun `normalizeDiscoveryStatus falls back to unknown for null or unrecognized`() {
        assertEquals("trusted", normalizeDiscoveryStatus("trusted"))
        assertEquals("unknown", normalizeDiscoveryStatus(null))
        assertEquals("unknown", normalizeDiscoveryStatus("garbage"))
    }

    @Test
    fun `discovery stages and thresholds align`() {
        assertEquals(listOf("unknown", "introduced", "established", "trusted", "bonded"), companionDiscoveryStages)
        assertEquals(0, companionDiscoveryThresholds["unknown"])
        assertEquals(2, companionDiscoveryThresholds["introduced"])
        assertEquals(8, companionDiscoveryThresholds["bonded"])
    }
}

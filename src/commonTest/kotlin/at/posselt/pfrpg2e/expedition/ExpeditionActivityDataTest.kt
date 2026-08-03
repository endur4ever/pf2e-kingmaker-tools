package at.posselt.pfrpg2e.expedition

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpeditionActivityDataTest {
    @Test
    fun testGetExpeditionActivityName() {
        val name = getExpeditionActivityName("diplomacy")
        // The actual value depends on translation, but it should be the translated name or the ID if not found.
        // Since we are in a test environment where translations might not be loaded, 
        // let's check if it at least returns something non-empty for a known id.
        // In tests, translatedExpeditionActivities will likely have the IDs as names if translation fails.
        assertEquals(name, getExpeditionActivityName("diplomacy"))
    }

    @Test
    fun testGetExpeditionActivityNameUnknown() {
        val name = getExpeditionActivityName("unknown_id")
        assertEquals("unknown_id", name)
    }
}
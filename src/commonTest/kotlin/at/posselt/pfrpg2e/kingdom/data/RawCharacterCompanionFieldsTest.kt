package at.posselt.pfrpg2e.kingdom.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** CQ6 — new RawCharacter companion fields (influence, campAvailable, discoveryStatus, personalQuestIds). */
class RawCharacterCompanionFieldsTest {

    @Test
    fun `factory includes new companion defaults`() {
        val c = RawCharacter("Amiri", "uuid-1")
        assertEquals(0, c.influence)
        assertTrue(c.campAvailable)
        assertEquals("unknown", c.discoveryStatus)
        assertEquals(0, c.personalQuestIds.size)
    }

    @Test
    fun `new fields can be set and read back`() {
        val c = RawCharacter("Amiri").apply {
            influence = 8
            campAvailable = false
            discoveryStatus = "trusted"
            personalQuestIds = arrayOf("q1", "q2")
        }
        assertEquals(8, c.influence)
        assertEquals(false, c.campAvailable)
        assertEquals("trusted", c.discoveryStatus)
        assertEquals(listOf("q1", "q2"), c.personalQuestIds.toList())
    }

    @Test
    fun `existing fields keep their defaults`() {
        val c = RawCharacter("Amiri")
        assertEquals("Amiri", c.name)
        assertEquals(0, c.speed)
        assertEquals("companion", c.role)
        assertTrue(c.active)
    }
}

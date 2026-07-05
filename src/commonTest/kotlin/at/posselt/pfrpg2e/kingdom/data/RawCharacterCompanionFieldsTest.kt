package at.posselt.pfrpg2e.kingdom.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `factory includes new expedition field defaults`() {
        val c = RawCharacter("Amiri")
        assertEquals(1, c.level)
        assertEquals(0, c.xp)
        assertEquals("available", c.expeditionStatus)
        assertNull(c.injuryDaysRemaining)
    }

    @Test
    fun `expedition fields can be set and read back`() {
        val c = RawCharacter("Amiri").apply {
            level = 7
            xp = 250
            expeditionStatus = "onExpedition"
            injuryDaysRemaining = 4
        }
        assertEquals(7, c.level)
        assertEquals(250, c.xp)
        assertEquals("onExpedition", c.expeditionStatus)
        assertEquals(4, c.injuryDaysRemaining)
    }

    @Test
    fun `existing fields keep their defaults`() {
        val c = RawCharacter("Amiri")
        assertEquals("Amiri", c.name)
        assertEquals(0, c.speed)
        assertEquals("companion", c.role)
        assertTrue(c.active)
    }

    @Test
    fun `factory includes career ledger defaults`() {
        val c = RawCharacter("Amiri")
        assertNull(c.careerExpeditions)
        assertNull(c.careerTriumphs)
        assertNull(c.careerScars)
    }

    @Test
    fun `career ledger fields can be set and read back`() {
        val c = RawCharacter("Amiri").apply {
            careerExpeditions = 5
            careerTriumphs = 2
            careerScars = 1
        }
        assertEquals(5, c.careerExpeditions)
        assertEquals(2, c.careerTriumphs)
        assertEquals(1, c.careerScars)
    }
}

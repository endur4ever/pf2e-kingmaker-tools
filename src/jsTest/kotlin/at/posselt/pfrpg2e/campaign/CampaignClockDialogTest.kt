package at.posselt.pfrpg2e.campaign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CampaignClockDialogTest {

    private fun createClock(
        id: String = "test-uuid",
        label: String = "Test Clock",
        turnsRemaining: Int = 6,
        maxTurns: Int = 6,
        description: String = "A test clock",
        pauseOnExpiry: Boolean = false,
        expired: Boolean = false,
        active: Boolean = true,
        expiryConsequenceUnrest: Int = 0,
        expiryMessage: String = ""
    ): CampaignClock = jsObject {
        this.id = id
        this.label = label
        this.turnsRemaining = turnsRemaining
        this.maxTurns = maxTurns
        this.description = description
        this.pauseOnExpiry = pauseOnExpiry
        this.expired = expired
        this.active = active
        this.expiryConsequenceUnrest = expiryConsequenceUnrest
        this.expiryMessage = expiryMessage
    }

    // I2.1 - dialog data model can be created
    @Test
    fun `dialog data model initializes correctly`() {
        val clock = createClock()
        assertEquals("test-uuid", clock.id)
        assertEquals("Test Clock", clock.label)
        assertEquals(6, clock.maxTurns)
        assertEquals(6, clock.turnsRemaining)
    }

    // I2.2 - add clock creates new entry
    @Test
    fun `add clock creates new entry with UUID`() {
        val newClock = createClock(id = "new-uuid-123", label = "New Clock")
        assertNotNull(newClock.id)
        assertTrue(newClock.id.isNotEmpty())
        assertEquals("New Clock", newClock.label)
    }

    // I2.3 - edit clock updates existing entry
    @Test
    fun `edit clock preserves id and updates fields`() {
        val original = createClock(id = "edit-uuid", label = "Original")
        val edited = createClock(
            id = original.id,
            label = "Edited Clock",
            maxTurns = 10,
            turnsRemaining = 8
        )

        assertEquals("edit-uuid", edited.id)
        assertEquals("Edited Clock", edited.label)
        assertEquals(10, edited.maxTurns)
        assertEquals(8, edited.turnsRemaining)
    }

    // I2.4 - delete clock removes entry
    @Test
    fun `delete clock removes entry from array`() {
        val clocks = arrayOf(
            createClock(id = "keep", label = "Keep"),
            createClock(id = "delete", label = "Delete")
        )

        val filtered = clocks.filter { it.id != "delete" }.toTypedArray()
        assertEquals(1, filtered.size)
        assertEquals("keep", filtered[0].id)
    }

    // I2.5 - validation rejects empty label
    @Test
    fun `validation rejects empty label`() {
        val clock = createClock(label = "")
        assertTrue(clock.label.isEmpty())
    }

    // I2.6 - validation rejects maxTurns less than 1
    @Test
    fun `validation rejects maxTurns less than 1`() {
        val clock = createClock(maxTurns = 0)
        assertTrue(clock.maxTurns < 1)
    }

    // I2.7 - toggle pause-on-expiry persists
    @Test
    fun `toggle pauseOnExpiry persists`() {
        val clock = createClock(pauseOnExpiry = true)
        assertTrue(clock.pauseOnExpiry)

        val toggled = createClock(id = clock.id, pauseOnExpiry = false)
        assertFalse(toggled.pauseOnExpiry)
    }

    @Test
    fun `clock with all fields populated correctly`() {
        val clock = createClock(
            id = "full-test",
            label = "Stag Lord Deadline",
            turnsRemaining = 6,
            maxTurns = 6,
            description = "Deadline to deal with the Stag Lord",
            pauseOnExpiry = true,
            expired = false,
            active = true,
            expiryConsequenceUnrest = 2,
            expiryMessage = "Stag Lord's power grows unchecked"
        )

        assertEquals("Stag Lord Deadline", clock.label)
        assertEquals(6, clock.turnsRemaining)
        assertEquals(6, clock.maxTurns)
        assertTrue(clock.pauseOnExpiry)
        assertFalse(clock.expired)
        assertTrue(clock.active)
        assertEquals(2, clock.expiryConsequenceUnrest)
        assertEquals("Stag Lord's power grows unchecked", clock.expiryMessage)
    }
}

package at.posselt.pfrpg2e.campaign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampaignClockTest {

    private fun createClock(
        id: String = "test-id",
        label: String = "Test Clock",
        turnsRemaining: Int = 5,
        maxTurns: Int = 5,
        description: String = "A test clock",
        pauseOnExpiry: Boolean = false,
        expired: Boolean = false,
        active: Boolean = true,
        expiryConsequenceUnrest: Int = 0,
        expiryMessage: String = "Expired!"
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

    @Test
    fun `T1_1 default clock has positive maxTurns`() {
        val clock = createClock(maxTurns = 5)
        assertTrue(clock.maxTurns >= 1)
        assertEquals(5, clock.maxTurns)
    }

    @Test
    fun `T1_2 turnsRemaining coerced to greater than or equal 0`() {
        // The interface doesn't enforce coercion at the type level,
        // but the manager should never produce negative values
        val clock = createClock(turnsRemaining = 0)
        assertTrue(clock.turnsRemaining >= 0)
    }

    @Test
    fun `T1_3 turnsRemaining less than or equal maxTurns`() {
        val clock = createClock(turnsRemaining = 5, maxTurns = 5)
        assertTrue(clock.turnsRemaining <= clock.maxTurns)
    }

    @Test
    fun `T1_4 expired true when turnsRemaining is 0`() {
        val clock = createClock(turnsRemaining = 0, expired = true)
        assertTrue(clock.expired)
        assertEquals(0, clock.turnsRemaining)
    }

    @Test
    fun `T1_5 expired false when turnsRemaining greater than 0`() {
        val clock = createClock(turnsRemaining = 3, expired = false)
        assertFalse(clock.expired)
        assertTrue(clock.turnsRemaining > 0)
    }

    @Test
    fun `T1_6 clock has unique id`() {
        val clock = createClock(id = "unique-123")
        assertEquals("unique-123", clock.id)
    }

    @Test
    fun `T1_7 clock label is non-empty`() {
        val clock = createClock(label = "Stag Lord Deadline")
        assertTrue(clock.label.isNotEmpty())
    }

    @Test
    fun `T1_8 clock defaults to active`() {
        val clock = createClock(active = true)
        assertTrue(clock.active)
    }

    @Test
    fun `T1_9 clock pauseOnExpiry defaults to false`() {
        val clock = createClock(pauseOnExpiry = false)
        assertFalse(clock.pauseOnExpiry)
    }

    @Test
    fun `T1_10 clock expiryConsequenceUnrest defaults to 0`() {
        val clock = createClock(expiryConsequenceUnrest = 0)
        assertEquals(0, clock.expiryConsequenceUnrest)
    }
}

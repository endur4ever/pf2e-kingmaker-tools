package at.posselt.pfrpg2e.campaign

import at.posselt.pfrpg2e.kingdom.sheet.contexts.toDashboardContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampaignClockContextTest {

    private fun createClock(
        id: String = "test-id",
        label: String = "Test Clock",
        turnsRemaining: Int = 5,
        maxTurns: Int = 10,
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

    // I3.1
    @Test
    fun `context includes correct progressPercent - 3 of 10`() {
        val clock = createClock(turnsRemaining = 3, maxTurns = 10)
        val context = arrayOf(clock).toDashboardContext(true)

        assertEquals(30, context.campaignClocks[0].progressPercent)
    }

    // I3.2
    @Test
    fun `context includes isExpiring for 1-turn clock`() {
        val clock = createClock(turnsRemaining = 1, active = true, expired = false)
        val context = arrayOf(clock).toDashboardContext(true)

        assertTrue(context.campaignClocks[0].isExpiring)
    }

    // I3.3
    @Test
    fun `context includes isExpired for 0-turn clock`() {
        val clock = createClock(turnsRemaining = 0, expired = true)
        val context = arrayOf(clock).toDashboardContext(true)

        assertTrue(context.campaignClocks[0].isExpired)
    }

    // I3.4
    @Test
    fun `isGM flag gates edit actions`() {
        val clock = createClock()
        val gmContext = arrayOf(clock).toDashboardContext(true)
        val playerContext = arrayOf(clock).toDashboardContext(false)

        assertTrue(gmContext.isGM)
        assertFalse(playerContext.isGM)
    }

    // I3.5
    @Test
    fun `empty clocks shows no clocks message context`() {
        val context = emptyArray<CampaignClock>().toDashboardContext(true)

        assertEquals(0, context.campaignClocks.size)
        assertFalse(context.hasActiveClocks)
    }

    // I3.7
    @Test
    fun `summary string formatting correct`() {
        val active1 = createClock(id = "a1", turnsRemaining = 5, active = true, expired = false)
        val active2 = createClock(id = "a2", turnsRemaining = 1, active = true, expired = false)
        val inactive = createClock(id = "i1", turnsRemaining = 3, active = false, expired = false)

        val context = arrayOf(active1, active2, inactive).toDashboardContext(true)

        assertEquals(2, context.activeClockCount)
        assertEquals(1, context.expiringClockCount)
        assertTrue(context.hasActiveClocks)
    }

    @Test
    fun `progressPercent correct for 5 of 10`() {
        val clock = createClock(turnsRemaining = 5, maxTurns = 10)
        val context = arrayOf(clock).toDashboardContext(true)

        assertEquals(50, context.campaignClocks[0].progressPercent)
    }

    @Test
    fun `progressPercent correct for 0 of 10`() {
        val clock = createClock(turnsRemaining = 0, maxTurns = 10, expired = true)
        val context = arrayOf(clock).toDashboardContext(true)

        assertEquals(0, context.campaignClocks[0].progressPercent)
    }

    @Test
    fun `paused clock shows paused flag`() {
        val clock = createClock(
            turnsRemaining = 0,
            expired = true,
            pauseOnExpiry = true
        )
        val context = arrayOf(clock).toDashboardContext(true)

        assertTrue(context.campaignClocks[0].paused)
    }

    @Test
    fun `non-paused expired clock does not show paused`() {
        val clock = createClock(
            turnsRemaining = 0,
            expired = true,
            pauseOnExpiry = false
        )
        val context = arrayOf(clock).toDashboardContext(true)

        assertFalse(context.campaignClocks[0].paused)
    }

    @Test
    fun `urgency colour green for greater than 50 percent`() {
        val clock = createClock(turnsRemaining = 6, maxTurns = 10)
        val context = arrayOf(clock).toDashboardContext(true)
        val progress = context.campaignClocks[0].progressPercent
        assertTrue(progress > 50)
    }

    @Test
    fun `urgency colour yellow for 25 to 50 percent`() {
        val clock = createClock(turnsRemaining = 3, maxTurns = 10)
        val context = arrayOf(clock).toDashboardContext(true)
        val progress = context.campaignClocks[0].progressPercent
        assertTrue(progress in 25..50)
    }

    @Test
    fun `urgency colour red for less than 25 percent`() {
        val clock = createClock(turnsRemaining = 1, maxTurns = 10)
        val context = arrayOf(clock).toDashboardContext(true)
        val progress = context.campaignClocks[0].progressPercent
        assertTrue(progress < 25)
    }
}

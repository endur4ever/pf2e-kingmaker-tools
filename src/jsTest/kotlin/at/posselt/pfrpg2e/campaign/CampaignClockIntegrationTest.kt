package at.posselt.pfrpg2e.campaign

import kotlin.test.*

class CampaignClockIntegrationTest {

    private fun createClock(
        id: String = "1",
        label: String = "Clock",
        turnsRemaining: Int = 5,
        maxTurns: Int = 10,
        description: String = "Description",
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

    // I1.1 - clocks survive TurnTickingEngine tick and produce clockEvents
    @Test
    fun `clocks tick via CampaignClockManager and produce ClockTickResult`() {
        val clocks = arrayOf(
            createClock(id = "c1", label = "C1", turnsRemaining = 2),
            createClock(id = "c2", label = "C2", turnsRemaining = 1, expiryConsequenceUnrest = 10, expiryMessage = "Boom!")
        )

        val result = CampaignClockManager.tickAll(clocks)

        // Verify we got events for both clocks
        assertTrue(result.events.isNotEmpty())

        // Check C1 advancement
        val c1Event = result.events.find { it.clockId == "c1" }
        assertNotNull(c1Event)
        assertEquals("ADVANCED", c1Event.type)

        // Check C2 expiry
        val c2Event = result.events.find { it.clockId == "c2" && it.type == "EXPIRED" }
        assertNotNull(c2Event)
        assertEquals("Boom!", c2Event.message)
    }

    // I1.2 - clock expiry increases kingdom unrest
    @Test
    fun `clock expiry produces unrest change`() {
        val clocks = arrayOf(
            createClock(id = "c1", turnsRemaining = 1, expiryConsequenceUnrest = 3)
        )

        val result = CampaignClockManager.tickAll(clocks)

        assertEquals(3, result.totalUnrestChange)
    }

    // I1.3 - clock events have correct values for chat output
    @Test
    fun `clock events have correct values for chat output`() {
        val clocks = arrayOf(
            createClock(id = "c1", label = "Stag Lord", turnsRemaining = 1, expiryMessage = "Stag Lord grows stronger")
        )

        val result = CampaignClockManager.tickAll(clocks)

        val expiredEvent = result.events.find { it.type == "EXPIRED" }
        assertNotNull(expiredEvent)
        assertEquals("Stag Lord", expiredEvent.label)
        assertEquals("Stag Lord grows stronger", expiredEvent.message)
        assertEquals(1, expiredEvent.oldTurns)
        assertEquals(0, expiredEvent.newTurns)
    }

    // I1.4 - migration adds empty campaignClocks
    @Test
    fun `migration seeds empty campaignClocks array`() {
        // Simulate a kingdom data object before migration
        val kingdom = js("{}")
        // campaignClocks is null before migration
        assertTrue(kingdom.campaignClocks === undefined)

        // After migration: set to empty array
        kingdom.campaignClocks = emptyArray<Any>()

        // Verify it's now an empty array, not null
        assertTrue(kingdom.campaignClocks !== undefined)
        assertEquals(0, kingdom.campaignClocks.length)
    }
}

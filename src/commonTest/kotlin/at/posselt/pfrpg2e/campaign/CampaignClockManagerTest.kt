package at.posselt.pfrpg2e.campaign

import kotlin.test.*

class CampaignClockManagerTest {

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

    // T2.1
    @Test
    fun `empty clocks returns empty result`() {
        val result = CampaignClockManager.tickAll(emptyArray())
        assertEquals(0, result.events.size)
        assertEquals(0, result.updatedClocks.size)
        assertEquals(0, result.totalUnrestChange)
    }

    // T2.2
    @Test
    fun `single clock advances by 1`() {
        val clock = createClock(turnsRemaining = 5)
        val result = CampaignClockManager.tickAll(arrayOf(clock))

        assertEquals(1, result.events.size)
        assertEquals("ADVANCED", result.events[0].type)
        assertEquals(5, result.events[0].oldTurns)
        assertEquals(4, result.events[0].newTurns)
        assertEquals(4, result.updatedClocks[0].turnsRemaining)
        assertEquals(0, result.totalUnrestChange)
    }

    // T2.3
    @Test
    fun `clock with 1 turn expires`() {
        val clock = createClock(
            turnsRemaining = 1,
            expiryConsequenceUnrest = 0,
            expiryMessage = "Expired!"
        )
        val result = CampaignClockManager.tickAll(arrayOf(clock))

        assertEquals(1, result.events.size)
        assertEquals("EXPIRED", result.events[0].type)
        assertEquals("Expired!", result.events[0].message)
        assertTrue(result.updatedClocks[0].expired)
        assertEquals(0, result.updatedClocks[0].turnsRemaining)
    }

    // T2.4
    @Test
    fun `clock with 0 turns and expired true is skipped`() {
        val clock = createClock(turnsRemaining = 0, expired = true)
        val result = CampaignClockManager.tickAll(arrayOf(clock))

        assertEquals(0, result.events.size)
        assertEquals(0, result.updatedClocks[0].turnsRemaining)
    }

    // T2.5
    @Test
    fun `pauseOnExpiry stops at 0`() {
        // First tick: clock goes from 1 to 0 and expires
        val clock = createClock(
            turnsRemaining = 1,
            pauseOnExpiry = true,
            expiryConsequenceUnrest = 3
        )
        val result1 = CampaignClockManager.tickAll(arrayOf(clock))
        assertEquals("EXPIRED", result1.events[0].type)
        assertEquals(0, result1.totalUnrestChange) // no unrest because pauseOnExpiry

        // Second tick: clock is at 0 with pauseOnExpiry → PAUSED
        val expiredClock = result1.updatedClocks[0]
        val result2 = CampaignClockManager.tickAll(arrayOf(expiredClock))
        assertEquals(1, result2.events.size)
        assertEquals("PAUSED", result2.events[0].type)
        assertEquals(0, result2.totalUnrestChange)
    }

    // T2.6
    @Test
    fun `pauseOnExpiry clock after GM unblocks`() {
        // Clock expires with pauseOnExpiry
        val clock = createClock(
            turnsRemaining = 1,
            pauseOnExpiry = true,
            expiryConsequenceUnrest = 3
        )
        val result1 = CampaignClockManager.tickAll(arrayOf(clock))
        assertEquals(0, result1.totalUnrestChange)

        // GM unblocks: set pauseOnExpiry = false, keep at 0, expired = true
        val unblocked = createClock(
            id = clock.id,
            label = clock.label,
            turnsRemaining = 0,
            expired = true,
            pauseOnExpiry = false,
            expiryConsequenceUnrest = 3
        )
        // Since expired = true, it should be skipped entirely
        val result2 = CampaignClockManager.tickAll(arrayOf(unblocked))
        assertEquals(0, result2.events.size)
    }

    // T2.7
    @Test
    fun `unrest consequence summed correctly`() {
        val clock1 = createClock(id = "c1", turnsRemaining = 1, expiryConsequenceUnrest = 3)
        val clock2 = createClock(id = "c2", turnsRemaining = 1, expiryConsequenceUnrest = 5)
        val result = CampaignClockManager.tickAll(arrayOf(clock1, clock2))

        assertEquals(8, result.totalUnrestChange)
    }

    // T2.8
    @Test
    fun `inactive clocks skipped completely`() {
        val clock = createClock(active = false)
        val result = CampaignClockManager.tickAll(arrayOf(clock))

        assertEquals(0, result.events.size)
        assertEquals(5, result.updatedClocks[0].turnsRemaining)
    }

    // T2.9
    @Test
    fun `multiple clocks tick independently`() {
        val clock1 = createClock(id = "c1", turnsRemaining = 5)
        val clock2 = createClock(id = "c2", turnsRemaining = 3)
        val clock3 = createClock(id = "c3", turnsRemaining = 1, expiryConsequenceUnrest = 2)
        val result = CampaignClockManager.tickAll(arrayOf(clock1, clock2, clock3))

        assertEquals(4, result.events.size) // ADVANCED + ADVANCED + EXPIRED + TRIGGERED

        val c1Event = result.events.find { it.clockId == "c1" }!!
        assertEquals("ADVANCED", c1Event.type)
        assertEquals(4, result.updatedClocks.find { it.id == "c1" }!!.turnsRemaining)

        val c2Event = result.events.find { it.clockId == "c2" }!!
        assertEquals("ADVANCED", c2Event.type)
        assertEquals(2, result.updatedClocks.find { it.id == "c2" }!!.turnsRemaining)

        val c3Expired = result.events.find { it.clockId == "c3" && it.type == "EXPIRED" }!!
        assertNotNull(c3Expired)
        assertTrue(result.updatedClocks.find { it.id == "c3" }!!.expired)
    }

    // T2.10
    @Test
    fun `ADVANCED event has correct old and new values`() {
        val clock = createClock(turnsRemaining = 5)
        val result = CampaignClockManager.tickAll(arrayOf(clock))

        assertEquals(5, result.events[0].oldTurns)
        assertEquals(4, result.events[0].newTurns)
    }

    // T2.11
    @Test
    fun `EXPIRED event includes expiryMessage`() {
        val clock = createClock(
            turnsRemaining = 1,
            expiryMessage = "The Stag Lord grows stronger!"
        )
        val result = CampaignClockManager.tickAll(arrayOf(clock))

        assertEquals("The Stag Lord grows stronger!", result.events[0].message)
    }

    // T2.12
    @Test
    fun `TRIGGERED event only fires when unrest greater than 0`() {
        val clock = createClock(
            turnsRemaining = 1,
            expiryConsequenceUnrest = 0
        )
        val result = CampaignClockManager.tickAll(arrayOf(clock))

        val hasTrigger = result.events.any { it.type == "TRIGGERED" }
        assertFalse(hasTrigger)
    }

    // T2.13
    @Test
    fun `TRIGGERED event fires for each clock with unrest`() {
        val clock1 = createClock(id = "c1", turnsRemaining = 1, expiryConsequenceUnrest = 2)
        val clock2 = createClock(id = "c2", turnsRemaining = 1, expiryConsequenceUnrest = 5)
        val result = CampaignClockManager.tickAll(arrayOf(clock1, clock2))

        val triggeredCount = result.events.count { it.type == "TRIGGERED" }
        assertEquals(2, triggeredCount)
    }

    // T2.15
    @Test
    fun `progressPercent correct for 5 of 10`() {
        val clock = createClock(turnsRemaining = 5, maxTurns = 10)
        val progressPercent = (clock.turnsRemaining * 100 / clock.maxTurns)
        assertEquals(50, progressPercent)
    }

    // T2.16
    @Test
    fun `progressPercent correct for 0 of 10`() {
        val clock = createClock(turnsRemaining = 0, maxTurns = 10)
        val progressPercent = if (clock.maxTurns > 0) {
            (clock.turnsRemaining * 100 / clock.maxTurns)
        } else 0
        assertEquals(0, progressPercent)
    }

    // T2.20
    @Test
    fun `maxTurns 1 turnsRemaining 1 expires after 1 tick`() {
        val clock = createClock(turnsRemaining = 1, maxTurns = 1)
        val result = CampaignClockManager.tickAll(arrayOf(clock))

        assertEquals(1, result.events.size)
        assertEquals("EXPIRED", result.events[0].type)
        assertTrue(result.updatedClocks[0].expired)
    }

    // T2.22
    @Test
    fun `5 clock stress test all tick independently`() {
        val clocks = (1..5).map { i ->
            createClock(id = "c$i", label = "Clock $i", turnsRemaining = i, maxTurns = 10)
        }.toTypedArray()

        val result = CampaignClockManager.tickAll(clocks)

        assertEquals(5, result.events.size)
        assertEquals(5, result.updatedClocks.size)

        // Verify each clock advanced correctly
        for (i in 1..5) {
            val updated = result.updatedClocks.find { it.id == "c$i" }!!
            assertEquals(i - 1, updated.turnsRemaining)
        }
    }

    // T2.23
    @Test
    fun `does not mutate input array`() {
        val clock = createClock(turnsRemaining = 5)
        val input = arrayOf(clock)
        val originalTurns = clock.turnsRemaining

        CampaignClockManager.tickAll(input)

        // Input clock should not be mutated
        assertEquals(originalTurns, clock.turnsRemaining)
    }

    // T2.24
    @Test
    fun `clock with pauseOnExpiry but turnsRemaining greater than 0 ticks normally`() {
        val clock = createClock(turnsRemaining = 3, pauseOnExpiry = true)
        val result = CampaignClockManager.tickAll(arrayOf(clock))

        assertEquals(1, result.events.size)
        assertEquals("ADVANCED", result.events[0].type)
        assertEquals(2, result.updatedClocks[0].turnsRemaining)
    }

    // T2.25
    @Test
    fun `summary counts active expiring expired`() {
        val active = createClock(id = "a1", turnsRemaining = 5, active = true, expired = false)
        val expiring = createClock(id = "e1", turnsRemaining = 1, active = true, expired = false)
        val expired = createClock(id = "x1", turnsRemaining = 0, active = true, expired = true)
        val inactive = createClock(id = "i1", turnsRemaining = 3, active = false, expired = false)

        val clocks = arrayOf(active, expiring, expired, inactive)
        val activeCount = clocks.count { it.active && !it.expired }
        val expiringCount = clocks.count { it.active && !it.expired && it.turnsRemaining <= 1 }

        assertEquals(2, activeCount) // active + expiring
        assertEquals(1, expiringCount) // only expiring (turnsRemaining == 1)
    }
}

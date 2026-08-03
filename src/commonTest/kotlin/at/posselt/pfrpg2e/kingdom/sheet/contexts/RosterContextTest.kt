package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RosterContextTest {

    @Test
    fun `roster row maps level, xp, expedition status, and injury days`() {
        val companion = RawCharacter("Amiri").apply {
            level = 4
            xp = 2500
            expeditionStatus = "onExpedition"
            injuryDaysRemaining = 1
        }
        val ctx = arrayOf(companion).toRosterContext(isGM = true)
        assertEquals(1, ctx.items.size)
        val row = ctx.items[0]
        assertEquals(4, row.level)
        assertEquals(2500, row.xp)
        assertEquals(50, row.xpPercent) // (2500 % 1000) / 10
        assertEquals("onExpedition", row.expeditionStatus)
        assertEquals("kingdom.companion.expeditionStatus.onExpedition", row.expeditionStatusLabel) // identity localize
        assertEquals(1, row.injuryDaysRemaining)
    }

    @Test
    fun `roster row uses identity localize for discovery status`() {
        val companion = RawCharacter("Amiri").apply {
            discoveryStatus = "trusted"
        }
        val ctx = arrayOf(companion).toRosterContext(isGM = true)
        assertEquals("kingdom.companion.discovery.trusted", ctx.items[0].discoveryStatusLabel)
    }

    @Test
    fun `roster row with no injury has null injury days`() {
        val companion = RawCharacter("Amiri").apply {
            expeditionStatus = "available"
            injuryDaysRemaining = null
        }
        val ctx = arrayOf(companion).toRosterContext(isGM = true)
        assertNull(ctx.items[0].injuryDaysRemaining)
    }

    @Test
    fun `roster row idle shows the available chip and xp percent`() {
        val companion = RawCharacter("Amiri").apply {
            level = 1
            xp = 300
            expeditionStatus = "available"
        }
        val row = arrayOf(companion).toRosterContext(isGM = true).items[0]
        assertEquals("kingdom.companion.expeditionStatus.available", row.expeditionStatusLabel)
        assertEquals(30, row.xpPercent) // (300 % 1000) / 10
    }

    @Test
    fun `roster row recovering shows the recoveringDays countdown via localize`() {
        val companion = RawCharacter("Amiri").apply {
            expeditionStatus = "unavailable"
            injuryDaysRemaining = 3
        }
        // A real localizer returns the template; the {days} placeholder is substituted.
        val localize: (String) -> String = { key ->
            if (key == "kingdom.companion.expeditionStatus.recoveringDays") "Recovering ({days}d)" else key
        }
        val row = arrayOf(companion).toRosterContext(isGM = true, localize = localize).items[0]
        assertEquals("Recovering (3d)", row.expeditionStatusLabel)
        assertEquals(3, row.injuryDaysRemaining)
    }

    @Test
    fun `roster row recovering falls back to the plain label when the template is missing`() {
        val companion = RawCharacter("Amiri").apply {
            expeditionStatus = "unavailable"
            injuryDaysRemaining = 3
        }
        // Identity localize => template == key => fall back to the plain unavailable label.
        val row = arrayOf(companion).toRosterContext(isGM = true).items[0]
        assertEquals("kingdom.companion.expeditionStatus.unavailable", row.expeditionStatusLabel)
    }
}

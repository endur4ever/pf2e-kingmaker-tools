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
}

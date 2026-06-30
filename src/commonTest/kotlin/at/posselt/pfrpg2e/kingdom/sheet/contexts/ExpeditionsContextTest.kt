package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExpeditionsContextTest {

    private fun exp(
        id: String,
        status: String = "inProgress",
        visible: Boolean = false,
        daysRemaining: Int = 1,
        totalDays: Int = 4,
        companionIds: Array<String> = arrayOf("uuid-a"),
    ): RawCompanionExpedition =
        js("{ id: id, title: 'T', activityId: 'scout', status: status, daysRemaining: daysRemaining, totalDays: totalDays, dc: 15, tier: 'standard', accruedXp: 0, accruedInfluenceDelta: 0, accruedInjuries: [], factionStandingDelta: 0, companionIds: companionIds, visibleToPlayers: visible, rewardApplied: false, gmNotes: '' }")
            .unsafeCast<RawCompanionExpedition>()

    @Test
    fun `toExpeditionsContext hides non-visible rows from players but shows all to the GM`() {
        val all = arrayOf(exp("e1", visible = false), exp("e2", visible = true))
        val comps = arrayOf(RawCharacter("Amiri", "uuid-a"))

        val gm = all.toExpeditionsContext(isGM = true, companions = comps)
        assertEquals(2, gm.items.size)
        assertTrue(gm.isGM)

        val player = all.toExpeditionsContext(isGM = false, companions = comps)
        assertEquals(1, player.items.size)
        assertEquals("e2", player.items[0].id)
        assertFalse(player.isGM)
    }

    @Test
    fun `toExpeditionsContext computes progress percent and companion names`() {
        val ctx = arrayOf(exp("e1", visible = true, daysRemaining = 1, totalDays = 4))
            .toExpeditionsContext(isGM = true, companions = arrayOf(RawCharacter("Amiri", "uuid-a")))
        assertEquals(75, ctx.items[0].progressPercent) // (4 - 1) / 4 = 75%
        assertEquals("Amiri", ctx.items[0].companionNames)
        assertTrue(ctx.items[0].isInProgress)
    }

    @Test
    fun `activeExpeditionCount counts only in-flight expeditions`() {
        val es = arrayOf(
            exp("e1", status = "inProgress"),
            exp("e2", status = "awaitingResolution"),
            exp("e3", status = "resolved"),
            exp("e4", status = "cancelled"),
        )
        assertEquals(2, activeExpeditionCount(es))
        assertEquals(3, MAX_CONCURRENT_EXPEDITIONS)
    }
}

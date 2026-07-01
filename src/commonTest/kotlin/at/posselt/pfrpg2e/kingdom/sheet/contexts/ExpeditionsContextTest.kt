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

    @Test
    fun `toExpeditionsContext blanks GM-only fields for players`() {
        val e = js("{ id: 'e1', title: 'T', activityId: 'scout', status: 'awaitingResolution', daysRemaining: 0, totalDays: 4, dc: 18, tier: 'standard', accruedXp: 80, accruedInfluenceDelta: 1, accruedInjuries: ['wounded'], factionStandingDelta: 0, companionIds: ['uuid-a'], visibleToPlayers: true, rewardApplied: false, gmNotes: 'secret plan' }")
            .unsafeCast<RawCompanionExpedition>()
        val comps = arrayOf(RawCharacter("Amiri", "uuid-a"))

        val gmRow = arrayOf(e).toExpeditionsContext(isGM = true, companions = comps).items[0]
        assertEquals(18, gmRow.dc)
        assertEquals("secret plan", gmRow.gmNotes)
        assertEquals(1, gmRow.accruedInjuries.size)

        val playerRow = arrayOf(e).toExpeditionsContext(isGM = false, companions = comps).items[0]
        assertEquals(0, playerRow.dc)              // dc hidden
        assertEquals("", playerRow.gmNotes)        // GM notes hidden
        assertEquals(0, playerRow.accruedInjuries.size) // injury slugs hidden
    }

    @Test
    fun `pruneResolvedExpeditions keeps active rows and caps terminal ones`() {
        val es = arrayOf(
            exp("r1", status = "resolved"),
            exp("r2", status = "cancelled"),
            exp("r3", status = "resolved"),
            exp("a1", status = "inProgress"),
            exp("a2", status = "awaitingResolution"),
        )
        val ids = pruneResolvedExpeditions(es, cap = 2).map { it.id }
        assertTrue("a1" in ids) // active always kept
        assertTrue("a2" in ids)
        assertFalse("r1" in ids) // oldest terminal dropped
        assertTrue("r2" in ids)  // newest 2 terminal kept
        assertTrue("r3" in ids)
        assertEquals(4, ids.size)
    }

    @Test
    fun `pruneResolvedExpeditions is a no-op under the cap`() {
        val es = arrayOf(exp("r1", status = "resolved"), exp("a1", status = "inProgress"))
        assertEquals(2, pruneResolvedExpeditions(es, cap = 50).size)
    }
}

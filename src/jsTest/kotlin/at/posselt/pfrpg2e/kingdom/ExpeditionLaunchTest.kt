package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.data.RawResources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpeditionLaunchTest {

    private fun c(id: String, name: String, status: String = "available"): RawCharacter =
        js("{ actorUuid: id, name: name, expeditionStatus: status, active: true }")
            .unsafeCast<RawCharacter>()

    private fun q(id: String, companionId: String, status: String = "active"): CompanionPersonalQuest =
        CompanionPersonalQuest(
            id = id,
            title = "Quest",
            description = "Desc",
            companionId = companionId,
            status = status,
            visibleToPlayers = true,
            influenceReward = 0
        )

    private fun k(expeditions: Array<RawCompanionExpedition> = emptyArray(), rp: Int = 10): KingdomData {
        val kingdom = js("{}").unsafeCast<KingdomData>()
        kingdom.companionExpeditions = expeditions
        val resources = js("{}").unsafeCast<RawResources>()
        resources.now = rp
        kingdom.resourcePoints = resources
        return kingdom
    }

    private fun e(tier: String, companionIds: Array<String>): RawCompanionExpedition =
        js("{ id: 'e1', title: 'T', tier: tier, companionIds: companionIds }")
            .unsafeCast<RawCompanionExpedition>()

    @Test
    fun testComputeAutonomousProposalWithActiveQuest() {
        val volunteer = c("comp-1", "Amiri")
        val quests = arrayOf(
            q("q-1", "comp-1", "active"),
            q("q-2", "comp-2", "active")
        )
        val proposal = computeAutonomousProposal(volunteer, quests)
        assertEquals("comp-1", proposal.companion.actorUuid)
        assertEquals("personal-quest", proposal.activityId)
        assertEquals("q-1", proposal.targetQuestId)
        assertEquals("standard", proposal.tier)
    }

    @Test
    fun testComputeAutonomousProposalWithoutActiveQuest() {
        val volunteer = c("comp-1", "Amiri")
        val quests = arrayOf(
            q("q-2", "comp-2", "active"),
            q("q-1", "comp-1", "completed")
        )
        val proposal = computeAutonomousProposal(volunteer, quests)
        assertEquals("comp-1", proposal.companion.actorUuid)
        assertEquals("scout", proposal.activityId)
        assertNull(proposal.targetQuestId)
        assertEquals("standard", proposal.tier)
    }

    @Test
    fun testLaunchExpeditionUnderCap() {
        val kingdom = k(rp = 10)
        val comp = c("c-1", "Amiri")
        val exp = e("standard", arrayOf("c-1"))
        val success = launchExpedition(kingdom, exp, arrayOf(comp))
        assertTrue(success)
        assertEquals(1, kingdom.companionExpeditions?.size)
        assertEquals("onExpedition", kingdom.companions?.firstOrNull()?.expeditionStatus)
        // standard cost is 2 RP, so 10 - 2 = 8 RP should remain
        assertEquals(8, kingdom.resourcePoints.now)
    }

    @Test
    fun testLaunchExpeditionOverCap() {
        // max concurrent expeditions cap is 3 (MAX_CONCURRENT_EXPEDITIONS = 3)
        val activeExpeditions = arrayOf(
            js("{ id: 'e-1', daysRemaining: 2, status: 'inProgress', tier: 'routine' }").unsafeCast<RawCompanionExpedition>(),
            js("{ id: 'e-2', daysRemaining: 1, status: 'inProgress', tier: 'routine' }").unsafeCast<RawCompanionExpedition>(),
            js("{ id: 'e-3', daysRemaining: 3, status: 'inProgress', tier: 'routine' }").unsafeCast<RawCompanionExpedition>()
        )
        val kingdom = k(expeditions = activeExpeditions, rp = 10)
        val comp = c("c-1", "Amiri")
        val exp = e("standard", arrayOf("c-1"))
        val success = launchExpedition(kingdom, exp, arrayOf(comp))
        assertFalse(success)
        assertEquals(3, kingdom.companionExpeditions?.size) // should still be 3, not added
        // should not deduct cost
        assertEquals(10, kingdom.resourcePoints.now)
    }

    // ── Review-gap tests: blocked-launch invariants + RP floor + name-keyed companions ──

    @Test
    fun testBlockedLaunchLeavesEverythingUntouched() {
        // 3 in-flight expeditions = at cap; the 4th launch must not mutate ANYTHING.
        val inFlight = arrayOf(
            js("{ id: 'a', title: 'A', tier: 'standard', companionIds: [], status: 'inProgress' }").unsafeCast<RawCompanionExpedition>(),
            js("{ id: 'b', title: 'B', tier: 'standard', companionIds: [], status: 'inProgress' }").unsafeCast<RawCompanionExpedition>(),
            js("{ id: 'c', title: 'C', tier: 'standard', companionIds: [], status: 'awaitingResolution' }").unsafeCast<RawCompanionExpedition>(),
        )
        val kingdom = k(expeditions = inFlight, rp = 10)
        val comp = c("comp-1", "Amiri")
        val launched = launchExpedition(kingdom, e("perilous", arrayOf("comp-1")), arrayOf(comp))
        assertFalse(launched)
        assertEquals(3, kingdom.companionExpeditions?.size) // expedition NOT added
        assertEquals(10, kingdom.resourcePoints.now)        // cost NOT deducted
        assertEquals("available", comp.expeditionStatus)    // companion NOT flipped
        assertNull(kingdom.companions)                      // companions array untouched
    }

    @Test
    fun testLaunchCostFloorsAtZero() {
        // rp=1, standard cost=2: launch succeeds and RP floors at 0 (never negative).
        val kingdom = k(rp = 1)
        val comp = c("comp-1", "Amiri")
        val launched = launchExpedition(kingdom, e("standard", arrayOf("comp-1")), arrayOf(comp))
        assertTrue(launched)
        assertEquals(0, kingdom.resourcePoints.now)
    }

    @Test
    fun testNameKeyedCompanionGetsFlipped() {
        // Unlinked companions are keyed by NAME (actorUuid null) — the status flip must match them.
        val kingdom = k()
        val comp = js("{ actorUuid: null, name: 'Nameless', expeditionStatus: 'available', active: true }")
            .unsafeCast<RawCharacter>()
        val launched = launchExpedition(kingdom, e("routine", arrayOf("Nameless")), arrayOf(comp))
        assertTrue(launched)
        assertEquals("onExpedition", kingdom.companions?.first()?.expeditionStatus)
    }

    @Test
    fun testResolvedExpeditionsDoNotCountTowardCap() {
        val terminal = arrayOf(
            js("{ id: 'a', title: 'A', tier: 'standard', companionIds: [], status: 'resolved' }").unsafeCast<RawCompanionExpedition>(),
            js("{ id: 'b', title: 'B', tier: 'standard', companionIds: [], status: 'resolved' }").unsafeCast<RawCompanionExpedition>(),
            js("{ id: 'c', title: 'C', tier: 'standard', companionIds: [], status: 'cancelled' }").unsafeCast<RawCompanionExpedition>(),
        )
        val kingdom = k(expeditions = terminal, rp = 10)
        val comp = c("comp-1", "Amiri")
        assertTrue(launchExpedition(kingdom, e("standard", arrayOf("comp-1")), arrayOf(comp)))
        assertEquals(4, kingdom.companionExpeditions?.size)
    }
}

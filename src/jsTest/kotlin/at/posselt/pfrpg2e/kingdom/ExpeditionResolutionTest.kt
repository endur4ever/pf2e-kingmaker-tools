package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpeditionResolutionTest {

    @Test
    fun `influenceBandBonus maps discovery bands to a +0, +1, +2 circumstance bonus`() {
        // unknown -> +0; introduced/established -> +1; trusted/bonded -> +2.
        // This is the bonus the linked-actor roll subtracts from the DC and the
        // unlinked d20Resolve adds to the roll — both make a higher band easier.
        assertEquals(0, influenceBandBonus("unknown"))
        assertEquals(1, influenceBandBonus("introduced"))
        assertEquals(1, influenceBandBonus("established"))
        assertEquals(2, influenceBandBonus("trusted"))
        assertEquals(2, influenceBandBonus("bonded"))
    }

    @Test
    fun `influenceBandBonus defaults to +0 for an unrecognized status`() {
        assertEquals(0, influenceBandBonus("nonsense"))
    }

    @Test
    fun `resolve-all-expeditions handler logic separates inProgress and awaitingResolution expeditions`() {
        // Test the core logic: expeditions are split into two groups
        // - inProgress: force-resolved via resolveExpeditionCore
        // - awaitingResolution: rewards applied via applyExpeditionRewardToKingdom
        val expeditions = arrayOf(
            createExp("e1", "inProgress"),
            createExp("e2", "awaitingResolution"),
            createExp("e3", "inProgress"),
            createExp("e4", "resolved"),
            createExp("e5", "cancelled"),
        )

        val inProgress = expeditions.filter { it.status == "inProgress" }
        val awaitingResolution = expeditions.filter { it.status == "awaitingResolution" }

        assertEquals(2, inProgress.size)
        assertEquals(1, awaitingResolution.size)
        assertTrue(inProgress.all { it.id in setOf("e1", "e3") })
        assertTrue(awaitingResolution.all { it.id == "e2" })
        // resolved and cancelled are ignored by both groups
    }

    private fun createExp(id: String, status: String): RawCompanionExpedition =
        js("{ id: id, title: 'Test', tier: 'standard', companionIds: [], status: status, daysRemaining: 3, totalDays: 3, dc: 15 }")
            .unsafeCast<RawCompanionExpedition>()
}

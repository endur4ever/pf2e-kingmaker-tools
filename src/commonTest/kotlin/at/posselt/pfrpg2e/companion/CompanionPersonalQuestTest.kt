package at.posselt.pfrpg2e.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompanionPersonalQuestTest {

    private fun quest(
        id: String = "q1",
        status: String = "active",
        visible: Boolean = false,
        turns: Int? = null,
        reward: Int = 0,
    ): CompanionPersonalQuest =
        js("{ id: id, title: 'T', description: 'D', companionId: 'c1', status: status, turnsRemaining: turns, visibleToPlayers: visible, influenceReward: reward }")
            .unsafeCast<CompanionPersonalQuest>()

    @Test
    fun `default-ish quest is active, GM-only, no deadline`() {
        val q = quest()
        assertEquals("active", q.status)
        assertFalse(q.visibleToPlayers)
        assertNull(q.turnsRemaining)
        assertEquals(0, q.influenceReward)
    }

    @Test
    fun `status transitions are representable`() {
        assertEquals("completed", quest(status = "completed").status)
        assertEquals("failed", quest(status = "failed").status)
        assertEquals("abandoned", quest(status = "abandoned").status)
    }

    @Test
    fun `all fields round-trip`() {
        val q = quest(id = "q9", status = "active", visible = true, turns = 3, reward = 4)
        assertEquals("q9", q.id)
        assertEquals("c1", q.companionId)
        assertEquals(3, q.turnsRemaining)
        assertTrue(q.visibleToPlayers)
        assertEquals(4, q.influenceReward)
    }

    // ── selectRewardQuest (expedition -> quest linkage) ─────────────────────

    private fun q(id: String, companionId: String, status: String = "active"): CompanionPersonalQuest =
        js("{ id: id, title: 'T', description: 'D', companionId: companionId, status: status, turnsRemaining: null, visibleToPlayers: false, influenceReward: 0 }")
            .unsafeCast<CompanionPersonalQuest>()

    @Test
    fun `selectRewardQuest prefers the targeted quest over the first active`() {
        val q1 = q("q1", "c1")
        val q2 = q("q2", "c1")
        // Even though q1 is the first active quest for c1, the explicit target wins.
        assertEquals("q2", selectRewardQuest(listOf(q1, q2), companionId = "c1", targetQuestId = "q2")?.id)
    }

    @Test
    fun `selectRewardQuest falls back to first active when target is missing or inactive`() {
        val q1 = q("q1", "c1", status = "active")
        val q2 = q("q2", "c1", status = "completed")
        // Target points at a non-active quest -> fall back to first active.
        assertEquals("q1", selectRewardQuest(listOf(q1, q2), "c1", targetQuestId = "q2")?.id)
        // Legacy expedition with no target -> first active.
        assertEquals("q1", selectRewardQuest(listOf(q1, q2), "c1", targetQuestId = null)?.id)
    }

    @Test
    fun `selectRewardQuest returns null when no active quest matches`() {
        val done = q("q1", "c1", status = "completed")
        assertNull(selectRewardQuest(listOf(done), "c1", targetQuestId = null))
    }
}

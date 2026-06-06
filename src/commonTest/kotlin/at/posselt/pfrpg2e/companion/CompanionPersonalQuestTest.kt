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
}

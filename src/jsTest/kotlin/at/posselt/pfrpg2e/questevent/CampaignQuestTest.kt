package at.posselt.pfrpg2e.questevent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampaignQuestTest {
    private fun makeQuest(
        status: QuestStatus = QuestStatus.ACTIVE,
        turnsRemaining: Int? = null,
    ) = CampaignQuest(
        id = "cq-1",
        templateId = "qt-1",
        name = "Test Quest",
        type = QuestType.COMBAT,
        description = "Test",
        recommendedLevel = 5,
        objectives = emptyList(),
        rewards = QuestRewards(xp = 40),
        status = status,
        turnsRemaining = turnsRemaining,
        createdAt = "2026-06-01T00:00:00Z",
        campaignId = "camp-1",
    )

    @Test
    fun `new quest defaults to ACTIVE and hidden`() {
        val q = makeQuest()
        assertEquals(QuestStatus.ACTIVE, q.status)
        assertFalse(q.visibleToPlayers)
    }

    @Test
    fun `status values serialize correctly`() {
        assertEquals("active", QuestStatus.ACTIVE.value)
        assertEquals("completed", QuestStatus.COMPLETED.value)
        assertEquals("failed", QuestStatus.FAILED.value)
        assertEquals("abandoned", QuestStatus.ABANDONED.value)
        assertEquals("on_hold", QuestStatus.ON_HOLD.value)
    }

    @Test
    fun `turnsRemaining null means no deadline`() {
        val q = makeQuest()
        assertEquals(null, q.turnsRemaining)
    }

    @Test
    fun `generatedByEvent defaults to false`() {
        val q = makeQuest()
        assertFalse(q.generatedByEvent)
    }

    @Test
    fun `sourceEventId defaults to null`() {
        val q = makeQuest()
        assertEquals(null, q.sourceEventId)
    }
}

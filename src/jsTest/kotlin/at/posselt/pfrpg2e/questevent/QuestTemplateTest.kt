package at.posselt.pfrpg2e.questevent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class QuestTemplateTest {
    @Test
    fun `QuestTemplate defaults match expectations`() {
        val template = QuestTemplate(
            id = "test-1",
            name = "Test Quest",
            type = QuestType.COMBAT,
            description = "A test quest",
            recommendedLevel = 5,
            objectives = emptyList(),
            rewards = QuestRewards(),
        )
        assertFalse(template.isDefaultVisibleToPlayers)
        assertEquals(emptyList(), template.sourceEventTraits)
    }

    @Test
    fun `GM-only visibility is default`() {
        val tmpl = QuestTemplate(
            id = "test-2",
            name = "Hidden Quest",
            type = QuestType.EXPLORATION,
            description = "GM only quest",
            recommendedLevel = 3,
            objectives = listOf(QuestObjective(id = "obj1", description = "Find the ruin")),
            rewards = QuestRewards(xp = 80, rp = 5),
        )
        assertFalse(tmpl.isDefaultVisibleToPlayers)
    }
}

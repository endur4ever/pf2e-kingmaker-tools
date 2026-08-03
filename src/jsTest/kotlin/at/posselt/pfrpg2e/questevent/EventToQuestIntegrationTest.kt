package at.posselt.pfrpg2e.questevent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test: create event -> generate quest -> verify campaign_quest record.
 * Tests the full flow from event template through generator to CampaignQuest creation.
 */
class EventToQuestIntegrationTest {

    private fun createEvent(
        id: String = "evt-bandit-raids",
        name: String = "Bandit Raids",
        description: String = "Bandits are raiding trade routes.",
        traits: List<String> = listOf("military", "threat"),
    ) = KingdomEventTemplate(
        id = id,
        name = name,
        description = description,
        traits = traits,
    )

    // I1.1: Full flow - event -> generator -> CampaignQuest with correct fields
    @Test
    fun `event generates quest with correct source tracking`() {
        val event = createEvent()

        val result = QuestGenerator.generateFromEvent(
            event = event,
            kingdomLevel = 5,
            currentQuests = emptyList(),
        )

        assertNotNull(result)
        assertTrue(result.isEligible)
        assertEquals("evt-bandit-raids", result.sourceEventId)
        assertEquals("Bandit Raids", result.sourceEventName)
        assertFalse(result.preview.isDefaultVisibleToPlayers)
    }

    // I1.2: Generated quest can be constructed with correct campaign tracking fields
    @Test
    fun `generated quest tracks source event correctly`() {
        val event = createEvent()
        val result = QuestGenerator.generateFromEvent(
            event = event,
            kingdomLevel = 3,
            currentQuests = emptyList(),
        )
        assertNotNull(result)

        // Simulate what GenerateQuestDialog does on commit
        val quest = CampaignQuest(
            id = "cq-1",
            templateId = result.preview.id,
            name = result.preview.name,
            type = result.preview.type,
            description = result.preview.description,
            recommendedLevel = result.preview.recommendedLevel,
            objectives = result.preview.objectives,
            rewards = result.preview.rewards,
            status = QuestStatus.ACTIVE,
            generatedByEvent = true,
            sourceEventId = result.sourceEventId,
            sourceEventName = result.sourceEventName,
            createdAt = "2026-06-01T00:00:00Z",
            campaignId = "camp-1",
        )

        assertEquals("cq-1", quest.id)
        assertTrue(quest.generatedByEvent)
        assertEquals("evt-bandit-raids", quest.sourceEventId)
        assertEquals("Bandit Raids", quest.sourceEventName)
        assertEquals(QuestStatus.ACTIVE, quest.status)
        assertFalse(quest.visibleToPlayers)
    }

    // I1.3: Quest rewards are snapshot from template at creation time
    @Test
    fun `quest rewards are snapshot from generator output`() {
        val event = createEvent()
        val result = QuestGenerator.generateFromEvent(
            event = event,
            kingdomLevel = 5,
            currentQuests = emptyList(),
        )
        assertNotNull(result)

        val rewards = result.preview.rewards
        // military + threat should give XP and RP
        assertTrue(rewards.xp > 0)
        assertTrue(rewards.rp > 0)
    }

    // I1.4: Multiple events generate distinct quests
    @Test
    fun `different events produce different quest templates`() {
        val event1 = createEvent(id = "evt-bandits", name = "Bandit Raids", traits = listOf("military", "threat"))
        val event2 = createEvent(id = "evt-crop-failure", name = "Crop Failure", traits = listOf("agriculture", "negative"))

        val result1 = QuestGenerator.generateFromEvent(event1, 4, emptyList())
        val result2 = QuestGenerator.generateFromEvent(event2, 4, emptyList())

        assertNotNull(result1)
        assertNotNull(result2)
        assertTrue(result1.preview.name.contains("Bandit Raids"))
        assertTrue(result2.preview.name.contains("Crop Failure"))
        // military -> COMBAT, agriculture -> EXPLORATION
        assertEquals(QuestType.COMBAT, result1.preview.type)
        assertEquals(QuestType.EXPLORATION, result2.preview.type)
    }

    // I1.5: Quest generation respects max active quests limit
    @Test
    fun `generation blocked when max active quests reached`() {
        val event = createEvent()
        val activeQuests = (1..10).map { i ->
            CampaignQuest(
                id = "cq-$i",
                templateId = "qt-$i",
                name = "Quest $i",
                type = QuestType.EXPLORATION,
                description = "Test",
                recommendedLevel = 1,
                objectives = emptyList(),
                rewards = QuestRewards(),
                status = QuestStatus.ACTIVE,
                generatedByEvent = true,
                createdAt = "2026-06-01T00:00:00Z",
                campaignId = "camp-1",
            )
        }

        val result = QuestGenerator.generateFromEvent(
            event = event,
            kingdomLevel = 5,
            currentQuests = activeQuests,
            settings = QuestGeneratorSettings(maxActiveGeneratedQuests = 10),
        )
        assertNotNull(result)
        assertFalse(result.isEligible)
        assertNotNull(result.ineligibilityReason)
    }

    // I1.6: Non-generated quests don't count toward the limit
    @Test
    fun `manual quests do not count toward generated quest limit`() {
        val event = createEvent()
        val manualQuests = (1..15).map { i ->
            CampaignQuest(
                id = "cq-$i",
                templateId = "qt-$i",
                name = "Quest $i",
                type = QuestType.EXPLORATION,
                description = "Test",
                recommendedLevel = 1,
                objectives = emptyList(),
                rewards = QuestRewards(),
                status = QuestStatus.ACTIVE,
                generatedByEvent = false,
                createdAt = "2026-06-01T00:00:00Z",
                campaignId = "camp-1",
            )
        }

        val result = QuestGenerator.generateFromEvent(
            event = event,
            kingdomLevel = 5,
            currentQuests = manualQuests,
            settings = QuestGeneratorSettings(maxActiveGeneratedQuests = 10),
        )
        assertNotNull(result)
        assertTrue(result.isEligible)
    }
}

package at.posselt.pfrpg2e.questevent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestGeneratorTest {
    private val sampleEvent = KingdomEventTemplate(
        id = "evt-crop-failure",
        name = "Crop Failure",
        description = "Crops are failing across the kingdom.",
        traits = listOf("agriculture", "negative"),
        suggestedQuestTemplateIds = listOf("qt-druid-conflict"),
    )

    private fun makeQuest(
        status: QuestStatus = QuestStatus.ACTIVE,
        generatedByEvent: Boolean = true,
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
        generatedByEvent = generatedByEvent,
        createdAt = "2026-06-01T00:00:00Z",
        campaignId = "camp-1",
    )

    @Test
    fun `generator maps event traits to quest type`() {
        val result = QuestGenerator.generateFromEvent(
            event = sampleEvent,
            kingdomLevel = 4,
            currentQuests = emptyList(),
        )
        assertNotNull(result)
        assertEquals("Crop Failure", result.sourceEventName)
        assertFalse(result.preview.isDefaultVisibleToPlayers)
        assertTrue(result.isEligible)
    }

    @Test
    fun `generator respects max active quests limit`() {
        val activeQuests = (1..10).map {
            makeQuest(status = QuestStatus.ACTIVE)
        }
        val result = QuestGenerator.generateFromEvent(
            event = sampleEvent,
            kingdomLevel = 4,
            currentQuests = activeQuests,
            settings = QuestGeneratorSettings(maxActiveGeneratedQuests = 10),
        )
        assertNotNull(result)
        assertFalse(result.isEligible)
        assertNotNull(result.ineligibilityReason)
    }

    @Test
    fun `generator allows generation under limit`() {
        val activeQuests = (1..5).map {
            makeQuest(status = QuestStatus.ACTIVE)
        }
        val result = QuestGenerator.generateFromEvent(
            event = sampleEvent,
            kingdomLevel = 4,
            currentQuests = activeQuests,
            settings = QuestGeneratorSettings(maxActiveGeneratedQuests = 10),
        )
        assertNotNull(result)
        assertTrue(result.isEligible)
    }

    @Test
    fun `advanceQuestTimers decrements turnsRemaining`() {
        val quest = makeQuest(status = QuestStatus.ACTIVE).copy(
            turnsRemaining = 3,
            generatedByEvent = true,
        )
        val result = QuestGenerator.advanceQuestTimers(listOf(quest), 5)
        assertEquals(1, result.size)
        assertEquals(2, result[0].turnsRemaining)
        assertEquals(QuestStatus.ACTIVE, result[0].status)
    }

    @Test
    fun `advanceQuestTimers marks quest FAILED when timer hits 0`() {
        val quest = makeQuest(status = QuestStatus.ACTIVE).copy(
            turnsRemaining = 1,
            generatedByEvent = true,
        )
        val result = QuestGenerator.advanceQuestTimers(listOf(quest), 5)
        assertEquals(1, result.size)
        assertEquals(QuestStatus.FAILED, result[0].status)
        assertEquals(0, result[0].turnsRemaining)
    }

    @Test
    fun `advanceQuestTimers does not affect non-generated quests`() {
        val quest = makeQuest(status = QuestStatus.ACTIVE, generatedByEvent = false).copy(
            turnsRemaining = 1,
        )
        val result = QuestGenerator.advanceQuestTimers(listOf(quest), 5)
        assertEquals(1, result.size)
        assertEquals(QuestStatus.ACTIVE, result[0].status)
        assertEquals(1, result[0].turnsRemaining)
    }

    @Test
    fun `advanceQuestTimers does not affect non-ACTIVE quests`() {
        val quest = makeQuest(status = QuestStatus.COMPLETED).copy(
            turnsRemaining = 1,
            generatedByEvent = true,
        )
        val result = QuestGenerator.advanceQuestTimers(listOf(quest), 5)
        assertEquals(1, result.size)
        assertEquals(QuestStatus.COMPLETED, result[0].status)
    }

    @Test
    fun `canGenerateMore returns true when under limit`() {
        val quests = (1..3).map { makeQuest() }
        assertTrue(QuestGenerator.canGenerateMore(quests, QuestGeneratorSettings(maxActiveGeneratedQuests = 10)))
    }

    @Test
    fun `canGenerateMore returns false when at limit`() {
        val quests = (1..10).map { makeQuest() }
        assertFalse(QuestGenerator.canGenerateMore(quests, QuestGeneratorSettings(maxActiveGeneratedQuests = 10)))
    }

    @Test
    fun `QuestGeneratorSettings has correct defaults`() {
        val settings = QuestGeneratorSettings()
        assertFalse(settings.defaultVisibilityToPlayers)
        assertEquals(10, settings.maxActiveGeneratedQuests)
        assertTrue(settings.autoAdvanceQuestTimersOnTurn)
    }
}

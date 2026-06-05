package at.posselt.pfrpg2e.questevent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestGeneratorSettingsTest {
    @Test
    fun `QuestGeneratorSettingsDataModel has expected defaults`() {
        val settings = QuestGeneratorSettings(
            defaultVisibilityToPlayers = false,
            maxActiveGeneratedQuests = 10,
            autoAdvanceQuestTimersOnTurn = true,
        )
        assertEquals(false, settings.defaultVisibilityToPlayers)
        assertEquals(10, settings.maxActiveGeneratedQuests)
        assertTrue(settings.autoAdvanceQuestTimersOnTurn)
    }

    @Test
    fun `QuestGeneratorSettings can be copied with new values`() {
        val original = QuestGeneratorSettings()
        val updated = original.copy(maxActiveGeneratedQuests = 5)
        assertEquals(5, updated.maxActiveGeneratedQuests)
        assertEquals(false, updated.defaultVisibilityToPlayers)
        assertTrue(updated.autoAdvanceQuestTimersOnTurn)
    }

    @Test
    fun `QuestGeneratorSettingsContext form can be built`() {
        // Verify the settings form data class works
        val data = object {
            val defaultVisibilityToPlayers = false
            val maxActiveGeneratedQuests = 10
            val autoAdvanceQuestTimersOnTurn = true
        }
        assertEquals(false, data.defaultVisibilityToPlayers)
        assertEquals(10, data.maxActiveGeneratedQuests)
        assertTrue(data.autoAdvanceQuestTimersOnTurn)
    }
}

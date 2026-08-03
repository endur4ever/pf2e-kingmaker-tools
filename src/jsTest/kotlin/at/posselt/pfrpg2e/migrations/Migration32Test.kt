package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.migrations.migrations.Migration32
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals

class Migration32Test {

    @Test
    fun testMigration32SeedsAllSixFields() = runTest {
        val gameMock = unsafeJso<Game>()
        val kingdom = unsafeJso<dynamic> {}

        Migration32().migrateKingdom(gameMock, kingdom)

        assertEquals(0, kingdom.questTemplates.unsafeCast<Array<dynamic>>().size)
        assertEquals(0, kingdom.campaignQuests.unsafeCast<Array<dynamic>>().size)
        assertEquals(0, kingdom.kingdomEventTemplates.unsafeCast<Array<dynamic>>().size)
        assertEquals(0, kingdom.campaignKingdomEvents.unsafeCast<Array<dynamic>>().size)
        assertEquals(0, kingdom.eventGenerationLogs.unsafeCast<Array<dynamic>>().size)

        val settings = kingdom.questGeneratorSettings.unsafeCast<dynamic>()
        assertEquals(false, settings.defaultVisibilityToPlayers.unsafeCast<Boolean>())
        assertEquals(10, settings.maxActiveGeneratedQuests.unsafeCast<Int>())
        assertEquals(true, settings.autoAdvanceQuestTimersOnTurn.unsafeCast<Boolean>())
    }

    @Test
    fun testMigration32PreservesExistingFields() = runTest {
        val gameMock = unsafeJso<Game>()
        val kingdom = unsafeJso<dynamic> {
            questTemplates = arrayOf("existing")
            campaignQuests = arrayOf("existing")
            kingdomEventTemplates = arrayOf("existing")
            campaignKingdomEvents = arrayOf("existing")
            eventGenerationLogs = arrayOf("existing")
            questGeneratorSettings = unsafeJso<dynamic> {
                defaultVisibilityToPlayers = true
                maxActiveGeneratedQuests = 5
                autoAdvanceQuestTimersOnTurn = false
            }
        }

        Migration32().migrateKingdom(gameMock, kingdom)

        assertEquals(1, kingdom.questTemplates.unsafeCast<Array<dynamic>>().size)
        assertEquals(1, kingdom.campaignQuests.unsafeCast<Array<dynamic>>().size)
        assertEquals(1, kingdom.kingdomEventTemplates.unsafeCast<Array<dynamic>>().size)
        assertEquals(1, kingdom.campaignKingdomEvents.unsafeCast<Array<dynamic>>().size)
        assertEquals(1, kingdom.eventGenerationLogs.unsafeCast<Array<dynamic>>().size)

        val settings = kingdom.questGeneratorSettings.unsafeCast<dynamic>()
        assertEquals(true, settings.defaultVisibilityToPlayers.unsafeCast<Boolean>())
        assertEquals(5, settings.maxActiveGeneratedQuests.unsafeCast<Int>())
        assertEquals(false, settings.autoAdvanceQuestTimersOnTurn.unsafeCast<Boolean>())
    }

    @Test
    fun testMigration32HandlesPartialExistingFields() = runTest {
        val gameMock = unsafeJso<Game>()
        val kingdom = unsafeJso<dynamic> {
            questTemplates = arrayOf("existing")
            // other fields missing
        }

        Migration32().migrateKingdom(gameMock, kingdom)

        assertEquals(1, kingdom.questTemplates.unsafeCast<Array<dynamic>>().size)
        assertEquals(0, kingdom.campaignQuests.unsafeCast<Array<dynamic>>().size)
        assertEquals(0, kingdom.kingdomEventTemplates.unsafeCast<Array<dynamic>>().size)
        assertEquals(0, kingdom.campaignKingdomEvents.unsafeCast<Array<dynamic>>().size)
        assertEquals(0, kingdom.eventGenerationLogs.unsafeCast<Array<dynamic>>().size)

        val settings = kingdom.questGeneratorSettings.unsafeCast<dynamic>()
        assertEquals(false, settings.defaultVisibilityToPlayers.unsafeCast<Boolean>())
        assertEquals(10, settings.maxActiveGeneratedQuests.unsafeCast<Int>())
        assertEquals(true, settings.autoAdvanceQuestTimersOnTurn.unsafeCast<Boolean>())
    }
}
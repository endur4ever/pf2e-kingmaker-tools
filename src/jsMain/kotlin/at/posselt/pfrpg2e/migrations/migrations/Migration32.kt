package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Adds quest/event generator fields to kingdom data.
 * Seeds empty arrays for questTemplates, campaignQuests, kingdomEventTemplates,
 * campaignKingdomEvents, eventGenerationLogs, and default QuestGeneratorSettings.
 */
class Migration32 : Migration(32) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.questTemplates == null) {
            kingdom.questTemplates = emptyArray<Any>()
        }
        if (kingdom.campaignQuests == null) {
            kingdom.campaignQuests = emptyArray<Any>()
        }
        if (kingdom.kingdomEventTemplates == null) {
            kingdom.kingdomEventTemplates = emptyArray<Any>()
        }
        if (kingdom.campaignKingdomEvents == null) {
            kingdom.campaignKingdomEvents = emptyArray<Any>()
        }
        if (kingdom.eventGenerationLogs == null) {
            kingdom.eventGenerationLogs = emptyArray<Any>()
        }
        if (kingdom.questGeneratorSettings == null) {
            kingdom.questGeneratorSettings = js("""({
                defaultVisibilityToPlayers: false,
                maxActiveGeneratedQuests: 10,
                autoAdvanceQuestTimersOnTurn: true
            })""")
        }
    }
}

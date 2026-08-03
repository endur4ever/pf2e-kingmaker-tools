package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Adds quest/event generator fields to KingdomData:
 * - questTemplates, campaignQuests, kingdomEventTemplates,
 *   campaignKingdomEvents, eventGenerationLogs, questGeneratorSettings
 *
 * These fields are additive and default to empty arrays / default settings.
 * Existing kingdom data is not modified.
 */
class Migration30 : Migration(30) {

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

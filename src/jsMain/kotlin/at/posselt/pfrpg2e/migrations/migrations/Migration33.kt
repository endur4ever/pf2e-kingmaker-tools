package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 33 (roadmap #7) — companion relationship fields + personal quests.
 *
 * Adds influence/campAvailable/discoveryStatus/personalQuestIds defaults to each companion and
 * seeds an empty companionPersonalQuests array on the kingdom. All additive with safe defaults.
 */
class Migration33 : Migration(33) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.companionPersonalQuests == null) {
            kingdom.companionPersonalQuests = emptyArray<dynamic>()
        }
        val companions = kingdom.companions
        if (companions != null) {
            for (i in 0 until (companions.length as Int)) {
                val companion = companions[i]
                if (companion.influence == null) companion.influence = 0
                if (companion.campAvailable == null) companion.campAvailable = true
                if (companion.discoveryStatus == null) companion.discoveryStatus = "unknown"
                if (companion.personalQuestIds == null) companion.personalQuestIds = emptyArray<String>()
            }
        }
    }
}

package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 40 — companion expedition fields.
 *
 * Adds level/xp/expeditionStatus defaults to each companion and seeds an empty
 * companionExpeditions array on the kingdom. injuryDaysRemaining defaults to null,
 * i.e. an absent key, which every consumer already treats as null — so it needs no
 * explicit backfill. All changes are additive with safe defaults.
 */
class Migration40 : Migration(40) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.companionExpeditions == null) {
            kingdom.companionExpeditions = emptyArray<dynamic>()
        }
        val companions = kingdom.companions
        if (companions != null) {
            for (i in 0 until (companions.length as Int)) {
                val companion = companions[i]
                if (companion.level == null) companion.level = 1
                if (companion.xp == null) companion.xp = 0
                if (companion.expeditionStatus == null) companion.expeditionStatus = "available"
            }
        }
    }
}

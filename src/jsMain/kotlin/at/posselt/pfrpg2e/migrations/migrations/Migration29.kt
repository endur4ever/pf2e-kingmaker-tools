package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Adds the `campaignClocks` array to KingdomData if it is missing.
 * Ensures backward compatibility with older migrations where this field did not exist.
 */
class Migration29 : Migration(29) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.campaignClocks == null) {
            kingdom.campaignClocks = emptyArray<Any>()
        }
    }
}

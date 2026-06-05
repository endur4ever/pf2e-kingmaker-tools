package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Ensures [hexContents] is initialized as an empty array if missing or null.
 */
class Migration27 : Migration(27) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.hexContents == null) {
            kingdom.hexContents = emptyArray<dynamic>()
        }
    }
}

package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

class Migration38 : Migration(38) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.shipments == null) {
            kingdom.shipments = emptyArray<dynamic>()
        }
    }
}

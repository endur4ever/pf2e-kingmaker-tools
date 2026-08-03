package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 43 — banked bonuses for Request Foreign Aid.
 *
 * Initializes the `bankedBonuses` array on existing kingdoms. Nullable array with safe default (empty array).
 */
class Migration43 : Migration(43) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.bankedBonuses == null) {
            kingdom.bankedBonuses = emptyArray<dynamic>()
        }
    }
}
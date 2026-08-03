package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 34 — backfill missing structureBlacklist.
 *
 * Kingdoms created before structureBlacklist existed (and never re-defaulted) have it undefined,
 * which crashes the Inspect Settlement and Structure Browser dialogs when they call .toSet().
 * Additive with a safe empty-array default.
 */
class Migration34 : Migration(34) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.structureBlacklist == null) {
            kingdom.structureBlacklist = emptyArray<String>()
        }
    }
}

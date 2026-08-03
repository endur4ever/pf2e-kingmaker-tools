package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 35 — initialize partyInfluence for the Party influence panel.
 *
 * Kingdoms created before the panel existed have partyInfluence undefined. Party membership itself
 * is read live from the party actor; this only seeds the persisted influence store with an empty
 * array. Additive with a safe default — no existing data is transformed.
 */
class Migration35 : Migration(35) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.partyInfluence == null) {
            kingdom.partyInfluence = emptyArray<Any>()
        }
    }
}

package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 42 — quest structure access grants.
 *
 * Initializes the `accessGrants` array on existing kingdoms. This field stores
 * quest-granted structure access benefits (trainers, crafting access, item level
 * increases) that are unioned with structure-derived access in InspectSettlement.
 * Nullable array with safe default (empty array).
 */
class Migration42 : Migration(42) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.accessGrants == null) {
            kingdom.accessGrants = emptyArray<dynamic>()
        }
    }
}
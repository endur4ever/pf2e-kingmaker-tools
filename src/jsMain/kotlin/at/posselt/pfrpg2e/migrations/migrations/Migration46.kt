package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 46 — per-threat player visibility flag.
 *
 * Initializes the `visibleToPlayers` field on existing war threats. Defaults to true
 * for backwards compatibility (all existing threats remain visible to players).
 * The field is nullable for migration safety; RawWarThreat.visibleToPlayers is also nullable.
 */
class Migration46 : Migration(46) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        val threats = kingdom.warThreats ?: return
        for (threat in threats) {
            if (threat.visibleToPlayers == null) {
                threat.visibleToPlayers = true
            }
        }
    }
}
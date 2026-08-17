package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 54 — Quality of Life's once-per-turn luxury marker.
 *
 * Seeds `luxuryBonusUsedThisTurn` to false so the feat's first-gain-of-the-turn bonus is available
 * on the turn a world upgrades, rather than being suppressed by an undefined flag.
 */
class Migration54 : Migration(54) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.luxuryBonusUsedThisTurn == null) {
            kingdom.luxuryBonusUsedThisTurn = false
        }
    }
}

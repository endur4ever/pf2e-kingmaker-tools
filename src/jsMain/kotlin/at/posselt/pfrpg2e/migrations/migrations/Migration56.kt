package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 56 — Decadent Feasts unrest shield.
 *
 * Seeds `decadentFeastsShieldActive` to false. A shield is a within-turn effect, so starting
 * unarmed is correct: nothing is owed to a world upgrading mid-turn.
 */
class Migration56 : Migration(56) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.decadentFeastsShieldActive == null) {
            kingdom.decadentFeastsShieldActive = false
        }
    }
}

package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 55 — Irrigation critical-failure tracking.
 *
 * Seeds `critFailedIrrigationHexes` to 0. Existing worlds may well contain hexes that critically
 * failed Irrigation before this was tracked; those are invisible to the module and the GM can raise
 * the count by re-running the activity, which is preferable to guessing at history.
 */
class Migration55 : Migration(55) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.critFailedIrrigationHexes == null) {
            kingdom.critFailedIrrigationHexes = 0
        }
    }
}

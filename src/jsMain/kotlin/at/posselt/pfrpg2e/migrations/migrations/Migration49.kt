package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.camping.CampingData
import com.foundryvtt.core.Game

/**
 * Migration 49 — persist the route planner's "move the party token" choice.
 *
 * The control was a plain DOM checkbox, so every sheet re-render reset it. Backfills the new
 * flag to false (the card's specified default) for camping data that predates it.
 */
class Migration49 : Migration(49) {

    override suspend fun migrateCamping(game: Game, camping: CampingData) {
        if (camping.travelMoveToken.unsafeCast<Boolean?>() == null) {
            camping.travelMoveToken = false
        }
    }
}

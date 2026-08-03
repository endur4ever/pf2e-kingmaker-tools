package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 45 — add Pull Together, Liquidate Resources, and Envy of the World tracking fields.
 *
 * These nullable fields track per-turn usage for the newly-automated feats.
 * They default to null (treated as false/0) for back-compat.
 */
class Migration45 : Migration(45) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        // Initialize Pull Together tracking
        if (!js("Object.hasOwn")(kingdom, "pullTogetherUsedThisTurn")) {
            kingdom.pullTogetherUsedThisTurn = false
        }
        if (!js("Object.hasOwn")(kingdom, "pullTogetherCurrentDC")) {
            kingdom.pullTogetherCurrentDC = 11
        }
        if (!js("Object.hasOwn")(kingdom, "pullTogetherTurnsSinceLastUsed")) {
            kingdom.pullTogetherTurnsSinceLastUsed = 0
        }

        // Initialize Liquidate Resources tracking
        if (!js("Object.hasOwn")(kingdom, "liquidateResourcesPenaltyNextTurn")) {
            kingdom.liquidateResourcesPenaltyNextTurn = false
        }

        // Initialize Envy of the World tracking
        if (!js("Object.hasOwn")(kingdom, "envyOfTheWorldFirstIgnoreUsed")) {
            kingdom.envyOfTheWorldFirstIgnoreUsed = false
        }
    }
}
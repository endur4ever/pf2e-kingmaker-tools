package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 50 — siege damage: per-settlement list of ruined structures.
 *
 * Backfills destroyedStructureIds to an empty array on every settlement, so a kingdom that
 * predates sieges evaluates exactly as before (nothing ruined) rather than reading undefined.
 */
class Migration50 : Migration(50) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        val settlements = kingdom.settlements
        if (settlements != null) {
            for (i in 0 until (settlements.length as Int)) {
                val settlement = settlements[i]
                if (settlement.destroyedStructureIds == null) {
                    settlement.destroyedStructureIds = arrayOf<String>()
                }
            }
        }
    }
}

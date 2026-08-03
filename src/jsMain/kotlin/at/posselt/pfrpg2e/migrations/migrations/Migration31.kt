package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Adds an empty populationRoster to each settlement in kingdom data.
 * The roster contains an empty npcs array, establishing the field for
 * future population tracking.
 */
class Migration31 : Migration(31) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        val settlements = kingdom.settlements
        if (settlements != null) {
            for (i in 0 until settlements.length) {
                val settlement = settlements[i]
                if (settlement.populationRoster == null) {
                    settlement.populationRoster = js("""({ npcs: [] })""")
                }
            }
        }
    }
}

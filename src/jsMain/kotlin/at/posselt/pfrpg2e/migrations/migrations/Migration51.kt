package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 51 — per-army battle defence bonus.
 *
 * Backfills defenseBonus to null on every army in every recorded battle, so armies in battles that
 * predate garrison effects read a defined value instead of undefined and keep their existing AC.
 */
class Migration51 : Migration(51) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        val battles = kingdom.activeBattles ?: return
        for (i in 0 until (battles.length as Int)) {
            val battle = battles[i]
            for (side in listOf(battle.attackers, battle.defenders)) {
                if (side == null) continue
                for (j in 0 until (side.length as Int)) {
                    if (side[j].defenseBonus == null) side[j].defenseBonus = null
                }
            }
        }
    }
}

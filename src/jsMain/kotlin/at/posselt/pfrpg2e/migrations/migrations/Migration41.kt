package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 41 — Deploy Army outcome tracking fields.
 *
 * Adds checkResult (degree of success from the Deploy Army activity check)
 * and effectsApplied (whether mishap effects have been applied to the PF2EArmy actor)
 * to each RawArmyDeployment. Both are nullable with safe defaults.
 */
class Migration41 : Migration(41) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        val deployments = kingdom.armyDeployments
        if (deployments != null) {
            for (i in 0 until (deployments.length as Int)) {
                val deployment = deployments[i]
                if (deployment.checkResult == null) deployment.checkResult = null
                if (deployment.effectsApplied == null) deployment.effectsApplied = false
            }
        }
    }
}
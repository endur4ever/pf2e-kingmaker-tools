package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 48 — companion influence/discovery attempt session tracking.
 *
 * Adds lastInfluenceAttemptSessionId and lastDiscoveryAttemptSessionId fields to each companion.
 * Defaults to null (never attempted).
 */
class Migration48 : Migration(48) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        val companions = kingdom.companions
        if (companions != null) {
            for (i in 0 until (companions.length as Int)) {
                val companion = companions[i]
                if (companion.lastInfluenceAttemptSessionId == null) companion.lastInfluenceAttemptSessionId = null
                if (companion.lastDiscoveryAttemptSessionId == null) companion.lastDiscoveryAttemptSessionId = null
            }
        }
    }
}
package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawRewildTracker
import com.foundryvtt.core.Game

/**
 * Map dynamism backfill (`docs/plans/2026-07-09-plan-map-dynamism.md` SS2.3; the plan's
 * placeholder said Migration49 -- 64 is the next contiguous number at landing).
 *
 * Non-breaking by construction: threats stay static (wanders=false) and start "at target";
 * the re-wild side-table starts empty and the first tick reconciles it; migration stays OFF
 * until the GM flips the dial, and the re-wild delay default only ever produces OFFERS.
 */
class Migration64 : Migration(64) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        kingdom.warThreats?.forEach { threat ->
            if (threat.wanders == null) {
                threat.wanders = false
                threat.currentHexLocation = threat.targetHexLocation
                threat.migrationConsumedTurn = null
            }
        }
        if (kingdom.rewildTrackers == null) {
            kingdom.rewildTrackers = emptyArray<RawRewildTracker>()
        }
        val settings = kingdom.settings
        if (settings.threatMigrationEnabled == null) settings.threatMigrationEnabled = false
        if (settings.threatMigrationSpeed == null) settings.threatMigrationSpeed = 1
        if (settings.rewildDelayTurns == null) settings.rewildDelayTurns = 6
    }
}

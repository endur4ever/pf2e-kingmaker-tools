package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.camping.CampingData
import com.foundryvtt.core.Game

/**
 * Migration 47 — auto-succeed in claimed hexes setting.
 *
 * Initializes the `autoSucceedInClaimedHexes` field on existing camping data.
 * Defaults to false to preserve RAW behavior.
 */
class Migration47 : Migration(47) {
    override suspend fun migrateCamping(game: Game, camping: CampingData) {
        if (camping.autoSucceedInClaimedHexes.unsafeCast<Boolean?>() == null) {
            camping.autoSucceedInClaimedHexes = false
        }
    }
}
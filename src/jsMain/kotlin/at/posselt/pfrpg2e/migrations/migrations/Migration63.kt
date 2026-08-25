package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawScheduledPressure
import com.foundryvtt.core.Game

/**
 * Migration 63 — chapter deadline scheduler.
 *
 * Seeds `scheduledPressures` to an empty array, following the convention every nullable array on
 * KingdomData gets. Idempotent — re-running never erases schedules a GM has authored.
 */
class Migration63 : Migration(63) {

    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        if (kingdom.scheduledPressures.unsafeCast<Array<RawScheduledPressure>?>() == null) {
            kingdom.scheduledPressures = emptyArray()
        }
    }
}

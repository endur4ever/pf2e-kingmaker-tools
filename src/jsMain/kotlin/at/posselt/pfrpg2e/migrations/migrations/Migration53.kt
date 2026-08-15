package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.camping.CampingData
import com.foundryvtt.core.Game

/**
 * Migration 53 — weather effects toggle.
 *
 * Backfills `enableWeatherEffects` to true for existing camping data, matching the feature's
 * default-on stance (the deltas are deliberately small). The null guard keeps it idempotent and
 * preserves a GM who has explicitly turned it off, the same shape as Migration19's default-on
 * backfill of autoApplyFatigued.
 */
class Migration53 : Migration(53) {

    override suspend fun migrateCamping(game: Game, camping: CampingData) {
        if (camping.enableWeatherEffects.unsafeCast<Boolean?>() == null) {
            camping.enableWeatherEffects = true
        }
    }
}

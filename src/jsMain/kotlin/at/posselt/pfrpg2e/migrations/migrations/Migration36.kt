package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.gearsettings.GearSettingsProfileManager
import com.foundryvtt.core.Game
import kotlin.js.Date

/**
 * Migration 36 — seed the gear-settings profile registry from the world's
 * existing module settings.
 *
 * The gear-settings profile system introduces a registry while the legacy
 * individual Foundry settings remain the cache/read-through path. This migration
 * captures the current values once, so worlds keep their configured behavior
 * after profiles become active.
 */
class Migration36 : Migration(36) {
    override suspend fun migrateOther(game: Game) {
        GearSettingsProfileManager(game).seedRegistryFromCurrentSettings(Date().toISOString())
    }
}

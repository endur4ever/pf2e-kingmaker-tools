package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import com.foundryvtt.core.Game

/**
 * Influence & Research subsystem store (docs/plans/2026-07-09-plan-influence-research.md
 * section 2.5; the plan's number was a placeholder to re-derive at landing, and 71 is the
 * contiguous one).
 *
 * The store is a WORLD SETTING, not a kingdom flag, so this overrides migrateOther rather than
 * migrateKingdom. The registered default is already "{}" and the accessors treat "{}" as empty,
 * so there is nothing to backfill -- the migration exists to keep the schema version monotonic,
 * and defensively re-asserts the empty blob only when the setting somehow reads blank.
 */
class Migration71 : Migration(71) {
    override suspend fun migrateOther(game: Game) {
        val current = runCatching { game.settings.pfrpg2eKingdomCampingWeather.getSubsystemStore() }
            .getOrNull()
        if (current.isNullOrBlank()) {
            game.settings.pfrpg2eKingdomCampingWeather.setSubsystemStore("{}")
        }
    }
}

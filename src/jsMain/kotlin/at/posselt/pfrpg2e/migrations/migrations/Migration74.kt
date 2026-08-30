package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import com.foundryvtt.core.Game

/**
 * Encounter-stager pending manifests (encounter-stager plan 2.4; the plan's "Migration49" was
 * never free and re-derives to 74 here).
 *
 * Deliberately a NO-OP on data: `RawHexContent.encounterManifest` is nullable and every read goes
 * through [at.posselt.pfrpg2e.kingdom.data.encounterManifestOrNull], so an existing hex needs no
 * seed -- null already means "no creatures curated". Registered so the chain stays contiguous and
 * the version stamp advances, which is what MigrationChainTest guards.
 */
class Migration74 : Migration(74) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        // intentionally empty: see the KDoc
    }
}

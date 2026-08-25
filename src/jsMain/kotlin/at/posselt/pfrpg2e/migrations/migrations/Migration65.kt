package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawTreasureLedgerEntry
import com.foundryvtt.core.Game

/**
 * Loot-manifests backfill (`docs/plans/2026-07-09-plan-loot-manifests.md` SS2.6; the plan's
 * reservation said Migration67 -- 65 is the next contiguous number at landing).
 *
 * Only the ledger needs seeding so append is always safe. Hex manifests stay ABSENT (null = "no
 * treasure prepped"), and the award guard flags stay absent too -- the plan's sketch "seeded"
 * them with a null-to-null no-op; nullable reads make that write pointless, so it is omitted.
 */
class Migration65 : Migration(65) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        if (kingdom.treasureLedger == null) {
            kingdom.treasureLedger = emptyArray<RawTreasureLedgerEntry>()
        }
    }
}

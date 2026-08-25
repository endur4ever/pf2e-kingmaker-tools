package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawPcDowntimeProject
import com.foundryvtt.core.Game

/**
 * Migration 62 — PC downtime project ledger.
 *
 * Seeds `downtimeProjects` to an empty array, the same belt-and-braces every nullable array on
 * KingdomData gets (quests/23, expeditions/40, caravans/44, threats/46, shipmentHistory/61): the
 * reader treats absent as empty, but seeding keeps the flag shape consistent and inspectable.
 * Idempotent — re-running never erases projects already being written.
 */
class Migration62 : Migration(62) {

    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        if (kingdom.downtimeProjects.unsafeCast<Array<RawPcDowntimeProject>?>() == null) {
            kingdom.downtimeProjects = emptyArray()
        }
    }
}

package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.RawShipmentHistoryEntry
import com.foundryvtt.core.Game

/**
 * Migration 61 — caravan delivery history.
 *
 * Seeds `shipmentHistory` to an empty array. The reader treats absent as empty, so this is
 * belt-and-braces: it makes the field visible when inspecting the flag and keeps the shape
 * consistent with the other array-valued kingdom fields. Idempotent, so re-running never erases a
 * history already being written.
 */
class Migration61 : Migration(61) {

    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        if (kingdom.shipmentHistory.unsafeCast<Array<RawShipmentHistoryEntry>?>() == null) {
            kingdom.shipmentHistory = emptyArray()
        }
    }
}

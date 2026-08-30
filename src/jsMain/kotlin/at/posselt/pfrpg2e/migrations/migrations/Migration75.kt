package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.RawSettlementLifeEventRecord
import at.posselt.pfrpg2e.kingdom.structures.RawSettlement
import com.foundryvtt.core.Game

/**
 * Settlement life-event history backfill (settlement-life plan 2.4; the plan's placeholder
 * "Migration49" re-derives to 75).
 *
 * Seeds the array to empty only where absent: the tick APPENDS, and an append against undefined
 * throws, so the seed is what makes the first life event on an upgraded world safe.
 */
class Migration75 : Migration(75) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        val settlements = kingdom.settlements.unsafeCast<Array<RawSettlement>?>() ?: return
        settlements.forEach { settlement ->
            if (settlement.lifeEventHistory == null) {
                settlement.lifeEventHistory = emptyArray<RawSettlementLifeEventRecord>()
            }
        }
    }
}

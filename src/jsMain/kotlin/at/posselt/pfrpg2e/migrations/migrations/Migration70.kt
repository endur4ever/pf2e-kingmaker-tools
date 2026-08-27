package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawPersonalHolding
import com.foundryvtt.core.Game

/**
 * Personal Holdings backfill (docs/plans/2026-07-09-plan-personal-holdings.md section 2.4; the
 * plan's number was a placeholder to re-derive at landing, and 70 is the contiguous one).
 *
 * Seeds the array to empty ONLY when absent: the grant dialog appends, and an append against
 * `undefined` throws, so the seed is what makes the first grant on an upgraded world safe. An
 * existence check rather than an assignment is the whole idempotence contract -- a re-run keeps
 * every holding a GM granted in between.
 */
class Migration70 : Migration(70) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        if (kingdom.personalHoldings == null) {
            kingdom.personalHoldings = emptyArray<RawPersonalHolding>()
        }
    }
}

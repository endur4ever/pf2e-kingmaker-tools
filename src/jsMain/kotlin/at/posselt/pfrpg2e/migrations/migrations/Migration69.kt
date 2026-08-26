package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawPcRenown
import at.posselt.pfrpg2e.kingdom.data.RawRenownDeed
import at.posselt.pfrpg2e.kingdom.data.RawTurnContribution
import com.foundryvtt.core.Game

/**
 * Renown and Spotlight backfill (`docs/plans/2026-07-09-plan-renown-spotlight.md` SS2.6; the plan
 * left the number as a placeholder to be re-derived at landing, and 69 is the contiguous one).
 *
 * Seeds the three new top-level arrays to empty ONLY when they are absent. Each of them is
 * appended to at the seam, and an append against `undefined` throws, so seeding is what makes the
 * first credited deed on an upgraded world safe. Each guard is an existence check rather than an
 * assignment, which is the whole of the idempotence contract: a GM who upgrades, plays a turn, and
 * then somehow re-runs the chain keeps every deed and every epithet they earned in between.
 *
 * `RawTurnRecord.contributions` is deliberately NOT touched. Legacy turn records genuinely have no
 * per-actor tallies -- nobody was recording them -- and seeding an empty array would assert that
 * nobody contributed those turns rather than that nobody was counting. Left null, the Spotlight
 * shows nothing for old turns, which is the honest answer.
 */
class Migration69 : Migration(69) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        if (kingdom.renown == null) {
            kingdom.renown = emptyArray<RawPcRenown>()
        }
        if (kingdom.currentTurnContributions == null) {
            kingdom.currentTurnContributions = emptyArray<RawTurnContribution>()
        }
        if (kingdom.currentTurnDeeds == null) {
            kingdom.currentTurnDeeds = emptyArray<RawRenownDeed>()
        }
    }
}

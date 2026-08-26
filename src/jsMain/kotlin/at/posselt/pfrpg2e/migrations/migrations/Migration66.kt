package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawCouncilVote
import com.foundryvtt.core.Game

/**
 * Council-votes backfill (`docs/plans/2026-07-09-plan-council-votes.md` section 2.4).
 *
 * Seeds `councilVotes` to an empty array on kingdoms that predate the flag. A nullable array does
 * not strictly REQUIRE a backfill — `appendCouncilVote` treats null as empty — but seeding keeps
 * every read of the ledger uniform, and the un-registered Migrations 41 to 48 are the standing
 * lesson that the cheap, registered migration is the one that actually runs.
 *
 * Guarded by a null check rather than an unconditional write, so a second pass over an already
 * migrated kingdom is a no-op and can never erase recorded ballots. Nothing else is touched: an
 * individual vote's own fields are all nullable, so there is no per-row default to write.
 */
class Migration66 : Migration(66) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        if (kingdom.councilVotes == null) {
            kingdom.councilVotes = emptyArray<RawCouncilVote>()
        }
    }
}

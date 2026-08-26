package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawRivalRealm
import com.foundryvtt.core.Game

/**
 * Rival Realms scoreboard (`docs/plans/2026-07-09-plan-rival-realms.md` section 2.3; the plan's
 * placeholder said Migration49, which was never free -- 67 is the number assigned at landing).
 *
 * Seeds `rivalRealms` to an empty array, the convention every nullable array on KingdomData gets,
 * so the standings table can iterate without a null check on every read.
 *
 * NO BACKFILL: rivals are opt-in and the GM adds them from the dialog. Seeding Pitax and Brevoy
 * stubs from known group names would put realms nobody asked for onto every existing world's
 * scoreboard, and there is no safe way to tell a stub the GM wanted from one this migration
 * invented once it has been edited.
 *
 * Idempotent: an array that already exists is left exactly as it is, so a second run over a
 * migrated world cannot erase realms the GM has authored.
 */
class Migration67 : Migration(67) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        if (kingdom.rivalRealms == null) {
            kingdom.rivalRealms = emptyArray<RawRivalRealm>()
        }
    }
}

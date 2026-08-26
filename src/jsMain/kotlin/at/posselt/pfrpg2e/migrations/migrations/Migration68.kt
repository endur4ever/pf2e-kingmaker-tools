package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawRivalCharterParty
import com.foundryvtt.core.Game

/**
 * Rival Charter Party storage (`docs/plans/2026-07-09-plan-rival-charter-party.md` SS2.3; the
 * plan reserved Migration66 -- 68 is the number that was actually free at landing).
 *
 * A pure null-guard, and nothing more. Bands are opt-in: the GM adds one through the dialog, so
 * there is nothing to backfill, and migrations are effectively one-shot and irreversible (a single
 * auto-backup), which makes "seed the array, touch nothing else" the only safe shape here.
 *
 * Non-breaking either way: a kingdom whose flag is still absent shows an empty rival board rather
 * than failing to load, and a kingdom that already holds bands keeps every one of them -- the
 * guard writes only when the array is missing, so a second run is a no-op.
 */
class Migration68 : Migration(68) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        if (kingdom.rivalCharterParties == null) {
            kingdom.rivalCharterParties = emptyArray<RawRivalCharterParty>()
        }
    }
}

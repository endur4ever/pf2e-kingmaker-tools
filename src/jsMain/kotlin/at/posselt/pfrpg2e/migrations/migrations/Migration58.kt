package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.MilestoneChoice
import com.foundryvtt.core.Game

/**
 * Migration 58 — milestone offer-dismissed marker.
 *
 * Seeds `offerDismissed = false` on every existing MilestoneChoice so the auto-detected milestone
 * offers have a place to record a GM's refusal. Everyone starts un-dismissed, which is the right
 * history to invent: nobody has been asked yet.
 *
 * The null guard keeps it idempotent and preserves a dismissal already recorded.
 */
class Migration58 : Migration(58) {

    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        // Kingdom data old enough to predate the milestones array itself reaches this migration
        // with `milestones` undefined. A migration that throws aborts the WHOLE remaining chain,
        // so bail rather than dereference it -- there are no choices to seed anyway.
        val choices = kingdom.milestones.unsafeCast<Array<MilestoneChoice>?>() ?: return
        choices.forEach { choice ->
            if (choice.offerDismissed.unsafeCast<Boolean?>() == null) {
                choice.offerDismissed = false
            }
        }
    }
}

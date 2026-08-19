package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.camping.CampingData
import com.foundryvtt.core.Game
import js.objects.Record
import js.objects.recordOf

/**
 * Migration 57 — per-actor starvation counters.
 *
 * Seeds `daysWithoutFood` to an empty record on existing camping data so the nightly meal loop can
 * write per-camper entries without a null check on every path. Everyone starts fed: an empty
 * record reads as zero nights without food for every actor, which is the right history to invent
 * for a campaign that was not tracking hunger until now.
 *
 * The null guard keeps it idempotent, the same shape as Migration37's backfill of
 * learnedCompanionActivitiesByActor.
 */
class Migration57 : Migration(57) {

    override suspend fun migrateCamping(game: Game, camping: CampingData) {
        if (camping.daysWithoutFood.unsafeCast<Record<String, Int>?>() == null) {
            camping.daysWithoutFood = recordOf()
        }
    }
}

package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.camping.CampingData
import com.foundryvtt.core.Game

/**
 * Migration 59 — camping session counter.
 *
 * Seeds `campingSessionId` to 0 on existing camping data so the once-per-session companion cap has
 * a marker that only moves when a session actually completes.
 *
 * Existing companions carry a last-attempt id recorded against the OLD marker (a dailyPrepsAtTime
 * world-time stamp), which will never equal a counter value. That resolves in the forgiving
 * direction: each companion gets one attempt back on upgrade, rather than being locked out against
 * an identity that can no longer recur.
 */
class Migration59 : Migration(59) {

    override suspend fun migrateCamping(game: Game, camping: CampingData) {
        if (camping.campingSessionId.unsafeCast<Int?>() == null) {
            camping.campingSessionId = 0
        }
    }
}

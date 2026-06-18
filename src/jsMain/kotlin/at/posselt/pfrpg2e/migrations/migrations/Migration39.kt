package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import com.foundryvtt.core.Game

/**
 * Migration 39 — fix stale Leadership-activity caps.
 *
 * The `leadershipActivityCap` / `leadershipActivityCapWithTownhall` world settings used to be a flat
 * per-kingdom total (old defaults 6 / 8). They were later repurposed into a PER-PC-LEADER allotment
 * (new defaults 2 / 3) that is multiplied by the number of PC leaders — but worlds upgraded across
 * that change kept the old flat values, so the cap ballooned (e.g. 8 leaders x 8 = 64) and the sheet
 * showed "8 per player". Reset worlds still carrying the old flat defaults to the new per-leader
 * defaults. A value that was deliberately changed to something else is left alone.
 */
class Migration39 : Migration(39) {
    override suspend fun migrateOther(game: Game) {
        val settings = game.settings.pfrpg2eKingdomCampingWeather
        if (settings.getLeadershipActivityCap() == OLD_FLAT_CAP) {
            settings.setLeadershipActivityCap(NEW_PER_LEADER_CAP)
        }
        if (settings.getLeadershipActivityCapWithTownhall() == OLD_FLAT_CAP_WITH_TOWNHALL) {
            settings.setLeadershipActivityCapWithTownhall(NEW_PER_LEADER_CAP_WITH_TOWNHALL)
        }
    }

    private companion object {
        const val OLD_FLAT_CAP = 6
        const val OLD_FLAT_CAP_WITH_TOWNHALL = 8
        const val NEW_PER_LEADER_CAP = 2
        const val NEW_PER_LEADER_CAP_WITH_TOWNHALL = 3
    }
}

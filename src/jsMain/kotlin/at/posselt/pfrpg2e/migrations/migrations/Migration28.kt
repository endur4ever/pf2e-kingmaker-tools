package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Backfills the [learnedCompanionActivities] field that was added to the camping data model.
 *
 * Camping data saved before `learnedCompanionActivities` existed has it as `undefined`,
 * which would cause a crash when the camping sheet tries to read it.
 */
class Migration28 : Migration(28) {

    override suspend fun migrateCamping(game: Game, camping: dynamic) {
        if (camping.learnedCompanionActivities == null) {
            camping.learnedCompanionActivities = emptyArray<String>()
        }
    }
}

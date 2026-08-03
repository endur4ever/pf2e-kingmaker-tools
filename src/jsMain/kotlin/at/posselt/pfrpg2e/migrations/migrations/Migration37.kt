package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * Migration 37 — remove enhance-weapons from the global learned list
 * so players have to learn it, while ensuring Amiri still knows it individually.
 */
class Migration37 : Migration(37) {

    override suspend fun migrateCamping(game: Game, camping: dynamic) {
        if (camping.learnedCompanionActivities != null) {
            val learnedList = camping.learnedCompanionActivities.unsafeCast<Array<String>>()
            if ("enhance-weapons" in learnedList) {
                // Remove from global list
                camping.learnedCompanionActivities = learnedList.filter { it != "enhance-weapons" }.toTypedArray()

                // Find Amiri's actor UUID and add it to her learned list
                val amiriActor = game.actors.contents.find {
                    val name = it.asDynamic().name.unsafeCast<String>()
                    name.contains("Amiri", ignoreCase = true)
                }
                if (amiriActor != null) {
                    val amiriUuid = amiriActor.asDynamic().uuid.unsafeCast<String>()
                    val key = amiriUuid.replace('.', '_')
                    if (camping.learnedCompanionActivitiesByActor == null) {
                        camping.learnedCompanionActivitiesByActor = recordOf<String, Array<String>>()
                    }
                    val byActor = camping.learnedCompanionActivitiesByActor
                    val amiriLearned = (byActor[key]?.unsafeCast<Array<String>>() ?: emptyArray()).toSet()
                    if ("enhance-weapons" !in amiriLearned) {
                        byActor[key] = (amiriLearned + "enhance-weapons").toTypedArray()
                    }
                }
            }
        }
    }
}

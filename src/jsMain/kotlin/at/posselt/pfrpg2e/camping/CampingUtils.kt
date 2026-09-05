package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf
import com.foundryvtt.core.grid.GridHex
import com.foundryvtt.kingmaker.kingmaker
import com.pixijs.Point

/**
 * Gets the party's current hex key from the party token's position on the hex map.
 *
 * The scene is chosen by [hexSceneSource]: the configured hexploration scene
 * ([CampingData.worldSceneId]) when there is one, otherwise the active scene. Preferring the
 * configured map is what keeps hex rules working once the GM switches to a campsite or battle
 * map, which is exactly when camping activities are rolled.
 *
 * Returns null when no hex map is available or the party has no token on it.
 */
fun getPartyCurrentHexKey(game: Game, actor: CampingActor, camping: CampingData?): String? {
    val configured = camping?.worldSceneId?.let { game.scenes.get(it) }
    val active = game.scenes.active
    val scene = when (hexSceneSource(configured?.grid?.isHexagonal, active?.grid?.isHexagonal)) {
        HexSceneSource.CONFIGURED -> configured
        HexSceneSource.ACTIVE -> active
        HexSceneSource.NONE -> null
    } ?: return null
    val token = scene.tokens.contents.find { it.actorId == actor.id } ?: return null
    val grid = scene.grid
    val center = Point(
        x = token.x + grid.sizeX / 2.0,
        y = token.y + grid.sizeY / 2.0,
    )
    val offset = grid.getOffset(center)
    // Kingmaker hex keys are "i*1000 + j" as strings (see HexGridSync.kt)
    return (offset.i * 1000 + offset.j).toString()
}

/**
 * Checks whether the party's current hex is claimed by the kingdom.
 * Returns false if no active scene, not a hex grid, party token not found,
 * or no kingdom/hex state available.
 */
fun isPartyHexClaimed(game: Game, actor: CampingActor, camping: CampingData?): Boolean {
    val hexKey = getPartyCurrentHexKey(game, actor, camping) ?: return false

    // Get hex state from kingmaker.state.hexes
    val hexState = com.foundryvtt.kingmaker.kingmaker.state.hexes[hexKey]
    val hexClaimed = hexState?.claimed == true
    if (!hexClaimed) return false

    // Also verify kingdom exists (defensive)
    val kingdomActor = game.getKingdomActors().firstOrNull() ?: return false
    val kingdom = kingdomActor.getKingdom() ?: return false

    return true
}

data class ClearedCompanionData(
    val clearedActivities: List<String>,
    val clearedWatchSlotsCount: Int,
    val clearedMealChoice: Boolean,
    val removedMealKeys: List<String> = emptyList(),
)

fun clearDepartingCompanionFromCamp(
    camping: CampingData,
    companionUuid: String,
): ClearedCompanionData {
    val clearedActivities = mutableListOf<String>()
    var clearedWatchSlotsCount = 0
    var clearedMealChoice = false

    // 1. Clear activity assignments
    val activities = camping.campingActivities
    val keys = js("Object.keys(activities)").unsafeCast<Array<String>>()
    keys.forEach { activityId ->
        val activity = activities[activityId]
        if (activity != null && activity.actorUuid == companionUuid) {
            activity.actorUuid = null
            clearedActivities.add(activityId)
        }
    }

    // 2. Clear watch slot membership
    val updatedWatchSlots = camping.watchSlots.map { slot ->
        val filtered = slot.filter { it != companionUuid }
        if (filtered.size < slot.size) {
            clearedWatchSlotsCount += (slot.size - filtered.size)
        }
        filtered.toTypedArray()
    }.toTypedArray()
    camping.watchSlots = updatedWatchSlots

    // 3. Clear meal choices. actorMeals is keyed by actor.id (UUIDs contain dots and would be
    //    mangled by Foundry's flag flattening — see CampingData.downtimeHoursKey), so match on the
    //    entry VALUE's actorUuid, never on the record key.
    val meals = camping.cooking.actorMeals
    val mealKeys = js("Object.keys(meals)").unsafeCast<Array<String>>()
    val removedMealKeys = mutableListOf<String>()
    mealKeys.forEach { mealKey ->
        if (meals[mealKey]?.actorUuid == companionUuid) {
            js("delete meals[mealKey]")
            removedMealKeys.add(mealKey)
            clearedMealChoice = true
        }
    }

    return ClearedCompanionData(
        clearedActivities = clearedActivities,
        clearedWatchSlotsCount = clearedWatchSlotsCount,
        clearedMealChoice = clearedMealChoice,
        // the caller persists with setCamping, which writes a Foundry FLAG -- and a flag write
        // MERGES. Deleting the key in memory therefore never removed it from storage; the caller
        // has to issue a real delete for each one.
        removedMealKeys = removedMealKeys.toList(),
    )
}

/**
 * Departure housekeeping for companions leaving on an expedition: unassign each departing
 * companion from camping activities, watch slots, and meal choices on the camping actor, persist
 * once, and post ONE GM-whispered note listing what was cleared. Assignments are deliberately NOT
 * auto-restored when the expedition returns — the GM re-assigns replacements.
 *
 * @param departing (actorUuid, displayName) pairs for the companions leaving camp.
 */
suspend fun clearDepartingCompanionsFromCamp(game: Game, departing: List<Pair<String, String>>) {
    if (departing.isEmpty()) return
    val campingActor = game.getCampingActors().firstOrNull() ?: return
    val camping = campingActor.getCamping() ?: return
    val clearedMealKeys = mutableListOf<String>()
    val clearedByName = departing.mapNotNull { (uuid, name) ->
        val cleared = clearDepartingCompanionFromCamp(camping, uuid)
        clearedMealKeys.addAll(cleared.removedMealKeys)
        val parts = buildList {
            if (cleared.clearedActivities.isNotEmpty()) {
                add(t("camping.departureClearedActivities", recordOf("count" to cleared.clearedActivities.size)))
            }
            if (cleared.clearedWatchSlotsCount > 0) {
                add(t("camping.departureClearedWatches", recordOf("count" to cleared.clearedWatchSlotsCount)))
            }
            if (cleared.clearedMealChoice) {
                add(t("camping.departureClearedMeal"))
            }
        }
        if (parts.isEmpty()) null else "$name: ${parts.joinToString(", ")}"
    }
    if (clearedByName.isEmpty()) return
    campingActor.setCamping(camping)
    // setCamping merges, so the keys deleted in memory above are still in storage. Issue the
    // real deletions -- the update builder emits Foundry's "-=" delete marker per key.
    val removedMealKeys = clearedMealKeys.distinct()
    if (removedMealKeys.isNotEmpty()) {
        campingActor.typedCampingUpdate {
            removedMealKeys.forEach { cooking.actorMeals.deleteEntry(it) }
        }
    }
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isNotEmpty()) {
        postChatMessage(
            t("camping.expeditionDepartureCleared", recordOf("details" to clearedByName.joinToString("; "))),
            whisper = gmUserIds,
        )
    }
}
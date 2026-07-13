package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import com.foundryvtt.core.Game
import com.foundryvtt.core.grid.GridHex
import com.foundryvtt.kingmaker.kingmaker
import com.pixijs.Point

/**
 * Gets the party's current hex key from the token position on the active hex-grid scene.
 * Returns null if no active scene, not a hex grid, or party token not found.
 */
fun getPartyCurrentHexKey(game: Game, actor: CampingActor): String? {
    val scene = game.scenes.active
    if (scene == null || !scene.grid.isHexagonal) return null
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
fun isPartyHexClaimed(game: Game, actor: CampingActor): Boolean {
    val hexKey = getPartyCurrentHexKey(game, actor) ?: return false

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

    // 3. Clear meal choices
    if (camping.cooking.actorMeals[companionUuid] != null) {
        js("delete camping.cooking.actorMeals[companionUuid]")
        clearedMealChoice = true
    }

    return ClearedCompanionData(
        clearedActivities = clearedActivities,
        clearedWatchSlotsCount = clearedWatchSlotsCount,
        clearedMealChoice = clearedMealChoice,
    )
}
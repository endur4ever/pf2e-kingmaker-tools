package at.posselt.pfrpg2e.actions.handlers

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.camping.CampingActivityWithId
import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.camping.CampingData
import at.posselt.pfrpg2e.camping.getAllActivities
import at.posselt.pfrpg2e.camping.getAllRecipes
import at.posselt.pfrpg2e.camping.getActorsInCamp
import at.posselt.pfrpg2e.camping.learnFromACompanionId
import at.posselt.pfrpg2e.camping.setCamping
import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.camping.removeMealEffects
import at.posselt.pfrpg2e.camping.syncCampingEffects
import at.posselt.pfrpg2e.camping.typedCampingUpdate
import at.posselt.pfrpg2e.camping.updateCampingPosition
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.Game
import js.objects.recordOf
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface SyncActivitiesAction {
    val activities: Array<CampingActivityWithId>
    val rollRandomEncounter: Boolean
    val clearMealEffects: Boolean
    val prepareCampsiteResult: String?
    val campingActorUuid: String
}

@JsPlainObject
external interface RandomEncounterContext {
    val campingActorUuid: String
}

class SyncActivitiesHandler(
    private val game: Game,
) : ActionHandler("syncActivities", originatorPolicy = OriginatorPolicy.ANY) {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<SyncActivitiesAction>()
        val campingActor = fromUuidTypeSafe<CampingActor>(data.campingActorUuid)
        val camping = campingActor?.getCamping()
        if (camping != null) {
            data.prepareCampsiteResult
                ?.let { fromCamelCase<DegreeOfSuccess>(it) }
                ?.let { result ->
                    if (result != DegreeOfSuccess.CRITICAL_FAILURE) {
                        camping.worldSceneId?.let {
                            updateCampingPosition(game, it, result, campingActor)
                        }
                    }
                }
            if (data.clearMealEffects) {
                removeMealEffects(
                    recipes = camping.getAllRecipes().toList(),
                    actors = camping.getActorsInCamp(),
                    onlyRemoveAfterRest = false,
                    removeWhenPreparingCampsite = true,
                )
            }
            camping.syncCampingEffects(data.activities)
            val learnedChanged = handleLearnFromCompanion(camping, data.activities)
            if (learnedChanged) {
                // A TARGETED write. `camping` was read at handler entry, and removeMealEffects and
                // syncCampingEffects above each await a run of embedded-document writes -- so
                // saving the whole snapshot back here reverted anything any client changed on the
                // camping flag during that window. Only the two learned fields are written.
                campingActor.typedCampingUpdate {
                    learnedCompanionActivitiesByActor.set(camping.learnedCompanionActivitiesByActor)
                }
            }
            if (data.rollRandomEncounter) {
                postChatTemplate(
                    "chatmessages/random-camping-encounter.hbs",
                    templateContext = RandomEncounterContext(
                        campingActorUuid = campingActor.uuid,
                    ),
                    rollMode = RollMode.BLINDROLL
                )
            }
        }
    }
}

/**
 * When the "Learn from a Companion" activity succeeds or critically succeeds, add the single
 * companion activity the player picked in the activity's dropdown to the actor's learned list,
 * so it stays available to that character even when that companion is absent from camp.
 * Learning is per-character (KCG RAW): it is recorded in [learnedCompanionActivitiesByActor]
 * for the acting PC only, not in the party-wide list.
 */
internal fun handleLearnFromCompanion(
    camping: CampingData,
    activities: Array<CampingActivityWithId>,
): Boolean {
    val learnResult = activities.find { it.activityId == learnFromACompanionId }
    val degree = learnResult?.result?.let { fromCamelCase<DegreeOfSuccess>(it) }
    if (degree != DegreeOfSuccess.SUCCESS && degree != DegreeOfSuccess.CRITICAL_SUCCESS) {
        return false
    }
    val targetId = learnResult.learnTargetActivityId?.takeIf { it.isNotEmpty() } ?: return false
    val isCompanionActivity = camping.getAllActivities()
        .any { it.id == targetId && it.requiredCompanion != null }
    if (!isCompanionActivity) return false
    val actorUuid = learnResult.actorUuid ?: return false
    val key = actorUuid.replace('.', '_')
    val byActor = camping.learnedCompanionActivitiesByActor ?: recordOf()
    val actorLearned = byActor[key]?.toSet() ?: emptySet()

    var changed = false
    if (targetId !in actorLearned) {
        byActor[key] = (actorLearned + targetId).toTypedArray()
        camping.learnedCompanionActivitiesByActor = byActor
        changed = true
    }
    return changed
}

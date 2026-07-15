package at.posselt.pfrpg2e.resting

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.actions.handlers.GainProvisions
import at.posselt.pfrpg2e.camping.ActivityEffect
import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.camping.CampingData
import at.posselt.pfrpg2e.camping.resetDowntimeHours
import at.posselt.pfrpg2e.camping.MealEffect
import at.posselt.pfrpg2e.camping.RecipeData
import at.posselt.pfrpg2e.camping.RestSettings
import at.posselt.pfrpg2e.camping.applyRestHealEffects
import at.posselt.pfrpg2e.camping.askDc
import at.posselt.pfrpg2e.kingdom.CompanionAutonomy
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.kingdom.computeAutonomousProposal
import at.posselt.pfrpg2e.macros.chooseParty
import at.posselt.pfrpg2e.expedition.getExpeditionActivityName
import at.posselt.pfrpg2e.settings.Pfrpg2eKingdomCampingWeatherSettings
import at.posselt.pfrpg2e.camping.calculateDailyPreparationSeconds
import at.posselt.pfrpg2e.camping.calculateRestDurationSeconds
import at.posselt.pfrpg2e.camping.campingActivitiesDoublingHealing
import at.posselt.pfrpg2e.camping.dialogs.RestRollMode
import at.posselt.pfrpg2e.camping.dialogs.play
import at.posselt.pfrpg2e.camping.getActorsInCamp
import at.posselt.pfrpg2e.camping.getAllActivities
import at.posselt.pfrpg2e.camping.getAllRecipes
import at.posselt.pfrpg2e.camping.groupActivities
import at.posselt.pfrpg2e.camping.doesNotRequireACheck
import at.posselt.pfrpg2e.camping.repetitionsOrDefault
import at.posselt.pfrpg2e.camping.getAppliedCampingEffects
import at.posselt.pfrpg2e.camping.getAppliedMealEffects
import at.posselt.pfrpg2e.camping.getCampingActorsByUuid
import at.posselt.pfrpg2e.camping.getMealEffectItems
import at.posselt.pfrpg2e.camping.mealEffectsChangingRestDuration
import at.posselt.pfrpg2e.camping.mealEffectsDoublingHealing
import at.posselt.pfrpg2e.camping.mealEffectsHalvingHealing
import at.posselt.pfrpg2e.camping.performCampingCheck
import at.posselt.pfrpg2e.camping.removeCombatEffects
import at.posselt.pfrpg2e.camping.removeMealEffects
import at.posselt.pfrpg2e.camping.removeProvisions
import at.posselt.pfrpg2e.camping.rollRandomEncounter
import at.posselt.pfrpg2e.camping.setCamping
import at.posselt.pfrpg2e.camping.findCurrentRegion
import at.posselt.pfrpg2e.camping.EncounterResolverEngine
import at.posselt.pfrpg2e.camping.dialogs.showEncounterResolutionDialog
import at.posselt.pfrpg2e.actor.resolveAttribute
import at.posselt.pfrpg2e.data.actor.Perception
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.fromOrdinal
import at.posselt.pfrpg2e.utils.awaitAll
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.formatSeconds
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.typeSafeUpdate
import at.posselt.pfrpg2e.utils.worldTimeSeconds
import at.posselt.pfrpg2e.kingdom.logToCalendar
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import com.foundryvtt.pf2e.actions.CheckDC
import com.foundryvtt.pf2e.actions.RestForTheNightOptions
import com.foundryvtt.pf2e.actor.PF2EActor
import com.foundryvtt.pf2e.actor.PF2ECreature
import com.foundryvtt.pf2e.actor.PF2ECharacter
import com.foundryvtt.pf2e.actor.PF2EParty
import com.foundryvtt.pf2e.actor.StatisticRollParameters
import com.foundryvtt.pf2e.pf2e
import js.objects.Object
import js.objects.recordOf
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

const val SIXTEEN_HOURS_SECONDS = 16 * 60 * 60
const val EIGHT_HOURS_SECONDS = 8 * 60 * 60
const val DAY_SECONDS = 24 * 60 * 60
private const val FOUR_HOURS_SECONDS = 4 * 3600

private suspend fun getRestSecondsPerPlayer(
    players: List<PF2EActor>,
    recipes: List<RecipeData>,
    increaseActorsKeepingWatch: Int = 0,
): List<Int> {
    val mealEffects = mealEffectsChangingRestDuration(recipes)
    val durationPerPlayer = players.asSequence()
        .map {
            buildPromise {
                EIGHT_HOURS_SECONDS + it.getAppliedMealEffects(mealEffects)
                    .mapNotNull(MealEffect::changeRestDurationSeconds)
                    .sum()
            }
        }
        .toList()
        .awaitAll()
    val additionalWatchers = generateSequence { EIGHT_HOURS_SECONDS }
        .take(increaseActorsKeepingWatch)
        .toList()
    return durationPerPlayer + additionalWatchers
}

private suspend fun getFullRestSeconds(
    watchers: List<PF2EActor>,
    recipes: List<RecipeData>,
    gunsToClean: Int,
    increaseActorsKeepingWatch: Int,
    skipWatch: Boolean,
    skipDailyPreparations: Boolean,
): Int {
    val dailyPrepSeconds = if (skipDailyPreparations) 0 else calculateDailyPreparationSeconds(gunsToClean)
    val restSeconds = calculateRestDurationSeconds(
        getRestSecondsPerPlayer(watchers, recipes, increaseActorsKeepingWatch),
        skipWatch = skipWatch
    )
    return restSeconds + dailyPrepSeconds
}

data class RestDuration(
    val value: Int,
) {
    val label: String
        get() = formatSeconds(value)
}


data class TotalRestDuration(
    val total: RestDuration,
    val left: RestDuration?,
)

suspend fun getTotalRestDuration(
    watchers: List<PF2EActor>,
    recipes: List<RecipeData>,
    gunsToClean: Int,
    skipWatch: Boolean,
    skipDailyPreparations: Boolean,
    increaseActorsKeepingWatch: Int = 0,
    remainingSeconds: Int? = null,
): TotalRestDuration {
    val total = getFullRestSeconds(
        watchers = watchers,
        recipes = recipes,
        gunsToClean = gunsToClean,
        increaseActorsKeepingWatch = increaseActorsKeepingWatch,
        skipWatch = skipWatch,
        skipDailyPreparations = skipDailyPreparations,
    )
    return TotalRestDuration(
        total = RestDuration(total),
        left = remainingSeconds
            ?.takeIf { it > 0 }
            ?.let(::RestDuration)
    )
}

private enum class HealMultiplier {
    HALVE,
    DOUBLE,
}

private fun PF2ECharacter.additionalHealingAfterRest(multiplier: HealMultiplier?): Int {
    return if (multiplier != null) {
        val healed = max(
            1,
            system.abilities.con.mod
        ) * system.details.level.value * hitPoints.recoveryMultiplier + hitPoints.recoveryAddend
        if (multiplier == HealMultiplier.DOUBLE) {
            healed
        } else {
            val healedWithHalf = (healed / 2) + system.attributes.hp.value
            if (healedWithHalf >= system.attributes.hp.max) {
                0
            } else {
                -(healed / 2)
            }
        }
    } else {
        0
    }
}

private suspend fun findHealMultiplier(
    actor: PF2ECharacter,
    recipesDoublingHealing: List<MealEffect>,
    recipesHalvingHealing: List<MealEffect>,
    activitiesDoublingHealing: List<ActivityEffect>,
): HealMultiplier? {
    val doubles = actor.getAppliedMealEffects(recipesDoublingHealing).isNotEmpty()
            || actor.getAppliedCampingEffects(activitiesDoublingHealing).isNotEmpty()
    val halves = actor.getAppliedMealEffects(recipesHalvingHealing).isNotEmpty()
    return if (doubles && halves) {
        null
    } else if (doubles) {
        HealMultiplier.DOUBLE
    } else if (halves) {
        HealMultiplier.HALVE
    } else {
        null
    }
}

private suspend fun additionalHealingPerActorAfterRest(
    recipes: List<RecipeData>,
    camping: CampingData,
    actors: List<PF2EActor>
): List<Pair<PF2ECharacter, Int>> = coroutineScope {
    val recipesDoublingHealing = mealEffectsDoublingHealing(recipes)
    val recipesHalvingHealing = mealEffectsHalvingHealing(recipes)
    val activitiesDoublingHealing = campingActivitiesDoublingHealing(camping.getAllActivities().toList())
    actors
        .filterIsInstance<PF2ECharacter>()
        .map { actor ->
            async {
                val multiplier = findHealMultiplier(
                    actor,
                    recipesDoublingHealing = recipesDoublingHealing,
                    recipesHalvingHealing = recipesHalvingHealing,
                    activitiesDoublingHealing = activitiesDoublingHealing
                )
                actor to actor.additionalHealingAfterRest(multiplier)
            }
        }
        .awaitAll()
}

private suspend fun applyAdditionalHealing(healingAfterRest: List<Pair<PF2ECharacter, Int>>) = coroutineScope {
    healingAfterRest.map { (actor, healing) ->
        val value = min(actor.hitPoints.value + healing, actor.hitPoints.max)
        async {
            if (healing > 0) {
                postChatMessage(t("camping.healingMoreHp", recordOf("hp" to healing)), speaker = actor)
            } else if (healing < 0) {
                postChatMessage(t("camping.healingFewerHp", recordOf("hp" to abs(healing))), speaker = actor)
            }
            actor.typeSafeUpdate { system.attributes.hp.value = value }
        }
    }.awaitAll()
}

private suspend fun findRandomEncounterAt(
    game: Game,
    campingActor: CampingActor,
    camping: CampingData,
    watchDurationSeconds: Int,
): Int? {
    val randomEncounterChecksAtSeconds = when (fromCamelCase<RestRollMode>(camping.restRollMode)) {
        RestRollMode.ONE -> List(1) { Random.nextInt(1, watchDurationSeconds - 1) }
        RestRollMode.ONE_EVERY_FOUR_HOURS -> List(watchDurationSeconds / (FOUR_HOURS_SECONDS)) { index ->
            val begin = index * FOUR_HOURS_SECONDS
            val end = index * FOUR_HOURS_SECONDS + FOUR_HOURS_SECONDS
            Random.nextInt(begin + 1, end - 1)
        }

        else -> emptyList()
    }
    for (checksAtSecond in randomEncounterChecksAtSeconds) {
        if (rollRandomEncounter(game = game, actor = campingActor, includeFlatCheck = true)) {
            return checksAtSecond
        }
    }
    return null
}

private suspend fun beginRest(
    game: Game,
    dispatcher: ActionDispatcher,
    campingActor: CampingActor,
    camping: CampingData,
    party: PF2EParty?,
) {
    val actorsByUuid = getCampingActorsByUuid(camping.actorUuids).associateBy(PF2EActor::uuid)
    val watchers = actorsByUuid.values
        .filter { !camping.actorUuidsNotKeepingWatch.contains(it.uuid) }
    val watchDurationSeconds = getFullRestSeconds(
        watchers = watchers,
        recipes = camping.getAllRecipes().toList(),
        gunsToClean = camping.gunsToClean,
        increaseActorsKeepingWatch = camping.increaseWatchActorNumber,
        skipWatch = camping.restSettings.skipWatch,
        skipDailyPreparations = camping.restSettings.skipDailyPreparations,
    )
    val randomEncounterAt = findRandomEncounterAt(
        game = game,
        campingActor = campingActor,
        camping = camping,
        watchDurationSeconds = watchDurationSeconds,
    )
    if (camping.restSettings.disableRandomEncounter == false && randomEncounterAt != null) {
        val campCharacters = actorsByUuid.values.filterIsInstance<PF2ECharacter>()
        val characterWatchers = watchers.filterIsInstance<PF2ECharacter>()
        val numSlots = max(1, camping.watchSlots.size)
        val slotDuration = watchDurationSeconds / numSlots
        val slotIndex = (randomEncounterAt / max(1, slotDuration)).coerceIn(0, numSlots - 1)
        val slotActorUuids = camping.watchSlots.getOrNull(slotIndex) ?: emptyArray()

        // Everyone assigned to this watch slot rolls Perception. When no watch slots
        // are configured, fall back to the whole watch rotation.
        val onWatch = slotActorUuids
            .mapNotNull { actorsByUuid[it] }
            .filterIsInstance<PF2ECharacter>()
            .ifEmpty { characterWatchers }
        val defaultDc = camping.findCurrentRegion()?.encounterDc ?: 15

        val formData = showEncounterResolutionDialog(
            watchers = onWatch,
            defaultDc = defaultDc
        ) ?: return

        if (onWatch.isNotEmpty()) {
            val totalDc = formData.dc - formData.rollModifier + formData.dcModifier
            val rollParameters = StatisticRollParameters(
                rollMode = "blindroll",
                dc = CheckDC(value = totalDc),
                extraRollOptions = arrayOf("camping", "watch")
            )

            // Each watcher on duty rolls Perception against the ambusher's Stealth DC.
            val watcherResults = onWatch.map { watcher ->
                val checkRoll = watcher.unsafeCast<PF2ECreature>().resolveAttribute(Perception)
                    ?.roll(rollParameters)
                    ?.await()
                val rollTotal = checkRoll?.total ?: 0
                val degree = checkRoll?.degreeOfSuccess
                    ?.let { fromOrdinal<DegreeOfSuccess>(it) }
                    ?: DegreeOfSuccess.FAILURE
                Triple(watcher.name, rollTotal, degree)
            }

            // The party is alerted by the best detection among the watchers.
            val best = watcherResults.maxByOrNull { it.third }
            val degree = best?.third ?: DegreeOfSuccess.FAILURE
            val bestRollTotal = best?.second ?: 0

            val resolution = EncounterResolverEngine.resolve(
                watcherRoll = bestRollTotal,
                stealthDc = totalDc,
                degree = degree
            )

            // Everyone in camp who is not awake on this watch is exposed in their sleep.
            val onWatchUuids = onWatch.map { it.uuid }.toSet()
            val sleepingActors = campCharacters.filter { it.uuid !in onWatchUuids }
            sleepingActors.forEach { actor ->
                resolution.appliedConditions.forEach { condition ->
                    actor.increaseCondition(condition)
                }
            }

            val rollMode = fromCamelCase<RollMode>(camping.randomEncounterRollMode) ?: RollMode.GMROLL
            postChatTemplate(
                templatePath = "chatmessages/encounter-resolution-card.hbs",
                templateContext = recordOf(
                    "watcherRolls" to watcherResults.map {
                        recordOf(
                            "name" to it.first,
                            "rollTotal" to it.second,
                            "degree" to t(it.third),
                        )
                    }.toTypedArray(),
                    "stealthDc" to totalDc,
                    "degree" to t(degree),
                    "distance" to resolution.distanceToEnemy,
                    "appliedConditions" to resolution.appliedConditions.joinToString(", ") { t("camping.conditions.$it") },
                    "ambusherState" to resolution.ambusherState,
                    "gmNotes" to resolution.gmNotes
                ),
                rollMode = rollMode
            )
        }

        // Persist the partial watch state first, then advance the clock detached — a misconfigured
        // Seasons & Stars calendar that hangs the advance must not strand the watch mid-rest.
        camping.watchSecondsRemaining = watchDurationSeconds - randomEncounterAt
        campingActor.setCamping(camping)
        game.time.advance(randomEncounterAt)
            .catch { console.error("[km] camping watch: failed to advance world time", it) }
    } else {
        camping.watchSecondsRemaining = watchDurationSeconds
        completeDailyPreparations(game, dispatcher, campingActor, camping, party)
    }
}

private suspend fun completeDailyPreparations(
    game: Game,
    dispatcher: ActionDispatcher,
    campingActor: CampingActor,
    camping: CampingData,
    party: PF2EParty?,
) = coroutineScope {
    val actors = camping.getActorsInCamp()
    val recipes = camping.getAllRecipes().toList()
    // Build the rest summary BEFORE clearing the activity results below.
    val activitiesSummary = camping.groupActivities()
        .filter { it.result.actorUuid != null }
        .map { activity ->
            val actorName = activity.result.actorUuid?.let { uuid ->
                actors.find { it.uuid == uuid }?.name ?: uuid
            } ?: "Unknown"
            val activityName = t(activity.data.name)
            val outcome = activity.result.result?.let { res ->
                val degree = fromCamelCase<DegreeOfSuccess>(res)
                if (degree != null) t(degree) else res
            } ?: if (activity.data.doesNotRequireACheck()) {
                val reps = activity.result.repetitionsOrDefault()
                "Performed (repetitions: $reps)"
            } else {
                "Assigned"
            }
            "- $activityName ($actorName): $outcome"
        }
        .joinToString("\n")

    val summaryContent = buildString {
        append("Camping session completed.\n\n")
        if (activitiesSummary.isNotEmpty()) {
            append("Activities:\n")
            append(activitiesSummary)
            append("\n\n")
        }
        append("Daily preparations completed. Healing applied.")
    }

    // Finalize and PERSIST all camp state BEFORE any third-party calendar call. Advancing the world
    // clock goes through Seasons & Stars, which can throw OR hang when the active calendar is
    // misconfigured ("Calendar not found"). If a hanging clock call were reached first, every step
    // after it would be skipped: the rest button stays on "Continue", per-actor downtime hours never
    // reset to their full budget, and assigned activities stay stuck on their cards. So reset and
    // save everything here first, then run the hang-prone calendar calls last and detached.
    val secondsToAdvance = camping.watchSecondsRemaining
    camping.watchSecondsRemaining = 0
    camping.encounterModifier = 0
    camping.secondsSpentTraveling = 0
    camping.secondsSpentHexploring = 0
    camping.dailyPrepsAtTime = game.time.worldTimeSeconds + secondsToAdvance
    Object.values(camping.campingActivities).forEach { it.result = null }
    Object.values(camping.cooking.results).forEach { it.result = null }
    camping.resetDowntimeHours()
    campingActor.setCamping(camping)

    val additionalHealing = additionalHealingPerActorAfterRest(recipes, camping, actors)
    runCatching {
        game.pf2e.actions.restForTheNight(RestForTheNightOptions(actors = actors.toTypedArray(), skipDialog = true))
            .await()
    }.onFailure { console.error("[km] camping rest: restForTheNight failed", it) }
    applyAdditionalHealing(additionalHealing)
    applyRestHealEffects(actors, recipes, getMealEffectItems(
        recipes = recipes,
        onlyRemoveAfterRest = false,
        removeWhenPreparingCampsite = false,
    ))
    removeMealEffects(recipes, actors, onlyRemoveAfterRest = true, removeWhenPreparingCampsite = false)
    removeProvisions(actors + listOfNotNull(party))
    removeCombatEffects(actors)
    gainMinimumSubsistence(dispatcher, camping.cooking.minimumSubsistence, party)

    // Hang-prone calendar side effects run last and detached (fire-and-forget) so a misconfigured
    // Seasons & Stars calendar can never abort or block the camp reset + healing above.
    buildPromise { logToCalendar(title = "Camp Rest Completed", content = summaryContent) }
    game.time.advance(secondsToAdvance)
        .catch { console.error("[km] camping rest: failed to advance world time", it) }

    // Companion autonomy: if enabled, post an offer card with volunteering companions
    if (Pfrpg2eKingdomCampingWeatherSettings.getCompanionAutonomyEnabled()) {
        buildPromise {
            val kingdomActors = game.getKingdomActors()
            if (kingdomActors.size > 1) {
                console.warn("[km] Companion autonomy: multiple kingdom actors exist (${kingdomActors.size}). Using the configured party actor (\"The Party\" convention).")
            }
            val kingdomActor = try {
                chooseParty(game)
            } catch (e: Exception) {
                kingdomActors.firstOrNull() ?: return@buildPromise
            }
            val kingdom = kingdomActor.getKingdom() ?: return@buildPromise
            val allCompanions = kingdom.companions ?: return@buildPromise
            val volunteers = CompanionAutonomy.selectAutonomousCompanions(allCompanions.toList())
            if (volunteers.isNotEmpty()) {
                val topPick = volunteers.first()
                val proposal = computeAutonomousProposal(topPick, kingdom.companionPersonalQuests ?: emptyArray())
                // Resolve localized activity name for the proposal pitch
                val activityName = getExpeditionActivityName(proposal.activityId)
                val volunteerData = volunteers.map { companion ->
                    val discoveryRank = when (companion.discoveryStatus) {
                        "established", "trusted", "bonded" -> true
                        else -> false
                    }
                    js.objects.recordOf(
                        "name" to companion.name,
                        "influence" to companion.influence,
                        "hasPersonalQuest" to companion.personalQuestIds.isNotEmpty(),
                        "discoveryEstablished" to discoveryRank,
                    )
                }.toTypedArray()
                postChatTemplate(
                    templatePath = "chatmessages/companion-autonomy-offer.hbs",
                    templateContext = js.objects.recordOf(
                        "volunteers" to volunteerData,
                        "actorUuid" to kingdomActor.uuid,
                        "isGM" to true,
                        "proposal" to js.objects.recordOf(
                            "volunteerKey" to (topPick.actorUuid ?: topPick.name),
                            "name" to topPick.name,
                            "activity" to activityName,
                            "activityId" to proposal.activityId,
                            "targetQuestId" to (proposal.targetQuestId ?: ""),
                            "tier" to proposal.tier,
                            "totalDays" to proposal.totalDays,
                            "rpCost" to proposal.rpCost,
                        ),
                    )
                )
            } else {
                postChatMessage(t("chatMessages.companionAutonomy.noEligible"))
            }
        }
    }
}

private suspend fun gainMinimumSubsistence(
    dispatcher: ActionDispatcher,
    quantity: Int,
    party: PF2EParty?,
) {
    if (party != null) {
        dispatcher.dispatch(
            ActionMessage(
                action = "gainProvisions",
                data = GainProvisions(
                    actorUuid = party.uuid,
                    quantity = quantity,
                ).unsafeCast<AnyObject>()
            )
        )
    }
}


suspend fun rest(
    game: Game,
    dispatcher: ActionDispatcher,
    campingActor: CampingActor,
    camping: CampingData,
    skipWatch: Boolean,
    skipDailyPreparations: Boolean,
    disableRandomEncounter: Boolean,
    skipWeather: Boolean,
    party: PF2EParty?,
) {
    camping.restSettings = RestSettings(
        skipWatch = skipWatch,
        skipDailyPreparations = skipDailyPreparations,
        disableRandomEncounter = disableRandomEncounter,
        skipWeather = skipWeather,
    )
    campingActor.setCamping(camping)
    if (camping.watchSecondsRemaining == 0) {
        camping.restingTrack?.play()
        beginRest(game, dispatcher, campingActor, camping, party)
    } else {
        completeDailyPreparations(game, dispatcher, campingActor, camping, party)
    }
}
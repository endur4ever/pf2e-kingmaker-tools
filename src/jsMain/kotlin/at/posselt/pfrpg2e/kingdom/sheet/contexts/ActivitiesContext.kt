package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.data.kingdom.KingdomPhase
import at.posselt.pfrpg2e.data.kingdom.KingdomSkillRanks
import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.RawActivity
import at.posselt.pfrpg2e.kingdom.availableSkills
import at.posselt.pfrpg2e.kingdom.canBePerformed
import at.posselt.pfrpg2e.kingdom.data.ChosenFeat
import at.posselt.pfrpg2e.kingdom.data.ChosenFeature
import at.posselt.pfrpg2e.kingdom.dialogs.getValidActivitySkills
import at.posselt.pfrpg2e.kingdom.increasedSkills
import at.posselt.pfrpg2e.kingdom.label
import at.posselt.pfrpg2e.kingdom.parse
import at.posselt.pfrpg2e.kingdom.skillRanks
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.ActivityCap
import at.posselt.pfrpg2e.kingdom.ActivityCapCalculator
import at.posselt.pfrpg2e.kingdom.getPerformedActivities
import at.posselt.pfrpg2e.kingdom.sumPerformedByPhase
import at.posselt.pfrpg2e.kingdom.activityAllowedDuringAnarchy
import at.posselt.pfrpg2e.kingdom.isInAnarchy
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import com.foundryvtt.core.applications.ux.TextEditor.enrichHtml
import com.foundryvtt.core.game
import js.array.toTypedArray
import js.objects.Object
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface ActivitySkillContext {
    val label: String
    val usable: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface ActivityContext {
    val label: String
    val disabled: Boolean
    val disabledReason: String?
    val description: String
    val actions: Array<Int>
    val id: String
    val special: String?
    val automationNotes: String?
    val requirement: String?
    val fortune: Boolean
    val criticalSuccess: String?
    val success: String?
    val failure: String?
    val criticalFailure: String?
    val isCollectTaxes: Boolean
    val order: Int?
    val open: Boolean
    val hasCheck: Boolean
    val skills: Array<ActivitySkillContext>
    val performedCount: Int
    val performed: Boolean
    val performedBadge: String?
}

@Suppress("unused")
@JsPlainObject
external interface ActivitiesContext {
    val commerce: Array<ActivityContext>
    val leadership: Array<ActivityContext>
    val region: Array<ActivityContext>
    val civic: Array<ActivityContext>
    val army: Array<ActivityContext>
    val upkeep: Array<ActivityContext>
    val leadershipPerformed: Int
    val leadershipCap: Int
    val leadershipRemaining: Int
    val civicPerformed: Int
    val civicCap: Int
    val civicRemaining: Int
    val regionPerformed: Int
    val regionCap: Int
    val regionRemaining: Int
    val armyPerformed: Int
    val armyCap: Int
    val armyRemaining: Int
    val commercePerformed: Int
    val commerceCap: Int
    val commerceRemaining: Int
}

private suspend fun toActivityContext(
    activity: RawActivity,
    kingdomLevel: Int,
    allowCapitalInvestment: Boolean,
    kingdomSkillRanks: KingdomSkillRanks,
    kingdom: KingdomData,
    chosenFeats: List<ChosenFeat>,
    chosenFeatures: List<ChosenFeature>,
    openedDetails: Set<String>,
    activeLeader: Leader?,
    anarchyAt: Int,
    currentUnrest: Int,
    performedCount: Int,
): ActivityContext = coroutineScope {
    val descriptionP = async { enrichHtml(activity.description) }
    val criticalSuccessP = async { activity.criticalSuccess?.msg?.let { enrichHtml(it) } }
    val successP = async { activity.success?.msg?.let { enrichHtml(it) } }
    val failureP = async { activity.failure?.msg?.let { enrichHtml(it) } }
    val criticalFailureP = async { activity.criticalFailure?.msg?.let { enrichHtml(it) } }
    val description = descriptionP.await()
    val criticalSuccess = criticalSuccessP.await()
    val success = successP.await()
    val failure = failureP.await()
    val criticalFailure = criticalFailureP.await()
    val availableSkills = activity.availableSkills(kingdom.settings.expandMagicUse)
    val validSkills = getValidActivitySkills(
        ranks = kingdomSkillRanks,
        activityRanks = activity.skillRanks(),
        ignoreSkillRequirements = kingdom.settings.kingdomIgnoreSkillRequirements,
        expandMagicUse = kingdom.settings.expandMagicUse,
        activityId = activity.id,
        increaseSkills = chosenFeats.map { it.feat.increasedSkills() }
    )
    val skills = availableSkills.asSequence()
        .map {
            ActivitySkillContext(
                label = t(it),
                usable = it in validSkills,
            )
        }
        .sortedBy { it.label }
        .toTypedArray()
    val actions = if (kingdom.settings.enableLeadershipModifiers &&
        (activity.actions == 1 || activity.actions == null) &&
        activeLeader != null
    ) {
        val activeLeaderSkills = kingdom.settings.leaderKingdomSkills.parse().resolveAttributes(activeLeader)
        if (validSkills.isEmpty() || validSkills.all { it in activeLeaderSkills }) {
            arrayOf(1)
        } else if (validSkills.any { it in activeLeaderSkills }) {
            arrayOf(1, 2)
        } else {
            arrayOf(2)
        }
    } else {
        arrayOf(activity.actions ?: 1)
    }
    val baseDisabled = !activity.canBePerformed(
        allowCapitalInvestment = allowCapitalInvestment,
        kingdomSkillRanks = kingdomSkillRanks,
        kingdom = kingdom,
        chosenFeats = chosenFeats,
    )
    val inAnarchy = isInAnarchy(currentUnrest, anarchyAt)
    val anarchyGated = kingdom.settings.enableAnarchyActivityGating == true
        && inAnarchy
        && !activityAllowedDuringAnarchy(activity.id)
    val disabled = baseDisabled || anarchyGated
    val disabledReason = if (anarchyGated) {
        t("kingdom.activityDisabledDuringAnarchy")
    } else {
        null
    }
    ActivityContext(
        id = activity.id,
        label = activity.label(
            kingdomLevel = kingdomLevel,
            activity = activity,
            chosenFeatures = chosenFeatures,
        ),
        actions = actions,
        description = description,
        special = activity.special,
        automationNotes = activity.automationNotes,
        disabled = disabled,
        disabledReason = disabledReason,
        fortune = activity.fortune,
        requirement = activity.requirement,
        criticalSuccess = criticalSuccess,
        success = success,
        failure = failure,
        criticalFailure = criticalFailure,
        isCollectTaxes = activity.id == "collect-taxes",
        order = activity.order,
        open = ("activity-" + activity.id) in openedDetails,
        hasCheck = Object.keys(activity.skills).isNotEmpty(),
        skills = skills,
        performedCount = performedCount,
        performed = performedCount > 0,
        performedBadge = if (performedCount > 1) "×$performedCount" else null,
    )
}

suspend fun activitiesToActivityContext(
    activities: List<RawActivity>,
    allowCapitalInvestment: Boolean,
    kingdomSkillRanks: KingdomSkillRanks,
    chosenFeatures: List<ChosenFeature>,
    openedDetails: Set<String>,
    kingdom: KingdomData,
    chosenFeats: List<ChosenFeat>,
    activeLeader: Leader?,
    anarchyAt: Int,
    currentUnrest: Int,
    performedByActivityId: Map<String, Int>,
) = coroutineScope {
    activities
        .map {
            async {
                toActivityContext(
                    activity = it,
                    kingdomLevel = kingdom.level,
                    allowCapitalInvestment = allowCapitalInvestment,
                    kingdomSkillRanks = kingdomSkillRanks,
                    kingdom = kingdom,
                    chosenFeats = chosenFeats,
                    chosenFeatures = chosenFeatures,
                    openedDetails = openedDetails,
                    activeLeader = activeLeader,
                    anarchyAt = anarchyAt,
                    currentUnrest = currentUnrest,
                    performedCount = performedByActivityId[it.id] ?: 0,
                )
            }
        }
        .awaitAll()
        .sortedWith(compareBy<ActivityContext> { it.order ?: Int.MAX_VALUE }.thenBy { it.label })
        .toTypedArray()
}

suspend fun toActivitiesContext(
    actor: KingdomActor,
    activities: List<RawActivity>,
    activityBlacklist: Set<String>,
    unlockedActivities: Set<String>,
    kingdom: KingdomData,
    chosenFeats: List<ChosenFeat>,
    allowCapitalInvestment: Boolean,
    kingdomSkillRanks: KingdomSkillRanks,
    chosenFeatures: List<ChosenFeature>,
    openedDetails: Set<String>,
    activeLeader: Leader?,
    anarchyAt: Int,
    currentUnrest: Int,
): ActivitiesContext = coroutineScope {
    val activitiesByPhase = activities
        .asSequence()
        .filter { it.id !in activityBlacklist || it.id in unlockedActivities }
        .groupBy { it.phase }
    val performedByActivityId = actor.getPerformedActivities()
    val phaseByActivityId = activities.associate { it.id to it.phase }
    val commerce = activitiesToActivityContext(
        activitiesByPhase[KingdomPhase.COMMERCE.value].orEmpty(),
        allowCapitalInvestment,
        kingdomSkillRanks,
        chosenFeatures,
        openedDetails,
        kingdom,
        chosenFeats,
        activeLeader,
        anarchyAt,
        currentUnrest,
        performedByActivityId,
    )
    val leadership = activitiesToActivityContext(
        activitiesByPhase[KingdomPhase.LEADERSHIP.value].orEmpty(),
        allowCapitalInvestment,
        kingdomSkillRanks,
        chosenFeatures,
        openedDetails,
        kingdom,
        chosenFeats,
        activeLeader,
        anarchyAt,
        currentUnrest,
        performedByActivityId,
    )
    val civic = activitiesToActivityContext(
        activitiesByPhase[KingdomPhase.CIVIC.value].orEmpty(),
        allowCapitalInvestment,
        kingdomSkillRanks,
        chosenFeatures,
        openedDetails,
        kingdom,
        chosenFeats,
        activeLeader,
        anarchyAt,
        currentUnrest,
        performedByActivityId,
    )
    val region = activitiesToActivityContext(
        activitiesByPhase[KingdomPhase.REGION.value].orEmpty(),
        allowCapitalInvestment,
        kingdomSkillRanks,
        chosenFeatures,
        openedDetails,
        kingdom,
        chosenFeats,
        activeLeader,
        anarchyAt,
        currentUnrest,
        performedByActivityId,
    )
    val army = activitiesToActivityContext(
        activitiesByPhase[KingdomPhase.ARMY.value].orEmpty(),
        allowCapitalInvestment,
        kingdomSkillRanks,
        chosenFeatures,
        openedDetails,
        kingdom,
        chosenFeats,
        activeLeader,
        anarchyAt,
        currentUnrest,
        performedByActivityId,
    )
    val upkeep = activitiesToActivityContext(
        activitiesByPhase[KingdomPhase.UPKEEP.value].orEmpty(),
        allowCapitalInvestment,
        kingdomSkillRanks,
        chosenFeatures,
        openedDetails,
        kingdom,
        chosenFeats,
        activeLeader,
        anarchyAt,
        currentUnrest,
        performedByActivityId,
    )

    val phasePerformed = sumPerformedByPhase(performedByActivityId, phaseByActivityId)
    val leadershipSettings = game.settings.pfrpg2eKingdomCampingWeather
    val capsResult = ActivityCapCalculator.calculate(
        kingdom,
        phasePerformed,
        leadershipCap = leadershipSettings.getLeadershipActivityCap(),
        leadershipCapWithTownhall = leadershipSettings.getLeadershipActivityCapWithTownhall(),
    )
    val leadershipCap = capsResult.caps.find { it.phase == "leadership" }
    val civicCap = capsResult.caps.find { it.phase == "civic" }
    val regionCap = capsResult.caps.find { it.phase == "region" }
    val armyCap = capsResult.caps.find { it.phase == "army" }
    val commerceCap = capsResult.caps.find { it.phase == "commerce" }

    fun ActivityCap?.remaining(default: Int) =
        ((this?.maximum ?: default) - (this?.current ?: 0)).coerceAtLeast(0)

    ActivitiesContext(
        upkeep = upkeep,
        commerce = commerce,
        leadership = leadership,
        region = region,
        civic = civic,
        army = army,
        leadershipPerformed = leadershipCap?.current ?: 0,
        leadershipCap = leadershipCap?.maximum ?: 0,
        leadershipRemaining = leadershipCap.remaining(0),
        civicPerformed = civicCap?.current ?: 0,
        civicCap = civicCap?.maximum ?: 0,
        civicRemaining = civicCap.remaining(0),
        regionPerformed = regionCap?.current ?: 0,
        regionCap = regionCap?.maximum ?: 0,
        regionRemaining = regionCap.remaining(0),
        armyPerformed = armyCap?.current ?: 0,
        armyCap = armyCap?.maximum ?: 0,
        armyRemaining = armyCap.remaining(0),
        commercePerformed = commerceCap?.current ?: 0,
        commerceCap = commerceCap?.maximum ?: 1,
        commerceRemaining = commerceCap.remaining(1),
    )
}
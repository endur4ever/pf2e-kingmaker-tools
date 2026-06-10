package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.camping.dialogs.RegionSetting
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.questevent.CampaignQuest
import at.posselt.pfrpg2e.questevent.QuestRewards
import at.posselt.pfrpg2e.questevent.QuestStatus
import at.posselt.pfrpg2e.questevent.QuestType
import kotlin.js.Date
import kotlin.random.Random
import at.posselt.pfrpg2e.utils.d20Check
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.getPF2EWorldTime
import at.posselt.pfrpg2e.utils.isDay
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.rollWithDraw
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.documents.RollTable
import com.foundryvtt.core.ui
import js.objects.recordOf

suspend fun rollRandomEncounter(
    game: Game,
    actor: CampingActor,
    includeFlatCheck: Boolean
): Boolean {
    actor.getCamping()?.let { camping ->
        // Roadmap #11: when a category proxy table is configured, route through the
        // curated category flow (GM preview) instead of the legacy "Creature vs nothing" roll.
        if (camping.encounterCategoryProxyTableUuid != null) {
            return rollCuratedEncounter(game, actor)
        }
        val currentRegion = camping.findCurrentRegion() ?: camping.regionSettings.regions.firstOrNull()
        currentRegion?.let { region ->
            val partyLevel = game.getAveragePartyLevel()
            return rollRandomEncounter(
                camping = camping,
                includeFlatCheck = includeFlatCheck,
                region = region,
                isDay = game.getPF2EWorldTime().time.isDay(),
                partyLevel = partyLevel,
            )
        }
    }
    return false
}

/**
 * Roadmap #11: roll a curated random encounter. A category proxy table routes to
 * a per-category roll table; the result is drawn WITHOUT posting to chat and the
 * GM previews it (see [EncounterPreviewDialog]) before accepting. On accept the
 * result is posted; reroll re-runs this flow; reject discards it.
 */
suspend fun rollCuratedEncounter(game: Game, actor: CampingActor): Boolean {
    val camping = actor.getCamping() ?: return false
    val region = camping.findCurrentRegion() ?: camping.regionSettings.regions.firstOrNull() ?: return false
    val rollMode = fromCamelCase<RollMode>(camping.randomEncounterRollMode) ?: RollMode.GMROLL

    // Roadmap #11: the Encounter Curator weight sliders drive category selection.
    // The category proxy table is only consulted as a fallback when every weight
    // is zero, so a pure table-driven setup still works.
    val weights = camping.categoryWeightsOrDefault()
    val category = if (weights.total > 0) {
        weights.pickCategory(Random.nextDouble())
    } else {
        val proxyTable = camping.encounterCategoryProxyTableUuid?.let { fromUuidTypeSafe<RollTable>(it) }
        val categoryName = proxyTable
            ?.rollWithDraw(rollMode = rollMode, displayChat = false)
            ?.draw?.results?.get(0)?.text?.trim()
        categoryName?.let { EncounterCategory.fromString(it) } ?: run {
            if (!categoryName.isNullOrBlank()) {
                console.warn("Encounter curator: proxy result '$categoryName' matched no category; defaulting to COMBAT")
            }
            EncounterCategory.COMBAT
        }
    }

    val categoryTableUuid = region.categoryRollTableUuidMap()[category.value] ?: region.rollTableUuid
    val categoryTable = categoryTableUuid?.let { fromUuidTypeSafe<RollTable>(it) }
    if (categoryTable == null) {
        ui.notifications.error(t("camping.encounterTableNotFound", recordOf("regionName" to region.name)))
        return false
    }
    val resultText = categoryTable
        .rollWithDraw(rollMode = rollMode, displayChat = false)
        .draw.results.get(0)?.text?.trim()
        ?: ""

    // A rumor is always offered as a potential quest hook so the GM can convert it
    // from the preview dialog (roadmap #11 rumor->quest pipeline).
    val rumor = if (category == EncounterCategory.RUMOR && resultText.isNotBlank()) {
        Rumor(text = resultText, sourceRegion = region.name, isQuestHook = true)
    } else null

    EncounterPreviewDialog(
        category = category,
        regionName = region.name,
        resultText = resultText,
        rumor = rumor,
        onAccept = { buildPromise {
            postChatTemplate(
                "chatmessages/curated-rumor.hbs",
                recordOf(
                    "category" to category.value,
                    "iconClass" to category.iconClass,
                    "regionName" to region.name,
                    "resultText" to resultText,
                    "isRumor" to (category == EncounterCategory.RUMOR),
                ),
            )
        } },
        onReroll = { buildPromise { rollCuratedEncounter(game, actor) } },
        onReject = {},
        onConvertToQuest = { hook -> buildPromise { convertRumorToQuest(game, hook) } },
    ).render(true)
    return true
}

/**
 * Roadmap #11: convert a curated rumor with a quest hook into a simple
 * [CampaignQuest] record on the kingdom (the full quest generator, roadmap #2,
 * stays out of scope). The quest is flagged generatedByEvent so it shows the
 * existing "From: …" badge on the quests board, and its id is tracked in
 * [KingdomData.rumorGeneratedQuestIds].
 */
suspend fun convertRumorToQuest(game: Game, rumor: Rumor) {
    val kingdomActor = game.getKingdomActors().firstOrNull()
    if (kingdomActor == null) {
        ui.notifications.error(t("camping.encounterNoKingdom"))
        return
    }
    val kingdom = kingdomActor.getKingdom() ?: return
    val now = Date().toISOString()
    val questId = "rumor-${Date().getTime().toLong()}"
    val quest = CampaignQuest(
        id = questId,
        templateId = rumor.questTemplateId,
        name = rumor.questTemplateName ?: rumor.text.take(60),
        type = QuestType.EXPLORATION,
        description = rumor.text,
        gmNotes = null,
        recommendedLevel = kingdom.level ?: 1,
        objectives = emptyList(),
        rewards = QuestRewards(),
        status = QuestStatus.ACTIVE,
        turnsRemaining = null,
        visibleToPlayers = false,
        generatedByEvent = true,
        sourceEventId = null,
        sourceEventName = t("camping.encounterCuratorRumorSource"),
        createdAt = now,
        campaignId = "default",
    )
    val quests = (kingdom.campaignQuests ?: emptyArray<dynamic>()).toMutableList()
    quests.add(quest)
    kingdom.campaignQuests = quests.toTypedArray()
    kingdom.rumorGeneratedQuestIds = (kingdom.rumorGeneratedQuestIds ?: emptyArray()) + questId
    kingdomActor.setKingdom(kingdom)
    ui.notifications.info(t("camping.encounterRumorConverted"))
}

private suspend fun rollRandomEncounter(
    camping: CampingData,
    includeFlatCheck: Boolean,
    region: RegionSetting,
    isDay: Boolean,
    partyLevel: Int,
): Boolean {
    val table = region.rollTableUuid?.let { fromUuidTypeSafe<RollTable>(it) }
    if (table == null) {
        if (region.rollTableUuid != null) {
            ui.notifications.error(t("camping.encounterTableNotFound", recordOf("regionName" to region.name)))
        }
        return false
    }
    val rollMode = fromCamelCase<RollMode>(camping.randomEncounterRollMode) ?: RollMode.GMROLL
    val proxyTable = camping.proxyRandomEncounterTableUuid?.let { fromUuidTypeSafe<RollTable>(it) }
    val dc = findEncounterDcModifier(camping, isDay)
    val rollCheck = if (includeFlatCheck) {
        d20Check(
            dc = dc,
            flavor = t("camping.rollingRandomEncounter", recordOf("regionName" to region.name, "dc" to dc)),
            rollMode = rollMode,
        ).degreeOfSuccess.succeeded()
    } else {
        true
    }
    if (rollCheck) {
        val proxyResult = proxyTable?.rollWithDraw(rollMode = rollMode)
            ?.draw
            ?.results
            ?.get(0)
            ?.text
            ?.trim()
            ?: "Creature"
        if (proxyResult == "Creature") {
            table.rollWithDraw(rollMode = rollMode)
        }
        if (camping.hasPreparedCampsite()) {
            postCombatEffects(
                activeActivities = camping.alwaysPerformActivityIds.toSet() +
                        camping.campingActivitiesWithId()
                            .filter { it.actorUuid != null }
                            .map { it.activityId },
                partyLevel = partyLevel
            )
        }
        return true
    }
    return false
}

fun findEncounterDcModifier(
    camping: CampingData,
    isDay: Boolean
): Int = (camping.findCurrentRegion()?.encounterDc ?: 0) +
        calculateModifierIncrease(camping, isDay) +
        camping.encounterModifier

private fun calculateModifierIncrease(camping: CampingData, isDay: Boolean): Int =
    camping.groupActivities().asSequence()
        .filter { it.done() || camping.alwaysPerformActivityIds.contains(it.data.id) }
        .map { (data, activity) -> calculateModifierIncrease(data, isDay, activity.parseResult()) }
        .sum()


private fun calculateModifierIncrease(
    data: CampingActivityData,
    isDay: Boolean,
    checkResult: DegreeOfSuccess?
): Int {
    val activityMod = data.modifyRandomEncounterDc?.atTime(isDay) ?: 0
    val resultMod = checkResult?.let { result ->
        data.getOutcome(result)
            ?.modifyRandomEncounterDc
            ?.atTime(isDay)
    } ?: 0
    return activityMod + resultMod
}

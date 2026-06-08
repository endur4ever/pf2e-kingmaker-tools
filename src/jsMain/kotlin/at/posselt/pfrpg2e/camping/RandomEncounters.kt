package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.camping.dialogs.RegionSetting
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.utils.d20Check
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.getPF2EWorldTime
import at.posselt.pfrpg2e.utils.isDay
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.postChatMessage
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

    val proxyTable = camping.encounterCategoryProxyTableUuid?.let { fromUuidTypeSafe<RollTable>(it) }
    val categoryName = proxyTable
        ?.rollWithDraw(rollMode = rollMode, displayChat = false)
        ?.draw?.results?.get(0)?.text?.trim()
    val category = categoryName?.let { EncounterCategory.fromString(it) } ?: EncounterCategory.COMBAT

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

    val rumor = if (category == EncounterCategory.RUMOR && resultText.isNotBlank()) {
        Rumor(text = resultText, sourceRegion = region.name)
    } else null

    EncounterPreviewDialog(
        category = category,
        regionName = region.name,
        resultText = resultText,
        rumor = rumor,
        onAccept = { buildPromise { postChatMessage(resultText) } },
        onReroll = { buildPromise { rollCuratedEncounter(game, actor) } },
        onReject = {},
        onConvertToQuest = {}, // wired in phase 6
    ).render(true)
    return true
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

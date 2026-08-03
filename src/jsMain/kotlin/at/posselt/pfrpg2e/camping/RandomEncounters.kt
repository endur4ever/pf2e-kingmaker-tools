package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.app.confirm
import at.posselt.pfrpg2e.camping.dialogs.RegionSetting
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.homebrew.HomebrewProfileRegistry
import at.posselt.pfrpg2e.homebrew.RuleResolutionHelper
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.questevent.CampaignQuest
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.questevent.QuestRewards
import at.posselt.pfrpg2e.questevent.QuestStatus
import at.posselt.pfrpg2e.questevent.QuestType
import at.posselt.pfrpg2e.utils.d20Check
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.getPF2EWorldTime
import at.posselt.pfrpg2e.utils.isDay
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.rollWithDraw
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.documents.RollTable
import com.foundryvtt.core.ui
import js.objects.recordOf
import kotlinx.coroutines.coroutineScope
import com.foundryvtt.core.documents.TokenDocument
import com.foundryvtt.core.grid.GridOffset2D
import com.foundryvtt.kingmaker.kingmaker
import com.foundryvtt.pf2e.actor.PF2EParty
import com.pixijs.Point
import js.objects.ReadonlyRecord
import kotlin.random.Random

suspend fun rollRandomEncounter(
    game: Game,
    actor: CampingActor,
    includeFlatCheck: Boolean
): Boolean {
    actor.getCamping()?.let { camping ->
        if (camping.encounterCategoryProxyTableUuid != null) {
            return rollCuratedEncounter(game, actor)
        }
        val currentRegion = camping.findCurrentRegion() ?: camping.regionSettings.regions.firstOrNull()
        currentRegion?.let { region ->
            val partyLevel = game.getAveragePartyLevel()
            return rollRandomEncounter(
                game = game,
                actor = actor,
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
suspend fun rollCuratedEncounter(game: Game, actor: CampingActor, offerRestore: Boolean = true): Boolean {
    val camping = actor.getCamping() ?: return false
    val region = camping.findCurrentRegion() ?: camping.regionSettings.regions.firstOrNull() ?: return false
    val rollMode = fromCamelCase<RollMode>(camping.randomEncounterRollMode) ?: RollMode.GMROLL

    // An un-committed preview persisted before a browser reload can be restored instead of
    // forcing a reroll. Only offered on fresh entry — the preview dialog's own Reroll button
    // passes offerRestore = false so it never prompts against itself.
    if (offerRestore) {
        val persistedCategory = restorableEncounterPreview(camping.lastEncounterCategory, camping.lastEncounterResult)
        val persistedResult = camping.lastEncounterResult
        if (persistedCategory != null && !persistedResult.isNullOrBlank()) {
            val restore = confirm(
                t(
                    "camping.encounterRestorePrompt",
                    recordOf("category" to t("camping.encounterCategory.${persistedCategory.value}")),
                ),
            )
            if (restore) {
                showEncounterPreview(game, actor, camping, persistedCategory, region.name, persistedResult)
                return true
            }
        }
    }

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

    // Check hex-state filter before committing to the category table roll
    val filterDecision = checkEncounterHexFilter(game, actor, camping, region, category)
    if (filterDecision == EncounterFilterDecision.SUPPRESS_COMBAT) {
        // Reroll into a non-combat category
        return rollCuratedEncounterWithSuppressedCombat(game, actor, camping, region, rollMode, weights)
    }
    if (filterDecision == EncounterFilterDecision.SUPPRESS_ALL) {
        // Content override suppresses everything — whisper to GM and return false
        whisperEncounterSuppressed(game, actor, "encounter suppressed (hex content override)")
        return false
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

    showEncounterPreview(game, actor, camping, category, region.name, resultText)
    return true
}

/**
 * Persist the rolled (un-committed) preview to [CampingData.lastEncounterCategory]/
 * [CampingData.lastEncounterResult] and show the preview dialog. The persisted fields survive a
 * browser reload mid-preview (offered back by [rollCuratedEncounter]) and are cleared when the GM
 * commits (accept/convert) or discards (reject) the preview; a reroll simply overwrites them.
 */
private suspend fun showEncounterPreview(
    game: Game,
    actor: CampingActor,
    camping: CampingData,
    category: EncounterCategory,
    regionName: String,
    resultText: String,
) {
    camping.lastEncounterCategory = category.value
    camping.lastEncounterResult = resultText
    actor.setCamping(camping)

    // A rumor is always offered as a potential quest hook so the GM can convert it
    // from the preview dialog (roadmap #11 rumor->quest pipeline).
    val rumor = if (category == EncounterCategory.RUMOR && resultText.isNotBlank()) {
        Rumor(text = resultText, sourceRegion = regionName, isQuestHook = true)
    } else null

    EncounterPreviewDialog(
        category = category,
        regionName = regionName,
        resultText = resultText,
        rumor = rumor,
        onAccept = { buildPromise {
            postChatTemplate(
                "chatmessages/curated-rumor.hbs",
                recordOf(
                    "category" to category.value,
                    "iconClass" to category.iconClass,
                    "regionName" to regionName,
                    "resultText" to resultText,
                    "isRumor" to (category == EncounterCategory.RUMOR),
                ),
            )
            clearEncounterPreview(actor)
        } },
        onReroll = { buildPromise { rollCuratedEncounter(game, actor, offerRestore = false) } },
        onReject = { buildPromise { clearEncounterPreview(actor) } },
        onConvertToQuest = { hook -> buildPromise {
            convertRumorToQuest(game, hook)
            clearEncounterPreview(actor)
        } },
    ).render(true)
}

/**
 * The homebrew rules profile registry from world settings; null when unset or unparseable.
 * (Reads the registered `homebrewProfileRegistry` string setting — never a raw `js()` call: the
 * Kotlin `getObject` extension does not exist as a method on the runtime settings object.)
 */
private fun loadHomebrewRegistry(game: Game): HomebrewProfileRegistry? =
    runCatching { game.settings.pfrpg2eKingdomCampingWeather.getHomebrewProfileRegistry() }
        .getOrNull()
        ?.let { HomebrewProfileRegistry.fromJson(it) }

/** Clear the persisted preview once it is committed or discarded. */
private suspend fun clearEncounterPreview(actor: CampingActor) {
    actor.getCamping()?.let { camping ->
        if (camping.lastEncounterCategory != null || camping.lastEncounterResult != null) {
            camping.lastEncounterCategory = null
            camping.lastEncounterResult = null
            actor.setCamping(camping)
        }
    }
}

/**
 * Reroll a curated encounter when combat was suppressed by the hex-state filter.
 * Picks a new category from the weights (excluding COMBAT) and rolls its table.
 */
private suspend fun rollCuratedEncounterWithSuppressedCombat(
    game: Game,
    actor: CampingActor,
    camping: CampingData,
    region: RegionSetting,
    rollMode: RollMode,
    weights: CategoryWeights,
): Boolean {
    // Build weights excluding COMBAT
    val nonCombatWeights = CategoryWeights(
        combat = 0,
        rp = weights.rp,
        rumor = weights.rumor,
        merchant = weights.merchant,
        disease = weights.disease,
        faction = weights.faction,
        weather = weights.weather,
        lore = weights.lore,
    )
    if (nonCombatWeights.total <= 0) {
        whisperEncounterSuppressed(game, actor, "combat encounter suppressed (claimed+cleared hex) — no non-combat categories configured")
        return false
    }
    val newCategory = nonCombatWeights.pickCategory(Random.nextDouble())

    val categoryTableUuid = region.categoryRollTableUuidMap()[newCategory.value] ?: region.rollTableUuid
    val categoryTable = categoryTableUuid?.let { fromUuidTypeSafe<RollTable>(it) }
    if (categoryTable == null) {
        ui.notifications.error(t("camping.encounterTableNotFound", recordOf("regionName" to region.name)))
        return false
    }
    val resultText = categoryTable
        .rollWithDraw(rollMode = rollMode, displayChat = false)
        .draw.results.get(0)?.text?.trim()
        ?: ""

    showEncounterPreview(game, actor, camping, newCategory, region.name, resultText)
    return true
}

/**
 * Checks the hex-state filter for the party's current position.
 * Returns the filter decision for the given category.
 */
private fun checkEncounterHexFilter(
    game: Game,
    actor: CampingActor,
    camping: CampingData,
    region: RegionSetting,
    category: EncounterCategory,
): EncounterFilterDecision {
    // Get the party's current hex key from the token position
    val hexKey = getPartyCurrentHexKey(game, actor) ?: return EncounterFilterDecision.ALLOW

    // Get hex state from kingmaker.state.hexes
    val hexState = com.foundryvtt.kingmaker.kingmaker.state.hexes[hexKey]
    val hexClaimed = hexState?.claimed == true
    val hexCleared = hexState?.cleared == true

    // Get hex content suppressesEncounters from kingdom's hexContents
    val kingdomActor = game.getKingdomActors().firstOrNull() ?: return EncounterFilterDecision.ALLOW
    val kingdom = kingdomActor.getKingdom() ?: return EncounterFilterDecision.ALLOW
    val hexContent = kingdom.hexContents?.firstOrNull { it.hexKey == hexKey }
    val hexContentSuppresses = hexContent?.suppressesEncounters

    // Check homebrew setting overlap: noRandomCombatInClaimedHexes takes precedence
    val registry = loadHomebrewRegistry(game)
    val activeProfile = RuleResolutionHelper.getActiveProfile(registry)
    val homebrewSuppresses = activeProfile?.let { RuleResolutionHelper.isRandomCombatSuppressedInClaimedHexes(it) } ?: false
    if (homebrewSuppresses && hexClaimed && category == EncounterCategory.COMBAT) {
        return EncounterFilterDecision.SUPPRESS_COMBAT
    }

    // Camping-side filter
    val filterEnabled = camping.isFilterByHexState()
    return decideEncounterFilter(
        filterEnabled = filterEnabled,
        hexClaimed = hexClaimed,
        hexCleared = hexCleared,
        hexContentSuppresses = hexContentSuppresses,
        rolledCategory = category,
    )
}

/**
 * Whispers a GM-only notification that an encounter was suppressed.
 */
private suspend fun whisperEncounterSuppressed(game: Game, actor: CampingActor, reason: String) {
    val gmUserIds = game.users.contents
        .filter { it.isGM }
        .mapNotNull { it.id }
    if (gmUserIds.isNotEmpty()) {
        postChatMessage(
            t("camping.encounterSuppressed", recordOf("reason" to reason)),
            speaker = actor,
            whisper = gmUserIds.toTypedArray(),
        )
    }
}

private suspend fun rollRandomEncounter(
    game: Game,
    actor: CampingActor,
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
        // Party hex state, looked up once for both the per-hex override and the combat filter.
        val hexKey = getPartyCurrentHexKey(game, actor)
        val hexState = hexKey?.let { com.foundryvtt.kingmaker.kingmaker.state.hexes[it] }
        val hexClaimed = hexState?.claimed == true
        val hexCleared = hexState?.cleared == true
        val hexContent = hexKey?.let { key ->
            game.getKingdomActors().firstOrNull()?.getKingdom()?.hexContents?.firstOrNull { it.hexKey == key }
        }
        val hexContentSuppresses = hexContent?.suppressesEncounters

        // The per-hex "suppresses encounters" override applies to EVERY encounter type — check it
        // before any table is rolled. (Previously only the Creature branch consulted it, so a
        // non-creature proxy result still fired — with a phantom proxy-roll chat card — in a hex
        // the GM explicitly marked encounter-free.)
        if (hexContentSuppresses == true) {
            whisperEncounterSuppressed(game, actor, "encounter suppressed (hex content override)")
            return false
        }

        val proxyResult = proxyTable?.rollWithDraw(rollMode = rollMode)
            ?.draw
            ?.results
            ?.get(0)
            ?.text
            ?.trim()
            ?: "Creature"
        if (proxyResult == "Creature") {
            // Combat-only filters: homebrew claimed-hex rule (takes precedence), then the
            // camping claimed+cleared toggle via the shared decision function.
            if (hexKey != null) {
                val registry = loadHomebrewRegistry(game)
                val activeProfile = RuleResolutionHelper.getActiveProfile(registry)
                val homebrewSuppresses = activeProfile?.let { RuleResolutionHelper.isRandomCombatSuppressedInClaimedHexes(it) } ?: false
                if (homebrewSuppresses && hexClaimed) {
                    whisperEncounterSuppressed(game, actor, "combat encounter suppressed (claimed hex, homebrew setting)")
                    return false
                }

                val filterEnabled = camping.isFilterByHexState()
                val decision = decideEncounterFilter(
                    filterEnabled = filterEnabled,
                    hexClaimed = hexClaimed,
                    hexCleared = hexCleared,
                    hexContentSuppresses = hexContentSuppresses,
                    rolledCategory = EncounterCategory.COMBAT,
                )
                if (decision != EncounterFilterDecision.ALLOW) {
                    whisperEncounterSuppressed(game, actor, "combat encounter suppressed (claimed+cleared hex)")
                    return false
                }
            }
            table.rollWithDraw(rollMode = rollMode)
        }
        if (camping.hasPreparedCampsite()) {
            postCombatEffects(
                activeActivities = camping.alwaysPerformActivityIds.toSet() +
                        camping.campingActivitiesWithId()
                            .filter { it.actorUuid != null }
                            .map { it.activityId },
                partyLevel = partyLevel,
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

/**
 * Roadmap #11: convert a curated rumor with a quest hook into a simple
 * [CampaignQuest] record on the kingdom (the full quest generator, roadmap #2,
 * stays out of scope). The quest is flagged generatedByEvent so it shows the
 * existing "From: ..." badge on the quests board, and its id is tracked in
 * [KingdomData.rumorGeneratedQuestIds].
 */
suspend fun convertRumorToQuest(game: Game, rumor: Rumor) {
    val kingdomActor = game.getKingdomActors().firstOrNull()
    if (kingdomActor == null) {
        ui.notifications.error(t("camping.encounterNoKingdom"))
        return
    }
    val kingdom = kingdomActor.getKingdom() ?: return
    val now = kotlin.js.Date().toISOString()
    val questId = "rumor-${kotlin.js.Date().getTime().toLong()}"
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
package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.forms.SimpleApp
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.TurnTickingEngine
import at.posselt.pfrpg2e.kingdom.TickChange
import at.posselt.pfrpg2e.kingdom.TickResult
import at.posselt.pfrpg2e.kingdom.ActivityCapCalculator
import at.posselt.pfrpg2e.kingdom.getRealmData
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.kingdom.getAllSettlements
import at.posselt.pfrpg2e.kingdom.parseRuins
import at.posselt.pfrpg2e.kingdom.trackUnrestStagnation
import at.posselt.pfrpg2e.kingdom.trackLevelMismatch
import at.posselt.pfrpg2e.kingdom.trackLootImbalance
import at.posselt.pfrpg2e.kingdom.pacingMaxTurnGap
import at.posselt.pfrpg2e.kingdom.pacingMinUnrestDelta
import at.posselt.pfrpg2e.kingdom.pacingLevelMismatchRange
import at.posselt.pfrpg2e.kingdom.pacingLootImbalanceEnabled
import at.posselt.pfrpg2e.kingdom.pacingLootImbalanceRange
import at.posselt.pfrpg2e.kingdom.pacingChapterTargetLevel
import at.posselt.pfrpg2e.kingdom.postPacingAlertChat
import at.posselt.pfrpg2e.kingdom.data.ChosenFeature
import at.posselt.pfrpg2e.kingdom.data.RawPacingAlert
import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import at.posselt.pfrpg2e.kingdom.appendTurnRecord
import at.posselt.pfrpg2e.kingdom.buildTurnRecord
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.actor.partyMembers
import at.posselt.pfrpg2e.kingdom.resources.calculateStorage
import at.posselt.pfrpg2e.kingdom.sheet.contexts.TurnWizardContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.ChecklistItemContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.KingdomStateContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.ActivityCapContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.TickChangeContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.RuinContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.toContext
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.setAppFlag
import at.posselt.pfrpg2e.utils.unsetAppFlag
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.Game
import com.foundryvtt.core.game
import com.foundryvtt.core.applications.api.ApplicationRenderOptions
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.get
import js.objects.Record
import js.objects.recordOf
import kotlin.js.Promise
import kotlinx.coroutines.await

fun TickChange.toDisplayString(): String {
    return when {
        category == "resourcePoints" && field == "now" -> {
            val params = js("{}")
            params["old"] = oldValue.toString()
            params["new"] = newValue.toString()
            t("kingdom.turnWizard.preview.changeRp", params.unsafeCast<com.foundryvtt.core.AnyObject>())
        }
        category == "fame" && field == "now" -> {
            val params = js("{}")
            params["old"] = oldValue.toString()
            params["new"] = newValue.toString()
            t("kingdom.turnWizard.preview.changeFame", params.unsafeCast<com.foundryvtt.core.AnyObject>())
        }
        category == "consumption" && field == "now" -> {
            val params = js("{}")
            params["old"] = oldValue.toString()
            params["new"] = newValue.toString()
            t("kingdom.turnWizard.preview.changeConsumption", params.unsafeCast<com.foundryvtt.core.AnyObject>())
        }
        category == "modifiers" && field == "expired" -> {
            val params = js("{}")
            params["count"] = newValue.toString()
            t("kingdom.turnWizard.preview.expiredModifiers", params.unsafeCast<com.foundryvtt.core.AnyObject>())
        }
        else -> {
            val label = when (category) {
                "resourcePoints" -> "RP"
                "resourceDice" -> "RD"
                "fame" -> "Fame"
                "consumption" -> "Consumption"
                else -> category
            }
            if (oldValue != null) {
                "$label ($field): $oldValue → $newValue"
            } else {
                "$label ($field): $newValue"
            }
        }
    }
}

/**
 * Single source of truth for assembling [TurnTickingEngine.tick] arguments from a kingdom
 * snapshot. Both the End Turn commit path ([performEndTurn]) and the Turn Wizard preview
 * MUST call this — never tick() directly — so the preview cannot drift from what
 * committing actually applies. [currentTurn] is the turn being ticked into (previous + 1).
 */
fun runKingdomTurnTick(kingdom: KingdomData, storage: CommodityStorage, currentTurn: Int): TickResult =
    TurnTickingEngine.tick(
        fame = kingdom.fame,
        resourcePoints = kingdom.resourcePoints,
        resourceDice = kingdom.resourceDice,
        consumption = kingdom.consumption,
        commodities = kingdom.commodities,
        storage = storage,
        councilCooldowns = kingdom.councilCooldowns,
        modifiers = kingdom.modifiers,
        campaignClocks = kingdom.campaignClocks,
        campaignQuests = kingdom.campaignQuests ?: emptyArray(),
        kingdomLevel = kingdom.level,
        warThreats = kingdom.warThreats ?: emptyArray(),
        armyDeployments = kingdom.armyDeployments ?: emptyArray(),
        warPressure = kingdom.warPressure,
        currentTurn = currentTurn,
        xp = kingdom.xp,
        xpThreshold = kingdom.xpThreshold,
        rpNow = kingdom.resourcePoints.now,
        rpToXpConversionRate = kingdom.settings.rpToXpConversionRate,
        rpToXpConversionLimit = kingdom.settings.rpToXpConversionLimit,
        maximumFamePoints = kingdom.settings.maximumFamePoints,
        autoGainFamePerTurn = kingdom.settings.autoGainFamePerTurn,
        bonusResourceDice = kingdom.bonusResourceDice,
        activeBattles = kingdom.activeBattles ?: emptyArray(),
    )

suspend fun performEndTurn(game: Game, actor: KingdomActor, kingdom: KingdomData): TickResult {
    val currentTurn = (kingdom.currentTurn ?: 0) + 1
    kingdom.currentTurn = currentTurn
    val realm = game.getRealmData(actor, kingdom)
    val settlements = kingdom.getAllSettlements(game)
    val storage = calculateStorage(realm = realm, settlements = settlements.allSettlements)

    val tickResult = runKingdomTurnTick(kingdom, storage, currentTurn)
    kingdom.supernaturalSolutions = tickResult.supernaturalSolutions
    kingdom.creativeSolutions = tickResult.creativeSolutions
    kingdom.fame = tickResult.fame
    kingdom.resourcePoints = tickResult.resourcePoints
    kingdom.resourceDice = tickResult.resourceDice
    kingdom.consumption = tickResult.consumption
    kingdom.commodities = tickResult.commodities
    kingdom.councilCooldowns = tickResult.councilCooldowns
    kingdom.modifiers = tickResult.modifiers
    kingdom.campaignQuests = tickResult.campaignQuests
    kingdom.warThreats = tickResult.warThreats
    kingdom.armyDeployments = tickResult.armyDeployments
    kingdom.warPressure = tickResult.warPressure
    kingdom.bonusResourceDice = tickResult.bonusResourceDice
    kingdom.activeBattles = tickResult.activeBattles

    // Apply campaign clock tick results (already included in tickResult)
    kingdom.campaignClocks = tickResult.updatedClocks
    if (tickResult.totalUnrestChange > 0) {
        kingdom.unrest = kingdom.unrest + tickResult.totalUnrestChange
    }

    // Balance & pacing alerts (roadmap #13): fire-once advisories, accumulated then posted to chat below.
    val firedPacingAlerts = mutableListOf<RawPacingAlert>()

    // Unrest stagnation — warns when unrest hasn't moved for too many turns.
    val stagnationTrack = trackUnrestStagnation(
        previousUnrest = kingdom.pacingLastUnrest,
        currentUnrest = kingdom.unrest,
        previousCount = kingdom.pacingTurnsSinceUnrestChange,
        maxTurnGap = kingdom.settings.pacingMaxTurnGap(),
        minDelta = kingdom.settings.pacingMinUnrestDelta(),
        turn = currentTurn,
    )
    kingdom.pacingTurnsSinceUnrestChange = stagnationTrack.turnsSinceUnrestChange
    kingdom.pacingLastUnrest = kingdom.unrest
    stagnationTrack.alert?.let { firedPacingAlerts.add(it) }

    // Level mismatch + loot imbalance — compared against the configured chapter target
    // level when one is set, otherwise the party's average level.
    val partyLevels = actor.partyMembers().map { it.system.details.level.value }
    val avgPartyLevel = if (partyLevels.isNotEmpty()) partyLevels.sum() / partyLevels.size else null
    val targetLevel = kingdom.settings.pacingChapterTargetLevel() ?: avgPartyLevel
    if (targetLevel != null) {
        val levelTrack = trackLevelMismatch(
            kingdomLevel = kingdom.level,
            partyLevel = targetLevel,
            range = kingdom.settings.pacingLevelMismatchRange(),
            previousSeverity = kingdom.pacingLastLevelMismatch,
            turn = currentTurn,
        )
        kingdom.pacingLastLevelMismatch = levelTrack.severity
        levelTrack.alert?.let { firedPacingAlerts.add(it) }

        // Loot imbalance — highest settlement item-purchase level vs the target level.
        if (kingdom.settings.pacingLootImbalanceEnabled()) {
            val maxItemAccess = settlements.allSettlements.maxOfOrNull { it.itemPurchaseLevel }
            if (maxItemAccess != null) {
                val lootTrack = trackLootImbalance(
                    itemAccessLevel = maxItemAccess,
                    partyLevel = targetLevel,
                    range = kingdom.settings.pacingLootImbalanceRange(),
                    previousSeverity = kingdom.pacingLastLootImbalance,
                    turn = currentTurn,
                )
                kingdom.pacingLastLootImbalance = lootTrack.severity
                lootTrack.alert?.let { firedPacingAlerts.add(it) }
            }
        }
    }

    if (firedPacingAlerts.isNotEmpty()) {
        kingdom.pacingAlerts = (kingdom.pacingAlerts ?: emptyArray()) + firedPacingAlerts.toTypedArray()
    }

    // Per-turn history record (gap analysis item 2): snapshot post-tick kingdom state.
    val clockEventNames = tickResult.clockEvents.map { it.label }.toTypedArray()
    val warPressureNow = kingdom.warPressure?.currentPressure
    kingdom.turnHistory = appendTurnRecord(
        history = kingdom.turnHistory,
        record = buildTurnRecord(
            turn = currentTurn,
            timestamp = kotlin.js.Date().toISOString(),
            fame = kingdom.fame.now,
            resourcePoints = kingdom.resourcePoints.now,
            consumption = kingdom.consumption.now,
            unrest = kingdom.unrest,
            xpAwarded = tickResult.xpAwarded,
            clockEvents = clockEventNames,
            warPressure = warPressureNow,
        ),
    )

    actor.setKingdom(kingdom)

    // Post clock tick events to chat
    if (tickResult.clockEvents.isNotEmpty()) {
        val clockContext = js("{}")
        clockContext.events = tickResult.clockEvents
        clockContext.totalUnrestChange = tickResult.totalUnrestChange
        postChatTemplate(
            templatePath = "chatmessages/clock-tick.hbs",
            templateContext = clockContext,
        )
    }

    // Post any pacing advisories that fired this turn to chat
    firedPacingAlerts.forEach { alert -> postPacingAlertChat(alert) }

    val endTurnContext = js("{}")
    endTurnContext.clockEvents = tickResult.clockEvents
    endTurnContext.changes = tickResult.changes.map { it.toDisplayString() }.toTypedArray()
    endTurnContext.kingdomName = kingdom.name
    endTurnContext.xpAwarded = tickResult.xpAwarded
    endTurnContext.fame = kingdom.fame.now
    endTurnContext.maximumFamePoints = kingdom.settings.maximumFamePoints
    postChatTemplate(
        templatePath = "chatmessages/end-turn.hbs",
        templateContext = endTurnContext,
    )

    return tickResult
}

class TurnWizardApplication(
    private val kingdomActor: KingdomActor,
) : SimpleApp<TurnWizardContext>(
    title = t("kingdom.turnWizard.title"),
    template = "applications/kingdom/turn-wizard.hbs",
    classes = setOf("km-scroll-application"),
    scrollable = setOf(".window-content"),
    id = "kmTurnWizard-${kingdomActor.uuid}",
    width = 600,
    height = 700,
    resizable = true,
) {
    private var cachedChanges: Array<TickChange> = emptyArray()

    override fun _preparePartContext(
        partId: String,
        context: at.posselt.pfrpg2e.app.HandlebarsRenderContext,
        options: com.foundryvtt.core.applications.api.HandlebarsRenderOptions
    ): Promise<TurnWizardContext> = buildPromise {
        val kingdom = kingdomActor.getKingdom() ?: throw IllegalStateException("No kingdom data")
        
        val state = kingdomActor.getAppFlag<KingdomActor, dynamic>("turn-wizard-state")
        val checkedItems = mutableSetOf<String>()
        var showPreview = false
        if (state != null) {
            showPreview = state.showPreview.unsafeCast<Boolean>()
            if (state.checklist != null) {
                val array = state.checklist.unsafeCast<Array<String>>()
                checkedItems.addAll(array)
            }
        }
        
        val previewChangesContext = cachedChanges.map { change ->
            TickChangeContext(
                category = change.category,
                field = change.field,
                displayText = change.toDisplayString()
            )
        }.toTypedArray()
        
        buildContext(
            kingdom = kingdom,
            actor = kingdomActor,
            partId = partId,
            isFormValid = true,
            checkedItems = checkedItems,
            showPreview = showPreview,
            previewChanges = previewChangesContext
        )
    }

    override fun _attachPartListeners(partId: String, htmlElement: HTMLElement, options: com.foundryvtt.core.applications.api.ApplicationRenderOptions) {
        super._attachPartListeners(partId, htmlElement, options)
        
        htmlElement.querySelectorAll("input[type='checkbox'].km-checklist-toggle").asList()
            .filterIsInstance<HTMLElement>()
            .forEach { checkbox ->
                checkbox.addEventListener("change", {
                    val id = checkbox.dataset["id"] ?: return@addEventListener
                    buildPromise {
                        toggleChecklistItem(id)
                    }
                })
            }
            
        htmlElement.querySelector("button[data-action='preview-turn']")
            ?.addEventListener("click", {
                buildPromise {
                    previewTurn()
                }
            })
            
        htmlElement.querySelector("button[data-action='commit-turn']")
            ?.addEventListener("click", {
                buildPromise {
                    commitTurn()
                }
            })
            
        htmlElement.querySelector("button[data-action='cancel']")
            ?.addEventListener("click", {
                buildPromise {
                    cancel()
                }
            })
    }

    private suspend fun toggleChecklistItem(id: String) {
        var state = kingdomActor.getAppFlag<KingdomActor, dynamic>("turn-wizard-state")
        if (state == null) {
            state = js("{ checklist: [], showPreview: false }")
        }
        if (state.checklist == null) {
            state.checklist = emptyArray<String>()
        }
        val checklistArray = state.checklist.unsafeCast<Array<String>>()
        val newChecklist = if (id in checklistArray) {
            checklistArray.filter { it != id }.toTypedArray()
        } else {
            checklistArray + id
        }
        state.checklist = newChecklist
        kingdomActor.setAppFlag("turn-wizard-state", state)
        render()
    }

    private suspend fun previewTurn() {
        var state = kingdomActor.getAppFlag<KingdomActor, dynamic>("turn-wizard-state")
        if (state == null) {
            state = js("{ checklist: [], showPreview: false }")
        }
        state.showPreview = true
        kingdomActor.setAppFlag("turn-wizard-state", state)
        
        val kingdom = kingdomActor.getKingdom() ?: return
        val realm = game.getRealmData(kingdomActor, kingdom)
        val settlements = kingdom.getAllSettlements(game)
        val storage = calculateStorage(realm, settlements.allSettlements)
        // Simulate the same upcoming turn End Turn will tick into, without persisting the increment.
        val tickResult = runKingdomTurnTick(kingdom, storage, (kingdom.currentTurn ?: 0) + 1)
        
        cachedChanges = tickResult.changes.toTypedArray()
        render()
    }

    private suspend fun commitTurn() {
        val kingdom = kingdomActor.getKingdom() ?: return
        performEndTurn(game, kingdomActor, kingdom)
        kingdomActor.unsetAppFlag("turn-wizard-state")
        close().await()
    }

    private suspend fun cancel() {
        kingdomActor.unsetAppFlag("turn-wizard-state")
        close().await()
    }

    companion object {
        fun buildContext(
            kingdom: KingdomData,
            actor: KingdomActor? = null,
            partId: String = "",
            isFormValid: Boolean = true,
            checkedItems: Set<String> = emptySet(),
            showPreview: Boolean = false,
            previewChanges: Array<TickChangeContext> = emptyArray(),
        ): TurnWizardContext {
            val checklist = arrayOf(
                ChecklistItemContext(
                    id = "gain-fame",
                    label = t("kingdom.turnWizard.checklist.gainFame"),
                    description = "",
                    checked = "gain-fame" in checkedItems,
                    highlight = false
                ),
                ChecklistItemContext(
                    id = "adjust-unrest",
                    label = t("kingdom.turnWizard.checklist.adjustUnrest"),
                    description = "",
                    checked = "adjust-unrest" in checkedItems,
                    highlight = kingdom.unrest > 0
                ),
                ChecklistItemContext(
                    id = "collect-resources",
                    label = t("kingdom.turnWizard.checklist.collectResources"),
                    description = "",
                    checked = "collect-resources" in checkedItems,
                    highlight = false
                ),
                ChecklistItemContext(
                    id = "pay-consumption",
                    label = t("kingdom.turnWizard.checklist.payConsumption"),
                    description = "",
                    checked = "pay-consumption" in checkedItems,
                    highlight = false
                ),
                ChecklistItemContext(
                    id = "check-events",
                    label = t("kingdom.turnWizard.checklist.checkEvents"),
                    description = "",
                    checked = "check-events" in checkedItems,
                    highlight = false
                )
            )

            val commodities = kingdom.commodities
            
            var foodCap = 0
            var lumberCap = 0
            var luxuriesCap = 0
            var oreCap = 0
            var stoneCap = 0
            val ruinContextArray = mutableListOf<RuinContext>()
            
            if (actor != null) {
                try {
                    val realm = game.getRealmData(actor, kingdom)
                    val settlements = kingdom.getAllSettlements(game)
                    val storage = calculateStorage(realm, settlements.allSettlements)
                    foodCap = storage.food
                    lumberCap = storage.lumber
                    luxuriesCap = storage.luxuries
                    oreCap = storage.ore
                    stoneCap = storage.stone
                    
                    val parseRuins = kingdom.parseRuins(
                        choices = emptyList<ChosenFeature>(),
                        baseThreshold = kingdom.settings.ruinThreshold,
                        government = kingdom.government,
                    )
                    val contextRuins = kingdom.ruin.toContext(kingdom.settings.automateStats, parseRuins)
                    ruinContextArray.addAll(contextRuins)
                } catch (e: Throwable) {
                    // Ignore errors during unit test environments
                }
            }

            val stateContext = KingdomStateContext(
                rpNow = kingdom.resourcePoints.now,
                rpNext = kingdom.resourcePoints.next,
                foodNow = commodities.now.food,
                foodCap = foodCap,
                lumberNow = commodities.now.lumber,
                lumberCap = lumberCap,
                luxuriesNow = commodities.now.luxuries,
                luxuriesCap = luxuriesCap,
                oreNow = commodities.now.ore,
                oreCap = oreCap,
                stoneNow = commodities.now.stone,
                stoneCap = stoneCap,
                consumption = kingdom.consumption.now,
                unrest = kingdom.unrest,
                ruin = ruinContextArray.toTypedArray(),
                activeModifiers = kingdom.modifiers.size
            )

            val capsResult = ActivityCapCalculator.calculate(kingdom, emptyMap())
            val activityCaps = capsResult.caps.map { cap ->
                ActivityCapContext(
                    phase = cap.phase,
                    phaseLabel = t("kingdom.${cap.phase}"),
                    current = cap.current,
                    maximum = cap.maximum,
                    isOverCap = cap.isOverCap
                )
            }.toTypedArray()

            val canCommit = !capsResult.hasAnyOverCap

            return TurnWizardContext(
                partId = partId,
                isFormValid = isFormValid,
                kingdomName = kingdom.name,
                checklist = checklist,
                kingdomState = stateContext,
                activityCaps = activityCaps,
                previewChanges = previewChanges,
                showPreview = showPreview,
                canCommit = canCommit
            )
        }
        
        fun buildContextWithPreview(kingdom: KingdomData): TurnWizardContext {
            val dummyChange = TickChange("resourcePoints", "now", 10, 15)
            val changesContext = arrayOf(
                TickChangeContext(
                    category = dummyChange.category,
                    field = dummyChange.field,
                    displayText = dummyChange.toDisplayString()
                )
            )
            return buildContext(
                kingdom = kingdom,
                actor = null,
                partId = "",
                isFormValid = true,
                checkedItems = emptySet(),
                showPreview = true,
                previewChanges = changesContext
            )
        }
    }
}

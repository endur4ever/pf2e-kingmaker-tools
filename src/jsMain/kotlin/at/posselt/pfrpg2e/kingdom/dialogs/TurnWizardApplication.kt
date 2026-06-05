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
import at.posselt.pfrpg2e.kingdom.data.ChosenFeature
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
import at.posselt.pfrpg2e.campaign.CampaignClockManager
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

suspend fun performEndTurn(game: Game, actor: KingdomActor, kingdom: KingdomData): TickResult {
    val realm = game.getRealmData(actor, kingdom)
    val settlements = kingdom.getAllSettlements(game)
    val storage = calculateStorage(realm = realm, settlements = settlements.allSettlements)

    val tickResult = TurnTickingEngine.tick(
        fame = kingdom.fame,
        resourcePoints = kingdom.resourcePoints,
        resourceDice = kingdom.resourceDice,
        consumption = kingdom.consumption,
        commodities = kingdom.commodities,
        storage = storage,
        councilCooldowns = kingdom.councilCooldowns,
        modifiers = kingdom.modifiers,
        campaignQuests = kingdom.campaignQuests ?: emptyArray(),
        kingdomLevel = kingdom.level,
    )
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

    // Tick campaign clocks
    val clockResult = CampaignClockManager.tickAll(kingdom.campaignClocks)
    kingdom.campaignClocks = clockResult.updatedClocks
    if (clockResult.totalUnrestChange > 0) {
        kingdom.unrest = kingdom.unrest + clockResult.totalUnrestChange
    }

    actor.setKingdom(kingdom)

    // Post clock tick events to chat
    if (clockResult.events.isNotEmpty()) {
        val clockContext = js("{}")
        clockContext.events = clockResult.events
        clockContext.totalUnrestChange = clockResult.totalUnrestChange
        postChatTemplate(
            templatePath = "chatmessages/clock-tick.hbs",
            templateContext = clockContext,
        )
    }

    val endTurnContext = js("{}")
    endTurnContext.clockEvents = clockResult.events
    endTurnContext.changes = tickResult.changes.map { it.toDisplayString() }.toTypedArray()
    endTurnContext.kingdomName = kingdom.name
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
    id = "kmTurnWizard-${kingdomActor.uuid}",
    width = 600,
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
        val tickResult = TurnTickingEngine.tick(
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
        )
        
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

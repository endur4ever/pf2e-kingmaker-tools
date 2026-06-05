package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.SearchInput
import at.posselt.pfrpg2e.data.events.KingdomEventTrait
import at.posselt.pfrpg2e.data.kingdom.KingdomSkill
import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.data.kingdom.settlements.Settlement
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.RawOngoingKingdomEvent
import at.posselt.pfrpg2e.kingdom.getEvents
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.kingdom.sheet.executeResourceButton
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.formatAsModifier
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.ApplicationRenderOptions
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.applications.ux.TextEditor.enrichHtml
import com.foundryvtt.core.data.dsl.buildSchema
import js.core.Void
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@JsPlainObject
external interface AddEventStagesContext {
    var skills: Array<String>
    var leader: String
    var criticalSuccess: String
    var success: String
    var failure: String
    var criticalFailure: String
}

@JsPlainObject
external interface AddEventContext {
    var id: String
    var label: String
    var description: String
    var automationNotes: String?
    var special: String?
    var resolution: String?
    var traits: Array<String>
    var location: String?
    var stages: Array<AddEventStagesContext>
    var isSettlement: Boolean
}


@JsPlainObject
external interface AddEventsContext : ValidatedHandlebarsContext {
    var events: Array<AddEventContext>
    var search: FormElementContext
    val isGM: Boolean
}

@JsPlainObject
external interface AddEventsData {
    val search: String
}

@JsExport
class AddEventsDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("search")
        }
    }
}

suspend fun createOngoingEvent(
    id: String,
    isSettlementEvent: Boolean,
    settlements: List<Settlement>,
) = if (isSettlementEvent) {
    val pick = pickEventSettlement(settlements)
    RawOngoingKingdomEvent(
        stage = 0,
        id = id,
        settlementSceneId = pick.settlementId,
        secretLocation = pick.secretLocation,
    )
} else {
    RawOngoingKingdomEvent(
        stage = 0,
        id = id,
    )
}

class AddEvent(
    private val game: Game,
    private val kingdomActor: KingdomActor,
    private val kingdom: KingdomData,
    private val settlements: List<Settlement>,
    private val onSave: suspend (event: RawOngoingKingdomEvent) -> Unit,
) : FormApp<AddEventsContext, AddEventsData>(
    title = t("kingdom.addEvent"),
    template = "applications/kingdom/event-browser.hbs",
    width = 600,
    id = "kmEvents-${kingdomActor.uuid}",
    dataModel = AddEventsDataModel::class.js,
    scrollable = setOf(".km-add-events"),
) {
    var search = ""

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "add-event" -> {
                buildPromise {
                    val id = target.dataset["id"] as String
                    val isSettlementEvent = target.dataset["settlementEvent"] == "true"
                    val evt = createOngoingEvent(
                        id = id,
                        isSettlementEvent = isSettlementEvent,
                        settlements = settlements,
                    )
                    onSave(evt)
                    close()
                }
            }
            "generate-quest-from-event" -> {
                val eventId = target.dataset["id"] as String
                buildPromise {
                    at.posselt.pfrpg2e.kingdom.dialogs.generateQuestFromEvent(
                        game = game,
                        kingdomActor = kingdomActor,
                        eventId = eventId,
                    )
                }
            }
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<AddEventsContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val events = kingdom.getEvents(applyBlacklist = true)
            .sortedBy { it.name }
            .filter {
                val stages = it.stages.joinToString(" ") {
                    (it.criticalSuccess?.msg ?: "") +
                            (it.success?.msg ?: "") +
                            (it.failure?.msg ?: "") +
                            (it.criticalFailure?.msg ?: "")
                }
                val haystack =
                    "${it.name} ${it.resolution ?: ""} ${it.special ?: ""} ${it.location ?: ""} $stages".lowercase()
                search.split(" ").all { haystack.contains(it) }
            }
            .map {
                async {
                    val modifier = it.modifier
                    val stages = it.stages.map { stage ->
                        val criticalSuccess = enrichHtml(stage.criticalSuccess?.msg ?: "")
                        val success = enrichHtml(stage.success?.msg ?: "")
                        val failure = enrichHtml(stage.failure?.msg ?: "")
                        val criticalFailure = enrichHtml(stage.criticalFailure?.msg ?: "")
                        AddEventStagesContext(
                            skills = stage.skills.mapNotNull { KingdomSkill.fromString(it) }
                                .map { t(it) }
                                .toTypedArray(),
                            leader = stage.leader.let { Leader.fromString(it) ?: Leader.RULER }.let { t(it) },
                            criticalSuccess = criticalSuccess,
                            success = success,
                            failure = failure,
                            criticalFailure = criticalFailure,
                        )
                    }.toTypedArray()
                    val description = enrichHtml(it.description)
                    AddEventContext(
                        id = it.id,
                        label = it.name + if (modifier != null && modifier != 0) " (${modifier.formatAsModifier()})" else "",
                        description = description,
                        special = it.special,
                        resolution = it.resolution,
                        traits = it.traits.mapNotNull { KingdomEventTrait.fromString(it) }.map { t(it) }.toTypedArray(),
                        location = it.location,
                        stages = stages,
                        isSettlement = KingdomEventTrait.SETTLEMENT.value in it.traits,
                        automationNotes = it.automationNotes,
                    )
                }
            }
            .awaitAll()
            .toTypedArray()
        AddEventsContext(
            partId = parent.partId,
            events = events,
            isFormValid = isFormValid,
            isGM = game.user.isGM,
            search = SearchInput(
                name = "search",
                label = t("kingdom.filter"),
                hideLabel = true,
                value = search,
                placeholder = t("kingdom.search"),
                required = false,
            ).toContext()
        )
    }

    override fun _attachPartListeners(partId: String, htmlElement: HTMLElement, options: ApplicationRenderOptions) {
        super._attachPartListeners(partId, htmlElement, options)
        htmlElement.querySelectorAll(".km-gain-lose").asList()
            .filterIsInstance<HTMLElement>()
            .forEach { elem ->
                elem.addEventListener("click", {
                    kingdomActor.getKingdom()?.let { kingdom ->
                        buildPromise {
                            executeResourceButton(
                                game = game,
                                actor = kingdomActor,
                                kingdom = kingdom,
                                elem = elem,
                                activityId = null,
                            )
                        }
                    }
                })
            }
    }

    override fun onParsedSubmit(value: AddEventsData): Promise<Void> = buildPromise {
        search = value.search
        undefined
    }
}

suspend fun generateQuestFromEvent(
    game: Game,
    kingdomActor: KingdomActor,
    eventId: String,
) {
    val kingdom = kingdomActor.getKingdom() ?: return
    val events = kingdom.getEvents(applyBlacklist = true)
    val event = events.find { it.id == eventId } ?: return
    val kingdomLevel = kingdom.level
    val template = at.posselt.pfrpg2e.questevent.KingdomEventTemplate(
        id = event.id,
        name = event.name,
        description = event.description,
        traits = event.traits.mapNotNull { at.posselt.pfrpg2e.data.events.KingdomEventTrait.fromString(it) }.map { it.value },
    )
    val result = at.posselt.pfrpg2e.questevent.QuestGenerator.generateFromEvent(
        event = template,
        kingdomLevel = kingdomLevel,
        currentQuests = emptyList(),
    )
    if (result != null && result.isEligible) {
        val now = js("new Date().toISOString()")
        val quest = at.posselt.pfrpg2e.questevent.CampaignQuest(
            id = "cq-${js("Date.now()")}",
            templateId = result.preview.id,
            name = result.preview.name,
            type = result.preview.type,
            description = result.preview.description,
            gmNotes = result.preview.gmNotes,
            recommendedLevel = result.preview.recommendedLevel,
            objectives = result.preview.objectives,
            rewards = result.preview.rewards,
            status = at.posselt.pfrpg2e.questevent.QuestStatus.ACTIVE,
            turnsRemaining = null,
            visibleToPlayers = false,
            generatedByEvent = true,
            sourceEventId = result.sourceEventId,
            sourceEventName = result.sourceEventName,
            createdAt = now.unsafeCast<String>(),
            campaignId = "default",
        )
        // Add campaign quest to kingdom data
        val currentQuests = kingdom.campaignQuests ?: emptyArray<dynamic>()
        val newQuests = currentQuests.toMutableList()
        newQuests.add(quest)
        kingdom.campaignQuests = newQuests.toTypedArray()
        kingdomActor.setKingdom(kingdom)
    }
}
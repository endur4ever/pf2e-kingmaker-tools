package at.posselt.pfrpg2e.questevent

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getEvents
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.ApplicationRenderOptions
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@JsPlainObject
external interface SummarizedEventContext {
    val id: String
    val name: String
    val traits: Array<String>
}

@JsPlainObject
external interface QuestPreviewContext {
    val name: String
    val description: String
    val type: String
    val recommendedLevel: Int
    val objectives: Array<ObjectiveContext>
    val rewards: RewardContext
    val isDefaultVisibleToPlayers: Boolean
}

@JsPlainObject
external interface ObjectiveContext {
    val id: String
    val description: String
    val optional: Boolean
}

@JsPlainObject
external interface RewardContext {
    val xp: Int
    val rp: Int
    val fame: Int
    val commodities: Array<CommodityRewardContext>
    val unrestReduction: Int
    val customReward: String?
}

@JsPlainObject
external interface CommodityRewardContext {
    val type: String
    val amount: Int
}

@JsPlainObject
external interface QuestGeneratorSettingsContext {
    val defaultVisibilityToPlayers: Boolean
    val maxActiveGeneratedQuests: Int
    val autoAdvanceQuestTimersOnTurn: Boolean
}

@JsPlainObject
external interface QuestGeneratorContext : ValidatedHandlebarsContext {
    val events: Array<SummarizedEventContext>
    val selectedEventId: String?
    val questPreview: QuestPreviewContext?
    val showPreview: Boolean
    val canGenerate: Boolean
    val generatedQuestCount: Int
    val maxQuests: Int
    val settings: QuestGeneratorSettingsContext
}

@JsPlainObject
external interface GenerateQuestData {
    val eventId: String?
    val visibleToPlayers: Boolean
}

@JsExport
class GenerateQuestDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?,
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("eventId", nullable = true)
            boolean("visibleToPlayers")
        }
    }
}

class GenerateQuestDialog(
    private val game: dynamic,
    private val kingdomActor: KingdomActor,
    private val settings: QuestGeneratorSettings = QuestGeneratorSettings(),
    private val onGenerate: suspend (quest: CampaignQuest) -> Unit,
) : FormApp<QuestGeneratorContext, GenerateQuestData>(
    title = t("kingdom.questGenerator.title"),
    template = "applications/kingdom/generate-quest-dialog.hbs",
    width = 700,
    height = 600,
    id = "kmGenerateQuest",
    dataModel = GenerateQuestDataModel::class.js,
) {
    private var selectedEventId: String? = null
    private var currentPreview: QuestGenerationResult? = null

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "generate" -> {
                selectedEventId = target.dataset["eventId"]
                render()
            }
            "commit" -> {
                currentPreview?.let { result ->
                    if (result.isEligible) {
                        val now = js("new Date().toISOString()")
                        val quest = CampaignQuest(
                            id = "cq-${js("Date.now()")}",
                            templateId = result.preview.id,
                            name = result.preview.name,
                            type = result.preview.type,
                            description = result.preview.description,
                            gmNotes = result.preview.gmNotes,
                            recommendedLevel = result.preview.recommendedLevel,
                            objectives = result.preview.objectives,
                            rewards = result.preview.rewards,
                            status = QuestStatus.ACTIVE,
                            turnsRemaining = null,
                            visibleToPlayers = false,
                            generatedByEvent = true,
                            sourceEventId = result.sourceEventId,
                            sourceEventName = result.sourceEventName,
                            createdAt = now.unsafeCast<String>(),
                            campaignId = "default",
                        )
                        buildPromise {
                            onGenerate(quest)
                            close()
                        }
                    }
                }
            }
            "cancel" -> {
                close()
            }
        }
    }

    override fun _onRender(context: AnyObject, options: ApplicationRenderOptions) {
        super._onRender(context, options)
        // After a Generate click re-renders the dialog, bring the preview into view so
        // it isn't missed below the event list.
        if (selectedEventId != null) {
            element.querySelector(".km-qg-preview")
                ?.asDynamic()
                ?.scrollIntoView(js("({ behavior: 'smooth', block: 'start' })"))
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<QuestGeneratorContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()

        val kingdom = kingdomActor.getKingdom()
        val kingdomLevel = kingdom?.level ?: 1
        val ongoingEvents = kingdom?.ongoingEvents ?: emptyArray()
        val allEventTemplates = kingdom?.getEvents(applyBlacklist = true) ?: emptyArray()

        // The picker lists the kingdom's ongoing events first (most relevant), then the
        // rest of the event catalog so the generator is usable even before any event is
        // active. Deduplicated by id, keeping the ongoing entry when both are present.
        val ongoingEntries = ongoingEvents.map { ongoing ->
            val template = allEventTemplates.firstOrNull { it.id == ongoing.id }
            SummarizedEventContext(
                id = ongoing.id,
                name = template?.name ?: ongoing.id,
                traits = template?.traits ?: emptyArray(),
            )
        }
        val catalogEntries = allEventTemplates
            .sortedBy { it.name }
            .map { template ->
                SummarizedEventContext(
                    id = template.id,
                    name = template.name,
                    traits = template.traits,
                )
            }
        val events = (ongoingEntries + catalogEntries)
            .distinctBy { it.id }
            .toTypedArray()

        val canGenerate = QuestGenerator.canGenerateMore(emptyList(), settings)

        val selectedTemplate = selectedEventId?.let { id ->
            allEventTemplates.firstOrNull { it.id == id }
        }
        val result = selectedTemplate?.let { template ->
            QuestGenerator.generateFromEvent(
                event = KingdomEventTemplate(
                    id = template.id,
                    name = template.name,
                    description = template.description,
                    traits = template.traits.toList(),
                ),
                kingdomLevel = kingdomLevel,
                currentQuests = emptyList(),
                settings = settings,
            )
        }
        // Cache the result so the "commit" action can build the CampaignQuest from it.
        currentPreview = result

        val preview = result?.takeIf { it.isEligible }?.preview?.let { tmpl ->
            QuestPreviewContext(
                name = tmpl.name,
                description = tmpl.description,
                type = tmpl.type.value,
                recommendedLevel = tmpl.recommendedLevel,
                objectives = tmpl.objectives.map { obj ->
                    ObjectiveContext(
                        id = obj.id,
                        description = obj.description,
                        optional = obj.optional,
                    )
                }.toTypedArray(),
                rewards = RewardContext(
                    xp = tmpl.rewards.xp,
                    rp = tmpl.rewards.rp,
                    fame = tmpl.rewards.fame,
                    commodities = tmpl.rewards.commodities.map { (k, v) ->
                        CommodityRewardContext(type = k, amount = v)
                    }.toTypedArray(),
                    unrestReduction = tmpl.rewards.unrestReduction,
                    customReward = tmpl.rewards.customReward,
                ),
                isDefaultVisibleToPlayers = tmpl.isDefaultVisibleToPlayers,
            )
        }

        QuestGeneratorContext(
            partId = parent.partId,
            isFormValid = canGenerate && preview != null,
            events = events,
            selectedEventId = selectedEventId,
            questPreview = preview,
            showPreview = preview != null,
            canGenerate = canGenerate,
            generatedQuestCount = 0,
            maxQuests = settings.maxActiveGeneratedQuests,
            settings = QuestGeneratorSettingsContext(
                defaultVisibilityToPlayers = settings.defaultVisibilityToPlayers,
                maxActiveGeneratedQuests = settings.maxActiveGeneratedQuests,
                autoAdvanceQuestTimersOnTurn = settings.autoAdvanceQuestTimersOnTurn,
            ),
        )
    }

    override fun onParsedSubmit(value: GenerateQuestData): Promise<Void> = buildPromise {
        selectedEventId = value.eventId
        null
    }
}

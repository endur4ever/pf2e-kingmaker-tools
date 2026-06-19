package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.TextArea
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.kingdom.data.RawQuest
import at.posselt.pfrpg2e.kingdom.data.RawQuestRewards
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
import org.w3c.dom.asList
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@JsExport
class QuestModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("title")
            string("description")
            string("giver")
            string("type")
            string("target", nullable = true)
            int("rp")
            int("xp")
            int("unrest", allowNegative = true)
            int("food")
            int("lumber")
            int("stone")
            int("ore")
            int("luxuries")
            string("rewardOther", nullable = true)
            string("flavorTextCompleted")
            string("notes", nullable = true)
        }
    }
}

@JsPlainObject
external interface AddQuestData {
    val title: String
    val description: String
    val giver: String
    val type: String
    val target: String?
    val rp: Int
    val xp: Int
    val unrest: Int
    val food: Int
    val lumber: Int
    val stone: Int
    val ore: Int
    val luxuries: Int
    val rewardOther: String?
    val flavorTextCompleted: String
    val notes: String?
    val generatedFromEvent: Boolean
    val sourceEventId: String?
    val sourceEventName: String?
}

@JsPlainObject
external interface AddQuestContext : ValidatedHandlebarsContext {
    val formRows: Array<FormElementContext>
}

class AddQuest(
    private val existing: RawQuest? = null,
    private val prefillTitle: String? = null,
    private val prefillGiver: String? = null,
    private val onSave: suspend (quest: RawQuest) -> Unit,
) : FormApp<AddQuestContext, AddQuestData>(
    title = t(if (existing != null) "kingdom.quests.editQuest" else "kingdom.quests.addQuest"),
    template = "components/forms/application-form.hbs",
    debug = true,
    dataModel = QuestModel::class.js,
    id = "kmAddQuest",
    width = 680,
    height = 700,
    resizable = true,
    // explicit height + scrollable content so the window can be freely resized
    // and keeps its size across submitOnChange re-renders (instead of snapping
    // back to content height); overflow lets a shorter window scroll.
    scrollable = setOf(".window-content"),
    classes = setOf("km-scroll-application"),
) {
    var data: AddQuestData = existing?.let { q ->
        AddQuestData(
            title = q.title,
            description = q.description,
            giver = q.giver,
            type = q.type,
            target = q.target,
            rp = q.rewards.rp ?: 0,
            xp = q.rewards.xp ?: 0,
            unrest = q.rewards.unrest ?: 0,
            food = q.rewards.food ?: 0,
            lumber = q.rewards.lumber ?: 0,
            stone = q.rewards.stone ?: 0,
            ore = q.rewards.ore ?: 0,
            luxuries = q.rewards.luxuries ?: 0,
            rewardOther = q.rewards.other ?: "",
            flavorTextCompleted = q.flavorTextCompleted,
            notes = q.notes ?: "",
            generatedFromEvent = false,
            sourceEventId = null,
            sourceEventName = null,
        )
    } ?: AddQuestData(
        // create mode: optional prefills seed the faction context when opened from a
        // faction-standing threshold offer.
        title = prefillTitle ?: "",
        description = "",
        giver = prefillGiver ?: "",
        type = "other",
        target = null,
        rp = 0,
        xp = 0,
        unrest = 0,
        food = 0,
        lumber = 0,
        stone = 0,
        ore = 0,
        luxuries = 0,
        rewardOther = "",
        flavorTextCompleted = "",
        notes = "",
        generatedFromEvent = false,
        sourceEventId = null,
        sourceEventName = null,
    )

    init {
        isFormValid = existing != null
    }

    // FormApp uses submitOnChange, so every edit re-renders the form and rebuilds
    // the textareas at their default height — discarding any manual resize. Remember
    // each textarea's dragged height (by name) and reapply it after every render so a
    // resized notes/reward box keeps its size for as long as the dialog is open.
    private val textareaHeights = mutableMapOf<String, String>()

    private fun snapshotTextareaHeights() {
        element.querySelectorAll("textarea[name]").asList().forEach { node ->
            val ta = node.asDynamic()
            val name = ta.name as? String ?: return@forEach
            val height = ta.style.height as? String
            if (!height.isNullOrEmpty()) {
                textareaHeights[name] = height
            }
        }
    }

    override fun _onRender(context: AnyObject, options: ApplicationRenderOptions) {
        super._onRender(context, options)
        element.querySelectorAll("textarea[name]").asList().forEach { node ->
            val ta = node.asDynamic()
            val name = ta.name as? String ?: return@forEach
            textareaHeights[name]?.let { ta.style.height = it }
        }
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                val quest = RawQuest(
                    id = existing?.id ?: "quest-${js("Date.now()")}",
                    title = data.title,
                    description = data.description,
                    giver = data.giver,
                    status = existing?.status ?: "active",
                    type = data.type,
                    target = data.target,
                    rewards = RawQuestRewards(
                        rp = if (data.rp != 0) data.rp else null,
                        xp = if (data.xp != 0) data.xp else null,
                        unrest = if (data.unrest != 0) data.unrest else null,
                        food = if (data.food != 0) data.food else null,
                        lumber = if (data.lumber != 0) data.lumber else null,
                        stone = if (data.stone != 0) data.stone else null,
                        ore = if (data.ore != 0) data.ore else null,
                        luxuries = if (data.luxuries != 0) data.luxuries else null,
                        other = data.rewardOther?.takeIf { it.isNotBlank() },
                    ),
                    flavorTextCompleted = data.flavorTextCompleted,
                    notes = data.notes?.takeIf { it.isNotBlank() },
                )
                buildPromise {
                    onSave(quest)
                    close()
                }
            }
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<AddQuestContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val typeOptions = listOf(
            SelectOption(value = "explore_hex", label = t("kingdom.quests.type.explore_hex")),
            SelectOption(value = "claim_hex", label = t("kingdom.quests.type.claim_hex")),
            SelectOption(value = "build_structure", label = t("kingdom.quests.type.build_structure")),
            SelectOption(value = "clear_hex", label = t("kingdom.quests.type.clear_hex")),
            SelectOption(value = "assign_leader", label = t("kingdom.quests.type.assign_leader")),
            SelectOption(value = "other", label = t("kingdom.quests.type.other")),
        )
        val rows = formContext(
            TextInput(
                name = "title",
                label = t("kingdom.quests.fields.title"),
                stacked = false,
                value = data.title,
            ),
            TextInput(
                name = "giver",
                label = t("kingdom.quests.fields.giver"),
                stacked = false,
                value = data.giver,
            ),
            Select(
                name = "type",
                label = t("kingdom.quests.fields.type"),
                value = data.type,
                options = typeOptions,
                stacked = false,
            ),
            TextInput(
                name = "target",
                label = t("kingdom.quests.fields.target"),
                stacked = false,
                value = data.target ?: "",
                required = false,
            ),
            TextInput(
                name = "description",
                label = t("kingdom.quests.fields.description"),
                stacked = false,
                value = data.description,
            ),
            TextInput(
                name = "flavorTextCompleted",
                label = t("kingdom.quests.fields.flavorTextCompleted"),
                stacked = false,
                value = data.flavorTextCompleted,
            ),
            TextArea(
                name = "notes",
                label = t("kingdom.quests.fields.notes"),
                help = t("kingdom.quests.fields.notesHelp"),
                value = data.notes ?: "",
                required = false,
                elementClasses = listOf("km-quest-notes-input"),
            ),
            NumberInput(
                name = "rp",
                label = t("kingdom.quests.fields.rp"),
                value = data.rp,
                stacked = false,
                required = false,
            ),
            NumberInput(
                name = "xp",
                label = t("kingdom.quests.fields.xp"),
                value = data.xp,
                stacked = false,
                required = false,
            ),
            NumberInput(
                name = "unrest",
                label = t("kingdom.quests.fields.unrest"),
                value = data.unrest,
                stacked = false,
                required = false,
            ),
            NumberInput(
                name = "food",
                label = t("kingdom.quests.fields.food"),
                value = data.food,
                stacked = false,
                required = false,
            ),
            NumberInput(
                name = "lumber",
                label = t("kingdom.quests.fields.lumber"),
                value = data.lumber,
                stacked = false,
                required = false,
            ),
            NumberInput(
                name = "stone",
                label = t("kingdom.quests.fields.stone"),
                value = data.stone,
                stacked = false,
                required = false,
            ),
            NumberInput(
                name = "ore",
                label = t("kingdom.quests.fields.ore"),
                value = data.ore,
                stacked = false,
                required = false,
            ),
            NumberInput(
                name = "luxuries",
                label = t("kingdom.quests.fields.luxuries"),
                value = data.luxuries,
                stacked = false,
                required = false,
            ),
            TextArea(
                name = "rewardOther",
                label = t("kingdom.quests.fields.rewardOther"),
                help = t("kingdom.quests.fields.rewardOtherHelp"),
                value = data.rewardOther ?: "",
                required = false,
                elementClasses = listOf("km-quest-reward-other-input"),
            ),
        )
        AddQuestContext(
            partId = parent.partId,
            formRows = rows,
            isFormValid = isFormValid,
        )
    }

    override fun onParsedSubmit(value: AddQuestData): Promise<Void> = buildPromise {
        // capture current textarea sizes before the (imminent) re-render resets them
        snapshotTextareaHeights()
        data = value
        null
    }
}

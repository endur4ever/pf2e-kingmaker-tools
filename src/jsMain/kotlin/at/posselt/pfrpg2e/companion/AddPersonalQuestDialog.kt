package at.posselt.pfrpg2e.companion

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.TextArea
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@JsExport
class PersonalQuestModel(
    value: AnyObject,
    options: DocumentConstructionContext?,
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("title")
            string("description")
            string("questHook", nullable = true)
            int("turnsRemaining", nullable = true)
            int("influenceReward")
            boolean("visibleToPlayers")
        }
    }
}

@JsPlainObject
external interface AddPersonalQuestData {
    val title: String
    val description: String
    val questHook: String?
    val turnsRemaining: Int?
    val influenceReward: Int
    val visibleToPlayers: Boolean
}

@JsPlainObject
external interface AddPersonalQuestContext : ValidatedHandlebarsContext {
    val formRows: Array<FormElementContext>
}

/**
 * Dialog to create or edit a [CompanionPersonalQuest] tied to a companion.
 * Defaults to GM-only (visibleToPlayers = false) per design Decision 2.
 */
class AddPersonalQuest(
    private val companionId: String,
    private val existing: CompanionPersonalQuest? = null,
    private val onSave: suspend (quest: CompanionPersonalQuest) -> Unit,
) : FormApp<AddPersonalQuestContext, AddPersonalQuestData>(
    title = t(if (existing != null) "kingdom.companion.editPersonalQuest" else "kingdom.companion.addPersonalQuest"),
    template = "components/forms/application-form.hbs",
    dataModel = PersonalQuestModel::class.js,
    id = "kmAddPersonalQuest",
) {
    var data: AddPersonalQuestData = existing?.let { q ->
        AddPersonalQuestData(
            title = q.title,
            description = q.description,
            questHook = q.questHook,
            turnsRemaining = q.turnsRemaining,
            influenceReward = q.influenceReward,
            visibleToPlayers = q.visibleToPlayers,
        )
    } ?: AddPersonalQuestData(
        title = "",
        description = "",
        questHook = null,
        turnsRemaining = null,
        influenceReward = 0,
        visibleToPlayers = false,
    )

    init {
        isFormValid = existing != null
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                val quest = CompanionPersonalQuest(
                    id = existing?.id ?: "pquest-${js("Date.now()")}",
                    title = data.title,
                    description = data.description,
                    companionId = existing?.companionId ?: companionId,
                    status = existing?.status ?: "active",
                    turnsRemaining = data.turnsRemaining,
                    visibleToPlayers = data.visibleToPlayers,
                    influenceReward = clampInfluence(data.influenceReward),
                ).also {
                    it.questHook = data.questHook
                }
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
        options: HandlebarsRenderOptions,
    ): Promise<AddPersonalQuestContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val rows = formContext(
            TextInput(
                name = "title",
                label = t("kingdom.companion.quest.title"),
                stacked = false,
                value = data.title,
            ),
            TextArea(
                name = "description",
                label = t("kingdom.companion.quest.description"),
                stacked = false,
                value = data.description,
                required = false,
            ),
            TextArea(
                name = "questHook",
                label = t("kingdom.companion.questHook"),
                stacked = false,
                value = data.questHook ?: "",
                required = false,
            ),
            NumberInput(
                name = "turnsRemaining",
                label = t("kingdom.companion.quest.turns"),
                value = data.turnsRemaining,
                stacked = false,
                required = false,
            ),
            NumberInput(
                name = "influenceReward",
                label = t("kingdom.companion.quest.influenceReward"),
                value = data.influenceReward,
                stacked = false,
                required = false,
            ),
            CheckboxInput(
                name = "visibleToPlayers",
                label = t("kingdom.companion.quest.visibleToPlayers"),
                value = data.visibleToPlayers,
            ),
        )
        AddPersonalQuestContext(
            partId = parent.partId,
            formRows = rows,
            isFormValid = isFormValid,
        )
    }

    override fun onParsedSubmit(value: AddPersonalQuestData): Promise<Void> = buildPromise {
        data = value
        null
    }
}

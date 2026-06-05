package at.posselt.pfrpg2e.questevent

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.NumberInput
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

@JsPlainObject
external interface QuestGeneratorSettingsFormContext : ValidatedHandlebarsContext {
    val formRows: Array<at.posselt.pfrpg2e.app.forms.FormElementContext>
}

@JsExport
class QuestGeneratorSettingsFormModel(
    value: AnyObject,
    options: DocumentConstructionContext?,
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            boolean("defaultVisibilityToPlayers")
            int("maxActiveGeneratedQuests")
            boolean("autoAdvanceQuestTimersOnTurn")
        }
    }
}

class QuestGeneratorSettingsDialog(
    private val currentSettings: QuestGeneratorSettings = QuestGeneratorSettings(),
    private val onSave: suspend (settings: QuestGeneratorSettings) -> Unit,
) : FormApp<QuestGeneratorSettingsFormContext, QuestGeneratorSettings>(
    title = t("kingdom.questGenerator.settings.title"),
    template = "components/forms/application-form.hbs",
    width = 500,
    id = "kmQuestGeneratorSettings",
    dataModel = QuestGeneratorSettingsFormModel::class.js,
) {
    private var settings = currentSettings

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                buildPromise {
                    onSave(settings)
                    close()
                }
            }
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<QuestGeneratorSettingsFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val rows = formContext(
            CheckboxInput(
                name = "defaultVisibilityToPlayers",
                label = t("kingdom.questGenerator.settings.defaultVisibility"),
                value = settings.defaultVisibilityToPlayers,
                stacked = false,
            ),
            NumberInput(
                name = "maxActiveGeneratedQuests",
                label = t("kingdom.questGenerator.settings.maxActiveQuests"),
                value = settings.maxActiveGeneratedQuests,
                stacked = false,
                required = true,
            ),
            CheckboxInput(
                name = "autoAdvanceQuestTimersOnTurn",
                label = t("kingdom.questGenerator.settings.autoAdvanceTimers"),
                value = settings.autoAdvanceQuestTimersOnTurn,
                stacked = false,
            ),
        )
        QuestGeneratorSettingsFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            formRows = rows,
        )
    }

    override fun onParsedSubmit(value: QuestGeneratorSettings): Promise<Void> = buildPromise {
        settings = value
        null
    }
}

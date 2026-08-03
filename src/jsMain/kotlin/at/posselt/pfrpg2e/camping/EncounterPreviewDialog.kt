package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import js.core.Void
import js.objects.recordOf
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@Suppress("unused")
@JsPlainObject
external interface EncounterPreviewContext : ValidatedHandlebarsContext {
    val categoryValue: String
    val categoryLabel: String
    val categoryIcon: String
    val regionName: String
    val resultText: String
    val hasResult: Boolean
    val hasRumor: Boolean
    val rumorText: String?
    val isQuestHook: Boolean
    val acceptLabel: String
    val rerollLabel: String
    val rejectLabel: String
    val convertLabel: String
}

@JsExport
class EncounterPreviewDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema { }
    }
}

@JsPlainObject
external interface EncounterPreviewData

/**
 * GM-only preview of a curated random encounter (roadmap #11). Shows the rolled
 * category + result before it is posted to chat, and lets the GM accept, reroll,
 * reject, or convert a rumor hook into a quest. Modeled on
 * [at.posselt.pfrpg2e.camping.dialogs.ConfirmWatchApplication].
 */
class EncounterPreviewDialog(
    private val category: EncounterCategory,
    private val regionName: String,
    private val resultText: String,
    private val rumor: Rumor?,
    private val onAccept: () -> Unit,
    private val onReroll: () -> Unit,
    private val onReject: () -> Unit,
    private val onConvertToQuest: (Rumor) -> Unit,
) : FormApp<EncounterPreviewContext, EncounterPreviewData>(
    title = t("camping.encounterPreview"),
    template = "applications/camping/encounter-preview.hbs",
    debug = false,
    dataModel = EncounterPreviewDataModel::class.js,
    submitOnChange = false,
    width = 480,
    id = "kmEncounterPreview",
) {
    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-accept" -> { close(); onAccept() }
            "km-reroll" -> { close(); onReroll() }
            "km-reject" -> { close(); onReject() }
            "km-convert-quest" -> rumor?.let { close(); onConvertToQuest(it) }
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<EncounterPreviewContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        EncounterPreviewContext(
            partId = parent.partId,
            isFormValid = true,
            categoryValue = category.value,
            categoryLabel = t("camping.encounterCategory.${category.value}"),
            categoryIcon = category.iconClass,
            regionName = regionName,
            resultText = resultText,
            hasResult = resultText.isNotBlank(),
            hasRumor = rumor != null,
            rumorText = rumor?.text,
            isQuestHook = rumor?.isQuestHook == true,
            acceptLabel = t("camping.encounterAccept"),
            rerollLabel = t("camping.encounterReroll"),
            rejectLabel = t("camping.encounterReject"),
            convertLabel = t("camping.encounterConvertQuest"),
        )
    }

    override fun onParsedSubmit(value: EncounterPreviewData): Promise<Void> = buildPromise {
        undefined
    }
}

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
import com.foundryvtt.core.Game
import at.posselt.pfrpg2e.camping.dialogs.ModifyEncounterStage
import at.posselt.pfrpg2e.utils.launch
import kotlin.js.Promise

/** The resolver's own "surprised at close range" distance; the stage dialog can override it. */
const val DEFAULT_STAGE_DISTANCE_FT = 60

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
    /** COMBAT-only: nothing to spawn for an RP, merchant, rumor, weather or lore result. */
    val canStage: Boolean
    val stageLabel: String
    val startDistanceFt: Int
    /** 0 renders the button disabled with a "curate creatures first" tooltip. */
    val creatureCount: Int
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
    private val game: Game,
    private val partyActor: CampingActor,
    private val category: EncounterCategory,
    private val regionName: String,
    private val resultText: String,
    /** Auto-seeded from the drawn table result when it referenced an actor; GM-editable. */
    private var manifest: RawEncounterManifest?,
    startDistanceFt: Int = DEFAULT_STAGE_DISTANCE_FT,
    spawnHidden: Boolean = false,
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
    /** The GM's staging edits live across re-opens of the stage dialog within this preview. */
    private var stageDistanceFt: Int = startDistanceFt
    private var stageHidden: Boolean = spawnHidden

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-accept" -> { close(); onAccept() }
            "km-reroll" -> { close(); onReroll() }
            "km-reject" -> { close(); onReject() }
            "km-convert-quest" -> rumor?.let { close(); onConvertToQuest(it) }
            "km-stage" -> {
                if (category != EncounterCategory.COMBAT) return
                ModifyEncounterStage(
                    game = game,
                    partyActor = partyActor,
                    initial = manifest,
                    startDistanceFt = stageDistanceFt,
                    spawnHidden = stageHidden,
                    onSaved = { updated, distance, hidden ->
                        // keep the WHOLE edited state so re-opening resumes the GM's curation
                        // instead of resurrecting the seed and the default distance
                        manifest = updated
                        stageDistanceFt = distance
                        stageHidden = hidden
                        render()
                    },
                ).launch()
            }
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
            canStage = category == EncounterCategory.COMBAT,
            stageLabel = t("camping.encounterStage"),
            startDistanceFt = stageDistanceFt,
            creatureCount = manifest.spawnCount(),
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

package at.posselt.pfrpg2e.camping.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.data.kingdom.settlements.Settlement
import at.posselt.pfrpg2e.kingdom.data.RawPcDowntimeProject
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeKind
import at.posselt.pfrpg2e.kingdom.downtime.prerequisiteMet
import at.posselt.pfrpg2e.kingdom.downtime.structuresFor
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import com.foundryvtt.core.ui
import io.github.uuidjs.uuid.v4
import js.core.Void
import js.objects.recordOf
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

/**
 * Creation dialog for PC downtime projects
 * (`docs/plans/2026-07-09-plan-downtime-projects.md` SS5, phase 3).
 *
 * Prerequisites validate against Settlement.constructedStructures -- the same list the Cleanse
 * Item structure gate reads, and explicitly NOT via InspectSettlement, which is a dialog and the
 * wrong seam for a data question. A refusal names the structures that would satisfy the kind, so
 * "you cannot" always comes with "here is what would work".
 */
@Suppress("unused")
@JsPlainObject
external interface AddDowntimeProjectContext : ValidatedHandlebarsContext {
    val formRows: Array<FormElementContext>
}

@Suppress("unused")
@JsPlainObject
external interface AddDowntimeProjectData {
    val pcActorUuid: String
    val kind: String
    val title: String
    val targetRef: String
    val settlementId: String
    val daysTotal: Int
    val dailyCostGp: Double
}

@JsExport
class AddDowntimeProjectDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?,
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("pcActorUuid")
            string("kind")
            string("title")
            string("targetRef", nullable = true)
            string("settlementId")
            int("daysTotal")
            double("dailyCostGp")
        }
    }
}

private data class DowntimeDraft(
    val pcActorUuid: String = "",
    val kind: String = "craft",
    val title: String = "",
    val targetRef: String = "",
    val settlementId: String = "",
    val daysTotal: Int = 4,
    val dailyCostGp: Double = 0.0,
)

/** Literal keys in a when -- dynamic key assembly is invisible to check_i18n_keys.py. */
fun downtimeKindLabel(kind: DowntimeKind?): String = when (kind) {
    DowntimeKind.CRAFT -> t("camping.downtime.kind.craft")
    DowntimeKind.RETRAIN -> t("camping.downtime.kind.retrain")
    DowntimeKind.EARN_INCOME -> t("camping.downtime.kind.earnIncome")
    DowntimeKind.RITUAL -> t("camping.downtime.kind.ritual")
    null -> t("camping.downtime.kind.unknown")
}

class AddDowntimeProject(
    private val pcs: List<Pair<String, String>>,
    private val settlements: List<Settlement>,
    private val afterSubmit: suspend (data: RawPcDowntimeProject) -> Unit,
) : FormApp<AddDowntimeProjectContext, AddDowntimeProjectData>(
    title = t("camping.downtime.addProject"),
    template = "components/forms/application-form.hbs",
    debug = true,
    dataModel = AddDowntimeProjectDataModel::class.js,
    id = "kmAddDowntimeProject",
    width = 480,
) {
    private var current = DowntimeDraft(
        pcActorUuid = pcs.firstOrNull()?.first ?: "",
        settlementId = settlements.firstOrNull()?.id ?: "",
    )

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> save()
        }
    }

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<AddDowntimeProjectContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        AddDowntimeProjectContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            formRows = formContext(
                Select(
                    name = "pcActorUuid",
                    label = t("camping.downtime.pc"),
                    value = current.pcActorUuid,
                    options = pcs.map { (uuid, name) -> SelectOption(name, uuid) },
                    stacked = false,
                ),
                Select(
                    name = "kind",
                    label = t("camping.downtime.kindLabel"),
                    value = current.kind,
                    options = listOf(
                        SelectOption(t("camping.downtime.kind.craft"), "craft"),
                        SelectOption(t("camping.downtime.kind.retrain"), "retrain"),
                        SelectOption(t("camping.downtime.kind.earnIncome"), "earnIncome"),
                        SelectOption(t("camping.downtime.kind.ritual"), "ritual"),
                    ),
                    stacked = false,
                ),
                TextInput(
                    name = "title",
                    label = t("applications.name"),
                    value = current.title,
                    required = true,
                    hideLabel = false,
                ),
                TextInput(
                    name = "targetRef",
                    label = t("camping.downtime.targetRef"),
                    value = current.targetRef,
                    help = t("camping.downtime.targetRefHelp"),
                    required = false,
                    hideLabel = false,
                ),
                Select(
                    name = "settlementId",
                    label = t("camping.downtime.settlement"),
                    value = current.settlementId,
                    options = settlements.map { SelectOption(it.name, it.id) },
                    stacked = false,
                ),
                NumberInput(
                    name = "daysTotal",
                    label = t("camping.downtime.daysTotal"),
                    value = current.daysTotal,
                    required = true,
                    hideLabel = false,
                ),
                NumberInput(
                    name = "dailyCostGp",
                    label = t("camping.downtime.dailyCostGp"),
                    value = current.dailyCostGp.toInt(),
                    help = t("camping.downtime.dailyCostGpHelp"),
                    required = false,
                    hideLabel = false,
                ),
            ),
        )
    }

    fun save(): Promise<Void> = buildPromise {
        val kind = DowntimeKind.fromValue(current.kind)
        val settlement = settlements.find { it.id == current.settlementId }
        when {
            kind == null || current.title.isBlank() || current.pcActorUuid.isBlank() -> {
                ui.notifications.error(t("camping.downtime.formIncomplete"))
            }

            current.daysTotal < 1 -> {
                ui.notifications.error(t("camping.downtime.daysTooShort"))
            }

            settlement == null -> {
                ui.notifications.error(t("camping.downtime.settlementMissing"))
            }

            !prerequisiteMet(kind, settlement.constructedStructures.map { it.name }.toSet()) -> {
                // the refusal NAMES what would satisfy the kind (plan SS10, checklist item 5)
                ui.notifications.error(
                    t(
                        "camping.downtime.prereqMissing",
                        recordOf(
                            "settlement" to settlement.name,
                            "structures" to structuresFor(kind).joinToString(", "),
                        ),
                    )
                )
            }

            else -> {
                close().await()
                val obj = js("{}").unsafeCast<RawPcDowntimeProject>()
                obj.id = v4()
                obj.pcActorUuid = current.pcActorUuid
                obj.kind = current.kind
                obj.targetRef = current.targetRef
                obj.title = current.title
                obj.settlementId = current.settlementId
                obj.daysTotal = current.daysTotal
                obj.daysRemaining = current.daysTotal
                obj.dailyCostGp = current.dailyCostGp.takeIf { it > 0 }
                obj.status = "inProgress"
                obj.pauseReason = null
                afterSubmit(obj)
            }
        }
        undefined
    }

    override fun onParsedSubmit(value: AddDowntimeProjectData): Promise<Void> = buildPromise {
        current = DowntimeDraft(
            pcActorUuid = value.pcActorUuid,
            kind = value.kind,
            title = value.title,
            targetRef = value.targetRef,
            settlementId = value.settlementId,
            daysTotal = value.daysTotal,
            dailyCostGp = value.dailyCostGp,
        )
        undefined
    }
}

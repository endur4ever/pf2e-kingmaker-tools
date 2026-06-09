package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.kingdom.data.ArmyDeploymentStatus
import at.posselt.pfrpg2e.kingdom.data.RawArmyDeployment
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

/** A PF2EArmy actor that can be deployed, with its display name + type cached. */
data class DeployableArmyOption(
    val uuid: String,
    val name: String,
    val typeValue: String,
    val typeLabel: String,
)

/** An active war threat a deployed army can be assigned to counter. */
data class DeployThreatOption(
    val id: String,
    val name: String,
)

@JsPlainObject
external interface DeployArmyFormData {
    var armyActorUuid: String
    var assignedThreatId: String?
}

@JsExport
class DeployArmyDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("armyActorUuid")
            string("assignedThreatId", nullable = true)
        }
    }
}

@JsPlainObject
external interface DeployArmyFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * Deploy a PF2EArmy actor to the war board (roadmap #12). Picks one of the
 * available [armies] and optionally assigns it to an active [threats] entry;
 * onSave receives the assembled [RawArmyDeployment] (status DEPLOYED).
 */
class DeployArmy(
    private val armies: List<DeployableArmyOption>,
    private val threats: List<DeployThreatOption>,
    private val onSave: (RawArmyDeployment) -> Unit,
) : FormApp<DeployArmyFormContext, DeployArmyFormData>(
    title = t("armyPressure.deployArmy"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = DeployArmyDataModel::class.js,
    width = 480,
    id = "kmDeployArmy",
) {
    private var data: DeployArmyFormData = DeployArmyFormData(
        armyActorUuid = armies.firstOrNull()?.uuid ?: "",
        assignedThreatId = null,
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<DeployArmyFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val armyOptions = armies.map { SelectOption("${it.name} (${it.typeLabel})", it.uuid) }
        val threatOptions = listOf(SelectOption(t("armyPressure.noThreatAssignment"), "")) +
            threats.map { SelectOption(it.name, it.id) }
        DeployArmyFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("armyPressure.deployArmy"),
                    formRows = listOf(
                        Select(
                            name = "armyActorUuid",
                            label = t("armyPressure.armyName"),
                            value = data.armyActorUuid,
                            options = armyOptions,
                            stacked = false,
                        ),
                        Select(
                            name = "assignedThreatId",
                            label = t("armyPressure.assignThreat"),
                            value = data.assignedThreatId ?: "",
                            options = threatOptions,
                            required = false,
                            stacked = false,
                            help = t("armyPressure.assignThreatHelp"),
                        ),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: DeployArmyFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                val army = armies.find { it.uuid == data.armyActorUuid } ?: return
                val deployment = RawArmyDeployment(
                    id = "deployment-${kotlin.js.Date().getTime().toLong()}",
                    armyActorUuid = army.uuid,
                    armyName = army.name,
                    armyType = army.typeLabel,
                    assignedThreatId = data.assignedThreatId?.ifBlank { null },
                    garrisonedSettlementId = null,
                    status = ArmyDeploymentStatus.DEPLOYED.value,
                    deployedTurn = 0,
                )
                close()
                onSave(deployment)
            }
        }
    }
}

package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.SectionsContext
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
import at.posselt.pfrpg2e.app.forms.TextArea
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_ACTIVE
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_DEFECTED
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_JOINED
import at.posselt.pfrpg2e.data.kingdom.RIVAL_STATUS_RETIRED
import at.posselt.pfrpg2e.kingdom.data.RawRivalCharterParty
import io.github.uuidjs.uuid.v4

@JsPlainObject
external interface RivalCharterFormData {
    var name: String
    var members: String
    var factionRef: String?
    var status: String
    var levelOffset: Int
    var currentHexKey: String?
    var agenda: String
    var pace: Int
    var pauseMovement: Boolean
    var aggressionThreshold: Int?
    var visibleToPlayers: Boolean
}

@JsExport
class RivalCharterDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("name")
            string("members")
            string("factionRef", nullable = true)
            string("status")
            int("levelOffset")
            string("currentHexKey", nullable = true)
            string("agenda")
            int("pace") { min = 0 }
            boolean("pauseMovement")
            int("aggressionThreshold", nullable = true)
            boolean("visibleToPlayers")
        }
    }
}

@JsPlainObject
external interface RivalCharterFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * Add or edit a rival charter party (plan section 4.3). Movement and scoreboard state -- position
 * countdown, aggression, arrivals, the idempotency stamps -- is NOT part of the form's rebuild:
 * an edit preserves every engine-owned field, the raw-field-wipe rule this repo has been bitten
 * by five times.
 */
@JsExport
class ModifyRivalCharterParty(
    private val existing: RawRivalCharterParty? = null,
    private val factions: List<String> = emptyList(),
    private val onSave: (RawRivalCharterParty) -> Unit,
) : FormApp<RivalCharterFormContext, RivalCharterFormData>(
    title = if (existing == null) t("kingdom.rivalCharter.dialog.add") else t("kingdom.rivalCharter.dialog.edit"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = RivalCharterDataModel::class.js,
    width = 520,
    id = "kmRivalCharterParty",
) {
    private var data: RivalCharterFormData = RivalCharterFormData(
        name = existing?.name ?: "",
        members = existing?.members ?: "",
        factionRef = existing?.factionRef,
        status = existing?.status ?: RIVAL_STATUS_ACTIVE,
        levelOffset = existing?.levelOffset ?: 0,
        currentHexKey = existing?.currentHexKey,
        agenda = existing?.agenda?.joinToString("\n") ?: "",
        pace = existing?.pace ?: 1,
        pauseMovement = existing?.pauseMovement ?: false,
        aggressionThreshold = existing?.aggressionThreshold,
        visibleToPlayers = existing?.visibleToPlayers ?: true,
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<RivalCharterFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        RivalCharterFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("kingdom.rivalCharter.title"),
                    formRows = listOf(
                        TextInput(name = "name", label = t("kingdom.rivalCharter.dialog.band"), value = data.name, stacked = false),
                        TextInput(name = "members", label = t("kingdom.rivalCharter.dialog.members"), value = data.members, stacked = false, required = false),
                        Select(
                            name = "factionRef",
                            label = t("kingdom.rivalCharter.dialog.faction"),
                            value = data.factionRef,
                            required = false,
                            options = factions.map { SelectOption(value = it, label = it) },
                            stacked = false,
                        ),
                        Select(
                            name = "status",
                            label = t("kingdom.rivalCharter.dialog.status"),
                            value = data.status,
                            options = listOf(
                                SelectOption(value = RIVAL_STATUS_ACTIVE, label = t("kingdom.rivalCharter.status.active")),
                                SelectOption(value = RIVAL_STATUS_DEFECTED, label = t("kingdom.rivalCharter.status.defected")),
                                SelectOption(value = RIVAL_STATUS_RETIRED, label = t("kingdom.rivalCharter.status.retired")),
                                SelectOption(value = RIVAL_STATUS_JOINED, label = t("kingdom.rivalCharter.status.joined")),
                            ),
                            stacked = false,
                        ),
                        NumberInput(name = "levelOffset", label = t("kingdom.rivalCharter.dialog.levelOffset"), value = data.levelOffset, stacked = false),
                        TextInput(name = "currentHexKey", label = t("kingdom.rivalCharter.dialog.currentHex"), value = data.currentHexKey ?: "", stacked = false, required = false),
                        TextArea(name = "agenda", label = t("kingdom.rivalCharter.dialog.agenda"), value = data.agenda, required = false),
                        NumberInput(name = "pace", label = t("kingdom.rivalCharter.dialog.pace"), value = data.pace, stacked = false),
                        CheckboxInput(name = "pauseMovement", label = t("kingdom.rivalCharter.dialog.pauseMovement"), value = data.pauseMovement, stacked = false),
                        NumberInput(name = "aggressionThreshold", label = t("kingdom.rivalCharter.dialog.aggressionThreshold"), value = data.aggressionThreshold ?: 0, stacked = false, required = false),
                        CheckboxInput(name = "visibleToPlayers", label = t("kingdom.rivalCharter.dialog.visibleToPlayers"), value = data.visibleToPlayers, stacked = false),
                    ),
                )
            )
        )
    }

    override fun onParsedSubmit(value: RivalCharterFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                val agendaKeys = data.agenda.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                val band = RawRivalCharterParty(
                    id = existing?.id ?: v4(),
                    name = data.name.ifBlank { t("kingdom.rivalCharter.title") },
                    factionRef = data.factionRef?.ifBlank { null },
                    status = data.status,
                    members = data.members.ifBlank { null },
                    levelOffset = data.levelOffset.takeIf { it != 0 },
                    currentHexKey = data.currentHexKey?.ifBlank { null },
                    agenda = agendaKeys.toTypedArray(),
                    // engine-owned fields survive an edit untouched
                    objectiveHexKey = existing?.objectiveHexKey,
                    objectiveKind = existing?.objectiveKind,
                    distanceToObjective = existing?.distanceToObjective,
                    pace = data.pace.coerceAtLeast(0),
                    pauseMovement = data.pauseMovement,
                    aggression = existing?.aggression,
                    aggressionThreshold = data.aggressionThreshold?.takeIf { it > 0 },
                    confrontationOffered = existing?.confrontationOffered,
                    arrivals = existing?.arrivals,
                    lastArrivalHexKey = existing?.lastArrivalHexKey,
                    lastArrivalTurn = existing?.lastArrivalTurn,
                    lastEncounterOfferTurn = existing?.lastEncounterOfferTurn,
                    rumoredObjectiveHexKey = existing?.rumoredObjectiveHexKey,
                    visibleToPlayers = data.visibleToPlayers,
                )
                // a band moved by hand loses its countdown, or it would arrive at the old objective from the new hex
                if (existing != null && band.currentHexKey != existing.currentHexKey) {
                    band.objectiveHexKey = null; band.objectiveKind = null; band.distanceToObjective = null
                }
                close()
                onSave(band)
            }
        }
    }
}

package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.data.kingdom.FACTION_ARCHETYPE_IDS
import at.posselt.pfrpg2e.kingdom.data.RawFactionAgenda
import at.posselt.pfrpg2e.kingdom.factionAgendaArchetypesById
import at.posselt.pfrpg2e.kingdom.localizeAgendaArchetype
import at.posselt.pfrpg2e.kingdom.localizeAgendaGoal
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

@JsPlainObject
external interface FactionAgendaFormData {
    var goalId: String
    var goalTitle: String
    var segments: Int
    var progress: Int
    var archetype: String
}

@JsExport
class FactionAgendaDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("goalId")
            string("goalTitle")
            int("segments")
            int("progress")
            string("archetype")
        }
    }
}

@JsPlainObject
external interface FactionAgendaFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * GM editor for one faction's agenda (plan 5.1's four actions in one dialog). Engine-owned
 * bookkeeping -- cooldowns, lastAdvancedTurn, targetFaction -- is carried by the CALLER from
 * the live row, never through this form.
 */
@JsExport
class ModifyFactionAgenda(
    private val factionName: String,
    private val initial: RawFactionAgenda?,
    private val onSave: (goalId: String, goalTitle: String, segments: Int, progress: Int, archetype: String) -> Unit,
) : FormApp<FactionAgendaFormContext, FactionAgendaFormData>(
    title = t("kingdom.factionAgenda.editTitle", recordOf("name" to factionName)),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = FactionAgendaDataModel::class.js,
    width = 480,
    id = "kmFactionAgenda",
) {
    private var data: FactionAgendaFormData = FactionAgendaFormData(
        goalId = initial?.goalId ?: "",
        goalTitle = initial?.goalTitle ?: "",
        segments = initial?.segments ?: 6,
        progress = initial?.progress ?: 0,
        archetype = initial?.archetype ?: FACTION_ARCHETYPE_IDS.first(),
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<FactionAgendaFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        // every goal from every archetype pool is offered: the GM may hand any faction any goal
        val goalOptions = factionAgendaArchetypesById().values
            .flatMap { it.goals.toList() }
            .distinct()
            .sorted()
            .map { SelectOption(value = it, label = localizeAgendaGoal(it, "")) }
        FactionAgendaFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("kingdom.factionAgenda.editTitle", recordOf("name" to factionName)),
                    formRows = listOf(
                        Select(
                            name = "goalId",
                            label = t("kingdom.factionAgenda.goalLabel"),
                            value = data.goalId,
                            options = goalOptions,
                            stacked = false,
                        ),
                        TextInput(
                            name = "goalTitle",
                            label = t("kingdom.factionAgenda.goalTitleLabel"),
                            value = data.goalTitle,
                            required = false,
                            stacked = false,
                            help = t("kingdom.factionAgenda.goalTitleHelp"),
                        ),
                        Select(
                            name = "segments",
                            label = t("kingdom.factionAgenda.segmentsLabel"),
                            value = data.segments.toString(),
                            options = listOf("4", "6", "8").map { SelectOption(value = it, label = it) },
                            stacked = false,
                        ),
                        NumberInput(
                            name = "progress",
                            label = t("kingdom.factionAgenda.progressLabel"),
                            value = data.progress,
                            stacked = false,
                        ),
                        Select(
                            name = "archetype",
                            label = t("kingdom.factionAgenda.archetypeLabel"),
                            value = data.archetype,
                            options = FACTION_ARCHETYPE_IDS.map {
                                SelectOption(value = it, label = localizeAgendaArchetype(it))
                            },
                            stacked = false,
                        ),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: FactionAgendaFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                close()
                onSave(
                    data.goalId,
                    data.goalTitle,
                    data.segments.coerceIn(1, 12),
                    data.progress.coerceAtLeast(0),
                    data.archetype,
                )
            }
        }
    }
}

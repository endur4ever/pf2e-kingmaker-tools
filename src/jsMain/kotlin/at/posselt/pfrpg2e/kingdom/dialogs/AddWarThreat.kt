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
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
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
external interface WarThreatFormData {
    var name: String
    var description: String
    var enemyFaction: String?
    var enemyFactionName: String?
    var maxEscalation: Int
    var eta: Int?
    var targetHexLocation: String?
    var pauseOnExpiry: Boolean
    var visibleToPlayers: Boolean
    var wanders: Boolean
}

@JsExport
class WarThreatDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("name")
            string("description")
            string("enemyFaction", nullable = true)
            string("enemyFactionName", nullable = true)
            int("maxEscalation") { min = 1 }
            int("eta", nullable = true)
            string("targetHexLocation", nullable = true)
            boolean("pauseOnExpiry")
            boolean("visibleToPlayers")
            boolean("wanders")
        }
    }
}

@JsPlainObject
external interface WarThreatFormContext : ValidatedHandlebarsContext, SectionsContext

/**
 * Create/edit a [RawWarThreat] (roadmap #12). [existing] non-null = edit mode;
 * onSave receives the assembled threat (id/status/escalation preserved on edit,
 * fresh on create).
 */
@JsExport
class AddWarThreat(
    private val existing: RawWarThreat? = null,
    private val prefillName: String? = null,
    private val prefillEnemyFaction: String? = null,
    /** Names of the kingdom's groups, for the faction link dropdown. */
    private val factions: List<String> = emptyList(),
    private val onSave: (RawWarThreat) -> Unit,
) : FormApp<WarThreatFormContext, WarThreatFormData>(
    title = if (existing == null) t("armyPressure.addThreat") else t("armyPressure.editThreat"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = WarThreatDataModel::class.js,
    width = 480,
    id = "kmWarThreat",
) {
    // In create mode (existing == null) the optional prefills seed the faction context
    // when the dialog is opened from a faction-standing threshold offer.
    private var data: WarThreatFormData = WarThreatFormData(
        name = existing?.name ?: prefillName ?: "",
        description = existing?.description ?: "",
        enemyFaction = existing?.enemyFaction ?: prefillEnemyFaction,
        // A threshold-crossing offer names the faction it came from, so a war started that way is
        // linked from the outset without the GM picking it again.
        enemyFactionName = existing?.enemyFactionName
            ?: prefillEnemyFaction?.takeIf { it in factions },
        maxEscalation = existing?.maxEscalation ?: 3,
        eta = existing?.eta,
        targetHexLocation = existing?.targetHexLocation,
        pauseOnExpiry = existing?.pauseOnExpiry ?: false,
        visibleToPlayers = existing?.visibleToPlayers ?: true,
        wanders = existing?.wanders ?: false,
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<WarThreatFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        WarThreatFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("armyPressure.threats"),
                    formRows = listOf(
                        TextInput(name = "name", label = t("armyPressure.threatName"), value = data.name, stacked = false),
                        TextInput(name = "description", label = t("armyPressure.threatDescription"), value = data.description, required = false, stacked = false),
                        TextInput(name = "enemyFaction", label = t("armyPressure.enemyFaction"), value = data.enemyFaction ?: "", required = false, stacked = false),
                        Select(
                            name = "enemyFactionName",
                            label = t("armyPressure.linkedFaction"),
                            value = data.enemyFactionName ?: "",
                            // The stored name is kept as an option even when it no longer matches a
                            // group, so opening the dialog on a threat whose faction was renamed
                            // shows the stale link instead of silently clearing it on save.
                            options = listOf(SelectOption(label = t("armyPressure.noFactionLink"), value = "")) +
                                (factions + listOfNotNull(data.enemyFactionName?.takeIf { it.isNotBlank() && it !in factions }))
                                    .map { SelectOption(label = it, value = it) },
                            required = false,
                            stacked = false,
                            help = t("armyPressure.linkedFactionHelp"),
                        ),
                        NumberInput(name = "maxEscalation", label = t("armyPressure.maxEscalation"), value = data.maxEscalation, stacked = false),
                        NumberInput(name = "eta", label = t("armyPressure.eta"), value = data.eta ?: 0, stacked = false, help = t("armyPressure.etaHelp")),
                        TextInput(name = "targetHexLocation", label = t("armyPressure.target"), value = data.targetHexLocation ?: "", required = false, stacked = false),
                        CheckboxInput(name = "pauseOnExpiry", label = t("armyPressure.pauseOnExpiry"), value = data.pauseOnExpiry, help = t("armyPressure.pauseOnExpiryHelp"), stacked = false),
                        CheckboxInput(name = "visibleToPlayers", label = t("armyPressure.visibleToPlayers"), value = data.visibleToPlayers, help = t("armyPressure.visibleToPlayersHelp"), stacked = false),
                        CheckboxInput(name = "wanders", label = t("armyPressure.wanders"), value = data.wanders, help = t("armyPressure.wandersHelp"), stacked = false),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: WarThreatFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                val etaValue = data.eta?.let { if (it <= 0) null else it }
                val threat = RawWarThreat(
                    id = existing?.id ?: "threat-${kotlin.js.Date().getTime().toLong()}",
                    name = data.name.ifBlank { t("armyPressure.threats") },
                    description = data.description,
                    enemyFaction = data.enemyFaction?.ifBlank { null },
                    enemyFactionName = data.enemyFactionName?.ifBlank { null },
                    escalationLevel = existing?.escalationLevel ?: 0,
                    maxEscalation = data.maxEscalation.coerceAtLeast(1),
                    eta = etaValue,
                    targetSettlementSceneId = existing?.targetSettlementSceneId,
                    targetHexLocation = data.targetHexLocation?.ifBlank { null },
                    linkedQuestId = existing?.linkedQuestId,
                    linkedEventId = existing?.linkedEventId,
                    pauseOnExpiry = data.pauseOnExpiry,
                    status = existing?.status ?: "active",
                    triggeredTurn = existing?.triggeredTurn,
                    visibleToPlayers = data.visibleToPlayers,
                    offerConsumed = existing?.offerConsumed,
                )
                // mobility fields are not part of the form's rebuild: preserve an edited threat's
                // position and per-turn guard, or a save would silently reset a wandering threat
                threat.wanders = data.wanders
                threat.currentHexLocation = existing?.currentHexLocation ?: threat.targetHexLocation
                threat.migrationConsumedTurn = existing?.migrationConsumedTurn
                close()
                onSave(threat)
            }
        }
    }
}

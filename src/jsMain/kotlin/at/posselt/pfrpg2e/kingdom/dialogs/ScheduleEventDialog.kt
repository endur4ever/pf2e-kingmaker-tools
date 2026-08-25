package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.TextArea
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawScheduledPressure
import at.posselt.pfrpg2e.kingdom.getEvents
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import io.github.uuidjs.uuid.v4
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.get
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

/**
 * The authoring dialog (plan SS6): a preset select fills every field for the three house rules
 * the feature exists for; everything stays editable after. Days are entered RELATIVE ("in N
 * days") and converted to world day numbers on save -- the GM thinks in countdowns, the engine
 * in day numbers.
 *
 * All payload/resolve rows render always (the schema is fixed); save keeps only the refs that
 * match the chosen kinds, so a leftover selection in an inapplicable row cannot leak into data.
 */
@Suppress("unused")
@JsPlainObject
external interface ScheduleEventContext : ValidatedHandlebarsContext {
    val formRows: Array<FormElementContext>
}

@Suppress("unused")
@JsPlainObject
external interface ScheduleEventData {
    val preset: String
    val name: String
    val startInDays: Int
    val recurrence: String
    val endInDays: Int
    val payloadKind: String
    val payloadEventId: String
    val payloadClockId: String
    val payloadBeatText: String
    val payloadEncounterId: String
    val resolveConditionKind: String
    val resolveConditionRef: String
    val active: Boolean
}

@JsExport
class ScheduleEventDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?,
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("preset")
            string("name")
            int("startInDays")
            string("recurrence")
            int("endInDays")
            string("payloadKind")
            string("payloadEventId", nullable = true)
            string("payloadClockId", nullable = true)
            string("payloadBeatText", nullable = true)
            string("payloadEncounterId", nullable = true)
            string("resolveConditionKind")
            string("resolveConditionRef", nullable = true)
            boolean("active")
        }
    }
}

private data class ScheduleDraft(
    val id: String,
    val preset: String = "custom",
    val name: String = "",
    val startInDays: Int = 7,
    val recurrence: String = "none",
    val endInDays: Int = 0,
    val payloadKind: String = "postBeat",
    val payloadEventId: String? = null,
    val payloadClockId: String? = null,
    val payloadBeatText: String = "",
    val payloadEncounterId: String = "",
    val resolveConditionKind: String = "none",
    val resolveConditionRef: String? = null,
    val active: Boolean = true,
)

class ScheduleEventDialog(
    private val kingdom: KingdomData,
    private val currentDay: Int,
    existing: RawScheduledPressure? = null,
    private val afterSubmit: suspend (data: RawScheduledPressure) -> Unit,
) : FormApp<ScheduleEventContext, ScheduleEventData>(
    title = if (existing == null) t("kingdom.deadlines.addSchedule") else t("kingdom.deadlines.editSchedule"),
    template = "components/forms/application-form.hbs",
    debug = true,
    dataModel = ScheduleEventDataModel::class.js,
    id = "kmScheduleEvent",
    width = 520,
) {
    private var current: ScheduleDraft = existing?.let {
        ScheduleDraft(
            id = it.id,
            name = it.name,
            startInDays = it.startDay - currentDay,
            recurrence = it.recurrence,
            endInDays = it.endDay?.let { end -> end - currentDay } ?: 0,
            payloadKind = it.payloadKind,
            payloadEventId = it.payloadEventId,
            payloadClockId = it.payloadClockId,
            payloadBeatText = it.payloadBeatText ?: "",
            payloadEncounterId = it.payloadEncounterId ?: "",
            resolveConditionKind = it.resolveConditionKind ?: "none",
            resolveConditionRef = it.resolveConditionRef,
            active = it.active,
        )
    } ?: ScheduleDraft(id = v4())

    init {
        isFormValid = existing != null
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> save()
        }
    }

    /**
     * Plan SS6 verbatim: Troll Sightings (weekly spawnEvent until the linked threat resolves),
     * Season of Bloom (daily spawnEvent through the season -- 90 days as the editable
     * approximation), Stag Lord deadline (one-shot beat in 90 days until the quest completes).
     * Ref guesses match by name and fall back to the first candidate; a miss leaves the select
     * empty for the GM.
     */
    private fun applyPreset(preset: String): ScheduleDraft {
        val threats = kingdom.warThreats ?: emptyArray()
        val quests = kingdom.quests ?: emptyArray()
        val events = kingdom.getEvents()
        return when (preset) {
            "trollSightings" -> current.copy(
                preset = preset,
                name = t("kingdom.deadlines.preset.trollSightingsName"),
                startInDays = 7,
                recurrence = "weekly",
                endInDays = 0,
                payloadKind = "spawnEvent",
                payloadEventId = events.firstOrNull { "troll" in it.name.lowercase() }?.id
                    ?: events.firstOrNull()?.id,
                resolveConditionKind = "threatResolved",
                resolveConditionRef = threats.firstOrNull {
                    "hargulka" in it.name.lowercase() || "troll" in it.name.lowercase()
                }?.id ?: threats.firstOrNull()?.id,
            )

            "cultEvents" -> current.copy(
                preset = preset,
                name = t("kingdom.deadlines.preset.cultEventsName"),
                startInDays = 1,
                recurrence = "daily",
                endInDays = 90,
                payloadKind = "spawnEvent",
                payloadEventId = events.firstOrNull { "cult" in it.name.lowercase() }?.id
                    ?: events.firstOrNull()?.id,
                resolveConditionKind = "none",
                resolveConditionRef = null,
            )

            "stagLordDeadline" -> current.copy(
                preset = preset,
                name = t("kingdom.deadlines.preset.stagLordName"),
                startInDays = 90,
                recurrence = "none",
                endInDays = 0,
                payloadKind = "postBeat",
                payloadBeatText = t("kingdom.deadlines.preset.stagLordBeat"),
                resolveConditionKind = "questCompleted",
                resolveConditionRef = quests.firstOrNull {
                    it.status == "active" && "stag" in it.title.lowercase()
                }?.id ?: quests.firstOrNull { it.status == "active" }?.id,
            )

            else -> current.copy(preset = preset)
        }
    }

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<ScheduleEventContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val emptyOption = SelectOption(t("kingdom.deadlines.noSelection"), "")
        val eventOptions = listOf(emptyOption) + kingdom.getEvents().map { SelectOption(it.name, it.id) }
        val clockOptions = listOf(emptyOption) + kingdom.campaignClocks.map { SelectOption(it.label, it.id) }
        val questOptions = listOf(emptyOption) + (kingdom.quests ?: emptyArray())
            .filter { it.status == "active" }
            .map { SelectOption(it.title, it.id) }
        val threatOptions = listOf(emptyOption) + (kingdom.warThreats ?: emptyArray())
            .map { SelectOption(it.name, it.id) }
        val refOptions = when (current.resolveConditionKind) {
            "questCompleted" -> questOptions
            "threatResolved" -> threatOptions
            else -> listOf(emptyOption)
        }
        ScheduleEventContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            formRows = formContext(
                Select(
                    name = "preset",
                    label = t("kingdom.deadlines.presetLabel"),
                    value = current.preset,
                    options = listOf(
                        SelectOption(t("kingdom.deadlines.preset.custom"), "custom"),
                        SelectOption(t("kingdom.deadlines.preset.trollSightingsName"), "trollSightings"),
                        SelectOption(t("kingdom.deadlines.preset.cultEventsName"), "cultEvents"),
                        SelectOption(t("kingdom.deadlines.preset.stagLordName"), "stagLordDeadline"),
                    ),
                    stacked = false,
                ),
                TextInput(
                    name = "name",
                    label = t("applications.name"),
                    value = current.name,
                    required = true,
                    hideLabel = false,
                ),
                NumberInput(
                    name = "startInDays",
                    label = t("kingdom.deadlines.startInDays"),
                    value = current.startInDays,
                    required = true,
                    hideLabel = false,
                ),
                Select(
                    name = "recurrence",
                    label = t("kingdom.deadlines.recurrenceLabel"),
                    value = current.recurrence,
                    options = listOf(
                        SelectOption(t("kingdom.deadlines.recurrence.none"), "none"),
                        SelectOption(t("kingdom.deadlines.recurrence.daily"), "daily"),
                        SelectOption(t("kingdom.deadlines.recurrence.weekly"), "weekly"),
                        SelectOption(t("kingdom.deadlines.recurrence.monthly"), "monthly"),
                    ),
                    stacked = false,
                ),
                NumberInput(
                    name = "endInDays",
                    label = t("kingdom.deadlines.endInDays"),
                    value = current.endInDays,
                    help = t("kingdom.deadlines.endInDaysHelp"),
                    required = true,
                    hideLabel = false,
                ),
                Select(
                    name = "payloadKind",
                    label = t("kingdom.deadlines.payloadKindLabel"),
                    value = current.payloadKind,
                    options = listOf(
                        SelectOption(t("kingdom.deadlines.payload.postBeat"), "postBeat"),
                        SelectOption(t("kingdom.deadlines.payload.spawnEvent"), "spawnEvent"),
                        SelectOption(t("kingdom.deadlines.payload.spawnEncounter"), "spawnEncounter"),
                        SelectOption(t("kingdom.deadlines.payload.advanceClock"), "advanceClock"),
                    ),
                    stacked = false,
                ),
                TextArea(
                    name = "payloadBeatText",
                    label = t("kingdom.deadlines.beatTextLabel"),
                    value = current.payloadBeatText,
                    help = t("kingdom.deadlines.beatTextHelp"),
                    required = false,
                    hideLabel = false,
                ),
                Select(
                    name = "payloadEventId",
                    label = t("kingdom.deadlines.eventRefLabel"),
                    value = current.payloadEventId ?: "",
                    options = eventOptions,
                    required = false,
                    stacked = false,
                ),
                Select(
                    name = "payloadClockId",
                    label = t("kingdom.deadlines.clockRefLabel"),
                    value = current.payloadClockId ?: "",
                    options = clockOptions,
                    required = false,
                    stacked = false,
                ),
                TextInput(
                    name = "payloadEncounterId",
                    label = t("kingdom.deadlines.encounterRefLabel"),
                    value = current.payloadEncounterId,
                    required = false,
                    hideLabel = false,
                ),
                Select(
                    name = "resolveConditionKind",
                    label = t("kingdom.deadlines.resolveKindLabel"),
                    value = current.resolveConditionKind,
                    options = listOf(
                        SelectOption(t("kingdom.deadlines.resolve.none"), "none"),
                        SelectOption(t("kingdom.deadlines.resolve.questCompleted"), "questCompleted"),
                        SelectOption(t("kingdom.deadlines.resolve.threatResolved"), "threatResolved"),
                    ),
                    help = t("kingdom.deadlines.resolveKindHelp"),
                    stacked = false,
                ),
                Select(
                    name = "resolveConditionRef",
                    label = t("kingdom.deadlines.resolveRefLabel"),
                    value = current.resolveConditionRef ?: "",
                    options = refOptions,
                    required = false,
                    stacked = false,
                ),
                CheckboxInput(
                    name = "active",
                    label = t("applications.enable"),
                    value = current.active,
                    required = false,
                    hideLabel = false,
                ),
            ),
        )
    }

    fun save(): Promise<Void> = buildPromise {
        if (isValid()) {
            close().await()
            afterSubmit(current.toRaw(currentDay))
        }
        undefined
    }

    override fun onParsedSubmit(value: ScheduleEventData): Promise<Void> = buildPromise {
        val presetChanged = value.preset != current.preset && value.preset != "custom"
        current = if (presetChanged) {
            applyPreset(value.preset)
        } else {
            current.copy(
                preset = value.preset,
                name = value.name,
                startInDays = value.startInDays,
                recurrence = value.recurrence,
                endInDays = value.endInDays,
                payloadKind = value.payloadKind,
                payloadEventId = value.payloadEventId.takeIf { it.isNotBlank() },
                payloadClockId = value.payloadClockId.takeIf { it.isNotBlank() },
                payloadBeatText = value.payloadBeatText,
                payloadEncounterId = value.payloadEncounterId,
                resolveConditionKind = value.resolveConditionKind,
                resolveConditionRef = value.resolveConditionRef.takeIf { it.isNotBlank() },
                active = value.active,
            )
        }
        undefined
    }
}

private fun ScheduleDraft.toRaw(currentDay: Int): RawScheduledPressure {
    val obj = js("{}").unsafeCast<RawScheduledPressure>()
    obj.id = id
    obj.name = name
    obj.startDay = currentDay + startInDays
    obj.recurrence = recurrence
    obj.endDay = endInDays.takeIf { it > 0 }?.let { currentDay + it }
    obj.payloadKind = payloadKind
    // only the ref matching the chosen kind survives -- a leftover selection cannot leak
    obj.payloadEventId = payloadEventId.takeIf { payloadKind == "spawnEvent" }
    obj.payloadClockId = payloadClockId.takeIf { payloadKind == "advanceClock" }
    obj.payloadBeatText = payloadBeatText.takeIf { payloadKind == "postBeat" && it.isNotBlank() }
    obj.payloadEncounterId = payloadEncounterId.takeIf { payloadKind == "spawnEncounter" && it.isNotBlank() }
    obj.escalationCount = 0
    obj.resolveConditionKind = resolveConditionKind.takeIf { it != "none" }
    obj.resolveConditionRef = resolveConditionRef.takeIf { resolveConditionKind != "none" }
    obj.active = active
    return obj
}

package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.Section
import at.posselt.pfrpg2e.app.forms.SectionsContext
import at.posselt.pfrpg2e.app.forms.TextArea
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.kingdom.data.RawInfluenceEncounter
import at.posselt.pfrpg2e.kingdom.data.RawInfluenceTrait
import at.posselt.pfrpg2e.kingdom.data.RawResearchProject
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemCheck
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemThreshold
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
 * Line-format parsers for the repeating collections. The form toolkit renders static rows, so
 * "repeating rows" are one TextArea per collection, one entry per line -- "Diplomacy | 20",
 * "4 | She reveals the assassin's name", "flattery | -1". A malformed line BLOCKS the save with
 * an error naming it, never silently drops.
 */
fun parseCheckLines(text: String): List<RawSubsystemCheck>? =
    text.lines().map { it.trim() }.filter { it.isNotBlank() }.map { line ->
        val parts = line.split("|").map { it.trim() }
        val dc = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (parts[0].isBlank()) return null
        // pipes past the second are the note's own: "diplomacy | 20 | mention X | not Y"
        val note = parts.drop(2).joinToString(" | ").takeIf { it.isNotBlank() }
        RawSubsystemCheck(skill = parts[0], dc = dc, revealed = false, note = note)
    }

fun parseThresholdLines(text: String): List<RawSubsystemThreshold>? =
    text.lines().map { it.trim() }.filter { it.isNotBlank() }.map { line ->
        val parts = line.split("|").map { it.trim() }
        val points = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val effect = parts.drop(1).joinToString(" | ").takeIf { it.isNotBlank() } ?: return null
        RawSubsystemThreshold(points = points, effect = effect, offerConsumed = false, revealedToPlayers = false)
    }

fun parseTraitLines(text: String): List<RawInfluenceTrait>? =
    text.lines().map { it.trim() }.filter { it.isNotBlank() }.map { line ->
        val parts = line.split("|").map { it.trim() }
        val delta = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (parts[0].isBlank()) return null
        val note = parts.drop(2).joinToString(" | ").takeIf { it.isNotBlank() }
        RawInfluenceTrait(label = parts[0], delta = delta, note = note)
    }

fun checksToLines(rows: Array<RawSubsystemCheck>?): String =
    (rows ?: emptyArray()).joinToString("\n") { listOfNotNull(it.skill, it.dc.toString(), it.note).joinToString(" | ") }

fun thresholdsToLines(rows: Array<RawSubsystemThreshold>?): String =
    (rows ?: emptyArray()).joinToString("\n") { "${it.points} | ${it.effect}" }

fun traitsToLines(rows: Array<RawInfluenceTrait>?): String =
    (rows ?: emptyArray()).joinToString("\n") { listOfNotNull(it.label, it.delta.toString(), it.note).joinToString(" | ") }

@JsPlainObject
external interface InfluenceEncounterFormData {
    var name: String
    var npcName: String
    var description: String
    var discoveries: String
    var influenceSkills: String
    var thresholds: String
    var resistances: String
    var weaknesses: String
}

@JsExport
class InfluenceEncounterDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("name")
            string("npcName")
            string("description")
            string("discoveries")
            string("influenceSkills")
            string("thresholds")
            string("resistances")
            string("weaknesses")
        }
    }
}

@JsPlainObject
external interface SubsystemFormContext : ValidatedHandlebarsContext, SectionsContext

/** Add/edit an influence encounter. GM-only; the tracker gates before opening. */
@JsExport
class AddInfluenceEncounter(
    private val initial: RawInfluenceEncounter?,
    private val onSave: (encounter: RawInfluenceEncounter) -> Unit,
) : FormApp<SubsystemFormContext, InfluenceEncounterFormData>(
    title = t(if (initial == null) "subsystems.influence.add" else "subsystems.influence.edit"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = InfluenceEncounterDataModel::class.js,
    width = 560,
    id = "kmAddInfluence",
) {
    private var data: InfluenceEncounterFormData = InfluenceEncounterFormData(
        name = initial?.name ?: "",
        npcName = initial?.npcName ?: "",
        description = initial?.description ?: "",
        discoveries = checksToLines(initial?.discoveries),
        influenceSkills = checksToLines(initial?.influenceSkills),
        thresholds = thresholdsToLines(initial?.thresholds),
        resistances = traitsToLines(initial?.resistances),
        weaknesses = traitsToLines(initial?.weaknesses),
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<SubsystemFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        SubsystemFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("subsystems.influence.title"),
                    formRows = listOf(
                        TextInput(name = "name", label = t("subsystems.dialog.name"), value = data.name, stacked = false),
                        TextInput(name = "npcName", label = t("subsystems.dialog.npc"), value = data.npcName, stacked = false),
                        TextInput(name = "description", label = t("subsystems.dialog.description"), value = data.description, required = false, stacked = false),
                        TextArea(name = "discoveries", label = t("subsystems.dialog.discoveries"), value = data.discoveries, required = false, help = t("subsystems.dialog.checkLineHelp")),
                        TextArea(name = "influenceSkills", label = t("subsystems.dialog.influenceSkills"), value = data.influenceSkills, required = false, help = t("subsystems.dialog.checkLineHelp")),
                        TextArea(name = "thresholds", label = t("subsystems.dialog.thresholds"), value = data.thresholds, required = false, help = t("subsystems.dialog.thresholdLineHelp")),
                        TextArea(name = "resistances", label = t("subsystems.dialog.resistances"), value = data.resistances, required = false, help = t("subsystems.dialog.traitLineHelp")),
                        TextArea(name = "weaknesses", label = t("subsystems.dialog.weaknesses"), value = data.weaknesses, required = false, help = t("subsystems.dialog.traitLineHelp")),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: InfluenceEncounterFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                val discoveries = parseCheckLines(data.discoveries)
                val skills = parseCheckLines(data.influenceSkills)
                val thresholds = parseThresholdLines(data.thresholds)
                val resistances = parseTraitLines(data.resistances)
                val weaknesses = parseTraitLines(data.weaknesses)
                if (data.name.isBlank() || data.npcName.isBlank() ||
                    discoveries == null || skills == null || thresholds == null ||
                    resistances == null || weaknesses == null
                ) {
                    ui.notifications.error(t("subsystems.dialog.badLines"))
                    return
                }
                close()
                onSave(RawInfluenceEncounter(
                    id = initial?.id ?: v4(),
                    name = data.name,
                    npcName = data.npcName,
                    description = data.description,
                    level = initial?.level,
                    influencePoints = initial?.influencePoints ?: 0,
                    status = initial?.status ?: "active",
                    discoveries = mergeRevealed(discoveries, initial?.discoveries),
                    influenceSkills = mergeRevealed(skills, initial?.influenceSkills),
                    thresholds = mergeThresholdState(thresholds, initial?.thresholds),
                    resistances = resistances.toTypedArray(),
                    weaknesses = weaknesses.toTypedArray(),
                    // engine-owned state carried explicitly: an edit must not zero the pool,
                    // the roster, or the log
                    participants = initial?.participants,
                    checkLog = initial?.checkLog,
                    visibleToPlayers = initial?.visibleToPlayers,
                    createdAt = initial?.createdAt ?: kotlin.js.Date.now(),
                    updatedAt = kotlin.js.Date.now(),
                ))
            }
        }
    }
}

/** Re-parsed rows keep their reveal state when they match an existing row by position. */
fun mergeRevealed(parsed: List<RawSubsystemCheck>, existing: Array<RawSubsystemCheck>?): Array<RawSubsystemCheck> =
    parsed.mapIndexed { index, row ->
        val prior = existing?.getOrNull(index)
        if (prior != null && prior.skill == row.skill && prior.dc == row.dc) {
            RawSubsystemCheck.copy(row, revealed = prior.revealed)
        } else row
    }.toTypedArray()

fun mergeThresholdState(parsed: List<RawSubsystemThreshold>, existing: Array<RawSubsystemThreshold>?): Array<RawSubsystemThreshold> =
    parsed.mapIndexed { index, row ->
        val prior = existing?.getOrNull(index)
        if (prior != null && prior.points == row.points) {
            RawSubsystemThreshold.copy(
                row,
                offerConsumed = prior.offerConsumed,
                // a reworded effect is a NEW secret: reveal state follows the text, not the slot
                revealedToPlayers = if (prior.effect == row.effect) prior.revealedToPlayers else false,
            )
        } else row
    }.toTypedArray()

@JsPlainObject
external interface ResearchProjectFormData {
    var name: String
    var description: String
    var libraryName: String
    var maxPoints: String
    var checks: String
    var thresholds: String
}

@JsExport
class ResearchProjectDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            string("name")
            string("description")
            string("libraryName")
            string("maxPoints")
            string("checks")
            string("thresholds")
        }
    }
}

/** Add/edit a research project; same line-format collections as the influence dialog. */
@JsExport
class AddResearchProject(
    private val initial: RawResearchProject?,
    private val onSave: (project: RawResearchProject) -> Unit,
) : FormApp<SubsystemFormContext, ResearchProjectFormData>(
    title = t(if (initial == null) "subsystems.research.add" else "subsystems.research.edit"),
    template = "components/forms/application-form.hbs",
    debug = false,
    dataModel = ResearchProjectDataModel::class.js,
    width = 560,
    id = "kmAddResearch",
) {
    private var data: ResearchProjectFormData = ResearchProjectFormData(
        name = initial?.name ?: "",
        description = initial?.description ?: "",
        libraryName = initial?.libraryName ?: "",
        maxPoints = initial?.maxResearchPoints?.toString() ?: "",
        checks = checksToLines(initial?.checks),
        thresholds = thresholdsToLines(initial?.thresholds),
    )

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<SubsystemFormContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        SubsystemFormContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            sections = formContext(
                Section(
                    legend = t("subsystems.research.title"),
                    formRows = listOf(
                        TextInput(name = "name", label = t("subsystems.dialog.name"), value = data.name, stacked = false),
                        TextInput(name = "description", label = t("subsystems.dialog.description"), value = data.description, required = false, stacked = false),
                        TextInput(name = "libraryName", label = t("subsystems.dialog.library"), value = data.libraryName, required = false, stacked = false),
                        TextInput(name = "maxPoints", label = t("subsystems.dialog.maxPoints"), value = data.maxPoints, required = false, stacked = false),
                        TextArea(name = "checks", label = t("subsystems.dialog.checks"), value = data.checks, required = false, help = t("subsystems.dialog.checkLineHelp")),
                        TextArea(name = "thresholds", label = t("subsystems.dialog.thresholds"), value = data.thresholds, required = false, help = t("subsystems.dialog.thresholdLineHelp")),
                    )
                )
            )
        )
    }

    override fun onParsedSubmit(value: ResearchProjectFormData): Promise<Void> = buildPromise {
        data = value
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "km-save" -> {
                val checks = parseCheckLines(data.checks)
                val thresholds = parseThresholdLines(data.thresholds)
                val maxRaw = data.maxPoints.trim()
                val maxPoints = maxRaw.takeIf { it.isNotBlank() }?.toIntOrNull()?.takeIf { it > 0 }
                val maxInvalid = maxRaw.isNotBlank() && maxPoints == null
                if (data.name.isBlank() || checks == null || thresholds == null || maxInvalid) {
                    ui.notifications.error(t("subsystems.dialog.badLines"))
                    return
                }
                close()
                onSave(RawResearchProject(
                    id = initial?.id ?: v4(),
                    name = data.name,
                    description = data.description,
                    libraryName = data.libraryName.ifBlank { null },
                    libraryLevel = initial?.libraryLevel,
                    researchPoints = initial?.researchPoints ?: 0,
                    maxResearchPoints = maxPoints,
                    status = initial?.status ?: "active",
                    checks = mergeRevealed(checks, initial?.checks),
                    thresholds = mergeThresholdState(thresholds, initial?.thresholds),
                    checkLog = initial?.checkLog,
                    sourceEventId = initial?.sourceEventId,
                    visibleToPlayers = initial?.visibleToPlayers,
                    createdAt = initial?.createdAt ?: kotlin.js.Date.now(),
                    updatedAt = kotlin.js.Date.now(),
                ))
            }
        }
    }
}

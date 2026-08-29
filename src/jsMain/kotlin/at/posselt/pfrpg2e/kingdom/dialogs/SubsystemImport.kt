package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.awaitablePrompt
import at.posselt.pfrpg2e.app.forms.TextArea
import at.posselt.pfrpg2e.app.forms.formContext
import at.posselt.pfrpg2e.kingdom.data.RawInfluenceEncounter
import at.posselt.pfrpg2e.kingdom.data.RawInfluenceTrait
import at.posselt.pfrpg2e.kingdom.data.RawResearchProject
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemCheck
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemStore
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemThreshold
import at.posselt.pfrpg2e.kingdom.updateSubsystemStore
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui
import io.github.uuidjs.uuid.v4
import js.objects.recordOf
import kotlin.js.jsTypeOf

/**
 * JSON import for the subsystem trackers (plan section 9): the module ships an EMPTY store, the
 * GM pastes published stats once. All-or-nothing on parse -- one malformed entry blocks the
 * whole paste with an error, the badLines philosophy, so a typo can't half-import a book page.
 * Ids and timestamps are assigned here; imported rows always start unrevealed and GM-only.
 */

private fun dynStr(v: dynamic): String? = if (jsTypeOf(v) == "string") v.unsafeCast<String>() else null
private fun dynInt(v: dynamic): Int? = if (jsTypeOf(v) == "number") v.unsafeCast<Double>().toInt() else null
private fun dynArray(v: dynamic): Array<dynamic>? =
    if (v != null && v is Array<*>) v.unsafeCast<Array<dynamic>>() else null

private fun parseChecks(v: dynamic): List<RawSubsystemCheck>? {
    val rows = dynArray(v) ?: return if (v == null || v == undefined) emptyList() else null
    return rows.map { row ->
        val skill = dynStr(row?.skill)?.takeIf { it.isNotBlank() } ?: return null
        val dc = dynInt(row?.dc) ?: return null
        RawSubsystemCheck(skill = skill, dc = dc, revealed = false, note = dynStr(row?.note))
    }
}

private fun parseThresholds(v: dynamic): List<RawSubsystemThreshold>? {
    val rows = dynArray(v) ?: return if (v == null || v == undefined) emptyList() else null
    return rows.map { row ->
        val points = dynInt(row?.points) ?: return null
        val effect = dynStr(row?.effect)?.takeIf { it.isNotBlank() } ?: return null
        RawSubsystemThreshold(points = points, effect = effect, offerConsumed = false, revealedToPlayers = false)
    }
}

private fun parseTraits(v: dynamic): List<RawInfluenceTrait>? {
    val rows = dynArray(v) ?: return if (v == null || v == undefined) emptyList() else null
    return rows.map { row ->
        val label = dynStr(row?.label)?.takeIf { it.isNotBlank() } ?: return null
        val delta = dynInt(row?.delta) ?: return null
        RawInfluenceTrait(label = label, delta = delta, note = dynStr(row?.note))
    }
}

/** Null on ANY malformed entry; ids/timestamps come from the caller so the parse stays testable. */
fun parseSubsystemImport(text: String, now: Double, newId: () -> String): RawSubsystemStore? {
    val parsed = runCatching { JSON.parse<dynamic>(text) }.getOrNull() ?: return null
    if (jsTypeOf(parsed) != "object") return null
    val encounters = dynArray(parsed.influenceEncounters)?.map { enc ->
        RawInfluenceEncounter(
            id = newId(),
            name = dynStr(enc?.name)?.takeIf { it.isNotBlank() } ?: return null,
            npcName = dynStr(enc?.npcName) ?: "",
            description = dynStr(enc?.description) ?: "",
            level = dynInt(enc?.level),
            influencePoints = 0,
            status = "active",
            discoveries = (parseChecks(enc?.discoveries) ?: return null).toTypedArray(),
            influenceSkills = (parseChecks(enc?.influenceSkills) ?: return null).toTypedArray(),
            thresholds = (parseThresholds(enc?.thresholds) ?: return null).toTypedArray(),
            resistances = (parseTraits(enc?.resistances) ?: return null).toTypedArray(),
            weaknesses = (parseTraits(enc?.weaknesses) ?: return null).toTypedArray(),
            participants = emptyArray(),
            checkLog = emptyArray(),
            visibleToPlayers = false,
            createdAt = now,
            updatedAt = now,
        )
    } ?: run { if (parsed.influenceEncounters == null || parsed.influenceEncounters == undefined) emptyList() else return null }
    val projects = dynArray(parsed.researchProjects)?.map { proj ->
        RawResearchProject(
            id = newId(),
            name = dynStr(proj?.name)?.takeIf { it.isNotBlank() } ?: return null,
            description = dynStr(proj?.description) ?: "",
            libraryName = dynStr(proj?.libraryName),
            libraryLevel = dynInt(proj?.libraryLevel),
            researchPoints = 0,
            maxResearchPoints = dynInt(proj?.maxResearchPoints)?.takeIf { it > 0 },
            status = "active",
            checks = (parseChecks(proj?.checks) ?: return null).toTypedArray(),
            thresholds = (parseThresholds(proj?.thresholds) ?: return null).toTypedArray(),
            checkLog = emptyArray(),
            sourceEventId = null,
            visibleToPlayers = false,
            createdAt = now,
            updatedAt = now,
        )
    } ?: run { if (parsed.researchProjects == null || parsed.researchProjects == undefined) emptyList() else return null }
    if (encounters.isEmpty() && projects.isEmpty()) return null
    return RawSubsystemStore(
        influenceEncounters = encounters.toTypedArray(),
        researchProjects = projects.toTypedArray(),
    )
}

private external interface ImportJsonData {
    val json: String
}

/** Paste-a-blob prompt; appends to the store, never replaces. */
suspend fun importSubsystemJson(game: Game) {
    val imported = awaitablePrompt<ImportJsonData, RawSubsystemStore?>(
        title = t("subsystems.import.title"),
        templatePath = "components/forms/form.hbs",
        templateContext = recordOf(
            "formRows" to formContext(
                TextArea(
                    name = "json",
                    label = t("subsystems.import.title"),
                    value = "",
                    help = t("subsystems.import.help"),
                )
            )
        ),
        width = 560,
    ) { data, _ ->
        parseSubsystemImport(data.json, kotlin.js.Date.now()) { v4() }
    }
    if (imported == null) {
        ui.notifications.error(t("subsystems.import.invalid"))
        return
    }
    game.updateSubsystemStore { store ->
        store.influenceEncounters =
            (store.influenceEncounters ?: emptyArray()) + (imported.influenceEncounters ?: emptyArray())
        store.researchProjects =
            (store.researchProjects ?: emptyArray()) + (imported.researchProjects ?: emptyArray())
        store
    }
    ui.notifications.info(
        t(
            "subsystems.import.done",
            recordOf(
                "encounters" to (imported.influenceEncounters?.size ?: 0),
                "projects" to (imported.researchProjects?.size ?: 0),
            ),
        )
    )
}

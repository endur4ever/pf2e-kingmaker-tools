package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.forms.SimpleApp
import at.posselt.pfrpg2e.Config
import at.posselt.pfrpg2e.data.kingdom.subsystems.ParticipantPoints
import at.posselt.pfrpg2e.data.kingdom.subsystems.PointApplication
import at.posselt.pfrpg2e.data.kingdom.subsystems.PointRule
import at.posselt.pfrpg2e.data.kingdom.subsystems.SubsystemOutcome
import at.posselt.pfrpg2e.data.kingdom.subsystems.SubsystemTrait
import at.posselt.pfrpg2e.data.kingdom.subsystems.applyCheck
import at.posselt.pfrpg2e.data.kingdom.subsystems.applyParticipantDelta
import at.posselt.pfrpg2e.actor.partyMembers
import at.posselt.pfrpg2e.kingdom.data.RawInfluenceEncounter
import at.posselt.pfrpg2e.kingdom.data.RawInfluenceTrait
import at.posselt.pfrpg2e.kingdom.data.RawResearchProject
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemCheck
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemCheckEntry
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemParticipant
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemThreshold
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.kingdom.getSubsystemStore
import at.posselt.pfrpg2e.kingdom.sheet.contexts.SubsystemTrackersContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildSubsystemTrackersContext
import at.posselt.pfrpg2e.kingdom.updateSubsystemStore
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.helpers.Hooks
import com.foundryvtt.core.ui
import kotlinx.coroutines.await
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

/**
 * The standalone Influence & Research tracker (plan section 4.1). Deliberately NOT hung off the
 * kingdom sheet: the store is world-scoped so these subsystems work in a campaign with no
 * kingdom actor at all. Manual-first: the GM records each check's degree by hand; auto-rolling
 * is the plan's explicit v2 deferral.
 */
class SubsystemTrackers(
    private val game: Game,
) : SimpleApp<SubsystemTrackersContext>(
    title = t("subsystems.trackersTitle"),
    template = "applications/subsystems/tracker.hbs",
    classes = setOf("km-scroll-application"),
    id = "kmSubsystemTrackers",
    width = 640,
) {
    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<SubsystemTrackersContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val party = runCatching {
            game.getKingdomActors()
                .firstOrNull()
                ?.partyMembers()
                ?.map { it.uuid to it.name }
        }.getOrNull() ?: emptyList()
        buildSubsystemTrackersContext(
            partId = parent.partId,
            store = game.getSubsystemStore(),
            isGM = game.user.isGM,
            partyOptions = party,
        )
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        // every branch is its own GM check: players can open the tracker (revealed rows are the
        // point), but no control mutates anything for them
        when (target.dataset["action"]) {
            "add-influence" -> {
                if (!game.user.isGM) return
                AddInfluenceEncounter(initial = null) { encounter ->
                    buildPromise {
                        game.updateSubsystemStore { store ->
                            store.influenceEncounters = (store.influenceEncounters ?: emptyArray()) + encounter
                            store
                        }
                        render()
                    }
                }.launch()
            }

            "add-research" -> {
                if (!game.user.isGM) return
                AddResearchProject(initial = null) { project ->
                    buildPromise {
                        game.updateSubsystemStore { store ->
                            store.researchProjects = (store.researchProjects ?: emptyArray()) + project
                            store
                        }
                        render()
                    }
                }.launch()
            }

            "edit-influence" -> {
                if (!game.user.isGM) return
                val id = target.dataset["id"] ?: return
                val existing = game.getSubsystemStore().influenceEncounters?.firstOrNull { it.id == id } ?: return
                AddInfluenceEncounter(initial = existing) { encounter ->
                    buildPromise {
                        game.updateSubsystemStore { store ->
                            // merge into the CURRENT row, not the snapshot the dialog opened on:
                            // checks recorded and reveals toggled while it was open must survive
                            store.influenceEncounters = store.influenceEncounters?.map { cur ->
                                if (cur.id != id) cur else RawInfluenceEncounter.copy(
                                    cur,
                                    name = encounter.name,
                                    npcName = encounter.npcName,
                                    description = encounter.description,
                                    discoveries = mergeRevealed((encounter.discoveries ?: emptyArray()).toList(), cur.discoveries),
                                    influenceSkills = mergeRevealed((encounter.influenceSkills ?: emptyArray()).toList(), cur.influenceSkills),
                                    thresholds = mergeThresholdState((encounter.thresholds ?: emptyArray()).toList(), cur.thresholds),
                                    resistances = encounter.resistances,
                                    weaknesses = encounter.weaknesses,
                                    updatedAt = encounter.updatedAt,
                                )
                            }?.toTypedArray()
                            store
                        }
                        render()
                    }
                }.launch()
            }

            "edit-research" -> {
                if (!game.user.isGM) return
                val id = target.dataset["id"] ?: return
                val existing = game.getSubsystemStore().researchProjects?.firstOrNull { it.id == id } ?: return
                AddResearchProject(initial = existing) { project ->
                    buildPromise {
                        game.updateSubsystemStore { store ->
                            store.researchProjects = store.researchProjects?.map { cur ->
                                if (cur.id != id) cur else RawResearchProject.copy(
                                    cur,
                                    name = project.name,
                                    description = project.description,
                                    libraryName = project.libraryName,
                                    maxResearchPoints = project.maxResearchPoints,
                                    checks = mergeRevealed((project.checks ?: emptyArray()).toList(), cur.checks),
                                    thresholds = mergeThresholdState((project.thresholds ?: emptyArray()).toList(), cur.thresholds),
                                    updatedAt = project.updatedAt,
                                )
                            }?.toTypedArray()
                            store
                        }
                        render()
                    }
                }.launch()
            }

            "delete-influence", "delete-research" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val id = target.dataset["id"] ?: return@buildPromise
                if (!at.posselt.pfrpg2e.app.confirm(t("subsystems.confirmDelete"))) return@buildPromise
                game.updateSubsystemStore { store ->
                    if (target.dataset["action"] == "delete-influence") {
                        store.influenceEncounters = store.influenceEncounters?.filter { it.id != id }?.toTypedArray()
                    } else {
                        store.researchProjects = store.researchProjects?.filter { it.id != id }?.toTypedArray()
                    }
                    store
                }
                render()
            }

            "record-check" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val id = target.dataset["id"] ?: return@buildPromise
                val outcome = target.dataset["outcome"]?.let { SubsystemOutcome.fromString(it) }
                    ?: return@buildPromise
                // the skill and PC come from the sibling selects in the same dashboard row
                val row = target.closest(".km-sub-record") ?: return@buildPromise
                val skill = (row.querySelector("[data-role='skill']") as? HTMLSelectElement)?.value
                    ?.takeIf { it.isNotBlank() } ?: return@buildPromise
                val pcUuid = (row.querySelector("[data-role='pc']") as? HTMLSelectElement)?.value
                val store = game.getSubsystemStore()
                val resolved = store.influenceEncounters?.any { it.id == id && it.status == "resolved" } == true ||
                    store.researchProjects?.any { it.id == id && it.status == "resolved" } == true
                if (resolved) {
                    ui.notifications.warn(t("subsystems.resolvedGuard"))
                    return@buildPromise
                }
                recordCheck(id, skill, pcUuid?.takeIf { it.isNotBlank() }, outcome)
                render()
            }

            "toggle-reveal" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val id = target.dataset["id"] ?: return@buildPromise
                val kind = target.dataset["kind"] ?: return@buildPromise
                val index = target.dataset["index"]?.toIntOrNull() ?: return@buildPromise
                toggleReveal(id, kind, index)
                render()
            }

            "toggle-visible" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val id = target.dataset["id"] ?: return@buildPromise
                game.updateSubsystemStore { store ->
                    store.influenceEncounters = store.influenceEncounters?.map {
                        if (it.id == id) RawInfluenceEncounter.copy(it, visibleToPlayers = it.visibleToPlayers != true) else it
                    }?.toTypedArray()
                    store.researchProjects = store.researchProjects?.map {
                        if (it.id == id) RawResearchProject.copy(it, visibleToPlayers = it.visibleToPlayers != true) else it
                    }?.toTypedArray()
                    store
                }
                render()
            }

            "toggle-resolved" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val id = target.dataset["id"] ?: return@buildPromise
                game.updateSubsystemStore { store ->
                    store.influenceEncounters = store.influenceEncounters?.map {
                        if (it.id == id) RawInfluenceEncounter.copy(it, status = if (it.status == "resolved") "active" else "resolved") else it
                    }?.toTypedArray()
                    store.researchProjects = store.researchProjects?.map {
                        if (it.id == id) RawResearchProject.copy(it, status = if (it.status == "resolved") "active" else "resolved") else it
                    }?.toTypedArray()
                    store
                }
                render()
            }

            "new-round" -> buildPromise {
                // advisory round bookkeeping: clears everyone's acted flag, blocks nothing
                if (!game.user.isGM) return@buildPromise
                val id = target.dataset["id"] ?: return@buildPromise
                game.updateSubsystemStore { store ->
                    store.influenceEncounters = store.influenceEncounters?.map { enc ->
                        if (enc.id == id) {
                            RawInfluenceEncounter.copy(enc, participants = enc.participants?.map {
                                RawSubsystemParticipant.copy(it, actedThisRound = false)
                            }?.toTypedArray())
                        } else enc
                    }?.toTypedArray()
                    store
                }
                render()
            }
        }
    }

    /**
     * One recorded check: pure math in, store write out. The applied delta -- post-trait,
     * post-clamp -- is what lands in the log and the participant row, never the dice's nominal
     * value. Trait matching is v1-simple: all of the encounter's resistances and weaknesses
     * whose label appears in the chosen skill's note are matched automatically; finer-grained
     * per-check trait picking is the dashboard's future, not its v1.
     */
    private suspend fun recordCheck(id: String, skill: String, pcUuid: String?, outcome: SubsystemOutcome) {
        game.updateSubsystemStore { store ->
            store.influenceEncounters = store.influenceEncounters?.map { enc ->
                if (enc.id != id || enc.status == "resolved") return@map enc
                val matched = matchedTraitsFor(
                    enc.influenceSkills?.firstOrNull { it.skill == skill }?.note,
                    enc.resistances,
                    enc.weaknesses,
                )
                val application = applyCheck(enc.influencePoints, outcome, PointRule(), matched)
                val pcName = pcUuid?.let { uuid ->
                    runCatching { game.actors.find { it.uuid == uuid }?.name }.getOrNull()
                }
                val participants = if (pcUuid != null) {
                    val updated = applyParticipantDelta(
                        (enc.participants ?: emptyArray()).map { ParticipantPoints(it.uuid, it.points) },
                        pcUuid,
                        application.appliedDelta,
                    )
                    updated.map { pp ->
                        val prior = enc.participants?.firstOrNull { it.uuid == pp.uuid }
                        RawSubsystemParticipant(
                            uuid = pp.uuid,
                            name = prior?.name ?: pcName ?: pp.uuid,
                            points = pp.points,
                            actedThisRound = if (pp.uuid == pcUuid) true else prior?.actedThisRound,
                        )
                    }.toTypedArray()
                } else enc.participants
                RawInfluenceEncounter.copy(
                    enc,
                    influencePoints = application.newTotal,
                    participants = participants,
                    checkLog = (enc.checkLog ?: emptyArray()) + RawSubsystemCheckEntry(
                        timestamp = kotlin.js.Date.now(),
                        participantUuid = pcUuid,
                        skill = skill,
                        outcome = outcome.value,
                        pointsDelta = application.appliedDelta,
                        note = null,
                    ),
                )
            }?.toTypedArray()
            store.researchProjects = store.researchProjects?.map { proj ->
                if (proj.id != id || proj.status == "resolved") return@map proj
                // projects carry no traits; the empty list is correct, not a stub
                val application = clampResearchApplication(
                    proj.researchPoints,
                    applyCheck(proj.researchPoints, outcome, PointRule(), emptyList()),
                    proj.maxResearchPoints,
                )
                RawResearchProject.copy(
                    proj,
                    researchPoints = application.newTotal,
                    checkLog = (proj.checkLog ?: emptyArray()) + RawSubsystemCheckEntry(
                        timestamp = kotlin.js.Date.now(),
                        participantUuid = pcUuid,
                        skill = skill,
                        outcome = outcome.value,
                        pointsDelta = application.appliedDelta,
                        note = null,
                    ),
                )
            }?.toTypedArray()
            store
        }
    }

    private suspend fun toggleReveal(id: String, kind: String, index: Int) {
        fun flipCheck(rows: Array<RawSubsystemCheck>?): Array<RawSubsystemCheck>? =
            rows?.mapIndexed { i, row ->
                if (i == index) RawSubsystemCheck.copy(row, revealed = row.revealed != true) else row
            }?.toTypedArray()

        fun flipThreshold(rows: Array<RawSubsystemThreshold>?): Array<RawSubsystemThreshold>? =
            rows?.mapIndexed { i, row ->
                if (i == index) RawSubsystemThreshold.copy(row, revealedToPlayers = row.revealedToPlayers != true) else row
            }?.toTypedArray()

        game.updateSubsystemStore { store ->
            store.influenceEncounters = store.influenceEncounters?.map { enc ->
                if (enc.id != id) return@map enc
                when (kind) {
                    "discovery" -> RawInfluenceEncounter.copy(enc, discoveries = flipCheck(enc.discoveries))
                    "skill" -> RawInfluenceEncounter.copy(enc, influenceSkills = flipCheck(enc.influenceSkills))
                    "threshold" -> RawInfluenceEncounter.copy(enc, thresholds = flipThreshold(enc.thresholds))
                    else -> enc
                }
            }?.toTypedArray()
            store.researchProjects = store.researchProjects?.map { proj ->
                if (proj.id != id) return@map proj
                when (kind) {
                    "check" -> RawResearchProject.copy(proj, checks = flipCheck(proj.checks))
                    "threshold" -> RawResearchProject.copy(proj, thresholds = flipThreshold(proj.thresholds))
                    else -> proj
                }
            }?.toTypedArray()
            store
        }
    }
}

/**
 * V1 trait matching, exactly as recordCheck's contract documents it: every resistance and
 * weakness whose label appears (case-insensitively) in the chosen skill row's note is matched.
 * No note, no traits.
 */
fun matchedTraitsFor(
    note: String?,
    resistances: Array<RawInfluenceTrait>?,
    weaknesses: Array<RawInfluenceTrait>?,
): List<SubsystemTrait> {
    val haystack = note?.lowercase() ?: return emptyList()
    return ((resistances ?: emptyArray()) + (weaknesses ?: emptyArray()))
        .filter { it.label.isNotBlank() && haystack.contains(it.label.lowercase()) }
        .map { SubsystemTrait(label = it.label, delta = it.delta) }
}

/**
 * Research pools stop at their max; the logged delta is what actually landed, honoring the
 * check-log's post-trait, post-clamp contract.
 */
fun clampResearchApplication(previous: Int, application: PointApplication, max: Int?): PointApplication =
    if (max != null && max > 0 && application.newTotal > max) {
        PointApplication(newTotal = max, appliedDelta = max - previous)
    } else application

private var openTracker: SubsystemTrackers? = null
private var trackerSyncHookRegistered = false

/** The Kingdom-less launch surface: exposed on the module API and callable from a macro. */
fun openSubsystemTrackers(game: Game) {
    if (!trackerSyncHookRegistered) {
        trackerSyncHookRegistered = true
        // the store is a world setting, i.e. a replicated Setting document: every client hears
        // updateSetting, so open windows (a player's included) stay honest without socket code
        Hooks.on("updateSetting") { setting: dynamic ->
            val key = setting?.key as? String
            if (key == "${Config.moduleId}.subsystemStore") {
                openTracker?.takeIf { it.rendered }?.render()
            }
        }
    }
    val app = SubsystemTrackers(game)
    openTracker = app
    app.launch()
}

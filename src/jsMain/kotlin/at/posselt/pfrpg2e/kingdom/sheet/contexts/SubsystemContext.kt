package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.kingdom.data.RawInfluenceEncounter
import at.posselt.pfrpg2e.kingdom.data.RawResearchProject
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemStore
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface SubsystemCheckRowContext {
    val index: Int
    val skill: String
    val dc: Int
    val revealed: Boolean
    val note: String?
}

@Suppress("unused")
@JsPlainObject
external interface SubsystemThresholdRowContext {
    val index: Int
    val points: Int
    /** Null for players until revealed -- the effect text is the GM's secret. */
    val effect: String?
    val reached: Boolean
    val revealed: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface SubsystemParticipantRowContext {
    val uuid: String
    val name: String
    val points: Int
    val acted: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface SubsystemLogRowContext {
    val skill: String
    val outcome: String
    val delta: Int
}

@Suppress("unused")
@JsPlainObject
external interface InfluenceEncounterContext {
    val id: String
    val visibleToPlayers: Boolean
    val name: String
    val npcName: String
    val description: String
    val points: Int
    val isResolved: Boolean
    val discoveries: Array<SubsystemCheckRowContext>
    val influenceSkills: Array<SubsystemCheckRowContext>
    val thresholds: Array<SubsystemThresholdRowContext>
    /** GM-only prose summaries; null for players (traits are the NPC's secrets). */
    val resistanceSummary: String?
    val weaknessSummary: String?
    val participants: Array<SubsystemParticipantRowContext>
    val recentLog: Array<SubsystemLogRowContext>
    val isGM: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface ResearchProjectContext {
    val id: String
    val visibleToPlayers: Boolean
    val name: String
    val description: String
    val libraryName: String?
    val points: Int
    val maxPoints: Int?
    val progressPct: Int
    val isResolved: Boolean
    val checks: Array<SubsystemCheckRowContext>
    val thresholds: Array<SubsystemThresholdRowContext>
    val recentLog: Array<SubsystemLogRowContext>
    val isGM: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface SubsystemTrackersContext : HandlebarsRenderContext {
    val encounters: Array<InfluenceEncounterContext>
    val projects: Array<ResearchProjectContext>
    val hasEncounters: Boolean
    val hasProjects: Boolean
    val isGM: Boolean
    val partyOptions: Array<PartyOptionContext>
}

@Suppress("unused")
@JsPlainObject
external interface PartyOptionContext {
    val uuid: String
    val name: String
}

/**
 * Builds the trackers view. The world-setting blob replicates to every client, so THIS is the
 * privacy boundary: a player's context never contains an unrevealed discovery row, an unrevealed
 * threshold's effect text, the NPC's traits, or a hidden encounter at all.
 */
fun buildSubsystemTrackersContext(
    partId: String,
    store: RawSubsystemStore,
    isGM: Boolean,
    partyOptions: List<Pair<String, String>>,
): SubsystemTrackersContext {
    val encounters = (store.influenceEncounters ?: emptyArray())
        .filter { isGM || it.visibleToPlayers == true }
        .mapNotNull { enc ->
            if (enc.id.isBlank()) return@mapNotNull null
            InfluenceEncounterContext(
                id = enc.id,
                visibleToPlayers = enc.visibleToPlayers == true,
                name = enc.name,
                npcName = enc.npcName,
                description = enc.description,
                points = enc.influencePoints,
                isResolved = enc.status == "resolved",
                discoveries = checkRows(enc.discoveries, isGM),
                influenceSkills = checkRows(enc.influenceSkills, isGM),
                thresholds = thresholdRows(enc.thresholds, enc.influencePoints, isGM),
                resistanceSummary = enc.resistances
                    ?.joinToString(", ") { "${it.label} (${it.delta})" }
                    ?.takeIf { isGM && it.isNotBlank() },
                weaknessSummary = enc.weaknesses
                    ?.joinToString(", ") { "${it.label} (+${it.delta})" }
                    ?.takeIf { isGM && it.isNotBlank() },
                participants = (enc.participants ?: emptyArray()).map {
                    SubsystemParticipantRowContext(
                        uuid = it.uuid, name = it.name, points = it.points,
                        acted = it.actedThisRound == true,
                    )
                }.toTypedArray(),
                recentLog = logRows(enc.checkLog),
                isGM = isGM,
            )
        }
    val projects = (store.researchProjects ?: emptyArray())
        .filter { isGM || it.visibleToPlayers == true }
        .mapNotNull { proj ->
            if (proj.id.isBlank()) return@mapNotNull null
            val max = proj.maxResearchPoints
            ResearchProjectContext(
                id = proj.id,
                visibleToPlayers = proj.visibleToPlayers == true,
                name = proj.name,
                description = proj.description,
                libraryName = proj.libraryName,
                points = proj.researchPoints,
                maxPoints = max,
                progressPct = if (max != null && max > 0) {
                    (proj.researchPoints * 100 / max).coerceIn(0, 100)
                } else 0,
                isResolved = proj.status == "resolved",
                checks = checkRows(proj.checks, isGM),
                thresholds = thresholdRows(proj.thresholds, proj.researchPoints, isGM),
                recentLog = logRows(proj.checkLog),
                isGM = isGM,
            )
        }
    return SubsystemTrackersContext(
        partId = partId,
        encounters = encounters.toTypedArray(),
        projects = projects.toTypedArray(),
        hasEncounters = encounters.isNotEmpty(),
        hasProjects = projects.isNotEmpty(),
        isGM = isGM,
        partyOptions = partyOptions.map { PartyOptionContext(uuid = it.first, name = it.second) }.toTypedArray(),
    )
}

private fun checkRows(
    rows: Array<at.posselt.pfrpg2e.kingdom.data.RawSubsystemCheck>?,
    isGM: Boolean,
): Array<SubsystemCheckRowContext> =
    (rows ?: emptyArray())
        .mapIndexedNotNull { index, row ->
            if (!isGM && row.revealed != true) return@mapIndexedNotNull null
            SubsystemCheckRowContext(
                index = index,
                skill = row.skill,
                dc = row.dc,
                revealed = row.revealed == true,
                note = row.note?.takeIf { isGM },
            )
        }
        .toTypedArray()

private fun thresholdRows(
    rows: Array<at.posselt.pfrpg2e.kingdom.data.RawSubsystemThreshold>?,
    currentPoints: Int,
    isGM: Boolean,
): Array<SubsystemThresholdRowContext> =
    (rows ?: emptyArray())
        .mapIndexedNotNull { index, row ->
            val revealed = row.revealedToPlayers == true
            if (!isGM && !revealed) return@mapIndexedNotNull null
            SubsystemThresholdRowContext(
                index = index,
                points = row.points,
                // the effect text is the secret; players see the milestone exists, not what it does,
                // until the GM reveals it
                effect = row.effect.takeIf { isGM || revealed },
                reached = currentPoints >= row.points,
                revealed = revealed,
            )
        }
        .toTypedArray()

private fun logRows(
    rows: Array<at.posselt.pfrpg2e.kingdom.data.RawSubsystemCheckEntry>?,
): Array<SubsystemLogRowContext> =
    (rows ?: emptyArray())
        .takeLast(5)
        .reversed()
        .map { SubsystemLogRowContext(skill = it.skill, outcome = localizeOutcome(it.outcome), delta = it.pointsDelta) }
        .toTypedArray()

/** Literal keys, not "subsystems.outcome.dollar-id" — composed keys are invisible to the i18n scan. */
private fun localizeOutcome(outcome: String?): String =
    when (outcome) {
        "criticalSuccess" -> t("subsystems.outcome.critSuccess")
        "success" -> t("subsystems.outcome.success")
        "failure" -> t("subsystems.outcome.failure")
        "criticalFailure" -> t("subsystems.outcome.critFailure")
        else -> outcome ?: ""
    }

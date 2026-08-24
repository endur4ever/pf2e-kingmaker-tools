package at.posselt.pfrpg2e.kingdom.downtime

/**
 * Pure core of the PC downtime project ledger
 * (`docs/plans/2026-07-09-plan-downtime-projects.md`).
 *
 * The clock does not advance a day at a time — downtime is precisely when a GM advances it in
 * jumps — so every function here takes a **day count**, never assumes 1. Getting that wrong loses
 * six days out of every seven.
 */

enum class DowntimeKind(val value: String) {
    CRAFT("craft"), RETRAIN("retrain"), EARN_INCOME("earnIncome"), RITUAL("ritual");

    companion object {
        fun fromValue(value: String?): DowntimeKind? = entries.find { it.value == value }
    }
}

enum class DowntimeStatus(val value: String) {
    IN_PROGRESS("inProgress"), PAUSED("paused"), COMPLETED("completed");

    companion object {
        fun fromValue(value: String?): DowntimeStatus? = entries.find { it.value == value }
    }
}

/** Structures that can host each kind, ascending: a better structure satisfies a lesser need. */
private val structuresByKind: Map<DowntimeKind, List<String>> = mapOf(
    DowntimeKind.CRAFT to listOf("Smithy", "Magic Shop", "Magical Streetlamps"),
    DowntimeKind.RETRAIN to listOf("Library", "Academy", "University"),
    DowntimeKind.EARN_INCOME to listOf("Marketplace", "Bank", "Guildhall"),
    DowntimeKind.RITUAL to listOf("Shrine", "Temple", "Cathedral"),
)

fun structuresFor(kind: DowntimeKind): List<String> = structuresByKind[kind].orEmpty()

/**
 * Whether a settlement holding [structureNames] can host a project of this [kind].
 *
 * Names come from actor names a GM types, so matching ignores case and padding. Any one of the
 * kind's structures qualifies -- they are alternatives, not a ladder to climb.
 */
fun prerequisiteMet(kind: DowntimeKind, structureNames: Set<String>): Boolean =
    structuresFor(kind).any { required ->
        structureNames.any { it.trim().equals(required, ignoreCase = true) }
    }

data class DowntimeProject(
    val id: String,
    val pcActorUuid: String,
    val kind: DowntimeKind,
    val title: String,
    val settlementId: String? = null,
    val daysTotal: Int,
    val daysRemaining: Int,
    val status: DowntimeStatus = DowntimeStatus.IN_PROGRESS,
    val pauseReason: String? = null,
)

data class DowntimeTickOutcome(
    val projects: List<DowntimeProject>,
    val completed: List<DowntimeProject>,
    val paused: List<DowntimeProject>,
)

const val DOWNTIME_HISTORY_CAP = 100

/** Reason recorded when a project's host settlement or structure is gone. */
const val PAUSE_REASON_PREREQUISITE_LOST = "prerequisiteLost"

/**
 * Advance every in-progress project by [days].
 *
 * Paused and completed projects are returned untouched — a paused project must not lose days it
 * was not working. A project failing [stillEligible] pauses instead of ticking, so a razed smithy
 * stops the craft rather than letting it complete somewhere that no longer exists.
 *
 * A project already at zero does not re-complete: completion is reported only on the tick that
 * actually consumes its last day.
 */
fun tickDowntimeProjects(
    projects: List<DowntimeProject>,
    days: Int,
    stillEligible: (DowntimeProject) -> Boolean = { true },
): DowntimeTickOutcome {
    val elapsed = days.coerceAtLeast(1)
    val next = mutableListOf<DowntimeProject>()
    val completed = mutableListOf<DowntimeProject>()
    val paused = mutableListOf<DowntimeProject>()
    for (p in projects) {
        if (p.status != DowntimeStatus.IN_PROGRESS) {
            next.add(p)
            continue
        }
        if (!stillEligible(p)) {
            val stopped = p.copy(status = DowntimeStatus.PAUSED, pauseReason = PAUSE_REASON_PREREQUISITE_LOST)
            next.add(stopped)
            paused.add(stopped)
            continue
        }
        val remaining = p.daysRemaining - elapsed
        if (remaining <= 0 && p.daysRemaining > 0) {
            val done = p.copy(daysRemaining = 0, status = DowntimeStatus.COMPLETED)
            next.add(done)
            completed.add(done)
        } else {
            next.add(p.copy(daysRemaining = maxOf(0, remaining)))
        }
    }
    return DowntimeTickOutcome(next, completed, paused)
}

/**
 * Append a project, trimming oldest **answered** entries at the cap.
 *
 * In-progress and paused projects are never pruned: dropping one silently cancels work a player is
 * waiting on.
 */
fun appendDowntimeProject(
    existing: List<DowntimeProject>,
    project: DowntimeProject,
    cap: Int = DOWNTIME_HISTORY_CAP,
): List<DowntimeProject> {
    val all = existing + project
    val answered = all.filter { it.status == DowntimeStatus.COMPLETED }
    if (answered.size <= cap) return all
    val drop = answered.take(answered.size - cap).toSet()
    return all.filterNot { it in drop }
}

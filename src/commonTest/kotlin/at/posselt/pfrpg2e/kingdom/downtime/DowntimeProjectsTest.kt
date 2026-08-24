package at.posselt.pfrpg2e.kingdom.downtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers the cases §10 of the downtime-projects plan enumerates. */
class DowntimeProjectsTest {
    private fun project(
        id: String = "p1",
        kind: DowntimeKind = DowntimeKind.CRAFT,
        daysTotal: Int = 30,
        daysRemaining: Int = 30,
        status: DowntimeStatus = DowntimeStatus.IN_PROGRESS,
    ) = DowntimeProject(
        id = id, pcActorUuid = "Actor.pc", kind = kind, title = "a sword",
        settlementId = "s1", daysTotal = daysTotal, daysRemaining = daysRemaining, status = status,
    )

    @Test
    fun aSevenDayJumpConsumesSevenDays() {
        // The bug this plan turned on: decrementing by 1 per tick loses six days of every seven.
        val out = tickDowntimeProjects(listOf(project(daysRemaining = 30)), days = 7)
        assertEquals(23, out.projects.single().daysRemaining)
    }

    @Test
    fun aSingleDayStillConsumesOne() {
        assertEquals(29, tickDowntimeProjects(listOf(project()), days = 1).projects.single().daysRemaining)
    }

    @Test
    fun aJumpLargerThanRemainingCompletesExactlyOnceAndClampsAtZero() {
        val out = tickDowntimeProjects(listOf(project(daysRemaining = 3)), days = 10)
        assertEquals(0, out.projects.single().daysRemaining)
        assertEquals(DowntimeStatus.COMPLETED, out.projects.single().status)
        assertEquals(1, out.completed.size)
    }

    @Test
    fun anAlreadyCompletedProjectDoesNotRecomplete() {
        val done = project(daysRemaining = 0, status = DowntimeStatus.COMPLETED)
        val out = tickDowntimeProjects(listOf(done), days = 5)
        assertTrue(out.completed.isEmpty(), "a finished project must not report completion again")
        assertEquals(0, out.projects.single().daysRemaining)
    }

    @Test
    fun anInProgressProjectAlreadyAtZeroDoesNotReportCompletion() {
        // Distinct from the COMPLETED case above, which the status guard short-circuits. A project
        // left in progress at zero days -- reopened, or written oddly -- must not re-fire its
        // completion offer on every subsequent tick.
        val stuck = project(daysRemaining = 0, status = DowntimeStatus.IN_PROGRESS)
        val out = tickDowntimeProjects(listOf(stuck), days = 7)
        assertTrue(out.completed.isEmpty(), "zero-day in-progress project must not re-complete")
        assertEquals(0, out.projects.single().daysRemaining)
    }

    @Test
    fun aPausedProjectLosesNoDaysAtAnyJumpSize() {
        val paused = project(daysRemaining = 12, status = DowntimeStatus.PAUSED)
        listOf(1, 7, 90).forEach { d ->
            assertEquals(12, tickDowntimeProjects(listOf(paused), days = d).projects.single().daysRemaining)
        }
    }

    @Test
    fun losingThePrerequisitePausesInsteadOfTicking() {
        val out = tickDowntimeProjects(listOf(project(daysRemaining = 10)), days = 7) { false }
        val p = out.projects.single()
        assertEquals(DowntimeStatus.PAUSED, p.status)
        assertEquals(10, p.daysRemaining, "a paused project keeps its days")
        assertEquals(PAUSE_REASON_PREREQUISITE_LOST, p.pauseReason)
        assertEquals(1, out.paused.size)
    }

    @Test
    fun aResumedProjectContinuesFromItsFrozenDays() {
        val resumed = project(daysRemaining = 12, status = DowntimeStatus.PAUSED)
            .copy(status = DowntimeStatus.IN_PROGRESS)
        assertEquals(5, tickDowntimeProjects(listOf(resumed), days = 7).projects.single().daysRemaining)
    }

    @Test
    fun zeroOrNegativeDaysStillConsumeOne() {
        // The hook only fires when at least one boundary was crossed; guard against a 0 slipping in.
        assertEquals(29, tickDowntimeProjects(listOf(project()), days = 0).projects.single().daysRemaining)
    }

    @Test
    fun aBetterStructureSatisfiesTheRequirement() {
        assertTrue(prerequisiteMet(DowntimeKind.RITUAL, setOf("Cathedral")))
        assertTrue(prerequisiteMet(DowntimeKind.RITUAL, setOf("Shrine")))
    }

    @Test
    fun anUnrelatedStructureNeverQualifies() {
        assertFalse(prerequisiteMet(DowntimeKind.CRAFT, setOf("Tavern", "Granary")))
        assertFalse(prerequisiteMet(DowntimeKind.RETRAIN, emptySet()))
    }

    @Test
    fun structureMatchingIgnoresCaseAndPadding() {
        assertTrue(prerequisiteMet(DowntimeKind.RETRAIN, setOf("  library  ")))
    }

    @Test
    fun eachKindHasItsOwnStructures() {
        assertFalse(prerequisiteMet(DowntimeKind.CRAFT, setOf("Library")))
        assertTrue(prerequisiteMet(DowntimeKind.CRAFT, setOf("Smithy")))
    }

    @Test
    fun theCapTrimsCompletedProjectsOnly() {
        val done = (1..DOWNTIME_HISTORY_CAP).map {
            project(id = "d$it", daysRemaining = 0, status = DowntimeStatus.COMPLETED)
        }
        val withOpen = done + project(id = "open", status = DowntimeStatus.IN_PROGRESS)
        val out = appendDowntimeProject(withOpen, project(id = "new", daysRemaining = 0, status = DowntimeStatus.COMPLETED))
        assertEquals(DOWNTIME_HISTORY_CAP, out.count { it.status == DowntimeStatus.COMPLETED })
        assertTrue(out.any { it.id == "open" }, "an in-progress project must never be pruned")
        assertFalse(out.any { it.id == "d1" }, "the oldest completed goes first")
    }

    @Test
    fun anOpenProjectIsNeverPrunedEvenPastTheCap() {
        val open = (1..DOWNTIME_HISTORY_CAP + 20).map { project(id = "o$it") }
        val out = appendDowntimeProject(open, project(id = "new"))
        assertEquals(open.size + 1, out.size)
    }

    @Test
    fun unknownStoredValuesMapToNullRatherThanThrowing() {
        assertEquals(null, DowntimeKind.fromValue("bricklaying"))
        assertEquals(null, DowntimeStatus.fromValue("abandoned"))
        assertEquals(DowntimeKind.RITUAL, DowntimeKind.fromValue("ritual"))
    }
}

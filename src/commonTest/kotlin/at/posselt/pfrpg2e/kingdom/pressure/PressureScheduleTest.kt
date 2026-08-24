package at.posselt.pfrpg2e.kingdom.pressure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Covers the cases §11 of the scheduled-pressure plan enumerates. */
class PressureScheduleTest {
    private fun sched(
        id: String = "s1",
        startDay: Int = 10,
        recurrence: Recurrence = Recurrence.WEEKLY,
        endDay: Int? = null,
        lastFiredDay: Int? = null,
        active: Boolean = true,
        escalationCount: Int = 0,
    ) = ScheduledPressure(
        id = id, name = id, startDay = startDay, recurrence = recurrence, endDay = endDay,
        lastFiredDay = lastFiredDay, payloadKind = PayloadKind.SPAWN_EVENT,
        escalationCount = escalationCount, active = active,
    )

    @Test
    fun aWeeklyScheduleFiresOnceAcrossASevenDayJump() {
        // The bug this engine exists to avoid: a week-long advance must not collapse to one tick
        // that ignores six days, nor fire seven times.
        val f = dueFirings(listOf(sched(startDay = 10)), fromDay = 9, toDay = 16)
        assertEquals(listOf(10), f.map { it.day })
    }

    @Test
    fun aWeeklyScheduleFiresThreeTimesAcrossTwentyOneDays() {
        val f = dueFirings(listOf(sched(startDay = 10)), fromDay = 9, toDay = 30)
        assertEquals(listOf(10, 17, 24), f.map { it.day })
    }

    @Test
    fun aDailyScheduleFiresEveryDayOfTheJump() {
        val f = dueFirings(listOf(sched(startDay = 5, recurrence = Recurrence.DAILY)), fromDay = 4, toDay = 11)
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11), f.map { it.day })
    }

    @Test
    fun theStartOfTheWindowIsExclusiveSoARetickCannotDoubleFire() {
        // Day 10 already fired when the clock crossed it; re-ticking from 10 must not repeat it.
        assertTrue(dueFirings(listOf(sched(startDay = 10)), fromDay = 10, toDay = 12).isEmpty())
    }

    @Test
    fun lastFiredDaySuppressesAnAlreadyCoveredSpan() {
        val f = dueFirings(listOf(sched(startDay = 10, lastFiredDay = 17)), fromDay = 9, toDay = 20)
        assertEquals(emptyList(), f.map { it.day })
    }

    @Test
    fun endDayIsInclusiveOnItsOwnDayAndStopsAfter() {
        assertEquals(listOf(10, 17), dueFirings(listOf(sched(startDay = 10, endDay = 17)), 9, 30).map { it.day })
        assertEquals(listOf(10), dueFirings(listOf(sched(startDay = 10, endDay = 16)), 9, 30).map { it.day })
    }

    @Test
    fun aResolvedScheduleYieldsNothing() {
        assertTrue(dueFirings(listOf(sched()), 9, 40) { true }.isEmpty())
    }

    @Test
    fun anInactiveScheduleYieldsNothing() {
        assertTrue(dueFirings(listOf(sched(active = false)), 9, 40).isEmpty())
    }

    @Test
    fun escalationIncrementsOncePerFiringAcrossOneJump() {
        val f = dueFirings(listOf(sched(startDay = 10)), fromDay = 9, toDay = 30)
        assertEquals(listOf(1, 2, 3), f.map { it.escalation })
    }

    @Test
    fun escalationContinuesFromTheStoredCount() {
        val f = dueFirings(listOf(sched(startDay = 10, escalationCount = 4)), fromDay = 9, toDay = 18)
        assertEquals(listOf(5, 6), f.map { it.escalation })
    }

    @Test
    fun aOneShotFiresOnceAndNeverAgain() {
        val one = sched(startDay = 10, recurrence = Recurrence.NONE)
        assertEquals(listOf(10), dueFirings(listOf(one), 9, 40).map { it.day })
        assertTrue(dueFirings(listOf(one.copy(lastFiredDay = 10)), 9, 400).isEmpty())
    }

    @Test
    fun timeMovingBackwardsFiresNothing() {
        // daysCrossed already guards this, but the engine must not invent firings on its own.
        assertTrue(dueFirings(listOf(sched()), fromDay = 40, toDay = 20).isEmpty())
    }

    @Test
    fun aZeroLengthWindowFiresNothing() {
        // A world-time update that crosses no day boundary. The early-out is a fast path -- the
        // stride walk would land past the ceiling anyway -- but the behaviour is pinned here so it
        // cannot drift into firing on a no-op update.
        assertTrue(dueFirings(listOf(sched(startDay = 10)), fromDay = 10, toDay = 10).isEmpty())
        assertTrue(dueFirings(listOf(sched(startDay = 10, recurrence = Recurrence.NONE)), 10, 10).isEmpty())
    }

    @Test
    fun firingsAcrossSchedulesComeBackInDayOrder() {
        val a = sched(id = "a", startDay = 12, recurrence = Recurrence.NONE)
        val b = sched(id = "b", startDay = 10, recurrence = Recurrence.NONE)
        assertEquals(listOf(10, 12), dueFirings(listOf(a, b), 9, 20).map { it.day })
    }

    @Test
    fun aMonthlyScheduleUsesThirtyDayStrides() {
        assertEquals(listOf(10, 40, 70), dueFirings(listOf(sched(recurrence = Recurrence.MONTHLY)), 9, 90).map { it.day })
    }

    @Test
    fun oneJumpSpanningManyWeeksMatchesWalkingDayByDay() {
        // The property that makes "advance a week" indistinguishable from seven single-day ticks.
        val s = listOf(sched(startDay = 3, recurrence = Recurrence.WEEKLY))
        val oneJump = dueFirings(s, 0, 60).map { it.day }
        val stepwise = (1..60).flatMap { d -> dueFirings(s, d - 1, d).map { it.day } }
        assertEquals(stepwise, oneJump)
    }

    @Test
    fun unknownStoredValuesMapToNullRatherThanThrowing() {
        assertEquals(null, Recurrence.fromValue("fortnightly"))
        assertEquals(null, PayloadKind.fromValue("teleport"))
        assertEquals(Recurrence.WEEKLY, Recurrence.fromValue("weekly"))
    }
}

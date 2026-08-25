package at.posselt.pfrpg2e.kingdom.pressure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextFiringDayTest {
    private fun schedule(
        startDay: Int,
        recurrence: Recurrence = Recurrence.WEEKLY,
        endDay: Int? = null,
        lastFiredDay: Int? = null,
        active: Boolean = true,
    ) = ScheduledPressure(
        id = "s", name = "S", startDay = startDay, recurrence = recurrence,
        endDay = endDay, lastFiredDay = lastFiredDay,
        payloadKind = PayloadKind.POST_BEAT, active = active,
    )

    @Test
    fun futureOneShotCountsDownToItsStartDay() {
        assertEquals(90, nextFiringDay(schedule(90, Recurrence.NONE), afterDay = 10))
    }

    @Test
    fun firedOneShotNeverFiresAgain() {
        assertNull(nextFiringDay(schedule(90, Recurrence.NONE, lastFiredDay = 90), afterDay = 100))
        // even when queried from before the firing day, lastFiredDay wins the floor
        assertNull(nextFiringDay(schedule(90, Recurrence.NONE, lastFiredDay = 90), afterDay = 10))
    }

    @Test
    fun recurringStepsFromStartDayNotFromToday() {
        // weekly from day 3: firings on 3, 10, 17...; from day 8 the next is 10, not 15
        assertEquals(10, nextFiringDay(schedule(3), afterDay = 8))
        assertEquals(10, nextFiringDay(schedule(3), afterDay = 9), "strictly after: day 10 due tomorrow")
        assertEquals(17, nextFiringDay(schedule(3), afterDay = 10), "due today already counts as passed")
    }

    @Test
    fun futureStartWinsOverStride() {
        assertEquals(50, nextFiringDay(schedule(50), afterDay = 10))
    }

    @Test
    fun endDayEndsTheRecurrence() {
        assertEquals(10, nextFiringDay(schedule(3, endDay = 10), afterDay = 8), "endDay itself still fires")
        assertNull(nextFiringDay(schedule(3, endDay = 10), afterDay = 10))
    }

    @Test
    fun inactiveOrResolvedNeverFires() {
        assertNull(nextFiringDay(schedule(3, active = false), afterDay = 0))
        assertNull(nextFiringDay(schedule(3), afterDay = 0, resolved = true))
    }

    @Test
    fun agreesWithDueFiringsOnTheSameSchedule() {
        // the load-bearing property: the row's countdown day IS the day the tick fires on
        val s = schedule(3, lastFiredDay = 10)
        val next = nextFiringDay(s, afterDay = 12)!!
        val fired = dueFirings(listOf(s), fromDay = 12, toDay = next)
        assertEquals(listOf(next), fired.map { it.day })
        assertEquals(emptyList(), dueFirings(listOf(s), fromDay = 12, toDay = next - 1).map { it.day })
    }
}

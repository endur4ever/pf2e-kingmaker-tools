package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.resting.DAY_SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyTickHooksTest {

    @Test
    fun daysCrossedZeroOnNegativeOrZeroDelta() {
        assertEquals(0, daysCrossed(100_000, 0))
        assertEquals(0, daysCrossed(100_000, -5000))
    }

    @Test
    fun daysCrossedWithinSameDay() {
        val worldTime = 10_000
        val delta = 2000
        assertEquals(0, daysCrossed(worldTime, delta))
    }

    @Test
    fun daysCrossedOverMidnight() {
        val worldTime = DAY_SECONDS + 100
        val delta = 200
        assertEquals(1, daysCrossed(worldTime, delta))
    }

    @Test
    fun daysCrossedMultipleDays() {
        val worldTime = 5 * DAY_SECONDS + 100
        val delta = 3 * DAY_SECONDS
        assertEquals(3, daysCrossed(worldTime, delta))
    }

    @Test
    fun untickedDaysCrossedNormalAdvance() {
        val worldTime = DAY_SECONDS + 100
        val delta = 86400
        val (days, newHw) = untickedDaysCrossed(worldTime, delta, highWaterMark = 0)
        assertEquals(1, days)
        assertEquals(1, newHw)
    }

    @Test
    fun untickedDaysCrossedRewindReturnsZeroAndPreservesHighWater() {
        val worldTime = 100
        val delta = -86400
        val (days, newHw) = untickedDaysCrossed(worldTime, delta, highWaterMark = 1)
        assertEquals(0, days)
        assertEquals(1, newHw)
    }

    @Test
    fun untickedDaysCrossedReAdvanceOverSameMidnightPreventsDuplicateTick() {
        // High-water mark is at day 1 (already ticked earlier).
        // World clock was rewound to day 0 and is now re-advanced across midnight to day 1.
        val worldTime = DAY_SECONDS + 100
        val delta = 86400
        val (days, newHw) = untickedDaysCrossed(worldTime, delta, highWaterMark = 1)
        assertEquals(0, days, "Should not tick day 1 again because high-water mark is already 1")
        assertEquals(1, newHw)
    }

    @Test
    fun untickedDaysCrossedAdvanceToNextDayTicksCleanly() {
        // High-water mark is at day 1. Advance from day 1 to day 2.
        val worldTime = 2 * DAY_SECONDS + 100
        val delta = 86400
        val (days, newHw) = untickedDaysCrossed(worldTime, delta, highWaterMark = 1)
        assertEquals(1, days)
        assertEquals(2, newHw)
    }

    @Test
    fun untickedDaysCrossedAdvancePastHighWaterOnlyTicksNewDays() {
        // High-water mark is at day 1. Clock was rewound to day 0, then advanced to day 3 (3 days passed).
        // Only days 2 and 3 should tick (2 days).
        val worldTime = 3 * DAY_SECONDS + 100
        val delta = 3 * DAY_SECONDS
        val (days, newHw) = untickedDaysCrossed(worldTime, delta, highWaterMark = 1)
        assertEquals(2, days, "Should only tick unticked days beyond high-water mark")
        assertEquals(3, newHw)
    }
}

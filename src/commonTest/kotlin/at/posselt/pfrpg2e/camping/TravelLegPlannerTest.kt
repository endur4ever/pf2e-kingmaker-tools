package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class TravelLegPlannerTest {
    private fun leg(hex: String, cost: Double) = RouteLeg(hex, cost)

    @Test
    fun eachFullBudgetLegTakesItsOwnDay() {
        val plan = splitRouteIntoDays(listOf(leg("a", 1.0), leg("b", 1.0)), activitiesPerDay = 1.0)
        assertEquals(2, plan.totalDays)
        assertEquals(2, plan.hexesEntered)
        assertEquals(listOf(listOf("a"), listOf("b")), plan.days.map { d -> d.legs.map { it.hexKey } })
    }

    @Test
    fun cheapLegsPackUntilTheBudgetIsSpent() {
        // three half-cost legs into a budget of 1.0 -> [a,b], [c]
        val plan = splitRouteIntoDays(listOf(leg("a", 0.5), leg("b", 0.5), leg("c", 0.5)), activitiesPerDay = 1.0)
        assertEquals(2, plan.totalDays)
        assertEquals(listOf(listOf("a", "b"), listOf("c")), plan.days.map { d -> d.legs.map { it.hexKey } })
        assertEquals(1.0, plan.days[0].totalActivityCost)
        assertEquals(0.5, plan.days[1].totalActivityCost)
    }

    @Test
    fun anOversizedLegStillGetsItsOwnDay() {
        // a rough/weather-inflated hex costing 2.0 exceeds a 1.0 budget but can't be split
        val plan = splitRouteIntoDays(listOf(leg("a", 1.0), leg("rough", 2.0), leg("c", 1.0)), activitiesPerDay = 1.0)
        assertEquals(3, plan.totalDays)
        assertEquals(listOf(listOf("a"), listOf("rough"), listOf("c")), plan.days.map { d -> d.legs.map { it.hexKey } })
    }

    @Test
    fun weatherModifiedCostsPackFewerLegsPerDay() {
        // same legs, a slower party/worse weather (budget 2.0 vs 4.0) yields more days
        val legs = List(4) { leg("h$it", 1.0) }
        assertEquals(2, splitRouteIntoDays(legs, activitiesPerDay = 2.0).totalDays)
        assertEquals(1, splitRouteIntoDays(legs, activitiesPerDay = 4.0).totalDays)
        assertEquals(4.0, splitRouteIntoDays(legs, activitiesPerDay = 4.0).totalActivityCost)
    }

    @Test
    fun emptyRouteYieldsNoDays() {
        val plan = splitRouteIntoDays(emptyList(), activitiesPerDay = 1.0)
        assertEquals(0, plan.totalDays)
        assertEquals(0, plan.hexesEntered)
        assertEquals(0.0, plan.totalActivityCost)
    }

    @Test
    fun nonPositiveBudgetFallsBackToOneLegPerDay() {
        val plan = splitRouteIntoDays(listOf(leg("a", 0.5), leg("b", 0.5)), activitiesPerDay = 0.0)
        assertEquals(2, plan.totalDays)
    }
}

/**
 * How a route execution is reported once an encounter can cut it short partway.
 */
class TravelExecutionSummaryTest {
    // 5 legs of 1 activity each at 2/day => days [a,b][c,d][e]
    private val plan = splitRouteIntoDays(
        listOf("a", "b", "c", "d", "e").map { RouteLeg(it, 1.0) },
        activitiesPerDay = 2.0,
    )

    @Test
    fun aCompletedRouteReportsEveryHexAndDay() {
        val summary = summarizeTravelExecution(plan, legsCompleted = 5)
        assertEquals(3, summary.daysElapsed)
        assertEquals(5, summary.hexesEntered)
        assertNull(summary.stoppedAtHexKey)
        assertTrue(summary.completed)
    }

    @Test
    fun stoppingPartwayReportsOnlyWhatWasTravelled() {
        // Interrupted entering "c" — the first leg of day two.
        val summary = summarizeTravelExecution(plan, legsCompleted = 3, stoppedAtHexKey = "c")
        assertEquals(2, summary.daysElapsed, "a partly travelled day still costs a day")
        assertEquals(3, summary.hexesEntered)
        assertEquals("c", summary.stoppedAtHexKey)
        assertFalse(summary.completed)
    }

    @Test
    fun anEncounterOnTheFirstHexBillsOneDayNotZero() {
        val summary = summarizeTravelExecution(plan, legsCompleted = 1, stoppedAtHexKey = "a")
        assertEquals(1, summary.daysElapsed)
        assertEquals(1, summary.hexesEntered)
        assertFalse(summary.completed)
    }

    @Test
    fun beingStoppedBeforeMovingCostsNoDays() {
        val summary = summarizeTravelExecution(plan, legsCompleted = 0, stoppedAtHexKey = "a")
        assertEquals(0, summary.daysElapsed)
        assertEquals(0, summary.hexesEntered)
        assertFalse(summary.completed)
    }

    @Test
    fun anEncounterOnTheFinalHexIsStillAnInterruption() {
        // Every leg was travelled, but something stopped them on arrival.
        val summary = summarizeTravelExecution(plan, legsCompleted = 5, stoppedAtHexKey = "e")
        assertEquals(5, summary.hexesEntered)
        assertFalse(summary.completed, "an interrupted run is not a completed one")
    }

    @Test
    fun legCountsAreClampedToThePlan() {
        val summary = summarizeTravelExecution(plan, legsCompleted = 99)
        assertEquals(5, summary.hexesEntered)
        assertEquals(3, summary.daysElapsed)
        assertTrue(summary.completed)
    }

    @Test
    fun anEmptyPlanIsTriviallyComplete() {
        val empty = splitRouteIntoDays(emptyList(), activitiesPerDay = 2.0)
        val summary = summarizeTravelExecution(empty, legsCompleted = 0)
        assertEquals(0, summary.daysElapsed)
        assertTrue(summary.completed)
    }
}

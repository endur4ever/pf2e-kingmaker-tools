package at.posselt.pfrpg2e.camping

/**
 * Pure travel leg-splitter for executing a planned hex route. The camping sheet already computes a
 * route (ordered hexes with terrain/road/weather/party-speed costs, see the routing module);
 * executing it means chunking those steps into travel days against the hexploration-activities-per-day
 * budget (`calculateHexplorationActivities`). This is the deterministic core the "Travel route"
 * action needs; advancing world time, rolling per-hex encounters, moving the token, and posting the
 * summary card are the deferred jsMain wiring.
 *
 * Types are `Route*`-prefixed to stay clear of the routing module's `TravelPlan`/`TravelRoute`.
 *
 * See card t_739fb124.
 */

/** One step of a route: entering [hexKey] costs [activityCost] hexploration activities. */
data class RouteLeg(
    val hexKey: String,
    val activityCost: Double,
)

/** The legs a party covers in a single travel day and their summed cost. */
data class RouteDay(
    val legs: List<RouteLeg>,
    val totalActivityCost: Double,
)

/** A fully split travel plan: the per-day breakdown plus totals for the summary card. */
data class RouteDayPlan(
    val days: List<RouteDay>,
    val totalDays: Int,
    val hexesEntered: Int,
    val totalActivityCost: Double,
)

/**
 * Greedily pack [legs] into travel days, each day holding as many whole legs as fit within
 * [activitiesPerDay]. A leg is never split across days, so a single leg whose cost exceeds the daily
 * budget still occupies its own day (an oversized/rough-terrain step). A non-positive budget is
 * treated as one leg per day. Deterministic and order-preserving.
 */
fun splitRouteIntoDays(legs: List<RouteLeg>, activitiesPerDay: Double): RouteDayPlan {
    val days = mutableListOf<RouteDay>()
    var current = mutableListOf<RouteLeg>()
    var currentCost = 0.0
    for (leg in legs) {
        val wouldExceed = activitiesPerDay <= 0.0 || currentCost + leg.activityCost > activitiesPerDay
        if (current.isNotEmpty() && wouldExceed) {
            days += RouteDay(current.toList(), currentCost)
            current = mutableListOf()
            currentCost = 0.0
        }
        current += leg
        currentCost += leg.activityCost
    }
    if (current.isNotEmpty()) {
        days += RouteDay(current.toList(), currentCost)
    }
    return RouteDayPlan(
        days = days,
        totalDays = days.size,
        hexesEntered = legs.size,
        totalActivityCost = legs.sumOf { it.activityCost },
    )
}

/** How a route execution ended, for the summary chat card. */
data class TravelExecutionSummary(
    /** Travel days consumed. A partially travelled day still counts as a day. */
    val daysElapsed: Int,
    /** Hexes actually entered before stopping (or the whole route when it completed). */
    val hexesEntered: Int,
    /** The hex the party stopped in when an encounter interrupted them; null when they arrived. */
    val stoppedAtHexKey: String?,
    /** True when the party reached the destination. */
    val completed: Boolean,
)

/**
 * Travel days consumed after finishing [legsCompleted] legs of [plan].
 *
 * A day that was only partly travelled still counts — the party spent that day on the road. Zero
 * legs is zero days, so an encounter on the very first hex does not silently bill a day.
 */
fun daysElapsedAfter(plan: RouteDayPlan, legsCompleted: Int): Int {
    if (legsCompleted <= 0) return 0
    var remaining = legsCompleted
    var days = 0
    for (day in plan.days) {
        if (remaining <= 0) break
        days++
        remaining -= day.legs.size
    }
    return days
}

/**
 * Builds the summary for a run that completed [legsCompleted] legs, stopping at
 * [stoppedAtHexKey] when an encounter interrupted it.
 *
 * The party only "completed" the route if nothing stopped them AND they covered every leg, so a
 * run cut short by an encounter on its final hex is still reported as interrupted.
 */
fun summarizeTravelExecution(
    plan: RouteDayPlan,
    legsCompleted: Int,
    stoppedAtHexKey: String? = null,
): TravelExecutionSummary {
    val covered = legsCompleted.coerceIn(0, plan.hexesEntered)
    return TravelExecutionSummary(
        daysElapsed = daysElapsedAfter(plan, covered),
        hexesEntered = covered,
        stoppedAtHexKey = stoppedAtHexKey,
        completed = stoppedAtHexKey == null && covered >= plan.hexesEntered,
    )
}

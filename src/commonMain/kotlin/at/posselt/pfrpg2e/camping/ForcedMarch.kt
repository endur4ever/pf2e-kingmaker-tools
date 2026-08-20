package at.posselt.pfrpg2e.camping

/**
 * Forced-march endurance: how long a party can push past a normal day's travel before it suffers.
 *
 * PF2e (Player Core, "Forced March"): a character can travel beyond a full day's movement for a
 * number of days equal to their Constitution modifier before fatigue sets in. A party marches at
 * the pace of whoever tires first, so the limit is the LOWEST Constitution modifier among the
 * characters, floored at one day — a party is never stopped from marching at all.
 *
 * All pure so the transitions are testable; the sheet renders these numbers and the nightly tick
 * decides from them whether to offer the GM a fatigue application.
 *
 * See card t_3cb12a7c.
 */

/** Seconds in one marching day. */
const val FORCED_MARCH_DAY_SECONDS = 60 * 60 * 24

/** Whole days marched so far. Negative accumulations are treated as zero rather than wrapping. */
fun forcedMarchDays(secondsSpentForcedMarching: Int): Int =
    secondsSpentForcedMarching.coerceAtLeast(0) / FORCED_MARCH_DAY_SECONDS

/**
 * Days this party can force march before fatigue: the weakest Constitution modifier present, at
 * least 1. An empty party (no characters to tire) yields 0 — there is nobody to march.
 */
fun forcedMarchMaxDays(constitutionModifiers: List<Int>): Int =
    constitutionModifiers.minOfOrNull { it.coerceAtLeast(1) } ?: 0

/**
 * Whether the party is currently past its endurance. Equal to the limit is still fine — the limit
 * is the number of days that CAN be marched, not the first day that hurts.
 */
fun forcedMarchOverLimit(days: Int, maxDays: Int): Boolean = maxDays > 0 && days > maxDays

/**
 * Whether this tick is the moment the party crossed INTO exhaustion — the single point at which the
 * GM should be offered a fatigue application.
 *
 * Deliberately an edge, not a level: marching a further day while already over the limit must not
 * re-offer the same fatigue every night, and a party that was already over before this tick has
 * already been asked. Returns false when the count goes down (a rest reset it).
 */
fun crossedForcedMarchLimit(previousDays: Int, newDays: Int, maxDays: Int): Boolean =
    !forcedMarchOverLimit(previousDays, maxDays) && forcedMarchOverLimit(newDays, maxDays)

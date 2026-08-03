package at.posselt.pfrpg2e.camping

/**
 * Pure per-actor starvation state machine for the camping meal loop. Choosing "Skip Meal" — or
 * failing a no-ration Subsist roll — leaves an actor unfed; enough unfed nights in a row starve
 * them. This is the deterministic counter + threshold core; the inline Subsist prompt, the "hungry"
 * badge, the per-actor `CampingData` field + migration, and the GM-confirmed condition offer all
 * live in jsMain (deferred).
 *
 * PF2e starvation (Player Core, "Starvation and Thirst"): a creature endures a number of days
 * without food before it suffers, scaling with its Constitution. This subsystem uses the compressed
 * per-night cadence the card specifies — `1 + Constitution modifier` nights before the first
 * consequence (fatigued), worsening thereafter — because camping resolves one night at a time.
 *
 * See card t_40652699.
 */

/** How accumulated hunger currently bites, given the actor's Constitution. */
enum class StarvationSeverity {
    /** Fed recently enough; no mechanical effect. */
    NONE,

    /** Past the endurance threshold: the actor is fatigued until fed. */
    FATIGUED,

    /** Well past the threshold: fatigue plus escalating harm — escalate the GM offer. */
    WORSENING,
}

/** Per-actor hunger counter persisted on camping state (one entry per actor in camp). */
data class StarvationState(
    val daysWithoutFood: Int = 0,
)

/** Nights this actor can skip food before the first consequence: `1 + Con modifier`, at least 1. */
fun starvationThresholdDays(constitutionModifier: Int): Int =
    (1 + constitutionModifier).coerceAtLeast(1)

/** Advance one night: being fed resets the counter, going unfed increments it. */
fun tickStarvation(state: StarvationState, fed: Boolean): StarvationState =
    if (fed) StarvationState(0) else StarvationState(state.daysWithoutFood + 1)

/**
 * The severity implied by [daysWithoutFood] nights unfed for an actor with [constitutionModifier]:
 * NONE up to and including the threshold, FATIGUED once it is exceeded, and WORSENING once it is
 * exceeded by more than another full threshold's worth of nights.
 */
fun starvationSeverity(daysWithoutFood: Int, constitutionModifier: Int): StarvationSeverity {
    val threshold = starvationThresholdDays(constitutionModifier)
    return when {
        daysWithoutFood <= threshold -> StarvationSeverity.NONE
        daysWithoutFood <= threshold * 2 -> StarvationSeverity.FATIGUED
        else -> StarvationSeverity.WORSENING
    }
}

/**
 * Whether advancing from [previousDaysWithoutFood] to [newDaysWithoutFood] pushes the actor into a
 * more severe band — the moment jsMain should post (or escalate) the GM-confirmed condition offer.
 * Returns false when severity is unchanged or improves (e.g. after being fed).
 */
fun crossedStarvationThreshold(
    previousDaysWithoutFood: Int,
    newDaysWithoutFood: Int,
    constitutionModifier: Int,
): Boolean {
    val before = starvationSeverity(previousDaysWithoutFood, constitutionModifier)
    val after = starvationSeverity(newDaysWithoutFood, constitutionModifier)
    return after.ordinal > before.ordinal
}

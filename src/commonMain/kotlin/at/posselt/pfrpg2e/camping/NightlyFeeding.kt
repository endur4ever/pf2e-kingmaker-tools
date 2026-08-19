package at.posselt.pfrpg2e.camping

/**
 * Who ate tonight, resolved from each camper's meal choice and the party's remaining rations.
 *
 * This is the missing half of [StarvationTrack]: the counter knows how to advance once told
 * "fed" or "unfed", but nothing decided which. Kept pure and here so the decision is testable —
 * the function that consumes it, `completeDailyPreparations`, is private and Foundry-coupled.
 *
 * Deliberately DOES NOT consume anything. Rations are drained by the sheet's separate "consume
 * rations" button, which a GM may or may not press; this only reads how many are on hand in order
 * to classify the night. Making the nightly tick spend food as a side effect would change what
 * every existing table sees at daily preparations, which is well outside this card.
 *
 * See card t_40652699.
 */

/** What a camper arranged to eat tonight. */
enum class NightlyMealKind {
    /** A cooked recipe. Feeds them regardless of the ration pool. */
    COOKED_MEAL,

    /** "Rations or Subsistence" — eats a ration if one is available, otherwise must forage. */
    RATIONS,

    /** "Skip Meal", or no choice recorded at all. Goes to bed hungry. */
    NOTHING,
}

/** One camper's arrangement for the night. */
data class NightlyMealChoice(
    val actorUuid: String,
    val kind: NightlyMealKind,
    /** Rations this choice costs; ignored for every kind but [NightlyMealKind.RATIONS]. */
    val rationCost: Int = 1,
)

/** The night's outcome, partitioned. Every input actor appears in exactly one list. */
data class NightlyFeeding(
    /** Ate — a cooked meal, or a ration the party could afford. */
    val fed: List<String>,
    /**
     * Chose rations but the pool ran dry. These actors get an inline Subsist roll; they are fed
     * only if it yields provisions, so they are NOT counted in [fed] here.
     */
    val mustSubsist: List<String>,
    /** Went without: skipped the meal, or recorded no choice at all. */
    val unfed: List<String>,
)

/**
 * Partition [meals] into fed / must-subsist / unfed against a pool of [availableRations].
 *
 * Rations are allocated in list order until the pool cannot cover the next camper's cost, which
 * mirrors how `reduceFoodBy` actually drains inventories — in order, returning whatever it could
 * not pay for. Order therefore matters and is the caller's roster order, not an arbitrary one.
 *
 * A camper with no recorded choice must be passed as [NightlyMealKind.NOTHING] by the caller:
 * being absent from the meal table means nobody arranged food for them, which is precisely the
 * case this feature exists to notice.
 */
fun resolveNightlyFeeding(
    meals: List<NightlyMealChoice>,
    availableRations: Int,
): NightlyFeeding {
    val fed = mutableListOf<String>()
    val mustSubsist = mutableListOf<String>()
    val unfed = mutableListOf<String>()
    var remaining = availableRations.coerceAtLeast(0)
    meals.forEach { meal ->
        when (meal.kind) {
            NightlyMealKind.COOKED_MEAL -> fed.add(meal.actorUuid)

            NightlyMealKind.RATIONS -> {
                val cost = meal.rationCost.coerceAtLeast(0)
                if (cost <= remaining) {
                    remaining -= cost
                    fed.add(meal.actorUuid)
                } else {
                    mustSubsist.add(meal.actorUuid)
                }
            }

            NightlyMealKind.NOTHING -> unfed.add(meal.actorUuid)
        }
    }
    return NightlyFeeding(fed = fed, mustSubsist = mustSubsist, unfed = unfed)
}

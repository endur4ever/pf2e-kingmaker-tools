package at.posselt.pfrpg2e.kingdom

/**
 * Pure mechanics for three activities whose degree-specific outcomes were previously "not automated"
 * (Hire Adventurers, Decadent Feasts, Irrigation). The impure surfaces (cost display, unrest-
 * application path, Event-phase flat check) consume these; the numbers live here, pinned to the
 * activity outcome text and unit-tested.
 */

/** Activity ids these rules belong to. */
const val HIRE_ADVENTURERS_ACTIVITY = "hire-adventurers"
const val IRRIGATION_ACTIVITY = "irrigation"

/** Hire Adventurers' escalated re-attempt cost (in Resource Dice) after a prior failure this event. */
const val HIRE_ADVENTURERS_ESCALATED_RD_COST = 2

/**
 * Hire Adventurers: on a failure or critical failure "the cost in RP increases to 2 Resource Dice"
 * for the next attempt against the same continuous event. Returns [HIRE_ADVENTURERS_ESCALATED_RD_COST]
 * once it has been failed this event, else the base cost.
 */
fun hireAdventurersRdCost(baseRdCost: Int, hasFailedThisEvent: Boolean): Int =
    if (hasFailedThisEvent) HIRE_ADVENTURERS_ESCALATED_RD_COST else baseRdCost

/** Result of running an unrest increase through a Decadent Feasts shield. */
data class UnrestShieldResult(
    /** Unrest actually gained after the shield (0 when the shield absorbs the effect). */
    val netUnrestGain: Int,
    /** Whether the shield was spent by this effect. */
    val shieldConsumed: Boolean,
)

/**
 * Decadent Feasts critical success: "the next time this Kingdom turn you suffer an effect that
 * increases Unrest, do not increase your Unrest." The shield negates ONE unrest-increasing effect
 * entirely, then is consumed. A zero/negative unrest change does not trip the shield.
 */
fun applyUnrestShield(unrestGain: Int, shieldActive: Boolean): UnrestShieldResult =
    if (shieldActive && unrestGain > 0) UnrestShieldResult(netUnrestGain = 0, shieldConsumed = true)
    else UnrestShieldResult(netUnrestGain = unrestGain, shieldConsumed = false)

/** Base DC of the Irrigation critical-failure plague flat check (with a single crit-failed hex). */
const val IRRIGATION_PLAGUE_FLAT_CHECK_BASE_DC = 4

/**
 * Irrigation critical failure: from then on, each Event phase attempt a flat check (base DC 4) whose
 * DC rises by 1 for each additional hex holding a critically-failed Irrigation; a failure adds a
 * Plague event. Returns 0 when no hex is crit-failed (no check runs).
 *
 * (Interpretation: the stated DC 4 is the one-hex case, +1 per hex beyond the first. The alternative
 * literal reading — DC 4 + count — would never expose the printed "DC 4"; documented here for review.)
 */
fun irrigationPlagueFlatCheckDc(critFailedIrrigationHexes: Int): Int =
    if (critFailedIrrigationHexes <= 0) 0
    else IRRIGATION_PLAGUE_FLAT_CHECK_BASE_DC + (critFailedIrrigationHexes - 1)

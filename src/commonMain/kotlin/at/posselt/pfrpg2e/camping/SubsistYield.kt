package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.actor.Proficiency
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess

/**
 * Provisions yielded by one Subsist attempt.
 *
 * Moved out of `macros/Subsist.kt` unchanged so the meal loop can reuse it: an actor who picked
 * "Rations or Subsistence" with no rations left has to forage for the night, and the answer to
 * "did they feed themselves?" is this number being greater than zero.
 *
 * Three deliberate asymmetries, preserved verbatim from the macro — they are the rules, not bugs:
 * - A non-forager's success bonus falls back to 0 but its critical-success bonus falls back to 1,
 *   which is why a non-forager critical success yields 2 provisions rather than 1.
 * - The base `1 +` on a critical success is NOT multiplied by the Coyote Cloak multiplier; only the
 *   forager bonus is.
 * - UNTRAINED and TRAINED yield identically (4/4 and 8/8). The progression is not linear in rank,
 *   so it cannot be collapsed into a formula.
 */
fun calculateProvisions(
    isForager: Boolean,
    hasCoyoteCloak: Boolean,
    hasCoyoteCloakGreat: Boolean,
    degree: DegreeOfSuccess,
    survivalProficiency: Proficiency = Proficiency.UNTRAINED,
): Int {
    val criticalMultiplier = if (hasCoyoteCloakGreat) {
        4
    } else if (hasCoyoteCloak) {
        2
    } else {
        1
    }

    val increaseSuccessBy = if (isForager) {
        when (survivalProficiency) {
            Proficiency.UNTRAINED -> 4
            Proficiency.TRAINED -> 4
            Proficiency.EXPERT -> 8
            Proficiency.MASTER -> 16
            Proficiency.LEGENDARY -> 32
        }
    } else {
        0
    }
    val increaseCriticalSuccessBy = if (isForager) {
        when (survivalProficiency) {
            Proficiency.UNTRAINED -> 8
            Proficiency.TRAINED -> 8
            Proficiency.EXPERT -> 16
            Proficiency.MASTER -> 32
            Proficiency.LEGENDARY -> 64
        }
    } else {
        1
    }
    return when (degree) {
        DegreeOfSuccess.CRITICAL_FAILURE -> 0
        DegreeOfSuccess.FAILURE -> 0
        DegreeOfSuccess.SUCCESS -> 1 + increaseSuccessBy
        DegreeOfSuccess.CRITICAL_SUCCESS -> 1 + increaseCriticalSuccessBy * criticalMultiplier
    }
}

/** Whether a Subsist attempt fed the forager tonight. */
fun subsistFedActor(provisions: Int): Boolean = provisions > 0

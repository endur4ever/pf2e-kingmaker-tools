package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.ArmyCondition
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess

/** HP an army loses when it fails a Deploy Army mishap flat check. */
const val DEPLOY_ARMY_FLAT_CHECK_DAMAGE = 1

/**
 * The concrete effects a Deploy Army degree of success inflicts on the army actor, mapped from the
 * activity's RAW outcome text. The GM-confirmed apply buttons consume this (never auto-applied):
 * conditions go on the PF2EArmy actor, the flat check is rolled and applies 1 HP damage on a
 * failure, and unrest is added to the kingdom.
 */
data class DeployArmyOutcome(
    /** Conditions gained by the army. */
    val conditions: List<ArmyCondition>,
    /** DC of a flat check that, on failure, costs the army [DEPLOY_ARMY_FLAT_CHECK_DAMAGE] HP; null = none. */
    val damageFlatCheckDc: Int?,
    /** Unrest dice the kingdom gains (e.g. "1d4"), or null. */
    val unrestDice: String?,
)

/**
 * Deploy Army outcome → effects, per the activity text:
 * - Critical success / success: the army simply arrives (no mishap).
 * - Failure: weary +1, then a flat DC 6 check; on a failure the army takes 1 HP.
 * - Critical failure: the army is lost (mired until it recovers), the kingdom gains 1d4 unrest, and a
 *   flat DC 11 check; on a failure the army takes 1 HP.
 */
fun deployArmyOutcome(degree: DegreeOfSuccess): DeployArmyOutcome = when (degree) {
    DegreeOfSuccess.CRITICAL_SUCCESS -> DeployArmyOutcome(conditions = emptyList(), damageFlatCheckDc = null, unrestDice = null)
    DegreeOfSuccess.SUCCESS -> DeployArmyOutcome(conditions = emptyList(), damageFlatCheckDc = null, unrestDice = null)
    DegreeOfSuccess.FAILURE -> DeployArmyOutcome(conditions = listOf(ArmyCondition.WEARY), damageFlatCheckDc = 6, unrestDice = null)
    DegreeOfSuccess.CRITICAL_FAILURE -> DeployArmyOutcome(conditions = listOf(ArmyCondition.MIRED), damageFlatCheckDc = 11, unrestDice = "1d4")
}

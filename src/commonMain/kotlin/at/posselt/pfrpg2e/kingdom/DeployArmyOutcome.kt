package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess

/** HP an army loses when it fails a Deploy Army mishap flat check. */
const val DEPLOY_ARMY_FLAT_CHECK_DAMAGE = 1

/**
 * An army condition a Deploy Army outcome grants, identified by its PF2e slug.
 *
 * Deliberately NOT [at.posselt.pfrpg2e.data.armies.ArmyCondition]. That enum is the battle engine's
 * internal model: it carries a DAMAGED member that PF2e has no condition for (PF2e models it as
 * HP below half), and it is a plain set with no room for a value. The real army conditions are
 * Effect items in the `pf2e.kingmaker-features` compendium, three of which this activity needs and
 * two of which — `efficient` and `lost` — that enum does not have at all.
 *
 * Applying them as the real compendium effects is what makes them mean anything: the module already
 * reads `weary` and `mired` badge values back off the army actor to penalise kingdom checks
 * (see armies/Modifiers.kt), so an effect applied here is one the next Deploy Army check will feel.
 */
enum class DeployArmyEffect(val slug: String, val valued: Boolean) {
    /** Critical success: the army arrives early and in good order. */
    EFFICIENT("efficient", valued = false),

    /** Failure: a hard march. Valued — each application raises the badge by one. */
    WEARY("weary", valued = true),

    /** Critical failure: the army never arrived, and stays lost until it recovers. */
    LOST("lost", valued = false),
}

/**
 * The concrete effects a Deploy Army degree of success inflicts, mapped from the activity's RAW
 * outcome text. GM-confirmed apply buttons consume this; nothing here is applied automatically.
 */
data class DeployArmyOutcome(
    /** Conditions gained by the army. */
    val effects: List<DeployArmyEffect>,
    /** DC of a flat check that, on failure, costs the army [DEPLOY_ARMY_FLAT_CHECK_DAMAGE] HP; null = none. */
    val damageFlatCheckDc: Int?,
    /** Unrest dice the kingdom gains, or null. */
    val unrestDice: String?,
)

/**
 * Deploy Army outcome → effects, quoting the activity text:
 * - Critical success: "arrives at its destination and then becomes **efficient**" — a benefit, not
 *   an absence of one. Reading this degree as "no mishap" silently dropped the reward.
 * - Success: "The army arrives at its destination." Nothing else.
 * - Failure: "Increase the army's weary condition by 1 and attempt a flat DC 6; on a failure,
 *   reduce the army's HP by 1."
 * - Critical failure: "the army becomes **lost** until it recovers from this condition. Increase
 *   Unrest by 1d4, and attempt a DC 11 flat check; on a failure, reduce the army's HP by 1."
 */
fun deployArmyOutcome(degree: DegreeOfSuccess): DeployArmyOutcome = when (degree) {
    DegreeOfSuccess.CRITICAL_SUCCESS -> DeployArmyOutcome(
        effects = listOf(DeployArmyEffect.EFFICIENT),
        damageFlatCheckDc = null,
        unrestDice = null,
    )

    DegreeOfSuccess.SUCCESS -> DeployArmyOutcome(
        effects = emptyList(),
        damageFlatCheckDc = null,
        unrestDice = null,
    )

    DegreeOfSuccess.FAILURE -> DeployArmyOutcome(
        effects = listOf(DeployArmyEffect.WEARY),
        damageFlatCheckDc = 6,
        unrestDice = null,
    )

    DegreeOfSuccess.CRITICAL_FAILURE -> DeployArmyOutcome(
        effects = listOf(DeployArmyEffect.LOST),
        damageFlatCheckDc = 11,
        unrestDice = "1d4",
    )
}

/** Button keys for the non-condition effects, stamped once applied. */
const val DEPLOY_OFFER_FLAT_CHECK = "flatCheck"
const val DEPLOY_OFFER_UNREST = "unrest"

/** One button on the Deploy Army offer card. */
data class DeployArmyOffer(
    /** Stable key: an effect slug, or one of the DEPLOY_OFFER_* constants. */
    val key: String,
    /** The condition to apply, when this button is a condition button. */
    val effect: DeployArmyEffect?,
    /** The flat check's DC; 0 for buttons that carry no number. */
    val amount: Int,
)

/**
 * The buttons a Deploy Army result should show, in display order, excluding any already applied.
 *
 * Pure and in commonMain so the card's contents are unit-testable: the card renders exactly what
 * this returns, and each button applies exactly the one effect it was labelled with.
 */
fun deployArmyOffers(
    outcome: DeployArmyOutcome,
    alreadyApplied: Set<String> = emptySet(),
): List<DeployArmyOffer> = buildList {
    outcome.effects.forEach { add(DeployArmyOffer(key = it.slug, effect = it, amount = 0)) }
    outcome.damageFlatCheckDc?.let { add(DeployArmyOffer(DEPLOY_OFFER_FLAT_CHECK, effect = null, amount = it)) }
    outcome.unrestDice?.let { add(DeployArmyOffer(DEPLOY_OFFER_UNREST, effect = null, amount = 0)) }
}.filterNot { it.key in alreadyApplied }

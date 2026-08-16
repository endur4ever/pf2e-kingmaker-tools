package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.kingdom.data.getChosenFeats
import at.posselt.pfrpg2e.kingdom.data.getChosenFeatures
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.tpl
import js.objects.recordOf

/** Both printings of the feat share the mechanic. */
val PULL_TOGETHER_FEATS = setOf("pull-together", "pull-together-vk")

/** The current flat-check DC, defaulting to the feat's printed 11. */
fun KingdomData.pullTogetherDc(): Int = pullTogetherCurrentDC ?: PULL_TOGETHER_BASE_DC

/** Whether this kingdom has taken either printing of Pull Together. */
fun KingdomData.hasPullTogether(): Boolean {
    val chosenFeatures = getChosenFeatures(getExplodedFeatures())
    return getChosenFeats(chosenFeatures).any { it.feat.id in PULL_TOGETHER_FEATS }
}

/**
 * The Pull Together button for a critical-failure result card, or an empty string.
 *
 * The feat reads: "Once per Kingdom turn when you roll a critical failure on a Kingdom skill check,
 * attempt a DC 11 flat check. If this succeeds … treat the Kingdom skill check result as failure
 * instead." So it is offered only on a critical failure, only once a turn, and only to a kingdom
 * that actually took the feat.
 */
suspend fun buildPullTogetherButton(
    actor: KingdomActor,
    degree: DegreeOfSuccess,
): String {
    if (degree != DegreeOfSuccess.CRITICAL_FAILURE) return ""
    val kingdom = actor.getKingdom() ?: return ""
    if (!canUsePullTogether(
            isCriticalFailure = true,
            alreadyUsedThisTurn = kingdom.pullTogetherUsedThisTurn == true,
        )
    ) return ""
    if (!kingdom.hasPullTogether()) return ""
    return tpl(
        path = "chatmessages/pull-together-offer.hbs",
        ctx = recordOf(
            "actorUuid" to actor.uuid,
            "label" to t("kingdom.pullTogether.offer", recordOf("dc" to kingdom.pullTogetherDc().toString())),
        ),
    )
}

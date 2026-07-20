package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.determineDegreeOfSuccess

/**
 * A banked circumstance bonus from Request Foreign Aid. RAW the aid applies to a FUTURE check AFTER
 * you see its result (reroll-insurance), not up front — so success banks an entry here and the GM
 * spends it on a later failed check to re-evaluate the degree.
 */
data class BankedBonus(
    val value: Int,
    val source: String,
    val gainedTurn: Int,
    /** Turn after which the bonus lapses; null = no expiry (documented default per RAW/V&K). */
    val expiresTurn: Int? = null,
)

/** A banked bonus is expired once the current turn has passed its [BankedBonus.expiresTurn]. */
fun BankedBonus.isExpired(currentTurn: Int): Boolean = expiresTurn != null && currentTurn > expiresTurn

/** Banked bonuses still spendable on [currentTurn] (not yet expired). */
fun List<BankedBonus>.spendable(currentTurn: Int): List<BankedBonus> = filter { !it.isExpired(currentTurn) }

/**
 * The degree of success after applying a banked circumstance [bonus] to a check total. The bonus
 * modifies the TOTAL (not the die), so any natural-1/natural-20 degree adjustment already baked into
 * [dieValue] is preserved. Reuses the same [determineDegreeOfSuccess] the check pipeline uses.
 */
fun degreeAfterSpendingAid(dc: Int, total: Int, dieValue: Int, bonus: Int): DegreeOfSuccess =
    determineDegreeOfSuccess(dc = dc, total = total + bonus, dieValue = dieValue)

/**
 * Whether spending [bonus] would actually improve this check's degree — used to decide whether the
 * "Spend banked aid" button is worth offering on a failed result (no point spending it otherwise).
 */
fun aidWouldImprove(dc: Int, total: Int, dieValue: Int, bonus: Int): Boolean {
    val before = determineDegreeOfSuccess(dc = dc, total = total, dieValue = dieValue)
    val after = degreeAfterSpendingAid(dc = dc, total = total, dieValue = dieValue, bonus = bonus)
    return after > before
}

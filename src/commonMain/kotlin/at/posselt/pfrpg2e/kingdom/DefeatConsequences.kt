package at.posselt.pfrpg2e.kingdom

/**
 * Suggested fallout from losing a war battle (all GM-confirmed offers, never auto-applied). Without
 * these, losing every army to an invasion is mechanically identical to never fighting.
 */
data class DefeatConsequences(
    /** Unrest the kingdom gains from the defeat. */
    val unrestGain: Int,
    /** War-pressure spike (the enemy presses its advantage). */
    val pressureJump: Int,
    /** How much the threat's escalation rises (0 when already at max). */
    val escalationBump: Int,
    /** Whether the defeat should offer to spawn the war-threat-arrival event now (invasion lands). */
    val spawnArrivalEvent: Boolean,
)

/**
 * Pure defeat-consequence calculator. Numbers are deliberately modest and scale with how far the
 * threat has escalated and what the defeat cost:
 *
 * - unrestGain = 1 (the loss itself) + up to 3 for armies lost + 2 if a settlement was the target.
 * - pressureJump = 2 + up to 3 scaled by the threat's escalation fraction (a near-max threat that
 *   beats you surges hardest).
 * - escalationBump = 1, unless the threat is already at its maximum escalation (then 0).
 * - spawnArrivalEvent = a settlement-targeting threat that is already at max escalation has arrived.
 *
 * @param threatEscalation current escalation (clamped to 0..[maxEscalation])
 * @param maxEscalation the threat's escalation ceiling (min 1)
 * @param armiesLost armies the kingdom lost in the battle
 * @param settlementTargeted whether the threat targets a settlement
 */
fun calculateDefeatConsequences(
    threatEscalation: Int,
    maxEscalation: Int,
    armiesLost: Int,
    settlementTargeted: Boolean,
): DefeatConsequences {
    val max = maxEscalation.coerceAtLeast(1)
    val escalation = threatEscalation.coerceIn(0, max)
    val unrestGain = 1 + armiesLost.coerceIn(0, 3) + if (settlementTargeted) 2 else 0
    val pressureJump = 2 + (escalation * 3 / max)
    val escalationBump = if (escalation < max) 1 else 0
    val spawnArrivalEvent = settlementTargeted && escalation >= max
    return DefeatConsequences(
        unrestGain = unrestGain,
        pressureJump = pressureJump,
        escalationBump = escalationBump,
        spawnArrivalEvent = spawnArrivalEvent,
    )
}

/** One individually-applicable line on the GM defeat-offer card. */
data class DefeatOffer(
    /** Stable key, written into [RawArmyBattle.defeatConsequencesApplied] once applied. */
    val key: String,
    /** The delta this button applies; 0 for the arrival-event offer, which carries no number. */
    val amount: Int,
)

const val DEFEAT_OFFER_UNREST = "unrest"
const val DEFEAT_OFFER_PRESSURE = "pressure"
const val DEFEAT_OFFER_ESCALATION = "escalation"
const val DEFEAT_OFFER_ARRIVAL = "arrival"

/**
 * The buttons a defeat card should show, in display order, excluding any the GM has already
 * applied. Zero-valued consequences are omitted so the card never offers a no-op button.
 *
 * Kept pure and in commonMain so the card's contents are unit-testable: the dialog only renders
 * what this returns, and each button applies exactly the [DefeatOffer.amount] it was labelled with.
 *
 * @param consequences output of [calculateDefeatConsequences]
 * @param alreadyApplied keys previously applied for this battle (idempotency across re-renders,
 *   re-posts, and a GM clicking the same button twice)
 */
fun defeatOffers(
    consequences: DefeatConsequences,
    alreadyApplied: Set<String> = emptySet(),
): List<DefeatOffer> = buildList {
    if (consequences.unrestGain > 0) add(DefeatOffer(DEFEAT_OFFER_UNREST, consequences.unrestGain))
    if (consequences.pressureJump > 0) add(DefeatOffer(DEFEAT_OFFER_PRESSURE, consequences.pressureJump))
    if (consequences.escalationBump > 0) add(DefeatOffer(DEFEAT_OFFER_ESCALATION, consequences.escalationBump))
    if (consequences.spawnArrivalEvent) add(DefeatOffer(DEFEAT_OFFER_ARRIVAL, 0))
}.filterNot { it.key in alreadyApplied }

package at.posselt.pfrpg2e.kingdom

/**
 * Per-turn cap on the bonus Resource Dice a kingdom can gain from caravan SALES, closing the
 * buy-low/sell-high arbitrage loop (HOLE B). The cap scales with progression — a kingdom can move
 * more goods as it grows — so it is set to the kingdom level (minimum 1). Documented + surfaced in
 * the dispatch preview and the delivery chat line when it binds.
 */
fun caravanBonusRdCap(kingdomLevel: Int): Int = kingdomLevel.coerceAtLeast(1)

/** Applies [cap] to an accumulated [bonusRd], never returning more than the cap (nor below zero). */
fun capCaravanBonusRd(bonusRd: Int, cap: Int): Int = bonusRd.coerceIn(0, cap.coerceAtLeast(0))

/**
 * Embargo predicate (HOLE A): a caravan may not be dispatched to a partner the kingdom is at war
 * with. War is not merely a raid-DC penalty — running profitable trade to the faction besieging you
 * is blocked outright at dispatch (in-transit shipments when war is later declared are handled
 * separately as a GM-confirmed recall/press-on offer).
 */
fun canDispatchCaravanTo(partnerAtWar: Boolean): Boolean = !partnerAtWar

/**
 * Partner names whose war flag just flipped on.
 *
 * Blocking new dispatches is only half of the embargo: shipments already on the road to a partner
 * you have just declared war on need a decision, and taking it away from the GM silently would be
 * the confiscation this card exists to prevent.
 */
fun partnersNewlyAtWar(before: Map<String, Boolean>, after: Map<String, Boolean>): List<String> =
    after.filter { (name, atWar) -> atWar && before[name] == false }.keys.sorted()

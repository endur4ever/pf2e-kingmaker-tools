package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.resting.DAY_SECONDS
import at.posselt.pfrpg2e.utils.worldTimeSeconds
import com.foundryvtt.core.Game
import com.foundryvtt.core.game

/**
 * THE write funnel for the rumor store (plan section 5.4). Every mutation -- the curator's
 * accept, the daily tick, the board's GM controls, the offer handlers -- reads once, transforms,
 * caps and writes once through here. One read-modify-write path is the answer to the camping
 * flag's deep-clone race: there is nothing else that writes `CampingData.rumors`, so two writers
 * can only be two calls to this funnel.
 *
 * GM-gated at the top rather than at each caller: players interact with rumors only through
 * GM-confirmed offers, so a non-GM reaching this at all is a caller bug. Single-writer holds PER
 * CLIENT -- two GM clients editing across the same day-boundary tick can still last-write-wins,
 * the same residual the kingdom flag carries; the funnel narrows the race, it cannot end it.
 */
suspend fun CampingActor.updateRumors(block: (List<Rumor>) -> List<Rumor>) {
    if (!game.user.isGM) return
    val camping = getCamping() ?: return
    val raws = camping.rumors ?: emptyArray()
    // rows this build cannot parse (an unknown state from a newer build, a hand-edited flag) are
    // CARRIED THROUGH the write untouched, not silently deleted: dropping them from evaluation is
    // the contract, dropping them from STORAGE would make one round trip through an older build
    // destructive -- the same rule the downtime tick documents for its own unknown rows
    val unparseable = raws.filter { it.toModel() == null }
    val next = capRumors(block(raws.mapNotNull { it.toModel() }))
    camping.rumors = (next.map { it.toRaw() } + unparseable).toTypedArray()
    setCamping(camping)
}

/** The world-clock day number, the unit the lifecycle ages in. */
fun currentWorldDay(game: Game): Int = game.time.worldTimeSeconds.floorDiv(DAY_SECONDS)

/**
 * Ages every camping actor's rumor store across [daysPassed] world days. SILENT by design in
 * this phase: fresh -> stale never narrates, and the expiry beats become offers in the offers
 * phase -- aging must not spam a whisper per rumor per week meanwhile.
 */
suspend fun tickRumorLifecycles(game: Game, daysPassed: Int) {
    if (daysPassed <= 0) return
    val today = currentWorldDay(game)
    for (actor in game.getCampingActors()) {
        val hasRumors = actor.getCamping()?.rumors?.isNotEmpty() == true
        if (!hasRumors) continue
        actor.updateRumors { rumors -> tickRumors(rumors, currentDay = today, days = daysPassed).rumors }
        // offers AFTER the aging write, from the post-tick store: candidates are EXPIRED rows
        // whose beat was never offered, and posting stamps them so tomorrow's tick stays quiet
        val due = beatCandidates(actor.getCamping()?.rumorList() ?: emptyList(), today)
        postRumorExpiryOffers(game, actor, due, today)
    }
}

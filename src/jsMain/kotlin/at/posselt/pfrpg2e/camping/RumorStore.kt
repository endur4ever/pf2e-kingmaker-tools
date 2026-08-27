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
 * GM-confirmed offers, so a non-GM reaching this at all is a caller bug, and refusing here keeps
 * the store single-writer even then.
 */
suspend fun CampingActor.updateRumors(block: (List<Rumor>) -> List<Rumor>) {
    if (!game.user.isGM) return
    val camping = getCamping() ?: return
    val next = capRumors(block(camping.rumorList()))
    camping.rumors = next.map { it.toRaw() }.toTypedArray()
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
    }
}

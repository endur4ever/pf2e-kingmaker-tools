package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.structures.RawNpcEntry
import at.posselt.pfrpg2e.kingdom.structures.RawPopulationRoster

/**
 * Carry the memory ledger across a roster rebuilt from the evaluated settlement model.
 *
 * The commonMain [at.posselt.pfrpg2e.data.kingdom.settlements.NpcEntry] deliberately holds only
 * display data, so every round trip through it -- `parseSettlement(...)` then `toRaw()` -- drops
 * `memoryTracked`, `memoryLog` and `attitudeScore`. The roster top-up does exactly that round
 * trip while promising that user state sticks, so without this merge one "Generate NPCs" click
 * erased every tracked resident's history in that settlement.
 *
 * Matched by **id**, not position: roster ids are stable and the rebuild may insert new entries
 * anywhere, so position means nothing here (unlike the group merge, whose rows have no ids).
 * An id with no predecessor is a genuinely new resident and keeps its empty ledger.
 */
fun mergeNpcMemoryFields(
    rebuilt: RawPopulationRoster,
    existing: RawPopulationRoster?,
): RawPopulationRoster {
    val previous = (existing?.npcs ?: emptyArray()).associateBy { it.id }
    if (previous.isEmpty()) return rebuilt
    return RawPopulationRoster(
        npcs = (rebuilt.npcs ?: emptyArray()).map { npc ->
            val prior = previous[npc.id] ?: return@map npc
            RawNpcEntry.copy(
                npc,
                memoryTracked = prior.memoryTracked,
                memoryLog = prior.memoryLog,
                attitudeScore = prior.attitudeScore,
            )
        }.toTypedArray()
    )
}

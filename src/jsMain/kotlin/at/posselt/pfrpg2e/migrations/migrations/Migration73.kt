package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.structures.RawNpcMemoryEntry
import com.foundryvtt.core.Game

/**
 * NPC memory backfill (docs/plans/2026-07-09-plan-npc-memory.md 3.1; the plan's placeholder
 * number re-derives to 73, the contiguous next).
 *
 * Walks settlements -> roster -> npcs and seeds memoryLog to empty ONLY where absent -- the tick
 * appends, and an append against undefined throws. memoryTracked and attitudeScore stay null:
 * null tracking IS the untracked state, and a null score reads as 0 without inventing a write.
 */
class Migration73 : Migration(73) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        // settlements is typed non-null but runtime-undefined on pre-settlement kingdoms
        val settlements = kingdom.settlements.unsafeCast<Array<at.posselt.pfrpg2e.kingdom.structures.RawSettlement>?>() ?: return
        settlements.forEach { settlement ->
            settlement.populationRoster?.npcs?.forEach { npc ->
                if (npc.memoryLog == null) {
                    npc.memoryLog = emptyArray<RawNpcMemoryEntry>()
                }
            }
        }
    }
}

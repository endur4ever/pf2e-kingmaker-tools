package at.posselt.pfrpg2e.kingdom.structures

import at.posselt.pfrpg2e.data.kingdom.settlements.PopulationRoster
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RawSettlement {
    var sceneId: String
    var lots: Int
    var level: Int
    var type: String // 'capital' | 'settlement'
    var layoutType: String // 'rigid' | 'freeForm'
    var secondaryTerritory: Boolean
    var manualSettlementLevel: Boolean?
    var waterBorders: Int
    var populationRoster: RawPopulationRoster?
    var terrain: String? // SettlementTerrain value, e.g. "forest", "swamp"
    var hexKey: String? // realm-map hex this settlement occupies (for caravan routing)

    /**
     * Token ids of structures razed by a siege. A ruined structure keeps its token on the map but
     * stops contributing bonuses, storage and block occupancy until it is rebuilt — clearing the id
     * restores it, so a sack is recoverable rather than destructive.
     */
    var destroyedStructureIds: Array<String>?
}

@JsPlainObject
external interface RawPopulationRoster {
    var npcs: Array<RawNpcEntry>?
}

@JsPlainObject
external interface RawNpcEntry {
    var id: String
    var name: String
    var occupation: String
    var notes: String?

    // NPC memory ledger (docs/plans/2026-07-09-plan-npc-memory.md 3.1) -- additive, nullable.
    /** GM opted this resident into memory tracking. Null/false = untracked: no log, no cost. */
    var memoryTracked: Boolean?
    /** Oldest first, capped at MEMORY_LOG_CAP; null on legacy data until Migration73 seeds it. */
    var memoryLog: Array<RawNpcMemoryEntry>?
    /** Running total of deltas, deliberately NEVER recomputed from the log; null = 0. */
    var attitudeScore: Int?
}

@JsPlainObject
external interface RawNpcMemoryEntry {
    var ruleId: String
    var turn: Int
    var delta: Int
    /** Interpolation value for the entry's i18n key, e.g. a caravan partner's name. */
    var subject: String?
}

fun PopulationRoster.toRaw(): RawPopulationRoster =
    RawPopulationRoster(
        npcs = npcs.map { npc ->
            RawNpcEntry(
                id = npc.id,
                name = npc.name,
                occupation = npc.occupation,
                notes = npc.notes,
            )
        }.toTypedArray()
    )

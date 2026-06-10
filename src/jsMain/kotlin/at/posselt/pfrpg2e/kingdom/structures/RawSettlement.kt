package at.posselt.pfrpg2e.kingdom.structures

import at.posselt.pfrpg2e.data.kingdom.settlements.PopulationRoster
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RawSettlement {
    var sceneId: String
    var lots: Int
    var level: Int
    var type: String // 'capital' | 'settlement';
    var layoutType: String // 'rigid' | 'freeForm'
    var secondaryTerritory: Boolean
    var manualSettlementLevel: Boolean?
    var waterBorders: Int
    var populationRoster: RawPopulationRoster?
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

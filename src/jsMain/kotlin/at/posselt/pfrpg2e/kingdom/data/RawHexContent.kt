package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RawHexContent {
    var id: String
    var hexKey: String
    var type: String          // HexContentType.value
    var name: String
    var visibility: String    // HexContentVisibility.value
    var gmNotes: String
    var playerText: String
    var suppressesEncounters: Boolean?
    /** GM queued an encounter here (war-threat arrival offer); cleared manually once run. */
    var pendingEncounter: Boolean?
    var travelModifier: Int?
    var linkedQuestId: String?       // legacy single-quest link (kept for back-compat)
    var linkedUuid: String?          // legacy single-document link (kept for back-compat)
    var linkedQuestIds: Array<String>?   // referenced kingdom quests
    var linkedUuids: Array<String>?      // referenced Foundry documents (journals/actors/scenes/items)
    var linkedWarThreatId: String?       // referenced war threat
    var icon: String?
}

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
    var travelModifier: Int?
    var linkedQuestId: String?
    var linkedUuid: String?
    var icon: String?
}

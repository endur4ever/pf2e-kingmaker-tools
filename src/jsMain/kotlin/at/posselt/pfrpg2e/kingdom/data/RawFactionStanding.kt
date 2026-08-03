package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * One entry in a faction's standing change log: a signed [delta] applied on a given
 * kingdom [turn], with a [reason] (an i18n key or free GM text) explaining the shift.
 */
@JsPlainObject
external interface RawFactionStandingEntry {
    var turn: Int
    var delta: Int
    var reason: String
}

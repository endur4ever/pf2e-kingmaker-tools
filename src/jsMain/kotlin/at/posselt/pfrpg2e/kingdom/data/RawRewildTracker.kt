package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * One cleared-but-unclaimed hex's re-wild timer
 * (`docs/plans/2026-07-09-plan-map-dynamism.md` SS2.2).
 *
 * A SIDE-TABLE: `kingmaker.state` owns the cleared/claimed booleans and has nowhere to keep a
 * timestamp, so the kingdom flag times the pressure. Reconciled every End Turn against the live
 * hex set; a hex that leaves cleared-unclaimed (claimed, re-wilded, un-cleared) drops its tracker
 * and a later re-clear starts a fresh one.
 */
@JsPlainObject
external interface RawRewildTracker {
    /** Native Kingmaker hex key, string form -- matches kingmaker.state.hexes keys. */
    var hexKey: String
    /** Kingdom turn this hex was first observed cleared-and-unclaimed. */
    var clearedSinceTurn: Int
    /** True once this cycle's re-wild offer was resolved (applied or kept); quiet until the
     * tracker resets by the hex leaving and re-entering the cleared-unclaimed set. */
    var offerConsumed: Boolean?
}

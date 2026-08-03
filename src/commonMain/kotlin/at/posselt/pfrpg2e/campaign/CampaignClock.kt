package at.posselt.pfrpg2e.campaign

import kotlinx.js.JsPlainObject

@JsPlainObject
external interface CampaignClock {
    val id: String              // unique UUID
    val label: String           // "Stag Lord Deadline", "Varnhold Vanint"
    var turnsRemaining: Int     // current countdown value (>= 0)
    val maxTurns: Int           // starting value (for progress bar, >= 1)
    val description: String     // GM-facing description of what this clock tracks
    var pauseOnExpiry: Boolean  // Decision 3: if true, stop at 0 and wait for GM
    var expired: Boolean        // true once turnsRemaining reaches 0 (regardless of soft-pause)
    var active: Boolean         // false = manually deactivated by GM
    val expiryConsequenceUnrest: Int  // unrest to add on expiry trigger (0 = none)
    val expiryMessage: String   // human-readable consequence description
}

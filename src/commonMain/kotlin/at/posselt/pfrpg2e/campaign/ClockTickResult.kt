package at.posselt.pfrpg2e.campaign

import kotlinx.js.JsPlainObject

enum class ClockEventType { ADVANCED, EXPIRED, TRIGGERED, PAUSED }

@JsPlainObject
external interface ClockTickEvent {
    val clockId: String
    val type: String            // ClockEventType value
    val label: String           // clock label (for chat output)
    val oldTurns: Int           // turns before tick
    val newTurns: Int           // turns after tick
    val message: String?        // expiryMessage or custom event description
    val unrestChange: Int       // change to apply to kingdom unrest (from expiryConsequenceUnrest)
}

@JsPlainObject
external interface ClockTickResult {
    val events: Array<ClockTickEvent>
    val updatedClocks: Array<CampaignClock>
    val totalUnrestChange: Int  // sum of all unrest changes this tick
}

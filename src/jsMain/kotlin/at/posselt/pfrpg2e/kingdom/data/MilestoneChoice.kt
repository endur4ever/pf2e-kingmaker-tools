package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

@JsPlainObject
external interface MilestoneChoice {
    var id: String
    var completed: Boolean
    var enabled: Boolean

    /**
     * True once a GM has answered this milestone's auto-detected award offer with "Dismiss".
     *
     * Auto-detection is level-triggered on standing world state — a road stays built, a claimed
     * region stays claimed — so without a record of the refusal the End Turn offer re-posts every
     * single turn for the rest of the campaign. Awarding sets [completed]; dismissing sets this.
     * Either way the milestone has been answered once and is never offered again.
     *
     * Nullable for kingdoms saved before this field existed; absent reads as "not dismissed".
     */
    var offerDismissed: Boolean?
}

package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.kingdom.KingdomData
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RawQuestRewards {
    var rp: Int?
    var xp: Int?
    var unrest: Int?
    var food: Int?
    var lumber: Int?
    var stone: Int?
    var ore: Int?
    var luxuries: Int?
    var other: String? // freeform / custom bounty reward text
}

/**
 * Snapshot of the exact reward deltas a quest applied when it was completed, so a later
 * "reopen" can reverse them precisely even where completion clamped values (unrest at
 * anarchy, commodities at storage). Null until the quest is completed; cleared on reopen.
 * All fields are the ACTUAL applied delta (post-clamp), not the nominal reward.
 */
@JsPlainObject
external interface RawQuestCompletionSnapshot {
    var priorStatus: String
    /** Kingdom turn the quest was completed on, so reopen can warn when dependent state has drifted. */
    var turn: Int
    var rp: Int
    var xp: Int
    var level: Int
    var unrest: Int
    var food: Int
    var lumber: Int
    var luxuries: Int
    var ore: Int
    var stone: Int
}

/**
 * Snapshot of the entire kingdom state captured at the start of an End Turn, so a later
 * "undo-end-turn" can restore it exactly. Stored as an app-flag on the KingdomActor.
 */
@JsPlainObject
external interface EndTurnSnapshot {
    /** The kingdom data deep-cloned before any End Turn mutations. */
    var kingdom: KingdomData
    /** The turn number this snapshot was ending when the snapshot was taken (snapshotTurn == currentTurn at snapshot time). */
    var snapshotTurn: Int
    /** The turn-wizard-state flag (performed-activity counts etc.) deep-cloned pre-tick; restored on undo. */
    var turnWizardState: Any?
    /** Ids of party-inventory items created by shipment delivery this turn; deleted on undo to avoid duplication. */
    var deliveredItemIds: Array<String>?
}

@JsPlainObject
external interface RawQuest {
    var id: String
    var title: String
    var description: String
    var giver: String
    var status: String // "active" | "completed"
    var type: String   // "explore_hex" | "claim_hex" | "build_structure" | "clear_hex" | "assign_leader" | "other"
    var category: String? // "main_story" | "side" | "mythic" | "companion"
    var level: Int?    // recommended party/quest level (null or 0 = unset)
    var target: String?
    var rewards: RawQuestRewards
    var flavorTextCompleted: String
    var notes: String? // freeform GM-only notes
    var hidden: Boolean? // GM-only: greyed out for the GM, hidden from players entirely
    var source: String? // source material reference — book + page, a URL, etc.
    var createdAt: Double? // epoch millis when the quest was first created
    var updatedAt: Double? // epoch millis of the most recent edit
    var completionSnapshot: RawQuestCompletionSnapshot? // reward deltas captured on completion, for reopen
}

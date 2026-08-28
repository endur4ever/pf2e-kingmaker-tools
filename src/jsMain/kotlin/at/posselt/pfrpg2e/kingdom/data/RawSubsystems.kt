package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * The Influence & Research subsystem store (plan: docs/plans/2026-07-09-plan-influence-research.md).
 *
 * WORLD-scoped, not kingdom-scoped: the house rules explicitly support running the flavor without
 * Kingdom Building, and these subsystems must work in a campaign with no kingdom actor at all.
 * The whole store round-trips as one JSON string setting; world settings replicate to every
 * client, so hiding unrevealed rows happens at context-build time -- the blob itself is
 * REPLICATED, NOT SECRET, the same accepted posture as GM-authored kingdom-flag data.
 */

@JsPlainObject
external interface RawSubsystemCheck {
    /** Skill slug or freeform label, e.g. "diplomacy", "kingmaker-lore". */
    var skill: String
    var dc: Int
    /** Shown to players once discovered/revealed; default GM-only. */
    var revealed: Boolean?
    var note: String?
}

@JsPlainObject
external interface RawSubsystemThreshold {
    /** Point value at which this unlocks. */
    var points: Int
    /** FREEFORM effect text -- no automation in v1; the offer card carries it to the GM. */
    var effect: String
    /** The threshold offer fired and was answered; never re-offered. */
    var offerConsumed: Boolean?
    var revealedToPlayers: Boolean?
}

@JsPlainObject
external interface RawSubsystemParticipant {
    var uuid: String
    var name: String
    /** This PC's running contribution to the pool. */
    var points: Int
    /** Advisory one-action-per-round bookkeeping (plan open question 5: advisory, not enforced). */
    var actedThisRound: Boolean?
}

@JsPlainObject
external interface RawSubsystemCheckEntry {
    var timestamp: Double
    var participantUuid: String?
    var skill: String
    /** A SubsystemOutcome.value; an unrecognised string is logged and skipped, never thrown on. */
    var outcome: String
    /** Signed points ACTUALLY applied -- post-trait, post-clamp -- not what the dice deserved. */
    var pointsDelta: Int
    var note: String?
}

@JsPlainObject
external interface RawInfluenceTrait {
    /** e.g. "flattery", "appeals to greed". */
    var label: String
    /** Resistance => negative, weakness => positive; applied to a matched check's base gain. */
    var delta: Int
    var note: String?
}

@JsPlainObject
external interface RawInfluenceEncounter {
    var id: String
    /** Encounter/scene label, e.g. "Restov Banquet". */
    var name: String
    var npcName: String
    var description: String
    var level: Int?
    var influencePoints: Int
    /** "active" | "resolved". */
    var status: String
    var discoveries: Array<RawSubsystemCheck>?
    var influenceSkills: Array<RawSubsystemCheck>?
    var thresholds: Array<RawSubsystemThreshold>?
    var resistances: Array<RawInfluenceTrait>?
    var weaknesses: Array<RawInfluenceTrait>?
    var participants: Array<RawSubsystemParticipant>?
    var checkLog: Array<RawSubsystemCheckEntry>?
    var visibleToPlayers: Boolean?
    var createdAt: Double?
    var updatedAt: Double?
}

@JsPlainObject
external interface RawResearchProject {
    var id: String
    /** "Vordakai's Library", "Cure for the Bloom". */
    var name: String
    var description: String
    var libraryName: String?
    var libraryLevel: Int?
    var researchPoints: Int
    var maxResearchPoints: Int?
    /** "active" | "resolved". */
    var status: String
    var checks: Array<RawSubsystemCheck>?
    var thresholds: Array<RawSubsystemThreshold>?
    var checkLog: Array<RawSubsystemCheckEntry>?
    /** The ongoing kingdom event this project was spawned from, when any. */
    var sourceEventId: String?
    var visibleToPlayers: Boolean?
    var createdAt: Double?
    var updatedAt: Double?
}

@JsPlainObject
external interface RawSubsystemStore {
    var influenceEncounters: Array<RawInfluenceEncounter>?
    var researchProjects: Array<RawResearchProject>?
}

package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * A durable per-turn history record (gap analysis item 2).
 * Captures a snapshot of key kingdom state at end-of-turn so the
 * Session Prep tab can show a "Recent Turns" recap section.
 * All fields use simple primitives so the record survives JSON round-trips
 * without custom serializers.
 */
@JsPlainObject
external interface RawTurnRecord {
    var turn: Int
    var timestamp: String
    var fame: Int
    var resourcePoints: Int
    var consumption: Int
    var unrest: Int
    var xpAwarded: Int?
    var clockEvents: Array<String>?
    var warPressure: Int?
    var pressurePerTurn: Int?
    var notes: String?
    /** Player-safe gazette: the same notes minus GM-only segments (secret campaign-clock progress).
     * Nullable for migration: legacy records have none, so the player recap simply shows no notes. */
    var playerNotes: String?
    var level: Int?
    var size: Int?
    var ruinCorruption: Int?
    var ruinCrime: Int?
    var ruinDecay: Int?
    var ruinStrife: Int?

    /** Per-actor contribution tallies accrued during this turn (renown-spotlight SS2.3).
     * Nullable: legacy records have none, so the Spotlight shows nothing for old turns rather
     * than asserting that nobody contributed. */
    var contributions: Array<RawTurnContribution>?

    /**
     * Ids of the council votes CLOSED during this turn -- the back-reference that lets a turn's
     * recap say which decisions the table made that month.
     *
     * Nullable and never backfilled: a legacy record genuinely closed no votes, and seeding an
     * empty array would assert that the council met and decided nothing rather than that nobody
     * was recording it. Same reasoning as [contributions] (see Migration69's KDoc).
     */
    var closedVoteIds: Array<String>?
}

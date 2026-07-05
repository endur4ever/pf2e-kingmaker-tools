package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * Schema for storing companion/NPC travel and state data on native Foundry VTT Actor flags.
 * Synced to/from native character-type actors via [at.posselt.pfrpg2e.utils.setAppFlag].
 */
@JsPlainObject
external interface RawCharacter {
    /** Display name of the companion/NPC */
    var name: String
    /** Optional reference to the Foundry Actor UUID this companion is bound to */
    var actorUuid: String?
    /** Travel: destination grid X coordinate (hex column) */
    var destinationX: Int?
    /** Travel: destination grid Y coordinate (hex row) */
    var destinationY: Int?
    /** Travel: speed in hexes per day */
    var speed: Int
    /** Travel: estimated time of arrival in days (counted down daily off the world clock) */
    var eta: Int?
    /** Plot hook or GM note associated with this companion */
    var plotHook: String?
    /** Whether this companion is currently traveling */
    var traveling: Boolean
    /** Whether this companion is active (false = sidelined/inactive) */
    var active: Boolean
    /** Companion role: "companion" for player companions, "npc" for NPCs */
    var role: String
    /** Optional portrait/image path */
    var img: String?
    /** Companion relationship influence (0-12 Influence Points, PF2e Influence subsystem). Default 0. */
    var influence: Int
    /** Whether this companion is available for camp activities (not traveling, not busy). Default true. */
    var campAvailable: Boolean
    /** Discovery/relationship stage: "unknown" | "introduced" | "established" | "trusted" | "bonded". Default "unknown". */
    var discoveryStatus: String
    /** IDs of personal quests linked to this companion. */
    var personalQuestIds: Array<String>
    /** Companion level (1-20). Default 1. */
    var level: Int
    /** Accumulated XP toward next level (0-999). Default 0. */
    var xp: Int
    /** Expedition status: "available" | "onExpedition" | "unavailable". Default "available". */
    var expeditionStatus: String
    /** Days remaining until injury heals (null when not injured). Default null. */
    var injuryDaysRemaining: Int?

    /** Total expeditions this companion has participated in (career counter). Nullable for back-compat. */
    var careerExpeditions: Int?

    /** Critical successes (triumphs) across all expeditions. Nullable for back-compat. */
    var careerTriumphs: Int?

    /** Expeditions that accrued injuries (scars). Nullable for back-compat. */
    var careerScars: Int?
}

/**
 * Default companion state for a new companion actor.
 */
fun RawCharacter(
    name: String,
    actorUuid: String? = null,
): RawCharacter =
    js("{ name: name, actorUuid: actorUuid, speed: 0, traveling: false, active: true, role: 'companion', plotHook: '', influence: 0, campAvailable: true, discoveryStatus: 'unknown', personalQuestIds: [], level: 1, xp: 0, expeditionStatus: 'available', injuryDaysRemaining: null, careerExpeditions: null, careerTriumphs: null, careerScars: null }")
        .unsafeCast<RawCharacter>()

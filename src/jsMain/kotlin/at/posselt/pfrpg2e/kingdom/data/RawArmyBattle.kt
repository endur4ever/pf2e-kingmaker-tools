package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * A single army's mutable battle state within an [RawArmyBattle].
 * Tracks per-army HP, conditions, and XP earned during the encounter.
 */
@JsPlainObject
external interface RawBattleArmy {
    var armyActorUuid: String
    var name: String
    var level: Int
    var currentHp: Int
    var maxHp: Int
    var conditions: Array<String>
    var xp: Int
}

/**
 * Represents a tactical battle between attacker and defender armies (roadmap #12, phase 1).
 * Tracks the battle round, terrain, participating armies on each side, a log of events,
 * and the overall battle status.
 */
@JsPlainObject
external interface RawArmyBattle {
    var id: String
    var threatId: String?
    var name: String
    var round: Int
    var terrain: String?
    var attackers: Array<RawBattleArmy>
    var defenders: Array<RawBattleArmy>
    var log: Array<String>
    var status: String
}

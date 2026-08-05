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

    /**
     * Keys of the defeat consequences the GM has already applied from this battle's offer card
     * (see [at.posselt.pfrpg2e.kingdom.defeatOffers]). Nullable and defaulted to empty on read, so
     * battles persisted before defeat consequences existed load unchanged — same approach as
     * [RawWarThreat.offerConsumed], which likewise needs no migration.
     */
    var defeatConsequencesApplied: Array<String>?
}

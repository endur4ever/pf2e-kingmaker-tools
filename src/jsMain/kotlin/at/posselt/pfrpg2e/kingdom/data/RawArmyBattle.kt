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

    /**
     * Flat bonus to this army's AC for the whole battle. Currently granted to an army garrisoned in
     * a settlement that has a Garrison structure, resolved once when the battle is created so the
     * number the GM sees in the rows is the number the engine fights with.
     */
    var defenseBonus: Int?
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
     * The status this battle held when the tick archived it.
     *
     * Archiving OVERWRITES status, so without this the fact that a battle was won is destroyed on
     * the next End Turn -- which made the first-battle-won deed a one-shot offer that vanished
     * forever if the GM ignored one digest. Nullable: battles archived before this field existed
     * simply report no recorded outcome.
     */
    var archivedOutcome: String?

    /**
     * Keys of the defeat consequences the GM has already applied from this battle's offer card
     * (see [at.posselt.pfrpg2e.kingdom.defeatOffers]). Nullable and defaulted to empty on read, so
     * battles persisted before defeat consequences existed load unchanged — same approach as
     * [RawWarThreat.offerConsumed], which likewise needs no migration.
     */
    var defeatConsequencesApplied: Array<String>?

    /**
     * Same idea as [defeatConsequencesApplied], for the victory card: which of the standing /
     * peace / tribute buttons the GM has already confirmed. Nullable and defaulted to empty on
     * read, so battles persisted before war-end conditions existed load unchanged.
     */
    var victoryConsequencesApplied: Array<String>?
}

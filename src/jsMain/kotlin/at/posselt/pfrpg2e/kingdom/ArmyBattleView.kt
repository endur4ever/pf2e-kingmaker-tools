package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.ArmyCondition
import at.posselt.pfrpg2e.data.armies.BattleArmyState
import at.posselt.pfrpg2e.data.armies.BattleState
import at.posselt.pfrpg2e.data.armies.BattleStatus
import at.posselt.pfrpg2e.data.armies.awardBattleXp
import at.posselt.pfrpg2e.data.armies.getArmyAc
import at.posselt.pfrpg2e.data.armies.getArmyAttackBonus
import at.posselt.pfrpg2e.data.armies.getArmyHitPoints
import at.posselt.pfrpg2e.data.armies.getArmyRoutThresholdModifier
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.kingdom.data.RawBattleArmy
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.structures.RawSettlement

/**
 * Pure mapping between the persisted [RawArmyBattle] (JS-interop, phase 1) and
 * the deterministic [BattleState] engine types (commonMain, phase 2/3).
 *
 * Convention: the engine's flat [BattleState.armies] list holds all attackers
 * first, then all defenders, so engine indices are
 * `0 until attackers.size` for attackers and
 * `attackers.size until attackers.size + defenders.size` for defenders.
 *
 * No Foundry dependencies — covered by jsTest.
 */

/** Minimal info about a PF2EArmy actor needed to enroll it in a battle. */
data class BattleArmyInfo(
    val uuid: String,
    val name: String,
    val level: Int,
)

/**
 * Default rout threshold per the workbook: maxHp / 4 rounded up,
 * adjusted by the army's specialized modifier (if any).
 */
fun routThresholdFor(name: String, maxHp: Int): Int =
    ((maxHp + 3) / 4 + getArmyRoutThresholdModifier(name)).coerceAtLeast(0)

/**
 * Builds the engine state for one army. AC and attack bonus are derived from
 * the army's level via the workbook statistics table; unknown condition
 * strings are ignored.
 */
fun toBattleArmyState(raw: RawBattleArmy): BattleArmyState = BattleArmyState(
    name = raw.name,
    level = raw.level,
    currentHp = raw.currentHp,
    maxHp = raw.maxHp,
    conditions = raw.conditions.mapNotNull { fromCamelCase<ArmyCondition>(it) }.toSet(),
    attackBonus = getArmyAttackBonus(raw.level),
    ac = getArmyAc(raw.level),
    routThreshold = routThresholdFor(raw.name, raw.maxHp),
    xp = raw.xp,
)

/** Builds the engine [BattleState] from a persisted battle (attackers first). */
fun toBattleState(battle: RawArmyBattle): BattleState = BattleState(
    round = battle.round,
    armies = (battle.attackers + battle.defenders).map { toBattleArmyState(it) },
    log = battle.log.toList(),
    status = BattleStatus.fromString(battle.status) ?: BattleStatus.ACTIVE,
    terrain = battle.terrain,
)

/**
 * Battle outcome from an engine state: VICTORY when every defender is
 * destroyed, DEFEAT when every attacker is, ACTIVE otherwise. Sides are
 * split at [attackerCount] per the flat-list convention.
 */
fun determineBattleStatus(state: BattleState, attackerCount: Int): BattleStatus {
    val attackers = state.armies.take(attackerCount)
    val defenders = state.armies.drop(attackerCount)
    val allDefendersDestroyed = defenders.isNotEmpty() &&
        defenders.all { ArmyCondition.DESTROYED in it.conditions }
    val allAttackersDestroyed = attackers.isNotEmpty() &&
        attackers.all { ArmyCondition.DESTROYED in it.conditions }
    return when {
        allDefendersDestroyed -> BattleStatus.VICTORY
        allAttackersDestroyed -> BattleStatus.DEFEAT
        else -> BattleStatus.ACTIVE
    }
}

private fun BattleArmyState.toRaw(original: RawBattleArmy): RawBattleArmy =
    RawBattleArmy.copy(
        original,
        currentHp = currentHp,
        conditions = conditions.map { it.value }.toTypedArray(),
        xp = xp,
    )

/**
 * Writes an engine state back onto the persisted battle: HP/conditions/XP per
 * army (identity fields like [RawBattleArmy.armyActorUuid] are preserved from
 * the original by index), plus round, log, and the recomputed status.
 */
fun updateRawBattle(battle: RawArmyBattle, state: BattleState): RawArmyBattle {
    val attackerCount = battle.attackers.size
    val newAttackers = battle.attackers.mapIndexed { i, original ->
        state.armies[i].toRaw(original)
    }.toTypedArray()
    val newDefenders = battle.defenders.mapIndexed { i, original ->
        state.armies[attackerCount + i].toRaw(original)
    }.toTypedArray()
    return RawArmyBattle.copy(
        battle,
        round = state.round,
        attackers = newAttackers,
        defenders = newDefenders,
        log = state.log.toTypedArray(),
        status = determineBattleStatus(state, attackerCount).value,
        terrain = state.terrain,
    )
}

private fun RawBattleArmy.isDestroyed(): Boolean =
    ArmyCondition.DESTROYED.value in conditions

/**
 * Awards battle XP (phase 3 [awardBattleXp]) to every surviving victor for
 * each destroyed enemy, accumulating onto [RawBattleArmy.xp].
 */
fun awardVictoryXp(
    victors: Array<RawBattleArmy>,
    defeated: Array<RawBattleArmy>,
): Array<RawBattleArmy> {
    val destroyedEnemies = defeated.filter { it.isDestroyed() }
    if (destroyedEnemies.isEmpty()) return victors
    return victors.map { army ->
        if (army.isDestroyed()) {
            army
        } else {
            val gained = destroyedEnemies.sumOf { awardBattleXp(army.level, it.level) }
            RawBattleArmy.copy(army, xp = army.xp + gained)
        }
    }.toTypedArray()
}

/**
 * Creates a new battle for [threat]: the kingdom's assigned deployments form
 * the attacker side and the threat itself fields a single enemy army at its
 * current escalation level (at least 1). HP comes from the workbook basic-army
 * table (default 4 when the name is unknown).
 */
fun createArmyBattle(
    id: String,
    threat: RawWarThreat,
    attackers: List<BattleArmyInfo>,
    terrain: String?,
): RawArmyBattle {
    val attackerArmies = attackers.map { info ->
        val maxHp = getArmyHitPoints(info.name)
        RawBattleArmy(
            armyActorUuid = info.uuid,
            name = info.name,
            level = info.level,
            currentHp = maxHp,
            maxHp = maxHp,
            conditions = emptyArray(),
            xp = 0,
        )
    }.toTypedArray()
    val defenderMaxHp = getArmyHitPoints(threat.name)
    val defender = RawBattleArmy(
        armyActorUuid = "",
        name = threat.name,
        level = threat.escalationLevel.coerceAtLeast(1),
        currentHp = defenderMaxHp,
        maxHp = defenderMaxHp,
        conditions = emptyArray(),
        xp = 0,
    )
    return RawArmyBattle(
        id = id,
        threatId = threat.id,
        name = threat.name,
        round = 0,
        terrain = terrain,
        attackers = attackerArmies,
        defenders = arrayOf(defender),
        log = emptyArray(),
        status = BattleStatus.ACTIVE.value,
    )
}

/**
 * Resolves the terrain of the battle target.
 */
fun resolveBattleTerrain(
    targetSettlementSceneId: String?,
    targetHexLocation: String?,
    settlements: Array<RawSettlement>,
): String? {
    if (targetSettlementSceneId != null) {
        val s = settlements.find { it.sceneId == targetSettlementSceneId }
        if (s?.terrain != null) return s.terrain
    }
    if (targetHexLocation != null) {
        val keyInt = targetHexLocation.toIntOrNull()
        if (keyInt != null) {
            val hex = runCatching { com.foundryvtt.kingmaker.kingmaker.region.hexes.find { it.key == keyInt } }.getOrNull()
            if (hex?.terrain?.id != null) return hex.terrain.id
        }
    }
    return null
}

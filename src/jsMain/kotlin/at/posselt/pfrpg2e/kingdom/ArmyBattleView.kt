package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.ActorArmyMapping
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
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.getAppFlag
import com.foundryvtt.core.utils.fromUuid
import com.foundryvtt.pf2e.actor.PF2EArmy
import kotlinx.coroutines.await

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
 *
 * This is the fallback path used when no PF2EArmy actor is linked (or the actor
 * cannot be resolved). For actors with a valid [RawBattleArmy.armyActorUuid],
 * prefer [toBattleArmyStateFromActor] which reads stats from the actor sheet.
 */
fun toBattleArmyState(raw: RawBattleArmy): BattleArmyState = BattleArmyState(
    name = raw.name,
    level = raw.level,
    currentHp = raw.currentHp,
    maxHp = raw.maxHp,
    conditions = raw.conditions.mapNotNull { fromCamelCase<ArmyCondition>(it) }.toSet(),
    attackBonus = getArmyAttackBonus(raw.level),
    ac = getArmyAc(raw.level) + (raw.defenseBonus ?: 0),
    routThreshold = routThresholdFor(raw.name, raw.maxHp),
    xp = raw.xp,
)

/**
 * Resolves a [RawBattleArmy] to a [BattleArmyState] by fetching the linked
 * PF2EArmy actor (if any) and reading its sheet stats (HP, AC, strikes, level, saves).
 * Falls back to the workbook level-table if the UUID is blank, the actor is missing,
 * or the actor is not a PF2EArmy.
 *
 * This is a suspend function because it performs an async `fromUuid` lookup.
 */
suspend fun toBattleArmyStateFromActor(raw: RawBattleArmy): BattleArmyState {
    val uuid = raw.armyActorUuid
    if (uuid.isBlank()) {
        return toBattleArmyState(raw)
    }
    val actor = if (js("typeof fromUuid") != "undefined") {
        fromUuid(uuid).await()?.unsafeCast<PF2EArmy>()
    } else {
        null
    }
    if (actor == null) {
        return toBattleArmyState(raw)
    }
    val state = ActorArmyMapping.toBattleArmyStateFromActorData(actor)
    // CRITICAL: Merge sheet-based attributes with the raw battle record state.
    // HP, conditions, and XP are dynamic and must come from the battle record.
    return state.copy(
        currentHp = raw.currentHp,
        conditions = raw.conditions.mapNotNull { fromCamelCase<ArmyCondition>(it) }.toSet(),
        xp = raw.xp,
        // Actor-backed armies read AC from the sheet, so the garrison bonus is applied on top here
        // too — otherwise it would silently apply only to workbook-fallback armies.
        ac = state.ac + (raw.defenseBonus ?: 0),
    )
}

/**
 * (Removed: a `toBattleArmyState(raw, useActor)` overload whose both branches called the
 * workbook fallback — zero callers, and its signature implied actor-stat behavior it never had.
 * Use [toBattleArmyStateFromActor] for actor-backed armies.)
 */

/** Builds the engine [BattleState] from a persisted battle (attackers first). */
suspend fun toBattleState(battle: RawArmyBattle): BattleState = BattleState(
    round = battle.round,
    armies = (battle.attackers + battle.defenders).map { toBattleArmyStateFromActor(it) },
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
    val allDefendersDefeated = defenders.isNotEmpty() &&
        defenders.all { ArmyCondition.DESTROYED in it.conditions || ArmyCondition.ROUTED in it.conditions }
    val allAttackersDefeated = attackers.isNotEmpty() &&
        attackers.all { ArmyCondition.DESTROYED in it.conditions || ArmyCondition.ROUTED in it.conditions }
    return when {
        allDefendersDefeated -> BattleStatus.VICTORY
        allAttackersDefeated -> BattleStatus.DEFEAT
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
 * current escalation level (at least 1). For attackers with an [armyActorUuid],
 * fetches the PF2EArmy actor and reads HP, AC, and attack bonus from the sheet.
 * Falls back to the workbook basic-army table (default 4 HP) when the name is
 * unknown or the actor cannot be resolved.
 */
suspend fun createArmyBattle(
    id: String,
    threat: RawWarThreat,
    attackers: List<BattleArmyInfo>,
    terrain: String?,
    /**
     * Flat AC bonus per army uuid, resolved by the caller — currently the Garrison-structure
     * defence for armies garrisoned in the settlement under threat. Baked into the battle record
     * so the row the GM reads and the AC the engine rolls against are the same number.
     */
    defenseBonusByUuid: Map<String, Int> = emptyMap(),
): RawArmyBattle {
    val attackerArmies = attackers.map { info ->
        val uuid = info.uuid
        if (uuid.isNotBlank()) {
            val actor = if (js("typeof fromUuid") != "undefined") {
                fromUuid(uuid).await()?.unsafeCast<PF2EArmy>()
            } else {
                null
            }
            if (actor != null) {
                val state = ActorArmyMapping.toBattleArmyStateFromActorData(actor)
                // Read persisted XP from actor flag (module flag "xp")
                val persistedXp = actor.getAppFlag<PF2EArmy, Int>("xp") ?: 0
                RawBattleArmy(
                    armyActorUuid = uuid,
                    name = state.name,
                    level = state.level,
                    currentHp = state.currentHp,
                    maxHp = state.maxHp,
                    conditions = emptyArray(),
                    xp = persistedXp,
                    defenseBonus = defenseBonusByUuid[uuid]?.takeIf { it != 0 },
                )
            } else {
                // Actor not found or not a PF2EArmy — fall back to workbook
                val maxHp = getArmyHitPoints(info.name)
                RawBattleArmy(
                    armyActorUuid = uuid,
                    name = info.name,
                    level = info.level,
                    currentHp = maxHp,
                    maxHp = maxHp,
                    conditions = emptyArray(),
                    xp = 0,
                    defenseBonus = defenseBonusByUuid[uuid]?.takeIf { it != 0 },
                )
            }
        } else {
            // No actor UUID — fall back to workbook
            val maxHp = getArmyHitPoints(info.name)
            RawBattleArmy(
                armyActorUuid = uuid,
                name = info.name,
                level = info.level,
                currentHp = maxHp,
                maxHp = maxHp,
                conditions = emptyArray(),
                xp = 0,
                defenseBonus = defenseBonusByUuid[uuid]?.takeIf { it != 0 },
            )
        }
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
        defenseBonus = null,
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
package at.posselt.pfrpg2e.data.armies

/**
 * Pure-deterministic battle engine for the Kingdom Warfare subsystem.
 *
 * DESIGN: this file lives in **commonMain** and has **zero randomness**.
 * Every function takes explicit d20 roll results as [Int] parameters.
 * The UI layer rolls dice and feeds the results here, so every outcome
 * is fully reproducible and every test can assert exact values.
 *
 * All functions are pure (no side-effects, no mutation); they return new
 * data instances.  Log entries are plain [String] — no i18n lookups in
 * commonMain.
 *
 * References (Kingmaker Army Rules):
 *   • Strike: d20 + attack bonus vs target AC → degree of success.
 *   • Damage: 1 point on hit, 2 on critical hit, 0 otherwise.
 *   • Rout: when current HP ≤ rout threshold, army must attempt a Morale
 *     check at the start of its turn; failure adds ROUTED.
 *   • DAMAGED: applied when HP drops below half max HP (rounded down).
 *   • DESTROYED: applied when HP reaches 0.
 */

// ---------------------------------------------------------------------------
// Outcome types
// ---------------------------------------------------------------------------

/**
 * Degree of success for a d20 roll against a DC/AC, following PF2e rules.
 *
 * Mapping (roll + bonus vs AC):
 *   • natural 20  → CRITICAL_SUCCESS  (regardless of AC)
 *   • natural 1   → CRITICAL_FAILURE  (regardless of AC)
 *   • total ≥ AC + 10  → CRITICAL_SUCCESS
 *   • total ≥ AC        → SUCCESS
 *   • total ≤ AC - 10   → CRITICAL_FAILURE
 *   • otherwise         → FAILURE
 */
enum class DegreeOfSuccess {
    CRITICAL_SUCCESS,
    SUCCESS,
    FAILURE,
    CRITICAL_FAILURE
}

/**
 * Result of a single strike attempt.
 *
 * @property degree  the degree of success
 * @property damage  damage inflicted (0 / 1 / 2)
 * @property log     human-readable log line (no i18n)
 */
data class StrikeOutcome(
    val degree: DegreeOfSuccess,
    val damage: Int,
    val log: String,
)

/**
 * Snapshot of an army's battle state used by the engine.
 * Pure data — no JS dependencies.
 */
data class BattleArmyState(
    val name: String,
    val level: Int,
    val currentHp: Int,
    val maxHp: Int,
    val conditions: Set<ArmyCondition> = emptySet(),
    val attackBonus: Int,
    val ac: Int,
    val routThreshold: Int,       // absolute HP value (e.g. 2 means rout at ≤2 HP)
    val moraleBonus: Int = 0,
)

/**
 * Snapshot of the overall battle.
 */
data class BattleState(
    val round: Int,
    val armies: List<BattleArmyState>,
    val log: List<String> = emptyList(),
    val status: BattleStatus = BattleStatus.ACTIVE,
)

/**
 * An action taken by an army in a round.
 */
data class BattleAction(
    val actorIndex: Int,          // index into BattleState.armies
    val targetIndex: Int,         // index into BattleState.armies
    val roll: Int,                // the d20 result (1-20), supplied by UI
)

// ---------------------------------------------------------------------------
// Pure functions
// ---------------------------------------------------------------------------

/**
 * Determines the degree of success for a d20 roll against a target AC.
 *
 * PF2e degree-of-success with natural-20 / natural-1 override rules.
 *
 * @param roll        the natural d20 result (1-20)
 * @param totalBonus  all modifiers added to the roll (attack bonus + conditions etc.)
 * @param targetAC    the defender's AC
 */
fun degreeOfSuccess(roll: Int, totalBonus: Int, targetAC: Int): DegreeOfSuccess {
    // Natural 20 / natural 1 always override
    if (roll == 20) return DegreeOfSuccess.CRITICAL_SUCCESS
    if (roll == 1) return DegreeOfSuccess.CRITICAL_FAILURE
    val total = roll + totalBonus
    val diff = total - targetAC
    return when {
        diff >= 10 -> DegreeOfSuccess.CRITICAL_SUCCESS
        diff >= 0 -> DegreeOfSuccess.SUCCESS
        diff <= -10 -> DegreeOfSuccess.CRITICAL_FAILURE
        else -> DegreeOfSuccess.FAILURE
    }
}

/**
 * Resolves a single strike from one army against another.
 *
 * Damage rules (Kingmaker workbook):
 *   • Critical success → 2 damage
 *   • Success          → 1 damage
 *   • Failure          → 0 damage
 *   • Critical failure → 0 damage
 *
 * @param attackRoll       natural d20 result (1-20)
 * @param attacker         the attacking army's state
 * @param defender         the defending army's state
 */
fun resolveStrike(
    attackRoll: Int,
    attacker: BattleArmyState,
    defender: BattleArmyState,
): StrikeOutcome {
    // PINNED armies cannot strike
    if (ArmyCondition.PINNED in attacker.conditions) {
        return StrikeOutcome(
            degree = DegreeOfSuccess.FAILURE,
            damage = 0,
            log = "${attacker.name} is pinned and cannot strike.",
        )
    }

    val atkBonus = attacker.attackBonus
    val deg = degreeOfSuccess(attackRoll, atkBonus, defender.ac)
    val dmg = when (deg) {
        DegreeOfSuccess.CRITICAL_SUCCESS -> 2
        DegreeOfSuccess.SUCCESS -> 1
        else -> 0
    }
    val log = when (deg) {
        DegreeOfSuccess.CRITICAL_SUCCESS ->
            "${attacker.name} critically hits ${defender.name} for $dmg damage!"
        DegreeOfSuccess.SUCCESS ->
            "${attacker.name} hits ${defender.name} for $dmg damage."
        DegreeOfSuccess.FAILURE ->
            "${attacker.name} misses ${defender.name}."
        DegreeOfSuccess.CRITICAL_FAILURE ->
            "${attacker.name} critically misses ${defender.name}!"
    }
    return StrikeOutcome(degree = deg, damage = dmg, log = log)
}

/**
 * Applies damage to an army and returns the updated state with
 * DAMAGED / DESTROYED conditions set as appropriate.
 *
 * Rules:
 *   • HP is clamped to [0, maxHp].
 *   • If HP ≤ 0 → add DESTROYED, remove DAMAGED (DESTROYED supersedes).
 *   • If HP ≤ maxHp/2 (rounded down) and not destroyed → add DAMAGED.
 *   • Otherwise DAMAGED is removed (healed above half).
 *
 * @param army    the army to apply damage to
 * @param damage  amount of damage (non-negative)
 */
fun applyDamage(army: BattleArmyState, damage: Int): BattleArmyState {
    val newHp = (army.currentHp - damage).coerceIn(0, army.maxHp)
    val halfThreshold = army.maxHp / 2  // integer division, rounds down
    val newConditions = army.conditions.toMutableSet()

    when {
        newHp <= 0 -> {
            newConditions.add(ArmyCondition.DESTROYED)
            newConditions.remove(ArmyCondition.DAMAGED)
        }
        newHp <= halfThreshold -> {
            newConditions.add(ArmyCondition.DAMAGED)
        }
        else -> {
            newConditions.remove(ArmyCondition.DAMAGED)
        }
    }
    return army.copy(currentHp = newHp, conditions = newConditions)
}

/**
 * Performs a morale check.
 *
 * Rout rule (Kingmaker workbook):
 *   At the start of a routed army's turn, if its current HP is at or below
 *   its rout threshold, it must attempt a Morale check (d20 + morale bonus vs DC 10).
 *   On failure, the army gains the ROUTED condition.
 *   On success, the army removes ROUTED (it recovers its nerve).
 *
 * This function takes the roll result as input and returns the updated
 * set of conditions plus a log line.
 *
 * @param roll              natural d20 result (1-20)
 * @param dc                the morale DC to beat (typically 10)
 * @param moraleBonus       the army's morale bonus/penalty
 * @param currentConditions the army's current conditions
 * @param armyName          for the log line
 * @return pair of (new conditions, log line)
 */
fun moraleCheck(
    roll: Int,
    dc: Int,
    moraleBonus: Int,
    currentConditions: Set<ArmyCondition>,
    armyName: String,
): Pair<Set<ArmyCondition>, String> {
    val total = roll + moraleBonus
    val success = total >= dc
    val newConditions = currentConditions.toMutableSet()
    val log: String
    if (success) {
        newConditions.remove(ArmyCondition.ROUTED)
        log = "$armyName passes the morale check (roll $roll + $moraleBonus = $total vs DC $dc) and recovers."
    } else {
        newConditions.add(ArmyCondition.ROUTED)
        log = "$armyName fails the morale check (roll $roll + $moraleBonus = $total vs DC $dc) and becomes routed!"
    }
    return Pair(newConditions, log)
}

/**
 * Computes condition-based modifiers for an army.
 *
 * Implemented conditions and their effects (Kingmaker workbook):
 *   • MIRED:  cannot advance (Advance action fails automatically).
 *             Source: "A mired army can't use the Advance action."
 *   • PINNED: cannot strike (Strike action fails automatically).
 *             Source: "A pinned army can't use the Strike action."
 *   • WEARY:  –1 penalty to attack rolls and AC.
 *             Source: "A weary army takes a –1 penalty to attack rolls and AC."
 *
 * @param conditions the army's current conditions
 * @return modifiers to apply
 */
data class ConditionModifiers(
    val attackPenalty: Int = 0,
    val acPenalty: Int = 0,
    val canAdvance: Boolean = true,
    val canStrike: Boolean = true,
)

fun conditionEffects(conditions: Set<ArmyCondition>): ConditionModifiers {
    val weary = ArmyCondition.WEARY in conditions
    return ConditionModifiers(
        attackPenalty = if (weary) 1 else 0,
        acPenalty = if (weary) 1 else 0,
        canAdvance = ArmyCondition.MIRED !in conditions,
        canStrike = ArmyCondition.PINNED !in conditions,
    )
}

/**
 * Advances the battle by one round, resolving all provided actions in order.
 *
 * For each action:
 *   1. Look up actor and target from [BattleState.armies].
 *   2. Apply condition effects to get effective attack bonus.
 *   3. Resolve the strike.
 *   4. Apply damage to the target.
 *   5. Append log lines.
 *
 * After all actions, the round counter increments.
 *
 * This function does NOT handle morale checks or rout-threshold checks —
 * those are separate explicit calls so the UI can present them at the
 * correct time (start of turn).
 *
 * @param battle  the current battle state
 * @param actions the list of actions to resolve this round
 * @return a new [BattleState] with updated armies, incremented round, and appended log
 */
fun tickRound(
    battle: BattleState,
    actions: List<BattleAction>,
): BattleState {
    // Mutable working copy of armies (indexed by position)
    val armies = battle.armies.toMutableList()
    val logs = battle.log.toMutableList()

    for (action in actions) {
        val actor = armies[action.actorIndex]
        val target = armies[action.targetIndex]

        // Skip destroyed armies
        if (ArmyCondition.DESTROYED in actor.conditions) continue
        if (ArmyCondition.DESTROYED in target.conditions) continue

        val mods = conditionEffects(actor.conditions)
        val effectiveActor = if (mods.attackPenalty != 0 || mods.acPenalty != 0) {
            actor.copy(
                attackBonus = actor.attackBonus - mods.attackPenalty,
                ac = actor.ac - mods.acPenalty,
            )
        } else {
            actor
        }

        val outcome = resolveStrike(action.roll, effectiveActor, target)
        logs.add(outcome.log)

        if (outcome.damage > 0) {
            val damagedTarget = applyDamage(target, outcome.damage)
            armies[action.targetIndex] = damagedTarget
            if (ArmyCondition.DESTROYED in damagedTarget.conditions) {
                logs.add("${damagedTarget.name} is destroyed!")
            } else if (ArmyCondition.DAMAGED in damagedTarget.conditions) {
                logs.add("${damagedTarget.name} is damaged!")
            }
        }
    }

    return battle.copy(
        round = battle.round + 1,
        armies = armies,
        log = logs,
    )
}

/**
 * Looks up the base AC for an army from its level using the workbook table.
 */
fun getArmyAc(level: Int): Int =
    armyStatistics.find { it.level == level }?.ac ?: 16

/**
 * Looks up the base attack bonus for an army from its level using the workbook table.
 */
fun getArmyAttackBonus(level: Int): Int =
    armyStatistics.find { it.level == level }?.attack ?: 9

/**
 * Looks up the base HP for a named army from the workbook.
 */
fun getArmyHitPoints(name: String): Int =
    workbookBasicArmies.find { it.name == name }?.hitPoints ?: 4

/**
 * Looks up the rout threshold for a named army from the workbook modifiers.
 * Returns the modifier value (which is added to the default rout threshold).
 * Default rout threshold is maxHp / 4 (rounded up) per the workbook.
 * Specialized modifiers adjust this.
 *
 * If no modifier is found, returns 0 (use default).
 */
fun getArmyRoutThresholdModifier(name: String): Int =
    workbookSpecializedArmyModifiers.find { it.name == name }?.routThreshold ?: 0

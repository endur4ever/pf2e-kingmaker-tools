package at.posselt.pfrpg2e.data.armies

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for [ArmyBattleEngine] — the pure-deterministic battle engine.
 *
 * Every test uses exact integer inputs; there is zero randomness.
 */

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun testArmy(
    name: String = "Test Army",
    level: Int = 1,
    hp: Int = 4,
    currentHp: Int? = null,
    conditions: Set<ArmyCondition> = emptySet(),
    attackBonus: Int = 9,
    ac: Int = 16,
    routThreshold: Int = 1,
    moraleBonus: Int = 0,
) = BattleArmyState(
    name = name,
    level = level,
    currentHp = currentHp ?: hp,
    maxHp = hp,
    conditions = conditions,
    attackBonus = attackBonus,
    ac = ac,
    routThreshold = routThreshold,
    moraleBonus = moraleBonus,
)

// ---------------------------------------------------------------------------
// degreeOfSuccess
// ---------------------------------------------------------------------------

class DegreeOfSuccessTest {

    @Test
    fun natural20AlwaysCriticalSuccess() {
        // Even with penalty, natural 20 = critical success
        assertEquals(DegreeOfSuccess.CRITICAL_SUCCESS, degreeOfSuccess(20, -100, 999))
    }

    @Test
    fun natural1AlwaysCriticalFailure() {
        // Even with huge bonus, natural 1 = critical failure
        assertEquals(DegreeOfSuccess.CRITICAL_FAILURE, degreeOfSuccess(1, 100, 1))
    }

    @Test
    fun criticalSuccessOnPlus10() {
        // roll 15 + bonus 5 = 20 vs AC 10 → diff = +10 → critical success
        assertEquals(DegreeOfSuccess.CRITICAL_SUCCESS, degreeOfSuccess(15, 5, 10))
    }

    @Test
    fun successOnExactMatch() {
        // roll 10 + bonus 5 = 15 vs AC 15 → diff = 0 → success
        assertEquals(DegreeOfSuccess.SUCCESS, degreeOfSuccess(10, 5, 15))
    }

    @Test
    fun successAboveAc() {
        // roll 12 + bonus 5 = 17 vs AC 15 → diff = +2 → success
        assertEquals(DegreeOfSuccess.SUCCESS, degreeOfSuccess(12, 5, 15))
    }

    @Test
    fun failureBelowAc() {
        // roll 5 + bonus 5 = 10 vs AC 15 → diff = -5 → failure
        assertEquals(DegreeOfSuccess.FAILURE, degreeOfSuccess(5, 5, 15))
    }

    @Test
    fun criticalFailureOnMinus10() {
        // roll 3 + bonus 2 = 5 vs AC 15 → diff = -10 → critical failure
        assertEquals(DegreeOfSuccess.CRITICAL_FAILURE, degreeOfSuccess(3, 2, 15))
    }

    @Test
    fun criticalFailureBelowMinus10() {
        // roll 2 + bonus 2 = 4 vs AC 15 → diff = -11 → critical failure
        assertEquals(DegreeOfSuccess.CRITICAL_FAILURE, degreeOfSuccess(2, 2, 15))
    }
}

// ---------------------------------------------------------------------------
// resolveStrike
// ---------------------------------------------------------------------------

class ResolveStrikeTest {

    @Test
    fun criticalSuccessDeals2Damage() {
        val attacker = testArmy(attackBonus = 9)
        val defender = testArmy(name = "Defender", ac = 16)
        // roll 20 = natural 20 = critical success
        val outcome = resolveStrike(20, attacker, defender)
        assertEquals(DegreeOfSuccess.CRITICAL_SUCCESS, outcome.degree)
        assertEquals(2, outcome.damage)
        assertTrue(outcome.log.contains("critically hits"))
    }

    @Test
    fun successDeals1Damage() {
        val attacker = testArmy(attackBonus = 9)
        val defender = testArmy(name = "Defender", ac = 16)
        // roll 10 + 9 = 19 vs 16 → success
        val outcome = resolveStrike(10, attacker, defender)
        assertEquals(DegreeOfSuccess.SUCCESS, outcome.degree)
        assertEquals(1, outcome.damage)
        assertTrue(outcome.log.contains("hits"))
    }

    @Test
    fun failureDeals0Damage() {
        val attacker = testArmy(attackBonus = 9)
        val defender = testArmy(name = "Defender", ac = 16)
        // roll 1 + 9 = 10 vs 16 → failure (natural 1 = critical failure)
        val outcome = resolveStrike(1, attacker, defender)
        assertEquals(DegreeOfSuccess.CRITICAL_FAILURE, outcome.degree)
        assertEquals(0, outcome.damage)
    }

    @Test
    fun pinnedArmyCannotStrike() {
        val attacker = testArmy(conditions = setOf(ArmyCondition.PINNED))
        val defender = testArmy(name = "Defender")
        // Even a natural 20 should not work when pinned
        val outcome = resolveStrike(20, attacker, defender)
        assertEquals(DegreeOfSuccess.FAILURE, outcome.degree)
        assertEquals(0, outcome.damage)
        assertTrue(outcome.log.contains("pinned"))
    }

    @Test
    fun criticalFailureLog() {
        val attacker = testArmy(name = "Attacker", attackBonus = 0)
        val defender = testArmy(name = "Defender", ac = 30)
        // roll 1 + 0 = 1 vs 30 → natural 1 = critical failure
        val outcome = resolveStrike(1, attacker, defender)
        assertEquals(DegreeOfSuccess.CRITICAL_FAILURE, outcome.degree)
        assertTrue(outcome.log.contains("critically misses"))
    }
}

// ---------------------------------------------------------------------------
// applyDamage
// ---------------------------------------------------------------------------

class ApplyDamageTest {

    @Test
    fun damageReducesHp() {
        val army = testArmy(hp = 4)
        val result = applyDamage(army, 1)
        assertEquals(3, result.currentHp)
    }

    @Test
    fun damageClampsToZero() {
        val army = testArmy(hp = 4)
        val result = applyDamage(army, 999)
        assertEquals(0, result.currentHp)
    }

    @Test
    fun destroyedAtZeroHp() {
        val army = testArmy(hp = 4)
        val result = applyDamage(army, 4)
        assertEquals(0, result.currentHp)
        assertTrue(ArmyCondition.DESTROYED in result.conditions)
    }

    @Test
    fun destroyedSupersedesDamaged() {
        val army = testArmy(hp = 4, conditions = setOf(ArmyCondition.DAMAGED))
        val result = applyDamage(army, 4)
        assertEquals(0, result.currentHp)
        assertTrue(ArmyCondition.DESTROYED in result.conditions)
        assertFalse(ArmyCondition.DAMAGED in result.conditions)
    }

    @Test
    fun damagedAtHalfHp() {
        // maxHp=4, half=2. Drop to 2 → DAMAGED
        val army = testArmy(hp = 4)
        val result = applyDamage(army, 2)
        assertEquals(2, result.currentHp)
        assertTrue(ArmyCondition.DAMAGED in result.conditions)
    }

    @Test
    fun damagedBelowHalfHp() {
        // maxHp=4, half=2. Drop to 1 → DAMAGED
        val army = testArmy(hp = 4)
        val result = applyDamage(army, 3)
        assertEquals(1, result.currentHp)
        assertTrue(ArmyCondition.DAMAGED in result.conditions)
    }

    @Test
    fun notDamagedAboveHalfHp() {
        // maxHp=4, half=2. Drop to 3 → not DAMAGED
        val army = testArmy(hp = 4)
        val result = applyDamage(army, 1)
        assertEquals(3, result.currentHp)
        assertFalse(ArmyCondition.DAMAGED in result.conditions)
    }

    @Test
    fun damagedRemovedWhenHealedAboveHalf() {
        // Army at 2 HP (DAMAGED), "healed" by 0 damage — stays DAMAGED
        // (applyDamage only does damage, not healing, so DAMAGED stays)
        val army = testArmy(hp = 4, currentHp = 2, conditions = setOf(ArmyCondition.DAMAGED))
        val result = applyDamage(army, 0)
        assertEquals(2, result.currentHp)
        // Still at 2 which is ≤ half, so DAMAGED stays
        assertTrue(ArmyCondition.DAMAGED in result.conditions)
    }

    @Test
    fun oddMaxHpHalfRoundsDown() {
        // maxHp=5, half=2 (integer division). Drop to 2 → DAMAGED
        val army = testArmy(hp = 5)
        val result = applyDamage(army, 3)
        assertEquals(2, result.currentHp)
        assertTrue(ArmyCondition.DAMAGED in result.conditions)
    }

    @Test
    fun oddMaxHpAtThreeIsNotDamaged() {
        // maxHp=5, half=2. At 3 → not DAMAGED
        val army = testArmy(hp = 5)
        val result = applyDamage(army, 2)
        assertEquals(3, result.currentHp)
        assertFalse(ArmyCondition.DAMAGED in result.conditions)
    }
}

// ---------------------------------------------------------------------------
// moraleCheck
// ---------------------------------------------------------------------------

class MoraleCheckTest {

    @Test
    fun successRemovesRouted() {
        val conditions = setOf(ArmyCondition.ROUTED)
        val (newCond, log) = moraleCheck(roll = 10, dc = 10, moraleBonus = 0, currentConditions = conditions, armyName = "Infantry")
        assertFalse(ArmyCondition.ROUTED in newCond)
        assertTrue(log.contains("passes"))
    }

    @Test
    fun failureAddsRouted() {
        val conditions = emptySet<ArmyCondition>()
        val (newCond, log) = moraleCheck(roll = 5, dc = 10, moraleBonus = 0, currentConditions = conditions, armyName = "Infantry")
        assertTrue(ArmyCondition.ROUTED in newCond)
        assertTrue(log.contains("fails"))
        assertTrue(log.contains("routed"))
    }

    @Test
    fun exactDcIsSuccess() {
        // roll 7 + bonus 3 = 10 vs DC 10 → success
        val conditions = setOf(ArmyCondition.ROUTED)
        val (newCond, _) = moraleCheck(roll = 7, dc = 10, moraleBonus = 3, currentConditions = conditions, armyName = "Cavalry")
        assertFalse(ArmyCondition.ROUTED in newCond)
    }

    @Test
    fun oneBelowDcIsFailure() {
        // roll 6 + bonus 3 = 9 vs DC 10 → failure
        val conditions = emptySet<ArmyCondition>()
        val (newCond, _) = moraleCheck(roll = 6, dc = 10, moraleBonus = 3, currentConditions = conditions, armyName = "Cavalry")
        assertTrue(ArmyCondition.ROUTED in newCond)
    }

    @Test
    fun logContainsRollDetails() {
        val (_, log) = moraleCheck(roll = 15, dc = 10, moraleBonus = 2, currentConditions = emptySet(), armyName = "Scouts")
        assertTrue(log.contains("15"))
        assertTrue(log.contains("2"))
        assertTrue(log.contains("17"))  // total
        assertTrue(log.contains("10"))  // DC
    }
}

// ---------------------------------------------------------------------------
// conditionEffects
// ---------------------------------------------------------------------------

class ConditionEffectsTest {

    @Test
    fun noConditionsNoModifiers() {
        val mods = conditionEffects(emptySet())
        assertEquals(0, mods.attackPenalty)
        assertEquals(0, mods.acPenalty)
        assertTrue(mods.canAdvance)
        assertTrue(mods.canStrike)
    }

    @Test
    fun miredCannotAdvance() {
        val mods = conditionEffects(setOf(ArmyCondition.MIRED))
        assertFalse(mods.canAdvance)
        assertTrue(mods.canStrike)
        assertEquals(0, mods.attackPenalty)
    }

    @Test
    fun pinnedCannotStrike() {
        val mods = conditionEffects(setOf(ArmyCondition.PINNED))
        assertTrue(mods.canAdvance)
        assertFalse(mods.canStrike)
        assertEquals(0, mods.attackPenalty)
    }

    @Test
    fun wearyGivesPenalties() {
        val mods = conditionEffects(setOf(ArmyCondition.WEARY))
        assertEquals(1, mods.attackPenalty)
        assertEquals(1, mods.acPenalty)
        assertTrue(mods.canAdvance)
        assertTrue(mods.canStrike)
    }

    @Test
    fun miredAndPinnedStack() {
        val mods = conditionEffects(setOf(ArmyCondition.MIRED, ArmyCondition.PINNED))
        assertFalse(mods.canAdvance)
        assertFalse(mods.canStrike)
    }

    @Test
    fun allConditionsStack() {
        val mods = conditionEffects(
            setOf(ArmyCondition.MIRED, ArmyCondition.PINNED, ArmyCondition.WEARY)
        )
        assertFalse(mods.canAdvance)
        assertFalse(mods.canStrike)
        assertEquals(1, mods.attackPenalty)
        assertEquals(1, mods.acPenalty)
    }

    @Test
    fun damagedAndDestroyedDontAffectModifiers() {
        // DAMAGED and DESTROYED don't impose condition modifiers
        val mods = conditionEffects(setOf(ArmyCondition.DAMAGED, ArmyCondition.DESTROYED))
        assertEquals(0, mods.attackPenalty)
        assertEquals(0, mods.acPenalty)
        assertTrue(mods.canAdvance)
        assertTrue(mods.canStrike)
    }
}

// ---------------------------------------------------------------------------
// tickRound
// ---------------------------------------------------------------------------

class TickRoundTest {

    @Test
    fun roundIncrements() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "A", attackBonus = 9, ac = 16),
                testArmy(name = "B", attackBonus = 9, ac = 16),
            ),
        )
        val result = tickRound(battle, emptyList())
        assertEquals(2, result.round)
    }

    @Test
    fun singleActionResolves() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Attacker", attackBonus = 9, ac = 16),
                testArmy(name = "Defender", attackBonus = 9, ac = 16, hp = 4),
            ),
        )
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 15))
        // 15 + 9 = 24 vs 16 → success → 1 damage
        val result = tickRound(battle, actions)
        assertEquals(3, result.armies[1].currentHp)  // 4 - 1
        assertTrue(result.log.any { it.contains("hits") })
    }

    @Test
    fun destroyedArmySkipped() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Dead", conditions = setOf(ArmyCondition.DESTROYED)),
                testArmy(name = "Target", hp = 4),
            ),
        )
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 20))
        val result = tickRound(battle, actions)
        // Dead army should be skipped, target untouched
        assertEquals(4, result.armies[1].currentHp)
    }

    @Test
    fun destroyedTargetSkipped() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Attacker", attackBonus = 9),
                testArmy(name = "Dead Target", conditions = setOf(ArmyCondition.DESTROYED)),
            ),
        )
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 20))
        val result = tickRound(battle, actions)
        // Target is destroyed, should be skipped
        assertTrue(result.log.isEmpty())
    }

    @Test
    fun damageTriggersDamagedCondition() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Attacker", attackBonus = 9),
                testArmy(name = "Defender", hp = 4, ac = 16),
            ),
        )
        // Deal 2 damage (critical hit): 4 - 2 = 2 → at half → DAMAGED
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 20))
        val result = tickRound(battle, actions)
        assertTrue(ArmyCondition.DAMAGED in result.armies[1].conditions)
        assertTrue(result.log.any { it.contains("damaged") })
    }

    @Test
    fun damageTriggersDestroyedCondition() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Attacker", attackBonus = 9),
                testArmy(name = "Defender", hp = 2, ac = 16),
            ),
        )
        // Deal 2 damage: 2 - 2 = 0 → DESTROYED
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 20))
        val result = tickRound(battle, actions)
        assertTrue(ArmyCondition.DESTROYED in result.armies[1].conditions)
        assertTrue(result.log.any { it.contains("destroyed") })
    }

    @Test
    fun wearyPenaltyAppliedDuringTick() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Weary Attacker", conditions = setOf(ArmyCondition.WEARY), attackBonus = 9, ac = 16),
                testArmy(name = "Defender", hp = 4, ac = 16),
            ),
        )
        // Weary: attackBonus effectively 8. roll 6 + 8 = 14 vs 16 → failure (no damage)
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 6))
        val result = tickRound(battle, actions)
        assertEquals(4, result.armies[1].currentHp)  // no damage dealt
    }

    @Test
    fun multipleActionsResolveInOrder() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "A", attackBonus = 9, ac = 16, hp = 10),
                testArmy(name = "B", attackBonus = 9, ac = 16, hp = 10),
            ),
        )
        val actions = listOf(
            BattleAction(actorIndex = 0, targetIndex = 1, roll = 15),  // A hits B
            BattleAction(actorIndex = 1, targetIndex = 0, roll = 15),  // B hits A
        )
        val result = tickRound(battle, actions)
        assertEquals(9, result.armies[0].currentHp)  // 10 - 1
        assertEquals(9, result.armies[1].currentHp)  // 10 - 1
        assertEquals(2, result.log.size)
    }

    @Test
    fun logAccumulates() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "A", attackBonus = 9, ac = 16),
                testArmy(name = "B", hp = 4, ac = 16),
            ),
            log = listOf("Battle started."),
        )
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 15))
        val result = tickRound(battle, actions)
        assertEquals("Battle started.", result.log[0])
        assertTrue(result.log[1].contains("hits"))
    }
}

// ---------------------------------------------------------------------------
// Workbook lookup helpers
// ---------------------------------------------------------------------------

class WorkbookLookupTest {

    @Test
    fun getArmyAcForLevel1() {
        assertEquals(16, getArmyAc(1))
    }

    @Test
    fun getArmyAcForLevel10() {
        assertEquals(30, getArmyAc(10))
    }

    @Test
    fun getArmyAcForLevel20() {
        assertEquals(45, getArmyAc(20))
    }

    @Test
    fun getArmyAttackBonusForLevel1() {
        assertEquals(9, getArmyAttackBonus(1))
    }

    @Test
    fun getArmyAttackBonusForLevel10() {
        assertEquals(23, getArmyAttackBonus(10))
    }

    @Test
    fun getArmyHitPointsForInfantry() {
        assertEquals(4, getArmyHitPoints("Infantry"))
    }

    @Test
    fun getArmyHitPointsForSiegeEngines() {
        assertEquals(6, getArmyHitPoints("Siege Engines"))
    }

    @Test
    fun getArmyHitPointsUnknownDefault() {
        assertEquals(4, getArmyHitPoints("Nonexistent Army"))
    }

    @Test
    fun getRoutThresholdModifierForSootscale() {
        assertEquals(1, getArmyRoutThresholdModifier("Sootscale Warriors"))
    }

    @Test
    fun getRoutThresholdModifierForNomen() {
        assertEquals(-4, getArmyRoutThresholdModifier("Nomen Scouts"))
    }

    @Test
    fun getRoutThresholdModifierUnknownDefault() {
        assertEquals(0, getArmyRoutThresholdModifier("Nonexistent Army"))
    }
}

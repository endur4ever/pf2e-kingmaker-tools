package at.posselt.pfrpg2e.data.armies

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import at.posselt.pfrpg2e.kingdom.determineBattleStatus
import at.posselt.pfrpg2e.data.armies.BattleStatus

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
        val result = moraleCheck(roll = 10, dc = 10, moraleBonus = 0, currentConditions = conditions, armyName = "Infantry")
        assertFalse(ArmyCondition.ROUTED in result.conditions)
        assertTrue(result.success)
    }

    @Test
    fun failureAddsRouted() {
        val conditions = emptySet<ArmyCondition>()
        val result = moraleCheck(roll = 5, dc = 10, moraleBonus = 0, currentConditions = conditions, armyName = "Infantry")
        assertTrue(ArmyCondition.ROUTED in result.conditions)
        assertFalse(result.success)
    }

    @Test
    fun exactDcIsSuccess() {
        // roll 7 + bonus 3 = 10 vs DC 10 → success
        val conditions = setOf(ArmyCondition.ROUTED)
        val result = moraleCheck(roll = 7, dc = 10, moraleBonus = 3, currentConditions = conditions, armyName = "Cavalry")
        assertFalse(ArmyCondition.ROUTED in result.conditions)
        assertTrue(result.success)
    }

    @Test
    fun oneBelowDcIsFailure() {
        // roll 6 + bonus 3 = 9 vs DC 10 → failure
        val conditions = emptySet<ArmyCondition>()
        val result = moraleCheck(roll = 6, dc = 10, moraleBonus = 3, currentConditions = conditions, armyName = "Cavalry")
        assertTrue(ArmyCondition.ROUTED in result.conditions)
        assertFalse(result.success)
    }

    @Test
    fun logContainsRollDetails() {
        val result = moraleCheck(roll = 15, dc = 10, moraleBonus = 2, currentConditions = emptySet(), armyName = "Scouts")
        assertEquals(15, result.roll)
        assertEquals(2, result.moraleBonus)
        assertEquals(17, result.total)
        assertEquals(10, result.dc)
        assertEquals("Scouts", result.armyName)
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

    @Test
    fun moraleCheckPassedDuringTick() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Attacker", hp = 4, currentHp = 1, routThreshold = 1, attackBonus = 9),
                testArmy(name = "Defender", hp = 4, ac = 16),
            ),
        )
        // moraleRoll = 10, passes (10 + 0 >= 10), strikes defender
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 15, moraleRoll = 10))
        val result = tickRound(battle, actions)
        assertEquals(3, result.armies[1].currentHp) // Hit resolves
        assertFalse(ArmyCondition.ROUTED in result.armies[0].conditions)
        assertTrue(result.log.any { it.startsWith("MORALE_PASS:") })
    }

    @Test
    fun moraleCheckFailedDuringTick() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Attacker", hp = 4, currentHp = 1, routThreshold = 1, attackBonus = 9),
                testArmy(name = "Defender", hp = 4, ac = 16),
            ),
        )
        // moraleRoll = 1 (fails vs DC 10), becomes ROUTED, strike skipped!
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 15, moraleRoll = 1))
        val result = tickRound(battle, actions)
        assertEquals(4, result.armies[1].currentHp) // No strike
        assertTrue(ArmyCondition.ROUTED in result.armies[0].conditions)
        assertTrue(result.log.any { it.startsWith("MORALE_FAIL:") })
    }

    @Test
    fun alreadyRoutedArmySkipped() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Attacker", hp = 4, conditions = setOf(ArmyCondition.ROUTED), attackBonus = 9),
                testArmy(name = "Defender", hp = 4, ac = 16),
            ),
        )
        // Already routed, no moraleRoll or no success -> skipped
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 15))
        val result = tickRound(battle, actions)
        assertEquals(4, result.armies[1].currentHp) // No strike
    }
}

// ---------------------------------------------------------------------------
// Round-flow integration tests (rout trigger, condition effects, recovery)
// ---------------------------------------------------------------------------

class RoundFlowIntegrationTest {

    /**
     * Scripted battle demonstrating an army routing instead of grinding to 0 HP.
     *
     * Scenario: Attacker (4 HP, routThreshold=1) vs Defender (4 HP).
     * Round 1: Attacker at 1 HP (<= routThreshold), morale check fails -> ROUTED.
     * Attacker skips strike. Defender counter-strikes but misses.
     * Round 2: Attacker is ROUTED, battle ends with DEFEAT for attackers.
     * Army routed at 1 HP instead of being destroyed at 0 HP.
     */
    @Test
    fun armyRoutsInsteadOfGrindingToZeroHp() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Attacker", hp = 4, currentHp = 1, routThreshold = 1, attackBonus = 9, moraleBonus = 0),
                testArmy(name = "Defender", hp = 4, ac = 16, attackBonus = 9, moraleBonus = 0),
            ),
        )

        // Round 1: Attacker moraleRoll=1 (fails vs DC 10), becomes ROUTED, strike skipped
        // Defender roll=5 (misses)
        val actions1 = listOf(
            BattleAction(actorIndex = 0, targetIndex = 1, roll = 15, moraleRoll = 1),
            BattleAction(actorIndex = 1, targetIndex = 0, roll = 5),
        )
        val result1 = tickRound(battle, actions1)

        // Attacker should be ROUTED at 1 HP (not destroyed at 0 HP)
        assertTrue(ArmyCondition.ROUTED in result1.armies[0].conditions)
        assertEquals(1, result1.armies[0].currentHp)
        assertFalse(ArmyCondition.DESTROYED in result1.armies[0].conditions)
        assertEquals(4, result1.armies[1].currentHp) // Defender untouched (attacker routed, defender missed)
        assertTrue(result1.log.any { it.startsWith("MORALE_FAIL:") })

        // Round 2: Attacker is ROUTED, should be skipped. Defender acts.
        // But determineBattleStatus would already show DEFEAT since all attackers are routed.
        // Verify the battle status would be DEFEAT
        val attackerCount = 1
        val status = determineBattleStatus(result1, attackerCount)
        assertEquals(BattleStatus.DEFEAT, status)
    }

    @Test
    fun wearyConditionAppliesPenalty() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Weary Attacker", conditions = setOf(ArmyCondition.WEARY), attackBonus = 9, ac = 16),
                testArmy(name = "Defender", hp = 4, ac = 16),
            ),
        )
        // WEARY gives -1 to attack. Roll 15 + 9 - 1 = 23 vs AC 16 = SUCCESS (1 damage)
        // Without WEARY: 15 + 9 = 24 vs 16 = CRITICAL_SUCCESS (2 damage)
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 15))
        val result = tickRound(battle, actions)

        assertEquals(3, result.armies[1].currentHp) // 1 damage (SUCCESS not CRITICAL_SUCCESS)
        assertTrue(result.log.any { it.startsWith("COND_WEARY:") })
    }

    @Test
    fun pinnedConditionPreventsStrike() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Pinned Attacker", conditions = setOf(ArmyCondition.PINNED), attackBonus = 9),
                testArmy(name = "Defender", hp = 4, ac = 16),
            ),
        )
        // Even natural 20 should not work when pinned
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 20))
        val result = tickRound(battle, actions)

        assertEquals(4, result.armies[1].currentHp) // No damage
        assertTrue(result.log.any { it.startsWith("COND_PINNED:") })
        // Exactly ONE line for the event. The structured COND_PINNED entry is what the UI
        // localizes; resolveStrike's raw-English "is pinned and cannot strike." used to be
        // appended alongside it, so the battle log showed the same event twice and the
        // second copy was never translated.
        assertFalse(
            result.log.any { it.contains("pinned and cannot strike") },
            "the raw-English duplicate must not reach the battle log",
        )
        assertEquals(
            1,
            result.log.count { it.startsWith("COND_PINNED:") },
            "a pinned army logs its refusal once",
        )
    }

    @Test
    fun miredConditionPreventsAdvance() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Mired Army", conditions = setOf(ArmyCondition.MIRED)),
                testArmy(name = "Other", hp = 4),
            ),
        )
        // MIRED prevents advance - logged but no advance action in current flow
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 15))
        val result = tickRound(battle, actions)

        assertTrue(result.log.any { it.startsWith("COND_MIRED:") })
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

// ---------------------------------------------------------------------------
// XP awards (awardBattleXp)
// ---------------------------------------------------------------------------

class XpAwardTest {

    @Test
    fun sameLevelAwards15Xp() {
        assertEquals(15, awardBattleXp(victorLevel = 5, defeatedLevel = 5))
    }

    @Test
    fun oneLevelHigherAwards20Xp() {
        assertEquals(20, awardBattleXp(victorLevel = 5, defeatedLevel = 6))
    }

    @Test
    fun twoLevelsHigherAwards25Xp() {
        assertEquals(25, awardBattleXp(victorLevel = 5, defeatedLevel = 7))
    }

    @Test
    fun threeLevelsHigherAwards30Xp() {
        assertEquals(30, awardBattleXp(victorLevel = 5, defeatedLevel = 8))
    }

    @Test
    fun fourLevelsHigherAwards40Xp() {
        assertEquals(40, awardBattleXp(victorLevel = 5, defeatedLevel = 9))
    }

    @Test
    fun fiveLevelsHigherAwards50Xp() {
        assertEquals(50, awardBattleXp(victorLevel = 5, defeatedLevel = 10))
    }

    @Test
    fun sixLevelsHigherAwards60Xp() {
        assertEquals(60, awardBattleXp(victorLevel = 5, defeatedLevel = 11))
    }

    @Test
    fun sevenOrMoreLevelsHigherAwards80Xp() {
        assertEquals(80, awardBattleXp(victorLevel = 5, defeatedLevel = 12))
        assertEquals(80, awardBattleXp(victorLevel = 1, defeatedLevel = 20))
    }

    @Test
    fun fourOrMoreLevelsLowerAwards5Xp() {
        assertEquals(5, awardBattleXp(victorLevel = 5, defeatedLevel = 1))
        assertEquals(5, awardBattleXp(victorLevel = 10, defeatedLevel = 1))
    }

    @Test
    fun threeLevelsLowerAwards8Xp() {
        assertEquals(8, awardBattleXp(victorLevel = 5, defeatedLevel = 2))
    }

    @Test
    fun twoLevelsLowerAwards10Xp() {
        assertEquals(10, awardBattleXp(victorLevel = 5, defeatedLevel = 3))
    }

    @Test
    fun oneLevelLowerAwards13Xp() {
        assertEquals(13, awardBattleXp(victorLevel = 5, defeatedLevel = 4))
    }
}

// ---------------------------------------------------------------------------
// XP thresholds (xpThresholdForLevel)
// ---------------------------------------------------------------------------

class XpThresholdTest {

    @Test
    fun level1ThresholdIs100() {
        assertEquals(100, xpThresholdForLevel(1))
    }

    @Test
    fun level5ThresholdIs200() {
        assertEquals(200, xpThresholdForLevel(5))
    }

    @Test
    fun level10ThresholdIs480() {
        assertEquals(480, xpThresholdForLevel(10))
    }

    @Test
    fun level15ThresholdIs1120() {
        assertEquals(1120, xpThresholdForLevel(15))
    }

    @Test
    fun level20ThresholdIsMaxValue() {
        assertEquals(Int.MAX_VALUE, xpThresholdForLevel(20))
    }

    @Test
    fun outOfRangeLevelReturnsMaxValue() {
        assertEquals(Int.MAX_VALUE, xpThresholdForLevel(0))
        assertEquals(Int.MAX_VALUE, xpThresholdForLevel(21))
        assertEquals(Int.MAX_VALUE, xpThresholdForLevel(99))
    }
}

// ---------------------------------------------------------------------------
// Level-up (applyLevelUp)
// ---------------------------------------------------------------------------

class LevelUpTest {

    @Test
    fun noLevelUpWhenXpBelowThreshold() {
        val army = testArmy(level = 3, hp = 4)
        val (result, remainingXp) = applyLevelUp(army, xp = 50, threshold = 140)
        assertEquals(3, result.level)
        assertEquals(4, result.maxHp)
        assertEquals(50, remainingXp)
    }

    @Test
    fun noLevelUpWhenAtMaxLevel() {
        val army = testArmy(level = 20, hp = 40)
        val (result, remainingXp) = applyLevelUp(army, xp = 9999, threshold = Int.MAX_VALUE)
        assertEquals(20, result.level)
        assertEquals(40, result.maxHp)
        assertEquals(9999, remainingXp)
    }

    @Test
    fun levelUpIncreasesLevelByOne() {
        val army = testArmy(level = 3, hp = 4)
        val (result, remainingXp) = applyLevelUp(army, xp = 140, threshold = 140)
        assertEquals(4, result.level)
        assertEquals(0, remainingXp)
    }

    @Test
    fun levelUpIncreasesMaxHp() {
        val army = testArmy(level = 3, hp = 4)
        val (result, _) = applyLevelUp(army, xp = 140, threshold = 140)
        // getArmyHpPerLevel returns +2 HP per level
        assertEquals(6, result.maxHp)
    }

    @Test
    fun levelUpIncreasesCurrentHp() {
        val army = testArmy(level = 3, hp = 4, currentHp = 2)
        val (result, _) = applyLevelUp(army, xp = 140, threshold = 140)
        assertEquals(4, result.currentHp)
    }

    @Test
    fun excessXpIsPreserved() {
        val army = testArmy(level = 3, hp = 4)
        val (result, remainingXp) = applyLevelUp(army, xp = 200, threshold = 140)
        assertEquals(4, result.level)
        assertEquals(60, remainingXp)
    }

    @Test
    fun levelUpCappedAt20() {
        val army = testArmy(level = 19, hp = 38)
        val (result, remainingXp) = applyLevelUp(army, xp = 2240, threshold = 2240)
        assertEquals(20, result.level)
        assertEquals(0, remainingXp)
    }

    @Test
    fun getArmyHpPerLevelReturns2() {
        assertEquals(2, getArmyHpPerLevel(1))
        assertEquals(2, getArmyHpPerLevel(10))
        assertEquals(2, getArmyHpPerLevel(20))
    }
}

// ---------------------------------------------------------------------------
// Condition recovery (recoverConditions)
// ---------------------------------------------------------------------------

class ConditionRecoveryTest {

    @Test
    fun wearyAlwaysRecovers() {
        val army = testArmy(conditions = setOf(ArmyCondition.WEARY))
        val result = recoverConditions(army)
        assertFalse(ArmyCondition.WEARY in result.conditions)
    }

    @Test
    fun routedAlwaysRecovers() {
        val army = testArmy(conditions = setOf(ArmyCondition.ROUTED))
        val result = recoverConditions(army)
        assertFalse(ArmyCondition.ROUTED in result.conditions)
    }

    @Test
    fun miredRecoversOnHighRoll() {
        val army = testArmy(conditions = setOf(ArmyCondition.MIRED))
        val result = recoverConditions(army, miredRoll = 10)
        assertFalse(ArmyCondition.MIRED in result.conditions)
    }

    @Test
    fun miredStaysOnLowRoll() {
        val army = testArmy(conditions = setOf(ArmyCondition.MIRED))
        val result = recoverConditions(army, miredRoll = 9)
        assertTrue(ArmyCondition.MIRED in result.conditions)
    }

    @Test
    fun pinnedRecoversOnHighRoll() {
        val army = testArmy(conditions = setOf(ArmyCondition.PINNED))
        val result = recoverConditions(army, pinnedRoll = 15)
        assertFalse(ArmyCondition.PINNED in result.conditions)
    }

    @Test
    fun pinnedStaysOnLowRoll() {
        val army = testArmy(conditions = setOf(ArmyCondition.PINNED))
        val result = recoverConditions(army, pinnedRoll = 1)
        assertTrue(ArmyCondition.PINNED in result.conditions)
    }

    @Test
    fun damagedNeverRecovers() {
        val army = testArmy(conditions = setOf(ArmyCondition.DAMAGED))
        val result = recoverConditions(army)
        assertTrue(ArmyCondition.DAMAGED in result.conditions)
    }

    @Test
    fun destroyedNeverRecovers() {
        val army = testArmy(conditions = setOf(ArmyCondition.DESTROYED))
        val result = recoverConditions(army)
        assertTrue(ArmyCondition.DESTROYED in result.conditions)
    }

    @Test
    fun multipleConditionsRecoverIndependently() {
        val army = testArmy(conditions = setOf(ArmyCondition.WEARY, ArmyCondition.MIRED, ArmyCondition.DAMAGED))
        val result = recoverConditions(army, miredRoll = 10)
        assertFalse(ArmyCondition.WEARY in result.conditions)
        assertFalse(ArmyCondition.MIRED in result.conditions)
        assertTrue(ArmyCondition.DAMAGED in result.conditions)
    }

    @Test
    fun noConditionsUnchanged() {
        val army = testArmy(conditions = emptySet())
        val result = recoverConditions(army)
        assertTrue(result.conditions.isEmpty())
    }
}

class TerrainModifierTest {

    @Test
    fun getTerrainModifierValues() {
        assertEquals(-2, getTerrainModifier("forest"))
        assertEquals(-2, getTerrainModifier("swamp"))
        assertEquals(-2, getTerrainModifier("wetlands")) // pf2e-kingmaker's common marsh hex id
        assertEquals(-2, getTerrainModifier("mountain"))
        assertEquals(-2, getTerrainModifier("mountains"))
        assertEquals(0, getTerrainModifier("plains"))
        assertEquals(0, getTerrainModifier("hills"))
        assertEquals(0, getTerrainModifier(null))
    }

    @Test
    fun terrainPenaltyAppliedDuringTick() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Attacker", attackBonus = 9, ac = 16),
                testArmy(name = "Defender", hp = 4, ac = 16),
            ),
            terrain = "forest"
        )
        // forest gives -2 penalty. attackBonus effectively 7. roll 8 + 7 = 15 vs 16 AC -> failure (no damage)
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 8))
        val result = tickRound(battle, actions)
        assertEquals(4, result.armies[1].currentHp) // no damage
    }

    @Test
    fun noTerrainPenaltyInEasyTerrain() {
        val battle = BattleState(
            round = 1,
            armies = listOf(
                testArmy(name = "Attacker", attackBonus = 9, ac = 16),
                testArmy(name = "Defender", hp = 4, ac = 16),
            ),
            terrain = "plains"
        )
        // plains gives 0 penalty. attackBonus effectively 9. roll 8 + 9 = 17 vs 16 AC -> success -> 1 damage
        val actions = listOf(BattleAction(actorIndex = 0, targetIndex = 1, roll = 8))
        val result = tickRound(battle, actions)
        assertEquals(3, result.armies[1].currentHp) // 1 damage
    }
}

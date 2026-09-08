package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.ArmyCondition
import at.posselt.pfrpg2e.data.armies.BattleArmyState
import at.posselt.pfrpg2e.data.armies.BattleStatus
import at.posselt.pfrpg2e.data.armies.awardBattleXp
import at.posselt.pfrpg2e.data.armies.getArmyAc
import at.posselt.pfrpg2e.data.armies.getArmyAttackBonus
import at.posselt.pfrpg2e.data.armies.recoverConditions
import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.kingdom.data.RawBattleArmy
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.structures.RawSettlement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

private fun rawBattleArmy(
    name: String = "1st Legion",
    level: Int = 5,
    currentHp: Int = 20,
    maxHp: Int = 20,
    conditions: Array<String> = emptyArray(),
    xp: Int = 0,
): RawBattleArmy = RawBattleArmy(
    armyActorUuid = "Actor.abc123",
    name = name,
    level = level,
    currentHp = currentHp,
    maxHp = maxHp,
    conditions = conditions,
    xp = xp,
)

private fun rawArmyBattle(
    id: String = "battle-1",
    name: String = "Skirmish at the Border",
    round: Int = 1,
    terrain: String? = "forest",
    attackers: Array<RawBattleArmy> = arrayOf(rawBattleArmy(name = "1st Legion")),
    defenders: Array<RawBattleArmy> = arrayOf(rawBattleArmy(name = "Goblin Scouts")),
    log: Array<String> = arrayOf("Battle begins."),
    status: String = BattleStatus.ACTIVE.value,
): RawArmyBattle = RawArmyBattle(
    id = id,
    threatId = null,
    name = name,
    round = round,
    terrain = terrain,
    attackers = attackers,
    defenders = defenders,
    log = log,
    status = status,
)

class ResolveBattleTest {
    private fun runTest(block: suspend () -> Unit): dynamic = @Suppress("DELICATE_API_TRANSITIONAL_MINI_MARKER") GlobalScope.promise { block() }

    @Test
    fun rawArmyBattleRoundIncrements() {
        val battle = rawArmyBattle(round = 3)
        assertEquals(3, battle.round)
    }

    @Test
    fun battleStatusIsActive() {
        val battle = rawArmyBattle(status = BattleStatus.ACTIVE.value)
        assertEquals(BattleStatus.ACTIVE.value, battle.status)
    }

    @Test
    fun battleStatusVictory() {
        val battle = rawArmyBattle(status = BattleStatus.VICTORY.value)
        assertEquals(BattleStatus.VICTORY.value, battle.status)
    }

    @Test
    fun battleStatusDefeat() {
        val battle = rawArmyBattle(status = BattleStatus.DEFEAT.value)
        assertEquals(BattleStatus.DEFEAT.value, battle.status)
    }

    @Test
    fun battleArmyConditionsEmpty() {
        val army = rawBattleArmy().conditions
        assertTrue(army.isEmpty())
    }

    @Test
    fun battleArmyDestroysOnDestroyedCondition() {
        val army = rawBattleArmy(conditions = arrayOf("destroyed"))
        assertTrue("destroyed" in army.conditions.toList())
    }

    @Test
    fun battleLogPreservesEntries() {
        val battle = rawArmyBattle(log = arrayOf("Round 1: Strike!", "Round 2: Miss!"))
        assertEquals(2, battle.log.size)
        assertEquals("Round 1: Strike!", battle.log[0])
    }

    @Test
    fun battleArmyHpTracking() {
        val army = rawBattleArmy(currentHp = 10, maxHp = 20)
        assertEquals(10, army.currentHp)
        assertEquals(20, army.maxHp)
    }

    @Test
    fun battleArmyLevelUpgrades() {
        val army = rawBattleArmy(level = 5)
        assertEquals(5, army.level)
    }

    @Test
    fun battleArmyXpTracking() {
        val army = rawBattleArmy(xp = 80)
        assertEquals(80, army.xp)
    }

    @Test
    fun battleArmyMultipleConditions() {
        val army = rawBattleArmy(conditions = arrayOf("mired", "weary", "pinned"))
        assertEquals(3, army.conditions.size)
        assertTrue("mired" in army.conditions.toList())
    }

    @Test
    fun battleTerrainField() {
        val battle = rawArmyBattle(terrain = "mountains")
        assertEquals("mountains", battle.terrain)
    }

    @Test
    fun battleThreatIdNullable() {
        val battle = rawArmyBattle()
        assertNull(battle.threatId)
    }

    @Test
    fun battleHasMultipleAttackers() {
        val battle = rawArmyBattle(
            attackers = arrayOf(
                rawBattleArmy(name = "1st Legion"),
                rawBattleArmy(name = "2nd Legion"),
            )
        )
        assertEquals(2, battle.attackers.size)
        assertEquals("1st Legion", battle.attackers[0].name)
        assertEquals("2nd Legion", battle.attackers[1].name)
    }

    @Test
    fun battleHasMultipleDefenders() {
        val battle = rawArmyBattle(
            defenders = arrayOf(
                rawBattleArmy(name = "Goblin Scouts"),
                rawBattleArmy(name = "Bugbear Elite"),
            )
        )
        assertEquals(2, battle.defenders.size)
        assertEquals("Goblin Scouts", battle.defenders[0].name)
    }

    // ── Raw ↔ engine mapping (ArmyBattleView) ────────────────────────────

    @Test
    fun toBattleStateOrdersAttackersFirst() = runTest {
        val battle = rawArmyBattle(
            attackers = arrayOf(rawBattleArmy(name = "1st Legion"), rawBattleArmy(name = "2nd Legion")),
            defenders = arrayOf(rawBattleArmy(name = "Goblin Scouts")),
        )
        val state = toBattleState(battle)
        assertEquals(3, state.armies.size)
        assertEquals("1st Legion", state.armies[0].name)
        assertEquals("2nd Legion", state.armies[1].name)
        assertEquals("Goblin Scouts", state.armies[2].name)
        assertEquals(battle.round, state.round)
    }

    @Test
    fun toBattleArmyStateDerivesStatsFromLevel() {
        val raw = rawBattleArmy(level = 5, conditions = arrayOf("weary", "notACondition"))
        val state = toBattleArmyState(raw)
        assertEquals(getArmyAttackBonus(5), state.attackBonus)
        assertEquals(getArmyAc(5), state.ac)
        assertEquals(setOf(ArmyCondition.WEARY), state.conditions)
        assertEquals(raw.currentHp, state.currentHp)
        assertEquals(raw.xp, state.xp)
    }

    @Test
    fun routThresholdIsQuarterMaxHpRoundedUp() {
        assertEquals(5, routThresholdFor("Unknown Army", 20))
        assertEquals(2, routThresholdFor("Unknown Army", 5))
        assertEquals(1, routThresholdFor("Unknown Army", 1))
    }

    private fun engineArmy(name: String, destroyed: Boolean) = BattleArmyState(
        name = name,
        level = 3,
        currentHp = if (destroyed) 0 else 4,
        maxHp = 4,
        conditions = if (destroyed) setOf(ArmyCondition.DESTROYED) else emptySet(),
        attackBonus = 10,
        ac = 16,
        routThreshold = 1,
    )

    @Test
    fun determineBattleStatusVictoryWhenAllDefendersDestroyed() = runTest {
        val state = toBattleState(rawArmyBattle()).copy(
            armies = listOf(engineArmy("a", destroyed = false), engineArmy("d", destroyed = true)),
        )
        assertEquals(BattleStatus.VICTORY, determineBattleStatus(state, attackerCount = 1))
    }

    @Test
    fun determineBattleStatusDefeatWhenAllAttackersDestroyed() = runTest {
        val state = toBattleState(rawArmyBattle()).copy(
            armies = listOf(engineArmy("a", destroyed = true), engineArmy("d", destroyed = false)),
        )
        assertEquals(BattleStatus.DEFEAT, determineBattleStatus(state, attackerCount = 1))
    }

    @Test
    fun determineBattleStatusActiveWhileBothSidesLive() = runTest {
        val state = toBattleState(rawArmyBattle()).copy(
            armies = listOf(engineArmy("a", destroyed = false), engineArmy("d", destroyed = false)),
        )
        assertEquals(BattleStatus.ACTIVE, determineBattleStatus(state, attackerCount = 1))
    }

    @Test
    fun updateRawBattleKeepsARoutDecidedVictoryAfterEndOfBattleRecovery() = runTest {
        // A battle can be decided by ROUT, but end-of-battle recovery always clears ROUTED.
        // The status must reflect the state the battle was decided on, not the recovered one.
        val battle = rawArmyBattle(
            attackers = arrayOf(rawBattleArmy(name = "1st Legion")),
            defenders = arrayOf(rawBattleArmy(name = "Goblin Scouts")),
        )
        val state = toBattleState(battle)
        val routed = state.copy(
            armies = listOf(
                state.armies[0],
                state.armies[1].copy(conditions = setOf(ArmyCondition.ROUTED)),
            ),
        )
        val decided = determineBattleStatus(routed, attackerCount = 1)
        assertEquals(BattleStatus.VICTORY, decided)

        val recovered = routed.copy(armies = routed.armies.map { recoverConditions(it) })
        assertTrue(
            ArmyCondition.ROUTED !in recovered.armies[1].conditions,
            "recovery is expected to clear ROUTED; that is what makes the override necessary",
        )

        assertEquals(
            BattleStatus.ACTIVE.value,
            updateRawBattle(battle, recovered).status,
            "without the override the recovered state reads as ACTIVE again",
        )
        assertEquals(
            BattleStatus.VICTORY.value,
            updateRawBattle(battle, recovered, statusOverride = decided).status,
        )
    }

    @Test
    fun updateRawBattleKeepsARoutDecidedDefeatAfterEndOfBattleRecovery() = runTest {
        val battle = rawArmyBattle(
            attackers = arrayOf(rawBattleArmy(name = "1st Legion")),
            defenders = arrayOf(rawBattleArmy(name = "Goblin Scouts")),
        )
        val state = toBattleState(battle)
        val routed = state.copy(
            armies = listOf(
                state.armies[0].copy(conditions = setOf(ArmyCondition.ROUTED)),
                state.armies[1],
            ),
        )
        val decided = determineBattleStatus(routed, attackerCount = 1)
        assertEquals(BattleStatus.DEFEAT, decided)

        val recovered = routed.copy(armies = routed.armies.map { recoverConditions(it) })
        assertEquals(
            BattleStatus.DEFEAT.value,
            updateRawBattle(battle, recovered, statusOverride = decided).status,
        )
    }

    @Test
    fun updateRawBattlePreservesIdentityAndRecomputesStatus() = runTest {
        val battle = rawArmyBattle(
            attackers = arrayOf(rawBattleArmy(name = "1st Legion", xp = 15)),
            defenders = arrayOf(rawBattleArmy(name = "Goblin Scouts")),
        )
        val state = toBattleState(battle)
        val afterRound = state.copy(
            round = state.round + 1,
            armies = listOf(
                state.armies[0].copy(currentHp = 10),
                state.armies[1].copy(currentHp = 0, conditions = setOf(ArmyCondition.DESTROYED)),
            ),
            log = state.log + "Goblin Scouts is destroyed!",
        )
        val updated = updateRawBattle(battle, afterRound)
        assertEquals("Actor.abc123", updated.attackers[0].armyActorUuid)
        assertEquals(15, updated.attackers[0].xp)
        assertEquals(10, updated.attackers[0].currentHp)
        assertEquals(0, updated.defenders[0].currentHp)
        assertTrue(ArmyCondition.DESTROYED.value in updated.defenders[0].conditions)
        assertEquals(BattleStatus.VICTORY.value, updated.status)
        assertEquals(state.round + 1, updated.round)
        assertEquals("Goblin Scouts is destroyed!", updated.log.last())
    }

    @Test
    fun awardVictoryXpGrantsXpToSurvivorsOnly() {
        val survivors = arrayOf(
            rawBattleArmy(name = "1st Legion", level = 5, xp = 10),
            rawBattleArmy(name = "2nd Legion", level = 5, conditions = arrayOf("destroyed")),
        )
        val defeated = arrayOf(
            rawBattleArmy(name = "Goblin Scouts", level = 3, conditions = arrayOf("destroyed")),
        )
        val rewarded = awardVictoryXp(survivors, defeated)
        assertEquals(10 + awardBattleXp(5, 3), rewarded[0].xp)
        assertEquals(0, rewarded[1].xp)
    }

    @Test
    fun awardVictoryXpIgnoresSurvivingEnemies() {
        val survivors = arrayOf(rawBattleArmy(name = "1st Legion", level = 5, xp = 0))
        val defeated = arrayOf(rawBattleArmy(name = "Goblin Scouts", level = 3))
        val rewarded = awardVictoryXp(survivors, defeated)
        assertEquals(0, rewarded[0].xp)
    }

    @Test
    fun createArmyBattleBuildsBothSidesFromThreat() = runTest {
        val threat = RawWarThreat(
            id = "w1", name = "Goblin Horde", description = "raiders", enemyFaction = null,
            escalationLevel = 0, maxEscalation = 4, eta = 2,
            targetSettlementSceneId = null, targetHexLocation = null,
            linkedQuestId = null, linkedEventId = null, pauseOnExpiry = false,
            status = "active", triggeredTurn = null,
        )
        val battle = createArmyBattle(
            id = "battle-1",
            threat = threat,
            attackers = listOf(BattleArmyInfo(uuid = "Actor.a", name = "1st Legion", level = 4)),
            terrain = "swamp",
        )
        assertEquals("w1", battle.threatId)
        assertEquals(BattleStatus.ACTIVE.value, battle.status)
        assertEquals(1, battle.attackers.size)
        assertEquals("Actor.a", battle.attackers[0].armyActorUuid)
        assertEquals(4, battle.attackers[0].level)
        assertEquals(battle.attackers[0].maxHp, battle.attackers[0].currentHp)
        assertEquals(1, battle.defenders.size)
        // escalation 0 is clamped to a level-1 enemy army
        assertEquals(1, battle.defenders[0].level)
        assertEquals(0, battle.round)
        assertEquals("swamp", battle.terrain)
    }

    @Test
    fun resolveBattleTerrainResolvesFromSettlement() {
        val rawSettlement = js("{ sceneId: 'scene-1', terrain: 'forest' }").unsafeCast<RawSettlement>()
        val resolved = resolveBattleTerrain(
            targetSettlementSceneId = "scene-1",
            targetHexLocation = null,
            settlements = arrayOf(rawSettlement),
        )
        assertEquals("forest", resolved)
    }
}

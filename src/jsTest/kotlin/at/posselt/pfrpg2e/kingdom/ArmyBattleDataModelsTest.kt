package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.kingdom.data.RawBattleArmy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private fun battleArmy(
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

private fun armyBattle(
    id: String = "battle-1",
    threatId: String? = null,
    name: String = "Skirmish at the Border",
    round: Int = 1,
    terrain: String? = "forest",
    attackers: Array<RawBattleArmy> = arrayOf(battleArmy(name = "1st Legion")),
    defenders: Array<RawBattleArmy> = arrayOf(battleArmy(name = "Goblin Scouts")),
    log: Array<String> = arrayOf("Battle begins."),
    status: String = "active",
): RawArmyBattle = RawArmyBattle(
    id = id,
    threatId = threatId,
    name = name,
    round = round,
    terrain = terrain,
    attackers = attackers,
    defenders = defenders,
    log = log,
    status = status,
)

class ArmyBattleDataModelsTest {
    @Test
    fun rawBattleArmyFieldsAreAccessible() {
        val army = battleArmy(
            name = "Cavalry",
            level = 8,
            currentHp = 15,
            maxHp = 25,
            conditions = arrayOf("mired", "weary"),
            xp = 120,
        )
        assertEquals("Cavalry", army.name)
        assertEquals(8, army.level)
        assertEquals(15, army.currentHp)
        assertEquals(25, army.maxHp)
        assertEquals(2, army.conditions.size)
        assertEquals("mired", army.conditions[0])
        assertEquals("weary", army.conditions[1])
        assertEquals(120, army.xp)
    }

    @Test
    fun rawBattleArmyDefaultsWork() {
        val army = battleArmy()
        assertEquals("Actor.abc123", army.armyActorUuid)
        assertEquals("1st Legion", army.name)
        assertEquals(20, army.currentHp)
        assertEquals(20, army.maxHp)
        assertEquals(0, army.conditions.size)
        assertEquals(0, army.xp)
    }

    @Test
    fun rawArmyBattleFieldsAreAccessible() {
        val battle = armyBattle(
            id = "b-42",
            threatId = "t-7",
            name = "Siege of the Capital",
            round = 3,
            terrain = "mountains",
            attackers = arrayOf(battleArmy(name = "Royal Guard"), battleArmy(name = "Militia")),
            defenders = arrayOf(battleArmy(name = "Invading Horde")),
            log = arrayOf("Round 1: Army engages.", "Round 2: Cavalry flanks.", "Round 3: Enemies retreat."),
            status = "active",
        )
        assertEquals("b-42", battle.id)
        assertEquals("t-7", battle.threatId)
        assertEquals("Siege of the Capital", battle.name)
        assertEquals(3, battle.round)
        assertEquals("mountains", battle.terrain)
        assertEquals(2, battle.attackers.size)
        assertEquals("Royal Guard", battle.attackers[0].name)
        assertEquals("Militia", battle.attackers[1].name)
        assertEquals(1, battle.defenders.size)
        assertEquals("Invading Horde", battle.defenders[0].name)
        assertEquals(3, battle.log.size)
        assertEquals("active", battle.status)
    }

    @Test
    fun rawArmyBattleThreatIdIsNullable() {
        val battle = armyBattle(threatId = null)
        assertNull(battle.threatId)
    }

    @Test
    fun rawArmyBattleTerrainIsNullable() {
        val battle = armyBattle(terrain = null)
        assertNull(battle.terrain)
    }

    @Test
    fun rawArmyBattleDefaultsWork() {
        val battle = armyBattle()
        assertEquals("battle-1", battle.id)
        assertEquals("Skirmish at the Border", battle.name)
        assertEquals(1, battle.round)
        assertEquals(1, battle.attackers.size)
        assertEquals(1, battle.defenders.size)
    }

    @Test
    fun conditionsArrayCanHoldArmyConditionValues() {
        val army = battleArmy(
            conditions = arrayOf("mired", "pinned", "damaged"),
        )
        assertEquals(3, army.conditions.size)
        assertEquals("mired", army.conditions[0])
        assertEquals("pinned", army.conditions[1])
        assertEquals("damaged", army.conditions[2])
    }

    @Test
    fun statusCanHoldBattleStatusValues() {
        val active = armyBattle(status = "active")
        assertEquals("active", active.status)
        val victory = armyBattle(status = "victory")
        assertEquals("victory", victory.status)
        val defeat = armyBattle(status = "defeat")
        assertEquals("defeat", defeat.status)
        val retreat = armyBattle(status = "retreat")
        assertEquals("retreat", retreat.status)
    }
}

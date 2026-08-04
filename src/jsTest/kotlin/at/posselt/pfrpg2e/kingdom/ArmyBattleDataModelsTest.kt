package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.ActorArmyMapping
import at.posselt.pfrpg2e.data.armies.BattleArmyState
import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.kingdom.data.RawBattleArmy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // ── ActorArmyMapping tests ──────────────────────────────────────────────

    // Fixtures mirror the REAL PF2e ArmySystemData schema (v8.1.2): top-level system.ac
    // {value, potency}, system.saves {maneuver, morale}, system.weapons {melee, ranged} with
    // {name, potency}, and system.attributes.hp {value, max, routThreshold}. The original
    // fixtures asserted an invented shape (attributes.ac, items[].system.bonus) that no real
    // army actor has — the tests were green while every custom army fell back to the level table.

    @Test
    fun actorArmyMappingUsesActorStatsForCustomArmy() {
        // Custom PF2EArmy: 60 HP, sheet AC 22 + potency 1, saves 13/9, melee potency 2, level 5
        val actorData = js(
            """
            {
              "name": "1st Legion",
              "system": {
                "details": { "level": { "value": 5 } },
                "ac": { "value": 22, "potency": 1 },
                "saves": { "maneuver": 13, "morale": 9 },
                "weapons": { "melee": { "name": "Pikes", "potency": 2 }, "ranged": null },
                "attributes": {
                  "hp": { "value": 60, "max": 60, "routThreshold": 15 }
                }
              }
            }
            """
        ).unsafeCast<Any>()

        val state = ActorArmyMapping.toBattleArmyStateFromActorData(actorData)

        assertEquals("1st Legion", state.name)
        assertEquals(5, state.level)
        assertEquals(60, state.maxHp)
        assertEquals(60, state.currentHp)
        assertEquals(23, state.ac)              // sheet 22 + armor potency 1
        assertEquals(17, state.attackBonus)     // level-5 table 15 + weapon potency 2
        assertEquals(15, state.routThreshold)   // straight from the sheet
        assertEquals(13, state.highSave)
        assertEquals(9, state.lowSave)
        assertEquals(9, state.moraleBonus)
    }

    @Test
    fun actorArmyMappingFallsBackToWorkbookForMissingFields() {
        // Minimal actor data (only name and level) — synthesized-enemy path must not regress
        val actorData = js(
            """
            {
              "name": "Test Army",
              "system": {
                "details": { "level": { "value": 3 } },
                "attributes": {}
              }
            }
            """
        ).unsafeCast<Any>()

        val state = ActorArmyMapping.toBattleArmyStateFromActorData(actorData)

        assertEquals("Test Army", state.name)
        assertEquals(3, state.level)
        // Should fall back to workbook HP for unknown army name (default 4)
        assertEquals(4, state.maxHp)
        assertEquals(4, state.currentHp)
        // Should fall back to workbook AC for level 3
        assertEquals(19, state.ac)  // getArmyAc(3) = 19
        // Should fall back to workbook attack for level 3
        assertEquals(12, state.attackBonus)  // getArmyAttackBonus(3) = 12
    }

    @Test
    fun actorArmyMappingUsesBestWeaponPotency() {
        // Ranged potency 3 beats melee potency 1: attack = level-5 table 15 + 3
        val actorData = js(
            """
            {
              "name": "Elite Guard",
              "system": {
                "details": { "level": { "value": 5 } },
                "ac": { "value": 20, "potency": 0 },
                "saves": { "maneuver": 12, "morale": 14 },
                "weapons": {
                  "melee": { "name": "Swords", "potency": 1 },
                  "ranged": { "name": "Longbows", "potency": 3 }
                },
                "attributes": {
                  "hp": { "value": 20, "max": 20, "routThreshold": 5 }
                }
              }
            }
            """
        ).unsafeCast<Any>()

        val state = ActorArmyMapping.toBattleArmyStateFromActorData(actorData)

        assertEquals(18, state.attackBonus)  // 15 + max(1, 3)
        assertEquals(20, state.ac)
        // saves can be inverted on the sheet — high/low take the actual max/min
        assertEquals(14, state.highSave)
        assertEquals(12, state.lowSave)
    }

    @Test
    fun actorArmyMappingHandlesCurrentHpLessThanMax() {
        val actorData = js(
            """
            {
              "name": "Wounded Army",
              "system": {
                "details": { "level": { "value": 1 } },
                "ac": { "value": 16, "potency": 0 },
                "attributes": {
                  "hp": { "value": 2, "max": 10 }
                }
              }
            }
            """
        ).unsafeCast<Any>()

        val state = ActorArmyMapping.toBattleArmyStateFromActorData(actorData)

        assertEquals(10, state.maxHp)
        assertEquals(2, state.currentHp)
        assertEquals(16, state.ac)
        // No sheet routThreshold -> derived fallback ceil(10/4)
        assertEquals(3, state.routThreshold)
    }
}

package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.AgendaArchetypeSpec
import at.posselt.pfrpg2e.data.kingdom.AgendaMoveSpec
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.kingdom.RawCouncilCooldowns
import at.posselt.pfrpg2e.kingdom.data.RawConsumption
import at.posselt.pfrpg2e.kingdom.data.RawCommodities
import at.posselt.pfrpg2e.kingdom.data.RawCurrentCommodities
import at.posselt.pfrpg2e.kingdom.data.RawFactionAgenda
import at.posselt.pfrpg2e.kingdom.data.RawFame
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.data.RawResources
import js.objects.recordOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FactionAgendaTickTest {
    private val moveSpecs = mapOf(
        "expand" to AgendaMoveSpec("expand", 2, "self", "clock", 1, null),
        "sabotage-rival" to AgendaMoveSpec("sabotage-rival", 3, "rival", "standing-delta", -10, "not-at-war"),
        "court-ally" to AgendaMoveSpec("court-ally", 2, "ally", "standing-delta", 8, null),
        "court-pcs" to AgendaMoveSpec("court-pcs", 0, "pcs", "standing-delta", 5, null),
    )
    private val archetypeSpecs = mapOf(
        "aggressive" to AgendaArchetypeSpec(
            "aggressive",
            mapOf("expand" to 30, "sabotage-rival" to 25, "court-ally" to 15, "court-pcs" to 10),
            listOf("conquer-neighbor", "build-army"),
        ),
    )

    private fun rawAgenda(lastAdvancedTurn: Int? = null) = RawFactionAgenda(
        goalId = "conquer-neighbor", goalTitle = "", progress = 0, segments = 6,
        archetype = "aggressive", moveCooldowns = recordOf(),
        lastAdvancedTurn = lastAdvancedTurn, targetFaction = null,
    )

    private fun group(name: String, standing: Int? = 0, agenda: RawFactionAgenda? = rawAgenda()) = RawGroup(
        name = name, negotiationDC = 15, atWar = false, preventPledgeOfFealty = false,
        relations = "none", standing = standing, standingLog = null, allianceLevel = null,
        hexKey = "5.5", agenda = agenda,
    )

    private fun runTick(groups: Array<RawGroup>, turn: Int, withCatalogs: Boolean = true) =
        TurnTickingEngine.tick(
            fame = RawFame(now = 0, next = 0, type = "famous"),
            resourcePoints = RawResources(now = 0, next = 0),
            resourceDice = RawResources(now = 0, next = 0),
            consumption = RawConsumption(now = 0, next = 0, armies = 0),
            commodities = RawCurrentCommodities(
                now = RawCommodities(food = 0, lumber = 0, luxuries = 0, ore = 0, stone = 0),
                next = RawCommodities(food = 0, lumber = 0, luxuries = 0, ore = 0, stone = 0),
            ),
            storage = CommodityStorage(food = 100, lumber = 100, luxuries = 100, ore = 100, stone = 100),
            councilCooldowns = RawCouncilCooldowns(audit = 0, scrying = 0, lockdown = 0, feast = 0),
            modifiers = emptyArray(),
            currentTurn = turn,
            groups = groups,
            kingdomName = "Elkhaven",
            agendaMoves = if (withCatalogs) moveSpecs else emptyMap(),
            agendaArchetypes = if (withCatalogs) archetypeSpecs else emptyMap(),
        )

    @Test
    fun tickAdvancesAgendasEmitsMovesAndNeverTouchesStanding() {
        val groups = arrayOf(
            group("Pitax", standing = 0),
            group("Mivon", standing = -30),
            group("Varnhold", standing = 10, agenda = null),
        )
        val result = runTick(groups, turn = 3)
        assertTrue(result.factionAgendaMoves.isNotEmpty())
        assertTrue(result.factionAgendaMoves.none { it.factionName == "Varnhold" })
        val byName = result.groups.associateBy { it.name }
        assertEquals(3, byName.getValue("Pitax").agenda?.lastAdvancedTurn)
        assertEquals(3, byName.getValue("Mivon").agenda?.lastAdvancedTurn)
        assertNull(byName.getValue("Varnhold").agenda)
        // intents only: no drift configured, so NO standing may change in the tick itself
        assertEquals(0, byName.getValue("Pitax").standing)
        assertEquals(-30, byName.getValue("Mivon").standing)
    }

    @Test
    fun previewAndCommitProduceIdenticalMoves() {
        fun freshGroups() = arrayOf(group("Pitax"), group("Mivon", standing = -30))
        val a = runTick(freshGroups(), turn = 5)
        val b = runTick(freshGroups(), turn = 5)
        assertEquals(a.factionAgendaMoves, b.factionAgendaMoves)
        assertEquals(
            a.groups.map { it.agenda?.progress to it.agenda?.goalId },
            b.groups.map { it.agenda?.progress to it.agenda?.goalId },
        )
    }

    @Test
    fun emptyCatalogsKeepTheFeatureInert() {
        val groups = arrayOf(group("Pitax"))
        val result = runTick(groups, turn = 2, withCatalogs = false)
        assertTrue(result.factionAgendaMoves.isEmpty())
        assertNull(result.groups.single().agenda?.lastAdvancedTurn)
    }
}

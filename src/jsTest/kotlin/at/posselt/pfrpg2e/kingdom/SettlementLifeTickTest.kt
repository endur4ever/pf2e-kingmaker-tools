package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.settlements.NpcEntry
import at.posselt.pfrpg2e.data.kingdom.settlements.PopulationRoster
import at.posselt.pfrpg2e.data.kingdom.settlements.Settlement
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementLayoutType
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementType
import at.posselt.pfrpg2e.data.kingdom.settlements.settlementSizeData
import at.posselt.pfrpg2e.data.kingdom.structures.AvailableItemBonuses
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.data.kingdom.structures.Structure
import at.posselt.pfrpg2e.kingdom.settlementlife.MAX_LIFE_EVENTS_PER_TURN
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ADAPTER around the settlement-life engine -- the layer that did not exist. Pins the caps
 * and gates as the adapter actually applies them, the record it writes, and that re-running a
 * turn cannot stack the same event twice.
 */
class SettlementLifeTickTest {
    private fun settlement(
        id: String,
        roster: List<NpcEntry> = listOf(NpcEntry("n1", "Svetlana", "Merchant"), NpcEntry("n2", "Oleg", "Farmer")),
        structures: List<String> = listOf("marketplace"),
    ) = Settlement(
        id = id,
        name = "Town $id",
        type = SettlementType.SETTLEMENT,
        waterBorders = 0,
        isSecondaryTerritory = false,
        settlementEventBonus = 0,
        leaderLeadershipActivityBonus = 0,
        bonuses = emptySet(),
        allowCapitalInvestment = false,
        notes = emptySet(),
        storage = CommodityStorage(),
        increaseLeadershipActivities = false,
        consumptionReduction = 0,
        availableItems = AvailableItemBonuses(),
        size = settlementSizeData.first(),
        unlockActivities = emptySet(),
        residentialLots = 0,
        hasBridge = false,
        occupiedBlocks = 2,
        preventItemLevelPenalty = false,
        delayedStructures = emptyList(),
        constructedStructures = structures.map { Structure(id = it, uuid = "", actorUuid = "", name = it) },
        structuresUnderConstruction = emptyList(),
        maximumCivicRdLimit = 0,
        settlementActions = 0,
        blocks = emptyList(),
        layoutType = SettlementLayoutType.RIGID,
        populationRoster = PopulationRoster(roster),
    )

    private fun kingdom(vararg sceneIds: String): KingdomData = unsafeJso<dynamic> {
        settlements = sceneIds.map { id -> unsafeJso<dynamic> { sceneId = id; lifeEventHistory = null } }.toTypedArray()
    }.unsafeCast<KingdomData>()

    private fun roll(k: KingdomData, settlements: List<Settlement>, turn: Int, chance: Int = 1) =
        rollSettlementLifeEvents(k, settlements, season = null, currentTurn = turn, chanceRoll = { chance }, pickRoll = { 0 })

    private fun history(k: KingdomData, id: String) =
        k.settlements.first { it.sceneId == id }.lifeEventHistory?.toList().orEmpty()

    @Test
    fun theKingdomWideCapHoldsHoweverManyTownsWantToFire() {
        val k = kingdom("a", "b", "c", "d")
        val fired = roll(k, listOf("a", "b", "c", "d").map { settlement(it) }, turn = 1)
        assertEquals(MAX_LIFE_EVENTS_PER_TURN, fired.size)
        assertEquals(MAX_LIFE_EVENTS_PER_TURN, listOf("a", "b", "c", "d").sumOf { history(k, it).size })
    }

    @Test
    fun failingTheChanceRollFiresNothing() {
        val k = kingdom("a")
        assertTrue(roll(k, listOf(settlement("a")), turn = 1, chance = 100).isEmpty())
        assertTrue(history(k, "a").isEmpty())
    }

    @Test
    fun theRecordCarriesTheCastAndTheOfferState() {
        val k = kingdom("a")
        val fired = roll(k, listOf(settlement("a")), turn = 3).single()
        val record = history(k, "a").single()
        assertEquals(fired.recordId, record.recordId)
        assertEquals(3, record.turn)
        assertEquals(fired.templateId, record.templateId)
        assertTrue(record.castNames.isNotEmpty(), "nobody was cast")
        // every cast id is a real roster resident -- the generator never invents one into the roster
        record.castNpcIds.forEach { assertTrue(it in setOf("n1", "n2"), "unknown npc $it") }
        assertEquals(false, record.hookApplied, "an unanswered offer must not start applied")
        assertTrue(fired.gazetteLine.isNotBlank())
    }

    @Test
    fun anEmptyRosterCastsSomeoneEphemeralAndNeverAnNpcId() {
        val k = kingdom("a")
        roll(k, listOf(settlement("a", roster = emptyList())), turn = 1)
        val record = history(k, "a").single()
        assertTrue(record.castNpcIds.isEmpty())
        assertTrue(record.castNames.isNotEmpty())
    }

    @Test
    fun aTemplateOnCooldownDoesNotFireAgainInTheSameTown() {
        // every shipped template carries a cooldown of at least one turn
        val k = kingdom("a")
        val first = roll(k, listOf(settlement("a")), turn = 1).single()
        val second = roll(k, listOf(settlement("a")), turn = 2)
        second.forEach { assertTrue(it.templateId != first.templateId, "${first.templateId} fired inside its cooldown") }
    }

    @Test
    fun reRunningTheSameTurnCannotStackTheSameEventTwice() {
        // the undo-and-redo path re-enters End Turn on the same turn number
        val k = kingdom("a")
        roll(k, listOf(settlement("a")), turn = 5)
        roll(k, listOf(settlement("a")), turn = 5)
        assertEquals(1, history(k, "a").size)
    }

    @Test
    fun theSameTownCannotMonopoliseBothSlotsForever() {
        // three towns, two slots a turn: across three turns every town gets its month
        val k = kingdom("a", "b", "c")
        val towns = listOf("a", "b", "c").map { settlement(it) }
        val seen = (1..3).flatMap { turn -> roll(k, towns, turn).map { it.settlementId } }.toSet()
        assertEquals(setOf("a", "b", "c"), seen)
    }

    @Test
    fun aTownTheKingdomDoesNotKnowIsSkippedNotCrashed() {
        val k = kingdom("a")
        val fired = roll(k, listOf(settlement("ghost"), settlement("a")), turn = 1)
        assertTrue(fired.all { it.settlementId == "a" })
    }

    @Test
    fun aVkVariantStructureSatisfiesTheTemplatesBaseId() {
        // plan 6.1: the catalog names "thieves-guild"; the V&K variant is "thieves-guild-vk". The
        // engine matches exactly, so raw ids would make guild-theft un-fireable in such a town.
        fun firedWith(structures: List<String>): Set<String> {
            val k = kingdom("a")
            return (1..4).flatMap { turn -> roll(k, listOf(settlement("a", structures = structures)), turn) }
                .map { it.templateId }.toSet()
        }
        assertTrue("guild-theft" in firedWith(listOf("thieves-guild-vk")), "the V&K guild did not count")
        assertTrue("guild-theft" !in firedWith(emptyList()), "guild-theft fired with no guild at all")
    }
}

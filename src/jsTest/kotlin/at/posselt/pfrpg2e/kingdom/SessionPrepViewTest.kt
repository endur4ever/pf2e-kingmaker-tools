package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.campaign.CampaignClock
import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.data.createRawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.data.RawQuest
import at.posselt.pfrpg2e.kingdom.data.RawQuestRewards
import at.posselt.pfrpg2e.kingdom.data.RawTurnRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionPrepViewTest {
    private fun quest(id: String, status: String, title: String = id, giver: String = "") =
        RawQuest(
            id = id,
            title = title,
            description = "",
            giver = giver,
            status = status,
            type = "other",
            target = null,
            rewards = RawQuestRewards(),
            flavorTextCompleted = "",
        )

    private fun clock(id: String, active: Boolean, expired: Boolean, turnsRemaining: Int = 3) =
        CampaignClock(
            id = id,
            label = id,
            turnsRemaining = turnsRemaining,
            maxTurns = 5,
            description = "",
            pauseOnExpiry = false,
            expired = expired,
            active = active,
            expiryConsequenceUnrest = 0,
            expiryMessage = "",
        )

    private fun event(id: String, status: String, name: String = id): dynamic {
        val e = js("({})")
        e.id = id
        e.name = name
        e.status = status
        return e
    }

    private fun warThreat(id: String, name: String): dynamic {
        val wt = js("({})")
        wt.id = id
        wt.name = name
        return wt
    }

    private fun hex(id: String, visibility: HexContentVisibility, name: String = id, hexKey: String = "0,0") =
        RawHexContent(
            id = id,
            hexKey = hexKey,
            type = HexContentType.LANDMARK.value,
            name = name,
            visibility = visibility.value,
            gmNotes = "secret",
            playerText = "",
        )

    private fun companionQuest(id: String, status: String, visibleToPlayers: Boolean, turnsRemaining: Int? = null) =
        CompanionPersonalQuest(
            id = id,
            title = id,
            description = "",
            companionId = "Amiri",
            status = status,
            visibleToPlayers = visibleToPlayers,
            influenceReward = 0,
            turnsRemaining = turnsRemaining,
        )

    private fun turnRecord(
        turn: Int = 1,
        timestamp: String = "2026-06-11T10:00:00Z",
        fame: Int = 0,
        resourcePoints: Int = 0,
        consumption: Int = 0,
        unrest: Int = 0,
        warPressure: Int? = null,
        xpAwarded: Int? = null,
        clockEvents: Array<String>? = null,
        notes: String? = null,
    ) = RawTurnRecord(
        turn = turn,
        timestamp = timestamp,
        fame = fame,
        resourcePoints = resourcePoints,
        consumption = consumption,
        unrest = unrest,
        xpAwarded = xpAwarded,
        clockEvents = clockEvents,
        warPressure = warPressure,
        notes = notes,
    )

    @Test
    fun pendingEncountersListedForGm() {
        val hexContent = hex("h1", HexContentVisibility.DISCOVERED, name = "Bandit Camp", hexKey = "1,2").also {
            it.pendingEncounter = true
            it.linkedWarThreatId = "wt1"
        }
        val threat = warThreat("wt1", "Ironfang Legion")
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = arrayOf(hexContent),
            companionQuests = null,
            isGM = true,
            warThreats = arrayOf(threat),
        )
        assertEquals(listOf("h1"), view.pendingEncounters.map { it.id })
        assertEquals("1,2 — Ironfang Legion", view.pendingEncounters.single().detail)
        // 1 pending encounter + 1 hex hook (since hex is DISCOVERED and GM sees it)
        assertEquals(2, view.totalCount)
    }

    @Test
    fun pendingEncountersWithUnknownThreat() {
        val hexContent = hex("h1", HexContentVisibility.DISCOVERED, name = "Bandit Camp", hexKey = "1,2").also {
            it.pendingEncounter = true
            it.linkedWarThreatId = "wt1"
        }
        // No war threats provided
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = arrayOf(hexContent),
            companionQuests = null,
            isGM = true,
            warThreats = null,
        )
        assertEquals(listOf("h1"), view.pendingEncounters.map { it.id })
        assertEquals("1,2 — Unknown Threat", view.pendingEncounters.single().detail)
    }

    @Test
    fun pendingEncountersExcludedWhenNotPending() {
        val hexContent = hex("h1", HexContentVisibility.DISCOVERED, name = "Bandit Camp", hexKey = "1,2").also {
            it.pendingEncounter = false
            it.linkedWarThreatId = "wt1"
        }
        val threat = warThreat("wt1", "Ironfang Legion")
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = arrayOf(hexContent),
            companionQuests = null,
            isGM = true,
            warThreats = arrayOf(threat),
        )
        assertTrue(view.pendingEncounters.isEmpty())
    }

    @Test
    fun pendingEncountersFilteredForPlayers() {
        val hexContent = hex("h1", HexContentVisibility.DISCOVERED, name = "Bandit Camp", hexKey = "1,2").also {
            it.pendingEncounter = true
            it.linkedWarThreatId = "wt1"
        }
        val threat = warThreat("wt1", "Ironfang Legion")
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = arrayOf(hexContent),
            companionQuests = null,
            isGM = false,
            warThreats = arrayOf(threat),
        )
        assertTrue(view.pendingEncounters.isEmpty())
        assertFalse(view.isGM)
    }

    @Test
    fun pendingEncountersWithMultipleHexes() {
        val hex1 = hex("h1", HexContentVisibility.DISCOVERED, name = "Bandit Camp", hexKey = "1,2").also {
            it.pendingEncounter = true
            it.linkedWarThreatId = "wt1"
        }
        val hex2 = hex("h2", HexContentVisibility.HIDDEN, name = "Goblin Lair", hexKey = "3,4").also {
            it.pendingEncounter = true
            it.linkedWarThreatId = "wt2"
        }
        val threat1 = warThreat("wt1", "Ironfang Legion")
        val threat2 = warThreat("wt2", "Goblin Horde")
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = arrayOf(hex1, hex2),
            companionQuests = null,
            isGM = true,
            warThreats = arrayOf(threat1, threat2),
        )
        assertEquals(listOf("h1", "h2"), view.pendingEncounters.map { it.id })
        assertEquals("1,2 — Ironfang Legion", view.pendingEncounters.first().detail)
        assertEquals("3,4 — Goblin Horde", view.pendingEncounters.last().detail)
        // 2 pending encounters + 2 hex hooks (both visible to GM)
        assertEquals(4, view.totalCount)
    }

    @Test
    fun nullInputsProduceEmptyView() {
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = null,
            companionQuests = null,
            isGM = true,
        )
        assertFalse(view.hasAnything)
        assertEquals(0, view.totalCount)
        assertTrue(view.isGM)
    }

    @Test
    fun gmSeesAllSectionsAndFiltersTerminalEntries() {
        val view = buildSessionPrepView(
            quests = arrayOf(
                quest("q1", "active", giver = "Oleg"),
                quest("q2", "completed"),
            ),
            clocks = arrayOf(
                clock("c1", active = true, expired = false),
                clock("c2", active = false, expired = false), // deactivated
                clock("c3", active = true, expired = true),    // already expired
            ),
            events = arrayOf(
                event("e1", "active", name = "Bandit Raid"),
                event("e2", "resolved"),
            ),
            hexContents = arrayOf(
                hex("h1", HexContentVisibility.HIDDEN),
                hex("h2", HexContentVisibility.DISCOVERED),
            ),
            companionQuests = arrayOf(
                companionQuest("cq1", "active", visibleToPlayers = false, turnsRemaining = 2),
                companionQuest("cq2", "completed", visibleToPlayers = true),
            ),
            isGM = true,
        )

        assertEquals(listOf("q1"), view.openQuests.map { it.id })
        assertEquals("Oleg", view.openQuests.single().detail)
        assertEquals(listOf("c1"), view.activeClocks.map { it.id })
        assertEquals(3, view.activeClocks.single().turnsRemaining)
        assertEquals(listOf("e1"), view.unresolvedEvents.map { it.id })
        assertEquals("Bandit Raid", view.unresolvedEvents.single().name)
        // GM sees hidden hex content too
        assertEquals(listOf("h1", "h2"), view.hexHooks.map { it.id })
        assertEquals(listOf("cq1"), view.companionMoments.map { it.id })
        assertEquals(2, view.companionMoments.single().turnsRemaining)
        // 1 quest + 1 clock + 1 event + 2 hexes + 1 companion moment
        assertEquals(6, view.totalCount)
        assertTrue(view.hasAnything)
    }

    @Test
    fun playerViewWithholdsGmOnlyContent() {
        val view = buildSessionPrepView(
            quests = arrayOf(quest("q1", "active")),
            clocks = arrayOf(clock("c1", active = true, expired = false)),
            events = arrayOf(event("e1", "active")),
            hexContents = arrayOf(
                hex("h1", HexContentVisibility.HIDDEN),
                hex("h2", HexContentVisibility.DISCOVERED),
                hex("h3", HexContentVisibility.CLEARED),
            ),
            companionQuests = arrayOf(
                companionQuest("cq1", "active", visibleToPlayers = false),
                companionQuest("cq2", "active", visibleToPlayers = true),
            ),
            isGM = false,
        )

        // Open quests still visible to players
        assertEquals(listOf("q1"), view.openQuests.map { it.id })
        // GM-facing sections are withheld
        assertTrue(view.activeClocks.isEmpty())
        assertTrue(view.unresolvedEvents.isEmpty())
        // Hidden hex content withheld; discovered/cleared shown
        assertEquals(listOf("h2", "h3"), view.hexHooks.map { it.id })
        // Only player-visible companion moments
        assertEquals(listOf("cq2"), view.companionMoments.map { it.id })
        assertFalse(view.isGM)
    }

    @Test
    fun recentTurnsGmFilter() {
        // When isGM = false, recentTurns should be empty regardless of turnHistory
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = null,
            companionQuests = null,
            isGM = false,
            turnHistory = arrayOf(
                turnRecord(turn = 1, fame = 10),
                turnRecord(turn = 2, fame = 20),
            )
        )
        assertTrue(view.recentTurns.isEmpty())
        assertFalse(view.isGM)
    }

    @Test
    fun recentTurnsLastTen() {
        // Should only show last 10 turns even if more provided
        val twelveTurns = (1..12).map { turnRecord(turn = it, fame = it * 10) }.toTypedArray()
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = null,
            companionQuests = null,
            isGM = true,
            turnHistory = twelveTurns
        )
        assertEquals(10, view.recentTurns.size)
        // Should contain turns 3-12 (the last 10)
        assertEquals(listOf(12, 11, 10, 9, 8, 7, 6, 5, 4, 3),
            view.recentTurns.map { it.turn })
        // Turns 1 and 2 should be dropped
        assertFalse(view.recentTurns.any { it.turn == 1 || it.turn == 2 })
    }

    @Test
    fun recentTurnsOrdering() {
        // Should be newest first (reverse chronological order)
        val turnHistory = arrayOf(
            turnRecord(turn = 1, fame = 10),
            turnRecord(turn = 2, fame = 20),
            turnRecord(turn = 3, fame = 30),
            turnRecord(turn = 4, fame = 40),
            turnRecord(turn = 5, fame = 50)
        )
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = null,
            companionQuests = null,
            isGM = true,
            turnHistory = turnHistory
        )
        assertEquals(5, view.recentTurns.size)
        // Most recent turn (5) should be first, oldest (1) should be last
        assertEquals(listOf(5, 4, 3, 2, 1),
            view.recentTurns.map { it.turn })
        assertEquals(listOf(50, 40, 30, 20, 10),
            view.recentTurns.map { it.fame })
    }

    @Test
    fun recentTurnsEmptyWhenNull() {
        // Should handle null turnHistory gracefully
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = null,
            companionQuests = null,
            isGM = true,
            turnHistory = null
        )
        assertTrue(view.recentTurns.isEmpty())
    }

    @Test
    fun recentTurnsEmptyWhenEmptyArray() {
        // Should handle empty turnHistory gracefully
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = null,
            companionQuests = null,
            isGM = true,
            turnHistory = emptyArray()
        )
        assertTrue(view.recentTurns.isEmpty())
    }

    @Test
    fun companionExpeditionsListedWithDaysRemaining() {
        val expedition = createRawCompanionExpedition(
            id = "exp1",
            activityId = "scout",
            title = "Scout the Western Marshes",
            companionIds = arrayOf("Amiri"),
            totalDays = 5,
            dc = 15,
            tier = "standard",
            visibleToPlayers = true,
        ).also { it.status = "inProgress"; it.daysRemaining = 3 }
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = null,
            companionQuests = null,
            isGM = true,
            companionExpeditions = arrayOf(expedition),
        )
        assertEquals(listOf("exp1"), view.companionExpeditions.map { it.id })
        assertEquals(3, view.companionExpeditions.single().turnsRemaining)
        assertEquals("scout", view.companionExpeditions.single().detail)
        // 1 expedition
        assertEquals(1, view.totalCount)
    }

    @Test
    fun completedExpeditionsExcluded() {
        val expedition = createRawCompanionExpedition(
            id = "exp1",
            activityId = "scout",
            title = "Scout the Western Marshes",
            companionIds = arrayOf("Amiri"),
            totalDays = 5,
            dc = 15,
            tier = "standard",
            visibleToPlayers = true,
        ).also { it.status = "resolved"; it.daysRemaining = 0 }
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = null,
            companionQuests = null,
            isGM = true,
            companionExpeditions = arrayOf(expedition),
        )
        assertTrue(view.companionExpeditions.isEmpty())
    }

    @Test
    fun awaitingResolutionExpeditionsIncluded() {
        val expedition = createRawCompanionExpedition(
            id = "exp1",
            activityId = "diplomacy",
            title = "Negotiate with Breachwarden",
            companionIds = arrayOf("Ezren"),
            totalDays = 7,
            dc = 18,
            tier = "perilous",
            visibleToPlayers = false,
        ).also { it.status = "awaitingResolution"; it.daysRemaining = 0 }
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = null,
            companionQuests = null,
            isGM = true,
            companionExpeditions = arrayOf(expedition),
        )
        assertEquals(listOf("exp1"), view.companionExpeditions.map { it.id })
    }

    @Test
    fun hiddenExpeditionsFilteredForPlayers() {
        val expedition = createRawCompanionExpedition(
            id = "exp1",
            activityId = "scout",
            title = "Secret Mission",
            companionIds = arrayOf("Amiri"),
            totalDays = 5,
            dc = 15,
            tier = "standard",
            visibleToPlayers = false,
        ).also { it.status = "inProgress"; it.daysRemaining = 2 }
        val view = buildSessionPrepView(
            quests = null,
            clocks = emptyArray(),
            events = null,
            hexContents = null,
            companionQuests = null,
            isGM = false,
            companionExpeditions = arrayOf(expedition),
        )
        assertTrue(view.companionExpeditions.isEmpty())
    }
}
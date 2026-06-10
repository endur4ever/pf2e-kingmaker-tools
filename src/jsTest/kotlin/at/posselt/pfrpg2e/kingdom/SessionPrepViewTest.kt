package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.campaign.CampaignClock
import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.data.RawQuest
import at.posselt.pfrpg2e.kingdom.data.RawQuestRewards
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
}

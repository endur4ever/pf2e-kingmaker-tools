package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionQuestRowsTest {

    private fun quest(
        id: String,
        status: String,
        visible: Boolean,
        companionId: String,
    ): CompanionPersonalQuest =
        js("{ id: id, title: 'T', description: 'D', companionId: companionId, status: status, turnsRemaining: null, visibleToPlayers: visible, influenceReward: 0 }")
            .unsafeCast<CompanionPersonalQuest>()

    @Test
    fun `rows resolve companion name and index`() {
        val amiri = RawCharacter("Amiri", "uuid-a").apply { personalQuestIds = arrayOf("q1") }
        val linzi = RawCharacter("Linzi").apply { personalQuestIds = arrayOf() }
        val rows = buildCompanionQuestRows(
            quests = arrayOf(quest("q1", "active", visible = true, companionId = "uuid-a")),
            companions = arrayOf(amiri, linzi),
            isGM = true,
        )
        assertEquals(1, rows.size)
        assertEquals("Amiri", rows[0].companionName)
        assertEquals(0, rows[0].companionIndex)
        assertTrue(rows[0].isActive)
    }

    @Test
    fun `player view drops GM-only rows`() {
        val amiri = RawCharacter("Amiri", "uuid-a").apply { personalQuestIds = arrayOf("q1", "q2") }
        val rows = buildCompanionQuestRows(
            quests = arrayOf(
                quest("q1", "active", visible = false, companionId = "uuid-a"),
                quest("q2", "active", visible = true, companionId = "uuid-a"),
            ),
            companions = arrayOf(amiri),
            isGM = false,
        )
        assertEquals(1, rows.size)
        assertEquals("q2", rows[0].id)
    }

    @Test
    fun `companionHasActivePersonalQuests blocks deletion only while a quest is active`() {
        val amiri = RawCharacter("Amiri", "uuid-a").apply { personalQuestIds = arrayOf("q1") }
        val kingdom = js("{}").unsafeCast<KingdomData>()
        kingdom.companions = arrayOf(amiri)

        kingdom.companionPersonalQuests = arrayOf(quest("q1", "active", visible = false, companionId = "uuid-a"))
        assertTrue(kingdom.companionHasActivePersonalQuests(0))

        kingdom.companionPersonalQuests = arrayOf(quest("q1", "completed", visible = false, companionId = "uuid-a"))
        assertFalse(kingdom.companionHasActivePersonalQuests(0))
    }

    @Test
    fun `companionHasActiveExpedition returns true when companion is on expedition`() {
        val kingdom = js("{}").unsafeCast<KingdomData>()
        val amiri = RawCharacter("Amiri", "uuid-a").apply { expeditionStatus = "onExpedition" }
        kingdom.companions = arrayOf(amiri)
        kingdom.companionExpeditions = arrayOf(
            js("{ id: 'e1', title: 'Expedition', companionIds: ['uuid-a'], status: 'inProgress', daysRemaining: 3, totalDays: 7, dc: 15, tier: 'standard' }")
        )
        assertTrue(kingdom.companionHasActiveExpedition(0))
    }

    @Test
    fun `companionHasActiveExpedition returns false when companion is available`() {
        val kingdom = js("{}").unsafeCast<KingdomData>()
        val amiri = RawCharacter("Amiri", "uuid-a").apply { expeditionStatus = "available" }
        kingdom.companions = arrayOf(amiri)
        assertFalse(kingdom.companionHasActiveExpedition(0))
    }

    @Test
    fun `companionHasActiveExpedition returns false when the expedition is resolved`() {
        val kingdom = js("{}").unsafeCast<KingdomData>()
        val amiri = RawCharacter("Amiri", "uuid-a")
        kingdom.companions = arrayOf(amiri)
        // The companion HAS an expedition, but it is resolved — exercises the status
        // filter (not merely an empty array), so deletion is no longer blocked.
        kingdom.companionExpeditions = arrayOf(
            js("{ id: 'e1', title: 'Expedition', companionIds: ['uuid-a'], status: 'resolved', daysRemaining: 0, totalDays: 7, dc: 15, tier: 'standard' }")
        )
        assertFalse(kingdom.companionHasActiveExpedition(0))
    }
}

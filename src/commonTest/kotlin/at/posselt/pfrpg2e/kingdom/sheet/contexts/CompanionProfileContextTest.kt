package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompanionProfileContextTest {

    private fun quest(
        id: String,
        status: String,
        visible: Boolean,
        hook: String?,
        companionId: String = "Amiri",
    ): CompanionPersonalQuest =
        js("{ id: id, title: 'T', description: 'D', companionId: companionId, status: status, questHook: hook, turnsRemaining: null, visibleToPlayers: visible, influenceReward: 0 }")
            .unsafeCast<CompanionPersonalQuest>()

    @Test
    fun `GM context counts active quests, computes percent and discovery selection`() {
        val companion = RawCharacter("Amiri").apply {
            influence = 6
            discoveryStatus = "trusted"
            personalQuestIds = arrayOf("q1", "q2")
        }
        val quests = listOf(
            quest("q1", "active", visible = false, hook = "secret hook"),
            quest("q2", "completed", visible = true, hook = null),
        )
        val ctx = buildCompanionProfileContext("form", companion, quests, isGM = true)

        assertEquals(2, ctx.personalQuests.size)
        assertEquals(1, ctx.activeQuestCount)
        assertEquals(50, ctx.influencePercent) // 6 / 12
        assertEquals(12, ctx.maxInfluence)
        assertEquals("trusted", ctx.discoveryStatus)
        assertEquals(5, ctx.discoveryStages.size)
        assertTrue(ctx.discoveryStages.first { it.value == "trusted" }.selected)
        assertEquals("secret hook", ctx.personalQuests.first { it.id == "q1" }.questHook)
    }

    @Test
    fun `player view hides GM-only quests and strips quest hooks`() {
        val companion = RawCharacter("Amiri")
        val quests = listOf(
            quest("q1", "active", visible = false, hook = "secret"),
            quest("q2", "active", visible = true, hook = "still-hidden-from-players"),
        )
        val ctx = buildCompanionProfileContext("form", companion, quests, isGM = false)

        assertEquals(1, ctx.personalQuests.size)
        assertEquals("q2", ctx.personalQuests[0].id)
        assertNull(ctx.personalQuests[0].questHook)
    }

    @Test
    fun `out-of-range influence is clamped into the bar`() {
        val companion = RawCharacter("Amiri").apply { influence = 99 }
        val ctx = buildCompanionProfileContext("form", companion, emptyList(), isGM = true)
        assertEquals(12, ctx.influence)
        assertEquals(100, ctx.influencePercent)
    }
}

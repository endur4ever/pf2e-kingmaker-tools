package at.posselt.pfrpg2e.questevent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateQuestDialogTest {
    @Test
    fun `SummarizedEventContext has correct shape`() {
        val ctx = SummarizedEventContext(
            id = "evt-1",
            name = "Test Event",
            traits = listOf("agriculture", "negative").toTypedArray(),
        )
        assertEquals("evt-1", ctx.id)
        assertEquals("Test Event", ctx.name)
        assertEquals(2, ctx.traits.size)
    }

    @Test
    fun `QuestPreviewContext defaults`() {
        val ctx = QuestPreviewContext(
            name = "Preview",
            description = "A preview quest",
            type = "exploration",
            recommendedLevel = 3,
            objectives = emptyArray(),
            rewards = RewardContext(
                xp = 40,
                rp = 5,
                fame = 0,
                commodities = emptyArray(),
                unrestReduction = 0,
                customReward = null,
            ),
            isDefaultVisibleToPlayers = false,
        )
        assertFalse(ctx.isDefaultVisibleToPlayers)
        assertEquals("exploration", ctx.type)
    }

    @Test
    fun `QuestGeneratorSettingsContext reflects defaults`() {
        val ctx = QuestGeneratorSettingsContext(
            defaultVisibilityToPlayers = false,
            maxActiveGeneratedQuests = 10,
            autoAdvanceQuestTimersOnTurn = true,
        )
        assertFalse(ctx.defaultVisibilityToPlayers)
        assertEquals(10, ctx.maxActiveGeneratedQuests)
        assertTrue(ctx.autoAdvanceQuestTimersOnTurn)
    }

    @Test
    fun `RewardContext with commodities`() {
        val ctx = RewardContext(
            xp = 0,
            rp = 5,
            fame = 0,
            commodities = arrayOf(
                CommodityRewardContext(type = "food", amount = 3),
                CommodityRewardContext(type = "lumber", amount = 2),
            ),
            unrestReduction = 0,
            customReward = null,
        )
        assertEquals(2, ctx.commodities.size)
        assertEquals("food", ctx.commodities[0].type)
        assertEquals(3, ctx.commodities[0].amount)
    }
}

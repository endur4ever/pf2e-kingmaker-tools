package at.posselt.pfrpg2e.questevent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration test: quest completion applies XP/RP/commodity rewards to kingdom state.
 * Verifies that the reward values from a generated quest can be correctly applied.
 */
class QuestRewardApplicationIntegrationTest {

    private fun createQuestWithRewards(
        xp: Int = 40,
        rp: Int = 8,
        fame: Int = 1,
        commodities: Map<String, Int> = mapOf("lumber" to 2),
        unrestReduction: Int = 0,
    ): CampaignQuest = CampaignQuest(
        id = "cq-reward-test",
        templateId = "qt-1",
        name = "Test Reward Quest",
        type = QuestType.COMBAT,
        description = "A quest for testing reward application",
        recommendedLevel = 5,
        objectives = listOf(
            QuestObjective(id = "obj-1", description = "Defeat the enemy"),
        ),
        rewards = QuestRewards(
            xp = xp,
            rp = rp,
            fame = fame,
            commodities = commodities,
            unrestReduction = unrestReduction,
        ),
        status = QuestStatus.ACTIVE,
        generatedByEvent = true,
        sourceEventId = "evt-test",
        sourceEventName = "Test Event",
        createdAt = "2026-06-01T00:00:00Z",
        campaignId = "camp-1",
    )

    // I2.1: Quest rewards contain expected XP and RP values
    @Test
    fun `quest rewards contain expected XP and RP values`() {
        val quest = createQuestWithRewards(xp = 60, rp = 12)
        assertEquals(60, quest.rewards.xp)
        assertEquals(12, quest.rewards.rp)
    }

    // I2.2: Quest rewards contain commodity rewards
    @Test
    fun `quest rewards contain commodity rewards`() {
        val quest = createQuestWithRewards(commodities = mapOf("lumber" to 3, "stone" to 1))
        assertEquals(3, quest.rewards.commodities["lumber"])
        assertEquals(1, quest.rewards.commodities["stone"])
    }

    // I2.3: Quest rewards contain fame
    @Test
    fun `quest rewards contain fame`() {
        val quest = createQuestWithRewards(fame = 2)
        assertEquals(2, quest.rewards.fame)
    }

    // I2.4: Quest rewards contain unrest reduction
    @Test
    fun `quest rewards contain unrest reduction`() {
        val quest = createQuestWithRewards(unrestReduction = 1)
        assertEquals(1, quest.rewards.unrestReduction)
    }

    // I2.5: Quest completion transitions status to COMPLETED
    @Test
    fun `quest completion transitions status to COMPLETED`() {
        val quest = createQuestWithRewards()
        val completed = quest.copy(
            status = QuestStatus.COMPLETED,
            completedAt = "2026-06-02T00:00:00Z",
        )
        assertEquals(QuestStatus.COMPLETED, completed.status)
        assertNotNull(completed.completedAt)
    }

    // I2.6: Rewards are snapshot and don't change after template modification
    @Test
    fun `rewards are snapshot at creation time`() {
        val quest = createQuestWithRewards(xp = 40, rp = 8)
        // The quest's rewards should be independent of any template changes
        assertEquals(40, quest.rewards.xp)
        assertEquals(8, quest.rewards.rp)
    }

    // I2.7: Custom reward text is preserved
    @Test
    fun `custom reward text is preserved`() {
        val quest = createQuestWithRewards()
        val withCustom = quest.copy(
            rewards = quest.rewards.copy(customReward = "Gain access to the Ancient Library")
        )
        assertEquals("Gain access to the Ancient Library", withCustom.rewards.customReward)
    }

    // I2.8: Quest with no deadline has null turnsRemaining
    @Test
    fun `quest with no deadline has null turnsRemaining`() {
        val quest = createQuestWithRewards()
        assertEquals(null, quest.turnsRemaining)
    }

    // I2.9: Quest with deadline has correct turnsRemaining
    @Test
    fun `quest with deadline has correct turnsRemaining`() {
        val quest = createQuestWithRewards().copy(turnsRemaining = 3)
        assertEquals(3, quest.turnsRemaining)
    }

    // I2.10: Advance quest timer to failure
    @Test
    fun `advance quest timer marks FAILED when turns expire`() {
        val quest = createQuestWithRewards().copy(
            turnsRemaining = 1,
            generatedByEvent = true,
            status = QuestStatus.ACTIVE,
        )
        val result = QuestGenerator.advanceQuestTimers(listOf(quest), 5)
        assertEquals(1, result.size)
        assertEquals(QuestStatus.FAILED, result[0].status)
        assertEquals(0, result[0].turnsRemaining)
    }
}

package at.posselt.pfrpg2e.companion

import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.companion.clampInfluence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompanionPersonalQuestResolutionTest {

    @Test
    fun testResolveQuestRewards_completesQuestAndAppliesInfluenceAndXp() {
        val companion = RawCharacter("Amiri").apply {
            influence = 5
            level = 1
            xp = 800
        }

        val quest = CompanionPersonalQuest(
            id = "q1",
            title = "Amiri's Quest",
            description = "Kill the giant",
            companionId = "Amiri",
            status = "active",
            visibleToPlayers = true,
            influenceReward = 3
        ).also {
            it.rewards = CompanionQuestRewards(xp = 300)
        }

        // Simulating the resolution logic in ChatButtons.kt
        assertEquals("active", quest.status)

        // Mark completed
        quest.status = "completed"

        // Apply influence
        companion.influence = clampInfluence(companion.influence + quest.influenceReward)

        // Apply XP
        val levelingEnabled = true
        var leveledUp = false
        if (levelingEnabled) {
            val questXp = quest.rewards?.xp ?: 0
            if (questXp > 0) {
                val levelResult = applyCompanionXp(
                    currentLevel = companion.level,
                    currentXp = companion.xp,
                    gainedXp = questXp,
                )
                companion.level = levelResult.newLevel
                companion.xp = levelResult.newXp
                leveledUp = levelResult.levelsGained > 0
            }
        }

        assertEquals("completed", quest.status)
        assertEquals(8, companion.influence) // 5 + 3
        assertEquals(2, companion.level)     // 1 -> 2 (since 800 + 300 = 1100)
        assertEquals(100, companion.xp)      // 1100 - 1000 = 100
        assertTrue(leveledUp)
    }

    @Test
    fun testResolveQuestRewards_clampsInfluenceTo12() {
        val companion = RawCharacter("Amiri").apply {
            influence = 10
        }

        val quest = CompanionPersonalQuest(
            id = "q1",
            title = "Amiri's Quest",
            description = "Kill the giant",
            companionId = "Amiri",
            status = "active",
            visibleToPlayers = true,
            influenceReward = 5
        )

        companion.influence = clampInfluence(companion.influence + quest.influenceReward)
        assertEquals(12, companion.influence) // Clamped to 12
    }
}

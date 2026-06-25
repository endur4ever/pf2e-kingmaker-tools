package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `level and xp are passed through with percent`() {
        val companion = RawCharacter("Amiri").apply {
            level = 3
            xp = 1500
        }
        val ctx = buildCompanionProfileContext("form", companion, emptyList(), isGM = true)
        assertEquals(3, ctx.level)
        assertEquals(1500, ctx.xp)
        assertEquals(50, ctx.xpPercent) // 1500 / 3000
    }

    @Test
    fun `xp percent is zero when level is zero`() {
        val companion = RawCharacter("Amiri").apply {
            level = 0
            xp = 0
        }
        val ctx = buildCompanionProfileContext("form", companion, emptyList(), isGM = true)
        assertEquals(0, ctx.xpPercent)
    }

    @Test
    fun `expedition status and injury days are exposed`() {
        val companion = RawCharacter("Amiri").apply {
            expeditionStatus = "onExpedition"
            injuryDaysRemaining = 3
        }
        val ctx = buildCompanionProfileContext("form", companion, emptyList(), isGM = true)
        assertEquals("onExpedition", ctx.expeditionStatus)
        assertEquals("kingdom.companion.expeditionStatus.onExpedition", ctx.expeditionStatusLabel) // identity localize
        assertEquals(3, ctx.injuryDaysRemaining)
    }

    @Test
    fun `GM can send on expedition when available and not injured`() {
        val companion = RawCharacter("Amiri").apply {
            expeditionStatus = "available"
            injuryDaysRemaining = null
        }
        val ctx = buildCompanionProfileContext("form", companion, emptyList(), isGM = true)
        assertTrue(ctx.canSendOnExpedition)
    }

    @Test
    fun `player cannot send on expedition`() {
        val companion = RawCharacter("Amiri").apply {
            expeditionStatus = "available"
            injuryDaysRemaining = null
        }
        val ctx = buildCompanionProfileContext("form", companion, emptyList(), isGM = false)
        assertFalse(ctx.canSendOnExpedition)
    }

    @Test
    fun `cannot send on expedition when injured`() {
        val companion = RawCharacter("Amiri").apply {
            expeditionStatus = "available"
            injuryDaysRemaining = 2
        }
        val ctx = buildCompanionProfileContext("form", companion, emptyList(), isGM = true)
        assertFalse(ctx.canSendOnExpedition)
    }

    @Test
    fun `cannot send on expedition when already on expedition`() {
        val companion = RawCharacter("Amiri").apply {
            expeditionStatus = "onExpedition"
            injuryDaysRemaining = null
        }
        val ctx = buildCompanionProfileContext("form", companion, emptyList(), isGM = true)
        assertFalse(ctx.canSendOnExpedition)
    }

    @Test
    fun `current and past expeditions are partitioned correctly`() {
        val companion = RawCharacter("Amiri").apply {
            actorUuid = "uuid-1"
        }
        val expeditions = listOf(
            js("{ id: 'exp1', title: 'Explore Ruins', companionIds: ['uuid-1'], status: 'inProgress', daysRemaining: 5, totalDays: 10, dc: 15, tier: 'standard' }").unsafeCast<RawCompanionExpedition>(),
            js("{ id: 'exp2', title: 'Completed Quest', companionIds: ['uuid-1'], status: 'resolved', daysRemaining: 0, totalDays: 7, dc: 12, tier: 'routine' }").unsafeCast<RawCompanionExpedition>(),
        )
        val ctx = buildCompanionProfileContext("form", companion, emptyList<CompanionPersonalQuest>(), isGM = true, expeditions = expeditions)
        assertEquals(1, ctx.currentExpeditions.size)
        assertEquals("exp1", ctx.currentExpeditions[0].id)
        assertEquals(50, ctx.currentExpeditions[0].progressPercent)
        assertEquals(1, ctx.pastExpeditions.size)
        assertEquals("exp2", ctx.pastExpeditions[0].id)
    }

    @Test
    fun `expeditions for other companions are excluded`() {
        val companion = RawCharacter("Amiri").apply {
            actorUuid = "uuid-1"
        }
        val expeditions = listOf(
            js("{ id: 'exp1', title: 'Other Expedition', companionIds: ['uuid-2'], status: 'inProgress', daysRemaining: 3, totalDays: 5, dc: 18, tier: 'perilous' }").unsafeCast<RawCompanionExpedition>(),
        )
        val ctx = buildCompanionProfileContext("form", companion, emptyList<CompanionPersonalQuest>(), isGM = true, expeditions = expeditions)
        assertEquals(0, ctx.currentExpeditions.size)
        assertEquals(0, ctx.pastExpeditions.size)
    }

    @Test
    fun `xpForLevel uses level times 1000`() {
        assertEquals(1000, xpForLevel(1))
        assertEquals(3000, xpForLevel(3))
        assertEquals(10000, xpForLevel(10))
    }
}

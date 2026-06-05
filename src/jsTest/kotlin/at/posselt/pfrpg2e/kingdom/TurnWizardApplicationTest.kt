package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.dialogs.TurnWizardApplication
import at.posselt.pfrpg2e.kingdom.dialogs.toDisplayString
import at.posselt.pfrpg2e.kingdom.data.RawResources
import at.posselt.pfrpg2e.kingdom.data.RawFame
import at.posselt.pfrpg2e.kingdom.data.RawConsumption
import at.posselt.pfrpg2e.kingdom.data.RawCurrentCommodities
import at.posselt.pfrpg2e.kingdom.data.RawRuin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnWizardApplicationTest {

    private fun createTestKingdom(
        unrest: Int = 0,
        name: String = "Test Kingdom",
        leadershipPerformed: Int = 0,
        increaseLeadershipActivities: Boolean = false,
    ): KingdomData {
        val settings = js("{}").unsafeCast<KingdomSettings>().apply {
            this.asDynamic().increaseLeadershipActivities = increaseLeadershipActivities
            this.asDynamic().automateStats = false
            this.asDynamic().ruinThreshold = 10
        }
        val rp = js("{}").unsafeCast<RawResources>().apply {
            this.asDynamic().now = 10
            this.asDynamic().next = 12
        }
        val rd = js("{}").unsafeCast<RawResources>().apply {
            this.asDynamic().now = 3
            this.asDynamic().next = 4
        }
        val fame = js("{}").unsafeCast<RawFame>().apply {
            this.asDynamic().now = 2
            this.asDynamic().next = 3
        }
        val consumption = js("{}").unsafeCast<RawConsumption>().apply {
            this.asDynamic().now = 1
        }
        val commodities = js("{}").unsafeCast<RawCurrentCommodities>().apply {
            val nowVal = js("{}")
            nowVal.food = 5
            nowVal.lumber = 4
            nowVal.luxuries = 3
            nowVal.ore = 2
            nowVal.stone = 1
            this.asDynamic().now = nowVal
        }
        val ruin = js("{}").unsafeCast<RawRuin>().apply {
            val corruptionVal = js("{}")
            corruptionVal.value = 1
            corruptionVal.penalty = 0
            corruptionVal.threshold = 10
            this.asDynamic().corruption = corruptionVal
            this.asDynamic().crime = corruptionVal
            this.asDynamic().decay = corruptionVal
            this.asDynamic().strife = corruptionVal
        }
        
        return js("{}").unsafeCast<KingdomData>().apply {
            this.asDynamic().name = name
            this.asDynamic().unrest = unrest
            this.asDynamic().settings = settings
            this.asDynamic().resourcePoints = rp
            this.asDynamic().resourceDice = rd
            this.asDynamic().fame = fame
            this.asDynamic().consumption = consumption
            this.asDynamic().commodities = commodities
            this.asDynamic().ruin = ruin
            this.asDynamic().modifiers = emptyArray<dynamic>()
            this.asDynamic().settlements = emptyArray<dynamic>()
        }
    }

    @Test
    fun testAllChecklistItemsPresent() {
        val kingdom = createTestKingdom()
        val context = TurnWizardApplication.buildContext(kingdom)
        val ids = context.checklist.map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf(
            "gain-fame", "adjust-unrest", "collect-resources",
            "pay-consumption", "check-events"
        )))
    }

    @Test
    fun testAdjustUnrestHighlightWhenUnrestGreaterThanZero() {
        val kingdomHighlight = createTestKingdom(unrest = 2)
        val contextHighlight = TurnWizardApplication.buildContext(kingdomHighlight)
        val adjustUnrestItem = contextHighlight.checklist.find { it.id == "adjust-unrest" }!!
        assertTrue(adjustUnrestItem.highlight)

        val kingdomNoHighlight = createTestKingdom(unrest = 0)
        val contextNoHighlight = TurnWizardApplication.buildContext(kingdomNoHighlight)
        val adjustUnrestItemNo = contextNoHighlight.checklist.find { it.id == "adjust-unrest" }!!
        assertFalse(adjustUnrestItemNo.highlight)
    }

    @Test
    fun testGainFameNotHighlightedInNormalState() {
        val kingdom = createTestKingdom()
        val context = TurnWizardApplication.buildContext(kingdom)
        val gainFameItem = context.checklist.find { it.id == "gain-fame" }!!
        assertFalse(gainFameItem.highlight)
    }

    @Test
    fun testKingdomStateSummaryComputesCorrectly() {
        val kingdom = createTestKingdom()
        val context = TurnWizardApplication.buildContext(kingdom)
        val state = context.kingdomState
        assertEquals(10, state.rpNow)
        assertEquals(12, state.rpNext)
        assertEquals(5, state.foodNow)
        assertEquals(1, state.consumption)
        assertEquals(0, state.unrest)
    }

    @Test
    fun testTickChangeToDisplayStringFormatsRpChange() {
        val change = TickChange("resourcePoints", "now", 10, 15)
        val display = change.toDisplayString()
        assertTrue(display.contains("10"))
        assertTrue(display.contains("15"))
    }

    @Test
    fun testTickChangeToDisplayStringFormatsFameChange() {
        val change = TickChange("fame", "now", 2, 4)
        val display = change.toDisplayString()
        assertTrue(display.contains("2"))
        assertTrue(display.contains("4"))
    }

    @Test
    fun testWizardPreviewShowsTickChangeListAfterPreviewButton() {
        val kingdom = createTestKingdom()
        val context = TurnWizardApplication.buildContextWithPreview(kingdom)
        assertTrue(context.previewChanges.isNotEmpty())
        assertTrue(context.showPreview)
    }

    @Test
    fun testWizardCanCommit() {
        val kingdom = createTestKingdom()
        val context = TurnWizardApplication.buildContext(kingdom)
        assertTrue(context.canCommit)
    }
}

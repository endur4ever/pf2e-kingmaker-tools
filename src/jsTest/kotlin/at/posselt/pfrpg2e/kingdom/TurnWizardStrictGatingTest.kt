package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.KingdomPhase
import at.posselt.pfrpg2e.kingdom.dialogs.TurnWizardApplication
import at.posselt.pfrpg2e.kingdom.sheet.contexts.getActivePhaseForGating
import at.posselt.pfrpg2e.kingdom.sheet.contexts.isActivityPhaseGated
import at.posselt.pfrpg2e.kingdom.data.RawResources
import at.posselt.pfrpg2e.kingdom.data.RawFame
import at.posselt.pfrpg2e.kingdom.data.RawConsumption
import at.posselt.pfrpg2e.kingdom.data.RawCurrentCommodities
import at.posselt.pfrpg2e.kingdom.data.RawRuin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TurnWizardStrictGatingTest {

    private fun createTestKingdom(strictEnabled: Boolean = false): KingdomData {
        val settings = js("{}").unsafeCast<KingdomSettings>().apply {
            this.asDynamic().increaseLeadershipActivities = false
            this.asDynamic().automateStats = false
            this.asDynamic().ruinThreshold = 10
            this.asDynamic().enableStrictPhaseGating = strictEnabled
        }
        val rp = js("{}").unsafeCast<RawResources>().apply {
            this.asDynamic().now = 10
            this.asDynamic().next = 12
        }
        val rd = js("{}").unsafeCast<RawResources>().apply {
            this.asDynamic().now = 5
            this.asDynamic().next = 6
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
            this.asDynamic().name = "Test Kingdom"
            this.asDynamic().unrest = 0
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
    fun testGetActivePhaseForGating() {
        // Empty checked items -> UPKEEP
        assertEquals(KingdomPhase.UPKEEP, getActivePhaseForGating(emptySet()))

        // Partial upkeep checked -> UPKEEP
        assertEquals(KingdomPhase.UPKEEP, getActivePhaseForGating(setOf("gain-fame", "adjust-unrest")))

        // Full upkeep checked -> LEADERSHIP
        val upkeepDone = setOf("gain-fame", "adjust-unrest", "collect-resources", "pay-consumption")
        assertEquals(KingdomPhase.LEADERSHIP, getActivePhaseForGating(upkeepDone))

        // Upkeep + Leadership checked -> CIVIC
        assertEquals(KingdomPhase.CIVIC, getActivePhaseForGating(upkeepDone + "leadership-phase"))

        // Upkeep + Leadership + Civic checked -> REGION
        assertEquals(KingdomPhase.REGION, getActivePhaseForGating(upkeepDone + "leadership-phase" + "civic-phase"))

        // Upkeep + Leadership + Civic + Region checked -> COMMERCE
        assertEquals(KingdomPhase.COMMERCE, getActivePhaseForGating(upkeepDone + "leadership-phase" + "civic-phase" + "region-phase"))

        // Upkeep + Leadership + Civic + Region + Commerce checked -> ARMY
        assertEquals(KingdomPhase.ARMY, getActivePhaseForGating(upkeepDone + "leadership-phase" + "civic-phase" + "region-phase" + "commerce-phase"))

        // Upkeep + Leadership + Civic + Region + Commerce + Army checked -> EVENT
        assertEquals(KingdomPhase.EVENT, getActivePhaseForGating(upkeepDone + "leadership-phase" + "civic-phase" + "region-phase" + "commerce-phase" + "army-phase"))

        // Everything checked -> null
        assertEquals(null, getActivePhaseForGating(upkeepDone + "leadership-phase" + "civic-phase" + "region-phase" + "commerce-phase" + "army-phase" + "check-events"))
    }

    @Test
    fun testStrictChecklistAppendsPhaseBoundaryCheckboxes() {
        val normalKingdom = createTestKingdom(strictEnabled = false)
        val normalContext = TurnWizardApplication.buildContext(normalKingdom)
        val normalIds = normalContext.checklist.map { it.id }
        assertFalse(normalIds.contains("leadership-phase"))
        assertFalse(normalIds.contains("civic-phase"))

        val strictKingdom = createTestKingdom(strictEnabled = true)
        val strictContext = TurnWizardApplication.buildContext(strictKingdom)
        val strictIds = strictContext.checklist.map { it.id }
        assertTrue(strictIds.contains("leadership-phase"))
        assertTrue(strictIds.contains("civic-phase"))
        assertTrue(strictIds.contains("region-phase"))
        assertTrue(strictIds.contains("commerce-phase"))
        assertTrue(strictIds.contains("army-phase"))
    }

    @Test
    fun testStrictChecklistDisablesCheckboxesOutOfSequence() {
        val kingdom = createTestKingdom(strictEnabled = true)
        
        // With nothing checked, first item is enabled, all subsequent are disabled
        val contextEmpty = TurnWizardApplication.buildContext(kingdom, checkedItems = emptySet())
        assertFalse(contextEmpty.checklist[0].disabled == true)
        assertTrue(contextEmpty.checklist[1].disabled == true)
        assertTrue(contextEmpty.checklist[2].disabled == true)

        // With first item checked, second item becomes enabled
        val contextFirstChecked = TurnWizardApplication.buildContext(kingdom, checkedItems = setOf("gain-fame"))
        assertFalse(contextFirstChecked.checklist[0].disabled == true)
        assertFalse(contextFirstChecked.checklist[1].disabled == true)
        assertTrue(contextFirstChecked.checklist[2].disabled == true)
    }

    @Test
    fun testApplyChecklistToggleNonStrict() {
        // Non-strict: plain add when absent.
        assertEquals(
            listOf("gain-fame"),
            TurnWizardApplication.applyChecklistToggle(emptyList(), "gain-fame", isStrict = false),
        )
        // Non-strict: plain remove when present, no cascade even if later items are checked.
        assertEquals(
            listOf("adjust-unrest", "check-events"),
            TurnWizardApplication.applyChecklistToggle(
                listOf("gain-fame", "adjust-unrest", "check-events"),
                "gain-fame",
                isStrict = false,
            ),
        )
    }

    @Test
    fun testApplyChecklistToggleStrictCheckGuard() {
        // Cannot check the first item's successor until the first is checked.
        assertEquals(
            emptyList(),
            TurnWizardApplication.applyChecklistToggle(emptyList(), "adjust-unrest", isStrict = true),
            "checking out of order is a no-op",
        )
        // First item can always be checked.
        assertEquals(
            listOf("gain-fame"),
            TurnWizardApplication.applyChecklistToggle(emptyList(), "gain-fame", isStrict = true),
        )
        // Next item unlocks once all predecessors are checked.
        val upkeep = listOf("gain-fame", "adjust-unrest", "collect-resources", "pay-consumption")
        assertEquals(
            upkeep + "leadership-phase",
            TurnWizardApplication.applyChecklistToggle(upkeep, "leadership-phase", isStrict = true),
        )
    }

    @Test
    fun testApplyChecklistToggleStrictUncheckCascade() {
        val all = listOf(
            "gain-fame", "adjust-unrest", "collect-resources", "pay-consumption",
            "leadership-phase", "civic-phase",
        )
        // Unchecking a middle item clears it and everything downstream, keeps upstream.
        assertEquals(
            listOf("gain-fame", "adjust-unrest", "collect-resources"),
            TurnWizardApplication.applyChecklistToggle(all, "pay-consumption", isStrict = true),
        )
        // Unchecking the very first item clears the whole list.
        assertEquals(
            emptyList(),
            TurnWizardApplication.applyChecklistToggle(all, "gain-fame", isStrict = true),
        )
        // Unchecking the last checked item only removes that item.
        assertEquals(
            all.dropLast(1),
            TurnWizardApplication.applyChecklistToggle(all, "civic-phase", isStrict = true),
        )
    }

    @Test
    fun testApplyChecklistToggleUnknownIdFallsBack() {
        // Ids outside the strict sequence behave like plain add/remove even under strict gating.
        assertEquals(
            listOf("custom-step"),
            TurnWizardApplication.applyChecklistToggle(emptyList(), "custom-step", isStrict = true),
        )
        assertEquals(
            listOf("gain-fame"),
            TurnWizardApplication.applyChecklistToggle(
                listOf("gain-fame", "custom-step"),
                "custom-step",
                isStrict = true,
            ),
        )
    }

    @Test
    fun testIsActivityPhaseGated() {
        val upkeepDone = setOf("gain-fame", "adjust-unrest", "collect-resources", "pay-consumption")

        // Disabled entirely when strict gating is off.
        assertFalse(isActivityPhaseGated("leadership", emptySet(), isStrict = false))

        // Active phase is UPKEEP with nothing checked: non-upkeep activities are gated, upkeep is not.
        assertTrue(isActivityPhaseGated("leadership", emptySet(), isStrict = true))
        assertFalse(isActivityPhaseGated("upkeep", emptySet(), isStrict = true))

        // Active phase becomes LEADERSHIP once upkeep is done.
        assertFalse(isActivityPhaseGated("leadership", upkeepDone, isStrict = true))
        assertTrue(isActivityPhaseGated("civic", upkeepDone, isStrict = true))

        // Once the full checklist is complete nothing is gated.
        val everything = upkeepDone + setOf(
            "leadership-phase", "civic-phase", "region-phase", "commerce-phase", "army-phase", "check-events",
        )
        assertFalse(isActivityPhaseGated("leadership", everything, isStrict = true))
        assertFalse(isActivityPhaseGated("army", everything, isStrict = true))
    }
}

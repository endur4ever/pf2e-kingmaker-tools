package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoSucceedInClaimedHexesTest {

    @Test
    fun `auto-succeeds when toggle on, hex claimed, and activity is prepare-campsite`() {
        assertTrue(autoSucceedInClaimedHexes(true, true, prepareCampsiteId))
    }

    @Test
    fun `auto-succeeds when toggle on, hex claimed, and activity is cook-meal`() {
        assertTrue(autoSucceedInClaimedHexes(true, true, cookMealId))
    }

    @Test
    fun `does not auto-succeed when toggle is off`() {
        assertFalse(autoSucceedInClaimedHexes(false, true, prepareCampsiteId))
        assertFalse(autoSucceedInClaimedHexes(false, true, cookMealId))
    }

    @Test
    fun `does not auto-succeed when hex is not claimed`() {
        assertFalse(autoSucceedInClaimedHexes(true, false, prepareCampsiteId))
        assertFalse(autoSucceedInClaimedHexes(true, false, cookMealId))
    }

    @Test
    fun `does not auto-succeed for other activities`() {
        assertFalse(autoSucceedInClaimedHexes(true, true, "hunt-and-gather"))
        assertFalse(autoSucceedInClaimedHexes(true, true, "discover-special-meal"))
        assertFalse(autoSucceedInClaimedHexes(true, true, "enhance-weapons"))
        assertFalse(autoSucceedInClaimedHexes(true, true, "unknown-activity"))
    }

    @Test
    fun `does not auto-succeed when both toggle off and hex not claimed`() {
        assertFalse(autoSucceedInClaimedHexes(false, false, prepareCampsiteId))
        assertFalse(autoSucceedInClaimedHexes(false, false, cookMealId))
    }

    @Test
    fun `does not auto-succeed when toggle on but hex not claimed for other activities`() {
        assertFalse(autoSucceedInClaimedHexes(true, false, "hunt-and-gather"))
    }
}
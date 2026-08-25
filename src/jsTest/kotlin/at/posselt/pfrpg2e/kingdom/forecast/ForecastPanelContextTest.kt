package at.posselt.pfrpg2e.kingdom.forecast

import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildForecastPanelContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The panel context's nullability IS the privacy gate: null in, null out, and the panel vanishes
 * from the data rather than merely from the template.
 */
class ForecastPanelContextTest {
    @Test
    fun aNullForecastYieldsANullPanel() {
        assertNull(buildForecastPanelContext(null))
    }

    @Test
    fun beatsSplitIntoDatedAndEndTurnBlocks() {
        val result = ForecastResult(
            beats = listOf(
                ForecastBeat(dayOffset = 3, kind = ForecastKind.ARRIVAL, labelKey = FORECAST_KEY_COMPANION_ARRIVES, target = "roster"),
                ForecastBeat(dayOffset = null, kind = ForecastKind.RISK, labelKey = FORECAST_KEY_CARAVAN_RISK, dc = 13, safePercent = 40, target = "turn"),
            ),
            horizonDays = 7,
        )
        val panel = buildForecastPanelContext(result)!!
        assertEquals(1, panel.dated.size)
        assertEquals(1, panel.endTurn.size)
        assertTrue(panel.hasBeats)
        assertEquals(7, panel.horizonDays)
        assertEquals("arrival", panel.dated[0].kindClass)
    }

    @Test
    fun aRiskBeatCarriesItsStakeLineAndOthersDoNot() {
        val result = ForecastResult(
            beats = listOf(
                ForecastBeat(dayOffset = null, kind = ForecastKind.RISK, labelKey = FORECAST_KEY_CARAVAN_RISK, dc = 13, safePercent = 40, target = "turn"),
                ForecastBeat(dayOffset = null, kind = ForecastKind.THREAT, labelKey = FORECAST_KEY_THREAT_ARRIVES, target = "armyPressure"),
            ),
            horizonDays = 7,
        )
        val panel = buildForecastPanelContext(result)!!
        assertTrue(panel.endTurn[0].risk != null, "the risk row carries stake and odds")
        assertNull(panel.endTurn[1].risk, "a threat row carries no odds -- it is certain")
    }

    @Test
    fun anEmptyForecastStillRendersAsAnEmptyPanelNotNull() {
        // A GM with a quiet horizon gets "nothing on the horizon", not a missing section.
        val panel = buildForecastPanelContext(ForecastResult(emptyList(), horizonDays = 7))!!
        assertEquals(false, panel.hasBeats)
    }
}

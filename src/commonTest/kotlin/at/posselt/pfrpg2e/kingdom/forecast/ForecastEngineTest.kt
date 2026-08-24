package at.posselt.pfrpg2e.kingdom.forecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the cases §9 of the session-forecast plan enumerates. */
class ForecastEngineTest {
    private fun countdown(
        remainingDays: Int,
        labelKey: String = "kingdom.forecast.caravanArrives",
        kind: ForecastKind = ForecastKind.ARRIVAL,
        target: String = "trade",
    ) = DailyCountdown(labelKey = labelKey, remainingDays = remainingDays, kind = kind, target = target)

    private fun risk(dc: Int, target: String = "trade") =
        RiskForecast(labelKey = "kingdom.forecast.caravanRisk", dc = dc, target = target)

    private fun labeled(labelKey: String, target: String = "turn") =
        LabeledBeat(labelKey = labelKey, target = target)

    @Test
    fun aCaravanTwoDaysOutAppearsInAThreeDayHorizonAndNotInAOneDayHorizon() {
        // The edge the whole panel is judged by: both sides of the horizon, same caravan.
        val two = countdown(remainingDays = 2)
        val wide = forecast(ForecastInputs(horizonDays = 3, countdowns = listOf(two)))
        assertEquals(listOf(2), wide.beats.map { it.dayOffset })
        val narrow = forecast(ForecastInputs(horizonDays = 1, countdowns = listOf(two)))
        assertTrue(narrow.beats.isEmpty())
    }

    @Test
    fun aCountdownAtExactlyTheHorizonAppearsAndOnePastItDoesNot() {
        val onEdge = forecast(ForecastInputs(horizonDays = 7, countdowns = listOf(countdown(remainingDays = 7))))
        assertEquals(listOf(7), onEdge.beats.map { it.dayOffset })
        val pastEdge = forecast(ForecastInputs(horizonDays = 7, countdowns = listOf(countdown(remainingDays = 8))))
        assertTrue(pastEdge.beats.isEmpty())
    }

    @Test
    fun aCountdownDueTodayAppearsAndANegativeOneDoesNot() {
        // Day 0 is "arrives today" -- still news; a negative remainder is stale input, not a beat.
        val today = forecast(ForecastInputs(horizonDays = 3, countdowns = listOf(countdown(remainingDays = 0))))
        assertEquals(listOf(0), today.beats.map { it.dayOffset })
        val stale = forecast(ForecastInputs(horizonDays = 3, countdowns = listOf(countdown(remainingDays = -1))))
        assertTrue(stale.beats.isEmpty())
    }

    @Test
    fun d20AtLeastPercentMatchesTheDieAtBothEndsAndTheMiddle() {
        assertEquals(100, d20AtLeastPercent(1))
        assertEquals(40, d20AtLeastPercent(13))
        assertEquals(0, d20AtLeastPercent(21))
    }

    @Test
    fun d20AtLeastPercentClampsOutsideTheDieInsteadOfLeavingTheScale() {
        // A DC of 0 must not read as 105% and a DC of 25 must not read as -20%.
        assertEquals(100, d20AtLeastPercent(0))
        assertEquals(0, d20AtLeastPercent(25))
    }

    @Test
    fun aRiskBeatCarriesTheStakeAndTheOddsNeverAnOutcome() {
        val beat = forecast(ForecastInputs(horizonDays = 7, risks = listOf(risk(dc = 13)))).beats.single()
        assertEquals(ForecastKind.RISK, beat.kind)
        assertEquals(13, beat.dc)
        assertEquals(d20AtLeastPercent(13), beat.safePercent)
        assertEquals(40, beat.safePercent)
        // Risks are decided at End Turn, never on a forecast day.
        assertNull(beat.dayOffset)
    }

    @Test
    fun aDeterministicBeatCarriesNoDcAndNoOdds() {
        // Odds belong to stakes only: a dated arrival is a fact, not a gamble.
        val beat = forecast(ForecastInputs(horizonDays = 3, countdowns = listOf(countdown(remainingDays = 2)))).beats.single()
        assertNull(beat.dc)
        assertNull(beat.safePercent)
    }

    @Test
    fun aResourceAlertMapsToExactlyOneResourceBeat() {
        val out = forecast(
            ForecastInputs(
                horizonDays = 7,
                endTurn = EndTurnBeats(resourceAlerts = listOf(labeled("kingdom.forecast.consumptionNegative"))),
            ),
        )
        assertEquals(1, out.beats.size)
        assertEquals(ForecastKind.RESOURCE, out.beats.single().kind)
    }

    @Test
    fun eachEndTurnListMapsToItsKind() {
        val out = forecast(
            ForecastInputs(
                horizonDays = 7,
                endTurn = EndTurnBeats(
                    clockExpirations = listOf(labeled("kingdom.forecast.clockExpires")),
                    newThreats = listOf(labeled("kingdom.forecast.threatArrives")),
                    questDeadlines = listOf(labeled("kingdom.forecast.questDeadline")),
                    resourceAlerts = listOf(labeled("kingdom.forecast.consumptionNegative")),
                ),
            ),
        )
        assertEquals(
            listOf(ForecastKind.EXPIRATION, ForecastKind.THREAT, ForecastKind.EXPIRATION, ForecastKind.RESOURCE),
            out.beats.map { it.kind },
        )
        assertTrue(out.beats.all { it.dayOffset == null })
    }

    @Test
    fun theRuinFlagBecomesExactlyOneThreatBeatWithTheConstantKeyAndOtherwiseNone() {
        val crossed = forecast(ForecastInputs(horizonDays = 7, endTurn = EndTurnBeats(ruinThresholdCrossed = true)))
        val beat = crossed.beats.single()
        assertEquals(ForecastKind.THREAT, beat.kind)
        assertEquals(RUIN_THRESHOLD_LABEL_KEY, beat.labelKey)
        val notCrossed = forecast(ForecastInputs(horizonDays = 7, endTurn = EndTurnBeats(ruinThresholdCrossed = false)))
        assertTrue(notCrossed.beats.isEmpty())
    }

    @Test
    fun datedBeatsComeFirstAscendingThenTheEndTurnBlock() {
        // Input lists the day-5 arrival before the day-2 one; output must not.
        val out = forecast(
            ForecastInputs(
                horizonDays = 7,
                countdowns = listOf(countdown(remainingDays = 5), countdown(remainingDays = 2)),
                risks = listOf(risk(dc = 10)),
                endTurn = EndTurnBeats(newThreats = listOf(labeled("kingdom.forecast.threatArrives"))),
            ),
        )
        assertEquals(listOf(2, 5, null, null), out.beats.map { it.dayOffset })
        assertEquals(
            listOf(ForecastKind.ARRIVAL, ForecastKind.ARRIVAL, ForecastKind.RISK, ForecastKind.THREAT),
            out.beats.map { it.kind },
        )
    }

    @Test
    fun beatsOnTheSameDayKeepTheirInputOrder() {
        val out = forecast(
            ForecastInputs(
                horizonDays = 7,
                countdowns = listOf(
                    countdown(remainingDays = 3, labelKey = "kingdom.forecast.caravanArrives"),
                    countdown(remainingDays = 3, labelKey = "kingdom.forecast.expeditionResolves", kind = ForecastKind.COMPLETION),
                ),
            ),
        )
        assertEquals(
            listOf("kingdom.forecast.caravanArrives", "kingdom.forecast.expeditionResolves"),
            out.beats.map { it.labelKey },
        )
    }

    @Test
    fun clampHorizonPinsBothEdgesOfTheRange() {
        assertEquals(14, clampHorizon(14))
        assertEquals(14, clampHorizon(15))
        assertEquals(1, clampHorizon(1))
        assertEquals(1, clampHorizon(0))
    }

    @Test
    fun aRequestedHorizonPastTheCapStillFiltersAtTheCap() {
        // The clamp must gate the beats, not just the reported number.
        val past = forecast(ForecastInputs(horizonDays = 90, countdowns = listOf(countdown(remainingDays = 15))))
        assertTrue(past.beats.isEmpty())
        assertEquals(MAX_FORECAST_HORIZON_DAYS, past.horizonDays)
        val inside = forecast(ForecastInputs(horizonDays = 90, countdowns = listOf(countdown(remainingDays = 14))))
        assertEquals(listOf(14), inside.beats.map { it.dayOffset })
    }

    @Test
    fun forecastingTwiceWithEqualInputsGivesEqualResults() {
        // Purity is the feature: the panel re-renders freely and consumes no randomness.
        val inputs = ForecastInputs(
            horizonDays = 7,
            countdowns = listOf(countdown(remainingDays = 2)),
            risks = listOf(risk(dc = 13)),
            endTurn = EndTurnBeats(
                newThreats = listOf(labeled("kingdom.forecast.threatArrives")),
                ruinThresholdCrossed = true,
            ),
        )
        assertEquals(forecast(inputs), forecast(inputs))
    }

    @Test
    fun emptyInputsProduceNoBeats() {
        val out = forecast(ForecastInputs(horizonDays = 7))
        assertTrue(out.beats.isEmpty())
        assertEquals(7, out.horizonDays)
    }

    @Test
    fun unknownStoredValuesMapToNullRatherThanThrowing() {
        assertNull(ForecastKind.fromValue("omen"))
        assertNull(ForecastKind.fromValue(null))
        assertEquals(ForecastKind.RISK, ForecastKind.fromValue("risk"))
    }
}

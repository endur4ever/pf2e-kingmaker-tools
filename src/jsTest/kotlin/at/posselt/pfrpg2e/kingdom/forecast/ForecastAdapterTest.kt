package at.posselt.pfrpg2e.kingdom.forecast

import at.posselt.pfrpg2e.campaign.ClockTickEvent
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.TickResult
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.migrations.runTest
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the forecast adapter's mappings and its gate-before-everything privacy rule. */
class ForecastAdapterTest {
    private fun companion(name: String, eta: Int?): RawCharacter {
        val c = unsafeJso<dynamic>()
        c.name = name
        c.eta = eta
        return c.unsafeCast<RawCharacter>()
    }

    private fun expedition(title: String, status: String, days: Int): RawCompanionExpedition {
        val e = unsafeJso<dynamic>()
        e.title = title
        e.status = status
        e.daysRemaining = days
        return e.unsafeCast<RawCompanionExpedition>()
    }

    @Test
    fun aCompanionWithNoEtaIsNotTravelling() {
        val out = forecastCountdowns(arrayOf(companion("Ekundayo", eta = null)), null)
        assertTrue(out.isEmpty())
    }

    @Test
    fun aTravellingCompanionBecomesAnArrivalCountdown() {
        val out = forecastCountdowns(arrayOf(companion("Ekundayo", eta = 3)), null)
        assertEquals(1, out.size)
        assertEquals(ForecastKind.ARRIVAL, out[0].kind)
        assertEquals(3, out[0].remainingDays)
        assertEquals("Ekundayo", out[0].labelArgs["name"])
        assertEquals(FORECAST_KEY_COMPANION_ARRIVES, out[0].labelKey)
    }

    @Test
    fun onlyInProgressExpeditionsCountDown() {
        // awaitingResolution is the GM's queue, not a schedule -- forecasting it would promise a
        // date for something that resolves whenever the GM clicks.
        val out = forecastCountdowns(
            null,
            arrayOf(
                expedition("Scout the Narlmarches", "inProgress", 4),
                expedition("Old hunt", "awaitingResolution", 0),
                expedition("Done hunt", "resolved", 0),
            ),
        )
        assertEquals(listOf("Scout the Narlmarches"), out.map { it.labelArgs["title"] })
        assertEquals(ForecastKind.COMPLETION, out[0].kind)
    }

    private fun tickResult(
        clockEvents: Array<ClockTickEvent> = emptyArray(),
        threats: Array<RawWarThreat> = emptyArray(),
        deadlines: List<String> = emptyList(),
        ruin: Boolean = false,
    ): TickResult {
        fun raw(): dynamic = unsafeJso<dynamic>()
        return TickResult(
            supernaturalSolutions = 0,
            creativeSolutions = 0,
            fame = raw().unsafeCast<at.posselt.pfrpg2e.kingdom.data.RawFame>(),
            resourcePoints = raw().unsafeCast<at.posselt.pfrpg2e.kingdom.data.RawResources>(),
            resourceDice = raw().unsafeCast<at.posselt.pfrpg2e.kingdom.data.RawResources>(),
            consumption = raw().unsafeCast<at.posselt.pfrpg2e.kingdom.data.RawConsumption>(),
            commodities = raw().unsafeCast<at.posselt.pfrpg2e.kingdom.data.RawCurrentCommodities>(),
            councilCooldowns = null,
            modifiers = emptyArray(),
            changes = emptyList(),
            clockEvents = clockEvents,
            newlyTriggeredThreats = threats,
            questDeadlineReached = deadlines,
            ruinThresholdCrossed = ruin,
        )
    }

    private fun clockEvent(type: String, label: String): ClockTickEvent {
        val e = unsafeJso<dynamic>()
        e.type = type
        e.label = label
        return e.unsafeCast<ClockTickEvent>()
    }

    @Test
    fun onlyExpiredAndTriggeredClocksBecomeBeats() {
        // ADVANCED fires for every ticking clock every turn; forwarding it would drown the panel.
        val beats = endTurnBeats(
            tickResult(
                clockEvents = arrayOf(
                    clockEvent("ADVANCED", "Stag Lord"),
                    clockEvent("EXPIRED", "Season of Bloom"),
                    clockEvent("TRIGGERED", "Brevoy Ultimatum"),
                ),
            ),
        )
        assertEquals(listOf("Season of Bloom", "Brevoy Ultimatum"), beats.clockExpirations.map { it.labelArgs["label"] })
    }

    @Test
    fun threatsDeadlinesAndRuinMapThrough() {
        val threat = unsafeJso<dynamic>()
        threat.name = "Hargulka"
        val beats = endTurnBeats(
            tickResult(
                threats = arrayOf(threat.unsafeCast<RawWarThreat>()),
                deadlines = listOf("Moon Radish Soup"),
                ruin = true,
            ),
        )
        assertEquals("Hargulka", beats.newThreats.single().labelArgs["name"])
        assertEquals("Moon Radish Soup", beats.questDeadlines.single().labelArgs["name"])
        assertTrue(beats.ruinThresholdCrossed)
        assertTrue(beats.resourceAlerts.isEmpty(), "resource alerts are the panel phase's decision")
    }

    @Test
    fun aNonGmGetsNullBeforeAnythingIsTouched() = runTest {
        // The actor fixture is deliberately bogus: if the gate were not the first statement, the
        // adapter would throw on it instead of returning null.
        val game = unsafeJso<dynamic>()
        game.user = unsafeJso<dynamic>()
        game.user.isGM = false
        val bogusActor = unsafeJso<dynamic>()
        assertNull(buildForecast(game.unsafeCast<Game>(), bogusActor.unsafeCast<KingdomActor>(), 7))
    }
}

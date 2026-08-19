package at.posselt.pfrpg2e.camping

import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Regression cover for the once-per-camping-session companion cap (card t_f80e7c4b).
 *
 * The cap was originally keyed on `dailyPrepsAtTime`, described as the camping system's own session
 * marker. It is not: two paths rewrite it with no camping session having occurred, and neither
 * clears the camping activity results that ARE the oncePerSession lock. These tests pin the
 * separation so a future simplification back onto the timestamp is caught here.
 */
class CampingSessionIdentityTest {

    private fun gameAtWorldTime(seconds: Int): Game {
        val g = unsafeJso<dynamic>()
        g.time = unsafeJso<dynamic>()
        g.time.worldTime = seconds
        return g.unsafeCast<Game>()
    }

    private fun campingAt(sessionId: Int?, dailyPreps: Int): CampingData {
        val c = unsafeJso<dynamic>()
        c.campingSessionId = sessionId
        c.dailyPrepsAtTime = dailyPreps
        c.secondsSpentTraveling = 0
        c.secondsSpentHexploring = 0
        return c.unsafeCast<CampingData>()
    }

    @Test
    fun resettingTheAdventuringTimeTrackerDoesNotHandBackCompanionAttempts() {
        // The sheet's "reset adventuring time tracker" button and the 24h auto-reset both call
        // this. Under the old keying it minted a brand-new session identity, silently re-enabling
        // every companion's once-per-session Influence and Discover attempt.
        val camping = campingAt(sessionId = 4, dailyPreps = 1_000)
        val before = camping.campingSessionId

        camping.resetTimeTracking(gameAtWorldTime(999_000))

        assertNotEquals(1_000, camping.dailyPrepsAtTime, "the time stamp is expected to move")
        assertEquals(before, camping.campingSessionId, "but the session identity must NOT")
    }

    @Test
    fun theSessionCounterIsWhatDistinguishesConsecutiveSessions() {
        val camping = campingAt(sessionId = 4, dailyPreps = 1_000)
        camping.campingSessionId = nextCampingSessionId(camping.campingSessionId)
        assertEquals(5, camping.campingSessionId)
        assertEquals("5", campingSessionIdOf(camping.campingSessionId))
    }
}

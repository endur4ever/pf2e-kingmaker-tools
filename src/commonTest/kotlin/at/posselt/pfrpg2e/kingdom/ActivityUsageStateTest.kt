package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityUsageStateTest {

    // ---- timeout rule table ----

    @Test
    fun falseVictoryLocksOneTurnOnFailureAndSixOnCriticalFailure() {
        assertEquals(1, activityTimeoutTurns("false-victory", DegreeOfSuccess.FAILURE))
        assertEquals(6, activityTimeoutTurns("false-victory", DegreeOfSuccess.CRITICAL_FAILURE))
    }

    @Test
    fun timeoutsOnlyTriggerOnFailureDegrees() {
        assertNull(activityTimeoutTurns("false-victory", DegreeOfSuccess.SUCCESS))
        assertNull(activityTimeoutTurns("false-victory", DegreeOfSuccess.CRITICAL_SUCCESS))
    }

    @Test
    fun criticalFailureOnlyTimeouts() {
        assertNull(activityTimeoutTurns("garrison-army", DegreeOfSuccess.FAILURE))
        assertEquals(4, activityTimeoutTurns("garrison-army", DegreeOfSuccess.CRITICAL_FAILURE))
        assertEquals(2, activityTimeoutTurns("supernatural-solution", DegreeOfSuccess.CRITICAL_FAILURE))
        assertEquals(2, activityTimeoutTurns("send-diplomatic-envoy", DegreeOfSuccess.CRITICAL_FAILURE))
        assertEquals(1, activityTimeoutTurns("preventative-measures", DegreeOfSuccess.CRITICAL_FAILURE))
    }

    @Test
    fun failureAndCriticalFailureBothTimeout() {
        assertEquals(1, activityTimeoutTurns("process-hidden-fees", DegreeOfSuccess.FAILURE))
        assertEquals(1, activityTimeoutTurns("process-hidden-fees", DegreeOfSuccess.CRITICAL_FAILURE))
        assertEquals(1, activityTimeoutTurns("supplementary-hunting", DegreeOfSuccess.FAILURE))
        assertEquals(1, activityTimeoutTurns("supplementary-hunting", DegreeOfSuccess.CRITICAL_FAILURE))
    }

    @Test
    fun untrackedActivitiesHaveNoTimeoutAndNoEscalation() {
        assertNull(activityTimeoutTurns("claim-hex", DegreeOfSuccess.CRITICAL_FAILURE))
        assertFalse(activityEscalatesDc("claim-hex"))
        assertFalse(activityTracksUsage("claim-hex"))
        assertTrue(activityTracksUsage("false-victory"))
        assertTrue(activityTracksUsage("clandestine-business"))
    }

    // ---- lockout write + self-expiry over turn boundaries ----

    @Test
    fun oneTurnTimeoutBlocksExactlyTheNextTurn() {
        // used on turn 5, failure => locked turns 5 (remainder) and 6, available on turn 7? no:
        // lockedUntilTurn = 5 + 1 + 1 = 7 -> locked while turn < 7
        val state = recordActivityUse(emptyList(), "process-hidden-fees", DegreeOfSuccess.FAILURE, currentTurn = 5)
        assertEquals(7, activityLockedUntil(state, "process-hidden-fees"))
        assertTrue(isActivityLocked(state, "process-hidden-fees", currentTurn = 5))  // just used
        assertTrue(isActivityLocked(state, "process-hidden-fees", currentTurn = 6))  // next turn: blocked
        assertFalse(isActivityLocked(state, "process-hidden-fees", currentTurn = 7)) // turn after: available
    }

    @Test
    fun sixTurnTimeoutBlocksSixTurns() {
        val state = recordActivityUse(emptyList(), "false-victory", DegreeOfSuccess.CRITICAL_FAILURE, currentTurn = 10)
        assertEquals(17, activityLockedUntil(state, "false-victory")) // 10 + 6 + 1
        assertTrue(isActivityLocked(state, "false-victory", 16))
        assertFalse(isActivityLocked(state, "false-victory", 17))
    }

    @Test
    fun successfulOutcomeDoesNotLock() {
        val state = recordActivityUse(emptyList(), "false-victory", DegreeOfSuccess.SUCCESS, currentTurn = 3)
        assertFalse(isActivityLocked(state, "false-victory", 4))
        assertEquals(emptyList(), state) // untracked outcome => no entry created
    }

    @Test
    fun recordUseIsNoOpForUntrackedActivity() {
        assertEquals(emptyList(), recordActivityUse(emptyList(), "build-structure", DegreeOfSuccess.CRITICAL_FAILURE, 1))
    }

    @Test
    fun isActivityLockedFalseWhenNoEntry() {
        assertFalse(isActivityLocked(emptyList(), "false-victory", 1))
        assertNull(activityLockedUntil(emptyList(), "false-victory"))
    }

    // ---- escalating DC ----

    @Test
    fun escalatingActivitiesFlaggedCorrectly() {
        assertTrue(activityEscalatesDc("clandestine-business"))
        assertTrue(activityEscalatesDc("request-foreign-aid-vk"))
        assertFalse(activityEscalatesDc("request-foreign-aid")) // base variant does NOT escalate
    }

    @Test
    fun escalatingDcClimbsByTwoPerConsecutiveTurnAndDecaysByOneWhenIdle() {
        // turn 1: use at base (bump 0)
        var state = recordActivityUse(emptyList(), "clandestine-business", DegreeOfSuccess.SUCCESS, currentTurn = 1)
        assertEquals(0, activityDcBump(state, "clandestine-business")) // this turn's check is at base
        state = tickActivityUsages(state, nextTurn = 2)
        assertEquals(2, activityDcBump(state, "clandestine-business")) // next turn: +2

        // turn 2: used again -> +2 more at next tick
        state = recordActivityUse(state, "clandestine-business", DegreeOfSuccess.FAILURE, currentTurn = 2)
        state = tickActivityUsages(state, nextTurn = 3)
        assertEquals(4, activityDcBump(state, "clandestine-business"))

        // turn 3: idle -> decays by 1
        state = tickActivityUsages(state, nextTurn = 4)
        assertEquals(3, activityDcBump(state, "clandestine-business"))
    }

    @Test
    fun escalatingDcFloorsAtZeroAndEntryIsDropped() {
        var state = listOf(ActivityUsage("clandestine-business", dcBump = 1, usedThisTurn = false))
        state = tickActivityUsages(state, nextTurn = 9) // 1 - 1 = 0 -> dropped
        assertEquals(0, activityDcBump(state, "clandestine-business"))
        assertTrue(state.isEmpty())
    }

    @Test
    fun tickResetsUsedThisTurnFlag() {
        var state = recordActivityUse(emptyList(), "clandestine-business", DegreeOfSuccess.SUCCESS, currentTurn = 1)
        assertTrue(state.single().usedThisTurn)
        state = tickActivityUsages(state, nextTurn = 2)
        assertFalse(state.single().usedThisTurn)
    }

    @Test
    fun tickKeepsLockedEntryEvenWhenBumpIsZero() {
        val locked = listOf(ActivityUsage("false-victory", lockedUntilTurn = 20, dcBump = 0))
        val ticked = tickActivityUsages(locked, nextTurn = 12)
        assertEquals(1, ticked.size)
        assertEquals(20, ticked.single().lockedUntilTurn)
    }

    @Test
    fun tickClearsLockOnceExpired() {
        val locked = listOf(ActivityUsage("false-victory", lockedUntilTurn = 12, dcBump = 0))
        val ticked = tickActivityUsages(locked, nextTurn = 12) // 12 < 12 is false -> expired -> dropped
        assertTrue(ticked.isEmpty())
    }
}

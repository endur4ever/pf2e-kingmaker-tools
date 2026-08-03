package at.posselt.pfrpg2e.companion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionSessionCapTest {
    @Test
    fun firstAttemptInASessionIsAllowed() {
        assertTrue(canAttemptCompanionInteraction(lastAttemptSessionId = null, currentSessionId = "s1"))
    }

    @Test
    fun secondAttemptInTheSameSessionIsBlocked() {
        // Attempted this session (last == current) -> blocked, even after reopening the dialog.
        assertFalse(canAttemptCompanionInteraction(lastAttemptSessionId = "s1", currentSessionId = "s1"))
    }

    @Test
    fun nextSessionReEnablesTheAttempt() {
        // dailyPrepsAtTime advanced -> new session id -> allowed again.
        assertTrue(canAttemptCompanionInteraction(lastAttemptSessionId = "s1", currentSessionId = "s2"))
    }

    @Test
    fun noActiveCampingSessionIsUncapped() {
        // Outside camping there is nothing to cap against.
        assertTrue(canAttemptCompanionInteraction(lastAttemptSessionId = "s1", currentSessionId = null))
        assertTrue(canAttemptCompanionInteraction(lastAttemptSessionId = null, currentSessionId = null))
    }
}

package at.posselt.pfrpg2e

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers which warnings are allowed to repeat, and when. */
class WarningCadenceTest {
    @Test
    fun aWorldScopedWarningFiresOnceAndNeverAgain() {
        assertTrue(shouldPostWarning("k", WarningCadence.ONCE_PER_WORLD, emptySet(), emptySet()))
        assertFalse(shouldPostWarning("k", WarningCadence.ONCE_PER_WORLD, setOf("k"), emptySet()))
    }

    @Test
    fun aWorldScopedWarningIgnoresSessionState() {
        // Reloading must not resurrect a warning the GM has already been given permanently.
        assertFalse(shouldPostWarning("k", WarningCadence.ONCE_PER_WORLD, setOf("k"), setOf("k")))
        assertTrue(shouldPostWarning("k", WarningCadence.ONCE_PER_WORLD, emptySet(), setOf("k")))
    }

    @Test
    fun aSessionScopedWarningRepeatsAfterAReload() {
        // The persisted set says it was shown once, long ago; a recurring failure must still warn.
        assertTrue(shouldPostWarning("k", WarningCadence.ONCE_PER_SESSION, setOf("k"), emptySet()))
    }

    @Test
    fun aSessionScopedWarningDoesNotRepeatWithinOneSitting() {
        // Five rests in one session must not post five identical warnings.
        assertFalse(shouldPostWarning("k", WarningCadence.ONCE_PER_SESSION, emptySet(), setOf("k")))
    }

    @Test
    fun keysAreIndependent() {
        assertTrue(shouldPostWarning("a", WarningCadence.ONCE_PER_WORLD, setOf("b"), emptySet()))
        assertTrue(shouldPostWarning("a", WarningCadence.ONCE_PER_SESSION, emptySet(), setOf("b")))
    }
}

package at.posselt.pfrpg2e.data.kingdom.subsystems

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubsystemEngineTest {
    private val rule = PointRule()

    @Test
    fun theDefaultRuleIsThePf2eInfluenceTable() {
        assertEquals(2, pointsForOutcome(SubsystemOutcome.CRITICAL_SUCCESS, rule))
        assertEquals(1, pointsForOutcome(SubsystemOutcome.SUCCESS, rule))
        assertEquals(0, pointsForOutcome(SubsystemOutcome.FAILURE, rule))
        assertEquals(-1, pointsForOutcome(SubsystemOutcome.CRITICAL_FAILURE, rule))
    }

    @Test
    fun resistancesGrindAGainToZeroButNeverIntoALoss() {
        assertEquals(0, adjustForTraits(1, listOf(SubsystemTrait("aloof", -2))),
            "a resistance is the NPC being hard to charm, not actively souring")
        assertEquals(3, adjustForTraits(2, listOf(SubsystemTrait("flattery", +1))))
        assertEquals(2, adjustForTraits(1, listOf(SubsystemTrait("flattery", +2), SubsystemTrait("aloof", -1))))
    }

    @Test
    fun aLossPassesThroughTraitsUntouched() {
        assertEquals(-1, adjustForTraits(-1, listOf(SubsystemTrait("flattery", +2))),
            "traits modify what you win, not what you fumble")
        assertEquals(0, adjustForTraits(0, listOf(SubsystemTrait("aloof", -2))))
    }

    @Test
    fun theCheckLogRecordsWhatHappenedToThePoolNotWhatTheDiceDeserved() {
        val atZero = applyCheck(0, SubsystemOutcome.CRITICAL_FAILURE, rule, emptyList())
        assertEquals(0, atZero.newTotal, "the pool floors at zero")
        assertEquals(0, atZero.appliedDelta, "a crit-fail at zero logs 0, not -1")

        val normal = applyCheck(3, SubsystemOutcome.CRITICAL_SUCCESS, rule, emptyList())
        assertEquals(5, normal.newTotal)
        assertEquals(2, normal.appliedDelta)

        val dip = applyCheck(1, SubsystemOutcome.CRITICAL_FAILURE, rule, emptyList())
        assertEquals(0, dip.newTotal)
        assertEquals(-1, dip.appliedDelta)
    }

    @Test
    fun thresholdsFireExactlyOnceOnTheUpwardCrossing() {
        assertEquals(listOf(4), newlyCrossedThresholds(3, 5, listOf(4, 6)))
        assertEquals(listOf(4, 6), newlyCrossedThresholds(3, 7, listOf(6, 4)),
            "a big jump crosses both, reported in ascending order")
        assertEquals(emptyList(), newlyCrossedThresholds(5, 3, listOf(4)),
            "losing points does not un-earn a revealed secret")
        assertEquals(emptyList(), newlyCrossedThresholds(4, 4, listOf(4)))
        assertEquals(listOf(4), newlyCrossedThresholds(3, 4, listOf(4)), "landing exactly on it counts")
        assertEquals(emptyList(), newlyCrossedThresholds(4, 5, listOf(4)),
            "a threshold already held does not re-fire when the pool moves further up")
    }

    @Test
    fun participantsAccumulateAndNewcomersGetARow() {
        var list = applyParticipantDelta(emptyList(), "pc-1", 2)
        list = applyParticipantDelta(list, "pc-1", 1)
        list = applyParticipantDelta(list, "pc-2", 1)
        assertEquals(listOf(ParticipantPoints("pc-1", 3), ParticipantPoints("pc-2", 1)), list)
    }

    @Test
    fun outcomesRoundTripAndRejectStrangers() {
        assertEquals(SubsystemOutcome.CRITICAL_SUCCESS, SubsystemOutcome.fromString("criticalSuccess"))
        assertEquals("criticalFailure", SubsystemOutcome.CRITICAL_FAILURE.value)
        assertNull(SubsystemOutcome.fromString("fumble"))
    }
}

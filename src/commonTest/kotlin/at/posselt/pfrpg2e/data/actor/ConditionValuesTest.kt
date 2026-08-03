package at.posselt.pfrpg2e.data.actor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionValuesTest {
    @Test
    fun unconsciousAndProneAreBinaryNotValued() {
        // the exact pair the encounter resolver applies to sleeping actors
        assertFalse(isValuedCondition("unconscious"))
        assertFalse(isValuedCondition("prone"))
    }

    @Test
    fun otherCommonBinaryConditionsAreNotValued() {
        listOf("blinded", "fatigued", "paralyzed", "off-guard", "grabbed", "hidden").forEach {
            assertFalse(isValuedCondition(it), "$it must be binary")
        }
    }

    @Test
    fun valuedConditionsAreClassifiedAsValued() {
        listOf("clumsy", "drained", "enfeebled", "frightened", "sickened", "slowed", "stunned", "stupefied", "doomed", "dying", "wounded").forEach {
            assertTrue(isValuedCondition(it), "$it must be valued")
        }
    }

    @Test
    fun unknownSlugsDefaultToBinary() {
        // safer default: never run an unknown slug through increase-condition
        assertFalse(isValuedCondition("some-future-condition"))
    }
}

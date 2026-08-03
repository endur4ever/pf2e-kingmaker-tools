package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.ArmyCondition
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeployArmyOutcomeTest {
    @Test
    fun successesInflictNoMishap() {
        for (degree in listOf(DegreeOfSuccess.CRITICAL_SUCCESS, DegreeOfSuccess.SUCCESS)) {
            val o = deployArmyOutcome(degree)
            assertTrue(o.conditions.isEmpty())
            assertNull(o.damageFlatCheckDc)
            assertNull(o.unrestDice)
        }
    }

    @Test
    fun failureIsWearyPlusDc6DamageCheck() {
        val o = deployArmyOutcome(DegreeOfSuccess.FAILURE)
        assertEquals(listOf(ArmyCondition.WEARY), o.conditions)
        assertEquals(6, o.damageFlatCheckDc)
        assertNull(o.unrestDice)
    }

    @Test
    fun criticalFailureIsMiredPlusUnrestPlusDc11DamageCheck() {
        val o = deployArmyOutcome(DegreeOfSuccess.CRITICAL_FAILURE)
        assertEquals(listOf(ArmyCondition.MIRED), o.conditions)
        assertEquals(11, o.damageFlatCheckDc)
        assertEquals("1d4", o.unrestDice)
    }

    @Test
    fun flatCheckDamageIsOneHp() {
        assertEquals(1, DEPLOY_ARMY_FLAT_CHECK_DAMAGE)
    }
}

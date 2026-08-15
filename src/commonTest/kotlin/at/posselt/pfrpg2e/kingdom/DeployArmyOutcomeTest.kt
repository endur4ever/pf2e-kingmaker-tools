package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeployArmyOutcomeTest {
    @Test
    fun criticalSuccessMakesTheArmyEfficient() {
        // "it arrives at its destination and then becomes efficient" -- a reward, not the absence
        // of a mishap. Reading this degree as "nothing happens" silently dropped the benefit.
        val o = deployArmyOutcome(DegreeOfSuccess.CRITICAL_SUCCESS)
        assertEquals(listOf(DeployArmyEffect.EFFICIENT), o.effects)
        assertNull(o.damageFlatCheckDc)
        assertNull(o.unrestDice)
    }

    @Test
    fun plainSuccessJustArrives() {
        val o = deployArmyOutcome(DegreeOfSuccess.SUCCESS)
        assertTrue(o.effects.isEmpty())
        assertNull(o.damageFlatCheckDc)
        assertNull(o.unrestDice)
    }

    @Test
    fun failureIsWearyPlusDc6DamageCheck() {
        val o = deployArmyOutcome(DegreeOfSuccess.FAILURE)
        assertEquals(listOf(DeployArmyEffect.WEARY), o.effects)
        assertEquals(6, o.damageFlatCheckDc)
        assertNull(o.unrestDice)
    }

    @Test
    fun criticalFailureLosesTheArmyRatherThanMiringIt() {
        // "the army becomes lost until it recovers from this condition" -- lost, not mired. Mired
        // is a different condition with a different rule element (it penalises the next Deploy
        // Army check), and substituting it both understates and misprices the critical failure.
        val o = deployArmyOutcome(DegreeOfSuccess.CRITICAL_FAILURE)
        assertEquals(listOf(DeployArmyEffect.LOST), o.effects)
        assertEquals(11, o.damageFlatCheckDc)
        assertEquals("1d4", o.unrestDice)
    }

    @Test
    fun onlyWearyStacks() {
        // Weary carries a counter badge in PF2e, so a second application raises it. Efficient and
        // lost are on-or-off, and re-applying them must not stack anything.
        assertTrue(DeployArmyEffect.WEARY.valued)
        assertTrue(!DeployArmyEffect.EFFICIENT.valued)
        assertTrue(!DeployArmyEffect.LOST.valued)
    }

    @Test
    fun everyEffectSlugMatchesThePf2eCondition() {
        // These slugs are the join to the pf2e.kingmaker-features compendium items; a typo here
        // means the effect is never found and the condition silently never applies.
        assertEquals("efficient", DeployArmyEffect.EFFICIENT.slug)
        assertEquals("weary", DeployArmyEffect.WEARY.slug)
        assertEquals("lost", DeployArmyEffect.LOST.slug)
    }

    @Test
    fun flatCheckDamageIsOneHp() {
        assertEquals(1, DEPLOY_ARMY_FLAT_CHECK_DAMAGE)
    }

    @Test
    fun criticalFailureOffersConditionFlatCheckAndUnrest() {
        val offers = deployArmyOffers(deployArmyOutcome(DegreeOfSuccess.CRITICAL_FAILURE))
        assertEquals(listOf("lost", DEPLOY_OFFER_FLAT_CHECK, DEPLOY_OFFER_UNREST), offers.map { it.key })
        assertEquals(11, offers.first { it.key == DEPLOY_OFFER_FLAT_CHECK }.amount)
        assertEquals(DeployArmyEffect.LOST, offers.first().effect)
    }

    @Test
    fun plainSuccessOffersNothingToApply() {
        assertTrue(deployArmyOffers(deployArmyOutcome(DegreeOfSuccess.SUCCESS)).isEmpty())
    }

    @Test
    fun anAlreadyAppliedEffectIsNotReOffered() {
        val outcome = deployArmyOutcome(DegreeOfSuccess.CRITICAL_FAILURE)
        val offers = deployArmyOffers(outcome, alreadyApplied = setOf("lost", DEPLOY_OFFER_UNREST))
        assertEquals(listOf(DEPLOY_OFFER_FLAT_CHECK), offers.map { it.key })
    }

    @Test
    fun aFullyAppliedOutcomeOffersNothing() {
        val outcome = deployArmyOutcome(DegreeOfSuccess.FAILURE)
        val offers = deployArmyOffers(outcome, alreadyApplied = setOf("weary", DEPLOY_OFFER_FLAT_CHECK))
        assertTrue(offers.isEmpty())
    }
}

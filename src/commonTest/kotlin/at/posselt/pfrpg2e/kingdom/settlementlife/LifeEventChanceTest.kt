package at.posselt.pfrpg2e.kingdom.settlementlife

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LifeEventChanceTest {
    @Test
    fun theCurveMatchesThePlansProposedDefaults() {
        // §9 Q2: 0.15 + 0.05*level + pop/20000 -- a village near one-in-five, a metropolis high
        assertEquals(20, lifeEventChancePercent(settlementLevel = 1, population = 0))
        assertEquals(22, lifeEventChancePercent(settlementLevel = 1, population = 400))
        assertTrue(lifeEventChancePercent(settlementLevel = 15, population = 25000) >= 80)
    }

    @Test
    fun noSettlementIsEverACertainty() {
        // a town whose chronicle never has a quiet month reads as a machine, not a place
        assertEquals(90, lifeEventChancePercent(settlementLevel = 20, population = 100000))
    }

    @Test
    fun garbageInputsClampInsteadOfGoingNegative() {
        assertTrue(lifeEventChancePercent(settlementLevel = -5, population = -100) >= 0)
    }
}

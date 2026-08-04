package at.posselt.pfrpg2e.data.armies

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmyLevelUpOfferTest {
    @Test
    fun crossingTheThresholdOffersALevelUp() {
        // level 1 threshold = 100
        assertTrue(shouldOfferArmyLevelUp(oldXp = 80, newXp = 100, currentLevel = 1))
        assertTrue(shouldOfferArmyLevelUp(oldXp = 0, newXp = 150, currentLevel = 1))
    }

    @Test
    fun alreadyAboveTheThresholdDoesNotReOffer() {
        // a declined offer must not be re-posted after every subsequent battle
        assertFalse(shouldOfferArmyLevelUp(oldXp = 120, newXp = 140, currentLevel = 1))
    }

    @Test
    fun belowTheThresholdDoesNotOffer() {
        assertFalse(shouldOfferArmyLevelUp(oldXp = 40, newXp = 99, currentLevel = 1))
    }

    @Test
    fun theLevelCapNeverOffers() {
        assertFalse(shouldOfferArmyLevelUp(oldXp = 0, newXp = Int.MAX_VALUE, currentLevel = 20))
    }

    @Test
    fun thresholdsScaleWithLevel() {
        // level 5 threshold = 200: 150 -> 199 no, 150 -> 200 yes
        assertFalse(shouldOfferArmyLevelUp(oldXp = 150, newXp = 199, currentLevel = 5))
        assertTrue(shouldOfferArmyLevelUp(oldXp = 150, newXp = 200, currentLevel = 5))
        assertEquals(200, xpThresholdForLevel(5))
    }
}

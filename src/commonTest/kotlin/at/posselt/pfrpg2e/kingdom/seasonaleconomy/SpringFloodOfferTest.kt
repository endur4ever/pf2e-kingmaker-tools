package at.posselt.pfrpg2e.kingdom.seasonaleconomy

import at.posselt.pfrpg2e.data.regions.Season
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpringFloodOfferTest {
    @Test
    fun onlySpringCarriesTheOfferAndOnlyWhenTheGateIsOpen() {
        // section 3.2: spring's multipliers stay neutral; the flood is the season's only consequence
        val spring = seasonalModifiers(Season.SPRING, enabled = true)
        assertTrue(spring.springFloodOffer)
        assertTrue(spring.farmlandFoodMultiplier == 1.0 && spring.foodConsumptionDelta == 0)
        assertFalse(seasonalModifiers(Season.SPRING, enabled = false).springFloodOffer)
        for (season in listOf(Season.SUMMER, Season.FALL, Season.WINTER)) {
            assertFalse(seasonalModifiers(season, enabled = true).springFloodOffer, "$season offered a flood")
        }
    }

    @Test
    fun theOfferFiresOncePerSpringNotOncePerMonth() {
        // spring is three End Turns; the marker records the world-year the card was posted in
        val spring = seasonalModifiers(Season.SPRING, enabled = true)
        assertTrue(shouldOfferSpringFlood(spring, lastFloodYear = null, worldYear = 4710))
        assertFalse(shouldOfferSpringFlood(spring, lastFloodYear = 4710, worldYear = 4710))
        assertTrue(shouldOfferSpringFlood(spring, lastFloodYear = 4710, worldYear = 4711))
    }

    @Test
    fun aClosedGateNeverOffersWhateverTheMarkerSays() {
        assertFalse(shouldOfferSpringFlood(seasonalModifiers(Season.SPRING, enabled = false), null, 4710))
    }
}

package at.posselt.pfrpg2e.data.regions

import kotlin.test.Test
import kotlin.test.assertEquals

class WeatherTest {
    @Test
    fun weatherType() {
        assertEquals(WeatherType.SNOWY, findWeatherType(isCold = true, hasPrecipitation = true))
        assertEquals(WeatherType.RAINY, findWeatherType(isCold = false, hasPrecipitation = true))
        assertEquals(WeatherType.COLD, findWeatherType(isCold = true, hasPrecipitation = false))
        assertEquals(WeatherType.SUNNY, findWeatherType(isCold = false, hasPrecipitation = false))
    }

    @Test
    fun seasonForMonth() {
        // Winter: Abadius (0), Calistril (1), Kuthona (11)
        assertEquals(Season.WINTER, getSeasonForMonth(0))
        assertEquals(Season.WINTER, getSeasonForMonth(1))
        assertEquals(Season.WINTER, getSeasonForMonth(11))

        // Spring: Pharast (2), Gozran (3), Desnus (4)
        assertEquals(Season.SPRING, getSeasonForMonth(2))
        assertEquals(Season.SPRING, getSeasonForMonth(3))
        assertEquals(Season.SPRING, getSeasonForMonth(4))

        // Summer: Sarenith (5), Erastus (6), Arodus (7)
        assertEquals(Season.SUMMER, getSeasonForMonth(5))
        assertEquals(Season.SUMMER, getSeasonForMonth(6))
        assertEquals(Season.SUMMER, getSeasonForMonth(7))

        // Fall: Rova (8), Lamashtan (9), Neth (10)
        assertEquals(Season.FALL, getSeasonForMonth(8))
        assertEquals(Season.FALL, getSeasonForMonth(9))
        assertEquals(Season.FALL, getSeasonForMonth(10))
    }
}
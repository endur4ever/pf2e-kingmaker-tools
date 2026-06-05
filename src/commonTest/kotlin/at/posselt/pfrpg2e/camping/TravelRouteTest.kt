package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TravelRouteServiceTest {

    private class MockGridProvider(
        private val adjacencies: Map<String, List<String>>,
        private val contents: Map<String, List<HexContent>>
    ) : HexGridProvider {
        override fun getAdjacentHexKeys(hexKey: String): List<String> = adjacencies[hexKey] ?: emptyList()
        override fun getContentForHex(hexKey: String): List<HexContent> = contents[hexKey] ?: emptyList()
    }

    private fun createMockContent(id: String, type: HexContentType): HexContent {
        return HexContent(
            id = id,
            hexKey = "key-$id",
            type = type,
            name = "Name-$id",
            visibility = HexContentVisibility.DISCOVERED
        )
    }

    @Test
    fun testSimpleRoute() {
        val provider = MockGridProvider(
            adjacencies = mapOf(
                "A" to listOf("B"),
                "B" to listOf("C")
            ),
            contents = emptyMap()
        )
        val service = TravelRouteService(provider)
        val plan = TravelPlan(1.0, emptyMap(), emptyMap(), 1.0)
        
        val route = service.calculateRoute("A", "C", plan)
        
        assertNotNull(route)
        assertEquals(listOf("A", "B", "C"), route.path.toList())
        assertEquals(2.0, route.totalCost)
    }

    @Test
    fun testTerrainModifier() {
        val forestContent = createMockContent("forest", HexContentType.CUSTOM)
        val provider = MockGridProvider(
            adjacencies = mapOf(
                "A" to listOf("B"),
                "B" to listOf("C")
            ),
            contents = mapOf(
                "B" to listOf(forestContent)
            )
        )
        val service = TravelRouteService(provider)
        // If B has forest, A->B weight is 1.0 + 2.0 = 3.0. 
        // Then B->C weight is 1.0. Total 4.0.
        val plan = TravelPlan(1.0, mapOf("CUSTOM" to 2.0), emptyMap(), 1.0)
        
        val route = service.calculateRoute("A", "C", plan)
        
        assertNotNull(route)
        assertEquals(4.0, route.totalCost)
    }

    @Test
    fun testWeatherModifier() {
        val provider = MockGridProvider(
            adjacencies = mapOf(
                "A" to listOf("else") // Dummy
            ),
            contents = emptyMap()
        )
        val service = TravelRouteService(provider)
        // Weather modifier 2.0 (doubles cost)
        val plan = TravelPlan(1.0, emptyMap(), emptyMap(), 2.0)
        
        // We need a real path to test weight multiplication. Let's use simple route.
        val provider2 = MockGridProvider(
            adjacencies = mapOf("A" to listOf("B")),
            contents = emptyMap()
        )
        val service2 = TravelRouteService(provider2)
        val route = service2.calculateRoute("A", "B", plan)
        
        assertNotNull(route)
        // 1.0 / 1.0 * 2.0 = 2.0
        assertEquals(2.0, route.totalCost)
    }
}

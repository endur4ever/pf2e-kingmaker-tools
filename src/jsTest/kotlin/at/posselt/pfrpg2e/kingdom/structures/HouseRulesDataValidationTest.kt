package at.posselt.pfrpg2e.kingdom.structures

import at.posselt.pfrpg2e.kingdom.RawActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@JsModule("./kingdom-activities.json")
external val testKingdomActivities: Array<RawActivity>

class HouseRulesDataValidationTest {

    @Test
    fun testCompanionStructuresExist() {
        val shack = structures.find { it.id == "companion-shacks" }
        assertNotNull(shack, "Companion Shacks should exist")
        assertEquals(2, shack.level)
        assertEquals(1, shack.lots)
        assertEquals("structures.companion-shacks.name", shack.name)
        assertEquals("structures.companion-shacks.notes", shack.notes)
        assertNotNull(shack.construction)
        assertEquals(4, shack.construction?.rp)
        assertEquals(4, shack.construction?.lumber)
        assertEquals(4, shack.construction?.stone)
        assertEquals(16, shack.construction?.dc)
        assertEquals(1, shack.construction?.skills?.size)
        assertEquals("engineering", shack.construction?.skills?.get(0)?.skill)

        val room = structures.find { it.id == "companion-rooms" }
        assertNotNull(room, "Companion Rooms should exist")
        assertEquals(9, room.level)
        assertEquals(2, room.lots)
        assertEquals("structures.companion-rooms.name", room.name)
        assertEquals("structures.companion-rooms.notes", room.notes)
        assertNotNull(room.construction)
        assertEquals(30, room.construction?.rp)
        assertEquals(5, room.construction?.lumber)
        assertEquals(5, room.construction?.stone)
        assertEquals(26, room.construction?.dc)
        assertEquals(1, room.upgradeFrom?.size)
        assertTrue(room.upgradeFrom?.contains("companion-shacks") == true)

        val quarters = structures.find { it.id == "companion-quarters" }
        assertNotNull(quarters, "Companion Quarters should exist")
        assertEquals(15, quarters.level)
        assertEquals(4, quarters.lots)
        assertEquals("structures.companion-quarters.name", quarters.name)
        assertEquals("structures.companion-quarters.notes", quarters.notes)
        assertNotNull(quarters.construction)
        assertEquals(45, quarters.construction?.rp)
        assertEquals(10, quarters.construction?.lumber)
        assertEquals(20, quarters.construction?.stone)
        assertEquals(8, quarters.construction?.luxuries)
        assertEquals(34, quarters.construction?.dc)
        assertEquals(1, quarters.upgradeFrom?.size)
        assertTrue(quarters.upgradeFrom?.contains("companion-rooms") == true)
    }

    @Test
    fun testHouseRulesKingdomActivitiesExist() {
        val naval = testKingdomActivities.find { it.id == "naval-support" }
        assertNotNull(naval, "Naval Support should exist")
        assertEquals("activities.naval-support.title", naval.title)
        assertEquals("activities.naval-support.description", naval.description)
        assertEquals("leadership", naval.phase)
        assertEquals("control", naval.dc)
        assertTrue(naval.enabled)
        assertEquals(0, naval.skills["boating"])

        val cleanse = testKingdomActivities.find { it.id == "cleanse-item" }
        assertNotNull(cleanse, "Cleanse Item should exist")
        assertEquals("activities.cleanse-item.title", cleanse.title)
        assertEquals("activities.cleanse-item.description", cleanse.description)
        assertEquals("activities.cleanse-item.special", cleanse.special)
        assertEquals("leadership", cleanse.phase)
        assertEquals("custom", cleanse.dc)
        assertTrue(cleanse.enabled)
        assertEquals(0, cleanse.skills["magic"])
    }
}

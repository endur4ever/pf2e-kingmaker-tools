package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.migrations.migrations.Migration26
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals

class Migration26Test {

    @Test
    fun testMigration26ConvertsFlatWatchSlotsToNested() = runTest {
        val gameMock = unsafeJso<Game>()
        val camping = unsafeJso<dynamic> {
            watchSlots = arrayOf("Actor.abc", "", "Actor.def")
        }

        Migration26().migrateCamping(gameMock, camping)

        val slots = camping.watchSlots.unsafeCast<Array<Array<String>>>()
        assertEquals(3, slots.size)
        assertEquals(listOf("Actor.abc"), slots[0].toList())
        assertEquals(emptyList(), slots[1].toList())
        assertEquals(listOf("Actor.def"), slots[2].toList())
    }

    @Test
    fun testMigration26HandlesEmptyStringAsEmptySlot() = runTest {
        val gameMock = unsafeJso<Game>()
        val camping = unsafeJso<dynamic> {
            watchSlots = arrayOf("", "", "")
        }

        Migration26().migrateCamping(gameMock, camping)

        val slots = camping.watchSlots.unsafeCast<Array<Array<String>>>()
        assertEquals(3, slots.size)
        assertEquals(emptyList(), slots[0].toList())
        assertEquals(emptyList(), slots[1].toList())
        assertEquals(emptyList(), slots[2].toList())
    }

    @Test
    fun testMigration26PreservesAlreadyNestedArrays() = runTest {
        val gameMock = unsafeJso<Game>()
        val camping = unsafeJso<dynamic> {
            watchSlots = arrayOf(
                arrayOf("Actor.abc"),
                arrayOf(),
                arrayOf("Actor.def", "Actor.ghi")
            )
        }

        Migration26().migrateCamping(gameMock, camping)

        val slots = camping.watchSlots.unsafeCast<Array<Array<String>>>()
        assertEquals(3, slots.size)
        assertEquals(listOf("Actor.abc"), slots[0].toList())
        assertEquals(emptyList(), slots[1].toList())
        assertEquals(listOf("Actor.def", "Actor.ghi"), slots[2].toList())
    }

    @Test
    fun testMigration26HandlesNullWatchSlots() = runTest {
        val gameMock = unsafeJso<Game>()
        val camping = unsafeJso<dynamic> {}

        Migration26().migrateCamping(gameMock, camping)

        val slots = camping.watchSlots.unsafeCast<Array<Array<String>>>()
        assertEquals(0, slots.size)
    }

    @Test
    fun testMigration26HandlesUndefinedWatchSlots() = runTest {
        val gameMock = unsafeJso<Game>()
        val camping = unsafeJso<dynamic> {
            watchSlots = null
        }

        Migration26().migrateCamping(gameMock, camping)

        val slots = camping.watchSlots.unsafeCast<Array<Array<String>>>()
        assertEquals(0, slots.size)
    }

    @Test
    fun testMigration26HandlesUnexpectedEntryTypes() = runTest {
        val gameMock = unsafeJso<Game>()
        val camping = unsafeJso<dynamic> {
            watchSlots = arrayOf(
                "Actor.valid",
                123,  // number - unexpected
                unsafeJso<dynamic> { foo = "bar" },  // object - unexpected
                null,  // null - unexpected
                "Actor.alsoValid"
            )
        }

        Migration26().migrateCamping(gameMock, camping)

        val slots = camping.watchSlots.unsafeCast<Array<Array<String>>>()
        assertEquals(5, slots.size)
        assertEquals(listOf("Actor.valid"), slots[0].toList())
        assertEquals(emptyList(), slots[1].toList())  // number dropped
        assertEquals(emptyList(), slots[2].toList())  // object dropped
        assertEquals(emptyList(), slots[3].toList())  // null dropped
        assertEquals(listOf("Actor.alsoValid"), slots[4].toList())
    }
}
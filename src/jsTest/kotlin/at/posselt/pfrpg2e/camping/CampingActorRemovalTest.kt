package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.fixtures.installFoundryGlobals
import js.objects.Object
import js.objects.Record
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What removing a camper actually writes.
 *
 * Asserted on the update PAYLOAD rather than through a faked Foundry document on purpose. Foundry
 * interprets the delete marker inside its own update pipeline, and a hand-rolled imitation of that
 * would be a fixture that can pass while production fails -- the exact failure mode these tests
 * exist to rule out. The payload is the contract this module owns, and the whole class of defect
 * here is a field the removal FORGETS, which is visible in the payload and nowhere else.
 */
class CampingActorRemovalTest {
    @BeforeTest
    fun installGlobals() {
        installFoundryGlobals()
    }

    private fun keysOf(record: Record<String, Any?>) = Object.keys(record).toList()

    /** A camp holding two campers, one of whom is on watch and has an activity and a meal. */
    private fun campWithTwo(): CampingData = js(
        """({
            actorUuids: ["Actor.leaver", "Actor.stays"],
            campingConditions: [],
            campingActivities: {
                act1: { actorUuid: "Actor.leaver", result: "success" },
                act2: { actorUuid: "Actor.stays", result: null }
            },
            cooking: { actorMeals: { leaver: { actorUuid: "Actor.leaver" }, stays: { actorUuid: "Actor.stays" } } },
            watchSlots: [["Actor.leaver", "Actor.stays"], ["Actor.leaver"], []],
            actorUuidsNotKeepingWatch: ["Actor.leaver", "Actor.stays"]
        })"""
    ).unsafeCast<CampingData>()

    @Test
    fun theLeaverIsTakenOffEveryWatchSlot() {
        val update = campingActorRemovalUpdate(campWithTwo(), "Actor.leaver", "leaver")
        val slots = update["watchSlots"].unsafeCast<Array<Array<String>>>()
        assertEquals(3, slots.size, "the slot structure must be preserved, not compacted")
        assertTrue(
            slots.none { slot -> slot.any { it == "Actor.leaver" } },
            "a camper who has left must not still be standing watch",
        )
        assertEquals(listOf("Actor.stays"), slots[0].toList(), "the camper who stayed keeps their watch")
        assertTrue(slots[1].isEmpty(), "a slot the leaver alone held becomes empty, not staffed")
    }

    @Test
    fun theLeaverIsDroppedFromTheNotKeepingWatchList() {
        val update = campingActorRemovalUpdate(campWithTwo(), "Actor.leaver", "leaver")
        assertEquals(
            listOf("Actor.stays"),
            update["actorUuidsNotKeepingWatch"].unsafeCast<Array<String>>().toList(),
        )
    }

    @Test
    fun theLeaverIsDroppedFromTheRoster() {
        val update = campingActorRemovalUpdate(campWithTwo(), "Actor.leaver", "leaver")
        assertEquals(listOf("Actor.stays"), update["actorUuids"].unsafeCast<Array<String>>().toList())
    }

    @Test
    fun onlyTheLeaversActivitiesAndMealAreDeleted() {
        val update = campingActorRemovalUpdate(campWithTwo(), "Actor.leaver", "leaver")
        val keys = keysOf(update)
        assertContains(keys, "campingActivities.act1")
        assertTrue(keys.none { it == "campingActivities.act2" }, "the other camper's activity must survive")
        assertContains(keys, "cooking.actorMeals.leaver")
        assertTrue(keys.none { it == "cooking.actorMeals.stays" }, "the other camper's meal must survive")
    }

    @Test
    fun theRemovalWritesNothingItWasNotAskedTo() {
        // a narrow write: it must not send `cooking`, or it clobbers every sibling field
        val keys = keysOf(campingActorRemovalUpdate(campWithTwo(), "Actor.leaver", "leaver"))
        assertTrue(keys.none { it == "cooking" }, "must not write the whole cooking object")
        assertEquals(
            setOf(
                "campingActivities.act1",
                "actorUuids",
                "cooking.actorMeals.leaver",
                "watchSlots",
                "actorUuidsNotKeepingWatch",
            ),
            keys.toSet(),
        )
    }

    @Test
    fun removingSomeoneWhoWasNeverInCampChangesNoRoster() {
        val update = campingActorRemovalUpdate(campWithTwo(), "Actor.ghost", "ghost")
        assertEquals(
            listOf("Actor.leaver", "Actor.stays"),
            update["actorUuids"].unsafeCast<Array<String>>().toList(),
        )
        val slots = update["watchSlots"].unsafeCast<Array<Array<String>>>()
        assertEquals(listOf("Actor.leaver", "Actor.stays"), slots[0].toList())
    }
}

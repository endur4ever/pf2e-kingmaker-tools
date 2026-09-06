package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.utils.asSequence
import js.array.component1
import js.array.component2
import js.array.component1
import js.array.component2
import js.objects.Record

/**
 * The partial update that removes a camper, as a plain record of path -> value.
 *
 * Kept in its OWN file, away from CampingActors.kt, deliberately. That file's top-level properties
 * reference Foundry document classes, and Kotlin/JS runs a file's initializers the first time
 * anything in it is touched -- so a test calling this would die on a Foundry reference before
 * reaching the code under test. Here it depends on nothing but the camping data and the update
 * builder, which is what makes it testable at all.
 *
 * The whole class of defect this guards is a field the removal FORGETS. Watches were forgotten
 * exactly that way: a departed camper stayed in watchSlots, so the slot read as staffed, the
 * "nobody is on watch" warning never fired, and the ambush Perception roll hunted an actor who was
 * no longer in camp. That is visible only in the update payload, which is what the tests assert on.
 */
fun campingActorRemovalUpdate(
    camping: CampingData,
    actorUuid: String,
    actorId: String,
    beforeSave: BeforeSave = {},
): Record<String, Any?> = buildCampingUpdate {
    removeCamper(camping, actorUuid, actorId)
    beforeSave(camping)
}

internal fun CampingUpdateBuilder.removeCamper(camping: CampingData, actorUuid: String, actorId: String) {
    val ids = camping.campingActivities.asSequence()
        .filter { it.component2().actorUuid == actorUuid }
        .map { it.component1() }
        .toSet()
    campingActivities.deleteEntries(ids)
    actorUuids.set(camping.actorUuids.filter { id -> id != actorUuid }.toTypedArray())
    cooking.actorMeals.deleteEntry(actorId)
    watchSlots.set(
        camping.watchSlots
            .map { slot -> slot.filter { it != actorUuid }.toTypedArray() }
            .toTypedArray()
    )
    actorUuidsNotKeepingWatch.set(
        camping.actorUuidsNotKeepingWatch.filter { it != actorUuid }.toTypedArray()
    )
}

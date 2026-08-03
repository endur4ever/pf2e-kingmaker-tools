package at.posselt.pfrpg2e.camping

import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the persistent per-actor downtime budget on [CampingData]: rolled hours are spent
 * per roll, accumulate, and are never refunded; no-check activities charge on assignment and
 * refund when unassigned or when the activity changes hands. Everything clamps to [0, 8] hours.
 */
class CampingDowntimeHoursTest {

    private fun emptyCamping(): CampingData = unsafeJso {}

    @Test
    fun remainingIsFullWhenNothingSpent() {
        val camping = emptyCamping()
        assertEquals(8, camping.downtimeHoursRemaining("actor-1"))
    }

    @Test
    fun eachRollSpendsTwoHoursAndAccumulates() {
        val camping = emptyCamping()
        camping.spendDowntimeHours("actor-1", 2)
        assertEquals(6, camping.downtimeHoursRemaining("actor-1"))
        // Re-rolling the same activity keeps spending: three rolls total = 6h spent, 2h left.
        camping.spendDowntimeHours("actor-1", 2)
        camping.spendDowntimeHours("actor-1", 2)
        assertEquals(2, camping.downtimeHoursRemaining("actor-1"))
    }

    @Test
    fun spendingIsTrackedPerActor() {
        val camping = emptyCamping()
        camping.spendDowntimeHours("actor-1", 2)
        assertEquals(6, camping.downtimeHoursRemaining("actor-1"))
        assertEquals(8, camping.downtimeHoursRemaining("actor-2"))
    }

    @Test
    fun remainingClampsAtZeroAndNeverGoesNegative() {
        val camping = emptyCamping()
        repeat(5) { camping.spendDowntimeHours("actor-1", 2) } // 10h spent
        assertEquals(0, camping.downtimeHoursRemaining("actor-1"))
    }

    @Test
    fun spendAndReadAreConsistentForDottedUuids() {
        // Actor UUIDs contain dots; spend/read must use the same sanitized key.
        val camping = emptyCamping()
        val uuid = "Scene.abc123.Token.def456.Actor.ghi789"
        camping.spendDowntimeHours(uuid, 2)
        camping.spendDowntimeHours(uuid, 2)
        assertEquals(4, camping.downtimeHoursRemaining(uuid))
    }

    @Test
    fun resetZeroesAllSpentHours() {
        val camping = emptyCamping()
        camping.spendDowntimeHours("actor-1", 4)
        camping.spendDowntimeHours("actor-2", 2)
        camping.resetDowntimeHours()
        assertEquals(8, camping.downtimeHoursRemaining("actor-1"))
        assertEquals(8, camping.downtimeHoursRemaining("actor-2"))
    }

    private fun testActivity(id: String, requiresCheck: Boolean): CampingActivityData = unsafeJso {
        this.id = id
        skills = if (requiresCheck) {
            arrayOf(CampingSkill(
                name = "survival",
                proficiency = "trained",
                dcType = "zone",
            ))
        } else {
            emptyArray()
        }
    }

    private fun assignActivity(camping: CampingData, actorUuid: String, activity: CampingActivityData) {
        val existing = camping.campingActivities[activity.id]
        if (existing == null) {
            camping.campingActivities[activity.id] = CampingActivity(
                actorUuid = actorUuid,
                selectedSkill = null,
            )
        } else {
            existing.actorUuid = actorUuid
            existing.result = null
        }
        if (!activity.requiresACheck()) {
            camping.spendDowntimeHours(actorUuid, 2)
        }
    }

    @Test
    fun assigningNoRollActivityDecrementsBudgetByTwoHours() {
        val camping = emptyCamping()
        camping.campingActivities = js.objects.recordOf()
        val activity = testActivity("no-roll-activity", requiresCheck = false)

        assertEquals(8, camping.downtimeHoursRemaining("actor-1"))
        assignActivity(camping, "actor-1", activity)
        assertEquals(6, camping.downtimeHoursRemaining("actor-1"))
    }

    @Test
    fun budgetCanBeExhaustedByNoRollActivities() {
        val camping = emptyCamping()
        camping.campingActivities = js.objects.recordOf()
        
        val activity1 = testActivity("no-roll-1", requiresCheck = false)
        val activity2 = testActivity("no-roll-2", requiresCheck = false)
        val activity3 = testActivity("no-roll-3", requiresCheck = false)
        val activity4 = testActivity("no-roll-4", requiresCheck = false)

        assignActivity(camping, "actor-1", activity1)
        assignActivity(camping, "actor-1", activity2)
        assignActivity(camping, "actor-1", activity3)
        assignActivity(camping, "actor-1", activity4)

        assertEquals(0, camping.downtimeHoursRemaining("actor-1"))
    }

    @Test
    fun rolledActivitiesDoNotSpendDowntimeOnAssignment() {
        val camping = emptyCamping()
        camping.campingActivities = js.objects.recordOf()
        val activity = testActivity("rolled-activity", requiresCheck = true)

        assertEquals(8, camping.downtimeHoursRemaining("actor-1"))
        assignActivity(camping, "actor-1", activity)
        assertEquals(8, camping.downtimeHoursRemaining("actor-1"))
    }

    @Test
    fun refundRestoresSpentHoursAndClampsAtZero() {
        val camping = emptyCamping()
        camping.spendDowntimeHours("actor-1", 2)
        assertEquals(6, camping.downtimeHoursRemaining("actor-1"))
        camping.refundDowntimeHours("actor-1", 2)
        assertEquals(8, camping.downtimeHoursRemaining("actor-1"))
        // Refunding more than was spent must not push remaining past the maximum.
        camping.refundDowntimeHours("actor-1", 2)
        assertEquals(8, camping.downtimeHoursRemaining("actor-1"))
    }

    @Test
    fun refundUsesSameSanitizedKeyAsSpendForDottedUuids() {
        val camping = emptyCamping()
        val uuid = "Scene.abc123.Token.def456.Actor.ghi789"
        camping.spendDowntimeHours(uuid, 2)
        camping.refundDowntimeHours(uuid, 2)
        assertEquals(8, camping.downtimeHoursRemaining(uuid))
    }

    @Test
    fun moveChargeChargesTheNewActorOnFirstAssignment() {
        val camping = emptyCamping()
        camping.downtimeHoursSpent = moveNoCheckDowntimeCharge(camping.downtimeHoursSpent, null, "actor-1")
        assertEquals(6, camping.downtimeHoursRemaining("actor-1"))
    }

    @Test
    fun moveChargeMovesHoursWhenTheActivityChangesHands() {
        val camping = emptyCamping()
        camping.downtimeHoursSpent = moveNoCheckDowntimeCharge(camping.downtimeHoursSpent, null, "actor-1")
        camping.downtimeHoursSpent = moveNoCheckDowntimeCharge(camping.downtimeHoursSpent, "actor-1", "actor-2")
        assertEquals(8, camping.downtimeHoursRemaining("actor-1"))
        assertEquals(6, camping.downtimeHoursRemaining("actor-2"))
    }

    @Test
    fun moveChargeIsANoOpForSameActorReassignment() {
        val camping = emptyCamping()
        camping.downtimeHoursSpent = moveNoCheckDowntimeCharge(camping.downtimeHoursSpent, null, "actor-1")
        camping.downtimeHoursSpent = moveNoCheckDowntimeCharge(camping.downtimeHoursSpent, "actor-1", "actor-1")
        assertEquals(6, camping.downtimeHoursRemaining("actor-1"))
    }

    @Test
    fun clearingANoRollAssignmentRefundsItsTwoHours() {
        val camping = emptyCamping()
        camping.campingActivities = js.objects.recordOf()
        val activity = testActivity("no-roll-activity", requiresCheck = false)
        assignActivity(camping, "actor-1", activity)
        assertEquals(6, camping.downtimeHoursRemaining("actor-1"))

        // Mirrors CampingSheet.clearActivity: no-check activities refund on unassign.
        if (!activity.requiresACheck()) {
            camping.refundDowntimeHours("actor-1", CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY)
        }
        camping.campingActivities[activity.id]?.actorUuid = null
        assertEquals(8, camping.downtimeHoursRemaining("actor-1"))
    }

    @Test
    fun repetitionsDefaultToOneForOlderSaves() {
        val activity = CampingActivity(actorUuid = "actor-1", selectedSkill = null)
        assertEquals(1, activity.repetitionsOrDefault())
        activity.repetitions = 3
        assertEquals(3, activity.repetitionsOrDefault())
    }

    @Test
    fun performingANoRollActivityThreeTimesCostsSixHours() {
        // Mirrors CampingSheet.repeatActivity: each press adds a repetition and spends 2h.
        val camping = emptyCamping()
        val activity = CampingActivity(actorUuid = "actor-1", selectedSkill = null, repetitions = 1)
        camping.spendDowntimeHours("actor-1", 2) // initial drop
        repeat(2) {
            activity.repetitions = activity.repetitionsOrDefault() + 1
            camping.spendDowntimeHours("actor-1", CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY)
        }
        assertEquals(3, activity.repetitionsOrDefault())
        assertEquals(2, camping.downtimeHoursRemaining("actor-1"))
    }

    @Test
    fun removingARepetitionRefundsItsTwoHours() {
        // Mirrors CampingSheet.removeActivityRepetition: one step back, one refund;
        // the last repetition unassigns the actor.
        val camping = emptyCamping()
        val activity = CampingActivity(actorUuid = "actor-1", selectedSkill = null, repetitions = 2)
        camping.spendDowntimeHours("actor-1", 4)

        camping.refundDowntimeHours("actor-1", CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY)
        activity.repetitions = activity.repetitionsOrDefault() - 1
        assertEquals(6, camping.downtimeHoursRemaining("actor-1"))
        assertEquals(1, activity.repetitionsOrDefault())

        camping.refundDowntimeHours("actor-1", CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY)
        if (activity.repetitionsOrDefault() - 1 <= 0) {
            activity.actorUuid = null
            activity.repetitions = null
        }
        assertEquals(8, camping.downtimeHoursRemaining("actor-1"))
        assertEquals(null, activity.actorUuid)
    }

    @Test
    fun moveChargeRefundsAllRepetitionsWhenChangingHands() {
        // actor-1 performed the activity three times (6h); handing it to actor-2 refunds
        // all of it and charges actor-2 a single repetition.
        val camping = emptyCamping()
        camping.spendDowntimeHours("actor-1", 6)
        camping.downtimeHoursSpent = moveNoCheckDowntimeCharge(
            spent = camping.downtimeHoursSpent,
            previousActorUuid = "actor-1",
            newActorUuid = "actor-2",
            refundHours = 6,
        )
        assertEquals(8, camping.downtimeHoursRemaining("actor-1"))
        assertEquals(6, camping.downtimeHoursRemaining("actor-2"))
    }
}

package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NightlyFeedingTest {
    private fun cooked(uuid: String) = NightlyMealChoice(uuid, NightlyMealKind.COOKED_MEAL)
    private fun rations(uuid: String, cost: Int = 1) = NightlyMealChoice(uuid, NightlyMealKind.RATIONS, cost)
    private fun nothing(uuid: String) = NightlyMealChoice(uuid, NightlyMealKind.NOTHING)

    @Test
    fun aCookedMealFeedsRegardlessOfTheRationPool() {
        val r = resolveNightlyFeeding(listOf(cooked("a"), cooked("b")), availableRations = 0)
        assertEquals(listOf("a", "b"), r.fed)
        assertTrue(r.mustSubsist.isEmpty())
        assertTrue(r.unfed.isEmpty())
    }

    @Test
    fun skippingTheMealGoesStraightToUnfedNotToSubsist() {
        // Skip Meal is a choice, not a shortage -- there is nothing to forage for, the actor simply
        // did not eat. Routing it through Subsist would hand out a free roll to opt out of hunger.
        val r = resolveNightlyFeeding(listOf(nothing("a")), availableRations = 99)
        assertEquals(listOf("a"), r.unfed)
        assertTrue(r.mustSubsist.isEmpty())
        assertTrue(r.fed.isEmpty())
    }

    @Test
    fun rationEatersAreFedWhileThePoolLasts() {
        val r = resolveNightlyFeeding(listOf(rations("a"), rations("b"), rations("c")), availableRations = 2)
        assertEquals(listOf("a", "b"), r.fed)
        assertEquals(listOf("c"), r.mustSubsist)
    }

    @Test
    fun anEmptyPoolSendsEveryRationEaterToSubsist() {
        // The card's headline case: nothing left in the packs, so everyone who was counting on
        // rations has to forage for the night.
        val r = resolveNightlyFeeding(listOf(rations("a"), rations("b")), availableRations = 0)
        assertEquals(listOf("a", "b"), r.mustSubsist)
        assertTrue(r.fed.isEmpty())
    }

    @Test
    fun aCamperTooExpensiveToFeedDoesNotBlockACheaperOneBehindThem() {
        // Allocation is first-come in roster order, but running out for one camper must not
        // silently swallow the remaining pool.
        val r = resolveNightlyFeeding(listOf(rations("a", cost = 5), rations("b", cost = 1)), availableRations = 1)
        assertEquals(listOf("a"), r.mustSubsist)
        assertEquals(listOf("b"), r.fed)
    }

    @Test
    fun everyCamperLandsInExactlyOneBucket() {
        val meals = listOf(cooked("a"), rations("b"), rations("c"), nothing("d"))
        val r = resolveNightlyFeeding(meals, availableRations = 1)
        val all = r.fed + r.mustSubsist + r.unfed
        assertEquals(meals.size, all.size)
        assertEquals(meals.map { it.actorUuid }.toSet(), all.toSet())
    }

    @Test
    fun aNegativeOrZeroPoolIsTreatedAsEmptyRatherThanGoingIntoDebt() {
        val r = resolveNightlyFeeding(listOf(rations("a")), availableRations = -3)
        assertEquals(listOf("a"), r.mustSubsist)
    }

    @Test
    fun aFreeRationChoiceIsFedEvenFromAnEmptyPool() {
        // cost 0 must not be charged against an empty pool -- 0 <= 0 feeds.
        val r = resolveNightlyFeeding(listOf(rations("a", cost = 0)), availableRations = 0)
        assertEquals(listOf("a"), r.fed)
    }

    @Test
    fun nobodyInCampIsAValidQuietNight() {
        val r = resolveNightlyFeeding(emptyList(), availableRations = 10)
        assertTrue(r.fed.isEmpty() && r.mustSubsist.isEmpty() && r.unfed.isEmpty())
    }
}

/**
 * The "Consume Rations" button spends the pool before the nightly tick reads it. These pin the
 * day-stamp that stops the tick charging for the same rations a second time -- the first attempt
 * shipped a flag that was cleared before its only reader ran, and a green suite said nothing
 * because no test referenced it.
 */
class RationsAlreadyPaidTest {
    @Test
    fun unpaidWhenNoStamp() {
        assertFalse(rationsAlreadyPaidFor(paidDay = null, day = 12))
    }

    @Test
    fun paidOnTheStampedDay() {
        assertTrue(rationsAlreadyPaidFor(paidDay = 12, day = 12))
    }

    @Test
    fun aStampDoesNotPayForALaterNight() {
        // the party consumed rations and then broke camp without resting: the stamp survives,
        // and must NOT feed them free on a later night
        assertFalse(rationsAlreadyPaidFor(paidDay = 12, day = 13))
    }

    @Test
    fun aStampDoesNotPayBackwards() {
        assertFalse(rationsAlreadyPaidFor(paidDay = 13, day = 12))
    }

    @Test
    fun aPaidRationFeedsEvenWithAnEmptyPool() {
        // what the whole stamp is for: the button already drained the pool, so charging again
        // judged a party that had eaten as unfed
        val meals = listOf(
            NightlyMealChoice(actorUuid = "a", kind = NightlyMealKind.RATIONS, rationCost = 0),
            NightlyMealChoice(actorUuid = "b", kind = NightlyMealKind.RATIONS, rationCost = 0),
        )
        val feeding = resolveNightlyFeeding(meals, availableRations = 0)
        assertEquals(listOf("a", "b"), feeding.fed)
        assertTrue(feeding.mustSubsist.isEmpty())
    }

    @Test
    fun anUnpaidRationStillNeedsFoodOnHand() {
        val meals = listOf(NightlyMealChoice(actorUuid = "a", kind = NightlyMealKind.RATIONS, rationCost = 1))
        val feeding = resolveNightlyFeeding(meals, availableRations = 0)
        assertEquals(listOf("a"), feeding.mustSubsist)
        assertTrue(feeding.fed.isEmpty())
    }
}

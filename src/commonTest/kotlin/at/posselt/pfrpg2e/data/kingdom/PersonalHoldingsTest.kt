package at.posselt.pfrpg2e.data.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalHoldingsTest {
    @Test
    fun incomeScalesWithLevelAndCapsAtTwenty() {
        assertEquals(HoldingIncome(2, 0, 0), holdingIncome(HoldingTier.MODEST, 1))
        assertEquals(HoldingIncome(50, 0, 1), holdingIncome(HoldingTier.COMFORTABLE, 10))
        assertEquals(HoldingIncome(200, 1, 2), holdingIncome(HoldingTier.LAVISH, 20))
        assertEquals(holdingIncome(HoldingTier.LAVISH, 20), holdingIncome(HoldingTier.LAVISH, 35),
            "level caps at 20")
        assertEquals(holdingIncome(HoldingTier.MODEST, 1), holdingIncome(HoldingTier.MODEST, 0),
            "a level below 1 floors to 1 rather than zeroing income")
    }

    @Test
    fun damageHalvesGoldButTheTitleKeepsItsFavors() {
        val damaged = conditionAdjustedIncome(HoldingTier.LAVISH, 10, HoldingCondition.DAMAGED)
        assertEquals(50, damaged.gold, "10 gp/level x 10 halved")
        assertEquals(0, damaged.luxuries, "1 luxury halved floors to 0")
        assertEquals(2, damaged.favors, "the neighbours still owe the family")
        assertEquals(HoldingIncome.ZERO, conditionAdjustedIncome(HoldingTier.LAVISH, 10, HoldingCondition.DESTROYED))
    }

    @Test
    fun theDamageLadderIsOneWayUntilRepaired() {
        assertEquals(HoldingCondition.DAMAGED,
            nextConditionAfterDamage(HoldingCondition.SOUND, DamageSeverity.MINOR))
        assertEquals(HoldingCondition.DESTROYED,
            nextConditionAfterDamage(HoldingCondition.DAMAGED, DamageSeverity.MINOR),
            "a second minor blow finishes the job")
        assertEquals(HoldingCondition.DESTROYED,
            nextConditionAfterDamage(HoldingCondition.SOUND, DamageSeverity.MAJOR),
            "major damage skips straight to rubble")
        assertEquals(HoldingCondition.DESTROYED,
            nextConditionAfterDamage(HoldingCondition.DESTROYED, DamageSeverity.MINOR),
            "rubble stays rubble")
    }

    @Test
    fun repairCostsMatchTheTableAndAreLevelIndependent() {
        assertEquals(0, repairCost(HoldingTier.LAVISH, HoldingCondition.SOUND))
        assertEquals(25, repairCost(HoldingTier.MODEST, HoldingCondition.DAMAGED))
        assertEquals(300, repairCost(HoldingTier.COMFORTABLE, HoldingCondition.DESTROYED))
        assertEquals(600, repairCost(HoldingTier.LAVISH, HoldingCondition.DESTROYED))
    }

    @Test
    fun aRebuildIsTwoPaidRepairs() {
        val step1 = repairedCondition(HoldingCondition.DESTROYED)
        assertEquals(HoldingCondition.DAMAGED, step1)
        assertEquals(HoldingCondition.SOUND, repairedCondition(step1))
        assertEquals(HoldingCondition.SOUND, repairedCondition(HoldingCondition.SOUND))
    }

    @Test
    fun incomeSumsAndZeroReadsEmpty() {
        assertTrue(HoldingIncome.ZERO.isEmpty)
        val sum = HoldingIncome(10, 1, 0) + HoldingIncome(5, 0, 2)
        assertEquals(HoldingIncome(15, 1, 2), sum)
        assertTrue(!sum.isEmpty)
    }

    @Test
    fun enumsRoundTripAndRejectStrangers() {
        assertEquals(HoldingTier.LAVISH, HoldingTier.fromValue(3))
        assertNull(HoldingTier.fromValue(7))
        assertNull(HoldingTier.fromValue(null))
        assertEquals(HoldingCondition.DAMAGED, HoldingCondition.fromValue("damaged"))
        assertNull(HoldingCondition.fromValue("scorched"))
    }

    @Test
    fun everyDamagingEventIdIsSlugCased() {
        // the map is keyed by data/events id slugs; a Title-Case name would never match
        HOLDING_DAMAGING_EVENT_IDS.keys.forEach { id ->
            assertEquals(id, id.lowercase(), "event id '$id' must be a slug")
            assertTrue(" " !in id, "event id '$id' must not contain spaces")
        }
        assertEquals(DamageSeverity.MAJOR, HOLDING_DAMAGING_EVENT_IDS["undead-uprising"])
        assertEquals(DamageSeverity.MINOR, HOLDING_DAMAGING_EVENT_IDS["bandit-activity"])
    }
}

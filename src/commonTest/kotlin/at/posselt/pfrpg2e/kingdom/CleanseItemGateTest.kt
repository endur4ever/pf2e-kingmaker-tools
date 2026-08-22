package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers which settlements may host a Cleanse Item ritual. */
class CleanseItemGateTest {
    private fun settlement(name: String, vararg structures: String) =
        CleanseItemSettlement(id = name.lowercase(), name = name, structureNames = structures.toSet())

    @Test
    fun aShrineSatisfiesAShrineRequirement() {
        assertTrue(CleanseItemStructure.SHRINE.satisfiedBy(setOf("Shrine")))
    }

    @Test
    fun aBetterStructureSatisfiesALesserRequirement() {
        // Telling a kingdom that built a cathedral it cannot perform the simplest cleansing
        // would be nonsense.
        assertTrue(CleanseItemStructure.SHRINE.satisfiedBy(setOf("Cathedral")))
        assertTrue(CleanseItemStructure.TEMPLE.satisfiedBy(setOf("Cathedral")))
    }

    @Test
    fun aLesserStructureDoesNotSatisfyAGreaterRequirement() {
        assertFalse(CleanseItemStructure.CATHEDRAL.satisfiedBy(setOf("Shrine", "Temple")))
        assertFalse(CleanseItemStructure.TEMPLE.satisfiedBy(setOf("Shrine")))
    }

    @Test
    fun structureNamesMatchRegardlessOfCaseOrPadding() {
        // Structure names come from actor names a GM types, not from an enum.
        assertTrue(CleanseItemStructure.TEMPLE.satisfiedBy(setOf("  temple  ")))
    }

    @Test
    fun anUnrelatedStructureNeverQualifies() {
        assertFalse(CleanseItemStructure.SHRINE.satisfiedBy(setOf("Tavern", "Smithy")))
    }

    @Test
    fun aSettlementWithNothingBuiltNeverQualifies() {
        assertFalse(CleanseItemStructure.SHRINE.satisfiedBy(emptySet()))
    }

    @Test
    fun onlyQualifyingSettlementsAreOffered() {
        val settlements = listOf(
            settlement("Tuskwater", "Tavern"),
            settlement("Restov", "Temple"),
            settlement("Pitax", "Cathedral"),
        )
        assertEquals(
            listOf("Restov", "Pitax"),
            eligibleCleanseSettlements(settlements, CleanseItemStructure.TEMPLE).map { it.name },
        )
    }

    @Test
    fun noQualifyingSettlementBlocksTheActivity() {
        val settlements = listOf(settlement("Tuskwater", "Shrine"))
        assertTrue(eligibleCleanseSettlements(settlements, CleanseItemStructure.CATHEDRAL).isEmpty())
    }

    @Test
    fun aLevelSixteenItemNeedsACathedral() {
        // Ties the gate to the cost table so the two cannot drift apart.
        assertEquals(CleanseItemStructure.CATHEDRAL, cleanseItemPlan(itemLevel = 16, kingdomLevel = 12).requiredStructure)
        assertEquals(CleanseItemStructure.SHRINE, cleanseItemPlan(itemLevel = 5, kingdomLevel = 12).requiredStructure)
    }
}

/** Covers what a Cleanse Item attempt actually costs, which differs from what it required. */
class CleanseItemChargeTest {
    @Test
    fun aCriticalSuccessConsumesHalfTheMaterials() {
        assertEquals(4, cleanseItemLuxuryCharge(8, at.posselt.pfrpg2e.data.checks.DegreeOfSuccess.CRITICAL_SUCCESS))
    }

    @Test
    fun everyOtherOutcomeConsumesTheLot() {
        // A failed cleansing still performed the ritual, so the materials are gone.
        listOf(
            at.posselt.pfrpg2e.data.checks.DegreeOfSuccess.SUCCESS,
            at.posselt.pfrpg2e.data.checks.DegreeOfSuccess.FAILURE,
            at.posselt.pfrpg2e.data.checks.DegreeOfSuccess.CRITICAL_FAILURE,
        ).forEach { assertEquals(8, cleanseItemLuxuryCharge(8, it), "wrong charge for $it") }
    }

    @Test
    fun halvingTheCheapestBandNeverMakesItFree() {
        assertEquals(1, cleanseItemLuxuryCharge(1, at.posselt.pfrpg2e.data.checks.DegreeOfSuccess.CRITICAL_SUCCESS))
    }

    @Test
    fun theChargeTracksTheBandFromThePlan() {
        val plan = cleanseItemPlan(itemLevel = 12, kingdomLevel = 10)
        assertEquals(4, plan.luxuryCost)
        assertEquals(2, cleanseItemLuxuryCharge(plan.luxuryCost, at.posselt.pfrpg2e.data.checks.DegreeOfSuccess.CRITICAL_SUCCESS))
    }
}

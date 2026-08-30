package at.posselt.pfrpg2e.kingdom.npcmemory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the cases §10 of the npc-memory plan enumerates. */
class NpcMemoryTest {
    private fun facts(
        turn: Int = 1,
        unrest: Int = 0,
        ruinCorruption: Int = 0,
        ruinCrime: Int = 0,
        ruinDecay: Int = 0,
        ruinStrife: Int = 0,
        size: Int = 1,
        level: Int = 1,
        fame: Int = 0,
        fameMax: Int = 3,
        warPressure: Int? = null,
        consumption: Int = 0,
        resourcePoints: Int = 0,
        clockEventIds: Set<String> = emptySet(),
        shipmentOutcomes: List<String> = emptyList(),
        milestonesEarnedThisTurn: Int = 0,
    ) = KingdomTurnFacts(
        turn = turn, unrest = unrest, ruinCorruption = ruinCorruption, ruinCrime = ruinCrime,
        ruinDecay = ruinDecay, ruinStrife = ruinStrife, size = size, level = level, fame = fame,
        fameMax = fameMax, warPressure = warPressure, consumption = consumption,
        resourcePoints = resourcePoints, clockEventIds = clockEventIds,
        shipmentOutcomes = shipmentOutcomes, milestonesEarnedThisTurn = milestonesEarnedThisTurn,
    )

    private fun rule(
        id: String = "r1",
        trigger: MemoryTriggerKind = MemoryTriggerKind.UNREST_ROSE,
        threshold: Int = 0,
        ref: String? = null,
        deltas: Map<String, Int> = emptyMap(),
        defaultDelta: Int = 0,
        cooldownTurns: Int = 0,
    ) = MemoryRule(
        id = id, trigger = trigger, threshold = threshold, ref = ref, deltas = deltas,
        defaultDelta = defaultDelta, cooldownTurns = cooldownTurns,
    )

    @Test
    fun unrestRisingPastTheThresholdFiresWhileASteadyStateDoesNot() {
        // Rules match deltas between snapshots, never levels: unrest that was already 6 is not news.
        val spike = rule(trigger = MemoryTriggerKind.UNREST_ROSE, threshold = 3)
        assertTrue(ruleFires(spike, facts(unrest = 2), facts(unrest = 6)))
        assertFalse(ruleFires(spike, facts(unrest = 6), facts(unrest = 6)))
        // The threshold itself is inclusive; one short of it is not.
        assertTrue(ruleFires(spike, facts(unrest = 2), facts(unrest = 5)))
        assertFalse(ruleFires(spike, facts(unrest = 2), facts(unrest = 4)))
    }

    @Test
    fun anUnrestRoseThresholdBelowOneNeverFires() {
        // A threshold of 0 would be satisfied by every flat turn; such a rule is broken data, and
        // even a genuine spike must not rescue it into firing.
        assertFalse(ruleFires(rule(trigger = MemoryTriggerKind.UNREST_ROSE, threshold = 0), facts(unrest = 2), facts(unrest = 6)))
        assertFalse(ruleFires(rule(trigger = MemoryTriggerKind.UNREST_ROSE, threshold = -1), facts(unrest = 2), facts(unrest = 6)))
    }

    @Test
    fun unrestCalmedNeedsZeroNowFromAtLeastTheThresholdBefore() {
        val calmed = rule(trigger = MemoryTriggerKind.UNREST_CALMED, threshold = 5)
        assertTrue(ruleFires(calmed, facts(unrest = 5), facts(unrest = 0)))
        assertFalse(ruleFires(calmed, facts(unrest = 4), facts(unrest = 0)))
        assertFalse(ruleFires(calmed, facts(unrest = 5), facts(unrest = 1)))
    }

    @Test
    fun ruinWorsenedFiresOnlyWhenTheNamedTrackIncreases() {
        val decay = rule(trigger = MemoryTriggerKind.RUIN_WORSENED, ref = "decay")
        assertTrue(ruleFires(decay, facts(ruinDecay = 1), facts(ruinDecay = 2)))
        assertFalse(ruleFires(decay, facts(ruinDecay = 2), facts(ruinDecay = 2)))
        // A different track worsening is someone else's grievance.
        assertFalse(ruleFires(decay, facts(ruinCrime = 1), facts(ruinCrime = 3)))
    }

    @Test
    fun anUnknownOrMissingRuinRefIsFalseRatherThanAThrow() {
        // Rules are data files; one bad ref must not take down the whole tick.
        assertFalse(ruleFires(rule(trigger = MemoryTriggerKind.RUIN_WORSENED, ref = "hubris"), facts(), facts(ruinDecay = 3)))
        assertFalse(ruleFires(rule(trigger = MemoryTriggerKind.RUIN_WORSENED, ref = null), facts(), facts(ruinDecay = 3)))
    }

    @Test
    fun ruinsClearedNeedsAllFourZeroAndANonzeroPredecessor() {
        val cleared = rule(trigger = MemoryTriggerKind.RUINS_CLEARED)
        assertTrue(ruleFires(cleared, facts(ruinStrife = 2), facts()))
        // A kingdom that never had ruin has not cleared anything.
        assertFalse(ruleFires(cleared, facts(), facts()))
        // One lingering track means not cleared yet.
        assertFalse(ruleFires(cleared, facts(ruinStrife = 2), facts(ruinCrime = 1)))
    }

    @Test
    fun sizeAndLevelFireOnlyOnAStrictIncrease() {
        val grew = rule(trigger = MemoryTriggerKind.SIZE_INCREASED)
        assertTrue(ruleFires(grew, facts(size = 5), facts(size = 6)))
        assertFalse(ruleFires(grew, facts(size = 6), facts(size = 6)))
        val advanced = rule(trigger = MemoryTriggerKind.LEVEL_INCREASED)
        assertTrue(ruleFires(advanced, facts(level = 3), facts(level = 4)))
        assertFalse(ruleFires(advanced, facts(level = 4), facts(level = 4)))
        assertFalse(ruleFires(advanced, facts(level = 4), facts(level = 3)))
    }

    @Test
    fun fameAtMaxFiresOnReachingTheCapAndNotWhileSittingAtIt() {
        // Edge, not level: renown peaking is one event, not a standing condition to re-report.
        val peaked = rule(trigger = MemoryTriggerKind.FAME_AT_MAX)
        assertTrue(ruleFires(peaked, facts(fame = 2, fameMax = 3), facts(fame = 3, fameMax = 3)))
        assertFalse(ruleFires(peaked, facts(fame = 3, fameMax = 3), facts(fame = 3, fameMax = 3)))
        // A zero cap can never be "reached".
        assertFalse(ruleFires(peaked, facts(fame = 0, fameMax = 0), facts(fame = 0, fameMax = 0)))
    }

    @Test
    fun warPressureRoseTreatsAMissingValueAsZero() {
        // Turn records predating the war-pressure board carry null; a first pressure of 1 is a rise.
        val rose = rule(trigger = MemoryTriggerKind.WAR_PRESSURE_ROSE)
        assertTrue(ruleFires(rose, facts(warPressure = null), facts(warPressure = 1)))
        assertFalse(ruleFires(rose, facts(warPressure = 2), facts(warPressure = 2)))
        assertFalse(ruleFires(rose, facts(warPressure = null), facts(warPressure = null)))
    }

    @Test
    fun warPressureRelievedFiresOnlyOnTheDropToZero() {
        val relieved = rule(trigger = MemoryTriggerKind.WAR_PRESSURE_RELIEVED)
        assertTrue(ruleFires(relieved, facts(warPressure = 3), facts(warPressure = 0)))
        assertTrue(ruleFires(relieved, facts(warPressure = 3), facts(warPressure = null)))
        assertFalse(ruleFires(relieved, facts(warPressure = 3), facts(warPressure = 1)))
        assertFalse(ruleFires(relieved, facts(warPressure = 0), facts(warPressure = 0)))
    }

    @Test
    fun aLeanYearIsConsumptionExceedingResourcePointsThisTurn() {
        val lean = rule(trigger = MemoryTriggerKind.LEAN_YEAR)
        assertTrue(ruleFires(lean, facts(), facts(consumption = 5, resourcePoints = 4)))
        // Breaking even is not lean.
        assertFalse(ruleFires(lean, facts(), facts(consumption = 5, resourcePoints = 5)))
    }

    @Test
    fun clockFiredMatchesItsRefAgainstThisTurnsClockEvents() {
        val clock = rule(trigger = MemoryTriggerKind.CLOCK_FIRED, ref = "vordakai-stirs")
        assertTrue(ruleFires(clock, facts(), facts(clockEventIds = setOf("vordakai-stirs"))))
        assertFalse(ruleFires(clock, facts(), facts(clockEventIds = setOf("other-clock"))))
        val noRef = rule(trigger = MemoryTriggerKind.CLOCK_FIRED, ref = null)
        assertFalse(ruleFires(noRef, facts(), facts(clockEventIds = setOf("vordakai-stirs"))))
    }

    @Test
    fun shipmentOutcomeMatchesItsRefAgainstThisTurnsOutcomes() {
        val raided = rule(trigger = MemoryTriggerKind.SHIPMENT_OUTCOME, ref = "raided")
        assertTrue(ruleFires(raided, facts(), facts(shipmentOutcomes = listOf("delivered", "raided"))))
        assertFalse(ruleFires(raided, facts(), facts(shipmentOutcomes = listOf("delivered"))))
        val noRef = rule(trigger = MemoryTriggerKind.SHIPMENT_OUTCOME, ref = null)
        assertFalse(ruleFires(noRef, facts(), facts(shipmentOutcomes = listOf("raided"))))
    }

    @Test
    fun milestoneEarnedFiresOnAnyPositiveCountThisTurn() {
        val milestone = rule(trigger = MemoryTriggerKind.MILESTONE_EARNED)
        assertTrue(ruleFires(milestone, facts(), facts(milestonesEarnedThisTurn = 1)))
        assertFalse(ruleFires(milestone, facts(), facts(milestonesEarnedThisTurn = 0)))
    }

    @Test
    fun anOccupationAbsentFromTheDeltasGetsTheDefaultDelta() {
        // Most residents are unmoved by most events: absent means default, never the first entry.
        val r = rule(deltas = mapOf("Merchant" to -2, "Soldier" to 1), defaultDelta = 0)
        assertEquals(0, deltaFor(r, "Farmer"))
        assertEquals(-2, deltaFor(r, "Merchant"))
    }

    @Test
    fun occupationMatchingIgnoresCaseAndPadding() {
        // Occupations are typed by a GM; "  merchant " must not read as a stranger to "Merchant".
        val r = rule(deltas = mapOf("Merchant" to -2), defaultDelta = 9)
        assertEquals(-2, deltaFor(r, "  merchant "))
        assertEquals(-2, deltaFor(r, "MERCHANT"))
    }

    @Test
    fun cooldownRequiresStrictlyMoreTurnsThanItsLengthToHavePassed() {
        assertTrue(cooldownAllows(lastFiredTurn = null, currentTurn = 5, cooldownTurns = 3))
        assertFalse(cooldownAllows(lastFiredTurn = 5, currentTurn = 6, cooldownTurns = 1))
        assertTrue(cooldownAllows(lastFiredTurn = 5, currentTurn = 7, cooldownTurns = 1))
    }

    @Test
    fun oneNpcsCooldownNeverSuppressesAnothers() {
        // The lastFired map is per NPC: the merchant on cooldown must not silence the teamster.
        val looms = rule(id = "war-looms", trigger = MemoryTriggerKind.WAR_PRESSURE_ROSE, cooldownTurns = 1)
        val prev = facts(turn = 5, warPressure = 1)
        val curr = facts(turn = 6, warPressure = 2)
        val onCooldown = evaluateMemoryRules(listOf(looms), prev, curr, mapOf("war-looms" to 5), "Merchant")
        assertTrue(onCooldown.isEmpty())
        val freshNpc = evaluateMemoryRules(listOf(looms), prev, curr, emptyMap(), "Teamster")
        assertEquals(1, freshNpc.size)
    }

    @Test
    fun evaluateResolvesDeltasThroughTheNpcsOccupation() {
        val raided = rule(
            id = "caravan-raided", trigger = MemoryTriggerKind.SHIPMENT_OUTCOME, ref = "raided",
            deltas = mapOf("Merchant" to -2, "Soldier" to 1), defaultDelta = 0,
        )
        val prev = facts(turn = 2)
        val curr = facts(turn = 3, shipmentOutcomes = listOf("raided"))
        assertEquals(-2, evaluateMemoryRules(listOf(raided), prev, curr, emptyMap(), "Merchant").single().delta)
        assertEquals(1, evaluateMemoryRules(listOf(raided), prev, curr, emptyMap(), "Soldier").single().delta)
        assertEquals(0, evaluateMemoryRules(listOf(raided), prev, curr, emptyMap(), "Farmer").single().delta)
    }

    @Test
    fun aRuleListedTwiceFiresAtMostOncePerTurn() {
        val milestone = rule(id = "milestone-earned", trigger = MemoryTriggerKind.MILESTONE_EARNED)
        val out = evaluateMemoryRules(
            listOf(milestone, milestone), facts(turn = 1), facts(turn = 2, milestonesEarnedThisTurn = 1),
            emptyMap(), "Priest",
        )
        assertEquals(1, out.size)
    }

    @Test
    fun aRuleThatDoesNotMatchProducesNoFiring() {
        val grew = rule(trigger = MemoryTriggerKind.SIZE_INCREASED)
        assertTrue(evaluateMemoryRules(listOf(grew), facts(size = 4), facts(size = 4), emptyMap(), "Farmer").isEmpty())
    }

    @Test
    fun attitudeClampsAtBothExtremes() {
        assertEquals(MAX_NPC_ATTITUDE, clampAttitude(50))
        assertEquals(MAX_NPC_ATTITUDE, clampAttitude(51))
        assertEquals(MIN_NPC_ATTITUDE, clampAttitude(-50))
        assertEquals(MIN_NPC_ATTITUDE, clampAttitude(-51))
        assertEquals(7, clampAttitude(7))
    }

    @Test
    fun decayMovesTowardZeroFromBothSignsOnlyOnMemoryFreeTurns() {
        assertEquals(9, decayAttitude(10, formedMemoryThisTurn = false))
        assertEquals(-9, decayAttitude(-10, formedMemoryThisTurn = false))
        // A turn that formed a memory renews the grudge instead of fading it.
        assertEquals(10, decayAttitude(10, formedMemoryThisTurn = true))
        assertEquals(-10, decayAttitude(-10, formedMemoryThisTurn = true))
    }

    @Test
    fun decayNeverCrossesZeroAndZeroStaysZero() {
        assertEquals(0, decayAttitude(1, formedMemoryThisTurn = false))
        assertEquals(0, decayAttitude(-1, formedMemoryThisTurn = false))
        assertEquals(0, decayAttitude(0, formedMemoryThisTurn = false))
    }

    @Test
    fun bandBoundariesAreExactOnBothSides() {
        assertEquals(AttitudeBand.HOSTILE, attitudeBand(-25))
        assertEquals(AttitudeBand.UNFRIENDLY, attitudeBand(-24))
        assertEquals(AttitudeBand.UNFRIENDLY, attitudeBand(-10))
        assertEquals(AttitudeBand.INDIFFERENT, attitudeBand(-9))
        assertEquals(AttitudeBand.INDIFFERENT, attitudeBand(9))
        assertEquals(AttitudeBand.FRIENDLY, attitudeBand(10))
        assertEquals(AttitudeBand.FRIENDLY, attitudeBand(24))
        assertEquals(AttitudeBand.HELPFUL, attitudeBand(25))
    }

    @Test
    fun crossedBandIsNullWithinABandAndTheNewBandOnCrossing() {
        assertNull(crossedBand(0, 5))
        assertEquals(AttitudeBand.FRIENDLY, crossedBand(9, 10))
        assertEquals(AttitudeBand.HOSTILE, crossedBand(-24, -25))
        // Edge-triggered: an NPC sitting at hostile must not re-offer every turn.
        assertNull(crossedBand(-25, -30))
    }

    @Test
    fun appendMemoryEntryTrimsTheOldestAtTheCap() {
        // Trimming touches the log only -- attitude is a running total the trim must never revise.
        val full = (1..MEMORY_LOG_CAP).map { NpcMemoryEntry(ruleId = "e$it", turn = it, delta = 1) }
        val out = appendMemoryEntry(full, NpcMemoryEntry(ruleId = "new", turn = 99, delta = -1))
        assertEquals(MEMORY_LOG_CAP, out.size)
        assertEquals("e2", out.first().ruleId)
        assertEquals("new", out.last().ruleId)
    }

    @Test
    fun appendBelowTheCapKeepsEveryEntry() {
        val some = (1..3).map { NpcMemoryEntry(ruleId = "e$it", turn = it, delta = 1) }
        val out = appendMemoryEntry(some, NpcMemoryEntry(ruleId = "new", turn = 4, delta = 1))
        assertEquals(4, out.size)
        assertEquals("e1", out.first().ruleId)
    }

    @Test
    fun canTrackAnotherRefusesTheEleventh() {
        assertTrue(canTrackAnother(9))
        assertFalse(canTrackAnother(10))
    }

    @Test
    fun unknownStoredValuesMapToNullRatherThanThrowing() {
        assertNull(MemoryTriggerKind.fromValue("forestRazed"))
        assertNull(AttitudeBand.fromValue("furious"))
        assertEquals(MemoryTriggerKind.UNREST_ROSE, MemoryTriggerKind.fromValue("unrestRose"))
        assertEquals(AttitudeBand.HELPFUL, AttitudeBand.fromValue("helpful"))
    }

    @Test
    fun clockRulesMatchTheLABELtheTurnRecordStores() {
        // the tick writes clock EVENT LABELS into the record, not ids, so a rule keyed on an id
        // could never fire -- and the shipped clock-fired rule had a null ref besides
        val rule = MemoryRule(
            id = "clock-fired",
            trigger = MemoryTriggerKind.CLOCK_FIRED,
            ref = "Cult of the Bloom",
        )
        val prev = facts(turn = 1)
        val fired = facts(turn = 2, clockEventIds = setOf("Cult of the Bloom"))
        assertTrue(ruleFires(rule, prev, fired))
        // labels are GM-typed, so matching tolerates case and padding
        assertTrue(ruleFires(rule, prev, facts(turn = 2, clockEventIds = setOf("  cult of the bloom  "))))
        assertFalse(ruleFires(rule, prev, facts(turn = 2, clockEventIds = setOf("Some Other Clock"))))
        // a rule with no ref can never fire, which is why the shipped file must carry one
        assertFalse(ruleFires(rule.copy(ref = null), prev, fired))
    }
}

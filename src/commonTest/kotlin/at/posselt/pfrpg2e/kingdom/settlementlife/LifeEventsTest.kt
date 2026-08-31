package at.posselt.pfrpg2e.kingdom.settlementlife

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers the phase-1 pure-core slice of §7.1 of the settlement-life plan. */
class LifeEventsTest {
    private fun template(
        id: String = "t1",
        weight: Int = 1,
        requiresStructure: String? = null,
        requiresSeason: String? = null,
        minPopulation: Int = 0,
        hookKind: LifeEventHookKind = LifeEventHookKind.NONE,
        hookMagnitude: Int = 0,
        castOccupation: String? = null,
    ) = LifeEventTemplate(
        id = id,
        weight = weight,
        requiresStructures = listOfNotNull(requiresStructure),
        // a single required season reads as "every OTHER season weighs zero", which is how the
        // richer model expresses what requiresSeason used to say outright
        seasonWeights = requiresSeason?.let { wanted ->
            listOf("spring", "summer", "fall", "winter").associateWith { if (it == wanted) 1.0 else 0.0 }
        } ?: emptyMap(),
        minPopulation = minPopulation,
        castSlots = castOccupation?.let { listOf(Triple("slot", listOf(it), emptyList<String>())) }
            ?: emptyList(),
        hookKind = hookKind,
        hookMagnitude = hookMagnitude,
    )

    private fun member(id: String = "n1", name: String = "Svetlana Morozov", occupation: String = "Innkeeper") =
        RosterMember(id = id, name = name, occupation = occupation)

    @Test
    fun aTemplateRequiringNothingIsEligibleEverywhere() {
        val open = template(id = "open")
        assertEquals(listOf(open), eligibleTemplates(listOf(open), emptySet(), season = null, population = 0))
    }

    @Test
    fun structureRequirementsMatchGmTypedNamesDespitePaddingAndCase() {
        // Structure names are typed by a GM; "Theater " must not read as a missing theater.
        val feast = template(requiresStructure = "theater")
        assertEquals(listOf(feast), eligibleTemplates(listOf(feast), setOf(" THEATER "), null, 0))
        assertEquals(listOf(feast), eligibleTemplates(listOf(feast), setOf("Theater"), null, 0))
        assertTrue(eligibleTemplates(listOf(feast), setOf("Tavern"), null, 0).isEmpty())
        assertTrue(eligibleTemplates(listOf(feast), emptySet(), null, 0).isEmpty())
    }

    @Test
    fun seasonRequirementsMatchExactlyAndFailWithoutACalendarSeason() {
        val midwinter = template(requiresSeason = "winter")
        assertEquals(listOf(midwinter), eligibleTemplates(listOf(midwinter), emptySet(), "winter", 0))
        assertTrue(eligibleTemplates(listOf(midwinter), emptySet(), "summer", 0).isEmpty())
        // A calendar that reports no season cannot host a winter-only feast...
        // a calendar reporting NO season is neutral now, not hostile: the richer model weights
        // seasons rather than gating on them, and a null season applies no multiplier at all
        assertEquals(listOf(midwinter), eligibleTemplates(listOf(midwinter), emptySet(), null, 0))
        // ...but a template with no season requirement does not care.
        val any = template(id = "any")
        assertEquals(listOf(any), eligibleTemplates(listOf(any), emptySet(), null, 0))
    }

    @Test
    fun populationAtExactlyTheMinimumIsEligibleAndOneBelowIsNot() {
        val fair = template(minPopulation = 400)
        assertEquals(listOf(fair), eligibleTemplates(listOf(fair), emptySet(), null, 400))
        assertTrue(eligibleTemplates(listOf(fair), emptySet(), null, 399).isEmpty())
    }

    @Test
    fun structureMatchingAlsoTrimsTheTemplateSide() {
        // Both sides are GM-typed: the template author can pad too, and a regression that trims
        // only the settlement side would silently disable every padded template.
        val padded = template(id = "p", requiresStructure = "theater ")
        val kept = eligibleTemplates(listOf(padded), setOf("Theater"), season = null, population = 0)
        assertEquals(listOf("p"), kept.map { it.id })
    }

    @Test
    fun aRosterMemberBeatsTheFallbackWhenBothExist() {
        // The fallback exists ONLY for an empty roster. Preferring it whenever provided would cast
        // an ephemeral nobody in place of a real resident and never attribute events to the roster.
        val cast = castFromRoster(
            listOf(RosterMember(id = "n1", name = "Svetlana", occupation = "Innkeeper")),
            preferredOccupation = null,
            fallbackName = "A Stranger",
        )
        assertEquals("n1", cast?.npcId)
        assertEquals("Svetlana", cast?.name)
    }

    @Test
    fun eligibilityPreservesCatalogOrder() {
        // weightedPick walks cumulative ranges in list order, so filtering must not reorder.
        // Ids are deliberately NOT in alphabetical order: a fixture whose survivors happen to be
        // sorted cannot tell "preserves input order" from "sorts by id".
        val z = template(id = "z")
        val b = template(id = "b", minPopulation = 1000)
        val a = template(id = "a")
        val d = template(id = "d", requiresSeason = "fall")
        val m = template(id = "m")
        // winter, so d's fall-only weighting zeroes it out; b fails the population floor
        val kept = eligibleTemplates(listOf(z, b, a, d, m), emptySet(), season = "winter", population = 0)
        assertEquals(listOf("z", "a", "m"), kept.map { it.id })
    }

    @Test
    fun weightedPickWalksCumulativeBoundariesInListOrder() {
        val list = listOf(
            template(id = "a", weight = 1),
            template(id = "b", weight = 2),
            template(id = "c", weight = 3),
        )
        assertEquals("a", weightedPick(list, 0)?.id)
        // Each boundary roll belongs to the NEXT template: ranges are half-open.
        assertEquals("b", weightedPick(list, 1)?.id)
        assertEquals("b", weightedPick(list, 2)?.id)
        assertEquals("c", weightedPick(list, 3)?.id)
        assertEquals("c", weightedPick(list, 5)?.id)
    }

    @Test
    fun weightedPickRejectsRollsOutsideZeroUntilTotal() {
        val list = listOf(template(id = "a", weight = 3), template(id = "b", weight = 2))
        assertEquals("a", weightedPick(list, 0)?.id)
        assertEquals("b", weightedPick(list, 4)?.id) // total - 1: the last valid roll
        assertNull(weightedPick(list, 5))            // roll == total: one past the end
        assertNull(weightedPick(list, -1))
    }

    @Test
    fun zeroAndNegativeWeightsOwnNoRangeSoNoRollCanLandOnThem() {
        val dead = template(id = "dead", weight = 0)
        val cursed = template(id = "cursed", weight = -5)
        val live = template(id = "live", weight = 2)
        val list = listOf(dead, cursed, live)
        // Total is the sum of POSITIVE weights only: the whole range belongs to "live".
        assertEquals("live", weightedPick(list, 0)?.id)
        assertEquals("live", weightedPick(list, 1)?.id)
        assertNull(weightedPick(list, 2))
    }

    @Test
    fun weightedPickOnAnEmptyOrAllNonpositiveListIsNull() {
        assertNull(weightedPick(emptyList(), 0))
        assertNull(weightedPick(listOf(template(weight = 0), template(weight = -1)), 0))
    }

    @Test
    fun lifeEventUnrestIsClampedToOnePointEitherWay() {
        assertEquals(-1, clampLifeEventUnrest(-2))
        assertEquals(-1, clampLifeEventUnrest(-1))
        assertEquals(0, clampLifeEventUnrest(0))
        assertEquals(1, clampLifeEventUnrest(1))
        assertEquals(1, clampLifeEventUnrest(2))
    }

    @Test
    fun castingPrefersAnOccupationMatchOverListPosition() {
        val roster = listOf(
            member(id = "n1", name = "Aldarn Vex", occupation = "Rat Catcher"),
            member(id = "n2", name = "Svetlana Morozov", occupation = "Innkeeper"),
        )
        // Occupations are GM-typed too: padding and case must not hide the match.
        val cast = castFromRoster(roster, preferredOccupation = " innkeeper ", fallbackName = null)
        assertNotNull(cast)
        assertEquals("n2", cast.npcId)
        assertEquals("Svetlana Morozov", cast.name)
    }

    @Test
    fun castingFallsBackToTheFirstResidentWhenNoOccupationMatches() {
        val roster = listOf(
            member(id = "n1", name = "Aldarn Vex", occupation = "Rat Catcher"),
            member(id = "n2", name = "Svetlana Morozov", occupation = "Innkeeper"),
        )
        // Soft preference: an unmatched occupation still casts someone rather than nobody.
        assertEquals("n1", castFromRoster(roster, preferredOccupation = "Blacksmith", fallbackName = null)?.npcId)
        assertEquals("n1", castFromRoster(roster, preferredOccupation = null, fallbackName = null)?.npcId)
    }

    @Test
    fun anEmptyRosterWithAFallbackNameCastsAnEphemeralMemberWithNoNpcId() {
        // The null npcId is the guard: an invented name can never be mistaken for a roster
        // resident or persisted back — the generator never resurrects user-deleted NPCs.
        val cast = castFromRoster(emptyList(), preferredOccupation = "Innkeeper", fallbackName = "Pell the Younger")
        assertNotNull(cast)
        assertNull(cast.npcId)
        assertEquals("Pell the Younger", cast.name)
    }

    @Test
    fun anEmptyRosterWithoutAFallbackNameCastsNobody() {
        assertNull(castFromRoster(emptyList(), preferredOccupation = "Innkeeper", fallbackName = null))
    }

    @Test
    fun unknownStoredHookKindsMapToNullRatherThanThrowing() {
        assertEquals(LifeEventHookKind.UNREST, LifeEventHookKind.fromValue("unrest-delta"))
        assertEquals(LifeEventHookKind.RP, LifeEventHookKind.fromValue("rp-delta"))
        assertEquals(LifeEventHookKind.RUMOR, LifeEventHookKind.fromValue("rumor-spawn"))
        // the shortened spellings the core briefly used are NOT the contract
        assertNull(LifeEventHookKind.fromValue("unrest"))
        assertEquals(LifeEventHookKind.NONE, LifeEventHookKind.fromValue("none"))
        assertNull(LifeEventHookKind.fromValue("goldRain"))
        assertNull(LifeEventHookKind.fromValue(null))
    }

    @Test
    fun theKingdomWidePerTurnCapIsTwo() {
        assertEquals(2, MAX_LIFE_EVENTS_PER_TURN)
    }

    // ── weighting: two documented behaviours that mutation testing found untested ──────────────

    @Test
    fun aStructureGroupMultipliesOnceHoweverManyOfItsIdsAreOwned() {
        // The KDoc promises this outright: owning three taverns must not make a feast eight times
        // likelier. Nothing tested it, so a per-matching-id loop passed the whole suite.
        val feast = LifeEventTemplate(
            id = "feast",
            weight = 10,
            structureWeights = listOf(listOf("tavern-dive", "tavern-popular", "theater") to 2.0),
            hookKind = LifeEventHookKind.NONE,
        )
        val one = effectiveWeight(feast, setOf("tavern-dive"), season = null)
        val all = effectiveWeight(feast, setOf("tavern-dive", "tavern-popular", "theater"), season = null)
        assertEquals(20, one)
        assertEquals(20, all, "the group multiplied more than once")
        // a settlement with none of them keeps the base weight
        assertEquals(10, effectiveWeight(feast, setOf("marketplace"), season = null))
    }

    @Test
    fun separateGroupsEachApplyAndSeasonsCompose() {
        val template = LifeEventTemplate(
            id = "market",
            weight = 10,
            structureWeights = listOf(
                listOf("marketplace") to 2.0,
                listOf("stockyard") to 1.5,
            ),
            seasonWeights = mapOf("summer" to 2.0),
            hookKind = LifeEventHookKind.NONE,
        )
        // 10 * 2.0 * 1.5 = 30, then summer doubles it
        assertEquals(30, effectiveWeight(template, setOf("marketplace", "stockyard"), season = null))
        assertEquals(60, effectiveWeight(template, setOf("marketplace", "stockyard"), season = "summer"))
    }

    @Test
    fun cooldownKeepsATemplateOutUntilItsTurnsHavePassed() {
        // cooldownTurns had no test at all: ignoring it entirely passed the suite
        val template = LifeEventTemplate(
            id = "feud",
            weight = 5,
            hookKind = LifeEventHookKind.NONE,
            cooldownTurns = 2,
        )
        val fired = mapOf("feud" to 5)
        fun eligibleOn(turn: Int) = eligibleTemplates(
            listOf(template), emptySet(), season = null, population = 0,
            lastFiredByTemplate = fired, currentTurn = turn,
        )
        // fired on turn 5 with a 2-turn cooldown: 6 and 7 are suppressed, 8 is clear
        assertTrue(eligibleOn(6).isEmpty())
        assertTrue(eligibleOn(7).isEmpty())
        assertEquals(listOf(template), eligibleOn(8))
        // a template that has never fired is always eligible
        assertEquals(
            listOf(template),
            eligibleTemplates(
                listOf(template), emptySet(), season = null, population = 0,
                lastFiredByTemplate = emptyMap(), currentTurn = 6,
            ),
        )
        // cooldown 0 never suppresses
        val noCooldown = template.copy(cooldownTurns = 0)
        assertEquals(
            listOf(noCooldown),
            eligibleTemplates(
                listOf(noCooldown), emptySet(), season = null, population = 0,
                lastFiredByTemplate = mapOf("feud" to 5), currentTurn = 5,
            ),
        )
    }
}

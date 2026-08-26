package at.posselt.pfrpg2e.data.kingdom

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers §7.1 of `docs/plans/2026-07-09-plan-renown-spotlight.md`: the accrual amounts and their
 * clamps, the exactness of the re-roll reversal, the epithet evaluator over a supplied catalog, the
 * perk resolution, and the Spotlight's ordering and tone rules.
 *
 * The catalog itself is authored content and lives outside the engine, so every epithet used below
 * is a locally built fixture. That is deliberate: these tests must not become the place a real
 * epithet id accidentally gets defined.
 */
class RenownEngineTest {

    private val pc = PcRenown(actorUuid = "Actor.emissary")

    private fun deed(
        kind: ContributionKind,
        leader: Leader = Leader.EMISSARY,
        category: DeedCategory = DeedCategory.OTHER,
        factionName: String? = null,
    ) = Contribution(kind = kind, leader = leader, category = category, factionName = factionName)

    private fun tally(
        uuid: String,
        checks: Int = 0,
        crits: Int = 0,
        critFails: Int = 0,
        activities: Int = 0,
        events: Int = 0,
        name: String? = null,
    ) = TurnTally(
        actorUuid = uuid,
        actorName = name,
        checks = checks,
        crits = crits,
        critFails = critFails,
        activities = activities,
        events = events,
    )

    // --- deedCategoryFor -------------------------------------------------------------------

    @Test
    fun deedCategoryForBucketsEverySkillAndNeverLeavesOneUnclassified() {
        val diplomatic = setOf(
            KingdomSkill.STATECRAFT, KingdomSkill.POLITICS, KingdomSkill.TRADE, KingdomSkill.INTRIGUE,
        )
        val martial = setOf(KingdomSkill.WARFARE, KingdomSkill.DEFENSE)
        diplomatic.forEach { assertEquals(DeedCategory.DIPLOMATIC, deedCategoryFor(it), it.name) }
        martial.forEach { assertEquals(DeedCategory.MARTIAL, deedCategoryFor(it), it.name) }
        KingdomSkill.entries
            .filter { it !in diplomatic && it !in martial }
            .forEach { assertEquals(DeedCategory.OTHER, deedCategoryFor(it), it.name) }
        // The plan's named example of the residual bucket.
        assertEquals(DeedCategory.OTHER, deedCategoryFor(KingdomSkill.AGRICULTURE))
    }

    // --- the accrual source table ----------------------------------------------------------

    @Test
    fun renownDeltaForMatchesTheSourceTableExactly() {
        assertEquals(RenownDelta(populace = 3, faction = 2), renownDeltaFor(ContributionKind.CHECK_CRIT))
        assertEquals(RenownDelta(populace = 1, faction = 1), renownDeltaFor(ContributionKind.CHECK_SUCCESS))
        assertEquals(RenownDelta(populace = 0, faction = 0), renownDeltaFor(ContributionKind.CHECK_FAILURE))
        assertEquals(RenownDelta(populace = -2, faction = -1), renownDeltaFor(ContributionKind.CHECK_CRIT_FAIL))
        assertEquals(RenownDelta(populace = 1, faction = 0), renownDeltaFor(ContributionKind.ACTIVITY))
        assertEquals(RenownDelta(populace = 2, faction = 0), renownDeltaFor(ContributionKind.EVENT_RESOLVED))
        assertEquals(RenownDelta(populace = 2, faction = 0), renownDeltaFor(ContributionKind.PETITION_ANSWERED))
    }

    // --- accrueRenown ----------------------------------------------------------------------

    @Test
    fun aCritCreditsPopulaceAndTheNamedFaction() {
        val result = accrueRenown(
            pc,
            deed(ContributionKind.CHECK_CRIT, category = DeedCategory.DIPLOMATIC, factionName = "Pitax"),
        )
        assertEquals(3, result.renown.populace)
        assertEquals(mapOf("Pitax" to 2), result.renown.factionRenown)
        assertEquals(3, result.populaceApplied)
        assertEquals(2, result.factionApplied)
    }

    @Test
    fun activitiesEventsAndPetitionsCreditPopulaceOnlyEvenWithAFactionNamed() {
        val activity = accrueRenown(
            pc,
            deed(ContributionKind.ACTIVITY, category = DeedCategory.DIPLOMATIC, factionName = "Pitax"),
        )
        assertEquals(1, activity.renown.populace)
        assertEquals(emptyMap<String, Int>(), activity.renown.factionRenown)
        assertEquals(0, activity.factionApplied)

        val event = accrueRenown(pc, deed(ContributionKind.EVENT_RESOLVED, factionName = "Pitax"))
        assertEquals(2, event.renown.populace)
        assertEquals(emptyMap<String, Int>(), event.renown.factionRenown)

        val petition = accrueRenown(pc, deed(ContributionKind.PETITION_ANSWERED, factionName = "Pitax"))
        assertEquals(2, petition.renown.populace)
        assertEquals(emptyMap<String, Int>(), petition.renown.factionRenown)
    }

    @Test
    fun aPlainFailureMovesNothingAtAll() {
        val start = pc.copy(
            populace = 10,
            factionRenown = mapOf("Pitax" to 5),
            roleSuccesses = mapOf(Leader.EMISSARY to 4),
        )
        val result = accrueRenown(start, deed(ContributionKind.CHECK_FAILURE, factionName = "Pitax"))
        assertEquals(start, result.renown)
        assertEquals(0, result.populaceApplied)
        assertEquals(0, result.factionApplied)
    }

    @Test
    fun aCriticalFailureStopsAtZeroAndReportsWhatActuallyLanded() {
        val fromTwo = accrueRenown(pc.copy(populace = 2), deed(ContributionKind.CHECK_CRIT_FAIL))
        assertEquals(0, fromTwo.renown.populace)
        assertEquals(-2, fromTwo.populaceApplied)
        // One point is all there was to lose, so that is all that may be refunded on a re-roll.
        val fromOne = accrueRenown(pc.copy(populace = 1), deed(ContributionKind.CHECK_CRIT_FAIL))
        assertEquals(MIN_POPULACE_RENOWN, fromOne.renown.populace)
        assertEquals(-1, fromOne.populaceApplied)
        val fromZero = accrueRenown(pc, deed(ContributionKind.CHECK_CRIT_FAIL))
        assertEquals(MIN_POPULACE_RENOWN, fromZero.renown.populace)
        assertEquals(0, fromZero.populaceApplied)
    }

    @Test
    fun populaceStopsAtTheCeilingAndReportsTheClampedDelta() {
        val below = accrueRenown(pc.copy(populace = 96), deed(ContributionKind.CHECK_CRIT))
        assertEquals(99, below.renown.populace)
        assertEquals(3, below.populaceApplied)
        val crossing = accrueRenown(pc.copy(populace = 99), deed(ContributionKind.CHECK_CRIT))
        assertEquals(MAX_POPULACE_RENOWN, crossing.renown.populace)
        assertEquals(1, crossing.populaceApplied)
        val atCeiling = accrueRenown(pc.copy(populace = MAX_POPULACE_RENOWN), deed(ContributionKind.CHECK_CRIT))
        assertEquals(MAX_POPULACE_RENOWN, atCeiling.renown.populace)
        assertEquals(0, atCeiling.populaceApplied)
    }

    @Test
    fun factionRenownClampsAtItsOwnSoftCapNotTheKingdomStandingRange() {
        val justBelow = accrueRenown(
            pc.copy(factionRenown = mapOf("Pitax" to FACTION_RENOWN_SOFT_CAP - 1)),
            deed(ContributionKind.CHECK_CRIT, factionName = "Pitax"),
        )
        assertEquals(FACTION_RENOWN_SOFT_CAP, justBelow.renown.factionRenown["Pitax"])
        assertEquals(1, justBelow.factionApplied)

        val atCap = accrueRenown(
            pc.copy(factionRenown = mapOf("Pitax" to FACTION_RENOWN_SOFT_CAP)),
            deed(ContributionKind.CHECK_CRIT, factionName = "Pitax"),
        )
        assertEquals(FACTION_RENOWN_SOFT_CAP, atCap.renown.factionRenown["Pitax"])
        assertEquals(0, atCap.factionApplied)

        // Grinding crits forever never reaches kingdom-standing scale: the two economies must stay
        // visibly different in range (§2.4).
        var current = pc
        repeat(40) { current = accrueRenown(current, deed(ContributionKind.CHECK_CRIT, factionName = "Pitax")).renown }
        assertEquals(FACTION_RENOWN_SOFT_CAP, current.factionRenown["Pitax"])
        assertTrue(FACTION_RENOWN_SOFT_CAP < MAX_FACTION_STANDING)
    }

    @Test
    fun factionRenownFloorsAtTheNegativeSoftCap() {
        val justAbove = accrueRenown(
            pc.copy(factionRenown = mapOf("Pitax" to -(FACTION_RENOWN_SOFT_CAP - 1))),
            deed(ContributionKind.CHECK_CRIT_FAIL, factionName = "Pitax"),
        )
        assertEquals(-FACTION_RENOWN_SOFT_CAP, justAbove.renown.factionRenown["Pitax"])
        assertEquals(-1, justAbove.factionApplied)

        val atFloor = accrueRenown(
            pc.copy(factionRenown = mapOf("Pitax" to -FACTION_RENOWN_SOFT_CAP)),
            deed(ContributionKind.CHECK_CRIT_FAIL, factionName = "Pitax"),
        )
        assertEquals(-FACTION_RENOWN_SOFT_CAP, atFloor.renown.factionRenown["Pitax"])
        assertEquals(0, atFloor.factionApplied)
    }

    @Test
    fun anOutOfRangePopulaceIsPulledIntoRangeAndTheReversalStillPutsItBack() {
        // A GM adjustment or legacy row can sit above the ceiling. The clamp pulls it in, the
        // applied delta records the whole move, and reverting restores the odd value rather than
        // stranding the PC at 100 -- which is why revert must not re-clamp.
        val corrupt = pc.copy(populace = 120)
        val contribution = deed(ContributionKind.CHECK_CRIT)
        val accrued = accrueRenown(corrupt, contribution)
        assertEquals(MAX_POPULACE_RENOWN, accrued.renown.populace)
        assertEquals(-20, accrued.populaceApplied)
        assertEquals(
            corrupt,
            revertRenown(accrued.renown, contribution, accrued.populaceApplied, accrued.factionApplied),
        )
    }

    @Test
    fun accrueLeavesTheInputLedgerUntouched() {
        val start = pc.copy(
            populace = 5,
            factionRenown = mapOf("Pitax" to 1),
            roleSuccesses = mapOf(Leader.EMISSARY to 2),
        )
        val result = accrueRenown(start, deed(ContributionKind.CHECK_CRIT, factionName = "Pitax"))
        assertEquals(5, start.populace)
        assertEquals(mapOf("Pitax" to 1), start.factionRenown)
        assertEquals(mapOf(Leader.EMISSARY to 2), start.roleSuccesses)
        assertNotEquals(start, result.renown)
    }

    @Test
    fun aBlankFactionNameNeverMintsANamelessFactionRow() {
        listOf("", "   ").forEach { blank ->
            val result = accrueRenown(pc, deed(ContributionKind.CHECK_CRIT, factionName = blank))
            assertEquals(emptyMap<String, Int>(), result.renown.factionRenown, "blank=[$blank]")
            assertEquals(0, result.factionApplied, "blank=[$blank]")
            assertEquals(3, result.renown.populace, "blank=[$blank]")
        }
    }

    @Test
    fun accrueMaintainsEveryRoleAndCategoryCounterItTouches() {
        val success = accrueRenown(
            pc,
            deed(ContributionKind.CHECK_SUCCESS, leader = Leader.TREASURER, category = DeedCategory.DIPLOMATIC),
        ).renown
        assertEquals(mapOf(Leader.TREASURER to 1), success.roleSuccesses)
        assertEquals(emptyMap<Leader, Int>(), success.roleCrits)
        assertEquals(0, success.lifetimeCrits)

        val crit = accrueRenown(
            success,
            deed(ContributionKind.CHECK_CRIT, leader = Leader.TREASURER, category = DeedCategory.DIPLOMATIC),
        ).renown
        // A critical success is a success in its role as well as a crit.
        assertEquals(mapOf(Leader.TREASURER to 2), crit.roleSuccesses)
        assertEquals(mapOf(Leader.TREASURER to 1), crit.roleCrits)
        assertEquals(mapOf(DeedCategory.DIPLOMATIC to 1), crit.categoryCrits)
        assertEquals(1, crit.lifetimeCrits)

        val activity = accrueRenown(
            crit,
            deed(ContributionKind.ACTIVITY, leader = Leader.TREASURER, category = DeedCategory.DIPLOMATIC),
        ).renown
        assertEquals(mapOf(DeedCategory.DIPLOMATIC to 1), activity.categoryActivities)
        assertEquals(1, activity.lifetimeActivities)
        // An activity is not a rolled check and must not inflate the role's success tally.
        assertEquals(mapOf(Leader.TREASURER to 2), activity.roleSuccesses)

        val critFail = accrueRenown(
            activity,
            deed(ContributionKind.CHECK_CRIT_FAIL, leader = Leader.TREASURER),
        ).renown
        assertEquals(1, critFail.lifetimeCritFails)
        assertEquals(mapOf(Leader.TREASURER to 2), critFail.roleSuccesses)

        val event = accrueRenown(critFail, deed(ContributionKind.EVENT_RESOLVED, leader = Leader.TREASURER)).renown
        assertEquals(1, event.lifetimeEvents)
        assertEquals(1, event.lifetimeActivities)
    }

    // --- revertRenown ----------------------------------------------------------------------

    @Test
    fun revertIsTheExactInverseOfAccrueForEveryKind() {
        val start = pc.copy(
            populace = 30,
            factionRenown = mapOf("Pitax" to 7, "Restov" to -3),
            epithets = setOf("someHeldEpithet"),
            purchaseAccessTier = 1,
            lifetimeCrits = 4,
            lifetimeCritFails = 2,
            lifetimeActivities = 9,
            lifetimeEvents = 3,
            roleSuccesses = mapOf(Leader.EMISSARY to 6),
            roleCrits = mapOf(Leader.EMISSARY to 2),
            categoryActivities = mapOf(DeedCategory.DIPLOMATIC to 5),
            categoryCrits = mapOf(DeedCategory.DIPLOMATIC to 1),
        )
        ContributionKind.entries.forEach { kind ->
            val contribution = deed(
                kind,
                leader = Leader.EMISSARY,
                category = DeedCategory.DIPLOMATIC,
                factionName = "Pitax",
            )
            val accrued = accrueRenown(start, contribution)
            assertEquals(
                start,
                revertRenown(accrued.renown, contribution, accrued.populaceApplied, accrued.factionApplied),
                kind.value,
            )
        }
    }

    @Test
    fun revertIsExactAtEveryClampBoundaryAndOnAnEmptyLedger() {
        val cases = listOf(
            pc,
            pc.copy(populace = 99, factionRenown = mapOf("Pitax" to FACTION_RENOWN_SOFT_CAP - 1)),
            pc.copy(populace = MAX_POPULACE_RENOWN, factionRenown = mapOf("Pitax" to FACTION_RENOWN_SOFT_CAP)),
            pc.copy(populace = MIN_POPULACE_RENOWN, factionRenown = mapOf("Pitax" to -FACTION_RENOWN_SOFT_CAP)),
            pc.copy(populace = 1, factionRenown = mapOf("Pitax" to -(FACTION_RENOWN_SOFT_CAP - 1))),
        )
        val kinds = listOf(
            ContributionKind.CHECK_CRIT,
            ContributionKind.CHECK_SUCCESS,
            ContributionKind.CHECK_CRIT_FAIL,
        )
        kinds.forEach { kind ->
            cases.forEach { start ->
                val contribution = deed(kind, factionName = "Pitax")
                val accrued = accrueRenown(start, contribution)
                assertEquals(
                    start,
                    revertRenown(accrued.renown, contribution, accrued.populaceApplied, accrued.factionApplied),
                    "${kind.value} from $start",
                )
            }
        }
    }

    @Test
    fun revertDropsACounterKeyInsteadOfLeavingAZeroBehind() {
        val contribution = deed(
            ContributionKind.CHECK_CRIT,
            leader = Leader.WARDEN,
            category = DeedCategory.MARTIAL,
            factionName = "Restov",
        )
        val accrued = accrueRenown(pc, contribution)
        val reverted = revertRenown(accrued.renown, contribution, accrued.populaceApplied, accrued.factionApplied)
        assertFalse(reverted.roleCrits.containsKey(Leader.WARDEN))
        assertFalse(reverted.roleSuccesses.containsKey(Leader.WARDEN))
        assertFalse(reverted.categoryCrits.containsKey(DeedCategory.MARTIAL))
        assertFalse(reverted.factionRenown.containsKey("Restov"))
        assertEquals(pc, reverted)
    }

    @Test
    fun revertNeverRevokesAnEpithetOrTheGrantedTier() {
        val honoured = pc.copy(
            populace = 50,
            epithets = setOf("peoplesChampion", "theUntiring"),
            purchaseAccessTier = 2,
        )
        val contribution = deed(ContributionKind.CHECK_CRIT)
        val accrued = accrueRenown(honoured, contribution)
        val reverted = revertRenown(accrued.renown, contribution, accrued.populaceApplied, accrued.factionApplied)
        assertEquals(setOf("peoplesChampion", "theUntiring"), reverted.epithets)
        assertEquals(2, reverted.purchaseAccessTier)
        assertEquals(50, reverted.populace)
        assertEquals("Actor.emissary", reverted.actorUuid)
    }

    @Test
    fun revertingADeedTheLedgerNeverRecordedCannotMintNegativeCounters() {
        val contribution = deed(
            ContributionKind.CHECK_CRIT,
            leader = Leader.GENERAL,
            category = DeedCategory.MARTIAL,
        )
        val once = revertRenown(pc, contribution, populaceApplied = 0, factionApplied = 0)
        assertEquals(0, once.lifetimeCrits)
        assertEquals(emptyMap<Leader, Int>(), once.roleCrits)
        assertEquals(emptyMap<Leader, Int>(), once.roleSuccesses)
        assertEquals(emptyMap<DeedCategory, Int>(), once.categoryCrits)
        // Idempotent: a second reversal of the same missing deed changes nothing further.
        assertEquals(once, revertRenown(once, contribution, populaceApplied = 0, factionApplied = 0))
    }

    // --- epithetContextFor -----------------------------------------------------------------

    @Test
    fun epithetContextForFoldsEveryCounterTheCatalogReads() {
        val renown = pc.copy(
            populace = 42,
            factionRenown = mapOf("Pitax" to -26),
            lifetimeCrits = 8,
            lifetimeCritFails = 3,
            lifetimeActivities = 20,
            lifetimeEvents = 5,
            roleSuccesses = mapOf(Leader.TREASURER to 15),
            roleCrits = mapOf(Leader.EMISSARY to 9),
            categoryActivities = mapOf(DeedCategory.DIPLOMATIC to 6),
            categoryCrits = mapOf(DeedCategory.MARTIAL to 4),
        )
        val ctx = epithetContextFor(renown, holdsRulerRole = true)
        assertEquals(42, ctx.populace)
        assertEquals(mapOf("Pitax" to -26), ctx.factionRenown)
        assertEquals(8, ctx.critSuccesses)
        assertEquals(3, ctx.critFails)
        assertEquals(20, ctx.activities)
        assertEquals(5, ctx.eventsResolved)
        assertEquals(mapOf(Leader.TREASURER to 15), ctx.roleSuccesses)
        assertEquals(mapOf(Leader.EMISSARY to 9), ctx.roleCrits)
        assertEquals(mapOf(DeedCategory.DIPLOMATIC to 6), ctx.categoryActivities)
        assertTrue(ctx.holdsRulerRole)
        assertFalse(epithetContextFor(renown, holdsRulerRole = false).holdsRulerRole)
    }

    @Test
    fun epithetContextPopulaceDefaultsToThePcButStaysOverridable() {
        val renown = pc.copy(populace = 42)
        assertEquals(42, epithetContextFor(renown, holdsRulerRole = false).populace)
        assertEquals(7, epithetContextFor(renown, holdsRulerRole = false, populace = 7).populace)
    }

    // --- evaluateEpithets ------------------------------------------------------------------

    @Test
    fun anEpithetIsAwardedOnCrossingItsThresholdAndNeverASecondTime() {
        val catalog = listOf(
            EpithetDefinition(
                id = "fixtureChampion",
                condition = EpithetCondition { ctx, _ -> ctx.populace >= 50 },
                perk = RenownPerk.PurchaseAccess(2),
            ),
        )
        val justBelow = pc.copy(populace = 49)
        assertEquals(
            emptyList<EpithetAward>(),
            evaluateEpithets(justBelow, epithetContextFor(justBelow, holdsRulerRole = false), catalog),
        )
        val atThreshold = pc.copy(populace = 50)
        assertEquals(
            listOf(EpithetAward("fixtureChampion", RenownPerk.PurchaseAccess(2))),
            evaluateEpithets(atThreshold, epithetContextFor(atThreshold, holdsRulerRole = false), catalog),
        )
        // Once held, staying well past the threshold must never re-offer it.
        val held = pc.copy(populace = 80, epithets = setOf("fixtureChampion"))
        assertEquals(
            emptyList<EpithetAward>(),
            evaluateEpithets(held, epithetContextFor(held, holdsRulerRole = false), catalog),
        )
    }

    @Test
    fun awardsKeepCatalogOrderAndADuplicatedIdIsGrantedOnce() {
        val always = EpithetCondition { _, _ -> true }
        val never = EpithetCondition { _, _ -> false }
        val catalog = listOf(
            EpithetDefinition(id = "zzzFirstInTable", condition = always),
            EpithetDefinition(id = "aaaSecondInTable", condition = always, perk = RenownPerk.Invitation("Narlmarches")),
            EpithetDefinition(id = "zzzFirstInTable", condition = always),
            EpithetDefinition(id = "unmet", condition = never),
        )
        assertEquals(
            listOf(
                EpithetAward("zzzFirstInTable", null),
                EpithetAward("aaaSecondInTable", RenownPerk.Invitation("Narlmarches")),
            ),
            evaluateEpithets(pc, epithetContextFor(pc, holdsRulerRole = false), catalog),
        )
        // A repeated id whose first row did not qualify may still fire on a later row.
        val retry = listOf(
            EpithetDefinition(id = "twin", condition = never),
            EpithetDefinition(id = "twin", condition = always),
        )
        assertEquals(
            listOf(EpithetAward("twin", null)),
            evaluateEpithets(pc, epithetContextFor(pc, holdsRulerRole = false), retry),
        )
        assertEquals(
            emptyList<EpithetAward>(),
            evaluateEpithets(pc, epithetContextFor(pc, holdsRulerRole = false), emptyList()),
        )
    }

    @Test
    fun conditionsSeeTheirOwnFactionNamesAndTheRulerFlag() {
        val catalog = listOf(
            EpithetDefinition(
                id = "fixtureHammer",
                condition = EpithetCondition { ctx, names -> (ctx.bestFactionRenown(names) ?: 0) >= 25 },
                factionNames = listOf("Restov", "Swordlords"),
            ),
            EpithetDefinition(
                id = "fixtureKingmaker",
                condition = EpithetCondition { ctx, _ -> ctx.populace >= 75 && ctx.holdsRulerRole },
            ),
        )
        val renown = pc.copy(populace = 75, factionRenown = mapOf("swordlords" to 25))
        assertEquals(
            listOf(EpithetAward("fixtureHammer", null), EpithetAward("fixtureKingmaker", null)),
            evaluateEpithets(renown, epithetContextFor(renown, holdsRulerRole = true), catalog),
        )
        // Same numbers, but the PC does not hold the Ruler slot.
        assertEquals(
            listOf(EpithetAward("fixtureHammer", null)),
            evaluateEpithets(renown, epithetContextFor(renown, holdsRulerRole = false), catalog),
        )
    }

    @Test
    fun factionNamedConditionsMatchCaseInsensitivelyAndAStrangerIsNotAMatch() {
        val ctx = epithetContextFor(
            pc.copy(factionRenown = mapOf("  pItAx " to -26, "Restov" to 25)),
            holdsRulerRole = false,
        )
        assertEquals(-26, ctx.bestFactionRenown(listOf("Pitax")))
        assertEquals(-26, ctx.worstFactionRenown(listOf("PITAX")))
        assertEquals(25, ctx.bestFactionRenown(listOf("Restov", "Swordlords")))
        assertEquals(-26, ctx.worstFactionRenown(listOf("Restov", "Pitax")))
        assertEquals(25, ctx.bestFactionRenown(listOf("Restov", "Pitax")))
        // Unknown rather than zero: a nemesis condition of "<= -25" must not fire for a faction
        // this PC has never dealt with.
        assertNull(ctx.bestFactionRenown(listOf("Mivon")))
        assertNull(ctx.worstFactionRenown(listOf("Mivon")))
        assertNull(ctx.bestFactionRenown(emptyList()))
        assertNull(ctx.bestFactionRenown(listOf("", "   ")))
        assertNull(EpithetContext().bestFactionRenown(listOf("Pitax")))
    }

    // --- perks -----------------------------------------------------------------------------

    @Test
    fun perkLookupIsAClosedSetAndAnUnknownIdIsNullNotAThrow() {
        val met = EpithetCondition { _, _ -> true }
        val catalog = listOf(
            EpithetDefinition("fixtureCoinCounter", met, RenownPerk.PurchaseAccess(2)),
            EpithetDefinition("fixtureFeyFriend", met, RenownPerk.Invitation("Narlmarches")),
            EpithetDefinition("fixtureUnshaken", met),
        )
        assertEquals(RenownPerk.PurchaseAccess(2), perkForEpithet("fixtureCoinCounter", catalog))
        assertEquals(RenownPerk.Invitation("Narlmarches"), perkForEpithet("fixtureFeyFriend", catalog))
        assertNull(perkForEpithet("fixtureUnshaken", catalog))
        // An id left over from an older catalog degrades to "no perk" instead of breaking the pass.
        assertNull(perkForEpithet("idFromAnOlderCatalog", catalog))
        assertNull(perkForEpithet("fixtureCoinCounter", emptyList()))
    }

    @Test
    fun purchaseAccessLevelsClampToTheClosedTierRange() {
        assertEquals(0, purchaseAccessLevelsForTier(-1))
        assertEquals(0, purchaseAccessLevelsForTier(0))
        assertEquals(1, purchaseAccessLevelsForTier(1))
        assertEquals(2, purchaseAccessLevelsForTier(2))
        assertEquals(MAX_PURCHASE_ACCESS_TIER, purchaseAccessLevelsForTier(3))
        assertEquals(MAX_PURCHASE_ACCESS_TIER, purchaseAccessLevelsForTier(99))
    }

    @Test
    fun theSettlementNudgeIsTheBestTierInTheRealmAndZeroWithNobody() {
        assertEquals(0, settlementPurchaseAccessLevels(emptyList()))
        assertEquals(0, settlementPurchaseAccessLevels(listOf(0, 0)))
        assertEquals(2, settlementPurchaseAccessLevels(listOf(1, 2, 0)))
        assertEquals(1, settlementPurchaseAccessLevels(listOf(1, -3)))
        assertEquals(MAX_PURCHASE_ACCESS_TIER, settlementPurchaseAccessLevels(listOf(7)))
    }

    // --- spotlightOfTheTurn ----------------------------------------------------------------

    @Test
    fun theSpotlightIsNullWhenNobodyContributed() {
        assertNull(spotlightOfTheTurn(emptyList()))
        // Rows exist but hold nothing: no one may be crowned busiest with a count of zero.
        assertNull(spotlightOfTheTurn(listOf(tally("Actor.a"), tally("Actor.b"))))
    }

    @Test
    fun theSpotlightWeighsCritsAboveEventsAboveRoutineWork() {
        assertEquals(3, spotlightScore(tally("Actor.x", crits = 1)))
        assertEquals(2, spotlightScore(tally("Actor.x", events = 1)))
        assertEquals(1, spotlightScore(tally("Actor.x", activities = 1)))
        assertEquals(1, spotlightScore(tally("Actor.x", checks = 1)))
        // Two crits (6) beat five plain checks (5).
        val pick = spotlightOfTheTurn(listOf(tally("Actor.b", checks = 5), tally("Actor.a", crits = 2)))
        assertEquals("Actor.a", pick?.actorUuid)
        assertEquals(SpotlightKind.CRIT_STAR, pick?.kind)
        assertEquals(2, pick?.count)
    }

    @Test
    fun theSpotlightTieBreaksOnActorUuidSoTheSameTurnAlwaysNamesTheSamePc() {
        val ordered = spotlightOfTheTurn(listOf(tally("Actor.zeta", checks = 3), tally("Actor.alpha", checks = 3)))
        assertEquals("Actor.alpha", ordered?.actorUuid)
        val reversed = spotlightOfTheTurn(listOf(tally("Actor.alpha", checks = 3), tally("Actor.zeta", checks = 3)))
        assertEquals("Actor.alpha", reversed?.actorUuid)
    }

    @Test
    fun theBlundererLineNeedsTwoFumblesNotOne() {
        val star = tally("Actor.star", crits = 4)
        val oneFumble = spotlightOfTheTurn(listOf(star, tally("Actor.clumsy", checks = 1, critFails = 1)))
        assertEquals(SpotlightKind.CRIT_STAR, oneFumble?.kind)
        assertEquals("Actor.star", oneFumble?.actorUuid)
        val twoFumbles = spotlightOfTheTurn(listOf(star, tally("Actor.clumsy", checks = 1, critFails = 2)))
        assertEquals(SpotlightKind.BLUNDERER, twoFumbles?.kind)
        assertEquals("Actor.clumsy", twoFumbles?.actorUuid)
        assertEquals(BLUNDERER_CRIT_FAIL_THRESHOLD, twoFumbles?.count)
    }

    @Test
    fun theTurnsTopScorerIsNeverTheOneMocked() {
        // This PC both fumbled twice AND carried the turn: celebrate, never mock (§4.3 tone rule).
        val pick = spotlightOfTheTurn(
            listOf(
                tally("Actor.carrier", crits = 5, critFails = 2),
                tally("Actor.quiet", checks = 1),
            ),
        )
        assertEquals("Actor.carrier", pick?.actorUuid)
        assertEquals(SpotlightKind.CRIT_STAR, pick?.kind)
        assertEquals(5, pick?.count)
    }

    @Test
    fun theBlundererLineCanBeTurnedOffEntirely() {
        val tallies = listOf(
            tally("Actor.star", crits = 4),
            tally("Actor.clumsy", checks = 1, critFails = 3),
        )
        assertEquals(SpotlightKind.BLUNDERER, spotlightOfTheTurn(tallies)?.kind)
        val muted = spotlightOfTheTurn(tallies, allowBlunderer = false)
        assertEquals(SpotlightKind.CRIT_STAR, muted?.kind)
        assertEquals("Actor.star", muted?.actorUuid)
    }

    @Test
    fun theUnluckiestQualifyingPcTakesTheBlundererLine() {
        val pick = spotlightOfTheTurn(
            listOf(
                tally("Actor.star", crits = 6),
                tally("Actor.aaa", checks = 1, critFails = 2),
                tally("Actor.zzz", checks = 1, critFails = 4),
            ),
        )
        assertEquals("Actor.zzz", pick?.actorUuid)
        assertEquals(4, pick?.count)
        val tied = spotlightOfTheTurn(
            listOf(
                tally("Actor.star", crits = 6),
                tally("Actor.beta", checks = 1, critFails = 3),
                tally("Actor.alpha", checks = 1, critFails = 3),
            ),
        )
        assertEquals("Actor.alpha", tied?.actorUuid)
    }

    @Test
    fun aSpecificFlavourNeedsItsComponentToDominateNotMerelyTie() {
        // One crit (3) exactly ties three plain checks (3): the generic line, not "on fire".
        val tie = spotlightOfTheTurn(listOf(tally("Actor.solo", checks = 3, crits = 1)))
        assertEquals(SpotlightKind.BUSIEST, tie?.kind)
        assertEquals(4, tie?.count)
        val dominant = spotlightOfTheTurn(listOf(tally("Actor.solo", checks = 3, crits = 2)))
        assertEquals(SpotlightKind.CRIT_STAR, dominant?.kind)
        assertEquals(2, dominant?.count)
    }

    @Test
    fun theEventHeroLineFiresWhenCrisesDominate() {
        val pick = spotlightOfTheTurn(listOf(tally("Actor.steady", checks = 2, crits = 1, events = 3)))
        assertEquals(SpotlightKind.EVENT_HERO, pick?.kind)
        assertEquals(3, pick?.count)
    }

    @Test
    fun theBusiestLineCountsEffortIncludingFumbles() {
        val busy = tally("Actor.solo", checks = 2, critFails = 1, activities = 2)
        val pick = spotlightOfTheTurn(listOf(busy))
        assertEquals(SpotlightKind.BUSIEST, pick?.kind)
        assertEquals(5, pick?.count)
        // The score, unlike the count, is achievement only: the fumble earns nothing.
        assertEquals(4, spotlightScore(busy))
    }

    @Test
    fun corruptNegativeCountsCanNeitherWinNorPoisonTheSpotlight() {
        assertEquals(0, spotlightScore(tally("Actor.bad", checks = -5, crits = -2)))
        assertEquals(0, spotlightActionCount(tally("Actor.bad", checks = -5)))
        assertNull(spotlightOfTheTurn(listOf(tally("Actor.bad", checks = -5, crits = -2))))
        val pick = spotlightOfTheTurn(listOf(tally("Actor.bad", checks = -5), tally("Actor.good", checks = 1)))
        assertEquals("Actor.good", pick?.actorUuid)
    }

    @Test
    fun theSpotlightCarriesTheDenormalisedNameForTheChatLine() {
        assertEquals("Jhod", spotlightOfTheTurn(listOf(tally("Actor.a", crits = 2, name = "Jhod")))?.actorName)
        assertNull(spotlightOfTheTurn(listOf(tally("Actor.a", crits = 2)))?.actorName)
    }

    // --- persisted enum values -------------------------------------------------------------

    @Test
    fun persistedEnumValuesRoundTripAndUnknownStringsDropToNull() {
        ContributionKind.entries.forEach { assertEquals(it, ContributionKind.fromValue(it.value), it.name) }
        DeedCategory.entries.forEach { assertEquals(it, DeedCategory.fromValue(it.value), it.name) }
        SpotlightKind.entries.forEach { assertEquals(it, SpotlightKind.fromValue(it.value), it.name) }
        // A row written by a newer version must drop out, never take a tick down.
        assertNull(ContributionKind.fromValue("battleCommand"))
        assertNull(ContributionKind.fromValue(null))
        assertNull(DeedCategory.fromValue("mercantile"))
        assertNull(DeedCategory.fromValue(null))
        assertNull(SpotlightKind.fromValue("villain"))
        assertNull(SpotlightKind.fromValue(null))
        // The spotlight values ARE the leaf i18n keys the literal resolver switches on.
        assertEquals("busiest", SpotlightKind.BUSIEST.value)
        assertEquals("critStar", SpotlightKind.CRIT_STAR.value)
        assertEquals("blunderer", SpotlightKind.BLUNDERER.value)
        assertEquals("diplomat", SpotlightKind.DIPLOMAT.value)
        assertEquals("eventHero", SpotlightKind.EVENT_HERO.value)
        assertEquals("checkCritFail", ContributionKind.CHECK_CRIT_FAIL.value)
        assertEquals("diplomatic", DeedCategory.DIPLOMATIC.value)
    }

    // --- the double-count discipline -------------------------------------------------------

    @Test
    fun personalRenownNeverFeedsKingdomStanding() {
        val kingdomStanding = 10
        var current = pc
        repeat(30) { current = accrueRenown(current, deed(ContributionKind.CHECK_CRIT, factionName = "Pitax")).renown }
        val personal = current.factionRenown.getValue("Pitax")
        assertEquals(FACTION_RENOWN_SOFT_CAP, personal)
        // Read as a kingdom standing this PC's number would be FRIENDLY — but the kingdom's own
        // standing is neither an input to nor an output of anything in this engine, so it is still
        // exactly what the GM last set it to.
        assertEquals(FactionAttitude.FRIENDLY, attitudeFor(personal))
        assertEquals(FactionAttitude.INDIFFERENT, attitudeFor(kingdomStanding))
        assertTrue(FACTION_RENOWN_SOFT_CAP < MAX_FACTION_STANDING)
    }

    // ---- the real catalog (plan Appendix), now that it exists ----

    @Test
    fun evaluateEpithets_negativeNemesisEpithet() {
        // scourgeOfPitax is the one entry keyed on being HATED; a PC who never met Pitax must not
        // earn it, which is the whole reason worstFactionRenown returns null rather than 0.
        val hated = PcRenown(actorUuid = "Actor.a", factionRenown = mapOf("Pitax" to -30))
        val awards = evaluateEpithets(hated, epithetContextFor(hated, holdsRulerRole = false))
        assertTrue(awards.any { it.epithetId == "scourgeOfPitax" })

        val stranger = PcRenown(actorUuid = "Actor.b")
        assertFalse(
            evaluateEpithets(stranger, epithetContextFor(stranger, holdsRulerRole = false))
                .any { it.epithetId == "scourgeOfPitax" },
            "a PC who never met Pitax is not its scourge",
        )

        val justShort = PcRenown(actorUuid = "Actor.c", factionRenown = mapOf("Pitax" to -24))
        assertFalse(
            evaluateEpithets(justShort, epithetContextFor(justShort, holdsRulerRole = false))
                .any { it.epithetId == "scourgeOfPitax" },
            "-24 is one short of the -25 threshold",
        )
    }

    @Test
    fun epithetContextFor_derivesEveryCatalogCondition() {
        // The plan calls this "the guard that keeps it true": every catalog entry must be
        // decidable from the fields EpithetContext actually carries. A condition that reached for
        // a field the context lacks could not compile -- but one that silently reads a DEFAULT
        // (an absent map key, a zero) would pass unnoticed, so drive each entry to its own
        // threshold and prove it fires.
        val maxed = PcRenown(
            actorUuid = "Actor.max",
            populace = 100,
            factionRenown = mapOf("Restov" to 40, "Narlmarches" to 40),
            lifetimeCrits = 50,
            lifetimeCritFails = 50,
            lifetimeActivities = 50,
            lifetimeEvents = 50,
            roleSuccesses = Leader.entries.associateWith { 50 },
            roleCrits = Leader.entries.associateWith { 50 },
            categoryActivities = DeedCategory.entries.associateWith { 50 },
        )
        val ctx = epithetContextFor(maxed, holdsRulerRole = true)
        val earned = evaluateEpithets(maxed, ctx).map { it.epithetId }.toSet()
        // every entry except the nemesis one, which requires NEGATIVE standing
        val expected = EPITHET_CATALOG.map { it.id }.toSet() - "scourgeOfPitax"
        assertEquals(expected, earned, "a maxed-out PC earns every non-nemesis epithet")
        assertTrue(EPITHET_CATALOG.size == 12, "the plan authors twelve")
    }

    @Test
    fun catalogEntriesSeeOnlyTheirOwnFactionNames() {
        // feyFriend names Narlmarches; renown with a DIFFERENT faction must not earn it, or the
        // entry's factionNames list is doing nothing and any friendship would qualify.
        val elsewhere = PcRenown(actorUuid = "Actor.x", factionRenown = mapOf("Restov" to 40))
        val awards = evaluateEpithets(elsewhere, epithetContextFor(elsewhere, holdsRulerRole = false))
        assertTrue(awards.any { it.epithetId == "restovsHammer" }, "Restov standing earns Restov's Hammer")
        assertFalse(awards.any { it.epithetId == "feyFriend" }, "Restov standing is not fey friendship")
    }

    @Test
    fun theCatalogsPerksAreTheOnesThePlanAuthored() {
        // pins the perk column, so silently dropping or swapping one is caught
        assertEquals(RenownPerk.PurchaseAccess(1), perkForEpithet("theUntiring"))
        assertEquals(RenownPerk.PurchaseAccess(2), perkForEpithet("coinCounter"))
        assertEquals(RenownPerk.PurchaseAccess(2), perkForEpithet("peoplesChampion"))
        assertEquals(RenownPerk.Invitation("Narlmarches"), perkForEpithet("feyFriend"))
        assertNull(perkForEpithet("theIronhand"), "not every epithet carries a perk")
        assertNull(perkForEpithet("noSuchEpithet"))
    }

    // ---- weak spots the review proved would survive a mutation ----

    @Test
    fun aTurnOfNothingButFumblesStillTakesTheBlundererLine() {
        // score is 0 but three actions happened; filtering on score instead of effort would erase
        // this PC entirely and the blunderer line could never fire for its most deserving case
        val pick = spotlightOfTheTurn(
            listOf(
                tally("Actor.fumbler", checks = 0, critFails = 3),
                tally("Actor.busy", checks = 5),
            )
        )!!
        assertEquals(SpotlightKind.BLUNDERER, pick.kind)
        assertEquals("Actor.fumbler", pick.actorUuid)
    }

    @Test
    fun anExactWeightTieDoesNotCountAsDominance() {
        // crits*3 == events*2 exactly; "STRICTLY dominates" means neither wins its arm
        val pick = spotlightOfTheTurn(listOf(tally("Actor.a", crits = 2, events = 3)))!!
        assertEquals(SpotlightKind.BUSIEST, pick.kind, "an equal split is busy-ness, not a specialty")
    }

    @Test
    fun persistedEnumStringsAreTheStorageContract() {
        // round-tripping fromValue(value) is self-consistent under ANY renaming, so pin literals:
        // these strings are what lands in the save file.
        assertEquals("checkSuccess", ContributionKind.CHECK_SUCCESS.value)
        assertEquals("checkCrit", ContributionKind.CHECK_CRIT.value)
        assertEquals("checkFailure", ContributionKind.CHECK_FAILURE.value)
        assertEquals("checkCritFail", ContributionKind.CHECK_CRIT_FAIL.value)
        assertEquals("activity", ContributionKind.ACTIVITY.value)
        assertEquals("eventResolved", ContributionKind.EVENT_RESOLVED.value)
        assertEquals("petitionAnswered", ContributionKind.PETITION_ANSWERED.value)
        assertEquals("diplomatic", DeedCategory.DIPLOMATIC.value)
        assertEquals("martial", DeedCategory.MARTIAL.value)
        assertEquals("other", DeedCategory.OTHER.value)
    }

    @Test
    fun theProvisionalTuningConstantsArePinnedToTheirValues() {
        // asserted symbolically elsewhere, which cannot catch a changed number
        assertEquals(40, FACTION_RENOWN_SOFT_CAP)
        assertEquals(2, MAX_PURCHASE_ACCESS_TIER)
        assertEquals(0, purchaseAccessLevelsForTier(0))
        assertEquals(2, purchaseAccessLevelsForTier(3), "tier 3 clamps into the closed 0..2 range")
    }

    @Test
    fun everyCorruptCountIsFlooredNotJustTheFirst() {
        // the review found four of five floors unexercised
        assertEquals(0, spotlightScore(tally("Actor.n", events = -9)))
        assertEquals(0, spotlightScore(tally("Actor.n", activities = -9)))
        assertEquals(0, spotlightActionCount(tally("Actor.n", crits = -9)))
        assertEquals(0, spotlightActionCount(tally("Actor.n", activities = -9)))
        assertEquals(0, spotlightActionCount(tally("Actor.n", events = -9)))
        assertEquals(0, spotlightActionCount(tally("Actor.n", critFails = -9)))
    }

    @Test
    fun resolvingAnEventDoesNotInflateCategoryActivityTallies() {
        // Appendix #1 reads categoryActivities[DIPLOMATIC]; if an event bumped it, the
        // Bridge-Builder would be earned by doing something else entirely
        val before = PcRenown(actorUuid = "Actor.a")
        val after = accrueRenown(
            before,
            Contribution(kind = ContributionKind.EVENT_RESOLVED, leader = Leader.RULER, category = DeedCategory.DIPLOMATIC),
        ).renown
        assertEquals(before.categoryActivities, after.categoryActivities, "an event is not an activity")
        assertEquals(1, after.lifetimeEvents)
    }

    @Test
    fun answeringAPetitionMovesNoLifetimeCounter() {
        val before = PcRenown(actorUuid = "Actor.a")
        val after = accrueRenown(
            before,
            Contribution(kind = ContributionKind.PETITION_ANSWERED, leader = Leader.RULER),
        ).renown
        assertEquals(before.lifetimeActivities, after.lifetimeActivities)
        assertEquals(before.lifetimeEvents, after.lifetimeEvents)
        assertTrue(after.populace > before.populace, "it credits populace renown only")
    }

    @Test
    fun aFumbleIsNeverBankedAsACategoryCriticalSuccess() {
        val before = PcRenown(actorUuid = "Actor.a")
        val after = accrueRenown(
            before,
            Contribution(kind = ContributionKind.CHECK_CRIT_FAIL, leader = Leader.RULER, category = DeedCategory.MARTIAL),
        ).renown
        assertEquals(before.categoryCrits, after.categoryCrits)
        assertEquals(1, after.lifetimeCritFails)
    }


    @Test
    fun bestAndWorstFactionRenownDivergeAcrossMultipleNames() {
        // The scourgeOfPitax entry names ONE faction today, so best/worst are identical there and
        // swapping them is an equivalent mutation *for the current catalog*. That makes the rule
        // worth pinning on the helpers instead: a nemesis condition must read the WORST standing,
        // so that adding a second name later cannot let a PC loved by one and hated by the other
        // earn it. Verified equivalent 2026-08-26; this test is what keeps it safe to extend.
        val mixed = PcRenown(
            actorUuid = "Actor.mixed",
            factionRenown = mapOf("Pitax" to 40, "Irovetti" to -30),
        )
        val ctx = epithetContextFor(mixed, holdsRulerRole = false)
        val bothNames = listOf("Pitax", "Irovetti")
        assertEquals(40, ctx.bestFactionRenown(bothNames))
        assertEquals(-30, ctx.worstFactionRenown(bothNames))
        assertTrue(
            (ctx.worstFactionRenown(bothNames) ?: Int.MAX_VALUE) <= -25,
            "the nemesis rule fires on the worst standing",
        )
        assertFalse(
            (ctx.bestFactionRenown(bothNames) ?: Int.MAX_VALUE) <= -25,
            "and must NOT fire on the best -- this is the swap the catalog must never make",
        )
    }

}

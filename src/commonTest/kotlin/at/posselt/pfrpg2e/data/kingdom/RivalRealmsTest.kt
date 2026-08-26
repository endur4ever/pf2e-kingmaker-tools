package at.posselt.pfrpg2e.data.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RivalRealmsTest {

    private fun row(
        label: String,
        size: Int,
        fame: Int = 0,
        armies: Int = 0,
        isPlayer: Boolean = false,
        rank: Int = 0,
    ) = RivalStandingRow(label = label, size = size, fame = fame, armyCount = armies, isPlayer = isPlayer, rank = rank)

    // --- growStat: fractional accrual ---

    @Test
    fun fractionalAccrualCrossesOnceAndCarriesTheRemainder() {
        val step = growStat(value = 10, accrual = 0.6, perTurn = 0.5, paused = false)
        assertEquals(11, step.value)
        assertEquals(0.1, step.accrual, ACCRUAL_EPSILON)
        assertEquals(1, step.incremented)
    }

    @Test
    fun accrualReachingExactlyOnePointTicksOver() {
        val step = growStat(value = 3, accrual = 0.5, perTurn = 0.5, paused = false)
        assertEquals(4, step.value)
        assertEquals(1, step.incremented)
        assertEquals(0.0, step.accrual, ACCRUAL_EPSILON)
    }

    @Test
    fun accrualJustBelowOnePointDoesNotTickOver() {
        val step = growStat(value = 3, accrual = 0.4, perTurn = 0.5, paused = false)
        assertEquals(3, step.value)
        assertEquals(0, step.incremented)
        assertEquals(0.9, step.accrual, ACCRUAL_EPSILON)
    }

    @Test
    fun aRateAboveOneAddsEveryWholePointInTheSameTurn() {
        val step = growStat(value = 0, accrual = 0.0, perTurn = 2.5, paused = false)
        assertEquals(2, step.value)
        assertEquals(2, step.incremented)
        assertEquals(0.5, step.accrual, ACCRUAL_EPSILON)
    }

    @Test
    fun accrualIsLosslessOverTenTurnsAtAThirdOfAPoint() {
        // 0.3 is not representable in binary: a literal `accrual >= 1.0` comparison drifts down and
        // awards only 2 points over these ten turns. The plan promises the dial exactly.
        var value = 0
        var accrual = 0.0
        repeat(10) {
            val step = growStat(value = value, accrual = accrual, perTurn = 0.3, paused = false)
            value = step.value
            accrual = step.accrual
        }
        assertEquals(3, value)
    }

    @Test
    fun aHalfPointRateTicksOnEverySecondTurn() {
        var value = 4
        var accrual = 0.0
        val ticks = mutableListOf<Int>()
        repeat(4) {
            val step = growStat(value = value, accrual = accrual, perTurn = 0.5, paused = false)
            value = step.value
            accrual = step.accrual
            ticks.add(step.incremented)
        }
        assertEquals(listOf(0, 1, 0, 1), ticks)
        assertEquals(6, value)
    }

    // --- growStat: the guards ---

    @Test
    fun pausedGrowthLeavesTheValueAndTheCarriedAccrualUntouched() {
        val step = growStat(value = 12, accrual = 0.9, perTurn = 5.0, paused = true)
        assertEquals(12, step.value)
        assertEquals(0.9, step.accrual, ACCRUAL_EPSILON)
        assertEquals(0, step.incremented)
    }

    @Test
    fun aStatWithNoRateNeverTicksOverEvenHoldingAlmostAFullPoint() {
        // This is the invariant that makes a wartime EXPAND headline unreachable: at war the
        // adapter forces the size rate to zero, and a zero rate must leave the carry alone forever.
        var value = 10
        var accrual = 0.99
        repeat(50) {
            val step = growStat(value = value, accrual = accrual, perTurn = 0.0, paused = false)
            assertEquals(0, step.incremented)
            value = step.value
            accrual = step.accrual
        }
        assertEquals(10, value)
        assertEquals(0.99, accrual, ACCRUAL_EPSILON)
    }

    @Test
    fun aNegativeRateNeverShrinksAStat() {
        val step = growStat(value = 9, accrual = 0.6, perTurn = -1.0, paused = false)
        assertEquals(9, step.value)
        assertEquals(0.6, step.accrual, ACCRUAL_EPSILON)
        assertEquals(0, step.incremented)
    }

    @Test
    fun aNonFiniteRateIsIgnoredInsteadOfHangingOrPoisoningTheAccrual() {
        for (rate in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val step = growStat(value = 7, accrual = 0.6, perTurn = rate, paused = false)
            assertEquals(7, step.value, "rate $rate must not move the stat")
            assertEquals(0.6, step.accrual, ACCRUAL_EPSILON, "rate $rate must not touch the carry")
            assertEquals(0, step.incremented)
        }
    }

    @Test
    fun aCorruptCarriedAccrualIsResetRatherThanSpread() {
        for (corrupt in listOf(Double.NaN, Double.NEGATIVE_INFINITY, -3.0)) {
            val step = growStat(value = 4, accrual = corrupt, perTurn = 0.5, paused = false)
            assertEquals(4, step.value, "carry $corrupt must not move the stat")
            assertEquals(0.5, step.accrual, ACCRUAL_EPSILON, "carry $corrupt must restart from zero")
            assertEquals(0, step.incremented)
        }
    }

    @Test
    fun growthSaturatesAtTheIntCeilingInsteadOfWrappingNegative() {
        val step = growStat(value = Int.MAX_VALUE - 2, accrual = 0.0, perTurn = 10.0, paused = false)
        assertEquals(Int.MAX_VALUE, step.value)
        assertEquals(2, step.incremented, "the reported delta must match what the value actually moved")
        assertTrue(step.value > 0, "a saturating realm must not rank last")
    }

    @Test
    fun previewingATurnAndCommittingItProduceIdenticalGrowth() {
        val profile = RivalGrowthProfile(sizePerTurn = 0.33, famePerTurn = 0.5, armyPerTurn = 0.5)
        val preview = growStat(value = 12, accrual = 0.8, perTurn = profile.sizePerTurn, paused = false)
        val commit = growStat(value = 12, accrual = 0.8, perTurn = profile.sizePerTurn, paused = false)
        assertEquals(preview, commit)
    }

    // --- RivalGrowthProfile ---

    @Test
    fun anUnsetProfileIsDormantAndGrowsNothing() {
        val dormant = RivalGrowthProfile()
        assertEquals(0.0, dormant.sizePerTurn, ACCRUAL_EPSILON)
        assertEquals(0.0, dormant.famePerTurn, ACCRUAL_EPSILON)
        assertEquals(0.0, dormant.armyPerTurn, ACCRUAL_EPSILON)
        val step = growStat(value = 5, accrual = 0.9, perTurn = dormant.sizePerTurn, paused = false)
        assertEquals(5, step.value)
        assertEquals(0, step.incremented)
    }

    @Test
    fun eachProfileRateDrivesItsOwnStatOverFourTurns() {
        val profile = RivalGrowthProfile(sizePerTurn = 0.5, famePerTurn = 0.25, armyPerTurn = 0.0)
        var size = 8
        var fame = 2
        var armies = 1
        var sizeCarry = 0.0
        var fameCarry = 0.0
        var armyCarry = 0.0
        repeat(4) {
            growStat(size, sizeCarry, profile.sizePerTurn, paused = false).let {
                size = it.value
                sizeCarry = it.accrual
            }
            growStat(fame, fameCarry, profile.famePerTurn, paused = false).let {
                fame = it.value
                fameCarry = it.accrual
            }
            growStat(armies, armyCarry, profile.armyPerTurn, paused = false).let {
                armies = it.value
                armyCarry = it.accrual
            }
        }
        assertEquals(10, size)
        assertEquals(3, fame)
        assertEquals(1, armies)
    }

    // --- rivalPowerScore ---

    @Test
    fun aRealmWithNothingScoresNothing() {
        assertEquals(0, rivalPowerScore(size = 0, fame = 0, armyCount = 0))
    }

    @Test
    fun eachStatIsScoredWithItsOwnWeight() {
        assertEquals(RIVAL_POWER_SIZE_WEIGHT, rivalPowerScore(size = 1, fame = 0, armyCount = 0))
        assertEquals(RIVAL_POWER_FAME_WEIGHT, rivalPowerScore(size = 0, fame = 1, armyCount = 0))
        assertEquals(RIVAL_POWER_ARMY_WEIGHT, rivalPowerScore(size = 0, fame = 0, armyCount = 1))
    }

    @Test
    fun landOutweighsArmiesAndArmiesOutweighRenown() {
        // The documented ratio, asserted as orderings so retuning the weights only breaks this
        // test if the design statement itself changed.
        assertTrue(rivalPowerScore(11, 0, 0) > rivalPowerScore(10, 0, 1))
        assertTrue(rivalPowerScore(10, 0, 1) > rivalPowerScore(10, 2, 0))
    }

    @Test
    fun scoreIsStrictlyIncreasingInEveryStat() {
        val base = rivalPowerScore(size = 6, fame = 3, armyCount = 2)
        assertTrue(rivalPowerScore(7, 3, 2) > base, "growing must never lower a realm's power")
        assertTrue(rivalPowerScore(6, 4, 2) > base)
        assertTrue(rivalPowerScore(6, 3, 3) > base)
    }

    @Test
    fun scoreIsAdditiveAcrossStats() {
        assertEquals(
            rivalPowerScore(2, 0, 0) + rivalPowerScore(0, 3, 0) + rivalPowerScore(0, 0, 4),
            rivalPowerScore(2, 3, 4),
        )
    }

    // --- RivalStandingRow ---

    @Test
    fun aRowScoresItselfFromItsOwnStatsByDefault() {
        // Not a tautology: the default has to reach rivalPowerScore with the stats in the right
        // order, and a fame/armies swap in the default lands on a different number than this.
        val pitax = row("Pitax", size = 14, fame = 4, armies = 3)
        assertEquals(rivalPowerScore(14, 4, 3), pitax.score)
    }

    // --- rankStandings ---

    @Test
    fun rankingNoRowsYieldsNoRows() {
        assertEquals(emptyList<RivalStandingRow>(), rankStandings(emptyList()))
    }

    @Test
    fun standingsSortByScoreDescendingWithSequentialRanks() {
        val ranked = rankStandings(
            listOf(
                row("Mivon", size = 5),
                row("Pitax", size = 14),
                row("Brevoy", size = 9),
            )
        )
        assertEquals(listOf("Pitax", "Brevoy", "Mivon"), ranked.map { it.label })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
    }

    @Test
    fun tiedScoresKeepTheCallersInputOrderInBothDirections() {
        // 6 size and 3 size + 6 armies both score the same, so the tie is real and is not a tie on
        // any single stat -- it can only be resolved by the stable sort keeping input order.
        val byLand = row("Numeria", size = 6)
        val byArmies = row("Iobaria", size = 3, armies = 6)
        assertEquals(byLand.score, byArmies.score, "fixture must actually tie")

        val landFirst = rankStandings(listOf(byLand, byArmies, row("Varnhold", size = 1)))
        assertEquals(listOf("Numeria", "Iobaria", "Varnhold"), landFirst.map { it.label })
        assertEquals(listOf(1, 2, 3), landFirst.map { it.rank })

        val armiesFirst = rankStandings(listOf(byArmies, byLand, row("Varnhold", size = 1)))
        assertEquals(listOf("Iobaria", "Numeria", "Varnhold"), armiesFirst.map { it.label })
    }

    @Test
    fun thePlayerRowIsRankedInlineAndKeepsItsFlag() {
        val ranked = rankStandings(
            listOf(
                row("Pitax", size = 14, fame = 4, armies = 3),
                row("Our Kingdom", size = 11, fame = 6, armies = 2, isPlayer = true),
                row("Varnhold", size = 2),
            )
        )
        assertEquals(2, ranked.single { it.isPlayer }.rank, "the player is ranked among the rivals")
        assertEquals("Our Kingdom", ranked[1].label)
        assertEquals(listOf(false, true, false), ranked.map { it.isPlayer })
    }

    @Test
    fun aStaleRankOnTheInputIsRecomputedNotTrusted() {
        val ranked = rankStandings(listOf(row("Weak", size = 1, rank = 99), row("Strong", size = 2, rank = 99)))
        assertEquals(listOf("Strong", "Weak"), ranked.map { it.label })
        assertEquals(listOf(1, 2), ranked.map { it.rank })
    }

    @Test
    fun rankingSortsByTheRowsOwnScoreNotByReDerivingItFromTheStats() {
        // The player row's score may be derived differently (the players have no single army
        // count), so whatever the caller put on the row has to remain the sort key.
        val sprawling = row("Sprawling", size = 40)
        val weighted = row("Small but weighted", size = 1).copy(score = sprawling.score + 1)
        val ranked = rankStandings(listOf(sprawling, weighted))
        assertEquals("Small but weighted", ranked.first().label)
        assertEquals(1, ranked.first().rank)
    }

    // --- headlineTemplateIndex ---

    @Test
    fun theTemplateIndexAlwaysLandsInsideThePool() {
        for (pool in RivalHeadlinePool.entries) {
            for (turn in -3..12) {
                for (faction in listOf("Pitax", "Brevoy", "", "Hargulka's Trolls")) {
                    for (stat in RivalStat.entries) {
                        val index = headlineTemplateIndex(turn, faction, stat, pool.poolSize)
                        assertTrue(
                            index >= 0 && index < pool.poolSize,
                            "$pool turn $turn '$faction' $stat produced $index",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aDeeplyNegativeTurnStillProducesAnInBoundsIndex() {
        // turn * 31 swamps a one-character name's hash, so the raw remainder here really is
        // negative; without the folding modulo this would index a template list at -2.
        for (poolSize in listOf(2, 3)) {
            val index = headlineTemplateIndex(turn = -1000, factionRef = "A", stat = RivalStat.SIZE, poolSize = poolSize)
            assertTrue(index in 0 until poolSize, "poolSize $poolSize produced $index")
        }
    }

    @Test
    fun anEmptyPoolIndexesToZeroInsteadOfDividingByZero() {
        assertEquals(0, headlineTemplateIndex(4, "Pitax", RivalStat.ARMY, poolSize = 0))
        assertEquals(0, headlineTemplateIndex(4, "Pitax", RivalStat.ARMY, poolSize = -2))
    }

    @Test
    fun theSameTurnFactionAndStatAlwaysPickTheSameTemplate() {
        val inputs = listOf(1, 2, 17).flatMap { turn ->
            listOf("Pitax", "Brevoy").flatMap { faction ->
                RivalStat.entries.map { stat -> Triple(turn, faction, stat) }
            }
        }
        val first = inputs.map { (turn, faction, stat) -> headlineTemplateIndex(turn, faction, stat, 3) }
        val second = inputs.map { (turn, faction, stat) -> headlineTemplateIndex(turn, faction, stat, 3) }
        assertEquals(first, second, "a rolled headline would diverge between preview and commit")
    }

    @Test
    fun consecutiveTurnsRotateTheTemplate() {
        for (poolSize in listOf(2, 3)) {
            for (turn in 0..5) {
                assertTrue(
                    headlineTemplateIndex(turn, "Brevoy", RivalStat.FAME, poolSize) !=
                        headlineTemplateIndex(turn + 1, "Brevoy", RivalStat.FAME, poolSize),
                    "turn $turn and ${turn + 1} repeated a template in a $poolSize-slot pool",
                )
            }
        }
    }

    @Test
    fun twoStatsGrowingOnTheSameTurnDrawDifferentTemplates() {
        // The stat term is chosen so these cannot collide: FAME sits 7 slots from SIZE and 7 is
        // odd, so it can never share a slot in a 2-slot pool; ARMY sits 14 slots away and 14 is
        // not a multiple of 3, so it can never share a slot in the 3-slot EXPAND pool.
        for (turn in 0..5) {
            assertTrue(
                headlineTemplateIndex(turn, "Pitax", RivalStat.SIZE, 3) !=
                    headlineTemplateIndex(turn, "Pitax", RivalStat.ARMY, 3),
                "turn $turn collided SIZE with ARMY",
            )
            assertTrue(
                headlineTemplateIndex(turn, "Pitax", RivalStat.SIZE, 2) !=
                    headlineTemplateIndex(turn, "Pitax", RivalStat.FAME, 2),
                "turn $turn collided SIZE with FAME",
            )
        }
    }

    @Test
    fun differentRealmsDoNotAllShareOneTemplate() {
        val indices = listOf("A", "B", "C")
            .map { headlineTemplateIndex(turn = 3, factionRef = it, stat = RivalStat.SIZE, poolSize = 3) }
            .toSet()
        assertTrue(indices.size > 1, "every realm drew template $indices on the same turn")
    }

    // --- poolFor and the pools ---

    @Test
    fun onlyArmyHeadlinesHaveAWartimeVariant() {
        assertEquals(RivalHeadlinePool.ARMY, poolFor(RivalStat.ARMY, atWar = false))
        assertEquals(RivalHeadlinePool.ARMY_WAR, poolFor(RivalStat.ARMY, atWar = true))
        assertEquals(RivalHeadlinePool.EXPAND, poolFor(RivalStat.SIZE, atWar = false))
        assertEquals(RivalHeadlinePool.EXPAND, poolFor(RivalStat.SIZE, atWar = true))
        assertEquals(RivalHeadlinePool.FAME, poolFor(RivalStat.FAME, atWar = false))
        assertEquals(RivalHeadlinePool.FAME, poolFor(RivalStat.FAME, atWar = true))
    }

    @Test
    fun poolSizesMatchTheShippedTemplateCounts() {
        // These counts are the contract with the flattened i18n keys (expand1..3, fame1..2,
        // army1..2, armyWar1..2) and the literal `when` in the jsMain localizer.
        assertEquals(3, RivalHeadlinePool.EXPAND.poolSize)
        assertEquals(2, RivalHeadlinePool.FAME.poolSize)
        assertEquals(2, RivalHeadlinePool.ARMY.poolSize)
        assertEquals(2, RivalHeadlinePool.ARMY_WAR.poolSize)
        assertTrue(RivalHeadlinePool.entries.all { it.poolSize > 0 }, "an empty pool has no template to draw")
    }

    // --- enum values: unknown stored data drops out, never throws ---

    @Test
    fun statValuesRoundTripAndUnknownStoredValuesDropOut() {
        RivalStat.entries.forEach { assertEquals(it, RivalStat.fromValue(it.value)) }
        assertNull(RivalStat.fromValue(null))
        assertNull(RivalStat.fromValue(""))
        assertNull(RivalStat.fromValue("armies"))
        assertNull(RivalStat.fromValue("SIZE"))
    }

    @Test
    fun headlinePoolValuesRoundTripAndUnknownOverridesFallThrough() {
        RivalHeadlinePool.entries.forEach { assertEquals(it, RivalHeadlinePool.fromValue(it.value)) }
        assertNull(RivalHeadlinePool.fromValue(null))
        assertNull(RivalHeadlinePool.fromValue(""))
        assertNull(RivalHeadlinePool.fromValue("army-war"))
        assertNull(RivalHeadlinePool.fromValue("ARMY_WAR"))
    }

    @Test
    fun statDeclarationOrderIsAnInputToTheHeadlineHash() {
        // Reordering these silently re-rolls every headline in every campaign, so the order is
        // pinned here rather than left as an accident of the enum body.
        assertEquals(listOf(RivalStat.SIZE, RivalStat.FAME, RivalStat.ARMY), RivalStat.entries.toList())
    }
}

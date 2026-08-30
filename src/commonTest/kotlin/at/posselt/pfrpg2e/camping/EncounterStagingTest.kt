package at.posselt.pfrpg2e.camping

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncounterStagingTest {
    private val center = StagePoint(1000.0, 1000.0)

    /** Distance from the ring centre to a token's CENTRE, undoing the half-cell origin shift. */
    private fun radiusOf(point: StagePoint, gridSizePx: Double): Double {
        val cx = point.x + gridSizePx / 2.0
        val cy = point.y + gridSizePx / 2.0
        return sqrt((cx - center.x) * (cx - center.x) + (cy - center.y) * (cy - center.y))
    }

    @Test
    fun singleCreatureSitsAboveTheCentreAtTheRadius() {
        val points = ringPlacement(center, radiusPx = 200.0, count = 1, gridSizePx = 100.0)
        assertEquals(1, points.size)
        // straight up: same column, 200px above, minus the half-cell origin shift
        assertTrue(abs(points[0].x - (center.x - 50.0)) < 1e-9)
        assertTrue(abs(points[0].y - (center.y - 200.0 - 50.0)) < 1e-9)
    }

    @Test
    fun creaturesSpreadEvenlyAtTheSameRadius() {
        val points = ringPlacement(center, radiusPx = 300.0, count = 4, gridSizePx = 100.0)
        assertEquals(4, points.size)
        points.forEach { assertTrue(abs(radiusOf(it, 100.0) - 300.0) < 1e-6, "off the ring: $it") }
        // four creatures land on the four compass points, so every pair of neighbours differs
        val xs = points.map { it.x }.distinct()
        val ys = points.map { it.y }.distinct()
        assertTrue(xs.size >= 3 && ys.size >= 3, "not spread: xs=$xs ys=$ys")
    }

    @Test
    fun originsAreOffsetSoTokenCentresSitOnTheRing() {
        val grid = 140.0
        val points = ringPlacement(center, radiusPx = 280.0, count = 3, gridSizePx = grid)
        // the ORIGIN is half a cell short of the ring; the CENTRE is exactly on it
        points.forEach { assertTrue(abs(radiusOf(it, grid) - 280.0) < 1e-6) }
        val rawRadius = sqrt(
            (points[0].x - center.x) * (points[0].x - center.x) +
                (points[0].y - center.y) * (points[0].y - center.y)
        )
        assertTrue(abs(rawRadius - 280.0) > 1.0, "origins were not offset at all")
    }

    @Test
    fun zeroOrNegativeCountPlacesNothing() {
        assertEquals(0, ringPlacement(center, 200.0, 0, 100.0).size)
        assertEquals(0, ringPlacement(center, 200.0, -3, 100.0).size)
    }

    @Test
    fun placementIsDeterministic() {
        val a = ringPlacement(center, 200.0, 5, 100.0)
        val b = ringPlacement(center, 200.0, 5, 100.0)
        assertEquals(a, b)
    }

    @Test
    fun startAngleRotatesTheWholeRing() {
        val up = ringPlacement(center, 200.0, 1, 100.0)
        val right = ringPlacement(center, 200.0, 1, 100.0, startAngleRad = 0.0)
        assertTrue(abs(right[0].x - (center.x + 200.0 - 50.0)) < 1e-9)
        assertTrue(abs(right[0].y - (center.y - 50.0)) < 1e-9)
        assertTrue(up != right)
        // a full turn is the same ring
        val wrapped = ringPlacement(center, 200.0, 1, 100.0, startAngleRad = -PI / 2 + 2 * PI)
        assertTrue(abs(wrapped[0].x - up[0].x) < 1e-6 && abs(wrapped[0].y - up[0].y) < 1e-6)
    }

    @Test
    fun feetConvertThroughTheScenesOwnGridScale() {
        assertEquals(1200.0, feetToPixels(60.0, gridDistanceFt = 5.0, gridSizePx = 100.0))
        assertEquals(2400.0, feetToPixels(120.0, gridDistanceFt = 5.0, gridSizePx = 100.0))
        // a 10-ft grid halves the pixels for the same distance
        assertEquals(600.0, feetToPixels(60.0, gridDistanceFt = 10.0, gridSizePx = 100.0))
        // a malformed scene (0 ft per square) must not divide by zero
        assertEquals(0.0, feetToPixels(60.0, gridDistanceFt = 0.0, gridSizePx = 100.0))
    }

    @Test
    fun spawnCountSumsAndIgnoresNegatives() {
        assertEquals(5, manifestSpawnCount(listOf(StageCreature("a", 2), StageCreature("b", 3))))
        assertEquals(0, manifestSpawnCount(emptyList()))
        assertEquals(2, manifestSpawnCount(listOf(StageCreature("a", 2), StageCreature("b", -4))))
    }

    @Test
    fun creatureXpMatchesThePf2eTable() {
        assertEquals(40, creatureXpContribution(partyLevel = 5, creatureLevel = 5))
        assertEquals(30, creatureXpContribution(5, 4))
        assertEquals(20, creatureXpContribution(5, 3))
        assertEquals(15, creatureXpContribution(5, 2))
        assertEquals(10, creatureXpContribution(5, 1))
        assertEquals(60, creatureXpContribution(5, 6))
        assertEquals(80, creatureXpContribution(5, 7))
        assertEquals(120, creatureXpContribution(5, 8))
        assertEquals(160, creatureXpContribution(5, 9))
        // five levels below is off the bottom of the table: worth nothing
        assertEquals(0, creatureXpContribution(5, 0))
        // far above the party is capped at the +4 row rather than inventing a value
        assertEquals(160, creatureXpContribution(5, 20))
    }

    @Test
    fun budgetsScaleWithPartySize() {
        assertEquals(80, encounterBudget(4, EncounterThreat.MODERATE))
        assertEquals(120, encounterBudget(4, EncounterThreat.SEVERE))
        // a fifth character adds the per-character adjustment; a third removes it
        assertEquals(100, encounterBudget(5, EncounterThreat.MODERATE))
        assertEquals(60, encounterBudget(3, EncounterThreat.MODERATE))
        assertEquals(150, encounterBudget(5, EncounterThreat.SEVERE))
    }

    @Test
    fun threatBandReadsTheHighestBudgetReached() {
        assertEquals(EncounterThreat.MODERATE, threatForXp(4, 80))
        assertEquals(EncounterThreat.LOW, threatForXp(4, 79))
        assertEquals(EncounterThreat.SEVERE, threatForXp(4, 120))
        assertEquals(EncounterThreat.EXTREME, threatForXp(4, 200))
        // beneath every threshold is still trivial, never "less than trivial"
        assertEquals(EncounterThreat.TRIVIAL, threatForXp(4, 0))
    }

    @Test
    fun manifestTotalMultipliesByCount() {
        // three level-5 creatures against a level-5 party: 3 x 40 = severe for four PCs
        val total = manifestXpTotal(partyLevel = 5, creatureLevels = listOf(5 to 3))
        assertEquals(120, total)
        assertEquals(EncounterThreat.SEVERE, threatForXp(4, total))
        assertEquals(0, manifestXpTotal(5, emptyList()))
        assertEquals(40, manifestXpTotal(5, listOf(5 to 1, 5 to -2)))
    }
}

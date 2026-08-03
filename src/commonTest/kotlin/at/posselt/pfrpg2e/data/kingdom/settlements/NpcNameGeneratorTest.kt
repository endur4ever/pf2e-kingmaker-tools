package at.posselt.pfrpg2e.data.kingdom.settlements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ── SeededRng tests ──────────────────────────────────────────────────

class SeededRngTest {

    @Test
    fun sameSeedProducesSameSequence() {
        val a = SeededRng(42L)
        val b = SeededRng(42L)
        repeat(100) {
            assertEquals(a.nextInt(), b.nextInt())
        }
    }

    @Test
    fun differentSeedsProduceDifferentSequences() {
        val a = SeededRng(1L)
        val b = SeededRng(2L)
        val aVals = List(10) { a.nextInt() }
        val bVals = List(10) { b.nextInt() }
        assertNotEquals(aVals, bVals)
    }

    @Test
    fun nextIntBoundReturnsValuesInRange() {
        val rng = SeededRng(99L)
        repeat(1000) {
            val v = rng.nextInt(10)
            assertTrue(v >= 0)
            assertTrue(v < 10)
        }
    }

    @Test
    fun nextIntBoundOfOneAlwaysReturnsZero() {
        val rng = SeededRng(77L)
        repeat(50) {
            assertEquals(0, rng.nextInt(1))
        }
    }

    @Test
    fun nextIntRangeReturnsValuesInRange() {
        val rng = SeededRng(55L)
        repeat(1000) {
            val v = rng.nextInt(5, 10)
            assertTrue(v >= 5)
            assertTrue(v <= 10)
        }
    }

    @Test
    fun nextIntRangeSingleValue() {
        val rng = SeededRng(33L)
        repeat(20) {
            assertEquals(7, rng.nextInt(7, 7))
        }
    }

    @Test
    fun nextBooleanProducesBothValues() {
        val rng = SeededRng(123L)
        val results = List(100) { rng.nextBoolean() }
        assertTrue(results.any { it })
        assertTrue(results.any { !it })
    }

    @Test
    fun zeroSeedDoesNotStall() {
        val rng = SeededRng(0L)
        val values = List(10) { rng.nextInt() }
        assertTrue(true) // just verifying it doesn't throw
    }

    @Test
    fun largeSeedWorks() {
        val rng = SeededRng(Long.MAX_VALUE)
        val v = rng.nextInt(100)
        assertTrue(v in 0 until 100)
    }

    @Test
    fun negativeSeedWorks() {
        val rng = SeededRng(-42L)
        val v = rng.nextInt(100)
        assertTrue(v in 0 until 100)
    }

    @Test
    fun nonNegativeResults() {
        val rng = SeededRng(42L)
        repeat(1000) {
            assertTrue(rng.nextInt() >= 0)
        }
    }

    @Test
    fun nextIntMaxBound() {
        val rng = SeededRng(42L)
        repeat(100) {
            val v = rng.nextInt(1000)
            assertTrue(v in 0 until 1000)
        }
    }
}

// ── NpcNameTables tests ──────────────────────────────────────────────

class NpcNameTablesTest {

    @Test
    fun issianMaleNamesNotEmpty() {
        assertTrue(NpcNameTables.issianMaleGivenNames.isNotEmpty())
    }

    @Test
    fun issianFemaleNamesNotEmpty() {
        assertTrue(NpcNameTables.issianFemaleGivenNames.isNotEmpty())
    }

    @Test
    fun issianSurnamesNotEmpty() {
        assertTrue(NpcNameTables.issianSurnames.isNotEmpty())
    }

    @Test
    fun iobarianMaleNamesNotEmpty() {
        assertTrue(NpcNameTables.iobarianMaleGivenNames.isNotEmpty())
    }

    @Test
    fun iobarianFemaleNamesNotEmpty() {
        assertTrue(NpcNameTables.iobarianFemaleGivenNames.isNotEmpty())
    }

    @Test
    fun iobarianSurnamesNotEmpty() {
        assertTrue(NpcNameTables.iobarianSurnames.isNotEmpty())
    }

    @Test
    fun taldanMaleNamesNotEmpty() {
        assertTrue(NpcNameTables.taldanMaleGivenNames.isNotEmpty())
    }

    @Test
    fun taldanFemaleNamesNotEmpty() {
        assertTrue(NpcNameTables.taldanFemaleGivenNames.isNotEmpty())
    }

    @Test
    fun taldanSurnamesNotEmpty() {
        assertTrue(NpcNameTables.taldanSurnames.isNotEmpty())
    }

    @Test
    fun allCulturesHaveAtLeast30MaleNames() {
        NameCulture.entries.forEach { culture ->
            assertTrue(
                NpcNameTables.maleGivenNames(culture).size >= 30,
                "Culture $culture should have at least 30 male names",
            )
        }
    }

    @Test
    fun allCulturesHaveAtLeast30FemaleNames() {
        NameCulture.entries.forEach { culture ->
            assertTrue(
                NpcNameTables.femaleGivenNames(culture).size >= 30,
                "Culture $culture should have at least 30 female names",
            )
        }
    }

    @Test
    fun allCulturesHaveAtLeast25Surnames() {
        NameCulture.entries.forEach { culture ->
            assertTrue(
                NpcNameTables.surnames(culture).size >= 25,
                "Culture $culture should have at least 25 surnames",
            )
        }
    }

    @Test
    fun allMaleNamesAggregated() {
        val expected = NpcNameTables.issianMaleGivenNames +
            NpcNameTables.iobarianMaleGivenNames +
            NpcNameTables.taldanMaleGivenNames
        assertEquals(expected, NpcNameTables.allMaleGivenNames)
    }

    @Test
    fun allFemaleNamesAggregated() {
        val expected = NpcNameTables.issianFemaleGivenNames +
            NpcNameTables.iobarianFemaleGivenNames +
            NpcNameTables.taldanFemaleGivenNames
        assertEquals(expected, NpcNameTables.allFemaleGivenNames)
    }

    @Test
    fun allSurnamesAggregated() {
        val expected = NpcNameTables.issianSurnames +
            NpcNameTables.iobarianSurnames +
            NpcNameTables.taldanSurnames
        assertEquals(expected, NpcNameTables.allSurnames)
    }

    @Test
    fun noDuplicateNamesWithinCulture() {
        NameCulture.entries.forEach { culture ->
            val male = NpcNameTables.maleGivenNames(culture)
            val female = NpcNameTables.femaleGivenNames(culture)
            val surnames = NpcNameTables.surnames(culture)
            assertEquals(male.size, male.distinct().size, "Duplicate male names in $culture")
            assertEquals(female.size, female.distinct().size, "Duplicate female names in $culture")
            assertEquals(surnames.size, surnames.distinct().size, "Duplicate surnames in $culture")
        }
    }

    @Test
    fun issianPatronymicSuffixesNotEmpty() {
        assertTrue(NpcNameTables.issianPatronymicSuffixes.isNotEmpty())
    }

    @Test
    fun issianOccupationalSuffixesNotEmpty() {
        assertTrue(NpcNameTables.issianOccupationalSuffixes.isNotEmpty())
    }
}

// ── GeneratedNpcName tests ───────────────────────────────────────────

class GeneratedNpcNameTest {

    @Test
    fun fullNameCombinesGivenAndSurname() {
        val name = GeneratedNpcName(
            givenName = "Svetlana",
            surname = "Morozov",
            culture = NameCulture.ISSIAN,
            isFemale = true,
        )
        assertEquals("Svetlana Morozov", name.fullName)
    }

    @Test
    fun dataClassEquality() {
        val a = GeneratedNpcName("Ivan", "Volkov", NameCulture.ISSIAN, false)
        val b = GeneratedNpcName("Ivan", "Volkov", NameCulture.ISSIAN, false)
        assertEquals(a, b)
    }

    @Test
    fun dataClassCopy() {
        val original = GeneratedNpcName("Ivan", "Volkov", NameCulture.ISSIAN, false)
        val copied = original.copy(surname = "Orlov")
        assertEquals("Ivan", copied.givenName)
        assertEquals("Orlov", copied.surname)
        assertEquals(NameCulture.ISSIAN, copied.culture)
    }
}

// ── NpcNameGenerator tests ───────────────────────────────────────────

class NpcNameGeneratorTest {

    @Test
    fun generateReturnsValidName() {
        val gen = NpcNameGenerator(42L)
        val name = gen.generate()
        assertTrue(name.givenName.isNotEmpty())
        assertTrue(name.surname.isNotEmpty())
        assertTrue(name.fullName.isNotEmpty())
    }

    @Test
    fun generateFullNameHasTwoParts() {
        val gen = NpcNameGenerator(42L)
        val name = gen.generate()
        val parts = name.fullName.split(" ")
        assertEquals(2, parts.size)
        assertTrue(parts[0].isNotEmpty())
        assertTrue(parts[1].isNotEmpty())
    }

    @Test
    fun sameSeedProducesSameNames() {
        val gen1 = NpcNameGenerator(12345L)
        val gen2 = NpcNameGenerator(12345L)
        repeat(20) {
            assertEquals(gen1.generate(), gen2.generate())
        }
    }

    @Test
    fun differentSeedsProduceDifferentNames() {
        val gen1 = NpcNameGenerator(1L)
        val gen2 = NpcNameGenerator(2L)
        val names1 = List(10) { gen1.generate() }
        val names2 = List(10) { gen2.generate() }
        assertNotEquals(names1, names2)
    }

    @Test
    fun generateWithSpecificCulture() {
        val gen = NpcNameGenerator(42L)
        val name = gen.generateFromCulture(NameCulture.ISSIAN)
        assertEquals(NameCulture.ISSIAN, name.culture)
        assertTrue(NpcNameTables.issianMaleGivenNames.contains(name.givenName) ||
            NpcNameTables.issianFemaleGivenNames.contains(name.givenName))
        assertTrue(NpcNameTables.issianSurnames.contains(name.surname))
    }

    @Test
    fun generateFemaleProducesFemaleName() {
        val gen = NpcNameGenerator(42L)
        repeat(50) {
            val name = gen.generateFemale(NameCulture.TALDAN)
            assertEquals(true, name.isFemale)
            assertEquals(NameCulture.TALDAN, name.culture)
            assertTrue(NpcNameTables.taldanFemaleGivenNames.contains(name.givenName))
        }
    }

    @Test
    fun generateMaleProducesMaleName() {
        val gen = NpcNameGenerator(42L)
        repeat(50) {
            val name = gen.generateMale(NameCulture.IOBARIAN)
            assertEquals(false, name.isFemale)
            assertEquals(NameCulture.IOBARIAN, name.culture)
            assertTrue(NpcNameTables.iobarianMaleGivenNames.contains(name.givenName))
        }
    }

    @Test
    fun generateCountReturnsCorrectNumber() {
        val gen = NpcNameGenerator(42L)
        val names = gen.generate(10)
        assertEquals(10, names.size)
    }

    @Test
    fun generateZeroCountReturnsEmpty() {
        val gen = NpcNameGenerator(42L)
        val names = gen.generate(0)
        assertEquals(0, names.size)
    }

    @Test
    fun generateCountProducesUniqueNames() {
        val gen = NpcNameGenerator(42L)
        val names = gen.generate(50)
        val uniqueFullNames = names.map { it.fullName }.distinct()
        assertTrue(uniqueFullNames.size >= 40, "Expected at least 40 unique names out of 50, got ${uniqueFullNames.size}")
    }

    @Test
    fun generateAllCulturesCovered() {
        val gen = NpcNameGenerator(42L)
        val names = gen.generate(200)
        val cultures = names.map { it.culture }.toSet()
        assertEquals(NameCulture.entries.toSet(), cultures, "Should eventually produce names from all cultures")
    }

    @Test
    fun generateBothGenders() {
        val gen = NpcNameGenerator(42L)
        // Use足够 large count and multiple seeds to ensure both genders appear
        var foundFemale = false
        var foundMale = false
        val names = gen.generate(500)
        names.forEach { name ->
            if (name.isFemale) foundFemale = true else foundMale = true
        }
        // If this seed didn't produce both, try additional seeds
        if (!foundFemale || !foundMale) {
            for (seed in 1L..10L) {
                val extra = NpcNameGenerator(seed).generate(100)
                extra.forEach { name ->
                    if (name.isFemale) foundFemale = true else foundMale = true
                }
                if (foundFemale && foundMale) break
            }
        }
        assertTrue(foundFemale, "Should produce at least one female name")
        assertTrue(foundMale, "Should produce at least one male name")
    }

    @Test
    fun generateWithExplicitFemaleTrue() {
        val gen = NpcNameGenerator(42L)
        val name = gen.generate(isFemale = true)
        assertEquals(true, name.isFemale)
    }

    @Test
    fun generateWithExplicitFemaleFalse() {
        val gen = NpcNameGenerator(42L)
        val name = gen.generate(isFemale = false)
        assertEquals(false, name.isFemale)
    }

    @Test
    fun generateWithExplicitCulture() {
        val gen = NpcNameGenerator(42L)
        NameCulture.entries.forEach { culture ->
            val name = gen.generateFromCulture(culture)
            assertEquals(culture, name.culture)
        }
    }

    @Test
    fun generatedNamesBelongToCorrectTables() {
        val gen = NpcNameGenerator(99L)
        repeat(100) {
            val name = gen.generate()
            val allGiven = when (name.culture) {
                NameCulture.ISSIAN -> NpcNameTables.issianMaleGivenNames + NpcNameTables.issianFemaleGivenNames
                NameCulture.IOBARIAN -> NpcNameTables.iobarianMaleGivenNames + NpcNameTables.iobarianFemaleGivenNames
                NameCulture.TALDAN -> NpcNameTables.taldanMaleGivenNames + NpcNameTables.taldanFemaleGivenNames
            }
            assertTrue(name.givenName in allGiven,
                "Given name '${name.givenName}' not in ${name.culture} tables")
            assertTrue(name.surname in NpcNameTables.surnames(name.culture),
                "Surname '${name.surname}' not in ${name.culture} tables")
        }
    }

    @Test
    fun deterministicAcrossMultipleCalls() {
        val gen = NpcNameGenerator(777L)
        val first = List(5) { gen.generate() }
        val gen2 = NpcNameGenerator(777L)
        val second = List(5) { gen2.generate() }
        assertEquals(first, second)
    }

    @Test
    fun largeBatchGeneration() {
        val gen = NpcNameGenerator(42L)
        val names = gen.generate(1000)
        assertEquals(1000, names.size)
        names.forEach { name ->
            assertTrue(name.givenName.isNotEmpty())
            assertTrue(name.surname.isNotEmpty())
            assertTrue(name.fullName.contains(" "))
        }
    }
}

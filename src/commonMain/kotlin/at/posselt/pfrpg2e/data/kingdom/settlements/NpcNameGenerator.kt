package at.posselt.pfrpg2e.data.kingdom.settlements

/**
 * A simple, seedable pseudo-random number generator implemented in pure
 * Kotlin for use in [NpcNameGenerator].
 *
 * Uses a 32-bit integer LCG with parameters from Numerical Recipes.
 * All arithmetic stays within 32-bit Int range so it compiles correctly
 * on Kotlin/JS (which uses 32-bit bitwise operations).
 *
 * Not cryptographically secure.  Suitable for procedural name generation
 * and similar game-utility purposes only.
 */
class SeededRng(seed: Long) {
    private var state: Int = mix(seed)

    init {
        if (state == 0) state = 123456789
        // Warm up to decorrelate from seed
        nextInt()
        nextInt()
        nextInt()
        nextInt()
    }

    /**
     * Mix a Long seed into a 32-bit state using a simple hash.
     */
    private fun mix(s: Long): Int {
        var v = s
        // xorshift64 mixing, using only 32-bit ops
        var a = (v and 0xFFFFFFFFL).toInt()
        var b = (v ushr 32).toInt()
        a = a xor (a shl 13)
        a = a xor (a shr 17)
        a = a xor (a shl 5)
        b = b xor (b shl 13)
        b = b xor (b shr 17)
        b = b xor (b shl 5)
        return a xor b
    }

    /**
     * Returns the next pseudorandom non-negative [Int].
     * LCG: state = state * 1664525 + 1013904223 (Numerical Recipes)
     */
    fun nextInt(): Int {
        // 1664525 * state + 1013904223 — all fits in 32-bit Int with overflow
        state = state * 1664525 + 1013904223
        return state and 0x7FFFFFFF // ensure non-negative
    }

    /**
     * Returns a pseudorandom [Int] in the half-open range [0, bound).
     * @throws IllegalArgumentException if [bound] is non-positive.
     */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        // Simple approach: take modulo of a non-negative value
        // Slight bias is acceptable for name generation purposes
        return nextInt() % bound
    }

    /** Returns a pseudorandom [Int] in the inclusive range [from, to]. */
    fun nextInt(from: Int, to: Int): Int {
        require(from <= to) { "from must be <= to, was $from > $to" }
        val range = to - from + 1
        return from + nextInt(range)
    }

    /** Returns a pseudorandom [Boolean] with roughly 50/50 odds. */
    fun nextBoolean(): Boolean = (nextInt() and 1) == 1
}

/**
 * A generated NPC name with its component parts.
 */
data class GeneratedNpcName(
    val givenName: String,
    val surname: String,
    val culture: NameCulture,
    val isFemale: Boolean,
) {
    /** The full name, e.g. "Svetlana Morozov". */
    val fullName: String get() = "$givenName $surname"
}

/**
 * Seedable NPC name generator for the Stolen Lands / Brevoy / River Kingdoms.
 *
 * Produces culturally appropriate names drawn from Issian, Iobarian, and
 * Taldan name tables.  The generator is deterministic: the same seed always
 * produces the same sequence of names.
 *
 * Usage:
 * ```
 * val generator = NpcNameGenerator(seed = 12345L)
 * val name1 = generator.generate()
 * val name2 = generator.generate(NameCulture.ISSIAN, isFemale = true)
 * ```
 *
 * @param seed The RNG seed.  Same seed → same name sequence every time.
 */
class NpcNameGenerator(seed: Long) {
    private val rng = SeededRng(seed)

    /**
     * Generate a random NPC name.
     *
     * @param culture The cultural origin of the name.  If null, a random
     *   culture is chosen with equal weight.
     * @param isFemale Whether to use female given names.  If null, gender
     *   is chosen randomly (50/50).
     * @return A [GeneratedNpcName] with the full name and metadata.
     */
    fun generate(
        culture: NameCulture? = null,
        isFemale: Boolean? = null,
    ): GeneratedNpcName {
        val chosenCulture = culture ?: NameCulture.entries[rng.nextInt(NameCulture.entries.size)]
        val chosenGender = isFemale ?: rng.nextBoolean()

        val givenNames = if (chosenGender) {
            NpcNameTables.femaleGivenNames(chosenCulture)
        } else {
            NpcNameTables.maleGivenNames(chosenCulture)
        }
        val surnames = NpcNameTables.surnames(chosenCulture)

        val givenName = givenNames[rng.nextInt(givenNames.size)]
        val surname = surnames[rng.nextInt(surnames.size)]

        return GeneratedNpcName(
            givenName = givenName,
            surname = surname,
            culture = chosenCulture,
            isFemale = chosenGender,
        )
    }

    /**
     * Generate a sequence of [count] random NPC names.
     */
    fun generate(count: Int): List<GeneratedNpcName> {
        require(count >= 0) { "count must be non-negative, was $count" }
        return List(count) { generate() }
    }

    /**
     * Generate a name from a specific culture.
     */
    fun generateFromCulture(culture: NameCulture): GeneratedNpcName =
        generate(culture = culture, isFemale = null)

    /**
     * Generate a female name from a specific culture.
     */
    fun generateFemale(culture: NameCulture): GeneratedNpcName =
        generate(culture = culture, isFemale = true)

    /**
     * Generate a male name from a specific culture.
     */
    fun generateMale(culture: NameCulture): GeneratedNpcName =
        generate(culture = culture, isFemale = false)
}

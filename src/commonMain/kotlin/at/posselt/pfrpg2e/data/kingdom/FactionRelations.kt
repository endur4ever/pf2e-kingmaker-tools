package at.posselt.pfrpg2e.data.kingdom

/**
 * Pure, UI-free diplomacy standing math for the faction & diplomacy relations tracker.
 *
 * A faction's standing is an integer in [[MIN_FACTION_STANDING], [MAX_FACTION_STANDING]];
 * a `null` standing means "no recorded standing" and is treated as
 * [DEFAULT_FACTION_STANDING] (Indifferent). [attitudeFor] maps the integer to a
 * [FactionAttitude] band; [applyStandingDelta] clamps a change; the `shouldOffer*`
 * predicates fire only on the tick that *crosses* a threshold, so callers offer a
 * war threat / quest hook exactly once rather than every tick past the line.
 *
 * Kept here (commonMain) and unit-tested in commonTest so it stays independent of the
 * Foundry/JS `RawGroup` carrier, mirroring `ArmyBattleEngine` and `VkExtras`.
 */

const val MIN_FACTION_STANDING = -100
const val MAX_FACTION_STANDING = 100

/** Standing assumed for a faction whose `standing` is `null` (Indifferent). */
const val DEFAULT_FACTION_STANDING = 0

/**
 * Maps a (possibly null) standing to its [FactionAttitude] band:
 * `<= -50` Hostile, `-49..-15` Unfriendly, `-14..14` Indifferent, `15..49` Friendly,
 * `>= 50` Helpful. `null` is treated as [DEFAULT_FACTION_STANDING].
 */
fun attitudeFor(standing: Int?): FactionAttitude {
    val s = standing ?: DEFAULT_FACTION_STANDING
    return when {
        s <= -50 -> FactionAttitude.HOSTILE
        s <= -15 -> FactionAttitude.UNFRIENDLY
        s <= 14 -> FactionAttitude.INDIFFERENT
        s <= 49 -> FactionAttitude.FRIENDLY
        else -> FactionAttitude.HELPFUL
    }
}

/**
 * Applies [delta] to a (possibly null) [current] standing, clamped to
 * [[MIN_FACTION_STANDING], [MAX_FACTION_STANDING]]. A null current is treated as
 * [DEFAULT_FACTION_STANDING] before applying.
 */
fun applyStandingDelta(current: Int?, delta: Int): Int {
    val base = current ?: DEFAULT_FACTION_STANDING
    return (base + delta).coerceIn(MIN_FACTION_STANDING, MAX_FACTION_STANDING)
}

/**
 * True only when a change moves a faction *into* the Hostile band on this step (was not
 * Hostile before, is Hostile after). Lets a caller offer a war threat exactly once.
 */
fun shouldOfferWarThreat(before: Int?, after: Int): Boolean =
    attitudeFor(before) != FactionAttitude.HOSTILE && attitudeFor(after) == FactionAttitude.HOSTILE

/**
 * True only when a change moves a faction *into* Friendly-or-better on this step (was below
 * Friendly before, is Friendly/Helpful after). Lets a caller offer a diplomacy quest once.
 */
fun shouldOfferDiplomacyQuest(before: Int?, after: Int): Boolean {
    val wasFriendly = attitudeFor(before) >= FactionAttitude.FRIENDLY
    val isFriendly = attitudeFor(after) >= FactionAttitude.FRIENDLY
    return !wasFriendly && isFriendly
}

package at.posselt.pfrpg2e.data.kingdom

import kotlin.math.floor

/**
 * Pure core of the Rival Realms scoreboard, phase 2
 * (`docs/plans/2026-07-09-plan-rival-realms.md`, section 3.1).
 *
 * A rival realm is a SCOREBOARD, not a simulation: three integers (size, fame, armies) that creep
 * upward by a GM-dialled rate so the table can watch a neighbour out-grow them. Everything here is
 * arithmetic on primitives -- there is deliberately no RNG anywhere in the feature, because the
 * Turn Wizard renders a PREVIEW of the turn and then commits it, and a preview that disagreed with
 * its own commit would be worse than no preview at all. Determinism is the contract, not a nicety,
 * and it is why even the headline choice is a hash of (turn, faction, stat) rather than a roll.
 *
 * The persisted carrier (`RawRivalRealm`) is a jsMain plain object and cannot be named from
 * commonMain, so this file speaks only in primitives and plain data classes. The phase-3 jsMain
 * adapter owns everything impure around it: reading the growth profile files, reading the linked
 * faction's `atWar` flag, forcing size growth to zero at war, localizing headlines and posting
 * offers. That is the same split `FactionRelations` (here) and `GroupContext` (jsMain) already use.
 */

/**
 * Which stat grew this turn. Keys the headline pool, and through its ordinal the template choice
 * in [headlineTemplateIndex].
 *
 * DECLARATION ORDER IS LOAD-BEARING: [headlineTemplateIndex] mixes the ordinal into its hash, so
 * reordering these entries silently re-rolls every headline the campaign would otherwise have seen
 * for a given turn. Append new stats at the end.
 *
 * [value] is the persisted/serialized spelling; [fromValue] returns null instead of throwing so an
 * unrecognised stored value costs one headline rather than aborting an End Turn tick half-applied.
 */
enum class RivalStat(val value: String) {
    SIZE("size"),
    FAME("fame"),
    ARMY("army");

    companion object {
        fun fromValue(value: String?): RivalStat? = entries.find { it.value == value }
    }
}

/**
 * Which template pool a headline draws from, and how many templates that pool holds.
 *
 * [poolSize] is a compile-time constant on purpose: commonMain cannot read `lang/en.json` at all,
 * and the index has to be computable during a preview, so the count lives beside the enum instead
 * of being counted at runtime. The numbers mirror the flattened i18n keys the plan specifies
 * (expand1..expand3, fame1..fame2, army1..army2, armyWar1..armyWar2) and the literal `when` in the
 * jsMain localizer. If a locale ever gains a template without this count being bumped, the extra
 * template is simply never drawn -- the failure mode is a dull headline, never a missing key.
 *
 * [value] is the spelling the GM-only headline-pool override persists as; [fromValue] returns null
 * for anything unrecognised so the caller falls back to [poolFor] rather than throwing.
 */
enum class RivalHeadlinePool(val poolSize: Int, val value: String) {
    EXPAND(3, "expand"),
    FAME(2, "fame"),
    ARMY(2, "army"),
    ARMY_WAR(2, "armyWar");

    companion object {
        fun fromValue(value: String?): RivalHeadlinePool? = entries.find { it.value == value }
    }
}

/**
 * The pool a [stat] headline draws from this turn.
 *
 * Only ARMY has a wartime variant, and the asymmetry is a decision rather than an omission: at war
 * the adapter forces the size rate to zero (a realm fighting a war annexes no quiet hexes), and
 * [growStat] leaves a zero-rate accrual completely untouched, so a SIZE tickover is unreachable
 * while at war BY CONSTRUCTION. A wartime expansion pool would be dead templates in eight locale
 * files. FAME reads the same in peace and war, so it shares one pool.
 */
fun poolFor(stat: RivalStat, atWar: Boolean): RivalHeadlinePool = when (stat) {
    RivalStat.SIZE -> RivalHeadlinePool.EXPAND
    RivalStat.FAME -> RivalHeadlinePool.FAME
    RivalStat.ARMY -> if (atWar) RivalHeadlinePool.ARMY_WAR else RivalHeadlinePool.ARMY
}

/**
 * Resolved per-stat growth rates for ONE realm for ONE turn, after the adapter has applied the
 * resolution order (per-stat GM dial wins, else the named growth profile, else dormant).
 *
 * Rates are Double so a GM can say "a hex every three turns" (0.33) instead of being forced into
 * whole points per turn -- the fractional part is what [growStat] carries. Defaulting every rate to
 * zero makes the dormant fallback the cheapest thing to express, since most tracked realms sit
 * still most of the campaign.
 */
data class RivalGrowthProfile(
    val sizePerTurn: Double = 0.0,
    val famePerTurn: Double = 0.0,
    val armyPerTurn: Double = 0.0,
)

/**
 * Result of advancing ONE stat by one turn: the new integer [value], the fractional [accrual] to
 * carry into next turn, and [incremented] -- how many whole points this turn added.
 *
 * [incremented] is reported rather than left for the caller to subtract because it is what decides
 * whether a headline fires, and because it stays honest even when [value] saturates: the invariant
 * callers rely on is `new value == old value + incremented`, always.
 */
data class StatGrowth(val value: Int, val accrual: Double, val incremented: Int)

/**
 * How close to a whole point counts as a whole point.
 *
 * Rates like 0.33 and 0.3 are not representable in binary, so a literal `accrual >= 1.0` test
 * accumulates downward drift: ten turns at 0.3 lands on 0.9999999999999996 and awards TWO points
 * instead of three, and the error compounds for the rest of the campaign. The plan promises
 * lossless accrual ("numbers match the dial exactly"), so the comparison is nudged by an epsilon
 * far smaller than any dial a GM can meaningfully set. It also self-heals: the carried remainder
 * after such a tickover is floored at zero, discarding the accumulated error instead of banking it.
 */
const val ACCRUAL_EPSILON = 1e-9

/** Corrupt carried state (NaN from an old bug, a negative from a hand-edited flag) becomes zero
 *  rather than propagating: a poisoned accrual would freeze that stat for the rest of the campaign
 *  with no visible cause, and losing at most one turn of fraction is the cheaper failure. */
private fun sanitizeAccrual(raw: Double): Double = if (raw.isFinite() && raw > 0.0) raw else 0.0

/**
 * Advances one stat by one turn.
 *
 * `accrual += perTurn`, then every whole point in the accrual becomes +1 to the integer stat and
 * the remainder carries. Fully deterministic: the same inputs always produce the same [StatGrowth],
 * which is what makes preview/commit parity automatic for the whole feature.
 *
 * Guards, each earning its place:
 * - [paused] (the GM kill-switch) and a non-positive [perTurn] return the stat AND the carried
 *   accrual untouched. Untouched matters: it is what makes a wartime SIZE headline unreachable
 *   (see [poolFor]) and what lets a GM pause a realm mid-fraction and resume it without losing the
 *   progress the campaign already made.
 * - A non-finite [perTurn] is ignored rather than applied. Infinity through the old loop-based
 *   formulation would hang End Turn outright, and NaN would silently poison the stored accrual.
 * - The whole points are taken with [floor] instead of a `while (accrual >= 1.0)` loop -- identical
 *   for every sane dial, but a mistyped rate of a billion per turn is one subtraction instead of a
 *   billion iterations inside the turn tick.
 * - Growth saturates at [Int.MAX_VALUE] rather than wrapping negative, so a runaway dial makes a
 *   silly scoreboard rather than a rival that suddenly ranks last.
 */
fun growStat(value: Int, accrual: Double, perTurn: Double, paused: Boolean): StatGrowth {
    val carried = sanitizeAccrual(accrual)
    if (paused || !perTurn.isFinite() || perTurn <= 0.0) {
        return StatGrowth(value = value, accrual = carried, incremented = 0)
    }
    val total = carried + perTurn
    val whole = floor(total + ACCRUAL_EPSILON)
    val grown = (value.toDouble() + whole).coerceAtMost(Int.MAX_VALUE.toDouble())
    val newValue = grown.toInt()
    return StatGrowth(
        value = newValue,
        accrual = sanitizeAccrual(total - whole),
        incremented = newValue - value,
    )
}

/**
 * Weights behind [rivalPowerScore]. PROVISIONAL -- plan section 9 question 2 explicitly leaves the
 * tuning to Gregory ("`size*10 + fame*2 + armyCount*5` is a first guess"). They are named constants
 * rather than inline literals precisely so retuning is one edit here instead of a hunt through the
 * sheet and its tests.
 *
 * The RATIO is the design statement and outlives the exact numbers: land is what a realm is
 * measured by, a standing army counts for more than the reputation it buys, and renown alone never
 * makes a neighbour the one to beat.
 */
const val RIVAL_POWER_SIZE_WEIGHT = 10
const val RIVAL_POWER_FAME_WEIGHT = 2
const val RIVAL_POWER_ARMY_WEIGHT = 5

/**
 * The composite "Realm Power" the standings table sorts on.
 *
 * It exists because the per-stat columns answer "how is Pitax winning" but nothing answers "who is
 * winning" at a glance, and a table the player has to mentally reduce is a table they ignore. It is
 * a comparison key only -- never spent, never displayed as a resource -- so its absolute magnitude
 * carries no meaning and only the ordering it induces does.
 *
 * Strictly increasing in each stat (all weights positive), which is the property the scoreboard
 * depends on: growth must never be able to push a realm DOWN the rankings on its own.
 */
fun rivalPowerScore(size: Int, fame: Int, armyCount: Int): Int =
    size * RIVAL_POWER_SIZE_WEIGHT + fame * RIVAL_POWER_FAME_WEIGHT + armyCount * RIVAL_POWER_ARMY_WEIGHT

/**
 * One row of the standings table -- a rival realm or the players' own kingdom, with no UI in it.
 *
 * [score] defaults to [rivalPowerScore] of this row's own stats so the common case cannot be built
 * inconsistent, but stays a parameter because the player row is assembled from kingdom fields the
 * adapter may have to derive differently (the player has no single army count -- plan section 9
 * question 1), and whatever it decides must remain the sort key.
 *
 * [isPlayer] is carried rather than inferred from the label because the sheet highlights that row
 * and hides the GM growth dials on every other one; a label match would break the moment a GM named
 * their kingdom after a rival.
 */
data class RivalStandingRow(
    val label: String,
    val size: Int,
    val fame: Int,
    val armyCount: Int,
    val score: Int = rivalPowerScore(size, fame, armyCount),
    val isPlayer: Boolean = false,
    val rank: Int = 0,
)

/**
 * Sorts rows by [RivalStandingRow.score] descending and stamps 1-based ranks.
 *
 * Ranks are SEQUENTIAL (1..n), not competition-style: the table renders "#1", "#2", "#3" down the
 * column, and shared ranks would print two "#2" rows and then skip "#3", which reads as a bug to
 * every player who sees it.
 *
 * Ties therefore have to be broken by something, and that something is the caller's input order --
 * the sort is stable and equal scores are left alone. That keeps the tie-break deterministic (the
 * adapter builds the input list in a fixed order, so preview and commit rank identically) without
 * inventing a rule about whether a rival that has genuinely matched the players outranks them.
 *
 * The incoming [RivalStandingRow.rank] is ignored and overwritten: rows arrive built from stats, and
 * trusting a stale rank would let a row keep a number from a previous turn.
 */
fun rankStandings(rows: List<RivalStandingRow>): List<RivalStandingRow> =
    rows.sortedByDescending { it.score }
        .mapIndexed { index, row -> row.copy(rank = index + 1) }

/**
 * Picks which template a headline uses, deterministically, from (turn, faction, stat).
 *
 * A roll would be the obvious way to vary the prose, and it is exactly what this feature must not
 * do: the Turn Wizard previews a turn before committing it, so a rolled headline would change
 * between the preview the GM read and the chat card the table sees. Hashing the turn instead gives
 * variety across turns and across realms while staying a pure function of state.
 *
 * The double modulo is not redundant. `hashCode()` is signed and `turn * 31` may overflow, so the
 * first remainder can be negative; `(x % n + n) % n` folds it back into `[0, poolSize)`, which is
 * the range the caller indexes a template list with.
 *
 * A non-positive [poolSize] answers 0 rather than dividing by zero -- an empty pool is a caller bug
 * that must not take the turn tick down with it.
 *
 * Caveat worth knowing: the faction name is hashed, so RENAMING a realm reshuffles which template
 * it draws on a given turn. That is acceptable (headlines are flavor, and nothing stores the index)
 * and is the price of not persisting a per-realm counter.
 *
 * NAME OVERLOAD: the Rival Charter Parties feature declares a same-named function in this same
 * package (`RivalCharterParty.kt`) taking a String kind and hashing differently. The [RivalStat]
 * parameter is the only thing that tells the two apart, so a caller that passes a bare string
 * silently gets the OTHER feature's template. Always pass the enum.
 */
fun headlineTemplateIndex(turn: Int, factionRef: String, stat: RivalStat, poolSize: Int): Int =
    if (poolSize <= 0) 0 else ((turn * 31 + factionRef.hashCode() + stat.ordinal * 7) % poolSize + poolSize) % poolSize

/**
 * PROVISIONAL default for the standing-shift trigger: the one-turn size delta that reads as
 * "aggressive expansion on a shared border". Plan section 9 question 3 leaves 2-vs-3 to Gregory;
 * 2 ships because every preset tops out at 0.75 size/turn (delta 0 or 1), so anything >= 2 can
 * only come from a GM-dialed override -- exactly the realm the border-tension offer is about.
 * At 3 the offer would be unreachable even for most overrides, i.e. dead code.
 */
const val RIVAL_STANDING_SHIFT_SIZE_DELTA = 2

/** PROVISIONAL standing nudge the shift offer applies: a meaningful dent on the -100..100 scale,
 *  same order as the adjust-standing dialog's own "e.g. 10 or -15" help text. GM-confirmed, so a
 *  table that wants a harsher penalty applies it through the dialog afterwards. */
const val RIVAL_STANDING_SHIFT_DELTA = -10

/**
 * Should this turn's End Turn offer the GM a war threat for a rival?
 *
 * Fires only while the linked group is at war AND the army count sits at or past the GM's dialed
 * threshold AND the army has grown since the last time the offer was answered. [lastOffered] is
 * the bookkeeping the Dismiss/Raise buttons bump: without the third clause a dismissed offer would
 * reappear every single turn at an unchanged army count, which is exactly the spam the plan's
 * "won't re-fire until the army grows again" forbids. A null [threshold] means the GM disabled
 * the offer for this realm.
 */
fun rivalWarOfferDue(atWar: Boolean, armyCount: Int, threshold: Int?, lastOffered: Int?): Boolean =
    atWar && threshold != null && armyCount >= threshold &&
            (lastOffered == null || armyCount > lastOffered)

/** Should a one-turn size gain of [sizeDelta] offer the GM a border-tension standing shift? */
fun rivalStandingShiftDue(sizeDelta: Int, threshold: Int = RIVAL_STANDING_SHIFT_SIZE_DELTA): Boolean =
    sizeDelta >= threshold

package at.posselt.pfrpg2e.data.kingdom

/**
 * Personal holdings: per-PC fiefs, estates and businesses the GM grants as titles with teeth
 * (plan: docs/plans/2026-07-09-plan-personal-holdings.md). Everything here is pure and
 * commonTest-able; the jsMain adapters map it onto the Raw carrier and the tick.
 */

enum class HoldingTier(val value: Int, val goldPerLevel: Int, val luxuriesPerTurn: Int, val favorsPerTurn: Int) {
    MODEST(1, 2, 0, 0),
    COMFORTABLE(2, 5, 0, 1),
    LAVISH(3, 10, 1, 2);

    companion object {
        fun fromValue(value: Int?): HoldingTier? = entries.find { it.value == value }
    }
}

enum class HoldingCondition(val value: String) {
    SOUND("sound"),
    DAMAGED("damaged"),
    DESTROYED("destroyed");

    companion object {
        fun fromValue(value: String?): HoldingCondition? = entries.find { it.value == value }
    }
}

/** MINOR: one step down. MAJOR: straight to destroyed. */
enum class DamageSeverity { MINOR, MAJOR }

/** A turn's worth of personal income. Plain value type; mapped to offers/commodities in jsMain. */
data class HoldingIncome(val gold: Int, val luxuries: Int, val favors: Int) {
    val isEmpty get() = gold == 0 && luxuries == 0 && favors == 0
    operator fun plus(o: HoldingIncome) = HoldingIncome(gold + o.gold, luxuries + o.luxuries, favors + o.favors)

    companion object { val ZERO = HoldingIncome(0, 0, 0) }
}

/**
 * One PC's accrued line for the End Turn income digest. Primitives only, so it lives in
 * commonMain with the rest of the pure core even though TickResult, which carries it, is jsMain.
 */
data class HoldingIncomeLine(
    val ownerUserId: String?,
    val ownerLabel: String,
    val holdingId: String,
    val holdingName: String,
    val gold: Int,
    val luxuries: Int,
    val favors: Int,
)

const val MAX_HOLDINGS_PER_PC = 2
private const val HOLDING_LEVEL_CAP = 20

/**
 * Which kingdom events damage a holding at their location, and how hard (damage hook #3).
 * Keyed by the id SLUG in data/events JSON files -- NOT the Title-Case filename. Held as data so the
 * set is unit-testable and extensible without touching km-resolve-event. Every id is a
 * `dangerous` event in the shipped catalog.
 */
val HOLDING_DAMAGING_EVENT_IDS: Map<String, DamageSeverity> = mapOf(
    // MAJOR -- the site is overrun or levelled outright
    "undead-uprising" to DamageSeverity.MAJOR,
    "the-rampage-of-the-owlbear" to DamageSeverity.MAJOR,
    "local-disaster" to DamageSeverity.MAJOR,
    "a-devil-comes-calling" to DamageSeverity.MAJOR,
    // MINOR -- property is looted, spoiled or defaced, not destroyed
    "bandit-activity" to DamageSeverity.MINOR,
    "monster-activity" to DamageSeverity.MINOR,
    "crop-failure" to DamageSeverity.MINOR,
    "sacrifices" to DamageSeverity.MINOR,
    "too-close-to-home" to DamageSeverity.MINOR,
    "vandals" to DamageSeverity.MINOR,
    "feud" to DamageSeverity.MINOR,
    "troll-sightings" to DamageSeverity.MINOR,
)

/** Base income for a SOUND holding at [tier], scaled by the owning PC's [level], capped at 20. */
fun holdingIncome(tier: HoldingTier, level: Int): HoldingIncome {
    val lvl = level.coerceIn(1, HOLDING_LEVEL_CAP)
    return HoldingIncome(
        gold = tier.goldPerLevel * lvl,
        luxuries = tier.luxuriesPerTurn,
        favors = tier.favorsPerTurn,
    )
}

/**
 * Condition multiplier: sound x1, damaged x1/2 (gold and luxuries floored; FAVORS survive intact
 * -- a damaged manor still carries the title, and the neighbours still owe the family), destroyed
 * x0.
 */
fun conditionAdjustedIncome(tier: HoldingTier, level: Int, condition: HoldingCondition): HoldingIncome =
    when (condition) {
        HoldingCondition.SOUND -> holdingIncome(tier, level)
        HoldingCondition.DAMAGED -> holdingIncome(tier, level).let {
            HoldingIncome(it.gold / 2, it.luxuries / 2, it.favors)
        }
        HoldingCondition.DESTROYED -> HoldingIncome.ZERO
    }

/** Pure condition transition. Never throws; DESTROYED is terminal until repaired. */
fun nextConditionAfterDamage(current: HoldingCondition, severity: DamageSeverity): HoldingCondition =
    when {
        current == HoldingCondition.DESTROYED -> HoldingCondition.DESTROYED
        severity == DamageSeverity.MAJOR -> HoldingCondition.DESTROYED
        current == HoldingCondition.SOUND -> HoldingCondition.DAMAGED
        else -> HoldingCondition.DESTROYED   // DAMAGED + MINOR -> DESTROYED
    }

/**
 * Flat gp cost to restore a holding one step toward SOUND. Deliberately level-independent --
 * income scales with level but repair does not, so a low-level PC can still afford to rebuild.
 */
fun repairCost(tier: HoldingTier, condition: HoldingCondition): Int = when (condition) {
    HoldingCondition.SOUND -> 0
    HoldingCondition.DAMAGED -> when (tier) {
        HoldingTier.MODEST -> 25
        HoldingTier.COMFORTABLE -> 75
        HoldingTier.LAVISH -> 150
    }
    HoldingCondition.DESTROYED -> when (tier) {
        HoldingTier.MODEST -> 100
        HoldingTier.COMFORTABLE -> 300
        HoldingTier.LAVISH -> 600
    }
}

/** One step toward SOUND (DESTROYED -> DAMAGED -> SOUND), so a rebuild is two paid repairs. */
fun repairedCondition(current: HoldingCondition): HoldingCondition = when (current) {
    HoldingCondition.DESTROYED -> HoldingCondition.DAMAGED
    HoldingCondition.DAMAGED -> HoldingCondition.SOUND
    HoldingCondition.SOUND -> HoldingCondition.SOUND
}

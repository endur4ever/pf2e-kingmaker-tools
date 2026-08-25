package at.posselt.pfrpg2e.kingdom.loot

/**
 * Pure core of hex loot manifests and the treasure ledger, phase 1
 * (`docs/plans/2026-07-09-plan-loot-manifests.md`, §2 data model and §3 engine).
 *
 * The loot-imbalance pacing alert shipped by roadmap 13 runs on a PROXY: the highest item level a
 * settlement market can sell. It never sees the treasure the GM actually hands out, so a party
 * swimming in dropped magic items but shopping in a low-level town registers as perfectly
 * balanced. The ledger built here records REALIZED awards, giving phase 2 measured wealth to feed
 * the pacing comparator instead of shop access.
 *
 * The persisted shapes (RawLootManifestEntry, RawTreasureLedgerEntry) are jsMain plain objects and
 * cannot be referenced from commonMain, so the types below are pure mirrors: the phase-2 jsMain
 * adapters own the mapping, including collapsing the all-nullable Raw fields into these defaults
 * (a null Raw quantity is one, a null cursed is false). Likewise moving awarded items into the
 * party stash (plan §5.3) is Foundry inventory work and stays in jsMain — this core only records
 * what an award was worth, never applies it.
 */

/**
 * One prepped treasure row, mirrored from the jsMain RawLootManifestEntry.
 *
 * [itemUuid] is the preferred Foundry reference and [packRef] the fallback pack ref when no
 * resolvable UUID exists; [name] is captured at prep time so the row still renders — and still
 * totals — after the UUID breaks. [gpValue] is per unit and stays a Double because PF2e prices
 * are not always whole gp (sp and cp fidelity). [cursed] drives the Cleanse Item cross-link and
 * the ledger's cursed count; [note] is free GM annotation and never enters any total.
 */
data class LootManifestItem(
    val itemUuid: String? = null,
    val packRef: String? = null,
    val name: String,
    val qty: Int,
    val gpValue: Double,
    val cursed: Boolean = false,
    val note: String? = null,
)

/** The headline numbers the manifest editor summary line and the award offer card both render. */
data class ManifestTotals(
    val totalGp: Double,
    val cursedCount: Int,
)

/**
 * Totals a manifest without trusting it.
 *
 * A negative quantity is corrupt data (a bad import, a mangled DOM read-back) and is floored at
 * zero so it cannot SUBTRACT treasure from the total — an award must never make the pacing metric
 * believe the party got poorer. Cursed items are counted per distinct row, not per quantity: the
 * Cleanse Item cross-link points at an item, not at each copy of it, so three copies of one
 * cursed blade are one curse to cleanse.
 */
fun manifestTotals(items: List<LootManifestItem>): ManifestTotals {
    var totalGp = 0.0
    var cursedCount = 0
    for (item in items) {
        totalGp += maxOf(item.qty, 0) * item.gpValue
        if (item.cursed) {
            cursedCount++
        }
    }
    return ManifestTotals(totalGp = totalGp, cursedCount = cursedCount)
}

/**
 * One settled award, mirrored from the jsMain RawTreasureLedgerEntry.
 *
 * Deliberately denormalized — [itemNames], [totalGp] and [cursedCount] are snapshots, never
 * references — because the ledger must OUTLIVE the hex it came from: a GM can delete a hex
 * content entry after clearing it, but the wealth record has to persist for pacing and audit.
 * That is also why the runtime ledger lives campaign-scoped on KingdomData rather than on the
 * hex (plan §2.5); this mirror carries only the fields the pure math needs.
 */
data class TreasureLedgerEntry(
    val id: String,
    val turn: Int,
    val sourceHexKey: String,
    val itemNames: List<String>,
    val totalGp: Double,
    val cursedCount: Int,
)

/**
 * Assembles the persisted entry for one award from the manifest that produced it.
 *
 * Names keep manifest order — the offer card and the ledger table should read in the order the GM
 * prepped the hex — and the totals come from [manifestTotals], so the ledger can never disagree
 * with the summary the GM saw on the offer card they confirmed.
 */
fun buildLedgerEntry(
    id: String,
    turn: Int,
    sourceHexKey: String,
    items: List<LootManifestItem>,
): TreasureLedgerEntry {
    val totals = manifestTotals(items)
    return TreasureLedgerEntry(
        id = id,
        turn = turn,
        sourceHexKey = sourceHexKey,
        itemNames = items.map { it.name },
        totalGp = totals.totalGp,
        cursedCount = totals.cursedCount,
    )
}

/** Bounded like the other kingdom history flags, so a long campaign cannot bloat the actor. */
const val TREASURE_LEDGER_CAP = 200

/**
 * Appends one award, trimming the OLDEST entries once past [cap].
 *
 * Plain oldest-first trimming is safe here, unlike the expedition XP ledger: every treasure entry
 * is settled the moment it is written (the GM already confirmed the award), so there is no
 * never-prune state to protect — no pending offer whose loss would strand anything. Trimming only
 * costs the deep past of the wealth chronicle, which the windowed pacing reads never look at.
 * A non-positive cap keeps nothing rather than throwing.
 */
fun appendLedgerEntry(
    existing: List<TreasureLedgerEntry>,
    entry: TreasureLedgerEntry,
    cap: Int = TREASURE_LEDGER_CAP,
): List<TreasureLedgerEntry> {
    val appended = existing + entry
    return if (appended.size <= cap) appended else appended.takeLast(cap.coerceAtLeast(0))
}

/**
 * Realized gp awarded during the turns in [fromTurn]..[toTurn], both endpoints included.
 *
 * This is the measured input the phase-2 realized-loot pacing track consumes — replacing the
 * settlement-access PROXY the plan documents (§6.1), which watches what a market can SELL rather
 * than what the party was HANDED. (The proxy itself survives as a separate parallel signal for
 * the "shopping above level" house rule; only its role as the sole loot input is replaced.)
 * Only the measurement lives here: the fire-once severity tracking stays in PacingAlerts, which
 * is jsMain, and is deliberately not duplicated so the loot tracks cannot drift apart in how
 * their alerts latch. An inverted window ([toTurn] below [fromTurn]) matches no turn and yields
 * zero.
 */
fun realizedGpInWindow(
    entries: List<TreasureLedgerEntry>,
    fromTurn: Int,
    toTurn: Int,
): Double {
    var sum = 0.0
    for (entry in entries) {
        if (entry.turn in fromTurn..toTurn) {
            sum += entry.totalGp
        }
    }
    return sum
}

/**
 * Treasure a party of four is expected to gain DURING each level, from the PF2e Treasure by Level
 * table (index 0 = level 1). RAW rules data, not house tuning -- the same category as the
 * leadership-activity caps -- but worth a spot-check against the book before anyone tunes an
 * alert threshold on it.
 */
val TREASURE_PER_LEVEL_GP: List<Double> = listOf(
    175.0, 300.0, 500.0, 850.0, 1350.0,
    2000.0, 2900.0, 4000.0, 5700.0, 8000.0,
    11500.0, 16500.0, 25000.0, 36500.0, 54500.0,
    82500.0, 128000.0, 208000.0, 355000.0, 490000.0,
)

/**
 * Cumulative expected wealth: entry i is everything a party should hold on reaching level i+1.
 * Derived by summation rather than typed out, so the two tables cannot drift apart.
 */
val PARTY_WEALTH_BY_LEVEL: List<Double> = TREASURE_PER_LEVEL_GP.runningReduce { acc, v -> acc + v }

/**
 * The level [totalGp] of realized treasure implies: the highest level whose cumulative expected
 * wealth the party has actually reached.
 *
 * Returns 1 below the first threshold (a party with nothing is still level 1, not level 0) and
 * caps at the table's length -- past level 20 the curve has nothing left to say, and an
 * unbounded extrapolation would fire a CRITICAL alert on every late campaign forever.
 */
fun wealthLevelForGp(totalGp: Double): Int {
    // Below the first threshold there is no index to find, so the floor is answered here rather
    // than by a clamp; past it indexOfLast always matches (>= 0) and can never exceed the last
    // index, so the 1..size range holds by construction -- a clamp would be unreachable code.
    if (totalGp < (PARTY_WEALTH_BY_LEVEL.firstOrNull() ?: return 1)) return 1
    return PARTY_WEALTH_BY_LEVEL.indexOfLast { totalGp >= it } + 1
}

/**
 * What the realized-loot pacing track compares (plan SS3.4, SS6.1).
 *
 * [impliedWealthLevel] converts the ledger's LIFETIME gp onto the same level axis the settlement
 * proxy already uses, so one comparator serves both. Lifetime rather than a window: wealth-by-level
 * is a cumulative curve, and comparing one turn's haul against a cumulative expectation would
 * never fire.
 */
data class RealizedLootInput(
    val impliedWealthLevel: Int,
    val partyLevel: Int,
    val turn: Int,
)

fun pacingLootInput(
    ledger: List<TreasureLedgerEntry>,
    partyLevel: Int,
    turn: Int,
): RealizedLootInput =
    RealizedLootInput(
        impliedWealthLevel = wealthLevelForGp(ledger.sumOf { it.totalGp }),
        partyLevel = partyLevel,
        turn = turn,
    )

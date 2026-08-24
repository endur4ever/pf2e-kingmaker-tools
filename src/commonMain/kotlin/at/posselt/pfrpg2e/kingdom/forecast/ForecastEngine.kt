package at.posselt.pfrpg2e.kingdom.forecast

/**
 * Pure core of the next-session forecast (`docs/plans/2026-07-09-plan-session-forecast.md`).
 *
 * The forecast dry-runs the coming days plus one End Turn and reports the beats it finds; it never
 * mutates state, posts chat, or rolls dice (§3 of the plan). Everything here is pure Kotlin over
 * value inputs: the jsMain types the live data arrives in — TickResult, RawWarThreat, CaravanEvent
 * and friends — are deliberately not referenced, and the phase 2 jsMain adapter maps them into the
 * mirror inputs below. That keeps the whole engine unit-testable without Foundry and makes running
 * a forecast incapable of changing what End Turn subsequently does.
 */

enum class ForecastKind(val value: String) {
    ARRIVAL("arrival"),
    COMPLETION("completion"),
    EXPIRATION("expiration"),
    THREAT("threat"),
    RESOURCE("resource"),
    RISK("risk");

    companion object {
        fun fromValue(value: String?): ForecastKind? = entries.find { it.value == value }
    }
}

/**
 * One predicted beat. [dayOffset] is days from today; null means "at End Turn".
 *
 * [labelKey] is ALWAYS a compile-time constant supplied by the caller — the engine never assembles
 * keys, because a runtime-assembled key is invisible to the i18n guard (check_i18n_keys.py walks
 * literals only) and ships as a raw key on screen with every check green (§6 of the plan).
 *
 * [dc] and [safePercent] exist only on RISK beats: the stake and the odds, never an outcome (§3).
 * [target] is the MainNavEntry value the rendered row jumps to.
 */
data class ForecastBeat(
    val dayOffset: Int?,
    val kind: ForecastKind,
    val labelKey: String,
    val labelArgs: Map<String, String> = emptyMap(),
    val dc: Int? = null,
    val safePercent: Int? = null,
    val target: String,
)

data class ForecastResult(val beats: List<ForecastBeat>, val horizonDays: Int)

/**
 * Chance a d20 meets or beats [dc], as a percentage. A raid happens when the roll comes in UNDER
 * the DC, so for a raid DC this is the chance the cargo gets through. Clamped: a DC at or below 1
 * is a sure thing (100) and a DC above 21 is unmeetable (0) — never negative, never above 100.
 */
fun d20AtLeastPercent(dc: Int): Int = ((21 - dc).coerceIn(0, 20)) * 5

/**
 * Beyond two weeks the daily feeds are dominated by events the GM has not scheduled yet, and the
 * forecast starts inventing certainty it does not have (§6). The cap lives in the engine rather
 * than the UI so no caller can widen it.
 */
const val MAX_FORECAST_HORIZON_DAYS = 14

/**
 * [days] forced into 1..[MAX_FORECAST_HORIZON_DAYS]. A zero or negative horizon would silently
 * render an empty panel that looks like "nothing is coming", so it clamps up to one day instead.
 */
fun clampHorizon(days: Int): Int = days.coerceIn(1, MAX_FORECAST_HORIZON_DAYS)

/**
 * A deterministic beat a known number of days out — a caravan arriving, an expedition resolving.
 * Pure mirror input: the phase 2 jsMain adapter reduces DailyTickEngine results to these, so no
 * jsMain type reaches the engine.
 */
data class DailyCountdown(
    val labelKey: String,
    val labelArgs: Map<String, String> = emptyMap(),
    val remainingDays: Int,
    val kind: ForecastKind,
    val target: String,
)

/**
 * A roll-dependent stake: the DC of a roll End Turn will make, never the roll itself. Pure mirror
 * input: the phase 2 jsMain adapter derives [dc] from the CaravanTick DC helpers the caravan board
 * already displays, so forecast and board agree by construction (§3).
 */
data class RiskForecast(
    val labelKey: String,
    val labelArgs: Map<String, String> = emptyMap(),
    val dc: Int,
    val target: String,
)

/**
 * A label plus jump target with no schedule of its own — the shape of every End Turn beat. Like the
 * other input mirrors, filled by the phase-2 jsMain adapter from live TickResult fields.
 */
data class LabeledBeat(
    val labelKey: String,
    val labelArgs: Map<String, String> = emptyMap(),
    val target: String,
)

/**
 * What one discarded TurnTickingEngine tick reported, reduced to labels. Pure mirror input: the
 * phase 2 jsMain adapter reads the thrown-away TickResult — clockEvents, newlyTriggeredThreats,
 * questDeadlineReached, the changes diff, ruinThresholdCrossed — and fills these lists (§5).
 */
data class EndTurnBeats(
    val clockExpirations: List<LabeledBeat> = emptyList(),
    val newThreats: List<LabeledBeat> = emptyList(),
    val questDeadlines: List<LabeledBeat> = emptyList(),
    val resourceAlerts: List<LabeledBeat> = emptyList(),
    val ruinThresholdCrossed: Boolean = false,
)

/**
 * Everything the forecast may read, assembled in jsMain (phase 2). Pure values here, so the whole
 * engine runs without Foundry.
 */
data class ForecastInputs(
    val horizonDays: Int,
    val countdowns: List<DailyCountdown> = emptyList(),
    val risks: List<RiskForecast> = emptyList(),
    val endTurn: EndTurnBeats = EndTurnBeats(),
)

/**
 * The one label the engine owns: ruinThresholdCrossed is a bare Boolean on the tick result, so no
 * caller-side label can travel with it. A literal constant for the same reason every other
 * labelKey is one (see [ForecastBeat]).
 */
const val RUIN_THRESHOLD_LABEL_KEY = "kingdom.forecast.ruinThreshold"

/**
 * The dry run: every beat inside the clamped horizon, then everything the next End Turn brings.
 *
 * A countdown appears when its remaining days fall in 0..horizon — a caravan 2 days out appears in
 * a 3-day horizon and not in a 1-day horizon; day 0 means "today" and is inside the window. Every
 * risk becomes a RISK beat carrying its DC and [d20AtLeastPercent] odds and NEVER an outcome:
 * rolling here would both spoil the result — the GM reads next week's raid before it happens — and
 * diverge from it, because End Turn rolls again and gets a different number (§3). Consuming no
 * randomness is also what keeps preview and commit in parity: running the forecast cannot change
 * what End Turn subsequently rolls.
 *
 * Ordering: dated beats ascending by [ForecastBeat.dayOffset], stable within a day by input order,
 * then the undated End Turn block in build order (risks, clock expirations, new threats, quest
 * deadlines, resource alerts, ruin). Pure and deterministic: equal inputs give equal results,
 * however many times the panel re-renders.
 */
fun forecast(inputs: ForecastInputs): ForecastResult {
    val horizon = clampHorizon(inputs.horizonDays)
    val dated = inputs.countdowns
        .filter { it.remainingDays in 0..horizon }
        .sortedBy { it.remainingDays }
        .map {
            ForecastBeat(
                dayOffset = it.remainingDays, kind = it.kind, labelKey = it.labelKey,
                labelArgs = it.labelArgs, target = it.target,
            )
        }
    val atEndTurn = mutableListOf<ForecastBeat>()
    for (risk in inputs.risks) {
        atEndTurn.add(
            ForecastBeat(
                dayOffset = null, kind = ForecastKind.RISK, labelKey = risk.labelKey,
                labelArgs = risk.labelArgs, dc = risk.dc,
                safePercent = d20AtLeastPercent(risk.dc), target = risk.target,
            )
        )
    }
    fun addAll(beats: List<LabeledBeat>, kind: ForecastKind) {
        for (beat in beats) {
            atEndTurn.add(
                ForecastBeat(
                    dayOffset = null, kind = kind, labelKey = beat.labelKey,
                    labelArgs = beat.labelArgs, target = beat.target,
                )
            )
        }
    }
    addAll(inputs.endTurn.clockExpirations, ForecastKind.EXPIRATION)
    addAll(inputs.endTurn.newThreats, ForecastKind.THREAT)
    addAll(inputs.endTurn.questDeadlines, ForecastKind.EXPIRATION)
    addAll(inputs.endTurn.resourceAlerts, ForecastKind.RESOURCE)
    if (inputs.endTurn.ruinThresholdCrossed) {
        // A bare flag carries no jump target of its own; the row renders without one.
        atEndTurn.add(
            ForecastBeat(
                dayOffset = null, kind = ForecastKind.THREAT,
                labelKey = RUIN_THRESHOLD_LABEL_KEY, target = "",
            )
        )
    }
    return ForecastResult(beats = dated + atEndTurn, horizonDays = horizon)
}

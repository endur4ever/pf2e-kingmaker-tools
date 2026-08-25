package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.forecast.ForecastBeat
import at.posselt.pfrpg2e.kingdom.forecast.ForecastKind
import at.posselt.pfrpg2e.kingdom.forecast.ForecastResult
import at.posselt.pfrpg2e.utils.t
import js.objects.recordOf
import kotlinx.js.JsPlainObject

/**
 * Render context for the Session Prep forecast panel
 * (`docs/plans/2026-07-09-plan-session-forecast.md`, phase 3).
 *
 * NULLABILITY IS THE GATE: the builder is only ever fed a GM's [ForecastResult] (buildForecast
 * returns null for anyone else), and a null context removes the panel from the data, not just the
 * template. Players are OWNERs of the party actor, so a template conditional is layout, never
 * authorization.
 */
@Suppress("unused")
@JsPlainObject
external interface ForecastBeatContext {
    val label: String
    /** "in N days" / "today" for dated beats; the End-Turn heading covers the rest. */
    val dayLabel: String?
    /** ForecastKind.value, for the row accent class. */
    val kindClass: String
    /** "Raid DC 13 — 40% safe" for RISK beats; null elsewhere. Stake and odds, never an outcome. */
    val risk: String?
    val isEndTurn: Boolean
    /** MainNavEntry value the row jumps to via the sheet's existing change-nav action. */
    val target: String
}

@Suppress("unused")
@JsPlainObject
external interface ForecastHorizonChoice {
    val days: Int
    val active: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface ForecastPanelContext {
    val dated: Array<ForecastBeatContext>
    val endTurn: Array<ForecastBeatContext>
    val hasBeats: Boolean
    val horizonDays: Int
    /** 3/7/14, active flag precomputed so the template needs no comparison helper. */
    val horizonChoices: Array<ForecastHorizonChoice>
}

private fun ForecastBeat.toContext(): ForecastBeatContext {
    val args = recordOf(*labelArgs.map { (k, v) -> k to v }.toTypedArray())
    return ForecastBeatContext(
        label = t(labelKey, args),
        dayLabel = dayOffset?.let { offset ->
            if (offset == 0) t("kingdom.forecast.today")
            else t("kingdom.forecast.inDays", recordOf("days" to offset.toString()))
        },
        kindClass = kind.value,
        risk = if (dc != null && safePercent != null) {
            t("kingdom.forecast.risk", recordOf("dc" to dc.toString(), "percent" to safePercent.toString()))
        } else {
            null
        },
        isEndTurn = dayOffset == null,
        target = target,
    )
}

fun buildForecastPanelContext(result: ForecastResult?): ForecastPanelContext? {
    if (result == null) return null
    val beats = result.beats.map { it.toContext() }
    return ForecastPanelContext(
        dated = beats.filter { !it.isEndTurn }.toTypedArray(),
        endTurn = beats.filter { it.isEndTurn }.toTypedArray(),
        hasBeats = beats.isNotEmpty(),
        horizonDays = result.horizonDays,
        horizonChoices = intArrayOf(3, 7, 14).map {
            ForecastHorizonChoice(days = it, active = it == result.horizonDays)
        }.toTypedArray(),
    )
}

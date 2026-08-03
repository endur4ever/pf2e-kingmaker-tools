# Campaign Analytics & Trends Dashboard Implementation Plan

> **Status:** Draft — pending Gregory review
> **Date:** 2026-06-13
> **Roadmap item:** New backlog #3 (Campaign analytics / trends dashboard)
> **Builds on:** `kingdom/TurnHistory.kt` + `RawTurnRecord` (already records per-turn state),
> the Pacing Alerts system (#13), and the existing Turn-history "Recent Turns" recap section.

---

## Executive Summary

The module already snapshots kingdom state every turn. `buildTurnRecord` writes a
`RawTurnRecord` containing `turn`, `timestamp`, `fame`, `resourcePoints`, `consumption`,
`unrest`, `xpAwarded`, `clockEvents`, `warPressure`, and `notes`, and `appendTurnRecord`
keeps a capped rolling history on `KingdomData`. Today that history is only surfaced as a
textual "Recent Turns" list — nothing **visualizes the trend**.

This feature adds a read-only **Analytics** section to the Kingdom Sheet that charts the
recorded series over time (unrest, ruin, RP, consumption, fame, kingdom level/size, war
pressure) and overlays the **Pacing Alert** thresholds (#13) so the GM can *see* the
campaign drifting before an alert fires. It is purely presentational: it reads existing
history, computes summary statistics, and renders sparkline/line charts. It changes no game
state.

**Key capabilities:**

- **Trend charts** for each recorded metric across the rolling turn window.
- **Threshold overlays:** draw the pacing-alert lines (e.g. unrest-stagnation band, level
  mismatch range) on the relevant chart.
- **Summary stats:** current value, delta vs N turns ago, min/max/mean over the window,
  and a "turns since last change" figure feeding the stagnation view.
- **Window control:** last 10 / 25 / all recorded turns.
- **Zero new dependencies:** charts render as inline SVG built in Kotlin (the codebase
  already emits SVG/HTML via Handlebars); no external chart library is added.

**Relationship to architecture:** All math is pure and unit-tested in
`TurnAnalytics.kt` (mirrors `TurnHistory.kt` / `PacingAlerts.kt` / `VkExtras.kt`). The
Kingdom Sheet renders the Analytics section from a thin `@JsPlainObject` context built from
the existing `RawTurnRecord` array. No migration of game data is required for the charts
themselves; an **optional** extension adds a few fields to `RawTurnRecord` so richer series
(ruin, level, size, commodities) become available going forward.

---

## Affected Files

### New Kotlin Files

| File | Purpose |
|------|----------|
| `src/jsMain/kotlin/.../kingdom/TurnAnalytics.kt` | **Pure** series extraction, summary stats, and SVG-polyline/sparkline path generation (unit-tested) |
| `src/jsMain/kotlin/.../kingdom/sheet/contexts/AnalyticsContext.kt` | Thin UI context: one `MetricSeriesContext` per metric with points, axis bounds, threshold lines, and summary numbers |

### New Handlebars Templates

| File | Purpose |
|------|----------|
| `src/jsMain/resources/applications/kingdom/sections/analytics/page.hbs` | Analytics section (single root element); grid of metric cards + window selector |
| `src/jsMain/resources/applications/kingdom/sections/analytics/metric-chart.hbs` | Reusable inline-SVG line/sparkline + summary stat block |

### Modified Kotlin Files

| File | Changes |
|------|---------|
| `kingdom/sheet/KingdomSheet.kt` | Register the Analytics section + nav entry; build `AnalyticsContext` from `kingdom`'s turn history and pacing thresholds; handle the window-size control |
| `kingdom/sheet/navigation/MainNavEntry.kt` | Add the Analytics nav entry (read-only) |
| `lang/en.json` | Nested `kingdom.analytics.*` keys (metric labels, window options, stat labels) — **nested objects, never flat-dotted** (AGENTS.md) |

### Optional (richer series — separate phase)

| File | Changes |
|------|---------|
| `kingdom/data/RawTurnRecord.kt` | Add nullable `ruin: Int?`, `level: Int?`, `size: Int?`, `commodities: Int?` |
| `kingdom/TurnHistory.kt` | Extend `buildTurnRecord(...)` to snapshot the new fields |
| `kingdom/TurnTickingEngine.kt` | Pass the new values when it builds the per-turn record |
| `migrations/migrations/MigrationNN.kt` | None required — new fields are nullable; charts skip turns lacking data |

---

## Data Models

No new persisted model for Phase 1 — analytics is derived from the existing
`Array<RawTurnRecord>`. UI context shapes:

```kotlin
// AnalyticsContext.kt
@JsPlainObject
external interface MetricPointContext {
    var turn: Int
    var value: Double
    var x: Double   // pre-computed SVG coordinate
    var y: Double
}

@JsPlainObject
external interface MetricSeriesContext {
    var key: String            // "unrest", "resourcePoints", ...
    var label: String          // i18n
    var points: Array<MetricPointContext>
    var polyline: String       // "x1,y1 x2,y2 ..." for <polyline>
    var min: Double
    var max: Double
    var current: Double
    var deltaFromStart: Double
    var thresholdLines: Array<Double>?   // pacing-alert overlay values, if any
}

@JsPlainObject
external interface AnalyticsContext {
    var series: Array<MetricSeriesContext>
    var windowSize: Int
    var hasData: Boolean
}
```

`TurnAnalytics.kt` (pure):

```kotlin
/** Extract a numeric series for one metric from the (optionally windowed) history. */
fun seriesFor(history: Array<RawTurnRecord>, metric: String, window: Int?): List<Pair<Int, Double>>

/** Min/max/current/delta summary for a series. */
fun summarize(series: List<Pair<Int, Double>>): MetricSummary

/** Map a series to an SVG polyline string within a (width,height) viewbox. */
fun toPolyline(series: List<Pair<Int, Double>>, width: Int, height: Int): String
```

---

## Migration Plan

**Phase 1: none.** The dashboard reads whatever `RawTurnRecord` history already exists; if
history is empty it shows an empty state. **Phase 2 (optional)** adds nullable fields to
`RawTurnRecord`; existing records simply lack them and those metrics begin charting from the
first post-update turn. No destructive migration at any point.

---

## Testing Strategy

**jsTest — pure logic (`TurnAnalyticsTest.kt`, ~16 tests)**
- `seriesFor` extracts the right field for each metric and respects the window size.
- `seriesFor` skips records whose (optional) field is null without shifting turn alignment.
- `summarize` computes min/max/current/delta correctly, including single-point and empty series.
- `toPolyline` maps min→bottom and max→top of the viewbox, handles a flat series (no divide-by-zero),
  and produces N coordinate pairs for N points.

**jsTest — context (`AnalyticsContextTest.kt`, ~6 tests)**
- Builds one series per metric; `hasData=false` on empty history.
- Threshold overlay values are attached only to metrics that have a pacing threshold.
- Window selector of 10 vs all returns the expected point counts.

Build/verify per AGENTS.md: `python3 scripts/check_i18n_keys.py` then
`JAVA_HOME=<jdk25> ./gradlew assemble jsTest -x kotlinStoreYarnLock -Dorg.gradle.java.installations.paths=<jdk17>`
(Chrome headless + throwaway `karma.config.d` override).

---

## Manual Verification Checklist

1. Open Kingdom Sheet → an **Analytics** section/tab appears.
2. With several recorded turns, each metric card shows a line chart and summary stats.
3. Unrest/level charts show the pacing-alert threshold overlay line(s).
4. Switch window 10 / 25 / all → charts redraw with the expected number of points.
5. A brand-new kingdom (no history) shows the empty state, not a broken chart.
6. Advance a turn → the newest point appears at the right edge on next render.
7. (Phase 2) Ruin/level/size/commodities begin charting from the first turn after the update.
8. All labels resolve via i18n (no raw keys); the section is read-only (no state mutation).

---

## Open Questions for Gregory

- Phase 1 only (chart the 7 already-recorded metrics), or include Phase 2 (extend
  `RawTurnRecord` with ruin/level/size/commodities) in the first cut?
- Inline hand-rolled SVG (zero deps, full control) vs pulling in a small chart lib — plan
  assumes inline SVG to avoid a new dependency.
- Default window size (last 25 turns?) and the history cap (currently 100 in `appendTurnRecord`).
- Should the dashboard be GM-only or also visible to players (ties into the player-facing
  kingdom view, new-backlog #5)?

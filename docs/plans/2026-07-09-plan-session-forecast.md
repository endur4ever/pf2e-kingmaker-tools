# Plan: Next-Session Forecast — dry-run the coming days and turn in Session Prep

Card: `t_a9133f5f`.

## 1. Problem statement

Session Prep renders what the kingdom *is*. The GM still has to work out what happens *next* by
opening the caravan board, the expedition list, the campaign clocks and the war-threat table in turn
and doing the arithmetic in their head — every time, before every session, and usually while the
players are already at the table.

The forecast does that pass mechanically: simulate N in-world days plus one End Turn against a copy
of the state, mutate nothing, and render the beats it finds as a dated list.

**Value.** The GM opens one panel and sees "day 3: the Tuskwater caravan arrives; day 6: Ekundayo's
expedition resolves; End Turn: the Pitax threat arrives, consumption goes negative by 2". Everything
in that sentence exists in the data already — it is just spread over five tabs and a mental model.

## 2. Purity audit

This is the load-bearing section: a forecast is only possible where a side-effect-free evaluation
path exists. **The audit's result is that one already exists for every system in scope, so this
feature needs no extraction refactor at all.** Counts are occurrences in the file.

### 2.1 Pure cores — the forecast calls these directly

| File | `Random.` | `Date()` | `suspend fun` | `postChat` | persists |
| --- | --- | --- | --- | --- | --- |
| `kingdom/DailyTickEngine.kt` | 0 | 0 | 0 | 0 | 0 |
| `kingdom/TurnTickingEngine.kt` | 0 | 0 | 0 | 0 | 0 |
| `kingdom/CaravanTick.kt` | 0 | 0 | 0 | 0 | 0 |
| `kingdom/ArmyWarPressure.kt` | 0 | 0 | 0 | 0 | 0 |
| `camping/ExpeditionResolverEngine.kt` | 0 | 0 | 0 | 0 | 0 |

`ExpeditionResolverEngine.kt` states the property in its own header ("contains NO Foundry/DOM/roll/
chat/persistence dependencies"), and the other four hold it in fact.

### 2.2 Impure wrappers — the forecast must never call these

| File | `Random.` | `Date()` | `suspend fun` | `postChat` | persists | Pure core to call instead |
| --- | --- | --- | --- | --- | --- | --- |
| `kingdom/DailyTickHooks.kt` | 0 | 0 | 6 | 5 | 5 | `DailyTickEngine` |
| `kingdom/ExpeditionResolution.kt` | 0 | 1 | 6 | 16 | 5 | `ExpeditionResolverEngine` |

The trap is that the wrapper and the core have near-identical names. `DailyTickHooks.kt:299` defines
`private suspend fun tickPersonalQuests(game, daysPassed)` — plural, impure, posts chat and writes
state. The forecast wants `DailyTickEngine.tickPersonalQuest(...)` at `DailyTickEngine.kt:100` —
singular, pure. Calling the wrong one from a forecast posts real chat messages and writes real state
for events that have not happened.

### 2.3 Function-by-function

| Function | Where | Signature shape | Forecastable |
| --- | --- | --- | --- |
| `DailyTickEngine.tickTravelEta` | `DailyTickEngine.kt` | `(eta: Int?, days: Int) -> TravelTickResult` | yes, exactly |
| `DailyTickEngine.tickExpedition` | `DailyTickEngine.kt` | `(daysRemaining: Int, days: Int) -> ExpeditionTickResult` | yes, exactly |
| `DailyTickEngine.tickPersonalQuest` | `DailyTickEngine.kt:100` | `-> PersonalQuestTickResult` | yes, exactly |
| `TurnTickingEngine.tick` | `TurnTickingEngine.kt:170` | ~28 value params `-> TickResult` | yes, exactly |
| `tickCaravans` | `CaravanTick.kt:216` | `(List<CaravanTickInput>, bonusRdCap: Int?) -> CaravanTickResult` | **roll-dependent**, see §3 |
| `tickShipments` | `CaravanTick.kt:330` | `(List<ShipmentTickInput>) -> ShipmentTickResult` | **roll-dependent**, see §3 |

`TurnTickingEngine.tick` takes no state object — it takes ~28 scalars and arrays and returns a new
`TickResult`. There is nothing to snapshot and nothing to deep-copy: assembling the argument list
from `KingdomData` and discarding the result *is* the dry run. (There is no `KingdomState` type in
this repo; the persisted type is `KingdomData`.)

## 3. Rolls: forecast ranges, never dice

`tickCaravans` and `tickShipments` are pure **because the roll is an input**:
`CaravanTickInput.raidRoll` and `ShipmentTickInput.raidRoll` are supplied by the caller, which rolls
at `TurnWizardApplication.kt:380` and `:446` (`Random.nextInt(1, 21)`).

That means a forecast **must** decide what to pass, and both obvious answers are wrong:

- Rolling real dice in the forecast **spoils** the result — the GM reads next week's raid before it
  happens — and it **diverges**, because End Turn rolls again and gets a different number.
- Passing a fixed value (say 10) silently reports one arbitrary branch as if it were the outcome.

So the forecast never calls the roll-dependent tick at all. It calls the **DC helpers**, which are
pure and roll-free, and reports a probability:

```kotlin
/** Chance a d20 meets or beats [dc], as a percentage. A raid happens when the roll comes in UNDER
 *  the DC (see caravanRaidDc), so this is the chance the cargo gets through. */
fun d20AtLeastPercent(dc: Int): Int = ((21 - dc).coerceIn(0, 20)) * 5

/** "Raid DC 13 — 40% safe". Never a rolled outcome. */
fun forecastCaravanRisk(caravan: RawCaravan, safety: CaravanRouteSafety, partner: RawGroup?): RiskForecast
```

`caravanRaidDc(...)` and `shipmentRaidDc(...)` (`CaravanTick.kt`) already give the DC without a roll,
and the caravan board already displays it, so the forecast and the board agree by construction.

**Spoiler discipline.** A forecast row for a roll-dependent beat states the *stake and the odds*,
never an outcome: "day 4 — Tuskwater caravan runs the Pitax road, Raid DC 13, ~40% safe". Rows for
deterministic beats state the beat: "day 6 — Ekundayo's expedition resolves".

This is also what keeps preview/commit parity intact: the forecast consumes no randomness, so
running it does not change what End Turn subsequently rolls.

## 4. Data model

**No new persisted type, no new `Raw*` interface, no `Migration<N>`.** The forecast is recomputed
whenever the panel renders and is never written anywhere.

```kotlin
// commonMain: at/posselt/pfrpg2e/kingdom/forecast/ForecastModel.kt
enum class ForecastKind { ARRIVAL, COMPLETION, EXPIRATION, THREAT, RESOURCE, RISK }

/** One predicted beat. [dayOffset] is days from today; null means "at End Turn". */
data class ForecastBeat(
    val dayOffset: Int?,
    val kind: ForecastKind,
    /** i18n key, resolved by the caller -- never assembled at runtime (see §5). */
    val labelKey: String,
    val labelArgs: Map<String, String>,
    /** Present only for RISK beats: the stake, never an outcome. */
    val dc: Int? = null,
    val safePercent: Int? = null,
    /** MainNavEntry value the row jumps to. */
    val target: String,
)

data class ForecastResult(val beats: List<ForecastBeat>, val horizonDays: Int)
```

## 5. Engine design

`src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/forecast/ForecastEngine.kt`

```kotlin
/** Everything the forecast may read, assembled in jsMain. Pure here, so the whole engine is
 *  unit-testable without Foundry. */
data class ForecastInputs(
    val horizonDays: Int,
    val travelEtas: List<TravelEta>,          // label + remaining days
    val expeditions: List<ExpeditionEta>,
    val personalQuests: List<QuestEta>,
    val caravanRisks: List<RiskForecast>,     // DC + percent, already roll-free
    val endTurn: TickResult,                  // from TurnTickingEngine.tick on a discarded result
)

fun forecast(inputs: ForecastInputs): ForecastResult
```

**Daily beats** come from counting down each ETA with `DailyTickEngine`'s pure helpers — no loop over
days is needed for arrival dates, since `tickTravelEta(eta, days)` already takes a day count, so the
whole horizon is one call per entity. Cost is O(entities), not O(days x entities).

**End-turn beats** come from one `TurnTickingEngine.tick(...)` call whose `TickResult` is read and
thrown away. `TickResult` already carries what the panel needs, so the engine reads fields rather
than re-deriving them:

| `TickResult` field | Forecast row |
| --- | --- |
| `changes: List<TickChange>` | `(category, field, oldValue, newValue)` — a ready-made diff feed; RESOURCE rows come straight from it |
| `clockEvents: Array<ClockTickEvent>` | EXPIRATION rows |
| `newlyTriggeredThreats: Array<RawWarThreat>` | THREAT rows |
| `questDeadlineReached: List<String>` | EXPIRATION rows |
| `ruinThresholdCrossed: Boolean` | THREAT row |

`changes` is the field worth calling out: it is a complete before/after audit of the tick, so
"consumption goes negative by 2" needs no bespoke detection — it is a `TickChange` row.

**Tick surface.** The forecast hooks **nothing**. It is not a tick; it is a read performed when the
Session Prep panel renders. It borrows `TurnTickingEngine` (the monthly side) and `DailyTickEngine`
(the daily side) as pure functions and introduces no third tick.

## 6. UI

| Piece | Path |
| --- | --- |
| Template | `applications/kingdom/sections/session-prep/forecast.hbs` |
| Context | `kingdom/sheet/contexts/ForecastContext.kt` |
| Render target | `SessionPrepView.kt` |
| Styles | `.km-forecast*` in `applications/kingdom/kingdom-sheet.css` |
| i18n | `pf2e-kingmaker-tools.kingdom.forecast.*` |

**Horizon control.** A `<select>` of 3 / 7 / 14 days, defaulting to 7, stored in the sheet's
transient UI state only. **14 is a hard cap** — beyond two weeks the daily feeds are dominated by
events the GM has not scheduled yet, and the panel starts inventing certainty it does not have.

**GM-only.** The panel reads campaign-clock progress and unarrived war threats, which are secret by
construction. It renders inside the existing `{{#if isGM}}` region of Session Prep, **and**
`ForecastContext` is populated only when `game.user.isGM`. The template check controls layout; the
context check is the actual gate, because a player is an OWNER of the party actor and template
conditionals are not authorization. There is no player variant.

**Two repo constraints that have bitten before.** `forecast.hbs` is a registered partial and so has
no parent frame: inside `{{#each}}` reach outer values with `@root`, never `../`
(`check_hbs_scope.py` only catches chains that climb too far, never too few). And every row label
must be a **literal** i18n key — `ForecastBeat.labelKey` is a constant from a `when`, never
`"forecast.$kind"`, because a runtime-assembled key is invisible to `check_i18n_keys.py` and ships as
a raw key on screen with every guard green.

## 7. Chat and offer surfaces: none

**This feature adds no chat cards and no `km-offer-*` buttons, deliberately.**

The card's standing rule is that every *consequence* must be a GM-confirmed offer. A forecast has no
consequences: it predicts, it does not apply. An offer button exists to commit a state change, so a
button on a predicted event would either apply an event that has not happened — making the forecast
self-fulfilling and destroying the parity §3 protects — or do nothing, which is the dead-button bug
this repo has hit repeatedly.

The real consequences still arrive through the paths that already own them: `newlyTriggeredThreats`
posts its offer at End Turn, `ruinThresholdCrossed` posts the ruin offer from `performEndTurn`. The
forecast shows them coming; End Turn offers them.

## 8. Interactions and out of scope

**Reads:** `DailyTickEngine`, `TurnTickingEngine`, `CaravanTick` (DC helpers only), `ArmyWarPressure`,
`ExpeditionResolverEngine`, `SessionPrepView.kt`, `MainNavEntry` (jump targets).
**Writes:** nothing. Adding a write to this feature is a design error, not a feature.

**Out of scope:** multi-turn forecasting (compounding uncertainty; one turn only); pre-rolling any
dice; a player-visible variant; persisting a forecast; forecasting weather (its own daily system, and
its randomness is not injected the way the caravan roll is); acting on a forecast in any way.

## 9. Test plan

**commonTest** (`ForecastEngineTest`)
- A caravan 2 days out appears in a 3-day horizon and not in a 1-day horizon (both sides of the edge).
- `d20AtLeastPercent`: DC 1 → 100, DC 21 → 0, DC 13 → 40; clamped outside 1..21 rather than negative.
- A RISK beat carries `dc` and `safePercent` and **no** outcome field.
- `TickResult.changes` mapping: a negative consumption change becomes exactly one RESOURCE beat.
- `newlyTriggeredThreats` / `questDeadlineReached` / `clockEvents` each produce their row kind.
- Horizon clamped to 14.
- Every `labelKey` returned is a compile-time constant (assert against the known key set).

**jsTest** — `ForecastInputs` assembled from a fixture kingdom; `ForecastContext` null for a
non-GM user; forecast run twice over the same state returns equal results (purity/idempotence).

**Mutation-check every new test** (repo practice): flip the horizon comparison to `<`, return a
fixed 50 from `d20AtLeastPercent`, drop the `isGM` gate — and confirm the mutation *compiled* before
believing a "survived" result.

**Manual Foundry checklist**
1. Caravan 3 days out → Session Prep shows an arrival on day 3; change horizon to 1 → it disappears.
2. A caravan in transit → its row shows "Raid DC N — X% safe" and **no** outcome.
3. Open Session Prep, note the forecast, End Turn → the beats that fired match the deterministic
   rows; the risk rows may or may not have gone the predicted way (that is correct).
4. Open the panel five times → identical output every time; no chat messages posted; no state change.
5. Log in as a player → no forecast panel at all.
6. Set horizon to 14 with several caravans and expeditions → panel renders without stalling the sheet.

## 10. Phasing

**Phase 1 — pure engine.** `ForecastModel.kt`, `ForecastEngine.kt`, `d20AtLeastPercent`, full
commonTest suite. No UI, nothing wired.

**Phase 2 — inputs adapter.** Assemble `ForecastInputs` in jsMain from live state, including the one
discarded `TurnTickingEngine.tick` call. jsTest for purity and the GM gate.

**Phase 3 — panel.** `forecast.hbs`, `ForecastContext`, CSS, i18n for all eight locales (one locale
short and CI goes red; only `check_i18n_keys.py --all` catches it).

**Phase 4 — horizon control and jump-links.** The 3/7/14 select and row targets into `MainNavEntry`.

Phases 1 and 2 ship no user-visible change, which is what makes phases 3 and 4 safe.

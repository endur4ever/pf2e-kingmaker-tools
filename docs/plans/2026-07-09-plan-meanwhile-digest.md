# "Meanwhile in the Stolen Lands" — End-Turn Interlude Digest Plan

> Status: Plan-only · Branch: `kingmaker.5` · Plan file: `docs/plans/2026-07-09-plan-meanwhile-digest.md`
> Source of truth for persisted state: the free-form `kingdom-sheet` Foundry flag on the `PF2EParty` actor (`getAppFlag`/`setAppFlag`), NOT `KingdomSheetDataModel`.

---

## 1. Problem Statement + Player/GM Value

**The gap.** The module now runs **six independent off-screen feeds** every turn:
1. **Faction standing drift** (`FactionRelations.kt`) — standing ±N per turn, threshold crossings spawn war threats / diplomacy quests.
2. **Caravans in transit** (`CaravanTick.kt`) — raid rolls, cargo loss, arrivals delivering Resource Dice or Commodities.
3. **Campaign clocks** (`CampaignClockManager.kt`) — ETA countdowns, expirations, new deadlines spawned.
4. **Companion expeditions** (`ExpeditionResolverEngine.kt` + `DailyTickHooks.kt`) — daily-tick progress, degree-of-success outcomes, level-ups, injuries, faction standing deltas.
5. **War-threat ETAs** (`ArmyWarPressure.kt` + `TurnTickingEngine.kt`) — escalation steps, arrival triggers, pressure deltas on unrest/consumption/ruin.
6. **Weather / climate** (`DailyTickHooks.kt` + `Climate.kt`) — daily weather events, seasonal shifts, scene FX.

**The missing piece.** None of these feeds stitch their outputs into a single **narrated interlude** that tells the players *"the world moved while you governed."* Instead, each system posts its own chat card or journal entry — or nothing player-facing at all — so the table gets a fragmented, noisy stream that the GM must mentally assemble.

**Why this earns table time.** Kingmaker is a campaign about *ruling*. The fiction of the Stolen Lands is that events cascade: a caravan lost to bandits (feed 2) emboldens a hostile faction (feed 1), whose war threat (feed 5) advances faster, while the party's companion scouting ahead (feed 4) reports the buildup — and the GM needs **one** beat to read aloud that makes those connections visible. This feature provides that beat.

**Deliverables at End Turn (monthly) and on-demand from Session Prep:**
- **One player-facing interlude chat card** — prose narrative (2–4 beats), localized, no GM-only data.
- **One gazette entry** appended to the kingdom journal (`formatTurnGazette` extension).
- **GM offer cards only for consequences that require confirmation** (e.g., a war threat arrival that spawns an event) — *never* for pure flavor.

---

## 2. Data Model

### 2.1 New External Interfaces (Raw\*)

All new fields are **nullable** for migration safety. They live on the kingdom flag (same persistence layer as `companionExpeditions`, `turnHistory`, etc.).

#### `RawMeanwhileDigestConfig.kt` (jsMain `kingdom/data/`)

```kotlin
@JsPlainObject
external interface RawMeanwhileDigestConfig {
    /** Whether the end-turn interlude is enabled. Default true. */
    var enabled: Boolean?
    /** Maximum number of beats in the interlude. Default 4. Hard cap. */
    var maxBeats: Int?
    /** Whether to also post the interlude to the gazette journal. Default true. */
    var appendToGazette: Boolean?
    /** Deduplication window: how many prior turns' recap beats to compare against. Default 1. */
    var dedupWindowTurns: Int?
    /** Interest-scoring weights (see §3.1). Nullable → defaults in code. */
    var scoringWeights: RawScoringWeights?
}

@JsPlainObject
external interface RawScoringWeights {
    /** Weight for absolute state-change magnitude (e.g., |Δstanding|, |Δcargo|). Default 1.0 */
    var magnitude: Double?
    /** Weight for player relevance (kingdom-owned caravans, capital-adjacent clocks, etc.). Default 1.5 */
    var relevance: Double?
    /** Weight for recency (events this turn > events last turn). Default 0.5 per turn decay. */
    var recency: Double?
    /** Minimum score to be considered at all. Default 0.1 */
    var floor: Double?
}
```

#### `RawMeanwhileEvent.kt` (jsMain `kingdom/data/`) — adapter output event

```kotlin
@JsPlainObject
external interface RawMeanwhileEvent {
    /** Stable type tag for template selection: "factionDrift" | "caravan" | "clock" | "expedition" | "warThreat" | "weather" */
    var type: String
    /** Human-readable source name for dedup/context: "Pitax", "Olegs Caravan", "Stag Lord Clock", etc. */
    var source: String
    /** Machine-readable sub-type for scoring/template branching. */
    var subType: String
    /** The kingdom turn this event was emitted (for recency scoring). */
    var turn: Int
    /** Interest score computed by the pure engine (see §3.1). */
    var score: Double
    /** Payload for prose templates — only player-safe data. */
    var payload: AnyObject
    /** GM-only payload (never serialized to interlude). May contain hidden threat details. */
    var gmPayload: AnyObject?
    /** Localization key namespace prefix, e.g. "kingdom.meanwhile.factionDrift". */
    var i18nNamespace: String
    /** Whether this event is visible to players (always true for events that reach the digest; false for GM-only feed items). */
    var visibleToPlayers: Boolean
}
```

#### `RawMeanwhileDigestRecord.kt` (jsMain `kingdom/data/`) — persisted digest history for dedup

```kotlin
@JsPlainObject
external interface RawMeanwhileDigestRecord {
    var turn: Int
    var timestamp: String
    /** The selected beat summaries (strings) that were rendered this turn. */
    var beatSummaries: Array<String>
    /** The full interlude prose (HTML) for gazette append. */
    var interludeHtml: String
}
```

### 2.2 Persistence Location

| Data | Persists On | Flag Path |
|------|-------------|-----------|
| `RawMeanwhileDigestConfig` | Kingdom flag | `meanwhileDigest` |
| `RawMeanwhileDigestRecord[]` | Kingdom flag | `meanwhileDigestHistory` (cap 50) |

### 2.3 Migration

**MigrationNN** (next available number after current highest):
1. Initialize `meanwhileDigest` with defaults (`enabled=true`, `maxBeats=4`, `appendToGazette=true`, `dedupWindowTurns=1`, `scoringWeights=null`).
2. Initialize `meanwhileDigestHistory` to `[]`.
3. No data backfill needed — first run produces first digest.

---

## 3. Engine Design

### 3.1 Interest-Scoring Function (Pure, CommonMain)

**File:** `src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/MeanwhileScoring.kt`

```kotlin
/**
 * Pure interest scoring for Meanwhile events.
 *
 * Score = magnitude * w_magnitude + relevance * w_relevance + recency * w_recency
 * All inputs normalized to [0, 1] before weighting.
 *
 * Pure so it is fully unit-testable in commonTest.
 */
data class ScoringWeights(
    val magnitude: Double = 1.0,
    val relevance: Double = 1.5,
    val recency: Double = 0.5,
    val floor: Double = 0.1,
)

data class ScoringInput(
    /** Absolute change magnitude, normalized to [0,1] by source-specific max. */
    val magnitudeNorm: Double,
    /** Player relevance: 1.0 = directly involves kingdom assets/PCs; 0.0 = purely background. */
    val relevance: Double,
    /** Turns ago this event occurred (0 = this turn). */
    val turnsAgo: Int,
)

fun computeInterestScore(input: ScoringInput, weights: ScoringWeights): Double {
    val recencyNorm = (1.0 - (input.turnsAgo.toDouble() * weights.recency)).coerceIn(0.0, 1.0)
    val score = input.magnitudeNorm * weights.magnitude
        + input.relevance * weights.relevance
        + recencyNorm * weights.recency
    return score.coerceAtLeast(weights.floor)
}
```

**Source-specific normalization (each adapter computes `magnitudeNorm`):**

| Source | `magnitudeNorm` Formula | Max Reference |
|--------|------------------------|---------------|
| Faction drift | `abs(delta) / 100.0` | ±100 standing scale |
| Caravan | `cargoLost / max(cargoAmount, 1)` for raids; `deliveredAmount / max(cargoAmount, 1)` for arrivals | pre-tick cargo |
| Campaign clock | `1.0` if expired/triggered this turn; `turnsRemaining / maxTurns` otherwise | Clock duration |
| Expedition | `1.0` for crit success/fail; `0.7` for success; `0.4` for failure; `0.1` for in-progress | DegreeOfSuccess |
| War threat | `escalationLevel / maxEscalation`; `1.0` if triggered this turn | Threat max escalation |
| Weather | `eventLevel / partyLevel` clamped [0,1] for hazardous events; `0.2` for flavor | Party level |

> **`CaravanEvent` now carries this context.** It was widened with four defaulted fields —
> `cargoAmount`, `cargoCommodity`, `originLabel`, `destLabel` — populated at every construction site,
> so both the magnitude formula and §3.5's prose templates resolve without touching persistence.
>
> `cargoAmount` is the cargo as it stood **before** the tick: `advanced()` returns a new caravan
> rather than mutating, so reading it at the event site gives the pre-raid figure. That is the
> denominator the score needs — losing 2 of 10 is not losing 2 of 2.
>
> Magnitude: `cargoLost / max(cargoAmount, 1)` for raids, `deliveredAmount / max(cargoAmount, 1)` for
> arrivals. The `{origin}`, `{destination}` and `{commodity}` placeholders now resolve for raid beats
> too — the RAIDED event previously carried no commodity at all.

**Relevance scoring rules (deterministic, no RNG):**
- `1.0`: involves kingdom capital, PC-owned caravan, active companion, faction at war/allied.
- `0.7`: involves claimed hex, trade partner, active clock linked to kingdom quest.
- `0.4`: involves neighboring hex, neutral faction, background clock.
- `0.1`: purely cosmetic (flavor weather, distant faction drift).

### 3.2 Feed Adapter Interface (Pure, CommonMain)

**File:** `src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/MeanwhileAdapters.kt`

```kotlin
/**
 * Each off-screen feed implements this interface to expose typed events for the digest.
 * Adapters read EXISTING persisted state only — NO new persistence.
 */
interface MeanwhileFeedAdapter {
    /** Unique feed identifier (matches RawMeanwhileEvent.type). */
    val feedType: String
    /** Human-readable source name for dedup context. */
    val sourceName: String
    /** i18n namespace prefix for this feed's templates. */
    val i18nNamespace: String

    /**
     * Extract player-safe events from the current kingdom state for the given turn.
     * @param kingdom The kingdom data snapshot (Raw* interfaces).
     * @param currentTurn The turn being digested.
     * @param previousTurnEvents Events from the previous turn (for recency=0 scoring).
     * @return List of RawMeanwhileEvent with score already computed.
     */
    fun collectEvents(
        kingdom: KingdomSnapshot,
        currentTurn: Int,
        previousTurnEvents: List<RawMeanwhileEvent>,
        weights: ScoringWeights,
    ): List<RawMeanwhileEvent>

    /**
     * Filter function: returns true if the event contains NO GM-only data and is safe for players.
     * Default implementation checks `visibleToPlayers` flag; adapters can override for custom logic.
     */
    fun isPlayerSafe(event: RawMeanwhileEvent): Boolean = event.visibleToPlayers
}
```

**`KingdomSnapshot` (pure data carrier, commonMain):**
```kotlin
data class KingdomSnapshot(
    val turn: Int,
    val groups: Array<RawGroup>,           // for faction drift
    val caravans: Array<RawCaravan>,       // for caravan tick results
    val campaignClocks: Array<CampaignClock>,
    val companionExpeditions: Array<RawCompanionExpedition>,
    val companions: Array<RawCharacter>,
    val warThreats: Array<RawWarThreat>,
    val turnHistory: Array<RawTurnRecord>, // for last-turn recap dedup
    val weatherEvents: Array<WeatherEvent>?, // if persisted
    val config: RawMeanwhileDigestConfig,
)
```

### 3.3 Concrete Adapters (jsMain) — Event Types Available TODAY

Each adapter enumerates the exact `subType` values it emits **without new persistence**.

| Feed | `feedType` | `subType` values (today) | Player-Safe Payload Fields |
|------|------------|--------------------------|----------------------------|
| **FactionDriftAdapter** | `factionDrift` | `drift`, `thresholdCrossHostile`, `thresholdCrossFriendly` | `factionName`, `oldStanding`, `newStanding`, `delta`, `attitudeBand`, `isThresholdCross` |
| **CaravanAdapter** | `caravan` | `raidLoss`, `arrivedSell` (Resource Dice), `arrivedBuy` (Commodities), `lost` | `caravanId`, `origin`, `destination`, `commodity`, `amount`, `lost`, `bonusRD`, `partnerName`, `partnerStanding` |
| **ClockAdapter** | `clock` | `ticking`, `expired`, `newDeadline` | `clockId`, `name`, `turnsRemaining`, `isExpired`, `isNew` |
| **ExpeditionAdapter** | `expedition` | `completedCritSuccess`, `completedSuccess`, `completedFailure`, `completedCritFailure`, `inProgress` | `expeditionId`, `title`, `companionNames`, `activityId`, `tier`, `outcomeDegree`, `xpAwarded`, `lootTier`, `factionStandingDelta`, `targetFaction`, `willLevelUp`, `completesQuest` |
| **WarThreatAdapter** | `warThreat` | `escalated`, `triggered`, `pressureDelta` | `threatId`, `name`, `enemyFaction`, `escalationLevel`, `maxEscalation`, `eta`, `triggeredTurn`, `pressureDelta`, `unrestDelta`, `consumptionDelta` |
| **WeatherAdapter** | `weather` | `hazardous`, `flavor`, `seasonalShift` | `eventName`, `severity`, `description`, `isHazardous`, `season` |

**Visibility rules reused from each source:**
- Faction: `RawGroup` has no visibility split — standing is always GM-visible; `thresholdCross*` events are GM-offer triggers only, marked `visibleToPlayers=false` in adapter.
- Caravans: all events player-safe (cargo amounts are public knowledge).
- Clocks: `expired`/`newDeadline` are GM-only if clock is secret; adapter checks `clock.secret` flag.
- Expeditions: `RawCompanionExpedition.visibleToPlayers` respected; `gmPayload` holds injury conditions.
- War threats: `triggered` events are GM-only (spawn event/queue encounter offers); `escalated`/`pressureDelta` are player-safe.
- Weather: `hazardous` events are player-safe (FX visible); `flavor` always safe.

### 3.4 Digest Aggregator (Pure, CommonMain)

**File:** `src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/MeanwhileDigestEngine.kt`

```kotlin
data class DigestConfig(
    val maxBeats: Int = 4,
    val dedupWindowTurns: Int = 1,
    val weights: ScoringWeights = ScoringWeights(),
)

data class DigestResult(
    val selectedEvents: List<RawMeanwhileEvent>,
    val beatSummaries: List<String>, // plain-text one-liners for dedup
    val interludeHtml: String,       // full prose for chat card
    val gazetteLine: String,         // one-line for formatTurnGazette
)

/**
 * Pure aggregator: collects from all adapters, scores, dedups, caps, renders prose.
 */
object MeanwhileDigestEngine {

    fun buildDigest(
        snapshot: KingdomSnapshot,
        adapters: List<MeanwhileFeedAdapter>,
        previousDigest: RawMeanwhileDigestRecord?,
        config: DigestConfig,
        localize: (key: String, data: AnyObject) -> String,
    ): DigestResult {
        // 1. Collect all events from all adapters
        val allEvents = adapters.flatMap { adapter ->
            adapter.collectEvents(snapshot, snapshot.turn, previousDigest?.beatSummaries?.map { RawMeanwhileEvent(payload = js("{}")) } ?: emptyList(), config.weights)
        }

        // 2. Filter player-safe
        val playerEvents = allEvents.filter { it.visibleToPlayers && it.isPlayerSafe(it) }

        // 3. Score & sort descending
        val scored = playerEvents.sortedByDescending { it.score }

        // 4. Dedup against last-turn recap (b080cfe2) — compare beatSummaries strings
        val recentSummaries = snapshot.turnHistory
            .takeLast(config.dedupWindowTurns)
            .flatMap { it.notes?.split(" | ") ?: emptyList() }
            .toSet()

        val deduped = scored.filter { event ->
            val summary = renderBeatSummary(event, localize)
            !recentSummaries.contains(summary)
        }

        // 5. Hard cap
        val selected = deduped.take(config.maxBeats)

        // 6. Render prose (template-based, see §3.5)
        val interludeHtml = renderInterlude(selected, localize)
        val gazetteLine = renderGazetteLine(selected, localize)
        val beatSummaries = selected.map { renderBeatSummary(it, localize) }

        return DigestResult(selected, beatSummaries, interludeHtml, gazetteLine)
    }

    // Template rendering functions — see §3.5
}
```

### 3.5 Prose Templates (3 Samples Per Event Type)

All templates use the **localizer-injected pattern** from `SessionPrepNarrativeGenerator.kt` — `localize(key, data)` with nested `en.json` keys under `kingdom.meanwhile.*`.

#### Faction Drift (`kingdom.meanwhile.factionDrift.*`)

| Key | Template (en) |
|-----|---------------|
| `drift` | `"Relations with {faction} have {shifted} — standing is now {newStanding} ({attitude})."` |
| `thresholdCrossHostile` | `"Tensions with {faction} have boiled over. They now regard the kingdom as **Hostile** — war looms."` |
| `thresholdCrossFriendly` | `"Diplomatic overtures bear fruit: {faction} now views the kingdom as **Friendly**. Trade and alliance are possible."` |

**Shifted wording:** `shifted` = "improved" (delta > 0) / "worsened" (delta < 0) / "drifted" (delta == 0 but attitude changed — impossible with current drift, reserved for future).

#### Caravan (`kingdom.meanwhile.caravan.*`)

| Key | Template (en) |
|-----|---------------|
| `raidLoss` | `"A caravan bound for {destination} was waylaid by bandits near {origin}. {lost} {commodity} were lost; the survivors press on."` |
| `arrivedSell` | `"The caravan from {origin} arrived at {destination}, delivering {commodity} and securing {bonusRD} bonus Resource Die for the treasury."` |
| `arrivedBuy` | `"A shipment of {amount} {commodity} from {partner} has reached {destination}, replenishing the kingdom's stores."` |

#### Campaign Clock (`kingdom.meanwhile.clock.*`)

| Key | Template (en) |
|-----|---------------|
| `ticking` | `"{name} ticks down — {turnsRemaining} turns remain before the deadline."` |
| `expired` | `"The {name} deadline has passed. Consequences unfold across the Stolen Lands."` |
| `newDeadline` | `"A new pressure emerges: {name} — {turnsRemaining} turns to act."` |

#### Expedition (`kingdom.meanwhile.expedition.*`)

| Key | Template (en) |
|-----|---------------|
| `completedCritSuccess` | `"{companionNames} return triumphant from **{title}**. Their success echoes across the kingdom (+{xpAwarded} XP, {lootTier} loot)."` |
| `completedSuccess` | `"{companionNames} complete **{title}** with solid results (+{xpAwarded} XP)."` |
| `completedCritFailure` | `"{companionNames} limp back from **{title}** — the expedition ended in disaster. A narrative complication awaits the GM's eye."` |

#### War Threat (`kingdom.meanwhile.warThreat.*`)

| Key | Template (en) |
|-----|---------------|
| `escalated` | `"The {name} threat escalates (stage {escalationLevel}/{maxEscalation}). War pressure on the kingdom rises."` |
| `triggered` | `"{name} has arrived at the kingdom's doorstep. The GM must decide the consequence."` (GM-only, not in interlude) |
| `pressureDelta` | `"War pressure shifts: {unrestDelta} unrest, {consumptionDelta} consumption from {name}."` |

#### Weather (`kingdom.meanwhile.weather.*`)

| Key | Template (en) |
|-----|---------------|
| `hazardous` | `"A {eventName} sweeps the Stolen Lands — {description}. Travel and outdoor activities are hindered."` |
| `flavor` | `"The weather turns: {eventName}. {description}"` |
| `seasonalShift` | `"The season shifts to {season}. New challenges and opportunities arise."` |

### 3.6 Tick Integration Points

| Tick Surface | Hook | Responsibility |
|--------------|------|----------------|
| **TurnTickingEngine** (monthly, End Turn) | After `performEndTurn` commits, before chat cards posted | Build `KingdomSnapshot`, run `MeanwhileDigestEngine.buildDigest`, persist `RawMeanwhileDigestRecord`, post interlude chat card + append gazette. |
| **SessionPrepView** (on-demand) | `SessionPrepNarrativeGenerator.generate()` extension | Reuse same engine with `currentTurn = latestTurn`, `isGM = true` for full visibility; inject into session prep narrative. |

**No third tick invented.** Daily tick (`DailyTickHooks`) feeds expeditions/weather but does **not** produce interludes — interludes are monthly (End Turn) + on-demand (Session Prep).

---

## 4. UI

### 4.1 Kingdom Sheet — Configuration Section

- **Tab/Section:** Add a **Meanwhile Digest** subsection under the existing **Settings** tab (or a new **Interludes** sub-tab if Settings grows).
- **Template:** `src/jsMain/resources/applications/kingdom/sections/meanwhile/page.hbs`
- **Context:** `MeanwhileConfigContext` (mirrors `KingdomSettingsContext` pattern)
- **i18n namespace:** `kingdom.meanwhile.config.*`

**Settings exposed:**
- Enable/disable interlude (`enabled`)
- Max beats (`maxBeats`, 1–6, default 4)
- Append to gazette (`appendToGazette`, default true)
- Dedup window (`dedupWindowTurns`, 0–3, default 1)
- Scoring weights (advanced, collapsed by default): magnitude, relevance, recency, floor

### 4.2 Chat Card Template

**File:** `src/jsMain/resources/chatmessages/meanwhile-interlude.hbs`

```handlebars
<div class="km-meanwhile-interlude">
    <header class="km-meanwhile-header">
        <h3>{{localizeKM "kingdom.meanwhile.interludeTitle"}}</h3>
        <span class="km-meanwhile-turn">Turn {{turn}}</span>
    </header>
    <section class="km-meanwhile-body">
        {{{interludeHtml}}}
    </section>
    {{#if gmOffers.length}}
    <footer class="km-meanwhile-offers">
        {{#each gmOffers}}
            {{> this}}
        {{/each}}
    </footer>
    {{/if}}
</div>
```

**Context object:** `MeanwhileInterludeContext { turn, interludeHtml, gmOffers: Array<String> }` — `gmOffers` are pre-rendered `km-offer-*` chat cards (see §5).

### 4.3 Gazette Integration

Extend `formatTurnGazette` in `TurnHistory.kt` with a new parameter `meanwhileGazetteLine: String?` — appended as a final segment if non-null. The gazette line is a **single-line summary** (e.g., `"Meanwhile: Pitax turns Hostile; Olegs Caravan delivers +2 RD; Stag Lord Clock expires"`).

---

## 5. Chat/Offer Surfaces

**Rule:** Every consequence that mutates game state is a **GM-confirmed offer** (`km-offer-*` pattern, `ChatButtons.kt`). The interlude itself is **read-only prose** — no buttons unless a feed adapter flags a `gmPayload` that requires confirmation.

### 5.1 Offer Cards Enumerated

| Trigger | Offer Button Class | Payload | Effect on Confirm |
|---------|-------------------|---------|-------------------|
| Faction drift → Hostile threshold | `km-offer-war-threat` | `{ faction }` | Opens `AddWarThreat` dialog prefilled |
| Faction drift → Friendly threshold | `km-offer-diplomacy-quest` | `{ faction }` | Opens `AddQuest` dialog prefilled |
| War threat triggered (arrival) | `km-offer-war-threat-arrival` | `{ threatId, action: spawnEvent\|queueEncounter\|dismiss }` | Spawns event / queues encounter / dismisses |
| Expedition crit failure (injury) | `km-offer-injury` | `{ expeditionId, companionId }` | Applies injury conditions to linked actor |
| Expedition level-up | `km-offer-companion-levelup` | `{ companionId, targetLevel }` | Bumps real PF2e actor level |
| Expedition reward (XP/loot/influence) | `km-offer-expedition-reward` | `{ expeditionId }` | Applies accrued rewards, marks resolved |

**Interlude card includes only the offers relevant to the selected beats.** The `gmOffers` array in `MeanwhileInterludeContext` is built by the impure wrapper after `MeanwhileDigestEngine.buildDigest` — it inspects `gmPayload` on selected events and renders the appropriate `km-offer-*` partials.

---

## 6. Interactions with Existing Systems + Out-of-Scope

### 6.1 Concrete File Interactions

| System | Files Touched | Nature |
|--------|---------------|--------|
| Turn tick hook | `TurnTickingEngine.kt` (performEndTurn), `TurnWizardApplication.kt` | Call `MeanwhileDigestEngine.buildDigest` post-commit; persist record; post chat card. |
| Gazette | `TurnHistory.kt` (`formatTurnGazette`) | Add `meanwhileGazetteLine` param + i18n key. |
| Session Prep | `SessionPrepNarrativeGenerator.kt`, `SessionPrepView.kt` | Optional: call engine with `isGM=true` to inject interlude into GM prep. |
| Faction drift | `FactionRelations.kt`, `TurnTickingEngine.kt` (drift step) | Adapter reads `RawGroup.standing` + `standingLog`; no writes. |
| Caravans | `CaravanTick.kt`, `TurnTickingEngine.kt` | Adapter reads `CaravanTickResult.events`; no writes. |
| Clocks | `CampaignClockManager.kt`, `TurnTickingEngine.kt` | Adapter reads `CampaignClock` array; no writes. |
| Expeditions | `ExpeditionResolverEngine.kt`, `DailyTickHooks.kt` | Adapter reads `RawCompanionExpedition` (status, outcomeDegree, accrued*); no writes. |
| War threats | `ArmyWarPressure.kt`, `TurnTickingEngine.kt` | Adapter reads `RawWarThreat` + `newlyTriggeredThreats`; no writes. |
| Weather | `DailyTickHooks.kt`, `Climate.kt` | Adapter reads weather events if persisted; else emits seasonal flavor from `Climate.currentSeason`. |
| Chat buttons | `ChatButtons.kt` | No new buttons — reuses existing `km-offer-*` handlers. |
| i18n | `lang/en.json`, `lang/de.json`, etc. | New `kingdom.meanwhile.*` nested keys (see §3.5). |
| Settings | `KingdomSettings.kt`, `KingdomData.kt` | Add `meanwhileDigest` config block to kingdom flag. |

### 6.2 Explicit OUT-OF-SCOPE

- **No LLM/AI prose generation** — template-based only, like `SessionPrepNarrativeGenerator`.
- **No new persistence** for feed events — adapters read existing state only.
- **No new tick surface** — only End Turn (monthly) and Session Prep (on-demand).
- **No player-facing config UI beyond the Settings subsection** — no separate sheet tab.
- **No cross-kingdom digest** — single kingdom per world (current architecture).
- **No real-time streaming** — interlude is a discrete beat at turn boundary.
- **No auto-apply of GM-offer consequences** — every mutation remains a confirmed click.

---

## 7. Test Plan

### 7.1 commonTest (Pure Logic)

| Test Target | Cases |
|-------------|-------|
| `MeanwhileScoringTest` | Score boundaries: magnitudeNorm=0/1, relevance=0/1, turnsAgo=0/1/2; weight variations; floor enforcement. |
| `MeanwhileDigestEngineTest` | Collection from mock adapters; scoring sort; dedup against previous summaries; maxBeats cap; empty input; all-filtered-by-visibility. |
| Adapter unit tests (one per feed) | Each `collectEvents` returns correct `subType`, `visibleToPlayers`, `score` matches hand-calculated expectation for fixtures. |
| Template rendering | `renderInterlude` produces expected HTML structure for given event list; `renderGazetteLine` is single-line; `renderBeatSummary` stable for dedup. |

### 7.2 jsTest (Integration + Foundry Fixtures)

| Test | Fixture | Verifies |
|------|---------|----------|
| `MeanwhileDigestIntegrationTest` | Kingdom with 2 factions (one drifting hostile), 1 caravan (raided), 1 clock (expiring), 1 expedition (crit success), 1 war threat (escalated) | End-to-end: `performEndTurn` → digest built → chat card posted → gazette appended → record persisted. |
| `SessionPrepMeanwhileTest` | Same kingdom, call `SessionPrepNarrativeGenerator.generate` | Interlude appears in GM prep output; player-safe filtering works. |
| `MeanwhileConfigTest` | Toggle enabled=false | No interlude posted, no gazette line, no record persisted. |
| `DedupTest` | Two consecutive turns with identical caravan arrival | Second turn omits the duplicate beat; gazette line differs. |

### 7.3 Manual Foundry Verification Checklist

1. Start a kingdom with 2+ factions, 1 caravan in transit, 1 active clock, 1 companion on expedition, 1 war threat.
2. Advance to End Turn → click **End Turn** in Turn Wizard.
3. Observe chat: **one** "Meanwhile in the Stolen Lands" card appears with 2–4 prose beats.
4. Open Kingdom Sheet → **Journal** → **Gazette**: last entry includes a "Meanwhile:" line.
5. Reload world → interlude card persists in chat; gazette entry persists.
6. Open Session Prep → GM view includes the same interlude prose.
7. Toggle "Enabled" off in Settings → End Turn → no interlude card, no gazette line.
8. Set `maxBeats=2` → verify only 2 beats appear.
9. Drive a faction to Hostile → verify `km-offer-war-threat` button appears in interlude footer.
10. Complete an expedition with crit failure → verify `km-offer-injury` button appears.
11. Run `python3 scripts/check_i18n_keys.py` → 0 unresolved, 0 flat-dotted keys.
12. Run `./gradlew assemble jsTest` → all tests green.

---

## 8. Phasing (2–5 Independently Committable Phases)

| Phase | Scope | Deliverable | Worker Card Size |
|-------|-------|-------------|------------------|
| **1. Core Engine & Adapters** | `MeanwhileScoring.kt`, `MeanwhileAdapters.kt` (interface + 6 concrete adapters), `MeanwhileDigestEngine.kt`, `KingdomSnapshot.kt`, unit tests. | Pure aggregator that selects top-N beats from mocked kingdom state. | 1 card (builder) |
| **2. Persistence & Config** | `RawMeanwhileDigestConfig.kt`, `RawMeanwhileEvent.kt`, `RawMeanwhileDigestRecord.kt`, `MigrationNN.kt`, `KingdomData.kt` (config + history fields), `Defaults.kt`, config UI (Settings subsection + HBS + i18n). | Config persisted, migration runs, Settings UI toggles work. | 1 card (builder) |
| **3. Turn Tick Integration** | `TurnTickingEngine.kt` (hook after commit), `TurnWizardApplication.kt` (post interlude chat card), `TurnHistory.kt` (gazette line param + i18n), `ChatButtons.kt` (no new buttons — verify existing handlers fire). | End Turn produces interlude card + gazette entry + persisted record. | 1 card (builder) |
| **4. Session Prep & Polish** | `SessionPrepNarrativeGenerator.kt` (optional interlude injection), `SessionPrepView.kt`, template polish, dedup verification, full i18n pass (en/de/fr/it/pl/pt-BR/ru/zh-Hans). | On-demand interlude in Session Prep; all 8 languages green; manual checklist passes. | 1 card (builder) |
| **5. QA & Green Build** | Full `./gradlew assemble jsTest`, `check_i18n_keys.py`, manual Foundry verification checklist (§7.3). | Clean build, all tests pass, no regressions. | 1 card (qa) |

**Total: 5 phases → 5 worker cards.** Each phase is independently committable and verifiable.

---

## Appendix: Localization Key Namespace (en.json)

```json
{
  "kingdom": {
    "meanwhile": {
      "interludeTitle": "Meanwhile in the Stolen Lands",
      "config": {
        "enabled": "Enable End-Turn Interlude",
        "maxBeats": "Maximum Beats",
        "appendToGazette": "Append to Gazette",
        "dedupWindowTurns": "Deduplication Window (Turns)",
        "scoring": {
          "title": "Interest Scoring Weights (Advanced)",
          "magnitude": "Magnitude Weight",
          "relevance": "Relevance Weight",
          "recency": "Recency Weight",
          "floor": "Minimum Score Floor"
        }
      },
      "factionDrift": {
        "drift": "Relations with {faction} have {shifted} — standing is now {newStanding} ({attitude}).",
        "thresholdCrossHostile": "Tensions with {faction} have boiled over. They now regard the kingdom as **Hostile** — war looms.",
        "thresholdCrossFriendly": "Diplomatic overtures bear fruit: {faction} now views the kingdom as **Friendly**. Trade and alliance are possible."
      },
      "caravan": {
        "raidLoss": "A caravan bound for {destination} was waylaid by bandits near {origin}. {lost} {commodity} were lost; the survivors press on.",
        "arrivedSell": "The caravan from {origin} arrived at {destination}, delivering {commodity} and securing {bonusRD} bonus Resource Die for the treasury.",
        "arrivedBuy": "A shipment of {amount} {commodity} from {partner} has reached {destination}, replenishing the kingdom's stores."
      },
      "clock": {
        "ticking": "{name} ticks down — {turnsRemaining} turns remain before the deadline.",
        "expired": "The {name} deadline has passed. Consequences unfold across the Stolen Lands.",
        "newDeadline": "A new pressure emerges: {name} — {turnsRemaining} turns to act."
      },
      "expedition": {
        "completedCritSuccess": "{companionNames} return triumphant from **{title}**. Their success echoes across the kingdom (+{xpAwarded} XP, {lootTier} loot).",
        "completedSuccess": "{companionNames} complete **{title}** with solid results (+{xpAwarded} XP).",
        "completedCritFailure": "{companionNames} limp back from **{title}** — the expedition ended in disaster. A narrative complication awaits the GM's eye."
      },
      "warThreat": {
        "escalated": "The {name} threat escalates (stage {escalationLevel}/{maxEscalation}). War pressure on the kingdom rises.",
        "pressureDelta": "War pressure shifts: {unrestDelta} unrest, {consumptionDelta} consumption from {name}."
      },
      "weather": {
        "hazardous": "A {eventName} sweeps the Stolen Lands — {description}. Travel and outdoor activities are hindered.",
        "flavor": "The weather turns: {eventName}. {description}",
        "seasonalShift": "The season shifts to {season}. New challenges and opportunities arise."
      }
    }
  }
}
```

---

*End of plan. Ready for Gregory's review before implementation cards are created.*
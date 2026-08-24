# Map Dynamism — Wandering Threats & Re-Wilding Hexes — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** New backlog — "Map Dynamism" (living map / time pressure)
> **Depends on:** Army & War Pressure Board (#12, `RawWarThreat` + `tickWarThreats`), Hex Content system (`RawHexContent`, `HexContentSync`), the native `pf2e-kingmaker` realm map (`kingmaker.state`), `TurnTickingEngine` monthly tick, Turn History gazette
> **Branch:** `kingmaker.5`

---

## Executive Summary

The Stolen Lands map is currently **static between turns**. A war threat, once placed, sits on its target hex forever until the GM moves it by hand. A hex the party *clears* (kills the monster, drives off the bandits) but never gets around to *claiming* stays permanently safe — the house rule "No random combat encounters are rolled in claimed hexes" plus the camping encounter filter means a cleared+unclaimed hex reads as tamed indefinitely. Neither reflects the fiction of a frontier that pushes back.

This feature makes the map **breathe on the monthly cadence**:

1. **Wandering threats migrate.** A war threat flagged as mobile (a monster lair's threat radius, a marauding warband) creeps **one hex per turn toward its target settlement**. Destination is chosen deterministically (nearest step toward the target) with a GM speed dial. Each move is a **GM-confirmed offer**, never a silent map write.
2. **Cleared-but-unclaimed hexes re-wild.** A hex that is `cleared == true && claimed != true` starts a **re-wild timer**. After `rewildDelayTurns` turns without being claimed, it becomes a re-wild candidate: on GM confirmation its native `cleared` flag is lapsed back to `false`, so random combat encounters return there. This adds real time pressure to **claim what you clear**.

Both engines are **pure and deterministic** — derived entirely from persisted state + `currentTurn`, no `Math.random()` / `Date` — so the End-Turn preview and commit produce byte-identical proposals. All externally-visible consequences surface as one per-turn **"Map Changes" digest card** (GM-whispered) with per-change Apply/Hold/Dismiss buttons, mirroring the existing `war-threat-arrival-offer` flow.

---

## 1. Problem Statement + Player/GM Value

**Problem.** The realm map has no temporal dimension between kingdom turns:

- `RawWarThreat` carries a `targetSettlementSceneId` / `targetHexLocation` and an escalation clock (`tickWarThreats` in `ArmyWarPressure.kt`), but the threat never physically *moves*. Its whole life is "count down ETA, escalate, arrive." A GM who wants "the troll lair's raiders are getting closer" must hand-edit the threat's target and re-place the marker every turn.
- The native Kingmaker `HexState` (`kingmaker.state.hexes[key]`) exposes `cleared`, `claimed`, `explored` booleans. Camping's `RandomEncounters.kt` suppresses combat in claimed/cleared hexes (house rule, `docs/house-rules.md` line 60). So once you clear a hex it stays safe forever — there is **zero incentive to actually claim it**, and no mechanism to walk that back.

**Value to the table:**

- **Living-world atmosphere.** The map changes turn to turn on its own. "The Owlbear's hunting range has crept to the hex next to Tatzlford" appears as an offer card; the GM clicks *Advance* and the marker moves.
- **Genuine time pressure.** Clearing a hex is no longer permanent tenure. If the party clears the road to Varnhold but never claims it, monsters trickle back — a concrete, rules-backed reason to spend the Claim Hex activity, directly serving the house-rule goal "rewards claiming land" (`docs/house-rules.md` line 87).
- **GM prep reduction.** The threat-creep and re-wild bookkeeping the GM would otherwise track on paper is computed and proposed automatically, but nothing is applied without a click — the GM stays in control of the canonical map.
- **Gazette fuel.** Every accepted change emits a public gazette line, feeding "Recent Turns" and the session-prep journal export.

---

## 2. Data Model

### 2.1 Where "cleared-but-unclaimed" and threat position live today (audit)

The native realm map state is **not ours to extend**. `kingmaker.state` is the `pf2e-kingmaker` module's DataModel, persisted to a world setting; `com/foundryvtt/kingmaker/KingmakerModule.kt` binds it as:

```kotlin
external interface HexState {
    val commodity: String?
    val camp: String?
    val features: Array<HexFeature>?
    val claimed: Boolean?     // read-only to us
    val explored: Boolean?
    val cleared: Boolean?
}
external interface KingmakerState { val hexes: ReadonlyRecord<String, HexState> }
```

**Finding:** "cleared-but-unclaimed" is expressed **today** as the live predicate `hexState.cleared == true && hexState.claimed != true`. This is already the exact filter used elsewhere:
- `camping/RandomEncounters.kt` (~lines 229-249, 312-340) reads `hexState.claimed` / `hexState.cleared` to suppress combat encounters.
- `kingdom/ExpeditionDestinations.kt:90` uses the sibling predicate `it.claimed != true && it.explored == true` ("explored but unclaimed").
- `dialogs/TurnWizardApplication.kt:304` uses `hs?.claimed == true || hs?.cleared == true`.

**Two hard constraints follow:**
- The `HexState` fields are **`val` (read-only)** and there is **no turn/timestamp field**. A re-wild timer ("turns since cleared without claim") therefore **cannot be stored on `kingmaker.state`**. It must live on our **kingdom flag** as a side-table keyed by hex.
- Writing any hex flag back (lapsing `cleared`) is a **GM-only DataModel write** via `state.updateSource({hexes:{[key]:…}})` + `state.save()`. This is exactly the annexation path already in `KingdomSheet.kt:1252-1263`. Player clients cannot do this (memory lesson: embedded-doc / `kingmaker.state` writes are GM-only) — hence every re-wild is an offer applied by a GM.

Threat position: `RawWarThreat` (kingdom flag `warThreats: Array<RawWarThreat>?`) already has `targetSettlementSceneId: String?` and `targetHexLocation: String?` (a hex-key string, parsed with `toIntOrNull` in `ArmyBattleView.kt:299`). There is **no "current position" field** — a static threat is only ever *at* its target — so migration needs one.

### 2.2 New external interfaces (all `@JsPlainObject`, nullable for migration safety)

#### `RawWarThreat.kt` — ADDITIONS only (keep every existing field)

```kotlin
// Existing: id, name, description, enemyFaction, escalationLevel, maxEscalation, eta,
//   targetSettlementSceneId, targetHexLocation, linkedQuestId, linkedEventId,
//   pauseOnExpiry, status, triggeredTurn, offerConsumed, visibleToPlayers

/** When true this threat physically migrates one step toward its target each turn. Null/false = static (current behavior). */
var wanders: Boolean?

/** The hex key where the threat's radius currently sits. Null => treat as at [targetHexLocation]. Advances only on GM-accepted migration. */
var currentHexLocation: String?

/** Idempotency guard: the turn number whose migration offer for this threat was already resolved (accept OR dismiss). Mirrors [offerConsumed] semantics but is per-turn so a held offer re-proposes next turn. */
var migrationConsumedTurn: Int?
```

Rationale for landing on `RawWarThreat`: threat mobility is a property of the threat, threats already persist on the kingdom flag, and migration mutates the threat's own marker/position — no new top-level collection is warranted.

#### `RawRewildTracker.kt` — NEW, lives in `kingdom/data/`

```kotlin
@JsPlainObject
external interface RawRewildTracker {
    /** Native Kingmaker hex key (string form, matches kingmaker.state.hexes keys). */
    var hexKey: String
    /** Kingdom turn on which this hex was first observed cleared-and-unclaimed. Reset whenever the hex is claimed or re-cleared. */
    var clearedSinceTurn: Int
    /** True once this hex's re-wild offer has been resolved (re-wild applied or dismissed) for its current cycle. */
    var offerConsumed: Boolean?
}
```

#### `KingdomData.kt` — ADD one nullable top-level field

```kotlin
// Alongside: var hexContents: Array<RawHexContent>?  (line ~221), var groups: Array<RawGroup> (~250)
var rewildTrackers: Array<RawRewildTracker>?
```

Rationale: the re-wild timer has nowhere to live on `kingmaker.state` (§2.1), and it is kingdom-scoped bookkeeping, so it belongs on the kingdom flag next to `hexContents`. It is a **side-table**: the authoritative `cleared`/`claimed` booleans stay in `kingmaker.state`; we only track *when* each cleared-unclaimed hex entered that state, and we only flip `cleared` on GM accept.

#### `RawKingdomSettings` (in `KingdomData.kt`) — ADD three GM dials

```kotlin
var threatMigrationEnabled: Boolean?   // master toggle for wandering threats
var threatMigrationSpeed: Int?         // hexes moved per accepted step (default 1)
var rewildDelayTurns: Int?             // turns cleared-unclaimed before re-wild is offered; 0 disables
```

These mirror existing tick dials (`factionStandingDriftPerTurn`, `rpToXpConversionRate`, `autoGainFamePerTurn`) that live on `kingdom.settings`, are edited in `dialogs/KingdomSettings.kt`, and are read into `TurnTickingEngine.tick(...)` from `TurnWizardApplication.kt` (~lines 200-210).

### 2.3 Migration49

Migration chain currently ends at **`Migration61`** (`migrations/Migrations.kt`, `MigrationChainTest` asserts contiguity). Propose **`Migration49`** *(placeholder — not free; see caveat)* (Gregory sequences the real number). Following the `Migration47` template:

> ⚠️ **The number in this section is stale and must be re-derived at implementation.** The chain
> ends at `Migration61`, not 48, and 62–67 are already proposed by the downtime-projects,
> scheduled-pressure, petition-inbox, npc-memory, seasonal-economy and loot-manifests plans. Nine
> unimplemented plans currently name `Migration49`, so it is not free for any of them. Take the
> next contiguous number when this actually lands, and update `MigrationChainTest`.


```kotlin
class Migration49 : Migration(49) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        // War threats: opt-in mobility, start each threat at its declared target.
        kingdom.warThreats = kingdom.warThreats?.map { threat ->
            if (threat.wanders.unsafeCast<Boolean?>() == null) {
                threat.copyWith(
                    wanders = false,                              // preserve current static behavior
                    currentHexLocation = threat.targetHexLocation, // "at target" until it starts wandering
                    migrationConsumedTurn = null,
                )
            } else threat
        }?.toTypedArray()

        // Re-wild side-table starts empty; the first tick reconciles it against live hex state.
        if (kingdom.rewildTrackers.unsafeCast<Array<RawRewildTracker>?>() == null) {
            kingdom.rewildTrackers = emptyArray()
        }

        // Settings dials default to non-destructive values.
        val s = kingdom.settings
        if (s.threatMigrationEnabled.unsafeCast<Boolean?>() == null) s.threatMigrationEnabled = false
        if (s.threatMigrationSpeed.unsafeCast<Int?>() == null) s.threatMigrationSpeed = 1
        if (s.rewildDelayTurns.unsafeCast<Int?>() == null) s.rewildDelayTurns = 6
    }
}
```

**Non-breaking:** every new field is nullable and defaults to today's behavior (threats static, re-wilding off until the GM enables `rewildDelayTurns`). Existing saves load unchanged.

---

## 3. Engine Design

### 3.1 Pure core (commonMain) — no Foundry / no JS interop

Adjacency and hex distance are **Foundry-runtime coupled** — `KingmakerHexGridProvider.getAdjacentHexKeys` reads `kingmaker.region`, and `kingmaker.state.hexes` is the live map. The pure core therefore never touches them directly; it takes **injected closures**, exactly as `roadConnectedToCapital(...)` takes a `neighborsProvider` and `MilestoneOffers.kt:43` injects `provider.getAdjacentHexKeys`. This keeps the logic unit-testable against fakes (the same pattern `WarThreatSnapshot` / `detectNewlyTriggeredThreats` uses to keep war-threat logic in commonMain).

**File:** `src/commonMain/kotlin/.../kingdom/MapDynamism.kt`

```kotlin
package at.posselt.pfrpg2e.kingdom

// ---------- Threat migration ----------

/** Minimal mirror of the mobile fields of RawWarThreat, usable from commonTest. */
data class ThreatMigrationInput(
    val threatId: String,
    val wanders: Boolean,
    val currentHexKey: String?,   // falls back to targetHexKey when null
    val targetHexKey: String?,
    val migrationConsumedTurn: Int?,
)

/** A proposed one-step migration for a threat (NOT applied until GM confirms). */
data class ThreatMigration(
    val threatId: String,
    val fromHex: String,
    val toHex: String,
)

/**
 * Choose the next hex a threat should step to: the adjacent hex minimizing distance to the target.
 * Deterministic tie-break: smallest hex key by integer value, then lexicographically.
 * Returns null when already at target, target unknown, or no neighbor is strictly closer.
 */
fun nextThreatHex(
    currentHexKey: String,
    targetHexKey: String,
    adjacency: (String) -> List<String>,
    distanceToTarget: (String) -> Int?,   // BFS distance over the region, precomputed on the JS side
): String? {
    if (currentHexKey == targetHexKey) return null
    val here = distanceToTarget(currentHexKey) ?: return null
    return adjacency(currentHexKey)
        .mapNotNull { n -> distanceToTarget(n)?.let { d -> n to d } }
        .filter { (_, d) -> d < here }
        .minWithOrNull(compareBy({ it.second }, { it.first.toIntOrNull() ?: Int.MAX_VALUE }, { it.first }))
        ?.first
}

/** Plan one migration step for every mobile threat not already resolved this turn. Pure. */
fun planThreatMigrations(
    threats: List<ThreatMigrationInput>,
    currentTurn: Int,
    adjacency: (String) -> List<String>,
    distanceToTarget: (String) -> Int?,
): List<ThreatMigration> =
    threats.asSequence()
        .filter { it.wanders && it.migrationConsumedTurn != currentTurn }
        .mapNotNull { t ->
            val from = t.currentHexKey ?: t.targetHexKey ?: return@mapNotNull null
            val target = t.targetHexKey ?: return@mapNotNull null
            nextThreatHex(from, target, adjacency, distanceToTarget)
                ?.let { to -> ThreatMigration(t.threatId, from, to) }
        }
        .toList()

// ---------- Re-wild ----------

/** Live cleared/claimed snapshot for one hex (read from kingmaker.state on the JS side). */
data class HexClearState(val hexKey: String, val cleared: Boolean, val claimed: Boolean)

data class RewildTrackerSnapshot(
    val hexKey: String,
    val clearedSinceTurn: Int,
    val offerConsumed: Boolean?,
)

data class RewildReconcileResult(
    val trackers: List<RewildTrackerSnapshot>,  // authoritative next state to persist
    val candidates: List<String>,               // hexKeys whose timer elapsed -> offer re-wild
)

/**
 * Reconcile the re-wild side-table against live hex state for one turn. Pure & deterministic:
 *  - A hex that is cleared && !claimed with no tracker -> start a tracker at [currentTurn].
 *  - A hex that became claimed OR is no longer cleared -> drop its tracker (timer resets).
 *  - A tracked hex whose age (currentTurn - clearedSinceTurn) >= rewildDelayTurns and whose
 *    offer is not yet consumed -> emit as a candidate.
 * rewildDelayTurns <= 0 disables re-wilding (no candidates, trackers still reconciled).
 * Candidates are returned sorted (oldest first, then hexKey) for stable digests.
 */
fun reconcileRewild(
    hexStates: List<HexClearState>,
    trackers: List<RewildTrackerSnapshot>,
    currentTurn: Int,
    rewildDelayTurns: Int,
): RewildReconcileResult
```

Notes on the JS adapter (`jsMain`) that feeds these:
- `adjacency` = `KingmakerHexGridProvider().getAdjacentHexKeys`.
- `distanceToTarget` = BFS over `kingmaker.region` hexes from the target settlement/threat hex, memoized per target for the turn (computed once, wrapped in a closure). This keeps the *core* free of graph traversal while remaining deterministic.
- `hexStates` = every hex in `kingmaker.state.hexes` projected to `HexClearState` (or restricted to the union of existing trackers + currently cleared-unclaimed hexes, to bound the list).
- `RawRewildTracker` ⇄ `RewildTrackerSnapshot` is a trivial field copy (mirrors `RawWarThreat` ⇄ `WarThreatSnapshot`).

### 3.2 Determinism / preview parity invariant

**Invariant:** Given identical inputs — the current `kingmaker.state.hexes` snapshot, the persisted `warThreats` + `rewildTrackers`, `currentTurn`, and the settings dials — `planThreatMigrations` and `reconcileRewild` return **identical results on every call**. There is **no `Math.random()`, no `Date`, no `kotlin.random.Random`**. Migration target selection is a pure `min` with an explicit total-order tie-break; re-wild is a pure timer comparison.

Consequence: the End-Turn **preview** tick and the **commit** tick (`TurnTickingEngine.tick` is invoked for both, preview-safe by contract) yield the same "Map Changes" digest. This matches the existing parity guarantee for faction standing drift and war-pressure modifiers, both already computed inside `tick()`.

> No RNG is needed at all for v1. If future flavor wants jitter (e.g. a threat occasionally holds position), introduce a seeded LCG keyed by `(kingdom.id, currentTurn, threatId)` — pure `Int -> Int`, same recipe as the Faction Agenda plan — so parity is preserved. Out of scope here.

### 3.3 Tick surface (monthly End Turn only — no third tick)

Map dynamism ticks **monthly**, inside `TurnTickingEngine.tick()`, alongside faction drift and war-threat escalation. `DailyTickHooks` / `DailyTickEngine` (weather, companion travel) are **untouched**.

`tick()` gains parameters (all with today's-behavior defaults so existing call sites and tests compile):

```kotlin
threatMigrationEnabled: Boolean = false,
rewildDelayTurns: Int = 0,
rewildHexStates: Array<HexClearStateRaw> = emptyArray(),   // projected kingmaker.state snapshot
rewildTrackers: Array<RawRewildTracker> = emptyArray(),
adjacency: (String) -> List<String> = { emptyList() },
distanceToTarget: (String) -> Int? = { null },
```

`tick()` **computes proposals only** — it must not mutate a threat's `currentHexLocation` nor write `kingmaker.state`, because both are GM-gated map changes (like war-threat *arrival* sets `triggeredTurn` but does not spawn the event). It returns new `TickResult` fields:

```kotlin
val threatMigrations: List<ThreatMigration> = emptyList(),   // proposed one-step moves
val rewildCandidates: List<String> = emptyList(),            // hex keys ready to re-wild
val updatedRewildTrackers: Array<RawRewildTracker> = emptyArray(),  // reconciled timers to persist
```

- `updatedRewildTrackers` is applied to `kingdom.rewildTrackers` immediately at commit (pure bookkeeping, not a map change — no confirmation needed; starting/dropping/aging a timer writes nothing to the shared map).
- `threatMigrations` and `rewildCandidates` are **proposals** surfaced as offers; the actual threat-position and `cleared` writes happen only on button click.
- When `threatMigrationEnabled == false`, `planThreatMigrations` is skipped (empty). When `rewildDelayTurns <= 0`, trackers are still reconciled (so timers stay honest if re-enabled) but no candidates are emitted.

`TurnWizardApplication.performEndTurn` sources the new dials from `kingdom.settings`, builds the adjacency/distance/hexstate closures from the live module (guarded with `runCatching` + `game.modules.get("pf2e-kingmaker")?.active` like the annexation path), passes them into `tick()`, persists `updatedRewildTrackers`, and posts the digest card (§5).

---

## 4. UI

### 4.1 Per-turn "Map Changes" digest card (primary surface)

A single GM-whispered chat card per End Turn, posted from `performEndTurn` right after the war-threat-arrival block (`TurnWizardApplication.kt` ~line 588), only when `threatMigrations.isNotEmpty() || rewildCandidates.isNotEmpty()`. Follows the `war-threat-arrival-offer.hbs` structure (GM-only whisper computed on the posting client via `game.users.filter { it.isGM }`).

**Template:** `src/jsMain/resources/chatmessages/map-changes-offer.hbs`

```hbs
<h2>{{localizeKM "chatMessages.mapChanges.title"}}</h2>
{{#if migrations.length}}
<h3>{{localizeKM "chatMessages.mapChanges.threatsHeading"}}</h3>
<ul class="km-map-changes">
  {{#each migrations}}
  <li>
    {{localizeKM "chatMessages.mapChanges.threatCreeps" name=this.threatName fromHex=this.fromHex toHex=this.toHex}}
    <button type="button" class="km-offer-threat-migration"
            data-kingdom-actor-uuid="{{../actorUuid}}"
            data-threat-id="{{this.threatId}}" data-to-hex="{{this.toHex}}" data-action="advance">
      {{localizeKM "chatMessages.mapChanges.advance"}}
    </button>
    <button type="button" class="km-offer-threat-migration"
            data-kingdom-actor-uuid="{{../actorUuid}}"
            data-threat-id="{{this.threatId}}" data-action="hold">
      {{localizeKM "chatMessages.mapChanges.hold"}}
    </button>
  </li>
  {{/each}}
</ul>
{{/if}}
{{#if rewilds.length}}
<h3>{{localizeKM "chatMessages.mapChanges.rewildHeading"}}</h3>
<ul class="km-map-changes">
  {{#each rewilds}}
  <li>
    {{localizeKM "chatMessages.mapChanges.hexRewilds" hexKey=this.hexKey}}
    <button type="button" class="km-offer-hex-rewild"
            data-kingdom-actor-uuid="{{../actorUuid}}"
            data-hex-key="{{this.hexKey}}" data-action="rewild">
      {{localizeKM "chatMessages.mapChanges.rewild"}}
    </button>
    <button type="button" class="km-offer-hex-rewild"
            data-kingdom-actor-uuid="{{../actorUuid}}"
            data-hex-key="{{this.hexKey}}" data-action="keep">
      {{localizeKM "chatMessages.mapChanges.keepCleared"}}
    </button>
  </li>
  {{/each}}
</ul>
{{/if}}
```

**Context object** (built in `performEndTurn`, plain `js("{}")` like the arrival offer):

```kotlin
@JsPlainObject
external interface MapChangesOfferContext {
    var actorUuid: String
    var migrations: Array<MapChangeMigrationRow>   // threatId, threatName, fromHex, toHex
    var rewilds: Array<MapChangeRewildRow>         // hexKey
}
```

`threatName` is resolved from `kingdom.warThreats.find { it.id == threatId }?.name`; `fromHex`/`toHex` from the `ThreatMigration`.

### 4.2 Sheet surface — Army Pressure section (read-only status)

No new nav entry. The **Army & War Pressure** section (`ArmyPressureView.kt` / `ArmyPressureContext.kt`, which already renders `targetHexLocation`) gains a "wanders" badge and current-hex readout per threat. The **Settings** dialog (`dialogs/KingdomSettings.kt`) gains the three dials (§2.2) next to `autoGainFamePerTurn` / `rpToXpConversionRate`. The `AddWarThreat.kt` dialog (schema already declares `targetHexLocation`) gains a `wanders` checkbox and, when set, seeds `currentHexLocation = targetHexLocation`.

### 4.3 i18n namespace

All keys nested under `pf2e-kingmaker-tools` in `lang/en.json` (nested objects, never flat-dotted — `scripts/check_i18n_keys.py` guard), wired via `initLocalization()`:

```json
"chatMessages": {
  "mapChanges": {
    "title": "Map Changes",
    "threatsHeading": "Wandering Threats",
    "threatCreeps": "{{name}} creeps from hex {{fromHex}} toward hex {{toHex}}.",
    "advance": "Advance", "hold": "Hold",
    "rewildHeading": "Re-Wilding Hexes",
    "hexRewilds": "Hex {{hexKey}} was cleared but never claimed — the wilds are returning.",
    "rewild": "Let it re-wild", "keepCleared": "Keep cleared",
    "advanced": "{{name}} advanced to hex {{toHex}}.",
    "held": "{{name}} holds position.",
    "rewilded": "Hex {{hexKey}} has re-wilded; encounters may return.",
    "kept": "Hex {{hexKey}} stays cleared."
  }
},
"kingdom": {
  "settings": {
    "threatMigrationEnabled": "Threats wander toward settlements",
    "threatMigrationSpeed": "Threat migration speed (hexes/turn)",
    "rewildDelayTurns": "Turns before cleared-unclaimed hexes re-wild (0 = never)"
  },
  "turnGazette": {
    "threatMigrated": "{{name}} advanced to hex {{toHex}}.",
    "hexRewilded": "The wilds reclaimed hex {{hexKey}}."
  }
}
```

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

Two new `ChatButton` handlers in `kingdom/ChatButtons.kt`, both gated `if (!game.user.isGM) return@ChatButton` (players cannot write `kingmaker.state` or move markers). Each mirrors `km-offer-war-threat-arrival` (idempotent, resolves via `actor.getKingdom()` → mutate → `actor.setKingdom` → gazette line).

### 5.1 `km-offer-threat-migration` — Advance / Hold

| Action | Handler behavior |
|--------|------------------|
| `advance` | Look up threat by `data-threat-id`; guard `migrationConsumedTurn == currentTurn` (already resolved). Set `currentHexLocation = data-to-hex`, `migrationConsumedTurn = currentTurn`. `actor.setKingdom(kingdom)`. Re-sync the threat's map marker via the hex-content/war-threat sync (GM-only, `HexContentSync`/`HexGridSync`). Post public gazette line `chatMessages.mapChanges.advanced`. |
| `hold` | Set `migrationConsumedTurn = currentTurn` (suppress *this* turn; it re-proposes next turn from the same position). Post `chatMessages.mapChanges.held`. No map write. |

`migrationConsumedTurn` is intentionally **per-turn** (not a permanent `offerConsumed`): a held threat is re-offered next turn, so momentum resumes unless the GM disables `wanders`.

### 5.2 `km-offer-hex-rewild` — Let it re-wild / Keep cleared

| Action | Handler behavior |
|--------|------------------|
| `rewild` | Re-confirm the hex is still `cleared && !claimed` (a mid-turn claim voids the offer). Write `kingmaker.state` GM-only: `state.updateSource({hexes:{[hexKey]:{cleared:false}}})` + `state.save()` — the exact pattern in `KingdomSheet.kt:1252-1263`, wrapped in `runCatching` + `game.modules.get("pf2e-kingmaker")?.active`. Optionally clear any `RawHexContent.suppressesEncounters` for that hex so `RandomEncounters.kt` stops filtering combat. Mark the tracker `offerConsumed = true`. Post public gazette `chatMessages.mapChanges.rewilded`. |
| `keep` | Mark the tracker `offerConsumed = true` (suppress re-offer) without touching the map. Post `chatMessages.mapChanges.kept`. |

Because the reconcile step (§3.1) drops a tracker whenever the hex is later claimed or re-cleared, a `keep`-then-later-cleared-again hex naturally starts a fresh timer. A re-wilded hex (`cleared` flipped to false) also drops out of the cleared-unclaimed set, so it will only re-enter the pipeline if the party clears it again.

### 5.3 Interaction with the existing war-threat arrival offer

Migration and arrival are **orthogonal and both gated**: `tickWarThreats` still escalates ETA and (on max escalation) sets `triggeredTurn`, producing the existing `war-threat-arrival-offer` card. A wandering threat that *reaches its target settlement hex* (`nextThreatHex` returns null because `currentHexLocation == targetHexLocation`) stops migrating and its normal escalation/arrival flow takes over — i.e. reaching the settlement feeds straight into the war-threat-arrival → siege/kingdom-event pipeline. The migration digest never double-fires the arrival card; they are distinct `km-offer-*` classes with distinct idempotency guards (`migrationConsumedTurn` vs `offerConsumed`).

---

## 6. Interactions With Existing Systems + Out-of-Scope

| System | File(s) | Interaction |
|--------|---------|-------------|
| **War threats / escalation** | `kingdom/ArmyWarPressure.kt` (`tickWarThreats`), `data/RawWarThreat.kt`, `WarThreat.kt` (`detectNewlyTriggeredThreats`) | Migration reads/writes the same `warThreats` array; escalation and arrival detection are unchanged. A threat that reaches its target settlement hex hands off to the arrival flow. |
| **Siege / war-pressure board** | `TurnTickingEngine.kt` (`recalculateWarPressure`), `ArmyPressureView.kt`, `ArmyBattleView.kt` | A threat arriving via migration is the same `RawWarThreat` the pressure board already tracks; no new pressure math. Board shows the new "wanders"/current-hex readout. |
| **Native realm map** | `com/foundryvtt/kingmaker/KingmakerModule.kt` (`kingmaker.state`), `KingdomSheet.kt:1252-1263` (write pattern) | Re-wild is the **only** writer of `cleared`, via the established GM-only `updateSource + save`. Reads project `HexState` into `HexClearState`. |
| **Hex adjacency / grid** | `map/KingmakerHexGridProvider.kt` (`getAdjacentHexKeys`), `MilestoneOffers.kt` (injection precedent) | Provides the injected `adjacency` closure and the BFS basis for `distanceToTarget`. Foundry-coupled → never called from commonMain. |
| **Hex content & markers** | `map/HexContentSync.kt`, `map/HexGridSync.kt`, `data/RawHexContent.kt` | Marker re-sync after an accepted migration; re-wild may clear a hex's `suppressesEncounters`. All sync stays GM-gated (`game.user.isGM`, memory lesson on embedded-doc writes). |
| **Camping random encounters** | `camping/RandomEncounters.kt` | Consumer of `cleared`: once re-wild flips `cleared` to false, combat encounters return there automatically — no change needed in that file. |
| **Turn ticking** | `TurnTickingEngine.kt`, `dialogs/TurnWizardApplication.kt` (`performEndTurn`) | Monthly host for both engines; preview/commit parity preserved. |
| **Gazette / session prep** | `TurnHistory.kt` / `SessionPrepView.kt` | Accepted migrations and re-wilds emit public gazette lines. |
| **Daily tick** | `DailyTickHooks.kt`, `DailyTickEngine.kt` | **No interaction** — map dynamism is monthly only. |

### 6.1 Explicit OUT-OF-SCOPE

- **No pathfinding animation / token movement.** Migration moves a *marker/flag*, not a canvas token, one hex per accepted step.
- **No auto-apply of any map change.** Every migration and re-wild is a GM click; nothing writes `kingmaker.state` or moves markers on its own.
- **No re-wild of *claimed* hexes.** Only `cleared && !claimed`. Claimed land never re-wilds (respects "no encounters in claimed hexes").
- **No new encounter tables / content generation.** Re-wild only lapses the `cleared` flag; what encounters return is whatever the region table already produces.
- **No threat pathing around impassable terrain / rivers.** v1 uses region-graph BFS distance; terrain cost is a later refinement (same deferral as `KingmakerHexGridProvider` distance-only routing).
- **No player-initiated map dynamism** (players cannot trigger or veto; GM-only by construction).
- **No seeded RNG** (deterministic timers/min-distance only; see §3.2).

---

## 7. Test Plan

### 7.1 commonTest (pure logic, JVM-less) — `MapDynamismTest.kt`

| Test | Description |
|------|-------------|
| `nextThreatHex_stepsTowardTarget` | On a small fake grid, returns the neighbor with strictly-smaller distance-to-target. |
| `nextThreatHex_tieBreakDeterministic` | Two equidistant neighbors → the smaller integer hex key is chosen, every run. |
| `nextThreatHex_nullAtTargetOrNoCloserNeighbor` | Returns null when `current == target` and when no neighbor is closer (cul-de-sac). |
| `planThreatMigrations_onlyMobileUnresolved` | Static threats and threats with `migrationConsumedTurn == currentTurn` are excluded. |
| `planThreatMigrations_fallsBackToTargetWhenNoCurrent` | `currentHexKey == null` uses `targetHexKey` as origin. |
| `reconcileRewild_startsTimerForClearedUnclaimed` | New cleared-unclaimed hex → tracker at `currentTurn`, no candidate yet. |
| `reconcileRewild_dropsTrackerWhenClaimedOrRecleared` | Claiming or un-clearing a tracked hex removes its tracker (timer reset). |
| `reconcileRewild_emitsCandidateAtDelay` | Age `>= rewildDelayTurns` and not consumed → candidate; `< delay` → none. |
| `reconcileRewild_delayZeroDisables` | `rewildDelayTurns <= 0` → zero candidates, trackers still reconciled. |
| `reconcileRewild_respectsOfferConsumed` | A consumed tracker is never re-emitted as a candidate. |
| `bothEngines_deterministicSameInputs` | Same inputs → identical `List<ThreatMigration>` / `RewildReconcileResult` across repeated calls (parity invariant). |

### 7.2 jsTest (Foundry-integrated)

**File:** `src/jsTest/kotlin/.../kingdom/MapDynamismEngineTest.kt` + additions to `TurnTickingEngineTest.kt`

| Test | Description |
|------|-------------|
| `tick_producesMigrationsAndRewildCandidates` | `tick()` with mobile threats + aged trackers surfaces `threatMigrations` / `rewildCandidates` in `TickResult`. |
| `tick_previewCommitParity` | Preview tick and commit tick yield identical proposals + `updatedRewildTrackers`. |
| `tick_migrationDisabledYieldsNoMigrations` | `threatMigrationEnabled = false` → empty migrations, threats untouched. |
| `tick_trackersReconciledButNoCandidatesWhenDelayZero` | Timers reconcile while `rewildDelayTurns = 0` emits no candidates. |
| `rawRewildTracker_roundTrip` | `RawRewildTracker` ⇄ `RewildTrackerSnapshot` fidelity. |
| `migration49_backfillsFields` | Migration49 sets `wanders=false`, `currentHexLocation=targetHexLocation`, empty `rewildTrackers`, default dials (extend `MigrationBackfillsTest.kt`). |

### 7.3 Manual Foundry verification checklist

1. Enable `pf2e-kingmaker` + hex map. In Kingdom Settings set `rewildDelayTurns = 2`, `threatMigrationEnabled = on`.
2. Add a war threat, tick its `wanders` box, target a settlement several hexes away.
3. **End Turn** → GM-whispered "Map Changes" card shows the threat creeping toward the settlement with **Advance / Hold**. Players see no card.
4. Click **Advance** → threat marker moves one hex closer; gazette line appears; re-running End Turn proposes the next step.
5. Click **Hold** on a later turn → threat stays; next turn it re-proposes from the same hex.
6. Let a wandering threat reach its target hex → migration offer stops; normal escalation/arrival offer takes over.
7. In the field, **clear** a hex (kill its monster) but do **not** claim it. Confirm no immediate change.
8. End Turn twice (delay = 2) → "Re-Wilding Hexes" section offers that hex with **Let it re-wild / Keep cleared**.
9. Click **Let it re-wild** → `kingmaker.state` hex `cleared` flips to false (verify via a camping encounter roll there now producing combat again); gazette line appears.
10. Clear another hex, then **claim** it before the delay elapses → it never appears in the digest (tracker dropped).
11. Log in as a **player** (non-GM): the Map Changes card is absent; no way to trigger a re-wild or migration.
12. Reload the world → `rewildTrackers`, threat `currentHexLocation`, and dials persist.
13. Confirm End-Turn **preview** and the committed result list the same map changes (parity).

Build/verify per `AGENTS.md`: `python3 scripts/check_i18n_keys.py`, then `JAVA_HOME=<jdk25> ./gradlew assemble jsTest -x kotlinStoreYarnLock` (Chrome headless).

---

## 8. Phasing (Independently Committable)

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Data model + Migration49** | `wanders`/`currentHexLocation`/`migrationConsumedTurn` on `RawWarThreat`; new `RawRewildTracker`; `KingdomData.rewildTrackers`; three `RawKingdomSettings` dials; `Migration49` + backfill test. | `data/RawWarThreat.kt`, `data/RawRewildTracker.kt`, `KingdomData.kt`, `migrations/migrations/Migration49.kt`, `migrations/Migrations.kt`, `MigrationBackfillsTest.kt` |
| **2** | **Pure engine (commonMain)** | `MapDynamism.kt` (`nextThreatHex`, `planThreatMigrations`, `reconcileRewild` + data classes) with full `MapDynamismTest.kt`. No Foundry deps. | `commonMain/.../kingdom/MapDynamism.kt`, `commonTest/.../kingdom/MapDynamismTest.kt` |
| **3** | **TurnTickingEngine integration** | New `tick()` params + `TickResult` fields; wire `planThreatMigrations`/`reconcileRewild` in; source dials + build adjacency/distance/hexstate closures in `performEndTurn`; persist `updatedRewildTrackers`. jsTest parity. | `TurnTickingEngine.kt`, `dialogs/TurnWizardApplication.kt`, `map/KingmakerHexGridProvider.kt` (distance BFS helper), `TurnTickingEngineTest.kt`, `MapDynamismEngineTest.kt` |
| **4** | **Offer surfaces + map writes** | `map-changes-offer.hbs`; `km-offer-threat-migration` + `km-offer-hex-rewild` handlers (GM-gated, `updateSource+save` re-wild, marker re-sync); gazette lines; digest posting in `performEndTurn`; i18n keys. | `resources/chatmessages/map-changes-offer.hbs`, `ChatButtons.kt`, `map/HexContentSync.kt`/`HexGridSync.kt`, `TurnHistory.kt`, `lang/en.json` |
| **5** | **Sheet/dialog UI + QA** | Settings dials in `KingdomSettings.kt`; `wanders` checkbox in `AddWarThreat.kt`; wanders/current-hex readout in Army Pressure view; full manual checklist. | `dialogs/KingdomSettings.kt`, `dialogs/AddWarThreat.kt`, `ArmyPressureView.kt`/`ArmyPressureContext.kt`, `lang/en.json` |

**Dependencies:** 1 → 2 (parallelizable data/logic), 3 depends on 2, 4 depends on 3, 5 depends on 4. Phases 1–2 can run concurrently.

---

## Open Questions for Gregory

1. **Migration cadence vs. speed dial.** Default is one hex per *accepted* step. Should `threatMigrationSpeed > 1` propose a multi-hex jump in one offer, or just make holds cost more ground? (Plan proposes one offer per step regardless of speed for clarity.)
2. **Re-wild aggressiveness.** Should re-wilding also lapse `explored` (fully wild) or only `cleared` (encounters return, still on the map)? Plan does `cleared` only.
3. **Default `rewildDelayTurns`.** 6 turns (~half a year) as a gentle default, or shorter to make the pressure bite sooner?
4. **Held threats.** Re-offer every turn (plan) vs. a permanent per-threat "paused" state the GM sets once?
5. **Gazette visibility.** Should the *pre-acceptance* creep also be hinted to players (fog-of-war "something stirs to the west"), or stay GM-only until accepted (plan: GM-only offer, public line on accept)?

---

**End of Plan.** Ready for review. Upon approval, implementation cards will be created per the phasing table above.

# Rival Charter Party — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** Living-world follow-up to Faction & Diplomacy Relations Tracker (#1); adversarial sibling of Companion Expeditions and the Rival Realms Scoreboard
> **Depends on:** Faction & Diplomacy Relations Tracker (#1, phases 1–4 landed), Army & War Pressure Board (#12), Turn History gazette, the native pf2e-kingmaker hex model (`kingmaker.state` / `kingmaker.region`)
> **Siblings (delineated in §6):**
> - `docs/plans/2026-07-09-plan-rival-realms.md` — *Rival Realms Scoreboard* (a rival grows as a **kingdom**; abstract size/fame/army numbers, **no map position**)
> - `docs/plans/2026-06-24-companion-expeditions.md` — *Companion Expeditions* (**your own** off-screen parties, daily tick, grant XP to your companions)
> - `docs/plans/2026-07-09-plan-faction-agenda.md` — *Faction Agenda Engine* (factions act on each other)
> **Branch:** `kingmaker.5`

---

## Executive Summary

Kingmaker is a race for the Stolen Lands. The fiction repeatedly asserts that the PCs are **not the only** chartered party out there — Pitax, Restov, and rival lords all send their own explorers to claim the same hexes, clear the same lairs, and grab the same landmarks (`docs/house-rules.md`: "There is no way that a call for adventurers has gone unnoticed"). Today the module models none of that. Rivals are inert trade counters; nobody is out on the map competing for the ground the players want.

This feature adds a **Rival Charter Party**: a *competing adventuring band* (e.g. chartered by Pitax) that explores the hex map **off-screen on its own clock**. Each kingdom turn (monthly, End Turn) the band **advances deterministically toward the nearest valuable target** — an unexplored hex, an uncleared lair, a contested claim, a named landmark — and occasionally **beats the players there**. When it does, that "they got there first" moment surfaces as a **GM-confirmed offer** with real choices (cede it, race them, confront them, or narrate only). If the band's **aggression** climbs high enough — because the PCs keep contesting it, or it keeps prowling near their territory — it can **escalate into a war threat or a spawned encounter**, reusing the existing Army & War Pressure plumbing.

**Be honest about what this is:** a **scoreboard with teeth**, not a simulated party. There is **no hidden combat resolver, no rival inventory, no A\* pathfinding, no simulated rolls**. The band is a token that walks toward a target at a GM-dialed pace, and a headline generator with two offer buttons. Movement is a pure function of state + turn (cube-distance step-down + a deterministic argmin objective pick), so preview/commit parity is automatic and a GM can predict exactly where the band will be in N turns. The point is to give exploration **urgency** and the occasional **sting of losing the race** — cheaply, and always under GM control.

**Crucially: the rival never silently writes the map.** Reaching a hex first is an *offer + a gazette line*, never a covert `kingmaker.state` mutation. The players' map is the players' map; the rival only ever *proposes* consequences.

---

## 1. Problem Statement + Player/GM Value

**Problem:** Exploration in Kingmaker has no external competitor. The house rules ask the GM to invent off-screen pressure by hand — "Brevoy might claim the Rostland Hinterlands," "Pitax should recruit additional armies," agents from rival powers foreshadowed but never *acting*. There is nothing on the map that makes the players feel they must claim **that** hex **now** before someone else does. The Rival Realms Scoreboard (sibling plan) makes an abstract number creep upward; it does not put a rival *on the ground* racing for a specific prize.

**Value to the table:**

- **Exploration urgency (players).** "The Pitax Chartists are 2 hexes from the Temple of the Elk and will reach it next turn" is a concrete, visible reason to move — a rival with agency, not a lecture.
- **The occasional honest sting (players).** Sometimes the band gets there first. Losing a race the players could have won (had they prioritized differently) is exactly the pacing pressure the AP wants, and it lands as *fiction the GM chose to accept*, never a silent rug-pull.
- **Zero-improv off-screen rival (GM).** The GM dials a pace and an aggression threshold once; each End Turn the band advances toward the nearest prize and emits a headline the GM can read aloud. No tracking on paper.
- **A bridge to warfare that isn't automatic.** A band the players keep contesting escalates — at the GM's click — into a `RawWarThreat` on the Army & War Pressure board, or a spawned encounter. The scoreboard grows teeth without ever simulating a rival battle.
- **Gazette fuel.** Every turn the band moves, one public headline lands in the "Recent Turns" recap and the session-prep journal export — a living chronicle of the competition, for free.

**Deliberately NOT solved here:** an actual rival party with a real statblock, inventory, and simulated encounters. If the GM wants to *run* the confrontation, this feature hands them a prefilled war-threat/encounter offer and gets out of the way.

---

## 2. Data Model

### 2.1 New external interface (`@JsPlainObject`, nullable fields for migration safety)

`RawRivalCharterParty.kt` lives in `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/data/` (alongside `RawGroup.kt` / `RawWarThreat.kt` / `RawCompanionExpedition.kt`; `@JsPlainObject` interfaces are JS-only and cannot live in commonMain). All fields follow the codebase convention: external interface, auto `.copy`, **nullable for migration safety**.

```kotlin
package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * A competing adventuring band exploring the map off-screen (roadmap: living world).
 * The adversarial mirror of RawCompanionExpedition: it moves on the MONTHLY kingdom
 * tick (not the daily world clock), it belongs to a RIVAL faction (not the PCs), and it
 * competes over the SAME hex map the players explore. It has a real map POSITION and an
 * OBJECTIVE it walks toward — unlike a RawRivalRealm, which is an abstract scoreboard.
 *
 * Honesty contract: movement is deterministic (cube-distance step-down); there is no
 * simulated combat, no inventory, no pathfinding. Reaching a target is a GM-confirmed
 * OFFER + a gazette line, never a silent kingmaker.state write.
 */
@JsPlainObject
external interface RawRivalCharterParty {
    /** Stable id (UUID) so UI edits + offers target the right band. */
    var id: String

    /** Soft foreign key to RawGroup.name — the faction that chartered this band
     *  (e.g. "Pitax"). Same by-name link the caravan/war-threat systems use. Nullable:
     *  an unaffiliated band ("freebooters") is legal and shows an "unlinked" hint. */
    var factionRef: String?

    /** The band's own name for UI/gazette ("The Pitax Chartists"). */
    var name: String

    // --- Flavor (never mechanically simulated) -------------------------------
    /** Free-text roster blurb ("Ganderel and four sellswords"). Display only. */
    var members: String?
    /** Nominal party level — used ONLY to budget a confrontation encounter offer. */
    var level: Int?

    // --- Map position + objective (the whole point) --------------------------
    /** Current hex key (string key into kingmaker.state.hexes). null => off-map / not yet placed. */
    var currentHexKey: String?
    /** The hex the band is currently walking toward. null => idle / recompute next tick. */
    var objectiveHexKey: String?
    /** Why this hex is valuable: "unexplored" | "uncleared-lair" | "contested-claim" | "landmark".
     *  Drives the headline pool and the got-there-first offer copy. */
    var objectiveKind: String?
    /** Cached cube distance from currentHexKey to objectiveHexKey at last recompute.
     *  Deterministic; recomputed whenever the objective changes. */
    var distanceToObjective: Int?

    // --- GM movement dials (nullable => defaults) ----------------------------
    /** Hexes advanced toward the objective per kingdom turn. null/absent => 1. GM pace dial. */
    var pace: Int?
    /** GM kill-switch: true => band does not move this turn. */
    var pauseMovement: Boolean?

    // --- Aggression / confrontation escalation -------------------------------
    /** 0..threshold escalation clock. Rises when the band contests the players
     *  (arrives at a target the PCs wanted, or prowls near claimed territory). */
    var aggression: Int?
    /** When aggression >= this, a confrontation OFFER fires. null => confrontation disabled. */
    var aggressionThreshold: Int?
    /** Idempotency: true once a confrontation war-threat/encounter offer has been
     *  posted for the current aggression peak; reset when aggression is spent. */
    var confrontationOffered: Boolean?

    // --- Scoreboard + idempotency guards -------------------------------------
    /** How many targets this band has beaten the players to (the "they got there first" tally). */
    var arrivals: Int?
    /** Hex key of the last target the band reached, so the got-there-first offer fires once. */
    var lastArrivalHexKey: String?
    /** Turn the last arrival offer fired (idempotency across preview/commit + reloads). */
    var lastArrivalTurn: Int?

    // --- Presentation --------------------------------------------------------
    /** Optional override for the headline template pool; null => derive from objectiveKind. */
    var headlinePool: String?
    /** Player-board visibility (house rule). Nullable for migration safety; null/true = visible. */
    var visibleToPlayers: Boolean?
}
```

Field list (19): `id`, `factionRef`, `name`, `members`, `level`, `currentHexKey`, `objectiveHexKey`, `objectiveKind`, `distanceToObjective`, `pace`, `pauseMovement`, `aggression`, `aggressionThreshold`, `confrontationOffered`, `arrivals`, `lastArrivalHexKey`, `lastArrivalTurn`, `headlinePool`, `visibleToPlayers`.

### 2.2 Persistence location — top-level `KingdomData.rivalCharterParties`, NOT nested on `RawGroup`

**Decision:** a new top-level `KingdomData.rivalCharterParties: Array<RawRivalCharterParty>?`, each row carrying `factionRef → RawGroup.name`.

**Why a top-level array, and why the soft-FK-by-name link** (mirroring the sibling rivals plan and the established codebase pattern):

- A kingdom has *many* groups (Sootscale, every Brevoy house, minor trade partners) but only **1–3** are out on the map as charter parties. Nesting a `charterParty` block on every group would bloat the hot-path trade-partner record (`RawGroup` is read on trade activities, caravan routing, negotiation DCs).
- A top-level nullable array is exactly how `warThreats?` (`KingdomData.kt:273`), `armyDeployments?` (`:274`), and `companionExpeditions?` (`:301`) already live — soft-FK-by-name is the established contract (`RawWarThreat.enemyFaction`, caravan partner by name, `RawCompanionExpedition.companionIds`). A charter party is *closest in shape* to a `RawCompanionExpedition` (an off-screen party record) but adversarial and monthly.
- The band is **not a group** — it is a mobile band *chartered by* a group. A `RawGroup` is a static polity; conflating the two would break the trade/diplomacy model.

```kotlin
// KingdomData.kt — ADD after companionExpeditions (~L301), before the closing brace.
// Nullable => null means "not yet migrated"; empty => no bands.
var rivalCharterParties: Array<RawRivalCharterParty>?
```

`Defaults.kt` `createKingdomDefaults()` seeds `rivalCharterParties = emptyArray()` (beside `companionExpeditions = emptyArray()`).

The link is a **soft foreign key**: `factionRef` is a `RawGroup.name`. If a GM renames/deletes the group, the band survives, shows an "unlinked faction" hint, and its `atWar` lookup falls back to neutral — the same forgiving contract as caravans/war-threats. No cascade.

### 2.3 Migration — `Migration49`

The migration chain currently ends at **`Migration61`** (`src/jsMain/kotlin/at/posselt/pfrpg2e/migrations/Migrations.kt`; imports and the `migrations = listOf(...)` both terminate at `Migration48()`; `MigrationChainTest` asserts contiguity, `latestMigrationVersion = migrations.maxOfOrNull { it.version }!!`). Propose **`Migration49`** *(placeholder — not free; see caveat)*. **Gregory sequences the real number at implementation** — note the sibling *Rival Realms* plan also claims `49`; if both land, whichever ships second takes `50`.

> ⚠️ **The number in this section is stale and must be re-derived at implementation.** The chain
> ends at `Migration61`, not 48, and 62–67 are already proposed by the downtime-projects,
> scheduled-pressure, petition-inbox, npc-memory, seasonal-economy and loot-manifests plans. Nine
> unimplemented plans currently name `Migration49`, so it is not free for any of them. Take the
> next contiguous number when this actually lands, and update `MigrationChainTest`.


```kotlin
class Migration49 : Migration(49) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.rivalCharterParties == null) {
            kingdom.rivalCharterParties = emptyArray<Any>()
        }
    }
}
```

- **Non-breaking:** `rivalCharterParties` null/absent → no bands; the status section shows only an empty hint. Existing saves load unchanged.
- **No backfill:** bands are opt-in; the GM adds one via the dialog (with a "Pitax Chartists" seed template). Migrations are effectively one-shot/irreversible (single auto-backup) — keep this one a pure null-guard.
- Register `Migration49()` in the `migrations` list **and** add the import in `Migrations.kt`; `latestMigrationVersion` recomputes from the list.

---

## 3. Engine Design

Two layers, matching the real codebase split (`FactionRelations.kt` pure-primitive math in commonMain + a jsMain `RawGroup` adapter; and the pure `EncounterResolverEngine` vs. its impure wrapper):

- **Pure core (commonMain, `data/kingdom/RivalCharterParty.kt`)** — operates on **primitives and plain data classes only** (no `RawRivalCharterParty`, no `kingmaker.*`, no Foundry). Fully unit-testable in commonTest.
- **jsMain adapter (`kingdom/RivalCharterPartyEngine.kt`)** — thin glue: reads the live map via a **defensive snapshot builder**, reads/rebuilds `RawRivalCharterParty` via auto `.copy`, calls the pure core. Tested in jsTest.

### 3.1 The map snapshot — the one impure read, taken once

The pure core cannot touch `kingmaker.state`. So the jsMain adapter builds a **plain-data snapshot** of the map's competitive surface (the exact reads `ExpeditionDestinations.buildExpeditionDestinationOptions` already performs — `kingmaker.state.hexes` for `claimed`/`explored`, `kingmaker.region.hexes` for `.cube` and full-map coverage, all wrapped in `runCatching`):

```kotlin
// commonMain — plain, serializable, no Foundry.
data class HexCube(val q: Int, val r: Int, val s: Int)

/** One candidate target the rival could walk toward. */
data class RivalTarget(
    val hexKey: String,
    val cube: HexCube,
    val kind: String,       // "unexplored" | "uncleared-lair" | "contested-claim" | "landmark"
    val value: Int,         // GM-tunable weight per kind (landmark > lair > contested > unexplored)
    val label: String,      // pre-resolved hex label for headlines ("Temple of the Elk (3.7)")
)

/** Everything the pure movement core needs about the map this turn. Built once per tick. */
data class RivalMapSnapshot(
    val targetsByKey: Map<String, RivalTarget>,   // all candidate prizes still available
    val claimedKeys: Set<String>,                 // player-claimed hexes (for near-territory aggression)
    val cubeByKey: Map<String, HexCube>,          // full map for distance math
)
```

The jsMain builder `buildRivalMapSnapshot(kingdom): RivalMapSnapshot`:
- **unexplored** = `kingmaker.region.hexes.contents` minus everything in `kingmaker.state.hexes` with `explored == true` (the `uncharted` set `ExpeditionDestinations` already computes).
- **uncleared-lair** = hexes whose `kingmaker.region` content / `RawHexContent` marks a lair/encounter not yet cleared (read the same content model `getContentForHex` exposes; treat "has content, not claimed" as a lair prize).
- **contested-claim** = explored-but-unclaimed frontier (`claimed != true && explored == true`) — the ground both sides can still take.
- **landmark** = named region hexes flagged as landmarks/refuges (region `.name` + content type), the highest-value prizes.
- `value` per kind comes from `data/rival-charter-values.json` (GM-tunable), default `landmark 40 / uncleared-lair 30 / contested-claim 20 / unexplored 10`.
- Every access is `runCatching`-guarded; a missing pf2e-kingmaker module yields an **empty snapshot** (the band simply idles — honest degradation).

### 3.2 Pure core — concrete signatures

```kotlin
package at.posselt.pfrpg2e.data.kingdom

/** Cube distance between two hexes (reuses companion.hexCubeDistance; both are pure). */
fun hexDistance(a: HexCube, b: HexCube): Int

/** Plain movement state for ONE band (extracted from a RawRivalCharterParty by the adapter). */
data class RivalPartyState(
    val currentKey: String?,
    val currentCube: HexCube?,
    val objectiveKey: String?,
    val pace: Int,
    val aggression: Int,
    val aggressionThreshold: Int?,
)

/** The band's chosen target for this turn (nearest, highest-value, deterministic tiebreak). */
data class ObjectiveChoice(val target: RivalTarget, val distance: Int)

/**
 * Pick the objective: the target minimizing (distance, then -value, then hexKey) from the
 * band's current position. FULLY DETERMINISTIC — a stable argmin, no RNG. Returns null when
 * the band has no position or no candidate targets remain (idle).
 */
fun chooseObjective(currentCube: HexCube?, candidates: Collection<RivalTarget>): ObjectiveChoice?

/** What one turn's advance produced. */
data class RivalMove(
    val newState: RivalPartyState,       // updated position, objective, aggression
    val arrivedAt: RivalTarget?,         // non-null => reached a prize this turn (=> got-there-first offer)
    val movedFrom: String?,              // for the "advanced toward X" headline
    val headlineKind: String,            // keys the headline pool ("advance" | "arrive" | "idle")
    val confrontation: Boolean,          // aggression crossed threshold this turn (=> confrontation offer)
)

/**
 * Advance ONE band one kingdom turn against [snapshot]. Deterministic, no RNG:
 *   1. If no objective (or objective no longer a candidate — the PCs took it first), chooseObjective.
 *   2. Step the band `pace` hexes toward the objective cube (reduce distance by pace, min 0).
 *      Position snaps to the objective hex on the arriving turn (we do not model intermediate
 *      hexes — honest: the band is a scoreboard token, not a path).
 *   3. If distance reaches 0: arrivedAt = objective; aggression += arrivalAggression(kind);
 *      clear objective so a new one is chosen next turn.
 *   4. If the band sits within N hexes of a claimed hex: aggression += proximityAggression.
 *   5. confrontation = (threshold != null && before < threshold && after >= threshold).
 * `paused` => no movement, no aggression change (returns an "idle" move).
 */
fun advanceRival(
    state: RivalPartyState,
    snapshot: RivalMapSnapshot,
    paused: Boolean,
    turn: Int,
): RivalMove

/**
 * Deterministic headline template index for a move — no RNG, stable per (turn, bandId, kind).
 * Same idiom as RivalRealms.headlineTemplateIndex. Returns an index in [0, poolSize).
 */
fun headlineTemplateIndex(turn: Int, bandId: String, kind: String, poolSize: Int): Int
```

### 3.3 The movement / objective model — stated plainly (no hidden simulation)

So nobody mistakes this for a simulated party:

1. **Objective = nearest, highest-value candidate.** `chooseObjective` is a pure argmin over the snapshot's targets, ordered by `(distance asc, value desc, hexKey asc)`. The last key breaks ties **deterministically** — two runs on the same state pick the same hex.
2. **Movement = distance step-down.** Each turn the band closes `pace` hexes of cube distance. There is **no pathfinding, no terrain, no encounters en route** — the band is a token that gets `pace` closer. The GM dial is exactly the speed; nothing hidden accelerates it.
3. **Arrival snaps position** to the objective hex, tallies `arrivals++`, bumps aggression, and clears the objective so a fresh one is chosen next turn. Turns-to-arrival the players see is simply `ceil(distanceToObjective / pace)`.
4. **The PCs can win the race.** Before the band chooses/keeps an objective, the snapshot has already dropped any hex the players explored/claimed/cleared this turn. If the players got there first, that target is gone and the band re-targets — no "they got there first" offer fires. That is the intended counterplay.
5. **Aggression is arithmetic, not mood.** It rises by a fixed amount on a contested arrival and by a fixed amount per turn spent near claimed territory. No feedback loop, no RNG.
6. **Determinism invariant (the contract):** `advanceRival` is a **pure function of `(state, snapshot, paused, turn)`**. The only impurity in the whole feature is `buildRivalMapSnapshot` reading the live map; that read happens **once, in `performEndTurn`, before the tick**, and the resulting snapshot is passed *into* the (still-pure) tick. Given the same kingdom state and turn number, **preview and commit produce byte-identical `RivalMove`s** — stronger than a seeded RNG because there is no RNG at all. Headline *flavor* variety comes from `headlineTemplateIndex` (a hash of `turn+bandId+kind`), which is likewise deterministic.

### 3.4 jsMain adapter — concrete signature

```kotlin
// kingdom/RivalCharterPartyEngine.kt (jsMain)

/** One band's turn: updated record + the move record consumed by performEndTurn. */
data class RivalPartyTurn(
    val party: RawRivalCharterParty,     // NEW copy: position, objective, distance, aggression, arrivals
    val move: RivalPartyMove?,           // null when idle/paused with nothing to say
)

/** Per-headline / per-offer record for gazette + offers (jsMain). */
data class RivalPartyMove(
    val bandId: String,
    val factionRef: String?,
    val headlineKey: String,             // i18n key chosen by headlineTemplateIndex
    val headlineData: AnyObject,         // { band, place, faction, turns } for interpolation
    val gotThereFirst: RivalArrivalOffer?,   // non-null => post km-offer-rival-reached-target
    val confrontation: RivalConfrontationOffer?, // non-null => post km-offer-rival-confrontation
)

/** Build the snapshot once (impure), then advance every band (pure). */
fun advanceAllRivalParties(
    parties: Array<RawRivalCharterParty>,
    groupsByName: Map<String, RawGroup>,   // for atWar / label lookups
    snapshot: RivalMapSnapshot,            // from buildRivalMapSnapshot(kingdom)
    turn: Int,
): Pair<Array<RawRivalCharterParty>, List<RivalPartyMove>>
```

### 3.5 Tick surface — `TurnTickingEngine.tick()` (monthly), NOT `DailyTickHooks`

Rival-party movement runs **inside the monthly `TurnTickingEngine.tick()`** at End Turn, returning new fields on `TickResult`:

```kotlin
data class TickResult(
    // ... existing fields (groups, warThreats, newlyTriggeredThreats, updatedClocks, …) ...
    val rivalCharterParties: Array<RawRivalCharterParty> = emptyArray(),
    val rivalPartyMoves: Array<RivalPartyMove> = emptyArray(),
)
```

`tick()` gains two params, mirroring how it already receives `warThreats`/`groups` and returns their ticked forms:

```kotlin
rivalCharterParties: Array<RawRivalCharterParty> = emptyArray(),
rivalMapSnapshot: RivalMapSnapshot? = null,   // built by the caller (performEndTurn); null => skip
```

**Why the monthly tick (respecting the two-tick split, no third tick):**

- The band competes over **kingdom-scale exploration**, which advances at **End Turn** in lockstep with the players' own hex claiming and kingdom level. A band creeping daily while the players explore per session would make "turns to arrival" jitter meaninglessly.
- `tick()` is the **preview-safe** surface; every other turn consequence (clocks, standing drift, war threats, caravans) already resolves there, so the band participates in the same preview/commit parity contract. **`DailyTickHooks` is explicitly untouched** — it owns daily-world-clock effects (weather, companion **travel**, companion **expeditions**). This is the deliberate mirror: *your* companion expeditions tick daily; the *adversarial* charter party ticks monthly.
- The one impurity (`buildRivalMapSnapshot`, reading `kingmaker.state`) lives in **`performEndTurn`** (`TurnWizardApplication.kt`), which builds the snapshot and passes it into the pure `tick()`. `tick()` stays Foundry-free and unit-testable.

`performEndTurn` then: persists `kingdom.rivalCharterParties = tickResult.rivalCharterParties`, feeds `tickResult.rivalPartyMoves` headlines into `formatTurnGazette`, and posts the §5 offers (whispered to GMs, digest-style — the same shape as the `newlyTriggeredThreats` block at `TurnWizardApplication.kt:588`).

---

## 4. UI Design

### 4.1 Status section — Trade Agreements board (recommended tab)

**No new nav entry.** The rival band status belongs on **`MainNavEntry.TRADE_AGREEMENTS`** (`src/jsMain/resources/applications/kingdom/sections/trade-agreements/page.hbs`) — the board that already lists groups + attitude/standing and (per the sibling plan) the Rival Realms standings. A charter party reads naturally next to the faction it was chartered by. Add a new `<section>` after the Groups / Rival Realms sections.

Each band row shows what the players care about — **where it is, what it's after, and how close** — plus a GM-only dial column:

```hbs
<section>
  <h2>{{localizeKM "kingdom.rivalCharter.title"}}
    {{#if isGM}}<span class="km-header-right-align">
      <button type="button" data-action="add-rival-charter"><i class="fa-solid fa-plus"></i></button>
    </span>{{/if}}
  </h2>
  {{#if rivalCharter.rows.length}}
  <table>
    <thead><tr>
      <td>{{localizeKM "kingdom.rivalCharter.band"}}</td>
      <td>{{localizeKM "kingdom.rivalCharter.faction"}}</td>
      <td>{{localizeKM "kingdom.rivalCharter.position"}}</td>
      <td>{{localizeKM "kingdom.rivalCharter.objective"}}</td>
      <td>{{localizeKM "kingdom.rivalCharter.eta"}}</td>
      <td>{{localizeKM "kingdom.rivalCharter.arrivals"}}</td>
      {{#if isGM}}<td>{{localizeKM "kingdom.rivalCharter.aggression"}}</td><td></td>{{/if}}
    </tr></thead>
    <tbody>
    {{#each rivalCharter.rows}}
      <tr>
        <td>{{band}}</td>
        <td>{{faction}}{{#unless linked}} <i class="fa-solid fa-link-slash"
            data-tooltip="{{localizeKM "kingdom.rivalCharter.unlinked"}}"></i>{{/unless}}</td>
        <td>{{positionLabel}}</td>
        <td>{{objectiveLabel}} <span class="km-tag">{{objectiveKindLabel}}</span></td>
        <td>{{#if etaTurns}}{{localizeKM "kingdom.rivalCharter.etaTurns" turns=etaTurns}}{{else}}—{{/if}}</td>
        <td>{{arrivals}}</td>
        {{#if ../isGM}}
          <td><meter min="0" max="{{aggressionMax}}" value="{{aggression}}"></meter></td>
          <td>
            <button type="button" data-action="edit-rival-charter" data-id="{{id}}"><i class="fa-solid fa-sliders"></i></button>
            <button type="button" data-action="delete-rival-charter" data-id="{{id}}"><i class="fa-solid fa-trash"></i></button>
          </td>
        {{/if}}
      </tr>
    {{/each}}
    </tbody>
  </table>
  {{else}}<p>{{localizeKM "kingdom.rivalCharter.empty"}}</p>{{/if}}
</section>
```

**Optional map marker (GM-only write).** A band's `currentHexKey` can be surfaced as a hex-map marker (a Drawing/Note at that hex) so players *see* the rival closing in. **This write must be gated behind `game.user.isGM`** — per the "embedded-doc writes GM-only" lesson (`registerHexGridSync`/`HexContentSync` throw "User lacks permission" for players). Marker sync is a **deferred sub-phase**; the table above is the v1 surface.

### 4.2 Context object (`RivalCharterContext.kt`, jsMain, `sheet/contexts/`)

```kotlin
@JsPlainObject
external interface RivalCharterRowContext {
    val id: String
    val band: String
    val faction: String
    val linked: Boolean            // factionRef resolves to a real group
    val positionLabel: String      // native hex label or "off-map"
    val objectiveLabel: String     // target hex label or "—"
    val objectiveKindLabel: String // localized kind
    val etaTurns: Int?             // ceil(distance / pace); null when idle
    val arrivals: Int
    val aggression: Int            // GM-only fields still emitted; template gates on isGM
    val aggressionMax: Int
}

@JsPlainObject
external interface RivalCharterContext {
    val rows: Array<RivalCharterRowContext>
    val isGM: Boolean
}
```

Built in `KingdomSheet.kt` beside the existing group/rival-realm context assembly: map `kingdom.rivalCharterParties` (resolving labels via the same `formatHexKeyLabel` / `kingmaker.region.hexes` reads the engine uses, defensively) → `RivalCharterRowContext`. Rows with `visibleToPlayers == false` are **stripped when `!isGM`** (same visibility split as `RawWarThreat`/expeditions).

### 4.3 GM dialog — `ModifyRivalCharterParty.kt`

A small Foundry `FormApp`/dialog (pattern of `AddWarThreat.kt` / `ModifyFactionStanding.kt`) for add/edit:

- **Band name** (text) and **members** blurb (text) + nominal **level** (number, for confrontation budgeting)
- **Faction** (select from `kingdom.groups` names → `factionRef`; blank = unaffiliated)
- **Current hex** (text/hex-key input, or a picker seeded from `ExpeditionDestinationOptions` — reuse the destination builder)
- **Pace** (number, hexes/turn; default 1) and **Pause movement** (checkbox)
- **Aggression threshold** (number; blank = confrontation disabled)
- **Player-visible** (checkbox)
- A **"Pitax Chartists" seed template** button that prefills a plausible band.

On save: upsert into `kingdom.rivalCharterParties`, `actor.setKingdom(kingdom)`. Wired via `data-action="add-rival-charter" | edit-rival-charter | delete-rival-charter` handlers in `KingdomSheet._onClickAction`, GM-gated, in a `buildPromise{}` block.

### 4.4 i18n namespace

All keys nested under `pf2e-kingmaker-tools` → `kingdom.rivalCharter.*` in `lang/en.json` (**nested objects, never flat-dotted** — see `scripts/check_i18n_keys.py`; catalog wired through `initLocalization()`):

```json
"kingdom": {
  "rivalCharter": {
    "title": "Rival Charter Parties",
    "band": "Band", "faction": "Chartered By", "position": "Last Seen",
    "objective": "Making For", "eta": "ETA", "arrivals": "Beat You To",
    "aggression": "Aggression",
    "etaTurns": "{{turns}} turn(s)",
    "empty": "No rival bands on the map. Add one to put a competitor in the field.",
    "unlinked": "This band's faction no longer exists.",
    "kind": { "unexplored": "Unexplored hex", "uncleared-lair": "Uncleared lair",
      "contested-claim": "Contested claim", "landmark": "Landmark" },
    "headline": {
      "advance": ["{{band}} presses toward {{place}}.",
                  "Scouts spot {{band}} moving on {{place}}.",
                  "{{band}} is {{turns}} turn(s) from {{place}}."],
      "arriveUnexplored": ["{{band}} reached {{place}} ahead of you."],
      "arriveLair": ["{{band}} cleared the lair at {{place}} first."],
      "arriveContested": ["{{band}} planted {{faction}}'s banner on {{place}}."],
      "arriveLandmark": ["{{band}} claimed {{place}} — {{faction}} got there first."],
      "idle": ["{{band}} has gone to ground; no sign of movement."]
    },
    "arrivalOffer": { "title": "{{band}} reached {{place}} first",
      "cede": "Let Them Have It", "race": "Race Them", "confront": "Confront",
      "dismiss": "Narrate Only" },
    "confrontationOffer": { "title": "{{band}} grows bold",
      "warThreat": "Raise War Threat", "encounter": "Spawn Encounter", "dismiss": "Dismiss" },
    "dialog": { "add": "Add Rival Charter Party", "edit": "Edit Rival Charter Party",
      "band": "Band Name", "members": "Roster", "level": "Party Level", "faction": "Chartered By",
      "currentHex": "Current Hex", "pace": "Pace (hexes / turn)", "pauseMovement": "Pause Movement",
      "aggressionThreshold": "Aggression Threshold", "visibleToPlayers": "Visible to Players",
      "seedPitax": "Seed: Pitax Chartists" }
  },
  "turnGazette": { "rivalCharterMove": "{{list}}" }
}
```

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

Most turns, a band's move is **public gazette flavor with no button** ("presses toward the Temple of the Elk"). Only the two moments that would alter shared state — reaching a prize the players wanted, and escalating to war — become **GM-confirmed offers** (`km-offer-*` handlers in `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/ChatButtons.kt`), **never auto-applied**. A rival reaching a hex first is **an offer + a gazette line, not a silent `kingmaker.state` write**.

### 5.1 Gazette headlines (public, no button)

Each moving band emits one headline (chosen by `headlineTemplateIndex`), appended to the End-Turn gazette (see §4.4 pool). Pure fiction, no interaction. Idle/paused bands emit nothing (or the "gone to ground" line, at GM discretion).

### 5.2 Offer cards enumerated

| Trigger | Offer id | Buttons | Handler behavior (`ChatButtons.kt`) |
|---------|----------|---------|-------------------------------------|
| Band **arrived** at a target the players had not taken (`arrivedAt != null` and `lastArrivalTurn != currentTurn`) | `km-offer-rival-reached-target` | **[Let Them Have It]** · **[Race Them]** · **[Confront]** · **[Narrate Only]** | GM-gated, idempotent via `lastArrivalHexKey`/`lastArrivalTurn`. **[Let Them Have It]** posts a gazette-style loss line and **does not touch `kingmaker.state`** (the GM narrates the ceded hex); bumps `arrivals` (already done in the tick) and clears the offer. **[Race Them]** opens a lightweight time-pressure: prefills `AddQuest`/a campaign clock ("Reach {{place}} before {{band}} entrenches", short deadline) so the players still have a shot. **[Confront]** routes to the confrontation flow below (bumps aggression to threshold, immediately posts `km-offer-rival-confrontation`). **[Narrate Only]** posts nothing further. |
| Band **aggression crossed its threshold** this turn (`confrontation == true`) **or** the GM clicked **[Confront]** above | `km-offer-rival-confrontation` | **[Raise War Threat]** · **[Spawn Encounter]** · **[Dismiss]** | GM-gated, idempotent via `confrontationOffered`. **[Raise War Threat]** opens `AddWarThreat` prefilled `prefillName = t("kingdom.rivalCharter.confrontationOffer.title", {band})`, `prefillEnemyFaction = factionRef` — the **exact body of the existing `km-offer-war-threat` handler** (`ChatButtons.kt:179`): appends the threat, calls `recalculateWarPressure`, `setKingdom`. **[Spawn Encounter]** opens a prefilled encounter/quest hook budgeted off the band's `level` (GM runs it live). **[Dismiss]** sets `confrontationOffered = true` so it won't re-fire until aggression is spent, posts nothing. |

Both follow the established idempotent, GM-gated, `button.dataset["…"]`-driven shape (see `km-offer-war-threat` / `km-offer-war-threat-arrival` at `ChatButtons.kt:179`/`:201`, and `km-offer-diplomacy-quest` at `:270`). Both carry `data-kingdom-actor-uuid` for actor resolution and `data-band-id` to target the right party. New offer templates live in `resources/chatmessages/rival-charter-*.hbs`.

### 5.3 Digest, not spam

Offers are collected during `performEndTurn` and posted as **at most one whisper per offer type per turn** listing every band that crossed a threshold (mirrors the `newlyTriggeredThreats` digest at `TurnWizardApplication.kt:588`), so two bands reaching prizes produce one card with two button rows, not two cards.

---

## 6. Interactions With Existing Systems

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Native hex map (READ-ONLY)** | `kingdom/ExpeditionDestinations.kt`, `kingdom/map/KingmakerHexGridProvider.kt`, `kingdom/data/RawHexContent.kt`, `com.foundryvtt.kingmaker.*` | `buildRivalMapSnapshot` reuses the **exact reads** `buildExpeditionDestinationOptions` performs: `kingmaker.state.hexes` (`.claimed`/`.explored`/`.commodity`/content), `kingmaker.region.hexes` (`.find{it.key==intKey}`, `.name`, `.cube`), all `runCatching`-guarded. The rival **never writes** the map — got-there-first is an offer, not a mutation. |
| **Companion Expeditions (mirror idioms)** | `kingdom/ExpeditionDestinations.kt`, `companion/*` (`hexCubeDistance`, `expeditionTravelDays`, `formatHexKeyLabel`), `kingdom/RawCompanionExpedition.kt`, `DailyTickHooks.kt` | Reuses the cube-distance + travel-day + hex-label helpers verbatim. The band is the **adversarial mirror** of a companion expedition, deliberately on the **opposite tick** (monthly here vs. daily there) so the two never collide. `DailyTickHooks` untouched. |
| **Army & War Pressure** | `kingdom/data/RawWarThreat.kt`, `kingdom/dialogs/AddWarThreat.kt`, `kingdom/ChatButtons.kt` (`km-offer-war-threat`), `recalculateWarPressure` | Confrontation reuses `AddWarThreat` prefilled by `factionRef` — the same plumbing behind `km-offer-war-threat`. The band never spawns an army token; it *offers* a threat. |
| **Faction & Diplomacy Tracker** | `kingdom/data/RawGroup.kt`, `data/kingdom/FactionRelations.kt` | `factionRef → RawGroup.name` is the charter link; `RawGroup.atWar` biases aggression/headlines. A confrontation naturally interoperates with the tracker's own threshold hooks once the war threat exists. |
| **Turn ticking** | `kingdom/TurnTickingEngine.kt` | New `rivalCharterParties` + `rivalMapSnapshot` in, `rivalCharterParties` + `rivalPartyMoves` out on `TickResult`; movement runs in the pure monthly `tick()`. |
| **End-turn flow** | `kingdom/dialogs/TurnWizardApplication.kt` (`performEndTurn`) | Builds the map snapshot (the one impure read), passes it to `tick()`, persists `kingdom.rivalCharterParties`, feeds `rivalPartyMoves` into `formatTurnGazette` (both GM `turnNotes` **and** player `playerTurnNotes` at `:496`/`:510` — headlines are public), posts §5 offer digests. |
| **Gazette / Turn History** | `kingdom/TurnHistory.kt` (`formatTurnGazette`, `:73`) | Gains a `rivalCharterMoves: List<String> = emptyList()` param → public "Rival Bands" gazette lines. Passed to both GM and player gazette calls. |
| **Rival Realms Scoreboard (sibling)** | `docs/plans/2026-07-09-plan-rival-realms.md` | Complementary, **not coupled**. A charter party's `arrivals` could optionally feed a linked realm's `fame`/`size` — deliberately **out of scope** (a future `km-offer` bridge). See delineation below. |
| **Daily tick** | `kingdom/DailyTickHooks.kt` | **No interaction** — the band moves monthly only. |

### 6.1 Delineation from the two neighbors (explicit)

- **vs. Rival Realms Scoreboard** — A *rival realm* is an **abstract kingdom scoreboard**: `size`/`fame`/`armyCount` numbers that creep up, with **no position on the map**. A *rival charter party* is a **band with a concrete hex position and an objective** it walks toward, competing over the **same map the players explore**. "Pitax is Size 14" (realm) vs. "the Pitax Chartists are 2 hexes from the Temple of the Elk" (charter party). Both may emit off-screen gazette headlines — that shared *idiom* is the only overlap.
- **vs. Companion Expeditions** — Companion expeditions are **yours**: *you* dispatch them, they resolve on the **daily** world clock with degree-of-success rolls, and they grant **your** companions XP/loot/influence. A charter party is **theirs**: adversarial, GM-managed, resolves on the **monthly** kingdom tick, deterministic movement with **no simulated rolls**, and its "reward" is *your loss* (a hex you wanted). The charter party reuses the expedition system's *hex/travel helpers* but is otherwise its structural opposite.

### 6.2 Explicit OUT-OF-SCOPE

- **No simulated party.** No rival statblock, inventory, HP, or resolved combat. Confrontation hands the GM a prefilled war-threat/encounter and stops.
- **No silent map writes.** The rival never sets `claimed`/`explored`/`cleared` on `kingmaker.state`. Every map consequence is a GM-confirmed offer the GM applies (or narrates) by hand.
- **No pathfinding / terrain.** Movement is cube-distance step-down; the band does not route around rivers, armies, or difficult terrain.
- **No daily tick.** Monthly only; `DailyTickHooks` untouched.
- **No rival-vs-rival behavior.** Bands do not interact with each other or with rival realms (that convergence is a future bridge, not v1).
- **No player-facing band management.** Players see a read-only status row (and optional marker); all dials are GM-only.
- **Map marker sync is deferred** (its own GM-gated sub-phase) — the status table is the v1 surface.

---

## 7. Test Plan

### 7.1 commonTest — pure core (`data/kingdom/RivalCharterPartyTest.kt`, JVM-less)

| Test | Assertion |
|------|-----------|
| `hexDistance_matchesCubeMetric` | Distance is the standard cube metric; symmetric; 0 for same hex. |
| `chooseObjective_picksNearestThenHighestValue` | Given a near low-value hex and a slightly-farther landmark, picks by `(distance, -value, key)`; nearest wins ties on distance, value breaks equal distance. |
| `chooseObjective_deterministicTiebreak` | Two equal-distance, equal-value targets → the lower `hexKey` is chosen, every run. |
| `chooseObjective_nullWhenNoCandidates` | Empty snapshot or no position → null (idle). |
| `advanceRival_stepsPaceHexesTowardObjective` | distance 5, pace 2 → distanceToObjective 3, no arrival, no position snap. |
| `advanceRival_arrivesWhenDistanceReachesZero` | distance 2, pace 2 → `arrivedAt == objective`, position snaps to objective, objective cleared. |
| `advanceRival_reTargetsWhenObjectiveTakenByPlayers` | Objective absent from this turn's snapshot (players claimed it) → chooseObjective runs, no got-there-first offer. |
| `advanceRival_pausedNoMovementNoAggression` | `paused=true` → state unchanged, "idle" move. |
| `advanceRival_aggressionRisesNearClaimedTerritory` | Band within N hexes of a claimed hex → aggression increments by the proximity constant. |
| `advanceRival_confrontationOnThresholdCross` | aggression 4→5 with threshold 5 → `confrontation=true`; 5→6 → false (already past). |
| `advanceRival_deterministicSameInput` | Same `(state, snapshot, turn)` twice → identical `RivalMove` (no RNG). |
| `headlineTemplateIndex_deterministicInRange` | Same `(turn, bandId, kind)` → same index, always in `[0, poolSize)`; `poolSize=0 → 0`. |

### 7.2 jsTest — adapter + integration (`kingdom/RivalCharterPartyEngineTest.kt`)

| Test | Assertion |
|------|-----------|
| `buildMapSnapshot_defensiveOnMissingModule` | With `kingmaker` absent, `buildRivalMapSnapshot` returns an empty snapshot (no throw). |
| `buildMapSnapshot_classifiesKinds` | Unexplored/explored-unclaimed/lair/landmark hexes are bucketed into the right `kind` with the right `value`. |
| `advanceAllRivalParties_advancesAndHeadlines` | A band with a distant objective advances `pace`, emits an "advance" headline key. |
| `advanceAllRivalParties_arrivalEmitsGotThereFirstOffer` | Reaching a target sets `RivalPartyMove.gotThereFirst`; `arrivals` incremented; idempotent across a repeated same-turn call. |
| `advanceAllRivalParties_confrontationOffer` | Aggression crossing threshold sets `RivalPartyMove.confrontation`; below → null. |
| `tick_returnsRivalCharterPartiesAndMoves` | `TurnTickingEngine.tick(rivalCharterParties=…, rivalMapSnapshot=…)` populates `TickResult.rivalCharterParties` + `rivalPartyMoves`. |
| `tick_previewCommitParity` | Same kingdom state + turn + snapshot twice → byte-identical parties + moves. |
| `gazetteIncludesRivalCharterHeadlines` | `formatTurnGazette(rivalCharterMoves=…)` output contains the interpolated headline lines. |
| `unlinkedFaction_movesButAtWarFalse` | `factionRef` with no matching group → still moves; `atWar` treated false; no crash. |
| `migration49_seedsEmptyArray` | KingdomData without `rivalCharterParties` → after Migration49 → non-null, length 0. |

### 7.3 Manual Foundry verification checklist

1. Open Kingdom Sheet → **Trade Agreements** tab. With no bands, the Rival Charter Parties section shows the empty hint.
2. Click **Add Rival Charter Party** → "Seed: Pitax Chartists" → set current hex near an unexplored region, pace 1, aggression threshold 4 → Save. Row appears with position, objective (nearest prize), and an ETA.
3. **End Turn** → gazette shows a "presses toward {place}" headline; the row's ETA drops by 1; objective/distance persist.
4. Run several turns → confirm the band closes exactly `pace` hexes/turn (deterministic); ETA = `ceil(distance/pace)`.
5. Let the band reach a target the players have **not** taken → a whispered **`km-offer-rival-reached-target`** appears with four buttons; `arrivals` increments; a public "got there first" gazette line lands.
6. Click **[Race Them]** → a prefilled quest/clock offering the players a shot appears. Re-running the turn does **not** re-fire the arrival offer (idempotent).
7. Before an End Turn, have the players **claim the band's objective hex**; End Turn → the band silently re-targets, **no** got-there-first offer.
8. Push aggression past the threshold (prowl near claimed hexes, or click **[Confront]**) → **`km-offer-rival-confrontation`** → **[Raise War Threat]** opens `AddWarThreat` prefilled with the faction → Save → threat lands on the Army Pressure board.
9. Toggle **Pause Movement** → End Turn → the band does not move, emits no advance headline.
10. Log in as a **player** → the status row is read-only (no dials, no Add/Edit/Delete, no offer whispers); a `visibleToPlayers=false` band is hidden entirely.
11. Confirm the band **never** altered `kingmaker.state` (no hex silently flipped to claimed/explored). Rename the linked group → the row shows the "unlinked" hint but still renders/moves.
12. Reload the world → bands, positions, objectives, aggression, and arrivals persist. Run `scripts/check_i18n_keys.py` → no raw/flat keys.

---

## 8. Phasing (Independently Committable)

Each phase is one kanban worker card, ~1–2 days.

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Data model + migration + values** | `RawRivalCharterParty` interface, `KingdomData.rivalCharterParties`, `Defaults` seed, `Migration49` (+ import + registry), `data/rival-charter-values.json` (per-kind value weights + aggression constants). | `kingdom/data/RawRivalCharterParty.kt`, `kingdom/KingdomData.kt`, `sheet/Defaults.kt`, `migrations/migrations/Migration49.kt`, `migrations/Migrations.kt`, `data/rival-charter-values.json` |
| **2** | **Pure core (commonMain) + tests** | `RivalCharterParty.kt` (`HexCube`, `RivalTarget`, `RivalMapSnapshot`, `RivalPartyState`, `chooseObjective`, `advanceRival`, `RivalMove`, `headlineTemplateIndex`, `hexDistance`), full `RivalCharterPartyTest`. | `commonMain/.../data/kingdom/RivalCharterParty.kt`, `commonTest/.../data/kingdom/RivalCharterPartyTest.kt` |
| **3** | **jsMain engine + tick integration** | `RivalCharterPartyEngine.kt` (`buildRivalMapSnapshot`, `advanceAllRivalParties`, `RivalPartyTurn`, `RivalPartyMove`), extend `TickResult` + `tick()` params, wire the snapshot build + tick call + persistence + gazette into `performEndTurn`, `formatTurnGazette` `rivalCharterMoves` param, jsTest. | `kingdom/RivalCharterPartyEngine.kt`, `kingdom/TurnTickingEngine.kt`, `kingdom/dialogs/TurnWizardApplication.kt`, `kingdom/TurnHistory.kt`, `RivalCharterPartyEngineTest.kt` |
| **4** | **Status UI + GM dialog + i18n** | Rival Charter section on the trade-agreements board, `RivalCharterContext`, `ModifyRivalCharterParty` dialog (with Pitax seed + destination-picker reuse), sheet action handlers (GM-gated), `lang/en.json` keys. | `sections/trade-agreements/page.hbs`, `sheet/contexts/RivalCharterContext.kt`, `kingdom/dialogs/ModifyRivalCharterParty.kt`, `sheet/KingdomSheet.kt`, `lang/en.json` |
| **5** | **Offer handlers + QA** | `km-offer-rival-reached-target` (4 buttons) + `km-offer-rival-confrontation` (3 buttons) handlers (digest, idempotent, GM-gated; war-threat reuses `AddWarThreat`; race reuses `AddQuest`/clock), offer templates, jsTest for offers, manual checklist. | `kingdom/ChatButtons.kt`, `chatmessages/rival-charter-arrival-offer.hbs`, `chatmessages/rival-charter-confrontation-offer.hbs`, `RivalCharterPartyEngineTest.kt` (offer cases) |
| **6 (deferred/optional)** | **Hex-map marker sync** | GM-gated Drawing/Note marker at each band's `currentHexKey`, cleared on delete; players *see* the rival closing in. Reuses the hex-sync layer, gated behind `game.user.isGM` (embedded-doc-writes lesson). | `kingdom/map/*` (marker sync), `KingdomSheet.kt` |

**Dependencies:** 1 → 2 → 3 → {4, 5}. Phase 2 (pure) can start alongside Phase 1 (data). Phase 6 is optional and can ship anytime after Phase 3.

---

## 9. Open Questions for Gregory

1. **Objective value weights.** Default `landmark 40 / lair 30 / contested 20 / unexplored 10` — tune so the band feels like it chases *interesting* prizes, not just the nearest empty hex?
2. **Aggression sources.** Rise on contested arrival + proximity-to-claimed only, or also when the players *contest* it via **[Race Them]** / **[Confront]**? (Plan: arrival + proximity; Confront jumps to threshold.)
3. **Position snap vs. intermediate hexes.** The band snaps to its objective on arrival (no intermediate hex trail). Good enough, or do you want it to occupy the hex it's `pace`-stepped to each turn (more "real," slightly more state)?
4. **Race-Them mechanic.** Should **[Race Them]** spawn a `RawQuest` (via `AddQuest`) or a **campaign clock** with a deadline? Both exist; a clock is lighter, a quest is more visible.
5. **Multiple bands.** Cap concurrent bands (like `MAX_CONCURRENT_EXPEDITIONS=3`), or leave it unbounded and rely on the digest to avoid spam?
6. **Rival-realm bridge.** Should a band's `arrivals` optionally feed a linked `RawRivalRealm`'s `fame`/`size` when both features are present? (Currently out of scope — a clean future `km-offer` bridge.)

---

**End of Plan.** Ready for review. On approval, implementation cards follow the Phase 1–5 table (Phase 6 optional).

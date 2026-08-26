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

**Be honest about what this is:** a **scoreboard with teeth**, not a simulated party. There is **no hidden combat resolver, no rival inventory, no A\* pathfinding, no simulated rolls**. The band is a distance counter walking down toward a target at a GM-dialed pace, and a headline generator behind a handful of GM buttons. Movement is a pure function of state + turn (a cube-distance countdown plus a deterministic scored argmax over candidate targets), so preview/commit parity is automatic and a GM can predict exactly where the band will be in N turns. The point is to give exploration **urgency** and the occasional **sting of losing the race** — cheaply, and always under GM control.

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
 * Honesty contract: movement is deterministic (a cube-distance countdown; position only
 * changes on arrival); there is no simulated combat, no inventory, no pathfinding. Reaching
 * a target is a GM-confirmed OFFER + a gazette line, never a silent kingmaker.state write.
 * Lifecycle changes (retire/defect/join) are likewise GM clicks, never automatic.
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

    // --- Lifecycle (§2.4) ----------------------------------------------------
    /** "active" | "defected" | "retired" | "joined". null/absent => "active" (migration
     *  safety). Only active/defected bands tick. Bare String, no enum — the same shape
     *  RawCompanionExpedition.status/tier/lootTier use for their documented literal sets. */
    var status: String?

    // --- Flavor (never mechanically simulated) -------------------------------
    /** Free-text roster blurb ("Ganderel and four sellswords"). Display only. */
    var members: String?
    /** Level CURVE relative to the party, not an absolute level: the confrontation
     *  encounter budget is partyLevel + levelOffset, clamped to 1..20 (§2.6).
     *  null/absent => 0, i.e. "an even match". */
    var levelOffset: Int?

    // --- Map position + objective (the whole point) --------------------------
    /** Current hex key (string key into kingmaker.state.hexes). null => off-map / not yet placed. */
    var currentHexKey: String?
    /** GM-authored objective queue: ordered hex keys the band works through BEFORE the
     *  automatic scoring takes over. null/empty => fully automatic. Consumed head-first;
     *  dequeue/skip rules in §2.6. */
    var agenda: Array<String>?
    /** The hex the band is currently walking toward. null => idle / recompute next tick. */
    var objectiveHexKey: String?
    /** Why this hex is valuable: "unexplored" | "unclearedLair" | "contestedClaim" | "landmark".
     *  camelCase deliberately: ValueEnum.value is toCamelCase() and fromCamelCase resolves via
     *  toEnumConstant() (Utils.kt), so a hyphen would forever foreclose promoting this field to
     *  a ValueEnum. Drives the headline pool and the got-there-first offer copy. */
    var objectiveKind: String?
    /** Hexes still to cover to reach objectiveHexKey. Set to hexDistance(current, objective)
     *  when the objective is chosen and decremented by `pace` every turn — this countdown IS
     *  the band's progress, because currentHexKey only changes on arrival (§3.3 point 2). */
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
    /** Player-board visibility (house rule). Nullable for migration safety; null/true = visible. */
    var visibleToPlayers: Boolean?
}
```

Field list (20): `id`, `factionRef`, `name`, `status`, `members`, `levelOffset`, `currentHexKey`, `agenda`, `objectiveHexKey`, `objectiveKind`, `distanceToObjective`, `pace`, `pauseMovement`, `aggression`, `aggressionThreshold`, `confrontationOffered`, `arrivals`, `lastArrivalHexKey`, `lastArrivalTurn`, `visibleToPlayers`.

### 2.2 Persistence location — top-level `KingdomData.rivalCharterParties`, NOT nested on `RawGroup`

**Decision:** a new top-level `KingdomData.rivalCharterParties: Array<RawRivalCharterParty>?`, each row carrying `factionRef → RawGroup.name`.

**Why a top-level array, and why the soft-FK-by-name link** (mirroring the sibling rivals plan and the established codebase pattern):

- A kingdom has *many* groups (Sootscale, every Brevoy house, minor trade partners) but only **1–3** are out on the map as charter parties. Nesting a `charterParty` block on every group would bloat the hot-path trade-partner record (`RawGroup` is read on trade activities, caravan routing, negotiation DCs).
- A top-level nullable array is exactly how `KingdomData.warThreats`, `KingdomData.armyDeployments`, and `KingdomData.companionExpeditions` already live — soft-FK-by-name is the established contract (`RawWarThreat.enemyFaction`, caravan partner by name, `RawCompanionExpedition.companionIds`). A charter party is *closest in shape* to a `RawCompanionExpedition` (an off-screen party record) but adversarial and monthly.
- The band is **not a group** — it is a mobile band *chartered by* a group. A `RawGroup` is a static polity; conflating the two would break the trade/diplomacy model.

```kotlin
// KingdomData.kt — ADD after `companionExpeditions`, before the closing brace.
// Nullable => null means "not yet migrated"; empty => no bands.
var rivalCharterParties: Array<RawRivalCharterParty>?
```

`Defaults.kt` `createKingdomDefaults()` seeds `rivalCharterParties = emptyArray()` (beside `companionExpeditions = emptyArray()`).

The link is a **soft foreign key**: `factionRef` is a `RawGroup.name`. If a GM renames/deletes the group, the band survives, shows an "unlinked faction" hint, and its `atWar` lookup falls back to neutral — the same forgiving contract as caravans/war-threats. No cascade.

### 2.3 Migration — `Migration66`

The migration chain currently ends at **`Migration65`** (`src/jsMain/kotlin/at/posselt/pfrpg2e/migrations/Migrations.kt` — the imports and the `migrations = listOf(...)` both run contiguously to `Migration65()`; `latestMigrationVersion = migrations.maxOfOrNull { it.version }!!`, and `MigrationChainTest` asserts `(17..65).toList()`). Take **`Migration66`** and extend that hardcoded range — verify 66 is still free at implementation time. Note the sibling *Rival Realms* plan claims the next free number too; whichever ships second takes the one after.

> ⚠️ **The number in this section is a placeholder and must be re-derived at implementation.**
> The chain now ends at **`Migration65`**. Since these plans were written, four of the reserved
> numbers have LANDED: 62 = downtime-projects, 63 = scheduled-pressure-engine,
> 64 = map-dynamism, 65 = loot-manifests. `Migration49` was never free (it sits inside the
> long-registered 17..61 range) and several unimplemented plans still name it. The next free
> number is **66**. Take the next contiguous number when this actually lands, and extend
> `MigrationChainTest`'s hardcoded range.


```kotlin
class Migration66 : Migration(66) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.rivalCharterParties == null) {
            kingdom.rivalCharterParties = emptyArray<Any>()
        }
    }
}
```

- **Non-breaking:** `rivalCharterParties` null/absent → no bands; the status section shows only an empty hint. Existing saves load unchanged.
- **No backfill:** bands are opt-in; the GM adds one via the dialog (with a "Pitax Chartists" seed template). Migrations are effectively one-shot/irreversible (single auto-backup) — keep this one a pure null-guard.
- Register `Migration66()` in the `migrations` list **and** add the import in `Migrations.kt`; `latestMigrationVersion` recomputes from the list, and `MigrationChainTest`'s `assertEquals((17..65).toList(), …)` becomes `(17..66)`.

### 2.4 Lifecycle — `status`, and what a chapter beat does to a band

A charter is not eternal. The AP's rival bands get recalled, change patrons, or throw in with whoever is winning once a chapter closes. **Decision:** one bare-string `status` field with four literal values, and a single GM-confirmed offer that fires on the module's existing chapter signal.

`status` is a plain `String?`, not an enum — the same shape the sibling record uses (`RawCompanionExpedition.status` / `tier` / `lootTier` in `kingdom/data/RawCompanionExpedition.kt` are all documented literal sets with no backing enum).

| `status` | Fiction | Ticks? | Board row |
|---|---|---|---|
| `"active"` (also `null`/absent) | chartered and in the field | yes | full row |
| `"defected"` | still in the field, new patron — `factionRef` was rewritten | yes | full row; the faction cell carries a "changed banner" tooltip |
| `"retired"` | charter revoked, band went home | **no** | greyed; position/objective render `—`, `arrivals` kept for the record |
| `"joined"` | band threw in with the PCs | **no** | greyed; the fiction now lives on an ally `RawGroup` |

Pure-core predicate, in `RivalCharterParty.kt` beside the tunables:

```kotlin
/** Only these bands move. null/absent == "active" (migration safety). */
fun isRivalBandActive(status: String?): Boolean =
    status == null || status == "active" || status == "defected"
```

`advanceAllRivalParties` partitions on it: an inactive band is copied through **unchanged** and emits **no** `RivalPartyMove` — no headline, no offer, no aggression drift, no objective recompute. `RivalCharterRowContext` gains `inactive: Boolean` and `statusLabel: String` so the row **greys out rather than vanishing** (a retired rival is part of the chronicle, and its `arrivals` tally is the scoreboard's memory).

**Trigger — the chapter beat.** The module's existing "a chapter turned" signal is an expiring campaign clock (`campaign/CampaignClock.kt`; surfaced on `TickResult.updatedClocks` / `TickResult.clockEvents` in `kingdom/TurnTickingEngine.kt`). `performEndTurn` posts **one** GM-whispered `km-offer-rival-lifecycle` digest when `tickResult.updatedClocks.any { it.expired }` **and** at least one band is active — one row per active band, four buttons each. The same transitions are also reachable by hand from a **Status** select in `ModifyRivalCharterParty`, so a table that does not use campaign clocks is never stuck.

| Button | `data-action` | Effect (`ChatButtons.kt`, GM-gated, idempotent on `status`) |
|---|---|---|
| **[Recall Them]** | `retire` | `status = "retired"`, `objectiveHexKey = null`, `distanceToObjective = null`, `pauseMovement = true`. Posts the public gazette line `kingdom.rivalCharter.lifecycle.retired`. |
| **[Change Banner]** | `defect` | Opens a faction select over `kingdom.groups.map { it.name }` (the list `AddWarThreat` already takes). On save: `factionRef = <picked>`, `status = "defected"`, `aggression = 0`, `confrontationOffered = false`. The band keeps ticking for its new patron. |
| **[They Join You]** | `join` | `status = "joined"`, and — if no group of that name exists yet — appends `RawGroup(name = band.name, negotiationDC = 15, atWar = false, preventPledgeOfFealty = false, relations = "diplomatic-relations", standing = 25, hexKey = band.currentHexKey)` to `kingdom.groups`, so the band survives as a diplomatic partner on the very board its status row renders next to. |
| **[Leave Be]** | `dismiss` | Nothing; the digest is consumed for this turn. |

`status` is **never** written by the tick. Like every other consequence here, a lifecycle change is a GM click.

### 2.5 Why two bands — `MAX_RIVAL_CHARTER_PARTIES = 2`

**Decision:** the cap is **2**, enforced on the add path.

```kotlin
// commonMain, RivalCharterParty.kt, beside the other tunables (§3.6)
const val MAX_RIVAL_CHARTER_PARTIES = 2

/** Bands counting against the cap — retired/joined rows are archive, not competitors. */
fun activeRivalBandCount(statuses: List<String?>): Int = statuses.count(::isRivalBandActive)
```

Enforced exactly the way the expedition cap is. `kingdom/ExpeditionLaunch.kt`'s `launchExpedition` returns `false` when `activeExpeditionCount(kingdom.companionExpeditions ?: emptyArray()) >= MAX_CONCURRENT_EXPEDITIONS`; `ModifyRivalCharterParty`'s save path refuses an **add** the same way (edits are always allowed), `ui.notifications.warn`s with `kingdom.rivalCharter.capReached`, and the template disables the **Add** button when `rivalCharter.atCap`.

Two — not three, not unbounded — for two independent reasons:

- **Perf.** `buildRivalMapSnapshot` is an O(map) walk of `kingmaker.region.hexes.contents` plus `kingmaker.state.hexes`, paid once per End Turn no matter how many bands exist. But each *band* adds a full scoring pass over every surviving candidate target on top of it, and End Turn is already the heaviest click in the module. Two bands keeps the per-turn add-on to two linear scans of a few hundred hexes, comfortably dominated by the snapshot walk itself.
- **Spotlight discipline.** Every moving band claims a public gazette headline and can produce an arrival offer *and* a confrontation offer in the same turn. At two, the worst turn is two headlines and two button rows inside one digest card. At three or more, End Turn stops being the players' turn recap and becomes a rival newsletter — and the whole point of this feature is the occasional sting, not a running commentary.

`MAX_RIVAL_CHARTER_PARTIES` gets a commonTest assertion mirroring `ExpeditionsContextTest`'s `assertEquals(3, MAX_CONCURRENT_EXPEDITIONS)`, so the number is a decision on the record rather than a magic literal.

### 2.6 Level curve and the agenda queue

**Level curve — `levelOffset`, not `level`.** A band's fighting weight only means anything *relative to the party*; an absolute level goes stale the moment the PCs level up. So the record stores an offset and the budget is derived:

```kotlin
// commonMain, RivalCharterParty.kt
/** Confrontation encounter budget: the party's level shifted by the band's curve. */
fun rivalEffectiveLevel(partyLevel: Int, levelOffset: Int?): Int =
    (partyLevel + (levelOffset ?: 0)).coerceIn(1, 20)
```

`partyLevel` is read by the jsMain adapter from `game.getAveragePartyLevel()` (`camping/CampingData.kt`) — the same source `rollRandomEncounter` uses to budget an encounter — falling back to `kingdom.level` when there is no party actor. The **[Spawn Encounter]** button (§5.2) budgets off `rivalEffectiveLevel(...)`, so a band seeded at `levelOffset = +1` stays a slightly-harder-than-even fight for the whole campaign without the GM ever re-editing it. The dialog exposes it as a signed **Level vs. Party** number (−4..+4 in practice), default 0.

**Agenda queue — `agenda: Array<String>?`.** The automatic scoring in §3.3 is the *default*, not the only mode. `agenda` is an ordered list of hex keys the GM wants this band to work through — "Pitax sent them for the Temple of the Elk, then the Sootscale caves" — consumed head-first:

- `chooseObjective` takes an `agenda: List<String>` parameter. While it is non-empty, the head key **is** the objective if it is still a live candidate in this turn's snapshot; scoring is skipped entirely.
- **Dequeue on arrival.** Reaching the head key drops it from `agenda`; the adapter writes the shortened array back onto the record via auto `.copy`.
- **Skip on loss.** If the head key is *not* in `snapshot.targetsByKey` — the players got there first, or it stopped being a prize — it is dropped **without** an arrival offer, and the next key is tried in the same turn, until the queue is empty or a live key is found. Same counterplay as §3.3 point 4, applied to a scripted target.
- **Fallback.** Empty or exhausted queue ⇒ automatic scoring. A band never idles just because its script ran out.

The dialog edits `agenda` as a newline-separated hex-key textarea seeded from the same destination picker as **Current hex**; the status table shows a small queued-objectives badge (`kingdom.rivalCharter.queued`) when a band still has scripted targets.

---

## 3. Engine Design

Two layers, matching the real codebase split (`FactionRelations.kt` pure-primitive math in commonMain + a jsMain `RawGroup` adapter; and the pure `EncounterResolverEngine` vs. its impure wrapper):

- **Pure core (commonMain, `data/kingdom/RivalCharterParty.kt`)** — operates on **primitives and plain data classes only** (no `RawRivalCharterParty`, no `kingmaker.*`, no Foundry). Fully unit-testable in commonTest.
- **jsMain adapter (`kingdom/RivalCharterPartyEngine.kt`)** — thin glue: reads the live map via a **defensive snapshot builder**, reads/rebuilds `RawRivalCharterParty` via auto `.copy`, calls the pure core. Tested in jsTest.

### 3.1 The map snapshot — the one impure read, taken once

The pure core cannot touch `kingmaker.state`. So the jsMain adapter builds a **plain-data snapshot** of the map's competitive surface, reusing the same read *pattern* as `ExpeditionDestinations.buildExpeditionDestinationOptions`: `kingmaker.state.hexes` for `.claimed`/`.explored`/`.cleared`, `kingmaker.region.hexes.contents` for `.name` and full-map coverage, plus `expeditionHexDistance`'s `hexes.find { it.key == intKey }?.cube` read for cube coordinates — every access wrapped in `runCatching`. (Read *pattern*, not the exact reads: `buildExpeditionDestinationOptions` itself touches neither `.commodity` nor hex content nor `.cube`, and the classification below additionally needs the kingdom's own `hexContents`, which that function has no reason to look at.)

```kotlin
// commonMain — plain, serializable, no Foundry.
data class HexCube(val q: Int, val r: Int, val s: Int)

/** One candidate target the rival could walk toward. */
data class RivalTarget(
    val hexKey: String,
    val cube: HexCube,
    val kind: String,       // "unexplored" | "unclearedLair" | "contestedClaim" | "landmark"
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

The jsMain builder `buildRivalMapSnapshot(kingdom: KingdomData): RivalMapSnapshot` classifies each hex from **two** sources: `kingmaker.state.hexes` (a sparse `HexState` record exposing `commodity`/`camp`/`features`/`claimed`/`explored`/`cleared` — `com.foundryvtt.kingmaker.KingmakerModule.kt`) and the kingdom's own `KingdomData.hexContents: Array<RawHexContent>?`.

**Region hexes carry no content.** `KingmakerHex` exposes key/name/zone/terrain/travel/difficulty/discoveryTrait/explorationState/color, and inherits the grid geometry members of `GridHex` (`com/foundryvtt/core/grid/GridHex.kt` — `coordinates`, `grid`, `center`, `topLeft`) — but **no content**, so every content-derived kind must key off `RawHexContent.type`, whose legal values are the `HexContentType` enum (`commonMain/.../data/hex/HexContentType.kt`: `LANDMARK`, `REFUGE`, `WORKSITE`, `RESOURCE`, `RUIN`, `MERCHANT`, `TRAINER`, `ENEMY_ARMY`, `CUSTOM` — note there is **no** `LAIR` constant, and `RawHexContent` has no `cleared` field of its own).

| `kind` | Rule | `value` |
|---|---|---|
| `landmark` | a `hexContents` row at this hex whose `type` is `HexContentType.LANDMARK.value` or `REFUGE.value` | 40 |
| `unclearedLair` | a `hexContents` row whose `type` is `RUIN.value` or `ENEMY_ARMY.value`, **and** `kingmaker.state.hexes[key]?.cleared != true` | 30 |
| `contestedClaim` | `HexState.explored == true && HexState.claimed != true` — explored frontier both sides can still take | 20 |
| `unexplored` | present in `kingmaker.region.hexes.contents` and **not** in `kingmaker.state.hexes` with `explored == true` (the `uncharted` set `buildExpeditionDestinationOptions` already computes) | 10 |

A hex matching more than one rule takes the **highest-value** kind. The old "has content, not claimed ⇒ lair prize" heuristic is deliberately gone: it swept merchants, trainers and worksites into the prize pool, and `HexState.cleared` — the actual signal, which §6.2 already promises the rival will never write — was going unread.

- `value` and the aggression constants are `const val`s in the pure core, **not** a data catalog — see §3.6.
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
    /** Hexes still to cover. THE progress counter — position only changes on arrival (§3.3.2). */
    val distanceToObjective: Int?,
    /** GM-authored objective queue, head-first (§2.6). Empty => fully automatic. */
    val agenda: List<String>,
    val pace: Int,
    val aggression: Int,
    val aggressionThreshold: Int?,
)

/** The band's chosen target for this turn (best score, deterministic tiebreak). */
data class ObjectiveChoice(val target: RivalTarget, val distance: Int)

/**
 * Pick the objective.
 *
 * While [agenda] is non-empty its head key wins outright, provided that key is still a live
 * candidate; otherwise the head is dropped and the next tried (§2.6). With no usable queued
 * key, the target maximising a SINGLE SCALAR SCORE wins:
 *
 *     score = target.value - RIVAL_DISTANCE_WEIGHT * hexDistance(currentCube, target.cube)
 *
 * argmax, ties broken by the LOWEST hexKey. Deliberately NOT a lexicographic
 * `(distance asc, value desc, key asc)` ordering: with distance ranked first, `value` could
 * only ever fire on an exact distance tie, so the per-kind weights would be inert and a
 * landmark one hex farther away could never beat an empty hex at any weighting.
 *
 * FULLY DETERMINISTIC — a stable argmax, no RNG. Returns null when the band has no position
 * or no candidate targets remain (idle).
 */
fun chooseObjective(
    currentCube: HexCube?,
    candidates: Collection<RivalTarget>,
    agenda: List<String> = emptyList(),
): ObjectiveChoice?

/** What one turn's advance produced. */
data class RivalMove(
    val newState: RivalPartyState,       // updated position, objective, distance, agenda, aggression
    val arrivedAt: RivalTarget?,         // non-null => reached a prize this turn (=> got-there-first offer)
    val movedFrom: String?,              // for the "advanced toward X" headline
    /** Keys the headline pool via headlinePoolSize/headlineKey below: "advance" | "idle" |
     *  "arriveUnexplored" | "arriveLair" | "arriveContested" | "arriveLandmark" (the arrive*
     *  variants are derived from the arrived target's `kind`). */
    val headlineKind: String,
    val newObjective: Boolean,           // an objective was (re)chosen this turn => km-offer-rival-rumor
    val confrontation: Boolean,          // aggression crossed threshold this turn (=> confrontation offer)
)

/**
 * Advance ONE band one kingdom turn against [snapshot]. Deterministic, no RNG:
 *   1. If there is no objective, or the objective is no longer in `snapshot.targetsByKey`
 *      (the PCs took it first), call
 *      `chooseObjective(state.currentCube, snapshot.targetsByKey.values, state.agenda)` and set
 *      `distanceToObjective = hexDistance(currentCube, target.cube)`.
 *   2. Otherwise `distanceToObjective = max(0, distanceToObjective - pace)`. `currentKey` and
 *      `currentCube` do NOT change on a non-arriving turn — the countdown is the band's
 *      progress, not its position (honest: the band is a scoreboard token, not a path).
 *   3. If `distanceToObjective` reaches 0: arrivedAt = objective; `currentKey`/`currentCube`
 *      snap to the objective hex; aggression += RIVAL_ARRIVAL_AGGRESSION[kind]; objective AND
 *      distance cleared so a new one is chosen next turn.
 *   4. If the band sits within RIVAL_PROXIMITY_HEXES of a claimed hex:
 *      aggression += RIVAL_PROXIMITY_AGGRESSION.
 *   5. confrontation = (threshold != null && before < threshold && after >= threshold).
 * `paused` => no movement, no aggression change (returns an "idle" move).
 * Inactive bands (§2.4) never reach this function; the adapter filters them out first.
 */
fun advanceRival(
    state: RivalPartyState,
    snapshot: RivalMapSnapshot,
    paused: Boolean,
    turn: Int,
): RivalMove

/**
 * Deterministic headline template index for a move — no RNG, stable per (turn, bandId, kind).
 * NO PRECEDENT EXISTS YET: there is no `RivalRealms.headlineTemplateIndex` anywhere in `src/`;
 * this helper is INTRODUCED HERE. (The sibling `plan-rival-realms` proposes the same helper —
 * whichever lands first owns it and the other imports it.) A stable non-negative hash of the
 * three inputs, modulo [poolSize]; 0 when poolSize <= 0, so a missing pool degrades to the
 * first template instead of throwing.
 */
fun headlineTemplateIndex(turn: Int, bandId: String, kind: String, poolSize: Int): Int {
    if (poolSize <= 0) return 0
    var h = 17
    for (c in "$turn|$bandId|$kind") h = h * 31 + c.code
    return ((h % poolSize) + poolSize) % poolSize
}

/**
 * How many templates each headline kind owns. LITERAL, not derived from the catalog: the
 * i18n guard cannot see a runtime-composed key, so the pool sizes and the key strings live
 * together in the pure core and are asserted in commonTest (§4.4).
 */
fun headlinePoolSize(headlineKind: String): Int = when (headlineKind) {
    "advance" -> 3
    else -> 1
}

/** Exhaustive kind+index -> literal i18n key. No key is ever built by concatenation. */
fun headlineKey(headlineKind: String, index: Int): String = when (headlineKind) {
    "advance" -> when (index) {
        0 -> "kingdom.rivalCharter.headline.advance1"
        1 -> "kingdom.rivalCharter.headline.advance2"
        else -> "kingdom.rivalCharter.headline.advance3"
    }
    "arriveUnexplored" -> "kingdom.rivalCharter.headline.arriveUnexplored1"
    "arriveLair" -> "kingdom.rivalCharter.headline.arriveLair1"
    "arriveContested" -> "kingdom.rivalCharter.headline.arriveContested1"
    "arriveLandmark" -> "kingdom.rivalCharter.headline.arriveLandmark1"
    else -> "kingdom.rivalCharter.headline.idle1"
}
```

### 3.3 The movement / objective model — stated plainly (no hidden simulation)

So nobody mistakes this for a simulated party:

1. **Objective = the GM's script first, then the best score.** A non-empty `agenda` (§2.6) supplies the objective outright while its head key is still a live candidate. Otherwise `chooseObjective` is a pure **argmax** over the snapshot's targets on `value - RIVAL_DISTANCE_WEIGHT * distance`, ties broken by the lowest `hexKey` — **deterministic**, two runs on the same state pick the same hex. At the default weights (§3.6) a landmark stays the better prize until it is more than **6 hexes** farther than a nearby unexplored hex (`40 - 5d >= 10`), which is exactly the "chases interesting prizes, not just the nearest empty hex" behaviour the weights are for. A lexicographic `(distance, -value, key)` ordering would have made the weights inert, since `value` could then only break an exact distance tie.
2. **Movement = a distance countdown, not a creeping token.** `distanceToObjective` is set to `hexDistance(current, objective)` when the objective is chosen and drops by `pace` every turn thereafter; `currentHexKey` / `currentCube` stay put until arrival. The countdown is the state that moves — recomputing distance from an unchanged position would return the same number forever and the band would never arrive. There is **no pathfinding, no terrain, no encounters en route**; the GM dial is exactly the speed and nothing hidden accelerates it.
3. **Arrival snaps position** to the objective hex, tallies `arrivals++`, bumps aggression, and clears **both** the objective and the distance so a fresh one is chosen next turn. Turns-to-arrival the players see is simply `ceil(distanceToObjective / pace)`.
4. **The PCs can win the race.** Before the band chooses/keeps an objective, the snapshot has already dropped any hex the players explored/claimed/cleared this turn. If the players got there first, that target is gone and the band re-targets — no "they got there first" offer fires. That is the intended counterplay.
5. **Aggression is arithmetic, not mood.** It rises by a fixed amount on a contested arrival and by a fixed amount per turn spent near claimed territory. No feedback loop, no RNG.
6. **Determinism invariant (the contract):** `advanceRival` is a **pure function of `(state, snapshot, paused, turn)`**. The only impurity in the whole feature is `buildRivalMapSnapshot` reading the live map; that read happens **once, inside `runKingdomTurnTick`** — the single chokepoint every tick path funnels through (§3.5) — and the resulting snapshot is passed *into* the (still-pure) `tick()`. Given the same kingdom state and turn number, **preview, commit and the forecast produce byte-identical `RivalMove`s** — stronger than a seeded RNG because there is no RNG at all. Headline *flavor* variety comes from `headlineTemplateIndex` (a hash of `turn+bandId+kind`), which is likewise deterministic.

### 3.4 jsMain adapter — concrete signature

```kotlin
// kingdom/RivalCharterPartyEngine.kt (jsMain)

/** Per-headline / per-offer record for gazette + offers (jsMain). */
data class RivalPartyMove(
    val bandId: String,
    val factionRef: String?,
    val headlineKey: String,             // i18n key chosen by headlineTemplateIndex
    val headlineData: AnyObject,         // { band, place, faction, turns } for interpolation
    val gotThereFirst: RivalArrivalOffer?,   // non-null => post km-offer-rival-reached-target
    val confrontation: RivalConfrontationOffer?, // non-null => post km-offer-rival-confrontation
)

/**
 * Advance every band against a snapshot built once by the caller (pure from here down).
 * Inactive bands (§2.4) are copied through untouched and produce no move. There is
 * deliberately no per-band wrapper type: the two halves go to two different places —
 * the records to `kingdom.rivalCharterParties`, the moves to the gazette and the offers —
 * so a `RivalPartyTurn` pair would only be destructured at the single call site.
 */
fun advanceAllRivalParties(
    parties: Array<RawRivalCharterParty>,
    groupsByName: Map<String, RawGroup>,   // for atWar / label lookups
    snapshot: RivalMapSnapshot,            // from buildRivalMapSnapshot(kingdom)
    turn: Int,
): Pair<Array<RawRivalCharterParty>, List<RivalPartyMove>>
```

### 3.5 Tick surface — `TurnTickingEngine.tick()` (monthly), NOT `DailyTickHooks`

> ⚠️ **Deliberate deviation from the commissioning card.** The card specified that the band
> "moves on the world clock (**daily tick**)", grounded on `DailyTickHooks.kt`, with a movement
> model in **hexes/day**. This plan **overrides that instruction**: movement runs on the monthly
> kingdom tick with a **hexes/turn** pace. The three bullets below are the argument; it is raised
> as Open Question 0 in §9 so the override is signed off explicitly rather than inferred from a
> section heading.

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
- The one impurity (`buildRivalMapSnapshot`, reading `kingmaker.state`) lives in **`runKingdomTurnTick`**, which builds the snapshot and passes it into the pure `tick()`. `tick()` stays Foundry-free and unit-testable.

**The tick call goes through `runKingdomTurnTick`, never `tick()` directly.** `kingdom/dialogs/TurnWizardApplication.kt` carries the contract in that function's own doc comment: *"Single source of truth for assembling [TurnTickingEngine.tick] arguments from a kingdom snapshot. Both the End Turn commit path ([performEndTurn]) and the Turn Wizard preview MUST call this — never tick() directly — so the preview cannot drift from what committing actually applies."* Three call sites exist — `performEndTurn`, the Turn Wizard preview, and `kingdom/forecast/ForecastAdapter.kt` — and `src/jsTest/kotlin/at/posselt/pfrpg2e/kingdom/TurnWizardApplicationTest.kt` guards it.

So the snapshot build belongs **inside `runKingdomTurnTick`**:

```kotlin
fun runKingdomTurnTick(kingdom: KingdomData, storage: CommodityStorage, currentTurn: Int): TickResult =
    TurnTickingEngine.tick(
        // … existing arguments …
        rivalCharterParties = kingdom.rivalCharterParties ?: emptyArray(),
        rivalMapSnapshot = buildRivalMapSnapshot(kingdom),   // runCatching-guarded; empty on failure
    )
```

Building it here rather than adding a fourth parameter every caller must remember to supply is what keeps the parity claim true. Since `rivalMapSnapshot` defaults to `null` and "null ⇒ skip", threading it only through `performEndTurn` would leave the preview and the forecast ticking with **no** snapshot — the wizard would show no rival movement and then commit some, which is precisely the drift the chokepoint exists to prevent, and would make §3.3 point 6 and the `tick_previewCommitParity` test false. `buildRivalMapSnapshot` is already `runCatching`-guarded and returns an empty snapshot on any failure, so the extra read costs the preview one guarded map walk and can never throw into the tick.

`performEndTurn` then: persists `kingdom.rivalCharterParties = tickResult.rivalCharterParties`, feeds `tickResult.rivalPartyMoves` headlines into `formatTurnGazette`, and posts the §5 offers (whispered to GMs, digest-style — the same shape as the `newlyTriggeredThreats` digest block in `TurnWizardApplication.kt`).

### 3.6 Tunables — `const val`s in the pure core, not a data catalog

**Decision:** no `data/rival-charter-values.json`. The dials live as `const val`s in `commonMain` beside the rest of `RivalCharterParty.kt`.

```kotlin
// commonMain, at/posselt/pfrpg2e/data/kingdom/RivalCharterParty.kt
const val RIVAL_VALUE_LANDMARK = 40
const val RIVAL_VALUE_UNCLEARED_LAIR = 30
const val RIVAL_VALUE_CONTESTED_CLAIM = 20
const val RIVAL_VALUE_UNEXPLORED = 10

/** Hexes of distance worth one point of target value in chooseObjective's score (§3.3.1). */
const val RIVAL_DISTANCE_WEIGHT = 5

/** Aggression gained by arriving at a prize, by kind. */
const val RIVAL_ARRIVAL_AGGRESSION_CONTESTED = 2
const val RIVAL_ARRIVAL_AGGRESSION_OTHER = 1

/** Proximity-to-claimed-territory aggression: radius, and the per-turn increment. */
const val RIVAL_PROXIMITY_HEXES = 2
const val RIVAL_PROXIMITY_AGGRESSION = 1
```

**Why not a JSON catalog.** `data/` holds no top-level `.json` files at all — every catalog is a *subdirectory* of per-item files. `build.gradle.kts` registers `CombineJsonFiles` with `sourceDirectory = data/`, and that task does `Files.walk(source, 1).filter { Files.isDirectory(it) }.filter { it != source }` — depth-1 **directories only**, skipping the source root — so a top-level `data/rival-charter-values.json` would never be copied or emitted, and the `@JsModule("./rival-charter-values.json")` accessor the codebase uses (`KingdomGovernment.kt`, `KingdomMilestone.kt`, `RawActivity.kt`, …) could not resolve. Worse, `writeToFile` wraps every combined catalog in `buildJsonArray`, so the emitted shape is always a JSON **array of per-file objects**, never the single key/value dial object a tunables file wants.

Constants also keep the pure core self-contained: `chooseObjective` and `advanceRival` stay testable in commonTest with **zero** build wiring, which a catalog would break (the pure core would have to take the weights as parameters threaded from jsMain). If per-world tuning is ever wanted, the right shape is a **kingdom setting** on `KingdomData.settings` — not a build-time catalog — and the constants become the defaults.

If a catalog really is wanted later it must be `data/rival-charter/<one file per entry>.json`, combined into `rival-charter.json` as an ARRAY, with a matching `@JsModule("./rival-charter.json")` accessor mirroring `KingdomGovernment.kt`, plus a schema and a `JsonSchemaValidator` task like every other catalog.

---

## 4. UI Design

### 4.1 Status section — Trade Agreements board (recommended tab)

**No new nav entry.** The rival band status belongs on **`MainNavEntry.TRADE_AGREEMENTS`** (`src/jsMain/resources/applications/kingdom/sections/trade-agreements/page.hbs`) — the board that already lists groups + attitude/standing and (per the sibling plan) the Rival Realms standings. A charter party reads naturally next to the faction it was chartered by. Add a new `<section>` after the Groups / Rival Realms sections.

Each band row shows what the players care about — **where it is, what it's after, and how close** — plus a GM-only dial column:

```hbs
<section>
  <h2>{{localizeKM "kingdom.rivalCharter.title"}}
    {{#if isGM}}<span class="km-header-right-align">
      <button type="button" data-action="add-rival-charter" {{#if rivalCharter.atCap}}disabled
        data-tooltip="{{localizeKM "kingdom.rivalCharter.capReached"}}"{{/if}}><i class="fa-solid fa-plus"></i></button>
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
      <tr {{#if inactive}}class="km-row-inactive"{{/if}}>
        <td>{{band}} <span class="km-tag">{{statusLabel}}</span>{{#if queuedCount}}
          <i class="fa-solid fa-list-ol" data-tooltip="{{localizeKM "kingdom.rivalCharter.queued" count=queuedCount}}"></i>{{/if}}</td>
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
    val statusLabel: String        // localized kingdom.rivalCharter.status.*
    val inactive: Boolean          // retired/joined -> greyed row, no dials (§2.4)
    val positionLabel: String      // native hex label or "off-map"
    val objectiveLabel: String     // target hex label or "—"
    val objectiveKindLabel: String // localized kind
    val etaTurns: Int?             // ceil(distanceToObjective / pace); null when idle
    val queuedCount: Int           // remaining GM-authored agenda entries (§2.6)
    val arrivals: Int
    // null for players — the template gate is layout, not concealment.
    val aggression: Int?
    val aggressionMax: Int?
}

@JsPlainObject
external interface RivalCharterContext {
    val rows: Array<RivalCharterRowContext>
    val isGM: Boolean
    val atCap: Boolean             // activeRivalBandCount >= MAX_RIVAL_CHARTER_PARTIES (§2.5)
}
```

GM-only numbers are **blanked, not merely hidden**: `aggression = if (isGM) party.aggression ?: 0 else null` (same for `aggressionMax`), mirroring `ExpeditionsContext` — `dc = if (isGM) exp.dc else 0`, `accruedInjuries = if (isGM) exp.accruedInjuries else emptyArray()`, `gmNotes = if (isGM) exp.gmNotes else ""`, under the comment *"GM-only fields are blanked for the player-facing read-only board (no info leak)."* The kingdom context is assembled client-side (`val isGM = game.user.isGM` in `KingdomSheet.kt`) from data the player already owns, so this is consistency with a sibling board rather than an authorization boundary — but leaving the escalation clock sitting in a player's own DOM for no reason is not the pattern this codebase established.

`objectiveLabel` / `etaTurns` stay **public** on purpose, matching `ExpeditionsContext`'s deliberately public `destinationLabel`: the whole point of the feature is that players can see the rival closing in on a prize and choose to race it. A band the GM wants entirely hidden uses `visibleToPlayers = false`, which strips the row wholesale.

Built in `KingdomSheet.kt` beside the existing group/rival-realm context assembly: map `kingdom.rivalCharterParties` (resolving labels via the same `formatHexKeyLabel` / `kingmaker.region.hexes` reads the engine uses, defensively) → `RivalCharterRowContext`. Rows with `visibleToPlayers == false` are **stripped when `!isGM`** (same visibility split as `RawWarThreat`/expeditions).

### 4.3 GM dialog — `ModifyRivalCharterParty.kt`

A small Foundry `FormApp`/dialog (pattern of `AddWarThreat.kt` / `ModifyFactionStanding.kt`) for add/edit:

- **Band name** (text), **members** blurb (text), **Status** select (§2.4), and **Level vs. Party** (signed number ⇒ `levelOffset`; the confrontation budget is `partyLevel + levelOffset` clamped to 1..20, §2.6)
- **Faction** (select from `kingdom.groups` names → `factionRef`; blank = unaffiliated)
- **Current hex** (text/hex-key input, or a picker seeded from `ExpeditionDestinationOptions` — reuse the destination builder) and **Objective queue** (textarea, one hex key per line ⇒ `agenda`, §2.6)
- **Pace** (number, hexes/turn; default 1) and **Pause movement** (checkbox)
- **Aggression threshold** (number; blank = confrontation disabled)
- **Player-visible** (checkbox)
- A **"Pitax Chartists" seed template** button that prefills a plausible band.

On save: upsert into `kingdom.rivalCharterParties`, `actor.setKingdom(kingdom)` — an **add** first checks `activeRivalBandCount(...) < MAX_RIVAL_CHARTER_PARTIES` and otherwise warns and bails (§2.5). Wired via `data-action="add-rival-charter" | edit-rival-charter | delete-rival-charter` handlers in `KingdomSheet._onClickAction`, GM-gated, in a `buildPromise{}` block.

### 4.4 i18n namespace

All keys nested under `pf2e-kingmaker-tools` → `kingdom.rivalCharter.*` in **all eight catalogs** — `lang/de.json`, `en`, `fr`, `it`, `pl`, `pt-BR`, `ru`, `zh-Hans`. Parity is **enforced by CI**: `.github/workflows/test.yml` runs `python3 scripts/check_i18n_keys.py --all`, whose check 5 requires identical nested key sets (and identical placeholder names) across every `lang/*.json`; the eight files currently hold exactly 4,623 keys each. An English-only addition fails the build.

Two shape rules, both load-bearing:

- **Nested objects, never flat-dotted** — the existing guard.
- **Object values only, never JSON arrays.** `lang/en.json` contains **zero** array values across all 4,623 keys; nothing in the catalog pipeline is known to handle them. So a headline "pool" is not a JSON array indexed at runtime — it is *n* individually named literal keys (`advance1`, `advance2`, …), and `headlineTemplateIndex`'s result is mapped to one of them by the exhaustive `when` in `headlineKey` (§3.2). No key is ever built by string concatenation, so the guard can see all of them.

```json
{"kingdom": {"rivalCharter": { … }}}
```

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
    "capReached": "Two rival charter parties are already in the field.",
    "queued": "{{count}} scripted objective(s) still queued.",
    "kind": { "unexplored": "Unexplored hex", "unclearedLair": "Uncleared lair",
      "contestedClaim": "Contested claim", "landmark": "Landmark" },
    "status": { "active": "In the field", "defected": "Changed banner",
      "retired": "Recalled", "joined": "Joined you" },
    "headline": {
      "advance1": "{{band}} presses toward {{place}}.",
      "advance2": "Scouts spot {{band}} moving on {{place}}.",
      "advance3": "{{band}} is {{turns}} turn(s) from {{place}}.",
      "arriveUnexplored1": "{{band}} reached {{place}} ahead of you.",
      "arriveLair1": "{{band}} cleared the lair at {{place}} first.",
      "arriveContested1": "{{band}} planted {{faction}}'s banner on {{place}}.",
      "arriveLandmark1": "{{band}} claimed {{place}} — {{faction}} got there first.",
      "idle1": "{{band}} has gone to ground; no sign of movement."
    },
    "rumor": { "sighted": "A {{faction}} banner was seen making for {{place}}." },
    "discovery": { "name": "{{band}} was here",
      "playerText": "Someone has already been through here: cold campfires, a stripped cache, a scrap of banner." },
    "colocation": { "note": "{{band}} is in this hex right now." },
    "lifecycle": { "title": "{{band}} — the charter changes",
      "retire": "Recall Them", "defect": "Change Banner", "join": "They Join You",
      "dismiss": "Leave Be",
      "retired": "{{band}} has been recalled; their charter is spent.",
      "defected": "{{band}} now flies {{faction}}'s colors.",
      "joined": "{{band}} has thrown in with you." },
    "arrivalOffer": { "title": "{{band}} reached {{place}} first",
      "cede": "Let Them Have It", "race": "Race Them", "confront": "Confront",
      "dismiss": "Narrate Only" },
    "confrontationOffer": { "title": "{{band}} grows bold",
      "warThreat": "Raise War Threat", "encounter": "Spawn Encounter", "dismiss": "Dismiss" },
    "dialog": { "add": "Add Rival Charter Party", "edit": "Edit Rival Charter Party",
      "band": "Band Name", "members": "Roster", "levelOffset": "Level vs. Party",
      "faction": "Chartered By", "status": "Status", "agenda": "Objective Queue (one hex key per line)",
      "currentHex": "Current Hex", "pace": "Pace (hexes / turn)", "pauseMovement": "Pause Movement",
      "aggressionThreshold": "Aggression Threshold", "visibleToPlayers": "Visible to Players",
      "seedPitax": "Seed: Pitax Chartists" }
  },
  "turnGazette": { "rivalCharterMove": "{{list}}" }
}
```

A commonTest case asserts `headlinePoolSize(kind)` matches the number of `headline.*` keys the kind owns, so adding a fourth `advance` line to the catalogs without widening the `when` is caught by the build rather than by a GM seeing the same headline twice.

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

Most turns, a band's move is **public gazette flavor with no button** ("presses toward the Temple of the Elk"). Every moment that would alter shared state becomes a **GM-confirmed offer** (`km-offer-*` handlers in `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/ChatButtons.kt`), **never auto-applied**. A rival reaching a hex first is **an offer + a gazette line, not a silent `kingmaker.state` write**.

Five offer ids in total: `km-offer-rival-reached-target` (§5.2), `km-offer-rival-confrontation` (§5.2), `km-offer-rival-lifecycle` (§2.4), `km-offer-rival-encounter` (§5.4) and `km-offer-rival-rumor` (§5.5).

### 5.1 Gazette headlines (public, no button)

Each moving band emits one headline (chosen by `headlineTemplateIndex`), appended to the End-Turn gazette (see §4.4 pool). Pure fiction, no interaction. Idle/paused bands emit nothing (or the "gone to ground" line, at GM discretion).

### 5.2 Offer cards enumerated

| Trigger | Offer id | Buttons | Handler behavior (`ChatButtons.kt`) |
|---------|----------|---------|-------------------------------------|
| Band **arrived** at a target the players had not taken (`arrivedAt != null` and `lastArrivalTurn != currentTurn`) | `km-offer-rival-reached-target` | **[Let Them Have It]** · **[Race Them]** · **[Confront]** · **[Narrate Only]** | GM-gated, idempotent via `lastArrivalHexKey`/`lastArrivalTurn`. **[Let Them Have It]** posts a gazette-style loss line, **does not touch `kingmaker.state`** (the GM narrates the ceded hex), and writes the discovery-on-arrival hex note (§5.5 stage 3); bumps `arrivals` (already done in the tick) and clears the offer. **[Race Them]** opens a lightweight time-pressure: prefills `AddQuest`/a campaign clock ("Reach {{place}} before {{band}} entrenches", short deadline) so the players still have a shot. **[Confront]** routes to the confrontation flow below (bumps aggression to threshold, immediately posts `km-offer-rival-confrontation`). **[Narrate Only]** writes the discovery-on-arrival hex note (§5.5 stage 3) and posts nothing further. |
| Band **aggression crossed its threshold** this turn (`confrontation == true`) **or** the GM clicked **[Confront]** above | `km-offer-rival-confrontation` | **[Raise War Threat]** · **[Spawn Encounter]** · **[Dismiss]** | GM-gated, idempotent via `confrontationOffered`. **[Raise War Threat]** opens `AddWarThreat` prefilled `prefillName = t("kingdom.rivalCharter.confrontationOffer.title", {band})`, `prefillEnemyFaction = factionRef` — the **exact body of the existing `km-offer-war-threat` handler** in `ChatButtons.kt`: appends the threat, calls `recalculateWarPressure`, `setKingdom`. **[Spawn Encounter]** opens a prefilled encounter/quest hook budgeted off `rivalEffectiveLevel(game.getAveragePartyLevel(), band.levelOffset)` (§2.6) — the GM runs it live. **[Dismiss]** sets `confrontationOffered = true` so it won't re-fire until aggression is spent, posts nothing. |
| **Chapter beat** — a campaign clock expired this tick (§2.4) | `km-offer-rival-lifecycle` | **[Recall Them]** · **[Change Banner]** · **[They Join You]** · **[Leave Be]** | GM-gated, idempotent on `status`. See the §2.4 button table. |
| Party token stands in an active band's `currentHexKey` (§5.4) | `km-offer-rival-encounter` | **[Queue Encounter Here]** · **[Just a Sighting]** · **[Nothing Happens]** | GM-gated, idempotent per `(bandId, hexKey, turn)`. Writes a `RawHexContent` row at the shared hex; **[Queue Encounter Here]** additionally sets `pendingEncounter = true`. Never rolls or spawns anything. |
| Band selected a NEW objective this tick (§5.5 stage 1) | `km-offer-rival-rumor` | **[Plant the Rumor]** · **[Keep It Quiet]** | GM-gated, idempotent per `(bandId, objectiveHexKey)`. **[Plant the Rumor]** appends a `RawRumor` to the camping actor's `CampingData.rumors`; **[Keep It Quiet]** does nothing. |

Two of these need state the §2.1 interface must carry, or their idempotency claim is empty:
add `var lastEncounterOfferTurn: Int?` (guards `km-offer-rival-encounter` per band per turn — the
band cannot leave the hex mid-turn, so a turn stamp is the whole key) and
`var rumoredObjectiveHexKey: String?` (guards `km-offer-rival-rumor`: one rumor per objective, so
re-selecting the same objective after a failed approach does not re-plant it). Both nullable, both
following `RawWarThreat.offerConsumed`'s "compare `== true` / `== turn`, null means not-yet"
convention. Field count becomes 22.

All five follow the established idempotent, GM-gated, `button.dataset["…"]`-driven shape — see the `km-offer-war-threat`, `km-offer-war-threat-arrival` and `km-offer-diplomacy-quest` handlers in `ChatButtons.kt`, and in particular `km-offer-war-threat-arrival`'s `queueEncounter` action, which is the exact precedent for leaving durable state behind an offer (`hexContent.pendingEncounter = true` + `offerConsumed`). All carry `data-kingdom-actor-uuid` for actor resolution and `data-band-id` to target the right party. New offer templates live in `resources/chatmessages/rival-charter-*.hbs`.

### 5.3 Digest, not spam

Offers are collected during `performEndTurn` and posted as **at most one whisper per offer type per turn** listing every band that crossed a threshold (mirrors the `newlyTriggeredThreats` digest block in `TurnWizardApplication.kt`), so two bands reaching prizes produce one card with two button rows, not two cards. With the cap at two bands (§2.5), the worst End Turn is five cards (one per offer id in §5.2) of at most two rows each.

### 5.4 Co-location — the PCs and the band in the same hex

**Detection.** `getPartyCurrentHexKey(game, actor, camping)` (`camping/CampingUtils.kt`) already resolves the party token's hex key from its position on the hexploration scene — `(offset.i * 1000 + offset.j).toString()`, the same key space `currentHexKey` uses. Co-location is `partyHexKey == band.currentHexKey` for any band where `isRivalBandActive(band.status)`. It is checked in two places, both of which already have that key in hand:

- **Primary — `rollRandomEncounter`** (`camping/RandomEncounters.kt`), which calls `getPartyCurrentHexKey` before any table is consulted (it already reads `kingdom.hexContents` there for the `suppressesEncounters` override). A `checkRivalColocation(game, actor, camping)` call at that point costs one already-computed hex key and a scan of at most two bands, and it fires exactly when the map is about to produce something in the hex the party is standing in.
- **Secondary — `performEndTurn`**, so a table that never rolls camping encounters still gets the offer once a turn.

**Decision: (b) a GM note plus an optional pending-encounter marker. (a) encounter-table injection is REJECTED.**

Why (a) is rejected: **the module does not own a weighted encounter table to inject into.** Per-region encounter tables are Foundry `RollTable` **documents referenced by UUID** — `camping/dialogs/RegionEncounterTables.kt` stores eight per-category table UUIDs per region (combat, rp, rumor, merchant, disease, faction, weather, lore), and `rollCuratedEncounter` picks a category from the curator weights and then draws from whichever table that UUID points at. "Injecting a weighted entry while the band occupies the hex" therefore means **mutating a GM-owned RollTable document** and remembering to unmutate it later. That is (i) a silent write to shared world state, which §6.2 forbids outright; (ii) an embedded-document write that throws `"User X lacks permission"` for any non-GM client, per the embedded-doc-writes lesson; and (iii) an information leak, since a player who opens the region's table sees the rival before the fiction reveals it. A temporary mutation with a cleanup path is also exactly the kind of state that survives a crash and quietly poisons a campaign's encounter table forever.

Chosen (b), concretely: co-location posts **one** GM-whispered `km-offer-rival-encounter`, idempotent per `(bandId, hexKey, turn)`:

| Button | Effect |
|---|---|
| **[Queue Encounter Here]** | Upserts a `RawHexContent` row at the shared hex — `type = HexContentType.CUSTOM.value`, `visibility = HexContentVisibility.DISCOVERED.value`, `name = t("kingdom.rivalCharter.discovery.name")`, `playerText = t("kingdom.rivalCharter.colocation.note")`, `pendingEncounter = true` — the exact durable-state shape `km-offer-war-threat-arrival`'s `queueEncounter` action uses. It then shows up in the Session Prep dashboard's Pending Encounters list (`SessionPrepView.buildPendingEncounters` filters on `pendingEncounter == true`) and is cleared from the sheet once run. |
| **[Just a Sighting]** | The same row **without** `pendingEncounter` — the players get a described sighting, nothing is queued. |
| **[Nothing Happens]** | Posts nothing; consumes the offer for this turn. |

**No encounter is ever rolled, weighted, or spawned automatically.** The band's `levelOffset`-derived budget (§2.6) only ever reaches a die through the GM-clicked **[Spawn Encounter]** button in §5.2.

### 5.5 Narration chain — rumor → gazette → discovery-on-arrival

"The rivals got there first" has to reach the players three different ways, or it is just a line in a recap nobody reads.

**Stage 1 — rumor (before).** On the tick where a band selects a *new* objective, the digest carries a `km-offer-rival-rumor` row. **[Plant the Rumor]** appends a `RawRumor` to the camping actor's store (`CampingData.rumors: Array<RawRumor>?` in `camping/CampingData.kt`; `RawRumor` and the `CampingData.rumorList()` reader live in `camping/EncounterCuratorData.kt`) and persists via `campingActor.setCamping(camping)`:

```kotlin
RawRumor(
    text = t("kingdom.rivalCharter.rumor.sighted", recordOf("faction" to factionLabel, "place" to objectiveLabel)),
    isQuestHook = false,
    questTemplateId = null, questTemplateName = null,
    location = objectiveLabel,
    sourceRegion = camping.currentRegion,
    isConverted = false, convertedQuestId = null,
)
```

It then surfaces through the encounter curator's existing RUMOR category — so the news reaches the table *in play*, days of world time before the band arrives, and the players get a real chance to act on it. **[Keep It Quiet]** does nothing (some races should be a surprise).

**Stage 2 — gazette (during).** The §5.1 headline: public, no button, once per moving band per turn. This is the running commentary that makes the ETA column mean something.

**Stage 3 — discovery-on-arrival (after).** The arrival offer's **[Let Them Have It]** and **[Narrate Only]** buttons (the two outcomes where the band keeps the ground; **[Race Them]** and **[Confront]** leave the hex contested and write nothing) upsert a `RawHexContent` row at the arrival hex:

```kotlin
RawHexContent(
    id = <uuid>, hexKey = arrivalHexKey,
    type = HexContentType.CUSTOM.value,
    visibility = HexContentVisibility.DISCOVERED.value,
    name = t("kingdom.rivalCharter.discovery.name", recordOf("band" to band.name)),
    playerText = t("kingdom.rivalCharter.discovery.playerText"),
    gmNotes = "<band name> arrived turn <n> (objectiveKind: <kind>)",
)
```

Idempotent by hex key — a second arrival at the same hex updates the row rather than stacking. `HexContentSync` then renders it on the map and `HexContentManager` lists it, so when the PCs finally walk into that hex **months later** they *find* the cold campfire instead of being told about it. This is the only hex write in the feature, it is on the kingdom's own `hexContents` array (never `kingmaker.state`), and it happens only on a GM click.

---

## 6. Interactions With Existing Systems

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Native hex map (READ-ONLY)** | `kingdom/ExpeditionDestinations.kt`, `kingdom/map/KingmakerHexGridProvider.kt`, `kingdom/data/RawHexContent.kt`, `com.foundryvtt.kingmaker.*` | `buildRivalMapSnapshot` reuses the same read *pattern* as `buildExpeditionDestinationOptions`: `kingmaker.state.hexes` for `.claimed`/`.explored`/`.cleared`, `kingmaker.region.hexes.contents` for `.name` and full-map coverage, plus `expeditionHexDistance`'s `hexes.find { it.key == intKey }?.cube` read — all `runCatching`-guarded. (`buildExpeditionDestinationOptions` itself reads neither `.commodity` nor content nor `.cube`.) The rival **never writes `kingmaker.state`** — got-there-first is an offer, not a mutation. |
| **Companion Expeditions (mirror idioms)** | `kingdom/ExpeditionDestinations.kt`, `commonMain/.../companion/ExpeditionTravel.kt` (`hexCubeDistance`, `expeditionTravelDays`, `formatHexKeyLabel`), `kingdom/data/RawCompanionExpedition.kt`, `kingdom/ExpeditionLaunch.kt` (the concurrency-cap precedent), `DailyTickHooks.kt` | Reuses the cube-distance + travel-day + hex-label helpers verbatim. The band is the **adversarial mirror** of a companion expedition, deliberately on the **opposite tick** (monthly here vs. daily there) so the two never collide. `DailyTickHooks` untouched. |
| **Army & War Pressure** | `kingdom/data/RawWarThreat.kt`, `kingdom/dialogs/AddWarThreat.kt`, `kingdom/ChatButtons.kt` (`km-offer-war-threat`), `recalculateWarPressure` | Confrontation reuses `AddWarThreat` prefilled by `factionRef` — the same plumbing behind `km-offer-war-threat`. The band never spawns an army token; it *offers* a threat. |
| **Hex content (WRITE — one row, GM-clicked)** | `kingdom/data/RawHexContent.kt`, `kingdom/dialogs/HexContentManager.kt`, `kingdom/map/HexContentSync.kt`, `kingdom/SessionPrepView.kt` | The **only** map-adjacent write in the feature: the discovery-on-arrival note (§5.5 stage 3) and the co-location marker (§5.4) upsert a `RawHexContent` row on the kingdom's own `hexContents` array — never `kingmaker.state` — and only from a GM button. `HexContentSync` renders it; `SessionPrepView.buildPendingEncounters` picks up `pendingEncounter == true`. No `lootManifest`, `manifestAwarded` or `HexState.cleared` write ever happens — see §6.2. |
| **Camping / encounter curator** | `camping/CampingUtils.kt` (`getPartyCurrentHexKey`), `camping/RandomEncounters.kt`, `camping/CampingData.kt` (`rumors`), `camping/EncounterCuratorData.kt` (`RawRumor`, `rumorList`) | Co-location detection reuses `getPartyCurrentHexKey` verbatim (§5.4); the rumor stage appends to the curator's existing `CampingData.rumors` store (§5.5 stage 1). The per-region `RollTable` UUIDs in `camping/dialogs/RegionEncounterTables.kt` are **read about, never written** — see the rejected option in §5.4. |
| **Faction & Diplomacy Tracker** | `kingdom/data/RawGroup.kt`, `data/kingdom/FactionRelations.kt` | `factionRef → RawGroup.name` is the charter link; `RawGroup.atWar` biases aggression/headlines. A confrontation naturally interoperates with the tracker's own threshold hooks once the war threat exists. |
| **Turn ticking** | `kingdom/TurnTickingEngine.kt` | New `rivalCharterParties` + `rivalMapSnapshot` in, `rivalCharterParties` + `rivalPartyMoves` out on `TickResult`; movement runs in the pure monthly `tick()`. |
| **End-turn flow** | `kingdom/dialogs/TurnWizardApplication.kt` (`runKingdomTurnTick`, `performEndTurn`), `kingdom/forecast/ForecastAdapter.kt` | `runKingdomTurnTick` — the mandatory single chokepoint, never `tick()` directly — builds the map snapshot (the one impure read) and passes it to `tick()`, so its three callers (commit, Turn Wizard preview, forecast) stay in parity. `performEndTurn` then persists `kingdom.rivalCharterParties`, feeds `rivalPartyMoves` into both `formatTurnGazette` calls (GM `turnNotes` **and** player `playerTurnNotes` — headlines are public), and posts the §5 offer digests. |
| **Gazette / Turn History** | `kingdom/TurnHistory.kt` (`formatTurnGazette`) | Gains a `rivalCharterMoves: List<String> = emptyList()` param → public "Rival Bands" gazette lines. Slotted beside the existing optional params (`caravanEvents`, `campaignClocks`, `expeditionChronicle`, `battleDefeats`, …) and passed to both GM and player gazette calls. |
| **Rival Realms Scoreboard (sibling)** | `docs/plans/2026-07-09-plan-rival-realms.md` | Complementary, **not coupled**. A charter party's `arrivals` could optionally feed a linked realm's `fame`/`size` — deliberately **out of scope** (a future `km-offer` bridge). See delineation below. |
| **Daily tick** | `kingdom/DailyTickHooks.kt` | **No interaction** — the band moves monthly only. |

### 6.1 Delineation from the three neighbors (explicit)

- **vs. Rival Realms Scoreboard** — A *rival realm* is an **abstract kingdom scoreboard**: `size`/`fame`/`armyCount` numbers that creep up, with **no position on the map**. A *rival charter party* is a **band with a concrete hex position and an objective** it walks toward, competing over the **same map the players explore**. "Pitax is Size 14" (realm) vs. "the Pitax Chartists are 2 hexes from the Temple of the Elk" (charter party). Both may emit off-screen gazette headlines — that shared *idiom* is the only overlap.
- **vs. Companion Expeditions** — Companion expeditions are **yours**: *you* dispatch them, they resolve on the **daily** world clock with degree-of-success rolls, and they grant **your** companions XP/loot/influence. A charter party is **theirs**: adversarial, GM-managed, resolves on the **monthly** kingdom tick, deterministic movement with **no simulated rolls**, and its "reward" is *your loss* (a hex you wanted). The charter party reuses the expedition system's *hex/travel helpers* but is otherwise its structural opposite.
- **vs. Faction Agenda Engine** — **Independent in v1, with one named future bridge.** A band's `factionRef` is a *display and prefill* link only: an agenda **never** sets `objectiveHexKey`, `pace`, `agenda` or `aggression`, and a band's `arrivals` **never** advances an agenda's `progress` clock. The two systems can be installed together and will simply narrate the same faction from two angles — the agenda engine says what Pitax is *doing to other factions* (`RawFactionAgenda.goalId`/`progress`/`archetype`, ticking on `RawGroup.agenda`), this plan says where Pitax's *band is standing on the map*. Keeping them uncoupled is what lets either ship alone; coupling them would mean an agenda move could silently move a band, which is the one thing §6.2 forbids.
  **The future bridge, named now so it is not reinvented twice:** a sixth agenda move `dispatch-charter-party` with `effect = "charter-party"`, alongside the existing `"clock" | "standing-delta" | "war-threat" | "quest" | "army"` effects. Like every other agenda effect it resolves to a **GM-confirmed offer**, not an application — `km-offer-rival-charter-dispatch`, with **[Send a Band]** (creates a band prefilled from the acting faction, subject to `MAX_RIVAL_CHARTER_PARTIES`) and **[Re-target]** (pushes the agenda's target hex onto an existing band's `agenda` queue, §2.6). **Ownership if both are installed: the agenda engine owns that offer**, because it owns the decision to act; this feature owns everything downstream of a band existing (movement, arrival, confrontation, lifecycle). The reverse direction — `arrivals` feeding agenda `progress` — stays out of scope for the same reason the rival-realm bridge stays out (§9): an automatic cross-feature feedback loop is exactly the kind of hidden simulation the Executive Summary promises this feature does not contain.

### 6.2 Explicit OUT-OF-SCOPE

- **No simulated party.** No rival statblock, inventory, HP, or resolved combat. Confrontation hands the GM a prefilled war-threat/encounter and stops.
- **No silent map writes.** The rival never sets `claimed`/`explored`/`cleared` on `kingmaker.state`. Every map consequence is a GM-confirmed offer the GM applies (or narrates) by hand.

  **Per hex-content kind, explicitly** (the card asked all three; the answer is the same each time, but it should be on the record rather than inferred from a blanket sentence):

  | May a rival…? | v1 answer | The state it would have touched |
  |---|---|---|
  | claim a kingdom hex | **Never.** Not even by offer. | `HexState.claimed` |
  | strip a landmark's loot | **No.** The GM narrates an emptied cache; the manifest stays intact for whoever the GM decides actually gets it. | `RawHexContent.lootManifest` / `manifestAwarded` / `manifestAwardedTurn` |
  | clear a lair | **No.** A `unclearedLair` arrival is a headline and an offer; the lair stays uncleared until a GM (or the party) clears it. | `HexState.cleared` |

  The card's premise named `kingdom/dialogs/HexContentManager.kt` and `kingdom/map/HexContentSync.kt` as "hex content state the rivals mutate — via offers". **Both files exist and are deliberately used for exactly one thing: writing the discovery/co-location NOTE (§5.4, §5.5), never a state flag.** The reason is that loot and cleared-status are *rewards already promised to the players*: silently marking a manifest awarded means the party finds an empty room with no explanation, and marking a lair cleared removes an encounter the GM may have built a session around. A narrated loss costs the players the fiction; a state write costs them content.

  If Gregory wants the mutation after all, the cheapest concrete version is a third arrival button — **[Strip the Loot]** on `km-offer-rival-reached-target`, setting `manifestAwarded = true` (and `manifestAwardedTurn = currentTurn`) on the arrival hex's `RawHexContent` so the PCs find it emptied, with the existing discovery note explaining why. That is one button and one field; it is left out of v1 on purpose, not by oversight.
- **No pathfinding / terrain.** Movement is cube-distance step-down; the band does not route around rivers, armies, or difficult terrain.
- **No daily tick.** Monthly only; `DailyTickHooks` untouched. This overrides the commissioning card's explicit daily-clock instruction — see the deviation note at the top of §3.5 and Open Question 0.
- **No rival-vs-rival behavior.** Bands do not interact with each other or with rival realms (that convergence is a future bridge, not v1).
- **No player-facing band management.** Players see a read-only status row (and optional marker); all dials are GM-only, and GM-only numbers are `null` in the player context rather than merely hidden by the template (§4.2).
- **No automatic lifecycle.** A band never retires, defects or joins on its own; §2.4 is four buttons on a GM-whispered card.
- **Map marker sync is deferred** (its own GM-gated sub-phase) — the status table is the v1 surface.

---

## 7. Test Plan

### 7.1 commonTest — pure core (`data/kingdom/RivalCharterPartyTest.kt`, JVM-less)

| Test | Assertion |
|------|-----------|
| `hexDistance_matchesCubeMetric` | Distance is the standard cube metric; symmetric; 0 for same hex. |
| `chooseObjective_fartherLandmarkBeatsNearUnexplored` | Landmark (value 40) **2 hexes farther** than an unexplored hex (value 10) → the landmark wins at default weights (`40 − 5·d` vs `10 − 5·d`). The regression guard for the scalar score: a lexicographic `(distance, -value, key)` ordering fails this test. |
| `chooseObjective_distanceStillWinsBeyondTheWeight` | The same landmark **7+ hexes** farther → the near unexplored hex wins. Distance is not ignored, it is priced. |
| `chooseObjective_deterministicTiebreak` | Two targets with equal score → the lower `hexKey` is chosen, every run. |
| `chooseObjective_agendaHeadWinsOutright` | Non-empty agenda whose head is a live candidate → that hex, regardless of score. |
| `chooseObjective_agendaHeadSkippedWhenTaken` | Agenda head absent from the snapshot → dropped, next key tried in the same call; empty queue → falls back to scoring. |
| `chooseObjective_nullWhenNoCandidates` | Empty snapshot or no position → null (idle). |
| `advanceRival_countsDownDistanceWithoutMoving` | distance 5, pace 2 → `distanceToObjective == 3`, `currentKey`/`currentCube` **unchanged**, no arrival. |
| `advanceRival_repeatedTurnsReachZero` | distance 5, pace 2, three consecutive turns → 3, 1, 0 then arrival. The guard against the non-functional model where distance is recomputed from a stationary position and never decreases. |
| `advanceRival_arrivesWhenDistanceReachesZero` | distance 2, pace 2 → `arrivedAt == objective`, position snaps to the objective, objective **and** distance cleared. |
| `advanceRival_reTargetsWhenObjectiveTakenByPlayers` | Objective absent from this turn's snapshot (players claimed it) → chooseObjective runs, no got-there-first offer. |
| `advanceRival_pausedNoMovementNoAggression` | `paused=true` → state unchanged, "idle" move. |
| `advanceRival_aggressionRisesNearClaimedTerritory` | Band within `RIVAL_PROXIMITY_HEXES` of a claimed hex → aggression += `RIVAL_PROXIMITY_AGGRESSION`. |
| `advanceRival_confrontationOnThresholdCross` | aggression 4→5 with threshold 5 → `confrontation=true`; 5→6 → false (already past). |
| `advanceRival_deterministicSameInput` | Same `(state, snapshot, turn)` twice → identical `RivalMove` (no RNG). |
| `isRivalBandActive_lifecycleStates` | null/`"active"`/`"defected"` → true; `"retired"`/`"joined"` → false. |
| `activeRivalBandCount_ignoresRetiredAndJoined` | Two active + two retired → 2, i.e. the cap counts competitors, not archive rows. |
| `maxRivalCharterParties_isTwo` | `assertEquals(2, MAX_RIVAL_CHARTER_PARTIES)` — the §2.5 decision on the record (mirrors `ExpeditionsContextTest`'s expedition-cap assertion). |
| `rivalEffectiveLevel_clampsToLegalRange` | `(19, +4) → 20`; `(2, −4) → 1`; `(5, null) → 5`. |
| `headlineTemplateIndex_deterministicInRange` | Same `(turn, bandId, kind)` → same index, always in `[0, poolSize)`; `poolSize=0 → 0`; a negative intermediate hash still yields a non-negative index. |
| `headlineKey_coversEveryPoolIndex` | For every headline kind, `(0 until headlinePoolSize(kind)).map { headlineKey(kind, it) }` is distinct and matches the literal keys in §4.4 — the guard against a catalog line the `when` cannot reach. |

### 7.2 jsTest — adapter + integration (`kingdom/RivalCharterPartyEngineTest.kt`)

| Test | Assertion |
|------|-----------|
| `buildMapSnapshot_defensiveOnMissingModule` | With `kingmaker` absent, `buildRivalMapSnapshot` returns an empty snapshot (no throw). |
| `buildMapSnapshot_classifiesKinds` | Unexplored / explored-unclaimed / `RUIN`-with-`cleared != true` / `LANDMARK` hexes are bucketed into `unexplored` / `contestedClaim` / `unclearedLair` / `landmark` with the right `value`; a `MERCHANT` or `WORKSITE` row is **not** a lair; a `RUIN` on a `cleared == true` hex is not a prize. |
| `advanceAllRivalParties_advancesAndHeadlines` | A band with a distant objective advances `pace`, emits an "advance" headline key. |
| `advanceAllRivalParties_arrivalEmitsGotThereFirstOffer` | Reaching a target sets `RivalPartyMove.gotThereFirst`; `arrivals` incremented; idempotent across a repeated same-turn call. |
| `advanceAllRivalParties_confrontationOffer` | Aggression crossing threshold sets `RivalPartyMove.confrontation`; below → null. |
| `tick_returnsRivalCharterPartiesAndMoves` | `TurnTickingEngine.tick(rivalCharterParties=…, rivalMapSnapshot=…)` populates `TickResult.rivalCharterParties` + `rivalPartyMoves`. |
| `runKingdomTurnTick_buildsSnapshotForEveryCaller` | Extends the existing parity guard in `src/jsTest/kotlin/at/posselt/pfrpg2e/kingdom/TurnWizardApplicationTest.kt`: `runKingdomTurnTick` on a kingdom with one band returns non-empty `rivalCharterParties`/`rivalPartyMoves` **without** the caller passing a snapshot — so the Turn Wizard preview and `ForecastAdapter` cannot silently tick with `rivalMapSnapshot = null`. |
| `tick_previewCommitParity` | Two `runKingdomTurnTick` calls on the same kingdom state + turn → byte-identical parties + moves. |
| `inactiveBand_doesNotTick` | `status = "retired"` → record copied through unchanged, no `RivalPartyMove`, no aggression change. |
| `addBand_refusedAtCap` | Two active bands present → the add path returns false / warns; a third is not appended. Editing an existing band still succeeds. |
| `gazetteIncludesRivalCharterHeadlines` | `formatTurnGazette(rivalCharterMoves=…)` output contains the interpolated headline lines. |
| `unlinkedFaction_movesButAtWarFalse` | `factionRef` with no matching group → still moves; `atWar` treated false; no crash. |
| `colocation_offersOncePerBandHexTurn` | Party hex == band `currentHexKey` → one `km-offer-rival-encounter`; a repeat check in the same turn adds nothing. |
| `colocationQueueEncounter_writesPendingHexContent` | **[Queue Encounter Here]** upserts a `CUSTOM`/`DISCOVERED` `RawHexContent` with `pendingEncounter = true`; `SessionPrepView.buildPendingEncounters` then lists it. |
| `arrivalCede_writesDiscoveryNote` | **[Let Them Have It]** upserts the discovery `RawHexContent` at the arrival hex, idempotent on a second arrival; **[Race Them]** writes nothing. |
| `rumorOffer_appendsToCampingRumors` | **[Plant the Rumor]** appends one `RawRumor` to `CampingData.rumors` with the interpolated text and the objective label as `location`; **[Keep It Quiet]** leaves the array untouched. |
| `lifecycleOffer_retireStopsTicking` | **[Recall Them]** sets `status = "retired"` and clears objective/distance; the next tick emits no move for that band. |
| `migration66_seedsEmptyArray` | KingdomData without `rivalCharterParties` → after `Migration66` → non-null, length 0. |
| `migrationChain_extendedTo66` | `MigrationChainTest`'s range assertion becomes `(17..66)` and still passes (contiguity guard). |

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
12. Reload the world → bands, positions, objectives, aggression, and arrivals persist. Run `python3 scripts/check_i18n_keys.py --all` → no raw/flat keys, **and cross-language parity passes** (all eight `lang/*.json` carry the new `kingdom.rivalCharter.*` subtree; an English-only addition fails CI).
13. Walk the party token **onto** a band's current hex, then roll a random encounter → a whispered `km-offer-rival-encounter` appears; **[Queue Encounter Here]** leaves a hex-content note that shows up under Session Prep → Pending Encounters.
14. On the turn a band picks a new objective, click **[Plant the Rumor]** → the rumor appears in the camping Encounter Curator's rumor list and can surface through a RUMOR-category encounter.
15. After a got-there-first arrival, click **[Let Them Have It]**, then walk the party into that hex → the discovery note ("someone has already been through here") is on the map, and `kingmaker.state` is still untouched.
16. Expire a campaign clock → `km-offer-rival-lifecycle` appears; **[Recall Them]** greys the row, keeps its `arrivals` tally, and stops it moving on subsequent End Turns.
17. With two bands in the field, open the dialog → **Add** is disabled with the "already in the field" tooltip.

---

## 8. Phasing (Independently Committable)

Each phase is one kanban worker card, ~1–2 days.

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Data model + migration** | `RawRivalCharterParty` interface (20 fields, incl. `status`/`agenda`/`levelOffset`), `KingdomData.rivalCharterParties`, `Defaults` seed, `Migration66` (+ import + registry + `MigrationChainTest` range → `(17..66)`). **No data catalog** — the tunables are `const val`s in Phase 2 (§3.6). | `kingdom/data/RawRivalCharterParty.kt`, `kingdom/KingdomData.kt`, `sheet/Defaults.kt`, `migrations/migrations/Migration66.kt`, `migrations/Migrations.kt`, `jsTest/.../migrations/MigrationChainTest.kt` |
| **2** | **Pure core (commonMain) + tests** | `RivalCharterParty.kt` (`HexCube`, `RivalTarget`, `RivalMapSnapshot`, `RivalPartyState`, `chooseObjective`, `advanceRival`, `RivalMove`, `headlineTemplateIndex`/`headlinePoolSize`/`headlineKey`, `hexDistance`, `isRivalBandActive`, `activeRivalBandCount`, `rivalEffectiveLevel`, and the §3.6 `const val` tunables incl. `MAX_RIVAL_CHARTER_PARTIES`), full `RivalCharterPartyTest`. | `commonMain/.../data/kingdom/RivalCharterParty.kt`, `commonTest/.../data/kingdom/RivalCharterPartyTest.kt` |
| **3** | **jsMain engine + tick integration** | `RivalCharterPartyEngine.kt` (`buildRivalMapSnapshot`, `advanceAllRivalParties`, `RivalPartyMove`), extend `TickResult` + `tick()` params, build the snapshot **inside `runKingdomTurnTick`** (not `performEndTurn`) so commit/preview/forecast stay in parity, wire persistence + gazette into `performEndTurn`, `formatTurnGazette` `rivalCharterMoves` param, jsTest incl. the extended parity guard. | `kingdom/RivalCharterPartyEngine.kt`, `kingdom/TurnTickingEngine.kt`, `kingdom/dialogs/TurnWizardApplication.kt`, `kingdom/forecast/ForecastAdapter.kt` (verify — no signature change if the snapshot is built inside the chokepoint), `kingdom/TurnHistory.kt`, `RivalCharterPartyEngineTest.kt`, `jsTest/.../kingdom/TurnWizardApplicationTest.kt` |
| **4** | **Status UI + GM dialog + i18n** | Rival Charter section on the trade-agreements board, `RivalCharterContext` (GM-only fields nulled for players), `ModifyRivalCharterParty` dialog (Pitax seed, destination-picker reuse, Status select, agenda textarea, cap enforcement), sheet action handlers (GM-gated), the `kingdom.rivalCharter.*` subtree in **all eight `lang/*.json`** — parity is CI-enforced. | `sections/trade-agreements/page.hbs`, `sheet/contexts/RivalCharterContext.kt`, `kingdom/dialogs/ModifyRivalCharterParty.kt`, `sheet/KingdomSheet.kt`, `lang/*.json` (de, en, fr, it, pl, pt-BR, ru, zh-Hans) |
| **5** | **Offer handlers + narration chain + QA** | All five offer handlers (digest, idempotent, GM-gated): `km-offer-rival-reached-target` (4 buttons, two of which write the discovery hex note), `km-offer-rival-confrontation` (3, war-threat reuses `AddWarThreat`, race reuses `AddQuest`/clock, encounter budget via `rivalEffectiveLevel`), `km-offer-rival-lifecycle` (4, §2.4), `km-offer-rival-encounter` (3, §5.4 — incl. the `checkRivalColocation` call in `rollRandomEncounter`), `km-offer-rival-rumor` (2, §5.5 — appends to `CampingData.rumors`). Offer templates, jsTest for offers, manual checklist. | `kingdom/ChatButtons.kt`, `camping/RandomEncounters.kt` (co-location check), `camping/CampingData.kt` (rumor append via `setCamping`), `chatmessages/rival-charter-arrival-offer.hbs`, `chatmessages/rival-charter-confrontation-offer.hbs`, `chatmessages/rival-charter-lifecycle-offer.hbs`, `chatmessages/rival-charter-encounter-offer.hbs`, `chatmessages/rival-charter-rumor-offer.hbs`, `RivalCharterPartyEngineTest.kt` (offer cases) |
| **6 (deferred/optional)** | **Hex-map marker sync** | GM-gated Drawing/Note marker at each band's `currentHexKey`, cleared on delete; players *see* the rival closing in. Reuses the hex-sync layer, gated behind `game.user.isGM` (embedded-doc-writes lesson). | `kingdom/map/*` (marker sync), `KingdomSheet.kt` |

**Dependencies:** 1 → 2 → 3 → {4, 5}. Phase 2 (pure) can start alongside Phase 1 (data). Phase 6 is optional and can ship anytime after Phase 3.

---

## 9. Open Questions for Gregory

0. **Sign off the tick deviation (§3.5).** The card specified movement on the **daily world clock** in **hexes/day**; this plan moves the band on the **monthly kingdom tick** in **hexes/turn**, and §3.5 argues why (ETA in kingdom turns is meaningless if the band creeps daily while turns advance monthly; the two-tick split stays intact; `DailyTickHooks` is untouched). This is a deliberate reversal of an explicit instruction, so it needs a yes, not silence. If the answer is no, §3.5, §3.6, the pace field and every ETA in §4 change together.
1. **Distance weight.** `RIVAL_DISTANCE_WEIGHT = 5` against `landmark 40 / unclearedLair 30 / contestedClaim 20 / unexplored 10` means a landmark stays the better prize up to 6 hexes farther than an empty hex, a lair up to 4. Does that feel like a band chasing *interesting* prizes, or should the weight drop (greedier for landmarks) or rise (more local)? Only the one constant needs tuning; the ordering question is settled (§3.3.1).
2. **Aggression sources.** Rise on contested arrival + proximity-to-claimed only, or also when the players *contest* it via **[Race Them]** / **[Confront]**? (Plan: arrival + proximity; Confront jumps to threshold.)
3. **Intermediate hexes.** The band's `distanceToObjective` counts down while `currentHexKey` stays put until arrival (§3.3.2) — "last seen at X, N turns out". The alternative is stepping `currentCube` along the cube line each turn — `DailyTickHooks.kt` already has exactly that math in its `private fun cubeLerpStep(from, to, steps, distance)` + `cubeRound`, which would have to be lifted into the pure core to be reused — which drops the distance field but makes "last seen" a lie between sightings. Plan: the countdown. Flavor call, and it only moves if you want the map marker (Phase 6) to crawl.
4. **Race-Them mechanic.** Should **[Race Them]** spawn a `RawQuest` (via `AddQuest`) or a **campaign clock** with a deadline? Both exist; a clock is lighter, a quest is more visible.
5. **Rival-realm bridge.** Should a band's `arrivals` optionally feed a linked `RawRivalRealm`'s `fame`/`size` when both features are present? (Currently out of scope — a clean future `km-offer` bridge, same shape as the agenda bridge in §6.1.)

---

**End of Plan.** Ready for review. On approval, implementation cards follow the Phase 1–5 table (Phase 6 optional).

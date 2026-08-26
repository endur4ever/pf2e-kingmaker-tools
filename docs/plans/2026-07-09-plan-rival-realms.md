# Rival Realms Scoreboard — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** Living-world follow-up to Faction & Diplomacy Relations Tracker (#1); sibling of the Faction Agenda Engine plan (`docs/plans/2026-07-09-plan-faction-agenda.md`)
> **Depends on:** Faction & Diplomacy Relations Tracker (#1, phases 1–4 landed), Army & War Pressure Board (#12), Turn History gazette, Campaign Clocks (#1 timeline)
> **Parent feature:** **Faction Agenda Engine** — when agendas exist, rival-realm growth should be *driven by agenda moves*. This plan ships a standalone flat-growth mode NOW and defines the agenda-driven mode for when the parent lands.
> **Branch:** `kingmaker.5`

---

## Executive Summary

The module tracks other powers as `RawGroup` factions with a living **standing / attitude** layer (Faction & Diplomacy Relations Tracker). But those factions have no *scale*: Pitax and Brevoy feel identical in weight to a two-hex bandit camp. Kingmaker's fiction is about *competing realms* that grow while the PCs look away — Brevoy claims the Rostland Hinterlands if the Stag Lord is left standing, Pitax recruits fresh armies during the War of the River Kings (`docs/house-rules.md`, chapter time-pressure advice).

This feature models **2–4 rival realms** as **lightweight scoreboard entities** — `size`, `fame`, `armyCount`, and a **deterministic growth-per-turn dial per stat** — each *linked to an existing faction* (`RawGroup`). Every kingdom turn (monthly, End Turn) each rival grows by its dialed increments, a **standings table** on the faction/trade board ranks the players' kingdom against the rivals, and **gazette headlines** announce rival moves ("Pitax claimed 3 hexes near the Branthlend Mountains").

**Be honest about what this is:** a **scoreboard with flavor**, not a simulation. There is no hidden rival economy, no rival RP/commodities/consumption, no rival-vs-rival AI. Growth is fully deterministic (fractional accrual, *no RNG at all*), so preview/commit parity is automatic and a GM can predict exactly where a rival will be in N turns. The point is to give expansion **pacing pressure** and a visible rival to out-grow — cheaply.

---

## 1. Problem Statement + Player/GM Value

**Problem:** The house rules repeatedly ask the GM to apply *off-screen realm pressure* to force pacing — "3 months to deal with the Stag Lord, otherwise Brevoy might claim the Rostland Hinterlands"; "if they wait too long… Pitax should recruit additional armies." Today the GM must track that entirely in their head or on paper. The module gives factions a *standing* but no *size* the player can watch creep upward.

**Value to the table:**

- **Visible pacing pressure (players).** A standings table that says "Pitax: Size 14 · You: Size 11 · rank #2" makes the abstract "you're falling behind" concrete and motivates claiming hexes now, without the GM lecturing.
- **Zero-bookkeeping off-screen growth (GM).** The GM dials a growth profile once ("Pitax is on `pitax-wartime`") and the rival grows automatically each End Turn, emitting a gazette headline the GM can read aloud.
- **Chapter-tuned pressure.** Preset growth profiles map the house-rules pressure curve onto concrete per-stat increments, so the pressure matches the chapter (dormant early, army-heavy during the war chapters).
- **Ties into systems that already exist.** A growing rival at war can feed a **GM-confirmed war-threat offer** into the Army & War Pressure board; aggressive expansion can offer a **standing shift** on the diplomacy tracker. Both are optional and never auto-apply.
- **Gazette fuel.** Every turn a rival grows, one public headline lands in the "Recent Turns" recap and the session-prep journal export — a living chronicle of the neighbors, for free.

**Deliberately NOT solved here:** a real rival kingdom. If the GM wants Pitax to actually *fight* Mivon, that is the **Faction Agenda Engine** (parent plan). This feature is the number that goes up.

---

## 2. Data Model

### 2.1 New external interface (`@JsPlainObject`, nullable fields for migration safety)

`RawRivalRealm.kt` lives in `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/data/` (alongside `RawGroup.kt`; `@JsPlainObject` interfaces are JS-only, so they cannot live in commonMain).

```kotlin
package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RawRivalRealm {
    /** Stable id (UUID) so UI edits + offers target the right realm. */
    var id: String

    /** Soft foreign key to RawGroup.name — the faction this realm IS.
     *  Same by-name link the caravan/war-threat systems already use: RawWarThreat.enemyFactionName
     *  and RawCaravan.partnerName. (NOT RawWarThreat.enemyFaction — that one is free text kept
     *  alongside the soft FK, not the link itself.) */
    var factionRef: String

    // --- Player-visible scoreboard stats -------------------------------------
    var size: Int          // realm size, parallels kingdom.size
    var fame: Int          // fame / infamy score
    var armyCount: Int     // number of standing armies (scalar, NOT deployable tokens)

    // --- GM-only growth dials (nullable => treated as 0 / flat / not-set) -----
    /** Per-turn increment PER stat, fractional-friendly. 0.5 => +1 size every 2 turns. */
    var sizeGrowthPerTurn: Double?
    var fameGrowthPerTurn: Double?
    var armyGrowthPerTurn: Double?

    /** Hidden accrual accumulators so fractional growth is LOSSLESS + deterministic across turns.
     *  Each turn: accrual += growthPerTurn; while accrual >= 1.0 { stat += 1; accrual -= 1.0 }. */
    var sizeAccrual: Double?
    var fameAccrual: Double?
    var armyAccrual: Double?

    /** Chapter preset id from data/rival-growth-profiles/ (e.g. "pitax-wartime"; see 3.4).
     *  When set, resolves the three growthPerTurn values; per-stat dials above override it. */
    var growthProfile: String?

    /** "flat" (default, this feature) | "agenda-driven" (parent Faction Agenda Engine). */
    var growthMode: String?

    /** GM kill-switch: true => realm does not grow this turn. */
    var pauseGrowth: Boolean?

    /** Place-name token interpolated into headlines ("the Branthlend Mountains"). */
    var borderRegion: String?

    // --- War / offer plumbing (GM-only) --------------------------------------
    /** armyCount at which a war-threat OFFER fires while the linked group is atWar. null => disabled. */
    var warArmyThreshold: Int?
    /** Idempotency guard: armyCount when the last war offer fired; re-offer only past this. */
    var lastWarOfferArmyCount: Int?

    /** Optional override for the headline template pool key; null => derive from the grown stat. */
    var headlinePool: String?
}
```

Field list (18): `id`, `factionRef`, `size`, `fame`, `armyCount`, `sizeGrowthPerTurn`, `fameGrowthPerTurn`, `armyGrowthPerTurn`, `sizeAccrual`, `fameAccrual`, `armyAccrual`, `growthProfile`, `growthMode`, `pauseGrowth`, `borderRegion`, `warArmyThreshold`, `lastWarOfferArmyCount`, `headlinePool`.

*Optional polish (not required):* `growthMode` persists as a bare string and §3.6 compares it with
a literal, which is what shipped code already does for closed persisted sets (`quest.status == "active"`,
`caravan.kind == "sellToPartner"`; `RawCaravan.kind` has no enum at all). Newer commonMain subsystems
prefer a paired enum instead (`Relations.fromString(group.relations) ?: Relations.NONE`,
`sheet/contexts/GroupContext.kt:60`). If symmetry with those is wanted, add
`enum class RivalGrowthMode(val value: String) { FLAT("flat"), AGENDA_DRIVEN("agenda-driven"); companion object { fun fromValue(v: String?) = entries.firstOrNull { it.value == v } } }`
in commonMain and read `RivalGrowthMode.fromValue(realm.growthMode) ?: RivalGrowthMode.FLAT`. Either
way an unknown persisted value falls through to flat growth — `flat` is the default and the
agenda branch is gated on an exact match.

### 2.2 Persistence location — top-level `KingdomData.rivalRealms`, NOT nested on `RawGroup`

**Decision:** a new top-level `KingdomData.rivalRealms: Array<RawRivalRealm>?`, each row carrying `factionRef → RawGroup.name`.

**Why not nest `rival` on `RawGroup`** (the way the parent agenda plan nests `agenda`)?

- A kingdom has *many* groups (Sootscale, every Brevoy house, minor trade partners) but only **2–4** are rival *realms*. Nesting a realm block on every group bloats the far-more-frequently-touched trade-partner record.
- `RawGroup` is read on hot paths (trade activities, caravan routing, negotiation DCs). Keeping it lean matters.
- A top-level array mirrors how `campaignClocks`, `warThreats`, and `companionExpeditions` already live on `KingdomData` — soft-FK-by-name is the established pattern (`RawWarThreat.enemyFactionName` at `RawWarThreat.kt:45`, `RawCaravan.partnerName` at `RawCaravan.kt:25` — note `RawWarThreat.enemyFaction` at `:15` is the *free-text* label kept beside the FK, not the link). See `KingdomData.kt`.

```kotlin
// KingdomData.kt — ADD (nullable => null means "not yet migrated"; empty => no rivals)
var rivalRealms: Array<RawRivalRealm>?
```

`Defaults.kt` `createKingdomDefaults()` seeds `rivalRealms = emptyArray()`.

The link is a **soft foreign key**: `factionRef` is a `RawGroup.name`. If a GM renames/deletes the group, the realm row survives but shows an "unlinked faction" hint in the UI; `atWar` / standing lookups fall back to neutral. (No cascade — same forgiving contract as caravans.)

### 2.3 Migration — `Migration49` *(placeholder — see caveat)*

The migration chain currently ends at **`Migration65`** (`src/jsMain/kotlin/at/posselt/pfrpg2e/migrations/Migrations.kt`; `MigrationChainTest` asserts contiguity). Propose **`Migration49`** *(placeholder — not free; see caveat)*. **Gregory sequences the real number at implementation** in case other branches land migrations first.

> ⚠️ **The number in this section is a placeholder and must be re-derived at implementation.**
> The chain now ends at **`Migration65`**. Since these plans were written, four of the reserved
> numbers have LANDED: 62 = downtime-projects, 63 = scheduled-pressure-engine,
> 64 = map-dynamism, 65 = loot-manifests. `Migration49` was never free (it sits inside the
> long-registered 17..61 range) and several unimplemented plans still name it. The next free
> number is **66**. Take the next contiguous number when this actually lands, and extend
> `MigrationChainTest`'s hardcoded range.


```kotlin
// PLACEHOLDER NUMBER — see the caveat above. Take the next contiguous number at landing
// (Migration64/65 both shipped this way) and extend MigrationChainTest's hardcoded range.
class Migration49 : Migration(49) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.rivalRealms == null) {
            kingdom.rivalRealms = emptyArray<Any>()
        }
    }
}
```

- **Non-breaking:** `rivalRealms` null/absent → no rivals; the standings table shows only the player row. Existing saves load unchanged.
- **No backfill:** rivals are opt-in; the GM adds them via the dialog. (Optionally, a future migration could seed Pitax/Brevoy stubs from known group names — deliberately out of scope; a seed template in the dialog is friendlier.)
- Register `Migration49()` in the `migrations` list in `Migrations.kt`; `latestMigrationVersion` recomputes from the list.

---

## 3. Engine Design

Two layers, matching the real codebase split (`FactionRelations.kt` pure-primitive math in commonMain + `GroupContext.kt` jsMain `RawGroup` adapter):

- **Pure core (commonMain, `data/kingdom/RivalRealms.kt`)** — operates on **primitives and plain data classes only**, no `RawRivalRealm` (that is a jsMain `@JsPlainObject`). Fully unit-testable in commonTest with zero Foundry/JS deps.
- **jsMain adapter (`kingdom/RivalRealmEngine.kt`)** — thin glue: reads a `RawRivalRealm`, calls the pure core, rebuilds an updated `RawRivalRealm` via auto `.copy`. Tested in jsTest.

### 3.1 Pure core (commonMain) — concrete signatures

```kotlin
package at.posselt.pfrpg2e.data.kingdom

/** Which stat grew this turn — keys the headline template pool. */
enum class RivalStat { SIZE, FAME, ARMY }

/** Resolved per-stat growth rates for one turn (from a profile or per-stat dials). */
data class RivalGrowthProfile(
    val sizePerTurn: Double,
    val famePerTurn: Double,
    val armyPerTurn: Double,
)

/** Result of advancing ONE stat by one turn: new value, carried accrual, and whether it ticked over. */
data class StatGrowth(val value: Int, val accrual: Double, val incremented: Int)

/**
 * Advance one stat one turn. Deterministic, no RNG:
 *   accrual += perTurn; while accrual >= 1.0 { value += 1; accrual -= 1.0 }.
 * `perTurn <= 0.0` or `paused` => no change (accrual untouched).
 */
fun growStat(value: Int, accrual: Double, perTurn: Double, paused: Boolean): StatGrowth

/** Composite "realm power" used to rank standings. Size-weighted, then fame, then armies. */
fun rivalPowerScore(size: Int, fame: Int, armyCount: Int): Int   // e.g. size*10 + fame*2 + armyCount*5

/** One row in the standings table (player or rival), pure/UI-free. */
data class RivalStandingRow(
    val label: String,        // faction name, or the player's kingdom name
    val size: Int,
    val fame: Int,
    val armyCount: Int,
    val score: Int,           // rivalPowerScore(...)
    val isPlayer: Boolean,
    val rank: Int = 0,        // filled by rankStandings
)

/** Sort by score descending, assign 1-based rank, keep the player row flagged. Pure. */
fun rankStandings(rows: List<RivalStandingRow>): List<RivalStandingRow>

/**
 * Deterministic headline template index for a grown stat this turn — no RNG, stable per (turn, faction, stat).
 * Returns an index in [0, poolSize). Same turn+faction+stat always picks the same template.
 */
fun headlineTemplateIndex(turn: Int, factionRef: String, stat: RivalStat, poolSize: Int): Int =
    if (poolSize <= 0) 0 else ((turn * 31 + factionRef.hashCode() + stat.ordinal * 7) % poolSize + poolSize) % poolSize

/** Which template pool a headline draws from. [poolSize] is a compile-time constant so
 *  [headlineTemplateIndex] never has to read lang/en.json to know how many templates exist. */
enum class RivalHeadlinePool(val poolSize: Int) { EXPAND(3), FAME(2), ARMY(2), ARMY_WAR(2) }

/** Pool selection: only ARMY has a wartime variant (§3.5 — SIZE cannot tick over at war). */
fun poolFor(stat: RivalStat, atWar: Boolean): RivalHeadlinePool = when (stat) {
    RivalStat.SIZE -> RivalHeadlinePool.EXPAND
    RivalStat.FAME -> RivalHeadlinePool.FAME
    RivalStat.ARMY -> if (atWar) RivalHeadlinePool.ARMY_WAR else RivalHeadlinePool.ARMY
}
```

### 3.2 jsMain adapter — concrete signature

```kotlin
// kingdom/RivalRealmEngine.kt (jsMain)

/** One rival's growth for one kingdom turn. Pure w.r.t. Foundry (no Game/actor). */
data class RivalGrowthResult(
    val realm: RawRivalRealm,           // NEW copy with updated stats + accruals
    val grewStats: Set<RivalStat>,      // stats that ticked over (=> headlines)
    val sizeDelta: Int,
    val fameDelta: Int,
    val armyDelta: Int,
    val warOfferArmyCount: Int?,        // non-null => post km-offer-rival-war-threat at this armyCount
)

fun growRivalRealm(
    realm: RawRivalRealm,
    profile: RivalGrowthProfile,        // resolved from realm.growthProfile / per-stat dials (see §3.4)
    atWar: Boolean,                     // from the linked RawGroup.atWar
    turn: Int,
): RivalGrowthResult

/** Advance every rival one turn; returns updated realms + flattened move list for gazette/offers. */
fun advanceAllRivals(
    rivals: Array<RawRivalRealm>,
    groupsByName: Map<String, RawGroup>,
    turn: Int,
    profiles: Map<String, RivalGrowthProfile>,  // rivalGrowthProfilesById(), see 3.4
): Pair<Array<RawRivalRealm>, List<RivalMove>>
```

`RivalMove` (jsMain) is the per-headline / per-offer record consumed by `performEndTurn`:

```kotlin
data class RivalMove(
    val factionRef: String,
    val stat: RivalStat,
    val pool: RivalHeadlinePool,  // poolFor(stat, atWar)
    val templateIndex: Int,       // headlineTemplateIndex(turn, factionRef, stat, pool.poolSize)
    val headline: String,         // localizeRivalHeadline(pool, templateIndex, data) — already interpolated
    val warOffer: RivalWarOffer?, // non-null => a GM-confirmed war-threat offer to post
)
```

**Template index → i18n key: a literal `when`, not a composed key.** `check_i18n_keys.py`'s reference
scan (`collect_refs`, `scripts/check_i18n_keys.py:124`) only sees literal `t("…")` / `localizeKM "…"` strings, so
`t("kingdom.rivalRealms.headline.$pool$index")` would be invisible to it and would ship as a raw
key with every guard green. `kingdom/pressure/PressureDigest.kt:67-77` already solves exactly this
("Literal keys mapped in a when -- t(\"...$kind\") would be invisible to check_i18n_keys.py");
rival headlines follow it:

```kotlin
// kingdom/RivalRealmEngine.kt (jsMain) — data carries { rival, n, place }
fun localizeRivalHeadline(pool: RivalHeadlinePool, index: Int, data: AnyObject): String = when (pool) {
    RivalHeadlinePool.EXPAND -> when (index) {
        0 -> t("kingdom.rivalRealms.headline.expand1", data)
        1 -> t("kingdom.rivalRealms.headline.expand2", data)
        else -> t("kingdom.rivalRealms.headline.expand3", data)
    }
    RivalHeadlinePool.FAME -> if (index == 0) t("kingdom.rivalRealms.headline.fame1", data)
        else t("kingdom.rivalRealms.headline.fame2", data)
    RivalHeadlinePool.ARMY -> if (index == 0) t("kingdom.rivalRealms.headline.army1", data)
        else t("kingdom.rivalRealms.headline.army2", data)
    RivalHeadlinePool.ARMY_WAR -> if (index == 0) t("kingdom.rivalRealms.headline.armyWar1", data)
        else t("kingdom.rivalRealms.headline.armyWar2", data)
}
```

`RawRivalRealm.headlinePool` (§2.1) overrides `poolFor`'s choice by naming a `RivalHeadlinePool`
entry; an unparseable value falls back to `poolFor`. In jsTest no i18next instance is initialised, so
`t(key, data)` falls back to `"$key ($details)"` (`utils/Localization.kt:67-84`) — assertions use
`headline.startsWith("kingdom.rivalRealms.headline.armyWar1")` (or assert `pool`/`templateIndex`
directly), never string equality against the English text.

### 3.3 The growth model — deterministic, no hidden simulation

Stated plainly so nobody mistakes this for an economy:

1. Each stat has a **`growthPerTurn: Double`** dial (or 0 if unset). There is **no** demand, supply, RP, or feedback loop. The number the GM sets is exactly the number that accrues.
2. Each turn: `accrual += growthPerTurn`; every whole point in `accrual` becomes **+1** to the integer stat; the remainder carries. So `0.5` size/turn ⇒ +1 size on turns 2, 4, 6…; `2.0` ⇒ +2 every turn. Lossless and predictable.
3. **`pauseGrowth`** (GM kill-switch) freezes all stats.
4. **No decay, no cap** in v1 (a rival can outrun the player forever — that IS the pressure). A soft cap could be a later dial.
5. **Determinism:** because there is *no RNG* anywhere (accrual is arithmetic; headline choice is a hash of `turn+faction+stat`), the tick is a pure function of state. Preview and commit call it on the same input and get identical output — **preview/commit parity is automatic**, and stronger than the parent agenda plan's seeded-LCG approach.

### 3.4 Profile resolution (chapter presets)

Profiles ship as **one JSON file per profile in the directory `data/rival-growth-profiles/`** — *not*
a flat `data/rival-growth-profiles.json`. `data/` contains only subdirectories today, because the
bundling task `combineJsonFiles` (`build.gradle.kts:29-32`) is registered **once** over
`sourceDirectory = data/` and its action walks one level deep filtering for directories
(`Files.walk(source, 1).filter { Files.isDirectory(it) }.filter { it != source }`,
`buildSrc/src/main/kotlin/at/posselt/pfrpg2e/plugins/CombineJsonFiles.kt`), emitting
`build/generated/data/<dirname>.json` per subdirectory. A top-level *file* is skipped outright and
would never reach the bundle, so `@JsModule("./rival-growth-profiles.json")` would not resolve.

Because the task is generic, **a new subdirectory needs no Gradle change and no new task** — it is
picked up automatically and lands in commonMain resources via `resources.srcDirs(...,
tasks.named("combineJsonFiles"))` (`build.gradle.kts:76-79`).

The merged output is a JSON **array**, so each file is one self-describing object carrying its own
`id` (an id→rates map in a single file would not survive the merge):

```json
// data/rival-growth-profiles/pitax-wartime.json
{ "id": "pitax-wartime", "sizePerTurn": 0.33, "famePerTurn": 0.5, "armyPerTurn": 0.5 }
```

Loaded jsMain-side in the style of `KingdomMilestone.kt:17-18`:

```kotlin
// kingdom/RivalGrowthProfiles.kt (jsMain)
@JsPlainObject
external interface RawRivalGrowthProfile {
    var id: String
    var sizePerTurn: Double
    var famePerTurn: Double
    var armyPerTurn: Double
}

@JsModule("./rival-growth-profiles.json")
private external val rivalGrowthProfiles: Array<RawRivalGrowthProfile>

/** The "profile id -> rates" map 3.2 hands to advanceAllRivals, built by keying the array on id. */
fun rivalGrowthProfilesById(): Map<String, RivalGrowthProfile> =
    rivalGrowthProfiles.associate {
        it.id to RivalGrowthProfile(it.sizePerTurn, it.famePerTurn, it.armyPerTurn)
    }
```

Optionally add `src/commonMain/resources/schemas/rival-growth-profile.json` plus a
`JsonSchemaValidator` task mirroring `validateMilestones` (`build.gradle.kts:205-209`) and list it in
the `check` task's `dependsOn` block (`build.gradle.kts:120-135`), so a malformed profile fails the
build instead of the world.

Resolution order per realm:

1. If a per-stat dial (`sizeGrowthPerTurn`, …) is non-null → use it (GM override wins).
2. Else if `growthProfile` names a profile → use its rates.
3. Else → all zero (dormant).

Presets tuned from the `docs/house-rules.md` pressure curve — one file each, named `<id>.json`:

| Profile id | size/turn | fame/turn | army/turn | Fiction (house-rules citation) |
|------------|-----------|-----------|-----------|--------------------------------|
| `dormant` | 0 | 0 | 0 | Rival is quiescent (default). |
| `slow-expansion` | 0.33 | 0.2 | 0 | Background creep (~1 hex / 3 turns). |
| `brevoy-hinterlands` | 0.5 | 0.25 | 0.1 | Ch.1: "Brevoy might claim the Rostland Hinterlands" if the Stag Lord lingers. |
| `troll-horde` | 0.2 | 0 | 0.75 | Hargulka: "send a low level troll army"; army-heavy buildup. |
| `pitax-wartime` | 0.33 | 0.5 | 0.5 | War of the River Kings: "Pitax should recruit additional armies." |
| `expansionist-endgame` | 0.75 | 0.5 | 0.4 | Post-war northern powers scaling toward chapter 10. |

### 3.5 War interaction — decided

When the **linked `RawGroup.atWar == true`**:

- **Size growth pauses** — a realm actively at war is fighting, not annexing quiet hexes. (`profile.sizePerTurn` treated as 0 for this turn.)
- **Army growth continues (and profiles bias it high)** — they are mobilizing. **Army headlines use the `armyWar` pool** (§4.4) instead of the peacetime `army` pool. There is deliberately **no wartime *size* pool**: with `sizePerTurn` forced to 0 the size accrual never advances and `growStat` leaves the carried remainder untouched (§3.1), so a SIZE tickover is unreachable at war *by construction* — a wartime expansion headline could never fire.
- When `armyCount` **crosses `warArmyThreshold`** (and `armyCount > lastWarOfferArmyCount`), `growRivalRealm` sets `warOfferArmyCount`; `performEndTurn` posts a **GM-confirmed `km-offer-rival-war-threat`** that prefills `AddWarThreat` (reusing the exact plumbing behind `km-offer-war-threat`). Nothing is created until the GM clicks. `lastWarOfferArmyCount` is bumped to prevent re-offering until the army grows further.

This wires the scoreboard into the Army & War Pressure board **without** simulating rival battles.

### 3.6 Agenda-driven mode (parent Faction Agenda Engine)

`growthMode` selects the source of growth:

- **`flat` (default, ships now):** growth comes entirely from `growthPerTurn` / profile accrual, as above.
- **`agenda-driven` (activates when the parent lands):** flat accrual is **suppressed**; instead the Faction Agenda Engine's per-turn *moves* drive the stats. Concretely, when the parent's `advanceAllAgendas` emits a move for a faction that also has a rival realm, the move maps onto rival stats:
  - `expand` move → `size += effectMagnitude` (and a size headline)
  - `raise-army` move → `armyCount += 1`
  - `court-*` / `sabotage-*` moves → `fame += small` (prestige from maneuvering)

  The parent engine already resolves *which* move fired deterministically; this feature just translates the chosen move into a stat bump. Both plans run inside the same `TurnTickingEngine.tick()`, so ordering is: agendas resolve → their moves feed rival stats (skipping flat accrual for `agenda-driven` realms) → standings/gazette. Until the parent ships, every realm is `flat` and this branch is dormant code guarded by `growthMode == "agenda-driven"`.

### 3.7 Tick surface — `TurnTickingEngine.tick()` (monthly), NOT `DailyTickHooks`

Rival growth runs **inside `TurnTickingEngine.tick()`** at End Turn (monthly kingdom economy), returning new fields on `TickResult`:

```kotlin
data class TickResult(
    // ... existing fields (groups, warThreats, updatedClocks, clockEvents, …) ...
    val rivalRealms: Array<RawRivalRealm> = emptyArray(),
    val rivalMoves: Array<RivalMove> = emptyArray(),
)
```

**Justification for the monthly tick (respecting the two-tick split, no third tick):**

- Rival *scale* (`size`, `armyCount`) is a kingdom-scale quantity that must move in lockstep with the player's own `kingdom.size`/level, which change at **End Turn** — a rival growing daily while the player grows monthly would make the standings jitter meaninglessly.
- `tick()` is the **preview-safe** surface; every other turn consequence (clocks, standing drift, caravans) already resolves there, so rival growth participates in the same preview/commit parity contract. `DailyTickHooks` is for daily-world-clock effects (weather, companion travel) and is explicitly untouched.
- The engine already receives `groups` and returns `tickResult.groups`: the tick input is assembled in `runKingdomTurnTick` (`TurnWizardApplication.kt:207-239`, e.g. `groups = kingdom.groups.unsafeCast<Array<RawGroup>?>() ?: emptyArray(),` at line 237) and the result is written back in `performEndTurn` (`kingdom.groups = tickResult.groups`, line 355). Adding `rivalRealms` alongside is a one-parameter extension of an established flow.
- **Phase 3 adds `rivalRealms = kingdom.rivalRealms ?: emptyArray()` to the `TurnTickingEngine.tick(...)` argument list inside `runKingdomTurnTick`** — the single tick-input chokepoint shared by the End Turn commit (`TurnWizardApplication.kt:296`), the Turn Wizard preview (`:1175`) and `kingdom/forecast/ForecastAdapter.kt:148`. Its KDoc (`:202-206`) is explicit that both paths "MUST call this — never tick() directly", and `TurnWizardApplicationTest.kt:82` guards the contract, so wiring the new input anywhere else is a bug.

`performEndTurn` persists `kingdom.rivalRealms = tickResult.rivalRealms`, feeds `tickResult.rivalMoves` headlines into the gazette, and posts any war/standing offers.

---

## 4. UI Design

### 4.1 Standings table — Trade Agreements board (recommended tab)

**No new nav entry.** The rival standings belong on **`MainNavEntry.TRADE_AGREEMENTS`** (`src/jsMain/resources/applications/kingdom/sections/trade-agreements/page.hbs`) — the same board that already lists groups + attitude/standing. A rival realm *is* a faction with realm stats, so it reads naturally next to the diplomacy table. Add a new `<section>` after the existing Groups + Standing-Log sections.

**Ranking: composite score AND per-stat columns** (both, because each answers a different question):

- A **composite "Realm Power"** column (`rivalPowerScore`) drives the sort and gives the one-glance "who is winning" answer.
- **Per-stat columns** (Size · Fame · Armies) show *how* — is Pitax winning on armies or on land?
- The **player's own kingdom is a computed row** (from `kingdom.size`, `kingdom.fame`, and its army count) marked `isPlayer`, highlighted, and ranked in-line so the player sees "#2 of 3".

```hbs
<section>
  <h2>{{localizeKM "kingdom.rivalRealms.title"}}
    {{#if isGM}}<span class="km-header-right-align">
      <button type="button" data-action="add-rival-realm"><i class="fa-solid fa-plus"></i></button>
    </span>{{/if}}
  </h2>
  {{#if rivalRealms.rows.length}}
  <table>
    <thead><tr>
      <td>{{localizeKM "kingdom.rivalRealms.rank"}}</td>
      <td>{{localizeKM "kingdom.rivalRealms.realm"}}</td>
      <td>{{localizeKM "kingdom.rivalRealms.power"}}</td>
      <td>{{localizeKM "kingdom.rivalRealms.size"}}</td>
      <td>{{localizeKM "kingdom.rivalRealms.fame"}}</td>
      <td>{{localizeKM "kingdom.rivalRealms.armies"}}</td>
      {{#if isGM}}<td>{{localizeKM "kingdom.rivalRealms.growth"}}</td><td></td>{{/if}}
    </tr></thead>
    <tbody>
    {{#each rivalRealms.rows}}
      <tr class="{{#if isPlayer}}km-rival-player-row{{/if}}">
        <td>#{{rank}}</td>
        <td>{{label}}{{#unless linked}} <i class="fa-solid fa-link-slash" data-tooltip="{{localizeKM "kingdom.rivalRealms.unlinked"}}"></i>{{/unless}}</td>
        <td>{{score}}</td>
        <td>{{size}}</td>
        <td>{{fame}}</td>
        <td>{{armyCount}}</td>
        {{#if ../isGM}}
          <td>{{#unless isPlayer}}{{growthSummary}}{{/unless}}</td>
          <td>{{#unless isPlayer}}
            <button type="button" data-action="edit-rival-realm" data-id="{{id}}"><i class="fa-solid fa-sliders"></i></button>
            <button type="button" data-action="delete-rival-realm" data-id="{{id}}"><i class="fa-solid fa-trash"></i></button>
          {{/unless}}</td>
        {{/if}}
      </tr>
    {{/each}}
    </tbody>
  </table>
  {{else}}<p>{{localizeKM "kingdom.rivalRealms.empty"}}</p>{{/if}}
</section>
```

### 4.2 Context object (`RivalRealmsContext.kt`, jsMain, `sheet/contexts/`)

```kotlin
@JsPlainObject
external interface RivalStandingRowContext {
    val id: String?            // null for the player row
    val label: String
    val score: Int
    val size: Int
    val fame: Int
    val armyCount: Int
    val rank: Int
    val isPlayer: Boolean
    val linked: Boolean        // factionRef resolves to a real group
    val growthSummary: String? // GM-only: "Size +0.5/t · Armies +0.5/t (pitax-wartime)"; null for players
}

@JsPlainObject
external interface RivalRealmsContext {
    val rows: Array<RivalStandingRowContext>   // player + rivals, pre-ranked
    val isGM: Boolean
}
```

Built in `KingdomSheet.kt` alongside the existing group/analytics context assembly: map `kingdom.rivalRealms` + a computed player row through `rankStandings`, then to `RivalStandingRowContext`. `growthSummary` is populated **only when `isGM`** — players never see the dials.

### 4.3 GM dialog — `ModifyRivalRealm.kt`

A small Foundry `FormApp`/dialog (pattern of `ModifyFactionStanding.kt` / `ModifyCampaignClock.kt`) for add/edit:

- **Faction** (select from `kingdom.groups` names → sets `factionRef`)
- **Size / Fame / Armies** (number inputs)
- **Growth profile** (select over `rivalGrowthProfilesById().keys`, labelled `kingdom.rivalRealms.profile.<id>`) *or* per-stat growth overrides (three number inputs)
- **Growth mode** (`flat` / `agenda-driven`)
- **Border region** (text, for headline interpolation)
- **War army threshold** (number, blank = disabled)
- **Pause growth** (checkbox)

On save: upsert into `kingdom.rivalRealms`, `actor.setKingdom(kingdom)`. Wired via `data-action="add-rival-realm" | edit-rival-realm | delete-rival-realm` handlers in `KingdomSheet.kt` (GM-gated).

### 4.4 i18n namespace

All keys nested under `pf2e-kingmaker-tools` → `kingdom.rivalRealms.*` (nested objects, never flat-dotted — see `scripts/check_i18n_keys.py`), wired through `initLocalization()`. The namespace is added to **ALL EIGHT locale files** (`lang/en.json`, `de.json`, `fr.json`, `it.json`, `pl.json`, `pt-BR.json`, `ru.json`, `zh-Hans.json`) with **identical nested key sets and identical placeholder names** — `check_parity` (`scripts/check_i18n_keys.py:242`) enforces both, and CI runs the strict form `python3 scripts/check_i18n_keys.py --all` (`.github/workflows/test.yml:27`). An en-only addition is a red build.

Placeholders are **single-brace ICU** (`{rival}`, never `{{rival}}`): `initLocalization()` initialises i18next with the ICU plugin (`utils/Localization.kt:142-144`), and every sibling `kingdom.turnGazette.*` value is single-braced (`"Caravans: {list}"`, `"Campaign Clocks: {clocks}"`).

Values are **strings only — never JSON arrays.** There is not one array value anywhere in `lang/en.json` today, and `unfuckFoundryTranslations` (`utils/Localization.kt:114-123`) rebuilds every non-string node through `.toRecord()`, so an array pool would silently arrive at i18next as an object. The headline pools are therefore flattened into individually-named string keys (`headline.expand1`, `headline.expand2`, …):

```json
"kingdom": {
  "rivalRealms": {
    "title": "Rival Realms",
    "rank": "#", "realm": "Realm", "power": "Power", "size": "Size",
    "fame": "Fame", "armies": "Armies", "growth": "Growth",
    "empty": "No rival realms tracked. Add one to watch the neighbors grow.",
    "unlinked": "This realm's faction no longer exists.",
    "standingReason": "{rival}'s expansion strained the border",
    "profile": { "dormant": "Dormant", "slow-expansion": "Slow Expansion",
      "brevoy-hinterlands": "Brevoy — Hinterlands Claim", "troll-horde": "Troll Horde",
      "pitax-wartime": "Pitax — Wartime Levy", "expansionist-endgame": "Expansionist" },
    "headline": {
      "expand1": "{rival} claimed {n} hexes near {place}.",
      "expand2": "{rival} pushed its border toward {place}.",
      "expand3": "Settlers under {rival}'s banner spread across {place}.",
      "fame1": "{rival}'s renown spreads through the River Kingdoms.",
      "fame2": "Bards carry tales of {rival} to distant courts.",
      "army1": "{rival} raised {n} fresh companies.",
      "army2": "{rival}'s war-camps swell near {place}.",
      "armyWar1": "{rival}'s levies muster for war near {place}.",
      "armyWar2": "{rival} marches {n} fresh companies to the front."
    },
    "warOffer": { "title": "{rival} masses for war", "raise": "Raise War Threat", "dismiss": "Dismiss" },
    "standingOffer": { "title": "{rival}'s expansion strains the border", "apply": "Apply Standing Shift", "dismiss": "Dismiss" },
    "dialog": { "add": "Add Rival Realm", "edit": "Edit Rival Realm", "faction": "Faction",
      "growthProfile": "Growth Profile", "growthMode": "Growth Source", "borderRegion": "Border Region",
      "warThreshold": "War Army Threshold", "pauseGrowth": "Pause Growth" }
  },
  "turnGazette": { "rivalMove": "Rival Realms: {list}" }
}
```

**Profile labels are the one composed key, and they get their own guard.** Profile ids come from
`data/rival-growth-profiles/` (§3.4), so the label lookup is
`t("kingdom.rivalRealms.profile.$id")` — invisible to `collect_refs`, and a new profile file with
no label would ship a dropdown entry titled with the raw key while every check stays green. This is
exactly the shape `check_setup_wizard_keys` (`scripts/check_i18n_keys.py:356-389`) was written for,
so Phase 4 adds a sibling:

```python
# scripts/check_i18n_keys.py
RIVAL_PROFILE_DIR = "data/rival-growth-profiles"

def check_rival_profile_keys():
    """Check 8: every rival growth profile id must have a label in every locale."""
    ids = [json.load(open(p, encoding="utf-8"))["id"]
           for p in sorted(glob.glob(f"{RIVAL_PROFILE_DIR}/*.json"))]
    problems = [(path, f"kingdom.rivalRealms.profile.{i}")
                for path in sorted(glob.glob("lang/*.json"))
                for i in ids
                if not isinstance(
                    lookup_value(json.load(open(path, encoding="utf-8")).get(NS, {}),
                                 f"kingdom.rivalRealms.profile.{i}"), str)]
    ...
```

registered in `main()` alongside the other checks so `--all` runs it.

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

Rival moves are **mostly public gazette flavor** with **no button**. Only the two moves that grant a mechanical benefit become GM-confirmed offers (`km-offer-*` in `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/ChatButtons.kt`), never auto-applied.

### 5.1 Gazette headlines (public, no button)

Every stat that ticked over produces one headline line, appended to the End-Turn gazette (see §6). No interaction — pure fiction.

**The gazette string exists twice, on purpose.** `formatTurnGazette` takes an injected localizer defaulting to `::defaultLocalize` (`TurnHistory.kt:91`), so its unit tests render through a hand-maintained `when` rather than i18next. Phase 3 must therefore add

```kotlin
// TurnHistory.kt — inside defaultLocalize (:208), above the `else -> key` at :231
"kingdom.turnGazette.rivalMove" -> "Rival Realms: ${dyn.list}"
```

**verbatim-equivalent** to the `lang/en.json` value in §4.4. `check_gazette_resolver` (`scripts/check_i18n_keys.py:308-350`, run by CI via `--all`) normalises the Kotlin `${dyn.list}` to `{list}` and demands a byte-identical en.json string; without the `when` case `defaultLocalize` falls through to `else -> key`, §7.2's `gazetteIncludesRivalHeadlines` renders the raw key, and the divergence is invisible everywhere else.

### 5.2 Offer cards enumerated

| Trigger | Offer id | Buttons | Handler behavior (ChatButtons.kt) |
|---------|----------|---------|-----------------------------------|
| Linked group `atWar` **and** `armyCount` crosses `warArmyThreshold` | `km-offer-rival-war-threat` | **[Raise War Threat]** · **[Dismiss]** | GM-gated. `[Raise War Threat]` opens `AddWarThreat` prefilled with `prefillEnemyFaction = factionRef` (reuses the exact body of the existing `km-offer-war-threat` handler — appends threat, `recalculateWarPressure`, `setKingdom`). `[Dismiss]` bumps `lastWarOfferArmyCount` so it won't re-fire until the army grows again. |
| Rival `size` grew by ≥ configurable N this turn (aggressive expansion on a shared border) | `km-offer-rival-standing-shift` | **[Apply Standing Shift]** · **[Dismiss]** | GM-gated + idempotent. `[Apply Standing Shift]` routes a negative delta through `applyStandingDelta` on the linked `RawGroup`, appends a `RawFactionStandingEntry` (reason `kingdom.rivalRealms.standingReason`), `setKingdom`. Naturally participates in the diplomacy tracker's own threshold hooks. `[Dismiss]` posts nothing. |

Both follow the established idempotent, GM-gated, `button.dataset["…"]`-driven shape (see `km-offer-war-threat`, `km-offer-war-threat-arrival` at `ChatButtons.kt:216` / `:242`). Both carry `data-kingdom-actor-uuid` for actor resolution.

### 5.3 Digest, not spam

Offers are collected during `performEndTurn` and posted as at most **one whisper per offer type per turn** listing the rivals that crossed a threshold (mirrors the war-threat digest), so 3 rivals massing armies produce one card with three button rows, not three cards.

---

## 6. Interactions With Existing Systems

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Faction & Diplomacy Tracker** | `data/kingdom/FactionRelations.kt`, `kingdom/data/RawGroup.kt`, `sheet/contexts/GroupContext.kt` | `factionRef → RawGroup.name` is the link. `atWar` on the group gates the war/army growth split. `km-offer-rival-standing-shift` routes through `applyStandingDelta` + appends `RawFactionStandingEntry`. |
| **Turn ticking** | `kingdom/TurnTickingEngine.kt` | New `rivalRealms` in/`rivalRealms`+`rivalMoves` out on `TickResult`; growth runs in `tick()` (monthly), deterministic. |
| **End-turn flow** | `kingdom/dialogs/TurnWizardApplication.kt` (`performEndTurn`) | Persists `kingdom.rivalRealms = tickResult.rivalRealms`; feeds `rivalMoves` labels into `formatTurnGazette`; posts §5 offers. |
| **Gazette / Turn History** | `kingdom/TurnHistory.kt` | `formatTurnGazette` (`:80-206`) gains `rivalMoves: List<String> = emptyList()` → public "Rival Realms" gazette lines. Passed to **both** the GM (`turnNotes`) and player (`playerNotes`) gazette calls — rival headlines are **public** (unlike secret campaign-clock progress, which is dropped for players). **Also add the mandatory second copy of the string to `defaultLocalize`** — see below. |
| **Army & War Pressure** | `kingdom/data/RawWarThreat.kt`, `kingdom/dialogs/AddWarThreat.kt`, `recalculateWarPressure` | `km-offer-rival-war-threat` reuses `AddWarThreat`; `armyCount` is a scoreboard scalar that *offers* a threat, never spawns a token. |
| **Campaign Clocks** | `campaign/CampaignClock.kt`, `CampaignClockContext.kt` | Independent, but complementary: a "Stag Lord deadline" clock and a `brevoy-hinterlands` rival profile are the two halves of the same house-rule ("deal with the Stag Lord or Brevoy claims the Hinterlands"). No code coupling. |
| **Turn Analytics** | `kingdom/TurnAnalytics.kt`, `sheet/contexts/AnalyticsContext.kt` | **Optional overlay (deferred phase):** overlay the leading rival's `rivalPowerScore` (or size) as a second series on the existing size/level charts, so the player sees their curve vs the rival's. Requires snapshotting one rival value into `RawTurnRecord` (a new nullable field → its own migration) and an `extractSeries` key. Marked deferred; not required for v1. |
| **Faction Agenda Engine (parent)** | `docs/plans/2026-07-09-plan-faction-agenda.md` | `growthMode == "agenda-driven"` maps agenda moves onto rival stats (§3.6). Dormant until the parent ships. |
| **Daily tick** | `kingdom/DailyTickHooks.kt` | **No interaction** — rival growth is monthly only. |

### 6.1 Explicit OUT-OF-SCOPE

- **No rival economy.** Rivals have no RP, commodities, consumption, storage, structures, or unrest. Only `size` / `fame` / `armyCount`.
- **No rival-vs-rival behavior.** Rivals do not war on, ally with, or sabotage each other — that is the parent **Faction Agenda Engine**. This feature is a scoreboard.
- **No tactical map effects.** `armyCount` never spawns army tokens or moves on the hex map; `size` never claims map hexes or changes hex ownership. (A war-threat *offer* is the only bridge to the map, and only if the GM accepts it.)
- **No auto-applied consequences.** Every mechanical effect (war threat, standing shift) is a GM-confirmed offer.
- **No player-facing rival management.** Players view the standings table read-only; all dials are GM-only.
- **Analytics rival overlay is deferred** (see table) — ships later behind its own `RawTurnRecord` field + migration.
- **No decay / hard cap** on rival stats in v1.

---

## 7. Test Plan

### 7.1 commonTest — pure core (`data/kingdom/RivalRealmsTest.kt`, JVM-less)

| Test | Assertion |
|------|-----------|
| `growStat_fractionalAccrualCrossesOnce` | value 10, accrual 0.6, perTurn 0.5 → value 11, accrual 0.1, incremented 1. |
| `growStat_multiPointPerTurn` | perTurn 2.5, accrual 0.0 → +2 this turn, accrual 0.5. |
| `growStat_pausedNoChange` | `paused=true` → value + accrual unchanged, incremented 0. |
| `growStat_zeroRateNoChange` | perTurn 0.0 → no change. |
| `growStat_losslessOverManyTurns` | 10× ticks at 0.3 → exactly +3 total (no rounding drift). |
| `rivalPowerScore_ordering` | higher size outweighs higher armies per the weight formula; monotonic. |
| `rankStandings_sortsDescAndAssignsRank` | rows sorted by score desc, ranks 1..n, ties broken stably. |
| `rankStandings_playerRowFlaggedAndRanked` | player row keeps `isPlayer` and gets a correct in-line rank. |
| `headlineTemplateIndex_deterministic` | same (turn, faction, stat) → same index; always in `[0, poolSize)`; `poolSize=0 → 0`. |
| `headlineTemplateIndex_variesByStat` | SIZE vs ARMY on same turn/faction can differ (no collision by construction). |

### 7.2 jsTest — adapter + integration (`kingdom/RivalRealmEngineTest.kt`)

| Test | Assertion |
|------|-----------|
| `growRivalRealm_flatProfileIncrementsAndHeadlines` | `pitax-wartime` on a realm → army/size deltas match profile; `grewStats` drives correct headline keys. |
| `growRivalRealm_atWarPausesSizeKeepsArmy` | `atWar=true` → `sizeDelta==0`, `grewStats` contains only `ARMY`, the army move's `pool` is `RivalHeadlinePool.ARMY_WAR` and `headline.startsWith("kingdom.rivalRealms.headline.armyWar1")` (with no i18next in jsTest, `t(key, data)` returns `"$key ($details)"` — see §3.2). |
| `growRivalRealm_warOfferOnThresholdCross` | armyCount rising past `warArmyThreshold` (and past `lastWarOfferArmyCount`) → `warOfferArmyCount` non-null; below/equal → null. |
| `growRivalRealm_pauseGrowthFreezes` | `pauseGrowth=true` → no deltas, no moves. |
| `advanceAllRivals_deterministicPreviewCommitParity` | same input twice → byte-identical `rivalRealms` + `rivalMoves`. |
| `tick_returnsRivalRealmsAndMoves` | `TurnTickingEngine.tick()` with 3 rivals populates `TickResult.rivalRealms` + `rivalMoves`. |
| `gazetteIncludesRivalHeadlines` | `formatTurnGazette(rivalMoves=…)` output contains the interpolated headline lines. |
| `standingShiftOffer_onAggressiveExpansion` | size delta ≥ N → a `km-offer-rival-standing-shift` move; below N → none. |
| `unlinkedFaction_growsButNoWarOffer` | `factionRef` with no matching group → grows, but `atWar` treated false so no war offer. |
| `migration49_seedsEmptyArray` *(placeholder number — see the §2.3 caveat; rename with the class)* | KingdomData without `rivalRealms` → after Migration49 → `rivalRealms != null`, length 0. |

### 7.3 Manual Foundry verification checklist

1. Open Kingdom Sheet → **Trade Agreements** tab. With no rivals, the Rival Realms section shows only the player's kingdom row (rank #1) + empty hint.
2. Click **Add Rival Realm** → pick "Pitax", Size 12, Armies 2, profile `pitax-wartime`, border region "the Sellen River", war threshold 5 → Save. Row appears, ranked against the player.
3. **End Turn** → chat gazette shows a Rival Realms headline (e.g. "Pitax raised 1 fresh company"); standings table updates; player's rank may drop.
4. Run several turns → confirm fractional accrual (size ticks up every ~3 turns at 0.33; army every 2 at 0.5); numbers match the dial exactly (deterministic).
5. Set the Pitax group `atWar = true` → End Turn → size growth pauses, army keeps rising, wartime headline flavor appears.
6. Push `armyCount` past the war threshold → a **whispered `km-offer-rival-war-threat`** appears → **[Raise War Threat]** opens `AddWarThreat` prefilled with Pitax → Save → threat lands on the Army Pressure board; re-running a turn does not re-offer until the army grows further.
7. Give a rival a fast `size` profile → after a big expansion turn, a **`km-offer-rival-standing-shift`** offer appears → **[Apply Standing Shift]** drops the group's standing + adds a standing-log entry.
8. Toggle **Pause Growth** → End Turn → that realm's stats do not move.
9. Log in as a **player** → standings table is visible and read-only; no growth dials, no Add/Edit/Delete buttons, no offer whispers.
10. Rename the linked group → realm row shows the "unlinked faction" hint but still renders/grows.
11. Reload the world → rivals, stats, accruals, and dials persist. Run `python3 scripts/check_i18n_keys.py --all` (the form CI runs — includes cross-language parity, the gazette-resolver check and the new profile-label guard) → no raw keys in the UI.

---

## 8. Phasing (Independently Committable)

Each phase is one kanban worker card, ~1–2 days.

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Data model + migration + profiles** | `RawRivalRealm` interface, `KingdomData.rivalRealms`, `Defaults` seed, `Migration49` *(placeholder number — see the §2.3 caveat)* (+ registry), the six preset files in `data/rival-growth-profiles/`, `RawRivalGrowthProfile` + `rivalGrowthProfilesById()` loader (no `build.gradle.kts` change — `combineJsonFiles` picks the new directory up automatically). | `kingdom/data/RawRivalRealm.kt`, `KingdomData.kt`, `sheet/Defaults.kt`, `migrations/migrations/Migration49.kt` *(placeholder)*, `migrations/Migrations.kt`, `kingdom/RivalGrowthProfiles.kt`, `data/rival-growth-profiles/*.json` |
| **2** | **Pure core (commonMain) + tests** | `RivalRealms.kt` (`growStat`, `rivalPowerScore`, `RivalStandingRow`, `rankStandings`, `headlineTemplateIndex`, `RivalHeadlinePool`, `poolFor`, `RivalGrowthProfile`, `RivalStat`), full `RivalRealmsTest`. | `commonMain/.../data/kingdom/RivalRealms.kt`, `commonTest/.../data/kingdom/RivalRealmsTest.kt` |
| **3** | **jsMain engine + tick integration** | `RivalRealmEngine.kt` (`growRivalRealm`, `advanceAllRivals`, `localizeRivalHeadline`, `RivalGrowthResult`, `RivalMove`), extend `TickResult`, wire the new `rivalRealms` argument into `TurnTickingEngine.tick(...)` **inside `runKingdomTurnTick`** (`TurnWizardApplication.kt:207`) so commit/preview/forecast stay in parity, persist + gazette in `performEndTurn`, `formatTurnGazette` `rivalMoves` param, **and the matching `"kingdom.turnGazette.rivalMove" -> "Rival Realms: ${dyn.list}"` case in `defaultLocalize` (`TurnHistory.kt:208`, above `else -> key` at `:231`)** — `check_gazette_resolver` requires the two copies to match verbatim. | `kingdom/RivalRealmEngine.kt`, `kingdom/TurnTickingEngine.kt`, `kingdom/dialogs/TurnWizardApplication.kt` (`runKingdomTurnTick` + `performEndTurn`), `kingdom/TurnHistory.kt`, `RivalRealmEngineTest.kt` (jsTest) |
| **4** | **Standings UI + GM dialog + i18n** | Rival Realms section on trade-agreements board, `RivalRealmsContext`, computed player row + `rankStandings`, `ModifyRivalRealm` dialog, sheet action handlers, the `kingdom.rivalRealms.*` namespace in **all eight** locale files (identical key sets + placeholder names), and the `check_rival_profile_keys` guard. | `sections/trade-agreements/page.hbs`, `sheet/contexts/RivalRealmsContext.kt`, `kingdom/dialogs/ModifyRivalRealm.kt`, `sheet/KingdomSheet.kt`, `lang/en.json`, `lang/de.json`, `lang/fr.json`, `lang/it.json`, `lang/pl.json`, `lang/pt-BR.json`, `lang/ru.json`, `lang/zh-Hans.json`, `scripts/check_i18n_keys.py` |
| **5** | **Offer handlers + QA** | `km-offer-rival-war-threat` + `km-offer-rival-standing-shift` handlers (digest, idempotent, GM-gated), offer templates, jsTest for offers, manual checklist. | `kingdom/ChatButtons.kt`, `chatmessages/rival-*-offer.hbs`, `RivalRealmEngineTest.kt` (offer cases) |
| **6 (deferred/optional)** | **Analytics rival overlay** | Snapshot leading-rival power into `RawTurnRecord` (+ its own migration), add `extractSeries` key, overlay series on the size/level charts. | `kingdom/data/RawTurnRecord.kt`, `kingdom/TurnHistory.kt` (`buildTurnRecord`), `kingdom/TurnAnalytics.kt`, `sections/analytics/*.hbs`, new migration |

**Dependencies:** 1 → 2 → 3 → {4, 5}. Phase 2 (pure) can start alongside Phase 1 (data). Phase 6 is optional and can ship anytime after Phase 3.

---

## 9. Open Questions for Gregory

1. **Player-row army count.** The player's kingdom has no single `armyCount`; do we count `armyDeployments`, or leave the player's Armies column blank / derived from war-pressure armies? (Standings still rank fine on size + fame.)
2. **Composite weights.** `size*10 + fame*2 + armyCount*5` is a first guess — tune so the ranking "feels right" for a level-10 kingdom vs Pitax?
3. **Standing-shift trigger threshold N.** How aggressive an expansion (size delta in one turn) should trigger the border-tension offer — 2? 3?
4. **Seed rivals on migration?** Auto-create Pitax/Brevoy stub rivals from known group names, or leave it fully GM-driven via the dialog (current plan)?
5. **Analytics overlay now or later?** Phase 6 is deferred; promote it into v1 if the "your curve vs the rival's" visual is high-value.

---

**End of Plan.** Ready for review. On approval, implementation cards follow the Phase 1–5 table (Phase 6 optional).

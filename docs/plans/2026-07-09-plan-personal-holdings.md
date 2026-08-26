# Plan: Personal Holdings & Titles — give each PC a private stake in the kingdom

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** New backlog / player-engagement lever (sibling to Petition Inbox)
> **Parent:** [`2026-07-09-plan-petition-inbox.md`](2026-07-09-plan-petition-inbox.md) — shares the "personal surface" framing; titles integrate with petition addressing. Its **pure core has landed** (`src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/petitions/Petitions.kt`: `PetitionStatus`, `Petition(… targetRole: Leader …)`, `ExpiryOutcome`, with `PetitionsTest`); the jsMain/UI half — persistence, templates, i18n keys — is still unbuilt, which is why the title bridge stays in Phase 5.
> **Depends on:** Faction & Diplomacy Relations Tracker (#1), Army & War Pressure Board (#12), Turn History gazette, `TurnTickingEngine` (monthly End Turn tick), Player-facing collaborative kingdom view (per-user ownership gating).
> **Branch:** `kingmaker.5`

---

## Executive Summary

Kingdom management in this module is a shared, abstract ledger: one RP pool, one commodity
pool, one unrest track that the whole table nudges together. Nothing on the sheet is *mine*.
A player who is the Warden has no personal reason to care whether the Narlmarches stays
claimed, and a player who is the Treasurer feels the kingdom's gold as a spreadsheet number,
not as their own purse.

**Personal Holdings** give each PC a *private stake*: a **title** (Baron of the Tuskwater, the
Silver Stag's Patron) and one or two **holdings** — a capital manor, a hunting lodge bound to a
claimed Narlmarches hex, a share in the Silver Stag tavern bound to a settlement structure.
Each holding yields a **small personal per-turn income** (gold / luxuries / favors) at End Turn,
and — critically — can be **damaged** when kingdom events, war threats, or raids touch its hex or
settlement. Kingdom outcomes now land on each player's **own** ledger.

Every mechanical benefit stays a **GM-confirmed offer** (`km-offer-…`): income is *offered* at
End Turn, damage is *offered*, repair is *offered*. Nothing writes to a PC's sheet silently.
This is deliberately **flavor + stakes, not an economy sim** — hard-capped at ~2 holdings per PC,
with modest numbers well below treasure-by-level.

---

## 1. Problem Statement + Player/GM Value

**Problem.** The house-rules doc is blunt about the failure mode this feature targets. From
`docs/house-rules.md` (Kingdom Management):

> "This might be the most labor-intensive system in this AP. If you run it as written, it will
> be terrible at low levels. […] My players often ran out of Leadership activities that they
> could perform, even with faster leveling. **One player left the campaign early because of
> kingdom management.**"

and:

> "**Kingdom Building**: If you don't put in work to add flavor, tangible benefits, lore and RP
> plus ramp up the pressure using Unrest and Ruin in certain parts […] **this will feel like
> managing an Excel spreadsheet.**"

The doc's own prescription is "Tie their kingdom and buildings to XP, loot and access to
feats/items" and to make the world push back on the players' choices. Today the module gives
the kingdom *collective* tangible benefits (structures, item access) but nothing that a single
player owns and can lose. There is no lever that makes *this hex, my hex* worth defending.

**Value to the table.**

- **Player buy-in (the single strongest lever).** A holding is a small, legible thing a player
  owns. "The raid is heading for the Tuskwater — that's *my* manor" converts an abstract war
  threat into a personal stake. This is the disengagement antidote the house-rules doc asks for.
- **Consequences with a face.** When a war threat expires on a settlement or a kingdom event
  sacks a hex, the fallout is no longer just +1 Unrest — a specific player's income stream drops
  to half until they pay to rebuild. Kingdom neglect has a personal cost.
- **Reward for claiming/holding land.** Modest per-turn income gives players a reason to claim,
  road, and defend specific hexes — reinforcing the exploration loop the AP already leans on.
- **GM prep reduction.** The GM grants a title + holding once (a dialog); thereafter income,
  damage, and repair surface as offer cards the GM clicks. No per-turn bookkeeping.
- **Roleplay hooks.** Titles feed the Petition Inbox (§ "Title mechanics"): NPCs address the
  "Baron of the Narlmarches," not just "the Warden."

---

## 2. Data Model

### 2.1 `RawPersonalHolding` (jsMain, `@JsPlainObject`, nullable for migration safety)

New file: `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/data/RawPersonalHolding.kt`
(package `at.posselt.pfrpg2e.kingdom.data`, next to `RawGroup.kt` / `RawWarThreat.kt`).

```kotlin
package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * A title + personal holding granted to one PC. Kingdom-scoped, GM-granted.
 * All fields non-null where the grant dialog always supplies them; genuinely
 * optional fields are nullable so older saves and partial grants load cleanly.
 */
@JsPlainObject
external interface RawPersonalHolding {
    var id: String                     // stable UUID

    // --- Ownership (see §2.3 for why actorUuid is authoritative) ---
    var actorUuid: String?             // the PC actor this holding belongs to (authoritative owner)
    var ownerUserId: String?           // denormalised Foundry user id cache (fast per-user filter)
    var ownerLabel: String?            // display fallback ("Valeria") when the actor can't resolve

    // --- Identity / flavor ---
    var title: String?                 // cosmetic title, e.g. "Baron of the Tuskwater" (null = untitled)
    var name: String                   // holding name, e.g. "Silverstead Manor"
    var kind: String                   // "manor" | "lodge" | "tavern-stake" | "farmstead" | "workshop" | "other"

    // --- Location binding (exactly one is meaningful; both nullable) ---
    var boundHexKey: String?           // realm-map hex key (matches RawSettlement.hexKey / kingmaker.state.hexes)
    var structureSceneId: String?      // settlement sceneId (matches RawSettlement.sceneId) for structure-bound holdings
    var structureRef: String?          // optional structure id/name within that settlement ("Tavern")

    // --- Economy ---
    var incomeTier: Int                // 1 = Modest, 2 = Comfortable, 3 = Lavish (see §3.2)
    var condition: String              // "sound" | "damaged" | "destroyed"

    // --- Bookkeeping / ledger ---
    var grantedTurn: Int?              // kingdom turn the holding was granted
    var lastIncomeTurn: Int?           // last turn the TICK accrued a line (idempotency stamp; moves whether or not the GM clicks)
    var incomeAwardedTurn: Int?        // last turn the GM actually clicked Award Income (idempotency for the handler, §5.1)
    var lifetimeIncomeGold: Int?       // durable ledger: total gp ACTUALLY awarded (bumped by the Award handler only, never by the tick)
    var lastEventLabel: String?        // last thing that happened ("Raided by Tiger Lords") for the card hint
    var lastKnownClaimed: Boolean?     // last observed kingmaker.state claim state of boundHexKey (edge detection for damage hook #4, §6)
    var visibleToPlayers: Boolean?     // house-rule visibility; null/true = the owner can see it
}
```

Design notes:

- `kind` and `condition` are **string discriminators**, mirroring the existing `RawGroup.relations`
  / `RawGroup.allianceLevel` / `RawWarThreat.status` convention — parsed into commonMain enums
  (§3) at the boundary, never switched on raw strings in logic.
- **Location is a soft union.** A hex-bound holding sets `boundHexKey`; a structure-bound holding
  sets `structureSceneId` (+ optional `structureRef`). Validation in the grant dialog requires
  exactly one; the engine treats "neither set" as an un-sited holding that still yields income but
  can never be damaged by location hooks.
- No `Record<String,…>` maps here (unlike faction agendas) — a holding is a flat record.

### 2.2 Persistence — `KingdomData.personalHoldings` (top-level array)

Add one nullable top-level field to `KingdomData` (`src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/KingdomData.kt`),
alongside the existing `groups: Array<RawGroup>`, `companions: Array<RawCharacter>?`,
`companionExpeditions`, `campaignClocks`, etc.:

```kotlin
// KingdomData (additions only — keep all existing fields)
var personalHoldings: Array<RawPersonalHolding>?   // null on legacy saves; Migration66 seeds []
```

**Why a top-level kingdom array (recommended) vs alternatives:**

| Option | Verdict | Reason |
|--------|---------|--------|
| **`KingdomData.personalHoldings` (top-level array, keyed by `actorUuid`/`ownerUserId`)** | ✅ **Recommended** | Kingdom-scoped state, reachable by `TurnTickingEngine` exactly like `groups`; one place the GM sees every holding; travels with the kingdom flag; participates in preview/commit parity. |
| Nest under each `RawCharacter` (companion roster) | ❌ | `companions: Array<RawCharacter>` is the **companion** roster (Ekundayo, Amiri…), not the PCs. Holdings belong to player characters, who have no `RawCharacter` entry here. |
| Store on the PF2e PC actor via Foundry `flags` | ❌ | Scatters state across actor documents the kingdom tick can't cheaply enumerate; breaks the pure/previewable tick (would need per-actor reads); GM can't see all holdings in one view; players could self-edit. |
| Reuse `RawGroup` | ❌ | Groups are *factions*, semantically wrong; would pollute the diplomacy list. |

Ownership is stored as `actorUuid` (authoritative) plus an `ownerUserId` cache — see §2.3.

### 2.3 Ownership model (grounded in `Leaders.kt`)

`getOwnedLeaderRoles` (`src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/Leaders.kt`) already resolves
"does the current user own this?" by taking a leader's linked actor UUID, calling
`fromUuidOfTypes(uuid, PF2ECharacter, PF2ENpc)`, and checking `actor.isOwner`. Personal Holdings
reuse that exact pattern:

- `actorUuid` is the **authoritative** owner key. The "My Holdings" card (§4) shows a holding when
  its `actorUuid` resolves to an actor with `isOwner == true` (GM sees all).
- `ownerUserId` is a **denormalised convenience cache** for a fast first-pass filter and for
  holdings whose actor can't currently resolve; it is never the sole source of truth.

### 2.4 Migration — `Migration66` *(next free at time of writing; re-derive from `Migrations.kt` when this lands)*

The chain currently ends at `Migration65`
(`src/jsMain/kotlin/at/posselt/pfrpg2e/migrations/migrations/Migration65.kt`, registered as the last
entry of `Migrations.kt`'s `internal val migrations = listOf(…)`);
`src/jsTest/kotlin/at/posselt/pfrpg2e/migrations/MigrationChainTest.kt:24` asserts contiguity with
`assertEquals((17..65).toList(), migrations.map { it.version })`. Versions 49–65 are all taken, so
this feature takes **66** and bumps that assertion to `(17..66)`.

> ⚠️ **The number in this section is a placeholder and must be re-derived at implementation.**
> The chain now ends at **`Migration65`**. Since these plans were written, four of the reserved
> numbers have LANDED: 62 = downtime-projects, 63 = scheduled-pressure-engine,
> 64 = map-dynamism, 65 = loot-manifests. `Migration49` was never free (it sits inside the
> long-registered 17..61 range) and several unimplemented plans still name it. The next free
> number is **66**. Take the next contiguous number when this actually lands, and extend
> `MigrationChainTest`'s hardcoded range.


New file `Migration66.kt`, following the `Migration65` template (idempotent, `dynamic`):

```kotlin
package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 66 — personal holdings & titles.
 * Seeds an empty personalHoldings array on kingdoms that predate the feature.
 */
class Migration66 : Migration(66) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.personalHoldings == null) kingdom.personalHoldings = arrayOf<Any?>()
    }
}
```

Wiring (three edits, matching how 65 was added):
1. `import …migrations.Migration66` in `Migrations.kt`.
2. Append `Migration66()` after `Migration65()` in `internal val migrations = listOf(…)`.
3. Update `MigrationChainTest`'s assertion to `(17..66).toList()`.

Non-breaking: `personalHoldings` is nullable; a null array is treated as "no holdings."

---

## 3. Engine Design

Following the house rule **pure logic → commonMain + commonTest, impure → jsMain + jsTest**.
`RawPersonalHolding` is a `@JsPlainObject` (JS-only), so the split is:

- **commonMain** holds all pure math over *plain Kotlin value types* (enums + data classes),
  fully unit-tested with zero JS/Foundry deps.
- **jsMain** holds thin adapters that read a `RawPersonalHolding`, call the pure core, and return
  a copied `Raw` object. (This is a deliberate correction of the faction-agenda plan, which put
  `@JsPlainObject`-referencing functions in commonMain where they cannot compile.)

### 3.1 Pure core — `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/PersonalHoldings.kt`

```kotlin
package at.posselt.pfrpg2e.data.kingdom

// Both enums carry their raw discriminator and a NULLABLE fromValue, the shape every fromValue in
// this module already uses (PetitionStatus at petitions/Petitions.kt:18, plus XpLedger, NpcMemory,
// PressureSchedule, DowntimeProjects, ForecastEngine, LifeEvents). The default for an unrecognised
// value is written at the call site in §3.3, not hidden inside the enum.
enum class HoldingTier(val value: Int, val goldPerLevel: Int, val luxuriesPerTurn: Int, val favorsPerTurn: Int) {
    MODEST(1, 2, 0, 0),
    COMFORTABLE(2, 5, 0, 1),
    LAVISH(3, 10, 1, 2);

    companion object {
        fun fromValue(value: Int?): HoldingTier? = entries.find { it.value == value }
    }
}

enum class HoldingCondition(val value: String) {
    SOUND("sound"), DAMAGED("damaged"), DESTROYED("destroyed");

    companion object {
        fun fromValue(value: String?): HoldingCondition? = entries.find { it.value == value }
    }
}

enum class DamageSeverity { MINOR, MAJOR }   // MINOR: one step; MAJOR: straight to destroyed

/** A turn's worth of personal income. Plain value type; mapped to offers/commodities in jsMain. */
data class HoldingIncome(val gold: Int, val luxuries: Int, val favors: Int) {
    val isEmpty get() = gold == 0 && luxuries == 0 && favors == 0
    operator fun plus(o: HoldingIncome) = HoldingIncome(gold + o.gold, luxuries + o.luxuries, favors + o.favors)
    companion object { val ZERO = HoldingIncome(0, 0, 0) }
}

/**
 * One PC's accrued line for the End Turn income digest (§3.4, §5.3). Primitives only, so it lives
 * in commonMain with the rest of the pure core and stays commonTest-able — even though
 * [TickResult] that carries it is itself a jsMain data class (`TurnTickingEngine.kt:92`).
 */
data class HoldingIncomeLine(
    val ownerUserId: String?,
    val ownerLabel: String,
    val holdingId: String,
    val holdingName: String,
    val gold: Int,
    val luxuries: Int,
    val favors: Int,
)

const val MAX_HOLDINGS_PER_PC = 2
private const val LEVEL_CAP = 20

/**
 * Which kingdom events damage a holding at their location, and how hard (damage hook #3, §6).
 * Keyed by the `id` SLUG in `data/events/*.json` — NOT the Title-Case filename. Held here as data
 * so the set is unit-testable in commonTest and extensible without touching `km-resolve-event`.
 * Every id below is a `dangerous` event in the shipped catalog.
 */
val HOLDING_DAMAGING_EVENT_IDS: Map<String, DamageSeverity> = mapOf(
    // MAJOR — the site is overrun or levelled outright.
    "undead-uprising" to DamageSeverity.MAJOR,          // continuous, dangerous, settlement
    "the-rampage-of-the-owlbear" to DamageSeverity.MAJOR, // dangerous, settlement
    "local-disaster" to DamageSeverity.MAJOR,           // dangerous, settlement
    "a-devil-comes-calling" to DamageSeverity.MAJOR,    // continuous, dangerous, settlement
    // MINOR — property is looted, spoiled or defaced, not destroyed.
    "bandit-activity" to DamageSeverity.MINOR,          // continuous, dangerous
    "monster-activity" to DamageSeverity.MINOR,         // dangerous, continuous, hex
    "crop-failure" to DamageSeverity.MINOR,             // dangerous, hex
    "sacrifices" to DamageSeverity.MINOR,               // dangerous, continuous, hex
    "too-close-to-home" to DamageSeverity.MINOR,        // dangerous, hex
    "vandals" to DamageSeverity.MINOR,                  // continuous, dangerous, settlement
    "feud" to DamageSeverity.MINOR,                     // continuous, dangerous, settlement
    "troll-sightings" to DamageSeverity.MINOR,          // continuous, dangerous
)

/** Base income for a SOUND holding at [tier], scaled by [level] (PC or kingdom level, capped at 20). */
fun holdingIncome(tier: HoldingTier, level: Int): HoldingIncome {
    val lvl = level.coerceIn(1, LEVEL_CAP)
    return HoldingIncome(
        gold = tier.goldPerLevel * lvl,
        luxuries = tier.luxuriesPerTurn,
        favors = tier.favorsPerTurn,
    )
}

/** Condition multiplier applied to base income: sound ×1, damaged ×½ (floored), destroyed ×0. */
fun conditionAdjustedIncome(tier: HoldingTier, level: Int, condition: HoldingCondition): HoldingIncome =
    when (condition) {
        HoldingCondition.SOUND     -> holdingIncome(tier, level)
        HoldingCondition.DAMAGED   -> holdingIncome(tier, level).let { HoldingIncome(it.gold / 2, it.luxuries / 2, it.favors) }
        HoldingCondition.DESTROYED -> HoldingIncome.ZERO
    }

/** Pure condition transition. Never throws; DESTROYED is terminal until repaired. */
fun nextConditionAfterDamage(current: HoldingCondition, severity: DamageSeverity): HoldingCondition =
    when {
        current == HoldingCondition.DESTROYED -> HoldingCondition.DESTROYED
        severity == DamageSeverity.MAJOR      -> HoldingCondition.DESTROYED
        current == HoldingCondition.SOUND     -> HoldingCondition.DAMAGED
        else                                  -> HoldingCondition.DESTROYED   // DAMAGED + MINOR -> DESTROYED
    }

/**
 * Flat gp cost to restore a holding one step toward SOUND. Deliberately **level-independent** —
 * income scales with level but repair does not, so a low-level PC can still afford to rebuild.
 * Against a mid-level (L10) turn of income that is ~1.5× to patch a DAMAGED holding and ~6× to
 * raise a DESTROYED one; because costs are flat and income is not, no single ratio holds at every
 * level, and L10 is the reference point `repairCost_matchesTable` (§7.1) is written against.
 */
fun repairCost(tier: HoldingTier, condition: HoldingCondition): Int = when (condition) {
    HoldingCondition.SOUND     -> 0
    HoldingCondition.DAMAGED   -> when (tier) { HoldingTier.MODEST -> 25;  HoldingTier.COMFORTABLE -> 75;  HoldingTier.LAVISH -> 150 }
    HoldingCondition.DESTROYED -> when (tier) { HoldingTier.MODEST -> 100; HoldingTier.COMFORTABLE -> 300; HoldingTier.LAVISH -> 600 }
}

/** One step toward SOUND (DESTROYED -> DAMAGED -> SOUND), so a rebuild is two paid repairs. */
fun repairedCondition(current: HoldingCondition): HoldingCondition = when (current) {
    HoldingCondition.DESTROYED -> HoldingCondition.DAMAGED
    HoldingCondition.DAMAGED   -> HoldingCondition.SOUND
    HoldingCondition.SOUND     -> HoldingCondition.SOUND
}
```

### 3.2 Income table (concrete)

Income is **per kingdom turn** (monthly, at End Turn). `level` = the owning PC's level (fallback:
kingdom level). Numbers are deliberately modest — a Lavish holding at level 10 pays 100 gp/turn
(~1,200 gp/year of downtime), far below party treasure-by-level, and a PC is capped at 2 holdings.

| Tier | `incomeTier` | Name | Examples | gp/turn | Luxuries | Favors |
|------|--------------|------|----------|---------|----------|--------|
| MODEST | 1 | Modest | farmstead, market stall, a lodge | `2 × level` | 0 | 0 |
| COMFORTABLE | 2 | Comfortable | a manor, a mill, a workshop | `5 × level` | 0 | 1 |
| LAVISH | 3 | Lavish | a tavern stake, a noble estate | `10 × level` | 1 | 2 |

Sample gp/turn (SOUND):

| Tier | L1 | L5 | L10 | L15 | L20 |
|------|----|----|-----|-----|-----|
| Modest | 2 | 10 | 20 | 30 | 40 |
| Comfortable | 5 | 25 | 50 | 75 | 100 |
| Lavish | 10 | 50 | 100 | 150 | 200 |

- **Damaged** → gold and luxuries halved (floored), favors kept (a damaged tavern still trades gossip).
- **Destroyed** → zero until rebuilt.
- **Favors** are a soft social currency (narrative; can feed Petition Inbox response options later).
  They are *not* a kingdom resource — no storage, no consumption. Kept as flavor.
- **Luxuries** map to the kingdom's existing Luxuries commodity **only when the GM applies the
  offer** (§5); accrual itself writes nothing.

### 3.3 jsMain adapters — `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/PersonalHoldingsJs.kt`

```kotlin
// Read a RawPersonalHolding's tier/condition into commonMain enums. The `?:` is the deliberate
// boundary choice: a save carrying a discriminator we no longer recognise degrades to the most
// conservative live value rather than throwing mid-tick. It is written HERE, at the one boundary,
// not buried inside the enum's fromValue (§3.1).
fun RawPersonalHolding.tierEnum(): HoldingTier =
    HoldingTier.fromValue(incomeTier) ?: HoldingTier.MODEST
fun RawPersonalHolding.conditionEnum(): HoldingCondition =
    HoldingCondition.fromValue(condition) ?: HoldingCondition.SOUND

/** Pure-cored, returns a COPIED Raw with condition advanced + lastEventLabel set (no mutation of input). */
fun applyHoldingDamage(holding: RawPersonalHolding, severity: DamageSeverity, label: String): RawPersonalHolding {
    val next = nextConditionAfterDamage(holding.conditionEnum(), severity)
    return RawPersonalHolding.copy(
        holding,
        condition = next.value,
        lastEventLabel = label,
    )
}

/** Per-turn income accrual for one holding, respecting idempotency via lastIncomeTurn. */
fun accrueIncome(holding: RawPersonalHolding, ownerLevel: Int, currentTurn: Int): HoldingIncome {
    if (holding.lastIncomeTurn == currentTurn) return HoldingIncome.ZERO   // already accrued this turn
    return conditionAdjustedIncome(holding.tierEnum(), ownerLevel, holding.conditionEnum())
}
```

`RawPersonalHolding.copy(instance, field = …)` is the auto-generated `@JsPlainObject` copy. Note
the shape: it is the **static/companion** form taking the instance as the first argument, not an
instance method. That is the form used at every `@JsPlainObject` copy site in the repo —
`RawGroup.copy(group, standing = after, standingLog = newLog)` and
`RawConsumption.copy(newConsumption, now = …)` in `TurnTickingEngine.kt` (`:446`, `:374`),
`RawArmyBattle.copy(battle, status = ARCHIVED_BATTLE_STATUS)` (`:420`),
`MilestoneChoice.copy(it, offerDismissed = true)` in `ChatButtons.kt`. Writing
`holding.copy(condition = …)` would **not compile**: the instance-form `.copy` that does appear in
jsMain (e.g. `state.copy` at `kingdom/ArmyBattleView.kt:95`) belongs to Kotlin data classes, never to
`@JsPlainObject` external interfaces.

### 3.4 `TurnTickingEngine` surface

`TurnTickingEngine.tick()` (`src/jsMain/kotlin/.../kingdom/TurnTickingEngine.kt`) is the monthly
End Turn tick and is **preview/commit-safe** (deterministic, Foundry-free — see its own KDoc at
`TurnTickingEngine.kt:142`, "The engine contains no Foundry/Game dependencies"). Personal income
accrues **here** — never in `kingdom/DailyTickHooks.kt` (the daily world clock stays untouched).

Add to `tick(...)`:

```kotlin
// new params (defaulted, so existing call sites and tests keep compiling)
personalHoldings: Array<RawPersonalHolding> = emptyArray(),
holdingOwnerLevels: Map<String, Int> = emptyMap(),   // actorUuid -> level, resolved impurely before tick
```

Add to `TickResult` (mirroring how `groups` / `warThreatOffers` were added):

```kotlin
val updatedPersonalHoldings: Array<RawPersonalHolding> = emptyArray(),  // lastIncomeTurn advanced (NOT lifetimeIncomeGold)
val holdingIncomeOffers: Array<HoldingIncomeLine> = emptyArray(),        // per-PC accrued income for the offer card
```

`HoldingIncomeLine` is the commonMain data class declared in §3.1
(`data/kingdom/PersonalHoldings.kt`) — primitives only, so the digest-assembly math is testable in
commonTest even though `TickResult` that carries it is a jsMain data class
(`TurnTickingEngine.kt:92`).

**What the tick advances, and what it deliberately does not.** The tick advances **only**
`lastIncomeTurn` — the idempotency stamp, which is safe to move whether or not the GM ever acts —
and emits the `holdingIncomeOffers` lines. It does **not** touch `lifetimeIncomeGold`. That ledger
records gold *actually handed over*, and the whole premise of §5 is that the GM may dismiss the
card; advancing it in the tick would have the lifetime ledger count gold nobody ever received.
`lifetimeIncomeGold` is bumped **only** by the `km-offer-holding-income` Award handler (§5.1),
which stamps `incomeAwardedTurn = currentTurn` for double-click idempotency — the same shape
`awardLootManifest` uses with `content.manifestAwarded` / `content.manifestAwardedTurn`
(`kingdom/loot/LootAward.kt:127` and `:179-180`). The tick posts nothing.

**Thread the arguments through `runKingdomTurnTick` — never call `tick()` directly.**
`TurnWizardApplication.kt:207` declares

```kotlin
fun runKingdomTurnTick(kingdom: KingdomData, storage: CommodityStorage, currentTurn: Int): TickResult
```

and its KDoc (`:201-206`) is unambiguous: it is the "Single source of truth for assembling
[TurnTickingEngine.tick] arguments from a kingdom snapshot. Both the End Turn commit path
([performEndTurn]) and the Turn Wizard preview MUST call this — never tick() directly." All three
live callers go through it — `performEndTurn` (`TurnWizardApplication.kt:296`), the Turn Wizard
preview (`previewTurn()`, `:1162`, calling at `:1175`), and `ForecastAdapter.kt:148` — and `TurnWizardApplicationTest.kt:82`
(`testRunKingdomTurnTickForwardsEverySubsystemToTheEngine`) guards the contract. **This matters
because every new `tick()` parameter above is defaulted:** adding them to `tick()` alone compiles,
runs green, and yields zero holdings income in *both* preview and commit, with a naive
`tick_previewCommitParity` passing vacuously on two empty arrays. So Phase 2 must also:

1. Forward `personalHoldings = kingdom.personalHoldings ?: emptyArray()` inside
   `runKingdomTurnTick`'s `TurnTickingEngine.tick(…)` argument list. It is already reachable from
   the `kingdom` snapshot, so this needs no new parameter.
2. Add **one** new parameter to `runKingdomTurnTick` itself:
   `holdingOwnerLevels: Map<String, Int> = emptyMap()`, forwarded straight to `tick()`. It cannot
   be derived inside the function: `runKingdomTurnTick` is non-suspend and pure over
   `(kingdom, storage, currentTurn)`, while resolving `actorUuid → level` needs the suspend
   `fromUuidOfTypes(uuid, PF2ECharacter::class)` (`utils/Document.kt:109` —
   `suspend inline fun <T : Document> fromUuidOfTypes(...)`, the same call `getOwnedLeaderRoles`
   makes at `Leaders.kt:32`).
3. Each caller resolves the map impurely *before* the call: `performEndTurn`
   (`suspend fun performEndTurn(game, actor, kingdom): TickResult?`, `:250`) and `buildForecast`
   (`suspend`, `ForecastAdapter.kt:98`) can await directly; `previewTurn()` (`private suspend fun`, `:1162`) does the
   same. A caller that cannot resolve an actor passes no entry for it and the
   engine falls back to `kingdom.level` for that holding (§9 Q3), so preview and commit still agree.

Damage is **not** applied inside the tick's income pass — it originates from war-threat expiry,
event resolution, and raids, each of which already runs at End Turn or on GM action (§6), and each
emits its own damage **offer** rather than mutating a holding directly.

---

## 4. UI Design

### 4.1 "My Holdings" — per-user card on the Kingdom Sheet

- **Placement (decided).** A new `src/jsMain/resources/applications/kingdom/sections/holdings/`
  directory with `page.hbs` plus a reusable `holding-card.hbs`, rendered **inside the existing
  `character-sheet` tab** — the only per-PC surface the sheet has. No nav entry is added. (There is
  no "holdings"/"personal" area to reuse: `sections/` today contains exactly analytics,
  army-pressure, character-sheet, clocks, expeditions, modifiers, notes, pacing-alerts, party,
  quests, roster, session-prep, settlements, trade-agreements, turn.) If a nav entry is ever wanted
  later, the main nav class is `.km-tabs`.
- **Both templates are registered partials, referenced by NAME.** Add
  `"kingdom-holdings" to "applications/kingdom/sections/holdings/page.hbs"` and
  `"kingdom-holding-card" to "applications/kingdom/sections/holdings/holding-card.hbs"` to the
  `loadTemplatePartials(arrayOf(…))` array in `Main.kt` (~line 134, next to the existing
  `"kingdom-character-sheet"` entries), then reference them as `{{> kingdom-holding-card}}` — never
  by path. An unregistered partial fails to render with "partial X could not be found".
- **Inside `holding-card.hbs` use `@root`, never `../`.** A partial gets no frame above its own
  context, so within `{{#each holdings}}` a sheet-level flag must be read as `@root.isGM`;
  `{{#if ../isGM}}` is silently falsy and ships dead GM buttons — this is the exact bug
  `scripts/check_hbs_scope.py` was written for, and CI runs it
  (`.github/workflows/test.yml:36`), failing the build on `../` chains deeper than the file's own
  block nesting. Cheapest route: `HoldingCardContext` already carries its own `isGM`/`isOwner`
  (§4.3), so the card needs no parent lookup at all.
- **Per-user filtering.** A player sees only holdings they own (resolved via the `Leaders.kt`
  `actor.isOwner` pattern, §2.3); the **GM sees all**, grouped by owner, with grant/manage controls.
  This mirrors the shipped player-facing collaborative view.
- Each card shows: title (if any) + holding name, `kind` icon, location (hex label or settlement +
  structure), **condition chip** (sound/damaged/destroyed, colour-coded like the diplomacy attitude
  chips), this turn's projected income, and `lastEventLabel` as a one-line hint ("Raided by the
  Tiger Lords last turn").
- **Single root element** per template (ApplicationV2 requirement noted in project memory).

### 4.2 GM grant dialog — `GrantHolding.kt`

New file `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/dialogs/GrantHolding.kt`, extending
`at.posselt.pfrpg2e.app.FormApp` with a `@JsPlainObject HoldingFormData` interface plus a
`buildSchema { … }` `DataModel` validator and `formContext`/`Select`/`TextInput`/`NumberInput`
fields — exactly as `AddWarThreat.kt` does (`:3-27` imports, `:30-42` `WarThreatFormData`,
`:44-60` the `WarThreatDataModel.defineSchema()` block), and as `ModifyFactionStanding.kt` does.
Foundry's own `FormApplication` class is not used anywhere in this repo. Fields:

- Owner: a select of PC actors (the players' assigned leader actors; free-text `ownerLabel` fallback).
- Title (free text, optional), holding name, `kind` (select).
- Location: radio between **hex** (a `hexKey` select populated from `kingmaker.region.hexes`, same
  source `HexContentManager` uses) and **settlement structure** (`sceneId` select from
  `kingdom.settlements` + optional structure ref).
- `incomeTier` (Modest / Comfortable / Lavish).
- **Guardrail:** the dialog refuses to grant a 3rd holding to a PC already at `MAX_HOLDINGS_PER_PC`
  (2), surfacing an i18n error — enforcing the "flavor, not economy sim" discipline in the UI.

GM-only management actions on each card (all `if (!game.user.isGM) return`): edit, change tier,
force condition (sound/damage/destroy for narrative reasons), transfer owner, revoke. These live in
the **sheet** DOM, so each is a `data-action` button dispatched through `KingdomSheet`'s
`_onClickAction` override (`sheet/KingdomSheet.kt:544`) — `data-action="holding-edit"`,
`"holding-set-tier"`, `"holding-force-condition"`, `"holding-transfer"`, `"holding-revoke"` — and
**not** `ChatButton`s. See the binding note at the head of §5.

### 4.3 Context objects — `PersonalHoldingsContext.kt`

`src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/contexts/PersonalHoldingsContext.kt`:

```kotlin
@JsPlainObject
external interface PersonalHoldingsSectionContext {
    val holdings: Array<HoldingCardContext>
    val isGM: Boolean
    val canGrant: Boolean            // isGM
    val emptyLabel: String
}

@JsPlainObject
external interface HoldingCardContext {
    val id: String
    val title: String?
    val name: String
    val kindLabel: String            // localized
    val ownerLabel: String
    val locationLabel: String        // hex label or "Silver Stag, Tuskwater"
    val conditionValue: String       // "sound" | "damaged" | "destroyed"
    val conditionLabel: String       // localized
    val incomeLabel: String          // "50 gp, 1 luxury, 2 favors / turn"
    val lastEventLabel: String?
    val isOwner: Boolean
    val isGM: Boolean
}
```

### 4.4 i18n namespace

Nested under `pf2e-kingmaker-tools` in `lang/en.json` (i18next nested paths — **never** flat-dotted;
wired through `initLocalization()`). All keys under `kingdom.personalHoldings.*`:

```json
"kingdom": {
  "personalHoldings": {
    "title": "My Holdings",
    "empty": "You hold no titles or lands yet.",
    "grant": "Grant Holding",
    "kind": { "manor": "Manor", "lodge": "Hunting Lodge", "tavern-stake": "Tavern Stake",
              "farmstead": "Farmstead", "workshop": "Workshop", "other": "Holding" },
    "tier": { "1": "Modest", "2": "Comfortable", "3": "Lavish" },
    "condition": { "sound": "Sound", "damaged": "Damaged", "destroyed": "Destroyed" },
    "income": "{{gold}} gp, {{luxuries}} luxuries, {{favors}} favors / turn",
    "maxReached": "This character already holds the maximum of {{max}} holdings.",
    "offer": {
      "incomeTitle": "Holdings Income",
      "incomeBody": "{{owner}} accrued {{gold}} gp from {{name}} this turn.",
      "award": "Award Income",
      "damageTitle": "Holding Damaged",
      "damageBody": "{{name}} ({{owner}}) was {{severity}} by {{cause}}.",
      "applyDamage": "Apply Damage", "waive": "Waive",
      "repairTitle": "Repair Holding",
      "repairBody": "Restore {{name}} for {{cost}} gp?", "repair": "Pay & Repair"
    }
  }
}
```

**Build the labels from LITERAL keys.** `kindLabel` / `conditionLabel` / the tier label are
assembled by an exhaustive `when` in `PersonalHoldingsContext.kt` —
`when (kind) { "manor" -> t("kingdom.personalHoldings.kind.manor"); …; else -> t("kingdom.personalHoldings.kind.other") }`
— never `t("kingdom.personalHoldings.kind.$kind")`. `check_i18n_keys.py`'s `is_dynamic()` guard
skips any key containing `$`, so an interpolated key is invisible to the checker and renders raw in
the UI the first time a value has no entry. The in-repo note at
`kingdom/pressure/PressureDigest.kt:68-69` says exactly this. (The numeric `"1"/"2"/"3"` tier
sub-keys are legal and resolve fine; they only need renaming if word keys read better.)

**Every key above must be added to all 8 locales** — `lang/{de,en,fr,it,pl,pt-BR,ru,zh-Hans}.json`
— with identical nesting and identical `{{placeholder}}` names. CI runs
`python3 scripts/check_i18n_keys.py --all` (`.github/workflows/test.yml:27`), whose cross-language
parity check (check 5) computes `missing = en_keys - lang_keys` per locale and counts every missing
key as a problem, so an en-only key set fails the build.

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

Every mechanical benefit is a **GM-confirmed offer** posted via `postChatTemplate(templatePath =
"chatmessages/holding-*.hbs", templateContext = …)` (the exact pattern `postQuestDeadlineOffer` uses)
with `data-*` attributes handled by new `ChatButton("km-offer-holding-…")` handlers in
`src/jsMain/kotlin/.../kingdom/ChatButtons.kt`. Each handler starts `if (!game.user.isGM) return@ChatButton`
and is idempotent.

> **Binding rule — chat cards and sheet buttons are not interchangeable.** `bindChatButtons`
> (`ChatButtons.kt:1427`) registers every `ChatButton` inside
> `TypedHooks.onRenderChatLog { … bindChatClick(".${data.buttonClass}") … }`, i.e. against the
> **#chat sidebar only**. A `km-offer-holding-…` class on a button that renders in the sheet DOM
> therefore never receives its click. The file carries two in-code warnings about precisely this
> failure (`:1408-1411` and `:1416-1418`). So: the `km-offer-holding-…` ids below are live **only**
> inside `chatmessages/*.hbs`. Every control on the "My Holdings" sheet card — Repair, edit, tier,
> force-condition, transfer, revoke (§4.2) — is a `data-action` button dispatched through
> `KingdomSheet._onClickAction` (`sheet/KingdomSheet.kt:544`). The sheet's Repair action does not
> carry the ChatButton id itself; it **posts** the `holding-repair-offer.hbs` card, and the GM
> confirms on that card.

### 5.1 Offer catalog

| Trigger | Template | ChatButton id(s) | Handler behavior |
|---------|----------|------------------|------------------|
| **Income accrued** at End Turn (`TickResult.holdingIncomeOffers` non-empty) | `chatmessages/holding-income-offer.hbs` | `km-offer-holding-income` / dismiss | GM clicks **Award Income** → posts a public award line, bumps `lifetimeIncomeGold` by the awarded gp, and stamps `incomeAwardedTurn = currentTurn` so a re-click or a re-posted card is a no-op. This handler is the **only** writer of `lifetimeIncomeGold` (§3.4). Optionally, GM-gated, it also writes coins to the resolved PC actor via `actor.asDynamic().inventory.addCoins(coins)`. **Default is the chat award; no silent PC-inventory write.** |
| **Holding damaged** by war threat / event / raid (§6) | `chatmessages/holding-damage-offer.hbs` | `km-offer-holding-damage` / `km-waive-holding-damage` | **Apply Damage** → `applyHoldingDamage(holding, severity, cause)`, `actor.setKingdom(kingdom)`, post confirmation. **Waive** → no state change, records nothing. |
| **Repair** — posted by the sheet's `data-action="holding-repair"` handler (see the binding rule above), or auto-offered at End Turn while a damaged/destroyed holding exists | `chatmessages/holding-repair-offer.hbs` | `km-offer-holding-repair` | **Pay & Repair** → deducts `repairCost(tier, condition)` (from kingdom treasury RP-equivalent or PC gold, GM's choice in the card), sets `repairedCondition(current)`, saves. |

### 5.2 Where personal gold income lands — **RECOMMENDATION**

**Recommend an OFFER the GM applies, not a silent PC-inventory write.** Rationale:

- The module's ironclad rule is that anything granting a mechanical benefit is a GM-confirmed offer
  (`km-offer-*`). A silent write to a player's coin purse would be the first exception — and the one
  most likely to cause "where did this gold come from?" confusion mid-session.
- The two ledger fields are split on purpose (§3.4). The pure tick advances **`lastIncomeTurn`**
  only — that is the idempotency stamp, correct to move whether or not the GM acts, and it keeps
  accrual previewable. **`lifetimeIncomeGold`** records gold that actually changed hands, so it is
  bumped only by the Award click; otherwise the lifetime ledger would count gold the GM dismissed.
- **The shipped template to copy is `km-offer-loot-award` → `awardLootManifest`**
  (`kingdom/loot/LootAward.kt:124`, gated at `ChatButtons.kt:400`): GM-gated handler, one
  idempotency flag on the record (`content.manifestAwarded`, checked at `:127`, set with
  `manifestAwardedTurn` at `:179-180`), *then* the actor write. Personal Holdings mirrors that
  shape with `incomeAwardedTurn`. Note `awardLootManifest` moves items into the **party stash** on
  a GM click — it is not a counter-example to the rule above, which is about *silent* writes.
- On **Award Income**, the handler may — GM-gated and opt-in — call
  `actor.asDynamic().inventory.addCoins(coins)` on the resolved PC actor. That `asDynamic()` form
  is deliberate and is the repo's only coin-write shape (`sheet/KingdomSheet.kt:2711`); `inventory`
  is not on the typed PF2e actor bindings, so `actor.inventory.addCoins(…)` will not compile.
  (`km-offer-companion-levelup`, `ChatButtons.kt:1061`, uses `typeSafeUpdate` — but for a typed
  field, level, not coins.)
  The **default and safe path remains a public chat award** the GM reads out, keeping the module's
  zero-silent-write invariant intact.

### 5.3 Digest discipline

One **income digest card per End Turn** listing every PC's accrued line (not one card per holding),
mirroring the faction-moves digest — avoids chat spam when several PCs have holdings.

---

## 6. Interactions With Existing Systems

Concrete files and the exact damage hook points.

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Turn tick / income** | `kingdom/TurnTickingEngine.kt`, `kingdom/dialogs/TurnWizardApplication.kt` (`runKingdomTurnTick` at `:207`, `performEndTurn` at `:250`), `kingdom/forecast/ForecastAdapter.kt:148` | Income accrues in `tick()`, but the arguments are assembled **only** in `runKingdomTurnTick` — the documented single source of truth (§3.4); `tick()` is never called directly. `performEndTurn` reads `TickResult.holdingIncomeOffers` and posts the income digest offer. Preview/commit parity preserved (accrual is deterministic; offers post only on commit). |
| **War & War Pressure (damage hook #1)** | `kingdom/ArmyWarPressure.kt`, `kingdom/data/RawWarThreat.kt` | When a threat's escalation hits max and fires (unless `pauseOnExpiry`), match `RawWarThreat.targetHexLocation` against each holding's `boundHexKey`, and `targetSettlementSceneId` against each holding's `structureSceneId`. Matching holdings → emit a **`km-offer-holding-damage`** (severity MAJOR for a triggered siege, MINOR for a raid tick). |
| **Siege / settlement damage (damage hook #2)** | `kingdom/SiegeOffer.kt`, `commonMain/.../kingdom/SiegeDamage.kt`, `commonMain/.../kingdom/GarrisonDefense.kt`, `ChatButtons.kt` (the `"sack"` branch of `ChatButton("km-offer-war-threat-arrival")`, `:242`, razing at `:319-320`) | **Siege sacking already ships — there is nothing to wait for.** `calculateSiegeDamage` (`SiegeDamage.kt:57`) and `siegeDamageWithGarrison` (`GarrisonDefense.kt:44`) pick how many structures fall; `siegeTargetsFor(game, kingdom, sceneId)` (`SiegeOffer.kt:27`) names them; the handler appends the razed token ids to `settlement.destroyedStructureIds`. Hook the holding-damage offer into that same handler: after a sack of settlement `sceneId`, emit `km-offer-holding-damage` (severity **MAJOR**) for every holding whose `structureSceneId == sceneId`. No new subscription mechanism, and no cross-card coordination — this is ordinary Phase 4 work. |
| **Kingdom events (damage hook #3)** | `ChatButtons.kt` (`km-resolve-event` at `:157`), `data/events/`, `commonMain/.../data/kingdom/PersonalHoldings.kt` | Driven by an explicit id set, not by a description. Catalog events carry an `id` **slug** plus `traits` — there is no "sack/burn/raid" marker to branch on — so the selection lives in the `HOLDING_DAMAGING_EVENT_IDS: Map<String, DamageSeverity>` constant declared in §3.1: **MAJOR** on `undead-uprising`, `the-rampage-of-the-owlbear`, `local-disaster`, `a-devil-comes-calling`; **MINOR** on `bandit-activity`, `monster-activity`, `crop-failure`, `sacrifices`, `too-close-to-home`, `vandals`, `feud`, `troll-sightings`. When `km-resolve-event` resolves an event whose `event.id` is a key in that map, match the resolved event's location against each holding's `boundHexKey` or `structureSceneId` and emit a follow-on `km-offer-holding-damage` at the mapped severity. **Do not branch on `hex`/`settlement` traits** — most entries in the map carry neither (`bandit-activity` and `troll-sightings` are both `['continuous','dangerous']`, verified in `data/events/`), so a trait test would silently skip them. The id set IS the selector; location matching falls back to "offer against every holding the GM can see" when the resolved event carries no location. Keeping the set as commonMain data makes it unit-testable and extensible without touching the handler. |
| **Hex claim state (damage hook #4)** | `kingmaker.state.hexes[hexKey].claimed` (read exactly as `camping/CampingUtils.kt:51-52` does), `kingdom/dialogs/TurnWizardApplication.kt` (`performEndTurn`) | A hex-bound holding whose `boundHexKey` **becomes** unclaimed offers MINOR damage. "Becomes" needs the previous value, which is why `RawPersonalHolding.lastKnownClaimed` exists (§2.1) — without it the hook can only see *is* unclaimed and would re-offer damage every single turn. The check runs **impurely in `performEndTurn`, after the tick**, alongside the other damage-offer emissions — never inside `tick()`, which stays Foundry-free (`TurnTickingEngine.kt:142`). Read `kingmaker.state.hexes[boundHexKey]?.claimed == true`, emit the offer only on a `true → false` transition, then write the observed value back to `lastKnownClaimed`. Read-only against hex state; no change to `tick()` or `runKingdomTurnTick`. |
| **Structures / settlements** | `commonMain/.../modifiers/evaluation/EvaluateStructures.kt`, `kingdom/structures/RawSettlement.kt` | Structure-bound holdings reference a settlement `sceneId` (+ optional `structureRef`). `EvaluateStructures` is **not modified** — holdings read settlement identity for display/binding only; they do not add kingdom-wide structure bonuses. |
| **Titles ↔ leadership roles** | `commonMain/.../leaders/Leader.kt`, `kingdom/Leaders.kt` | `title` is **cosmetic** and independent of the `Leader` role; a PC can hold a title without a role and vice-versa. Ownership resolution reuses `getOwnedLeaderRoles`' `actor.isOwner` mechanism. |
| **Petition Inbox (parent)** | [`2026-07-09-plan-petition-inbox.md`](2026-07-09-plan-petition-inbox.md) | Petitions today address a `targetRole: Leader`. This plan lets a petition **also** be addressed to a titled holder: the petition greeting can interpolate `holding.title` ("To the Baron of the Tuskwater…"), and holdings become a natural petition subject ("your tenants at Silverstead petition…"). Integration is a one-field bridge (surface `title` to the petition template); the systems ship independently. |
| **Turn History gazette** | `kingdom/TurnHistory.kt` | Optional: a holding damaged/destroyed emits one public gazette line ("Silverstead Manor was raided"). Low priority; can defer to a follow-up. |
| **Daily tick** | `kingdom/DailyTickHooks.kt` | **No interaction.** Personal income is monthly (End Turn) only. |

### 6.1 Explicit OUT-OF-SCOPE

- **No holding-vs-holding economy, no markets, no upkeep costs.** Income is one-way flavor; there is
  no supply chain, no rent modeling, no depreciation beyond damage. This is the "not an economy sim"
  line, held deliberately.
- **No new kingdom-wide bonuses.** A holding never grants item bonuses, skill bonuses, or structure
  effects — `EvaluateStructures` is untouched.
- **No auto-grant.** Milestone/deed-triggered granting (§8, Phase 5) is an optional later phase; the
  MVP is GM-dialog grant only.
- **No token/tile placement** for holdings on the map (they are bound *logically* to a hex/settlement).
- **No silent PC-inventory writes** (see §5.2).
- **No cap above 2 holdings/PC**, and no per-settlement stockpile modeling.

---

## 7. Test Plan

### 7.1 commonTest (pure, JVM/JS-agnostic) — `src/commonTest/kotlin/at/posselt/pfrpg2e/data/kingdom/PersonalHoldingsTest.kt`

| Test | Asserts |
|------|---------|
| `holdingIncome_scalesByTierAndLevel` | Modest/Comfortable/Lavish at L1/L5/L10/L20 match §3.2 table exactly. |
| `holdingIncome_capsLevelAt20` | Level 25 yields the same as level 20. |
| `holdingIncome_floorsAtLevel1` | Level 0 / negative treated as 1. |
| `conditionAdjustedIncome_damagedHalvesGoldAndLuxuries` | Damaged Lavish@L10 = 50 gp, 0 luxuries (1/2 floored), 2 favors. |
| `conditionAdjustedIncome_destroyedIsZero` | Destroyed → `HoldingIncome.ZERO`. |
| `nextConditionAfterDamage_soundMinorToDamaged` | SOUND + MINOR → DAMAGED. |
| `nextConditionAfterDamage_damagedMinorToDestroyed` | DAMAGED + MINOR → DESTROYED. |
| `nextConditionAfterDamage_majorToDestroyed` | Any + MAJOR → DESTROYED. |
| `nextConditionAfterDamage_destroyedIsTerminal` | DESTROYED + any → DESTROYED. |
| `repairCost_matchesTable` | Damaged/Destroyed costs per tier match §3.1. |
| `repairedCondition_stepsOneLevel` | DESTROYED→DAMAGED→SOUND (rebuild = two repairs). |
| `holdingIncome_plus_accumulates` | `HoldingIncome` addition sums fields. |
| `damagingEventIds_areRealCatalogSlugs` | Every key of `HOLDING_DAMAGING_EVENT_IDS` is lowercase-slug shaped and maps to a `DamageSeverity`; the MAJOR/MINOR split matches §6 hook #3. |

### 7.2 jsTest (Foundry-integrated) — `src/jsTest/kotlin/at/posselt/pfrpg2e/kingdom/PersonalHoldingsJsTest.kt`

| Test | Asserts |
|------|---------|
| `applyHoldingDamage_returnsCopyWithoutMutating` | Input `RawPersonalHolding` unchanged; result has advanced condition + `lastEventLabel`. |
| `accrueIncome_idempotentWithinTurn` | Second `accrueIncome` for the same `currentTurn` returns ZERO. |
| `tick_advancesLastIncomeTurnOnly` | `TurnTickingEngine.tick()` with 2 holdings populates `holdingIncomeOffers` and advances `lastIncomeTurn` — and leaves `lifetimeIncomeGold` **unchanged** (that field is the Award handler's, §3.4). |
| `runKingdomTurnTick_returnsHoldingIncomeOffers` | Calling `runKingdomTurnTick(kingdom, storage, turn)` — **not** `tick()` — on a kingdom with holdings returns a NON-EMPTY `holdingIncomeOffers`. Guards against the defaulted-parameter no-op: without the §3.4 threading this fails while `tick()`-level tests still pass. |
| `awardIncome_bumpsLifetimeGoldOnceOnly` | The `km-offer-holding-income` handler bumps `lifetimeIncomeGold` and stamps `incomeAwardedTurn`; a second click for the same turn is a no-op. |
| `tick_previewCommitParity` | Preview and commit ticks produce identical `updatedPersonalHoldings` + `holdingIncomeOffers`, both non-empty. |
| `tick_destroyedHoldingYieldsNoIncome` | Destroyed holding contributes no offer line. |
| `context_filtersByOwnerForPlayers` | `PersonalHoldingsSectionContext` shows only owned holdings for a non-GM; GM sees all. |
| `grant_rejectsThirdHolding` | Grant path enforces `MAX_HOLDINGS_PER_PC`. |
| `hexUnclaimed_offersDamageOnceOnTransition` | A holding with `lastKnownClaimed = true` whose hex reads unclaimed emits one MINOR offer and writes `lastKnownClaimed = false`; a second pass emits nothing (hook #4, §6). |
| `migration66_seedsEmptyArray` | Kingdom without `personalHoldings` gets `[]`; existing array untouched. |
| `migrationChain_contiguous` | `MigrationChainTest`'s range assertion, bumped to `(17..66)`, still passes. |

### 7.3 Manual Foundry verification checklist

1. Open Kingdom Sheet as GM → **Grant Holding** → grant "Silverstead Manor" (Comfortable, hex-bound) to a PC actor with title "Baron of the Tuskwater."
2. Grant a 2nd holding to the same PC (structure-bound, Lavish); attempt a 3rd → blocked with the max-reached message.
3. Log in as that PC's player → **My Holdings** shows exactly their 2 holdings, income projected; other PCs' holdings hidden.
4. **End Turn** → an income digest offer card appears (GM-whispered). *Before* clicking, confirm `lifetimeIncomeGold` is **unchanged** (only `lastIncomeTurn` moved). Then click **Award Income** → public award posts and `lifetimeIncomeGold` increases; re-clicking does nothing (`incomeAwardedTurn` guard).
5. Create a war threat targeting the holding's hex; escalate to trigger → a **Holding Damaged** offer appears. Click **Apply Damage** → card condition flips to Damaged; income halves on the sheet.
6. Click **Waive** on a second damage offer → no change.
7. On the sheet's holding card click **Repair** → confirm the `data-action="holding-repair"` handler actually **posts a chat card** (it is not a `ChatButton`; a `km-offer-…` class in the sheet DOM would never fire — §5 binding rule). Then click **Pay & Repair** on that card → cost deducted, condition returns to Sound.
8. Destroy a holding (major damage) → income 0; repair twice (Destroyed→Damaged→Sound).
9. Unclaim the bound hex (`kingmaker.state`) → next End Turn offers MINOR damage **exactly once**; End Turn again with the hex still unclaimed → no second offer (`lastKnownClaimed` already false).
10. Reload the world → holdings, conditions, ledger, and titles persist.
11. Confirm all text resolves via i18n (no raw keys) and that the new keys were added to **all 8** locale files; `python3 scripts/check_i18n_keys.py --all` clean (the `--all` form is what CI runs — it includes the cross-language parity check that an en-only key set fails).

Build/verify per AGENTS.md: `python3 scripts/check_i18n_keys.py --all` then
`JAVA_HOME=<jdk25> ./gradlew assemble jsTest -x kotlinStoreYarnLock` (Chrome headless).

---

## 8. Phasing (independently committable)

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Pure core + data + migration** | `HoldingTier`/`HoldingCondition`/`DamageSeverity`/`HoldingIncome`/`HoldingIncomeLine` + `HOLDING_DAMAGING_EVENT_IDS` + all pure functions in commonMain with full `PersonalHoldingsTest`; `RawPersonalHolding`; `KingdomData.personalHoldings`; `Migration66` + chain-test bump to `(17..66)`. | `commonMain/.../data/kingdom/PersonalHoldings.kt`, `PersonalHoldingsTest.kt`, `kingdom/data/RawPersonalHolding.kt`, `KingdomData.kt`, `migrations/migrations/Migration66.kt`, `Migrations.kt`, `MigrationChainTest.kt` |
| **2** | **Tick integration + income offer** | jsMain adapters (`PersonalHoldingsJs.kt`); `tick()` accrual + `TickResult` fields; **threading through `runKingdomTurnTick` (`TurnWizardApplication.kt:207`) plus its new `holdingOwnerLevels` param, resolved by each of its three callers** (§3.4) — without this the defaulted params silently no-op; `performEndTurn` posts the income digest offer; `km-offer-holding-income` handler (sole writer of `lifetimeIncomeGold`) + template; jsTest for accrual/parity/idempotency **including `runKingdomTurnTick_returnsHoldingIncomeOffers`**. | `PersonalHoldingsJs.kt`, `TurnTickingEngine.kt`, `TurnWizardApplication.kt`, `forecast/ForecastAdapter.kt`, `ChatButtons.kt`, `chatmessages/holding-income-offer.hbs`, `PersonalHoldingsJsTest.kt` |
| **3** | **UI — My Holdings card + GM grant dialog** | Per-user section + card (registered as partials in `Main.kt`, `@root` not `../`), `PersonalHoldingsContext`, `GrantHolding` dialog (`FormApp` + `buildSchema`) with the 2-holding guardrail, GM management actions as `data-action`/`_onClickAction`, i18n in **all 8 locales**. | `sections/holdings/{page,holding-card}.hbs`, `contexts/PersonalHoldingsContext.kt`, `dialogs/GrantHolding.kt`, `sheet/KingdomSheet.kt`, `Main.kt` (partial registration), `lang/*.json` (all 8) |
| **4** | **Damage + repair offers + hooks** | `km-offer-holding-damage` / `km-waive-holding-damage` / `km-offer-holding-repair` handlers + templates; wire **all four** damage hooks — #1 (war-threat expiry), #2 (siege sack, which already ships), #3 (event resolution via `HOLDING_DAMAGING_EVENT_IDS`), #4 (hex unclaim, edge-detected via `lastKnownClaimed` in `performEndTurn`); sheet-side `data-action="holding-repair"` posts the repair card; repair flow; jsTest. | `ChatButtons.kt` (incl. the `"sack"` branch at `:319`), `ArmyWarPressure.kt`/`TurnWizardApplication.kt` (hook emission), `chatmessages/holding-damage-offer.hbs`, `chatmessages/holding-repair-offer.hbs` |
| **5** | **(Optional) Titles→petition bridge + deed grants** | Surface `title` to Petition Inbox templates *(blocked until the Inbox's jsMain/UI half exists — its commonMain core has landed, but there is no petition template or context builder to surface `title` to yet)*; optional milestone/deed-triggered grant offer; gazette lines. | petition templates, `TurnHistory.kt`, milestone hooks |

Phase 1 is standalone (pure + persistence). Phase 2 depends on 1. Phase 3 depends on 1. Phase 4
depends on 2+3 and on nothing external — siege sacking already ships (§6 hook #2). Phase 5 is
optional polish and is the only phase with an external dependency: the Petition Inbox's unbuilt
jsMain/UI half.

---

## 9. Open Questions for Gregory

1. **Income handoff:** default to chat-award only (recommended), or enable the opt-in
   `actor.asDynamic().inventory.addCoins(...)` write (the form at `sheet/KingdomSheet.kt:2711`)
   behind a setting from day one?
2. **Repair funding:** pay repair from the **kingdom treasury** (RP/commodities) or the **PC's own
   gold**? (Plan offers both on the card; which is the default?)
3. **Level basis:** scale income by the **owning PC's level** (recommended, personal) or the
   **kingdom level** (simpler, uniform)?
4. **Favors:** keep purely narrative, or later let favors feed Petition Inbox response options as a
   spendable social currency?
5. **Grant source:** GM-dialog only for MVP (recommended), or also auto-offer holdings on milestones
   (claim-a-region, found-a-settlement) in Phase 5?
6. **Visibility:** should other players see each other's holdings (transparency) or only their own +
   GM (privacy)? Plan defaults to owner + GM via `visibleToPlayers`.

---

**End of Plan.** Ready for review. On approval, implementation cards follow the Phase 1–5 table above.

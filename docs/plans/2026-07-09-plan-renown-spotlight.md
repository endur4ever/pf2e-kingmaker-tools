# Renown & Spotlight — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** New feature (PC Renown / Epithets + per-actor contribution tallies & Spotlight)
> **Depends on:** Faction & Diplomacy Relations Tracker (#1, landed), Player-facing collaborative
> kingdom view + roll-ownership locks (commit `d948f111`), Turn History / Gazette + last-turn recap
> (`postLastTurnRecap`, commit `b080cfe2`), Campaign Analytics dashboard (`TurnAnalytics`), Petition
> Inbox (`docs/plans/2026-07-09-plan-petition-inbox.md`)
> **Branch:** `kingmaker.5`

---

## Executive Summary

Kingdom rolls are already **ownership-locked to characters**: a check is performed *as a leadership
role* (`Leader.RULER … WARDEN`), each role maps to a PF2e actor UUID (`kingdom.leaders.<role>.uuid`),
and non-GM players can only roll the roles they own (`getOwnedLeaderRoles`, `Leaders.kt`). So a
kingdom check knows the *role* that made it — and, once two filters are applied (the role must be
selected, and it must be held by a PC rather than an NPC or nobody), *which PC did it*. The module
then throws that identity away the moment the degree of success is computed. Nothing accrues to the
character; nothing remembers that the Emissary has crit-succeeded eight diplomacy checks in a row.
§2.1 audits exactly which flows carry that identity — and which, like the re-roll path, lose it.

This plan adds **one credit-attribution substrate** feeding **two features**:

1. **PC Renown & Epithets** — a small, personal, *token-scale* reputation each PC earns with the
   **populace** (kingdom-wide) and **per-faction**. Crossing thresholds awards flavourful
   **epithets** ("The Bridge-Builder", "Restov's Hammer") and a **closed set of two cosmetic perks**
   (a settlement item-purchase *access tier*; a one-shot *invitation event*), each a GM-confirmed
   offer that never auto-applies.
2. **Per-actor contribution tallies & "Spotlight of the Turn"** — `RawTurnRecord` gains per-actor
   tallies (checks, crits, crit-fails, activities, events) that feed a single light-hearted
   **Spotlight** line in the existing last-turn recap beat (`postLastTurnRecap`, which fires at turn
   *open* — §3.3), in the turn gazette (§3.4), and an optional per-player series in the Analytics tab.

Both are driven by exactly one seam: `recordContribution(...)` (§3.2). The renown numbers are kept
deliberately **small** so they read as *personal colour*, never as a second faction-standing
economy — see §2.4 (double-count discipline).

---

## 1. Problem Statement + Player/GM Value

**Problem.** The kingdom subsystem is *kingdom-scoped*: RP, unrest, fame, and faction standing all
belong to the realm, not to any character. A player who repeatedly carries the kingdom on their
Emissary's back gets no personal recognition; a GM has no ledger of *who did what* to reward, taunt,
or foreshadow with. The acting-PC identity that roll-ownership already establishes at check time is
discarded immediately after `rollCheck` returns a `DegreeOfSuccess`.

**Value to the table:**

- **Players get personal stakes in kingdom turns.** "Your Emissary just earned *The Silver Tongue*"
  turns a spreadsheet action into a character beat. Renown is *theirs*, unlike shared RP.
- **GM gets a recognition & foreshadowing tool.** Epithet offers and the Spotlight line are prompts:
  reward the star, needle the blunderer, seed an *invitation* quest from a faction that now admires a
  specific PC.
- **Zero new bookkeeping for players.** Renown accrues automatically from checks they already make;
  the Spotlight line is generated, not authored.
- **Advisory, never coercive.** Every mechanical benefit (purchase-access tier, invitation) is a
  GM-confirmed offer. The Spotlight line and epithet flavour are pure colour with no rules weight.

---

## 2. Data Model

### 2.1 The attribution audit — who knows the acting PC *today*

`recordContribution(...)` can only be called where the acting PC is actually in scope. Auditing the
codebase, the flows do **not** split as cleanly as "the check pipeline knows the PC" suggests — two of
the rows below are the reason §3.2 needs a record-or-skip rule and a deed-replacement rule at all:

| Flow | Acting-PC known today? | How | Scoped in this plan |
|------|------------------------|-----|---------------------|
| **Kingdom skill check** (`kingdomCheckDialog` → `rollCheck`, `KingdomRoll.kt`) | **YES, with two filters** | `data.leader: String` is in scope at the `rollCheck` call site (`KingdomCheckDialog.kt:481`) — `CheckData.leader` is declared `var leader: String` at `:203`. Convert with `Leader.fromString(data.leader)` (as the file already does at `:562`), or use the dialog's typed `selectedLeader: Leader?` (`:311`), then resolve via `kingdom.parseLeaderActors().resolve(leader)` (`KingdomData.kt:659`; `LeaderActors.resolve`, `LeaderActors.kt:25-35`). The two filters: the resolved `LeaderActor.type` may be `REGULAR_NPC`/`HIGHLY_MOTIVATED_NPC`/`NON_PATHFINDER_NPC` (`LeaderType.kt:8-12`) and the role may be vacant/unassigned (`RawLeaderValues.uuid: String?`, `RawLeaders.kt:6-11`); and `selectedLeader` is nullable, defaulting to the Ruler at `:330`. §3.2 states the record-or-skip rule. `rollCheck` returns the `DegreeOfSuccess`, so crit vs. success vs. crit-fail is known there. | **Phase 2 — primary seam** |
| **Activity performed** (`CheckType.PerformActivity`) | **YES** | Same pipeline — an activity is a check with a `RawActivity`; same `leader` in scope. | **Phase 2** |
| **Kingdom event resolved** (`CheckType.HandleEvent`) | **YES** | Same pipeline; the resolving check carries its `leader`. | **Phase 2** |
| **Petition answered** (`RawPetition.targetRole: String`, Petition Inbox) | **YES** | A petition is addressed to a role persisted as `Leader.value` — `var targetRole: String` (`docs/plans/2026-07-09-plan-petition-inbox.md:28`), a shape that plan defends at `:45-50` ("persisted types here are `@JsPlainObject external interface`s of primitives"). Resolve with `kingdom.parseLeaderActors().resolve(Leader.fromString(petition.targetRole) ?: return)?.uuid`. **Not** `kingdom.leaders[targetRole]`: `RawLeaders` (`RawLeaders.kt:13-23`) is a fixed-property `@JsPlainObject` with eight named `val`s and is not indexable. | **Phase 2** |
| **Re-roll of any of the above** (`ReRolls.kt:184`, inside `suspend fun reRoll(chatMessage, mode)` at `:170`) | **NO — identity is lost in the chat message** | `reRoll` re-enters the *same* `rollCheck` from a `RollMetaContext` (`ReRolls.kt:33-56`) rebuilt out of the `.km-roll-meta` dataset. That context carries `label/dc/skill/activityId/eventId/degree/rollMode/modifier/pills/…` and **no leader**: its `actorUuid` is dereferenced as `fromUuidTypeSafe<KingdomActor>(meta.actorUuid)` (`:173`), i.e. the KINGDOM actor, not a PC. Every `ReRollMode` (`ROLL_TWICE_KEEP_HIGHEST`/`LOWEST`, `DEFAULT`, `FAME_OR_INFAMY`, `FREE_AND_FAIR`, `CREATIVE_SOLUTION`) goes through it. | **Phase 2 — requires widening the `.km-roll-meta` dataset** (`generateRollMeta`, `ReRolls.kt:95`, rendering `chatmessages/roll-flavor.hbs`) with the acting PC, the faction, and a deed id; see §3.2 |
| **Battle command** (`ResolveBattle.kt`) | **NO** (not in scope) | `ResolveBattle` resolves army rounds with **no** `leader`/`game.user`/actor UUID in scope (verified: zero such tokens in the file). Armies are conceptually the **General's**, but the General's actor UUID is *not threaded into battle resolution today*. | **OUT OF SCOPE** (see §6.1); optional later seam = resolve `leaders.general.uuid` at battle end |
| **Expedition launch** (`ExpeditionLaunch.kt`) | **NO clean PC** | Expeditions are **companion-scoped** (`companion.actorUuid`) and GM-initiated from the sheet; there is no single "launching PC". Companions already keep their own `careerExpeditions`/`careerTriumphs` on `RawCharacter`. | **OUT OF SCOPE** (companions are not populace-renown PCs; see §6.1) |

**Conclusion that scopes the whole feature:** the only action-time flow that knows an acting PC today
is the **kingdom check pipeline** (covering checks, crits, activities, and events) plus **petition
answers**. Renown therefore accrues from those four sources. Two qualifications the table above makes
explicit, because both are load-bearing and neither is free:

- **The check pipeline knows a *role*, not necessarily a *PC*.** A role can be vacant, unassigned, or
  filled by an NPC, and `selectedLeader` is nullable. §3.2 fixes a record-or-skip rule rather than
  crediting whoever happens to sit in the Ruler slot.
- **The re-roll path re-enters `rollCheck` with the PC identity stripped.** It is the *same* seam, so
  it cannot be ignored: left alone it either records nothing (the original, usually failed, degree
  stays credited and the improved result never lands) or double-credits the same deed. §3.2 widens
  the roll-meta dataset and defines a deed-replacement rule.

Battle and expedition attribution are explicitly deferred because the acting-PC identity does not
exist at those call sites at all — instrumenting them anyway would fabricate attribution and is
called out as out-of-scope rather than hand-waved.

### 2.2 Contribution kinds (pure enum, commonMain)

```kotlin
// src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/RenownEngine.kt
enum class ContributionKind {
    CHECK_SUCCESS,     // any successful kingdom check
    CHECK_CRIT,        // critical success
    CHECK_FAILURE,     // plain failure (no renown, but tallied for the Spotlight)
    CHECK_CRIT_FAIL,   // critical failure — small negative populace renown ("everyone saw that")
    ACTIVITY,          // a RawActivity was performed (independent of degree)
    EVENT_RESOLVED,    // a kingdom event was handled
    PETITION_ANSWERED, // a petition addressed to this PC's role was answered
}
```

### 2.3 New carriers + nullable additions to existing interfaces

*Every field added to an **existing** interface is nullable for migration safety* — that is where
migration safety actually bites, and it matches the house comment style already on `RawTurnRecord`
(`RawTurnRecord.kt:25-27`: "Nullable for migration: legacy records have none"). The **new** carriers'
identity and count fields are non-nullable because their rows are only ever created by
`recordContribution` (§3.2), never backfilled by a migration reading legacy data.

#### `RawTurnRecord.kt` — ADDITION only (jsMain: `kingdom/data/RawTurnRecord.kt`)

```kotlin
// existing fields unchanged …
/** Per-actor contribution tallies accrued during this turn. Nullable: legacy records have none,
 *  so the Spotlight simply shows nothing for old turns. */
var contributions: Array<RawTurnContribution>?
```

```kotlin
// src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/data/RawTurnContribution.kt
/** ONE row per PC per turn — the Spotlight's unit of comparison. Deliberately NOT keyed by role or
 *  skill: role- and category-scoped counts are lifetime counters on RawPcRenown (below), because
 *  keying this row by (actor x role x category) would explode the per-turn record for no gain. */
@JsPlainObject
external interface RawTurnContribution {
    var actorUuid: String
    var actorName: String?   // denormalised for display without a UUID lookup
    var checks: Int          // successful checks this turn
    var crits: Int           // critical successes this turn
    var critFails: Int?      // critical failures (nullable: back-compat)
    var activities: Int      // activities performed this turn
    var events: Int?         // events resolved this turn (nullable: back-compat)
}
```

#### New renown carriers (jsMain: `kingdom/data/RawRenown.kt`)

```kotlin
@JsPlainObject
external interface RawPcRenown {
    var actorUuid: String
    var actorName: String?
    var populace: Int?                            // 0..100, null => 0 (unknown)
    var factionRenown: Array<RawPcFactionRenown>? // personal per-faction renown (see §2.4)
    var epithets: Array<String>?                  // earned epithet ids (see §5 catalog)
    var purchaseAccessTier: Int?                  // granted perk: 0/1/2 (see §5 perk set)
    var lastOfferedTurn: Int?                     // last turn an epithet/perk offer was emitted (dedup)

    // Lifetime counters. These exist because the epithet catalog (Appendix) asks role- and
    // category-scoped LIFETIME questions ("15 Treasurer-role successes", "8 Emissary crit-successes",
    // "5 diplomatic activities") that no aggregate of RawTurnContribution can answer: those rows are
    // keyed by actor alone, and turnHistory is capped at TURN_HISTORY_CAP = 100 (TurnHistory.kt:13,
    // :24-32). recordContribution maintains all of these in O(1) — it already has the Leader and the
    // rolled KingdomSkill in scope at the seam.
    var lifetimeCrits: Int?
    var lifetimeCritFails: Int?
    var lifetimeActivities: Int?
    var lifetimeEvents: Int?
    var roleTallies: Array<RawRoleTally>?         // per leadership role
    var categoryTallies: Array<RawCategoryTally>? // per deed category (see deedCategoryFor, §3.1)
}

/** Per-role lifetime tally. `role` is `Leader.value` (ruler … warden), parsed with Leader.fromString. */
@JsPlainObject
external interface RawRoleTally {
    var role: String
    var successes: Int   // successful checks rolled in this role (critical successes included)
    var crits: Int       // critical successes rolled in this role
}

/** Per-category lifetime tally. `category` is `DeedCategory.value` (diplomatic | martial | other). */
@JsPlainObject
external interface RawCategoryTally {
    var category: String
    var activities: Int
    var crits: Int
}

/** One recorded deed for the OPEN turn, kept so a re-roll can REPLACE its own earlier result rather
 *  than double-counting it (§3.2). `populaceApplied`/`factionApplied` are the deltas that actually
 *  landed AFTER clamping, so reversal is exact even at the 0 / 100 / ±40 boundaries. Cleared at End
 *  Turn together with `currentTurnContributions`. */
@JsPlainObject
external interface RawRenownDeed {
    var deedId: String         // minted in rollCheck, carried through re-rolls via .km-roll-meta
    var actorUuid: String
    var kind: String           // ContributionKind.value
    var role: String           // Leader.value
    var category: String       // DeedCategory.value
    var factionName: String?
    var populaceApplied: Int
    var factionApplied: Int
}

@JsPlainObject
external interface RawPcFactionRenown {
    var factionName: String   // matches RawGroup.name
    var renown: Int           // personal standing with that faction, token-scale (see §2.4)
}
```

#### `KingdomData.kt` — ADDITIONS only (both nullable, top-level, mirroring `turnHistory`/`groups`)

```kotlin
var renown: Array<RawPcRenown>?                           // cumulative per-PC renown ledger
var currentTurnContributions: Array<RawTurnContribution>? // in-progress tallies for the OPEN turn
var currentTurnDeeds: Array<RawRenownDeed>?               // per-deed log for the OPEN turn (re-roll replacement)
```

`currentTurnContributions` is the accumulator for the turn currently in progress; it is flushed into
the new `RawTurnRecord.contributions` at End Turn and then reset (see §3.3). `currentTurnDeeds` is
the re-roll ledger and is reset in the same step — it is **never** written into the turn record, so
it stays bounded by one turn's worth of rolls. `renown` is the durable cumulative ledger.

### 2.4 Double-count discipline (the load-bearing design rule)

There are now **two** things that look like "standing with Pitax", and they must never be confused:

| | `RawGroup.standing` (existing) | `RawPcFactionRenown.renown` (new) |
|---|---|---|
| **Owner** | the **KINGDOM** | an individual **PC** |
| **Scale** | ±100, moves in **±5…±10** steps | token, moves in **+1…+3** steps, soft-capped ~±40 |
| **Drives** | war-threat / diplomacy-quest **offers**, caravan raid DC, negotiation | **epithets only** (cosmetic + closed 2-perk set) |
| **Mechanical feedback into kingdom checks?** | yes (existing) | **never** |

A PC's crit-success on a *Send Diplomatic Envoy* toward Pitax raises that PC's personal
`factionRenown["Pitax"]` by +2. It does **not** move `RawGroup.standing`, and neither does any other
kingdom check: the complete set of `applyStandingDelta(` call sites in `src/jsMain` is
`WarStanding.kt:22`, `TurnTickingEngine.kt:432` (passive drift), `ExpeditionResolution.kt:304` and
`:623`, and `KingdomSheet.kt:1558` (the GM's `"adjust-standing"` action, `:1544`) — plus, which only copies the previous value forward across a form submit. Kingdom
standing is moved by the GM's *Adjust Standing* control, expedition resolution, war, and passive
drift; **the check itself writes only the personal number.**

So there is no double-count to reconcile in the first place — and the earlier draft's claim that a
diplomacy crit "raises both … (existing behaviour, unchanged)" described behaviour that does not
exist. What the rule above still buys is the guarantee that renown never *becomes* a second standing
economy: the personal number has **zero mechanical consequence** beyond epithets and the closed perk
set, and no code path reads it into a DC, roll, or resource. Asserted by tests
(§7.1: `personalRenownNeverFeedsKingdomStanding`).

**Practical consequence for QA:** after a diplomacy crit, Pitax's *kingdom* standing is expected to
be **unchanged** unless a GM moved it — see §7.3 step 3.

### 2.5 Persistence — where each thing lives, and why

- **Renown + in-progress tallies → kingdom actor flag (`KingdomData`).** Renown is kingdom-scoped PC
  colour (your reputation *in this realm*); it belongs with the kingdom the PC leads, not the world
  setting (a PC could lead two kingdoms with different renown) and not the camping flag (nothing to
  do with rests).
- **Per-turn tallies → the turn stream (`RawTurnRecord.contributions`).** They are a historical
  snapshot, exactly like every other field on `RawTurnRecord`, and feed Analytics' time series.
- **No world setting, no camping flag.**

### 2.6 Migration

**`Migration<next>`** *(placeholder — the number is re-derived at implementation; see the caveat
below)*. Contiguity is guarded by `MigrationChainTest.registeredVersionsAreContiguous17To61`
(`MigrationChainTest.kt:23`), which today asserts `(17..65)` — note the test's **name and its range
have already drifted apart**, so registering the new class in `Migrations.kt` means bumping **both**
the assertion range *and* the test's own name. *Gregory sequences the real number at implementation
time if other migrations land first.*

> ⚠️ **The number in this section is a placeholder and must be re-derived at implementation.**
> The chain now ends at **`Migration65`**. Since these plans were written, four of the reserved
> numbers have LANDED: 62 = downtime-projects, 63 = scheduled-pressure-engine,
> 64 = map-dynamism, 65 = loot-manifests. `Migration49` was never free (it sits inside the
> long-registered 17..61 range) and several unimplemented plans still name it. The next free
> number is **66**. Take the next contiguous number when this actually lands, and extend
> `MigrationChainTest`'s hardcoded range.


```kotlin
// migrations/migrations/Migration<next>.kt   — <next> is re-derived at implementation (see the caveat)
class Migration<next> : Migration(<next>) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.renown == null) kingdom.renown = arrayOf<dynamic>()
        if (kingdom.currentTurnContributions == null) kingdom.currentTurnContributions = arrayOf<dynamic>()
        if (kingdom.currentTurnDeeds == null) kingdom.currentTurnDeeds = arrayOf<dynamic>()
        // RawTurnRecord.contributions is left null on legacy records (Spotlight shows nothing for old turns).
    }
}
```

Non-breaking: every new field is nullable; legacy saves load unchanged and simply show no renown and
no Spotlight until the first instrumented check runs.

---

## 3. Engine Design

### 3.1 Pure core (commonMain, UI-free — mirrors `FactionRelations.kt`)

File: `src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/RenownEngine.kt`, package
`at.posselt.pfrpg2e.data.kingdom`. It operates on **plain value types** (not the JS `Raw*` carriers),
exactly as `FactionRelations` operates on `Int` rather than `RawGroup`. A thin jsMain adapter
(`RenownAdapter.kt`) converts `RawPcRenown ⇆ PcRenown`.

```kotlin
const val MAX_POPULACE_RENOWN = 100
const val MIN_POPULACE_RENOWN = 0
const val FACTION_RENOWN_SOFT_CAP = 40   // personal per-faction renown clamps to ±this

/** Deed categories, derived from the KingdomSkill the check was rolled with — which IS in scope at
 *  the seam (`rollCheck(skill: KingdomSkill, …)`, KingdomRoll.kt:99). There is no `DIPLOMACY`
 *  kingdom skill, so the diplomatic bucket is the statecraft/politics/trade/intrigue group; martial
 *  is warfare/defense. Deliberately coarse — it only has to feed epithet conditions. */
enum class DeedCategory { DIPLOMATIC, MARTIAL, OTHER }

fun deedCategoryFor(skill: KingdomSkill): DeedCategory = when (skill) {
    KingdomSkill.STATECRAFT, KingdomSkill.POLITICS, KingdomSkill.TRADE, KingdomSkill.INTRIGUE -> DeedCategory.DIPLOMATIC
    KingdomSkill.WARFARE, KingdomSkill.DEFENSE -> DeedCategory.MARTIAL
    else -> DeedCategory.OTHER
}

data class PcRenown(
    val actorUuid: String,
    val populace: Int = 0,
    val factionRenown: Map<String, Int> = emptyMap(),
    val epithets: Set<String> = emptySet(),
    val purchaseAccessTier: Int = 0,
    // Lifetime counters (§2.3). These are what makes the Appendix catalog evaluable; without them
    // `roleSuccesses` has no source, because RawTurnContribution is keyed by actor alone and
    // turnHistory is capped at 100 records.
    val lifetimeCrits: Int = 0,
    val lifetimeCritFails: Int = 0,
    val lifetimeActivities: Int = 0,
    val lifetimeEvents: Int = 0,
    val roleSuccesses: Map<Leader, Int> = emptyMap(),
    val roleCrits: Map<Leader, Int> = emptyMap(),
    val categoryActivities: Map<DeedCategory, Int> = emptyMap(),
    val categoryCrits: Map<DeedCategory, Int> = emptyMap(),
)

/** One credited deed, as the engine sees it. Bundled rather than passed as five parameters because
 *  the same value has to be reversible (§3.2, re-rolls). */
data class Contribution(
    val kind: ContributionKind,
    val leader: Leader,
    val category: DeedCategory = DeedCategory.OTHER,
    val factionName: String? = null,
)

data class TurnTally(
    val actorUuid: String,
    val actorName: String? = null,
    val checks: Int = 0,
    val crits: Int = 0,
    val critFails: Int = 0,
    val activities: Int = 0,
    val events: Int = 0,
)
```

#### Source table with AMOUNTS (the single source of truth for accrual)

```kotlin
/** Renown deltas per contribution. Kept small on purpose (see §2.4). `factionName` non-null routes
 *  the faction portion to per-faction renown; the populace portion always applies. */
fun renownDeltaFor(kind: ContributionKind): RenownDelta = when (kind) {
    ContributionKind.CHECK_CRIT       -> RenownDelta(populace = +3, faction = +2)
    ContributionKind.CHECK_SUCCESS    -> RenownDelta(populace = +1, faction = +1)
    ContributionKind.CHECK_FAILURE    -> RenownDelta(populace =  0, faction =  0) // tally only
    ContributionKind.CHECK_CRIT_FAIL  -> RenownDelta(populace = -2, faction = -1)
    ContributionKind.ACTIVITY         -> RenownDelta(populace = +1, faction =  0)
    ContributionKind.EVENT_RESOLVED   -> RenownDelta(populace = +2, faction =  0)
    ContributionKind.PETITION_ANSWERED-> RenownDelta(populace = +2, faction =  0)
}
data class RenownDelta(val populace: Int, val faction: Int)
```

Amounts chosen so a *very* active PC gains ~15–25 populace renown per busy turn's worth of checks —
the populace thresholds in the Appendix catalog sit at 30/40/50/75, i.e. several turns of sustained
contribution, not one.

#### Pure signatures

```kotlin
/** Apply one deed to a PC's renown, clamped, updating every lifetime counter it touches. Returns a
 *  NEW PcRenown (immutable) plus the deltas that ACTUALLY landed after clamping — the adapter
 *  persists those on the deed row so a re-roll can reverse them exactly (§3.2). */
fun accrueRenown(current: PcRenown, deed: Contribution): AccrualResult

data class AccrualResult(val renown: PcRenown, val populaceApplied: Int, val factionApplied: Int)

/** Exact inverse of [accrueRenown] for one already-recorded deed: subtracts the applied deltas and
 *  decrements the same counters [accrueRenown] incremented. Epithets already granted are NEVER
 *  revoked here — an epithet is a GM-confirmed honour, not a derived value (§5). */
fun revertRenown(current: PcRenown, deed: Contribution, populaceApplied: Int, factionApplied: Int): PcRenown

/** Which epithets does this PC now qualify for that they do not already hold? Pure over the
 *  cumulative renown plus the role/category context folded out of it by [epithetContextFor]. */
fun evaluateEpithets(renown: PcRenown, ctx: EpithetContext): List<EpithetAward>

/** Builds the context from the renown record itself — every count it needs is a lifetime counter on
 *  PcRenown, so this needs no turn-history scan and no 100-turn window caveat. `holdsRulerRole` is
 *  the one thing renown cannot know: the caller passes
 *  `kingdom.parseLeaderActors().resolve(Leader.RULER)?.uuid == renown.actorUuid`. */
fun epithetContextFor(renown: PcRenown, holdsRulerRole: Boolean): EpithetContext

/** Perk (if any) that a newly-earned epithet grants, as a CLOSED set. */
fun perkForEpithet(epithetId: String): RenownPerk?   // PurchaseAccess(n) | Invitation(factionName?) | null

/** Pick the single Spotlight subject for a turn from the turn's tallies (top contributor by a
 *  weighted score; deterministic tie-break by actorUuid). Returns null when nobody contributed. */
fun spotlightOfTheTurn(tallies: List<TurnTally>): SpotlightPick?

data class SpotlightPick(val actorUuid: String, val actorName: String?, val kind: SpotlightKind, val count: Int)
enum class SpotlightKind { BUSIEST, CRIT_STAR, BLUNDERER, DIPLOMAT, EVENT_HERO }  // selects the i18n flavour line

sealed interface RenownPerk {
    /** Raises the item PURCHASE LEVEL a settlement can offer, by `levels`. Not a price discount:
     *  see §5/§6 — nothing in InspectSettlement renders a price. Tier 1 => +1, tier 2 => +2. */
    data class PurchaseAccess(val levels: Int) : RenownPerk
    data class Invitation(val factionName: String?) : RenownPerk // spawns an invitation quest offer
}

fun purchaseAccessLevelsForTier(tier: Int): Int = tier.coerceIn(0, 2)

data class EpithetAward(val epithetId: String, val perk: RenownPerk?)

/** Everything the Appendix catalog's conditions read. Every field is a fold of PcRenown's own
 *  lifetime counters — nothing here requires scanning `turnHistory`. */
data class EpithetContext(
    val roleSuccesses: Map<Leader, Int> = emptyMap(),        // Leader.TREASURER -> 15
    val roleCrits: Map<Leader, Int> = emptyMap(),            // Leader.EMISSARY  -> 8
    val categoryActivities: Map<DeedCategory, Int> = emptyMap(),
    val critSuccesses: Int = 0,      // renown.lifetimeCrits
    val critFails: Int = 0,          // renown.lifetimeCritFails
    val activities: Int = 0,         // renown.lifetimeActivities
    val eventsResolved: Int = 0,     // renown.lifetimeEvents
    val holdsRulerRole: Boolean = false,
    /** Kingdom-wide populace standing at evaluation time, read by most Appendix conditions.
     *  Sourced from the kingdom's own unrest/fame state by the jsMain adapter, NOT stored per-PC:
     *  it is a property of the realm, and duplicating it onto every PC would let two PCs disagree
     *  about the same kingdom. */
    val populace: Int = 0,
    /** Per-faction renown for the conditions that name a group, keyed by RawGroup.name.
     *  Empty map = no faction standing known, which every such condition treats as "not met". */
    val factionRenown: Map<String, Int> = emptyMap(),
)
```

**Faction-named epithets** (#2 `restovsHammer`, #6 `feyFriend`, #12 `scourgeOfPitax`) match
`RawGroup.name` case-insensitively against a name list carried in the epithet table itself, because a
faction has no id — `RawGroup` (`RawGroup.kt:5-23`) is keyed by free-text `name`. A table that
renamed Pitax simply never earns #12; that is a documented dead entry, not a silent failure.

`spotlightOfTheTurn`'s weighting: `crits*3 + events*2 + activities*1 + checks*1`, with a special-case
`BLUNDERER` pick — the light-hearted "worst of the turn" line — taken **only** when a PC's
`critFails >= 2` **and** that same PC is not the turn's top scorer by the weighting above. Nobody gets
called a fool for the turn they carried; the rule lives in the pure function so the §4.3 tone rules
are enforced by code, not by whoever writes the string. Being pure, it is fully unit-tested with
fixed inputs (no RNG, no clock).

### 3.2 Impure adapter + the seam (jsMain)

```kotlin
// src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/RecordContribution.kt
/** THE seam. Mutates the kingdom's in-progress tally + cumulative renown for one deed.
 *  Idempotent PER DEED, not per call: a second call carrying a `deedId` already in
 *  `kingdom.currentTurnDeeds` REPLACES that deed rather than adding a second one (that is the
 *  re-roll path, §2.1). Never emits offers itself — epithet/perk offers are batched at End Turn
 *  (§3.3) to avoid mid-turn chat spam. */
fun recordContribution(
    kingdom: KingdomData,
    deedId: String,
    actorUuid: String,
    actorName: String?,
    kind: ContributionKind,
    leader: Leader,
    category: DeedCategory,
    factionName: String? = null,
) {
    // 0) REPLACEMENT: if currentTurnDeeds already holds deedId, first undo it —
    //    revertRenown(prior.populaceApplied, prior.factionApplied) and decrement the same
    //    currentTurnContributions counter the prior kind incremented — then drop the row.
    // 1) bump currentTurnContributions[actorUuid].<field> (create the row if absent)
    // 2) accrueRenown(toPcRenown(row), Contribution(kind, leader, category, factionName))
    //    -> fromPcRenown, and append the AccrualResult's applied deltas as a RawRenownDeed.
    // Caller is responsible for kingdomActor.setKingdom(kingdom) (batched with its own write).
}
```

#### Which leader counts (the record-or-skip rule)

`rollCheck` is reached with a *role*, and a role is not always a PC. The rule, stated once so no call
site has to re-decide it:

```kotlin
val leader = Leader.fromString(data.leader)                       // CheckData.leader is a String (:203)
val leaderActor = kingdom.parseLeaderActors().resolve(leader)     // KingdomData.kt:659
val creditUuid = leaderActor?.takeIf { it.type == LeaderType.PC }?.uuid
```

- **`selectedLeader == null` ⇒ record NOTHING.** `KingdomCheckDialog` falls back to
  `Leader.RULER.value` at `:330` when no leader is selected, and the callers pass
  `selectedLeader = game.getActiveLeader()` (`KingdomSheet.kt:2084`, `:2403`, `:2824`,
  `StructureBrowser.kt:393`, `ArmyBrowser.kt:170`, `ArmyTacticsBrowser.kt:129`,
  `CleanseItemRoll.kt:34`), where `fun Game.getActiveLeader(): Leader?` (`Leaders.kt:10-13`) reads a
  world setting and returns `null` when it is unset. Silently crediting the Ruler for every
  unselected check would poison the whole ledger, so an unselected check earns nobody anything.
- **Non-PC leaders ⇒ record NOTHING.** `LeaderType` (`LeaderType.kt:8-12`) is
  `PC | REGULAR_NPC | HIGHLY_MOTIVATED_NPC | NON_PATHFINDER_NPC`, and `RawLeaderValues.uuid` is
  `String?` (`RawLeaders.kt:6-11`), so a role can be vacant or held by a PF2ENpc. Renown is *player*
  colour; NPC officials do not accrue it.

This is a filter, not new plumbing: `parseLeaderActors()` already returns a `LeaderActor` carrying a
typed `type: LeaderType` (`LeaderActors.kt:5-13`).

#### Instrumented call sites

- **`KingdomRoll.rollCheck(...)`** (`KingdomRoll.kt:96`) — gains three nullable params:
  `leaderActorUuid: String?`, `factionName: String?`, and `deedId: String?` (minted here when null).
  After the degree is computed, map degree → `ContributionKind` and call `recordContribution`. One
  call handles checks/crits/crit-fails; a second `ACTIVITY`/`EVENT_RESOLVED` call fires when
  `activity`/`event` is non-null and reuses the same `deedId` with a `:activity` suffix so both
  halves of one roll replace cleanly. `category = deedCategoryFor(skill)` — `skill: KingdomSkill` is
  already a `rollCheck` parameter.
- **`KingdomCheckDialog`** (`:481`) — passes `leaderActorUuid` per the record-or-skip rule above,
  `factionName = params.factionName` (below), and `deedId = null` (a first roll always mints one).
- **Petition answer handler** (Petition Inbox `km-offer-*` / option button) — resolves
  `kingdom.parseLeaderActors().resolve(Leader.fromString(petition.targetRole) ?: return)?.uuid`,
  applies the same `LeaderType.PC` filter, and calls
  `recordContribution(..., kind = PETITION_ANSWERED, deedId = "petition:${petition.id}")` — which
  also makes answering the same petition twice a no-op by construction.

#### Where `factionName` comes from (it does not exist yet)

The plan's earlier draft asserted that `factionName` "is populated when the activity/event targets a
`RawGroup`". Nothing supplies it today. The group picked for a negotiation is a **local** in the
params builder: `val group = pickGroup(groups, required = false)` (`KingdomCheckDialog.kt:878-882`),
reached only when the activity's DC is `ActivityDcType.NEGOTIATION` or `NEGOTIATION_OR_CONTROL`, and
its only two uses are `groupDc = group?.negotiationDC` (`:895`) and
`rollOptions = if (group?.atWar == true) …` (`:925`). It never reaches `CheckDialogParams`
(`:277-290`, which has no group field) and never reaches the dialog constructor (`:300-311`, which
takes `selectedLeader` only); `RawActivity` (`RawActivity.kt:41-64`) has no faction field either.

The plumbing, therefore:

1. Add `val factionName: String? = null` to `CheckDialogParams` (`:277-290`) and set it to
   `group?.name` in the `CheckType.PerformActivity` branch that already has `group` in scope
   (`:918-928`). The **name**, not the `RawGroup` — the name is all that is consumed, it is what
   `RawPcFactionRenown.factionName` stores, and it keeps a jsMain data carrier out of the dialog's
   parameter list.
2. `KingdomCheckDialog` reads `params.factionName` and passes it to `rollCheck` at `:481`.
3. `rollCheck` writes it into the roll-meta dataset (below) so the re-roll path can restore it.

`factionName` is non-null **only** for `NEGOTIATION` / `NEGOTIATION_OR_CONTROL` activities where
`pickGroup` actually returned a group. Every other check credits populace renown only.

#### Re-rolls: one deed, credited once, at its final degree

`ReRolls.reRoll` (`ReRolls.kt:170`) re-enters `rollCheck` at `:184` from a `RollMetaContext`
(`:33-56`) rebuilt out of the chat card's `.km-roll-meta` element — which carries no leader and no
faction (§2.1). Three fields are therefore added to the dataset and threaded end to end:

| Field | Written by | Read back by |
|-------|-----------|--------------|
| `data-deed-id` | `generateRollMeta` (`ReRolls.kt:95`) → `chatmessages/roll-flavor.hbs` (the `.km-roll-meta` div, `:2-20`) | `parseRollMeta` (`ReRolls.kt:58`) → `reRoll` → `rollCheck` |
| `data-leader-actor-uuid` | same | same |
| `data-faction-name` | same | same |

`RollMetaContext` gains the three matching `val`s. With that in place the rule is mechanical:

> **A re-roll REPLACES the original deed.** `recordContribution` finds the `deedId` in
> `kingdom.currentTurnDeeds`, reverses the exact deltas stored on that row (`populaceApplied` /
> `factionApplied` — stored post-clamp precisely so the reversal is exact at the 0 / 100 / ±40
> boundaries), decrements the counter the original kind incremented, and then records the new degree.
> The ledger always reflects the **final** degree of the check, exactly once.

Two accepted edge cases, stated rather than discovered later:

- A re-roll of a check rolled in a **previous** turn finds no matching deed (the log is cleared at
  End Turn, §3.3) and is credited as a fresh deed. The turn boundary is the accounting boundary; a
  cross-turn correction would have to reopen a closed `RawTurnRecord`.
- An **upgrade/downgrade** of a degree via the chat context menu (`ContextMenus.kt` →
  `suspend fun changeDegree(rollMeta, mode)`, `ChangeDegree.kt:52`) does not re-enter `rollCheck`
  and so does not re-credit. It is a GM correction to a posted result, not a
  new deed. Out of scope, and noted in §6.1.

### 3.3 Tick surface — TWO seams, not one (End Turn, then Turn Open)

Respecting the tick split (no third tick): renown accrues *immediately* on each deed (personal colour
can move mid-turn), but everything that **composes** happens on the monthly turn surface. That
surface is **two distinct beats**, and an earlier draft collapsed them:

| Beat | Function | Fired by |
|------|----------|----------|
| **End Turn** | `performEndTurn` (`TurnWizardApplication.kt:250-797`), which calls `appendTurnRecord(` at `:625` wrapping `buildTurnRecord(` at `:627` | the sheet's `"end-turn"` action (`KingdomSheet.kt:2332-2344`), which calls **only** `performEndTurn` |
| **Turn OPEN** | `suspend fun postLastTurnRecap` (`TurnWizardApplication.kt:849`), reading `computeLastTurnRecap` (`TurnHistory.kt:259`) | the separate `"open-turn-wizard"` action (`KingdomSheet.kt:2346-2352`) — "GM-only, GM-whispered recap of the previous turn; idempotent per turn". `postPlayerPings` documents the same seam (`PlayerPings.kt:157`: "Fires at TURN OPEN, the same seam as postLastTurnRecap") |

`postLastTurnRecap` has **no caller inside `performEndTurn`**. So the Spotlight line is composed at
End Turn but only *surfaces* when the GM next opens the Turn Wizard. Daily `DailyTickHooks` is **not**
touched.

**At End Turn (inside `performEndTurn`), in order:**

1. **Flush tallies:** copy `kingdom.currentTurnContributions` into the new
   `RawTurnRecord.contributions` passed to `buildTurnRecord` (`:627`), then reset both
   `kingdom.currentTurnContributions = emptyArray()` and `kingdom.currentTurnDeeds = emptyArray()`.
2. **Evaluate epithets:** for each PC in `kingdom.renown`,
   `evaluateEpithets(renown, epithetContextFor(renown, holdsRulerRole))`; for each newly-earned
   epithet emit **one** GM-confirmed offer card (§5), guarded by `lastOfferedTurn` + "epithet not
   already held" so it fires once. These whispers appear **immediately** on clicking End Turn.

**At Turn Open (inside `postLastTurnRecap`, `:849`):**

3. **Compose Spotlight:** `computeLastTurnRecap` gains a nullable `spotlight: String?` derived from
   the latest record's `contributions` via `spotlightOfTheTurn`, and `postLastTurnRecap` adds it to
   the `chatmessages/last-turn-recap.hbs` context alongside `notesList`. Being derived inside the
   pure `computeLastTurnRecap` (`TurnHistory.kt:259-277`), it stays unit-testable. The Spotlight line
   is **pure flavour, not an offer** (no mechanical benefit → no confirmation).

### 3.4 Gazette composition — one line, in both notes fields

`performEndTurn` writes **two** gazette strings from `formatTurnGazette` (`TurnHistory.kt:80`): the
GM string at `:594` → `RawTurnRecord.notes` (`:644`) and the player-safe string at `:609` →
`RawTurnRecord.playerNotes` (`:645`, identical except the secret campaign-clock progress is dropped).
Those two fields are what the Session Prep "Recent Turns" view and the exported recap journal render,
so a Spotlight that only reaches the GM-whispered chat card vanishes from the campaign's written
record.

**Decision: the Spotlight line goes into BOTH.** It is player-safe by construction — it names a PC
and counts their own public deeds, and references no GM-only state (that is the first tone rule
below). So `formatTurnGazette` gains a `spotlight: String? = null` parameter appended as its own
gazette event, and `performEndTurn` passes the same string to both calls (`:594` and `:609`).

⚠️ **Guard interaction:** `formatTurnGazette` carries a hand-maintained `defaultLocalize` fallback,
and `scripts/check_i18n_keys.py::check_gazette_resolver` (check 6, `:308-350`) asserts every
`kingdom.turnGazette.*` string in it matches `lang/en.json` **verbatim** (`${dyn.foo}` normalised to
`{foo}`). A new `kingdom.turnGazette.spotlight` key must be added to *both* copies with identical
text, or CI fails. This is the same guard that exists because the gazette tests assert against
`defaultLocalize` and would otherwise stay green while production rendered different text.

---

## 4. UI Design

### 4.1 Renown card — on the Party tab (NOT the Roster)

**PCs do not live on the Roster.** `rosterContext` is built purely from `kingdom.companions`
(`KingdomSheet.kt:3662`); `sections/roster/page.hbs` renders companion/NPC role badges, influence
/12, XP /1000, camp availability, personal-quest, expedition and injury chips, and its add button is
`kingdom.roster.addCompanion`; `dialogs/RosterPanel.kt` is documented "Dialog for adding a new
companion/NPC to the roster" (`:22-24`) and contains `RosterAddDialog` (`:36`) and `RosterEditDialog`
(`:164`) — there is **no `RosterPanel` class at all**. Putting a PC renown card there would also
contradict this plan's own §6.1 ("companions are not populace-renown PCs").

**Recommendation: the Party tab** (`MainNavEntry.PARTY`, `MainNavEntry.kt:16`;
`applications/kingdom/sections/party/page.hbs`, registered as the `kingdom-party` partial in
`Main.kt`). That tab is the only sheet surface that already reads real PCs:
`buildPartyInfluenceContext(companions = …, members = actor.partyMembers(), …)`
(`KingdomSheet.kt:3682-3696`), where `fun PF2EParty.partyMembers(): Array<PF2ECharacter>`
(`actor/Game.kt:13`). It needs no new nav entry — parity with how Diplomacy reused the Trade
Agreements section rather than adding nav.

**But it gets its OWN section**, not a row inside the existing loop: the party board is organised as
*companion × party-member influence* (`CompanionInfluenceGroupContext.members`,
`PartyInfluenceContext.kt:19-25`), and renown is per-PC, not per-(companion, PC) pair. The renown
card list is keyed by `actor.partyMembers()` and cross-referenced to `kingdom.leaders.<role>.uuid` so
the card can show which role that PC holds.

**Files:**
- `src/jsMain/resources/applications/kingdom/sections/party/renown-card.hbs` — one card per PC:
  populace renown bar, earned epithet chips, per-faction renown chips.
- `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/contexts/RenownContext.kt` — context objects.

**Context objects:**

```kotlin
@JsPlainObject
external interface PcRenownContext {
    val actorUuid: String
    val name: String
    val roleLabel: String?            // the leadership role this PC holds, if any
    // Withheld fields. NULLABLE by design: a viewer who is neither the GM nor this PC's owner gets
    // null, not 0 -- see the visibility note below. A non-nullable Int would force the builder to
    // ship either the real number or a lie.
    val populace: Int?
    val populacePct: Int?             // 0-100 for the bar
    val attitudeLabel: String?        // localized populace band label
    val factionRenown: Array<FactionRenownChipContext>?
    val purchaseAccessTier: Int?      // 0 = none
    // Always present: epithets are public honours, announced in chat when granted.
    val epithets: Array<EpithetChipContext>
    val isGM: Boolean                 // gates the "Adjust Renown" GM control
    val isOwn: Boolean                // this viewer owns this PC (players see their own fully)
}

@JsPlainObject external interface EpithetChipContext { val id: String; val label: String; val hint: String? }
@JsPlainObject external interface FactionRenownChipContext { val factionName: String; val renown: Int; val label: String }
```

**Visibility is enforced at the CONTEXT level, not in the template.** Players own the party actor, so
anything that reaches the context is readable regardless of what the template renders — an
`{{#if isGM}}` around a number that is already in the payload hides nothing. The builder therefore
populates `populace`/`populacePct`/`attitudeLabel`/`factionRenown`/`purchaseAccessTier` **only when
`isGM || isOwn`** and leaves them null otherwise, mirroring the house pattern:
`analyticsLevelTarget(isGM, …) = if (!isGM) null else …` and `filterAnalyticsMetricsForUser`
(`AnalyticsContext.kt:48-66`), both applied at the context builder (`KingdomSheet.kt:3367`).
`{{#if isGM}}` in the template is **layout only**.

So: a player sees their own PC's card in full, and other PCs' **epithet chips** (public honours) with
no raw numbers. The GM sees everything plus an **Adjust Renown** button opening `ModifyPcRenown`
(set/clear populace or faction renown with a reason; manually grant/revoke an epithet — all still
GM-side, no auto-apply concerns since the GM *is* the authority).

### 4.2 Analytics — optional per-player series

`AnalyticsContext` gains an optional per-actor contribution series built by extending
`TurnAnalytics.extractSeries` to read `RawTurnRecord.contributions` (e.g. metric
`"contribution:<actorUuid>:crits"`), rendered by the existing `sections/analytics/metric-chart.hbs`.
Zero new *chart* code — `mapSeriesToCoordinates`/`summarizeSeries` (`TurnAnalytics.kt:50+`) are
metric-agnostic. `extractSeries` (`:20-48`) is a closed `when (metric)` over 13 literal keys with
`else -> null`, so the contribution metric needs a **prefix branch** ahead of that `else`, parsing
`contribution:<uuid>:<field>` and summing the matching row out of `record.contributions`.

**Visibility decision (this is the part that would otherwise ship dead).** Metric filtering is
deny-by-default: `analyticsPlayerSafeMetricKeys = setOf("unrest", "fame", "resourcePoints", "size",
"level")` (`AnalyticsContext.kt:48`) and `filterAnalyticsMetricsForUser` (`:50-55`), applied at
`KingdomSheet.kt:3367`. A `contribution:<uuid>:crits` key is not in that set, so a non-GM would see
**nothing** — invisible to exactly the players it exists for.

Fix: widen the player-safe path rather than the set. `filterAnalyticsMetricsForUser` gains an
`ownedActorUuids: Set<String> = emptySet()` parameter and, for a non-GM, additionally admits keys
matching `contribution:<uuid>:*` where `uuid ∈ ownedActorUuids`. A player sees their own contribution
series and no one else's; the GM keeps seeing every series. The default-empty parameter keeps the
existing call sites and unit tests compiling unchanged.

### 4.3 i18n namespace

All keys nested under `kingdom.renown.*` (nested objects, never flat-dotted) in **all eight**
`lang/*.json` files — `de`, `en`, `fr`, `it`, `pl`, `pt-BR`, `ru`, `zh-Hans`, every one of them wired
in `module.json`'s `languages` array. `scripts/check_i18n_keys.py::check_parity` (`:242-291`) reports
every MISSING and EXTRA key per locale **plus placeholder mismatches**, and CI runs
`python3 scripts/check_i18n_keys.py --all` (`.github/workflows/test.yml:27`), which calls it
(`:466-469`). Adding ~25 `kingdom.renown.*` keys to `en.json` alone fails CI on seven locales.

```json
"kingdom": {
  "renown": {
    "title": "Renown",
    "populace": "Populace",
    "band": { "unknown": "Unknown", "known": "Known", "renowned": "Renowned", "celebrated": "Celebrated", "legendary": "Legendary" },
    "epithet": {
      "bridgeBuilder": { "label": "The Bridge-Builder", "hint": "Forged ties across the realm." },
      "restovsHammer": { "label": "Restov's Hammer", "hint": "A martial faction's champion." }
      /* … one entry per epithet in §5 … */
    },
    "perk": { "purchaseAccess": "Favoured Customer (tier {{tier}}): settlements offer items {{levels}} level(s) higher", "invitation": "Invitation" },
    "spotlight": {
      "busiest": "🌟 Spotlight: {{name}} never stopped working ({{count}} actions).",
      "critStar": "🌟 Spotlight: {{name}} was on fire — {{count}} critical successes!",
      "blunderer": "😬 Spotlight: {{name}} had… a turn ({{count}} critical fumbles).",
      "diplomat": "🌟 Spotlight: {{name}} charmed the realm this turn.",
      "eventHero": "🌟 Spotlight: {{name}} steadied the kingdom through {{count}} crises."
    },
    "offer": {
      "epithet": "{{name}} has earned the epithet {{epithet}}.",
      "purchaseAccess": "{{name}}'s renown could open better stock to the realm's shops (tier {{tier}}).",
      "invitation": "{{faction}} wishes to fête {{name}} — spawn an invitation?"
    },
    "dialog": { "adjust": "Adjust Renown", "reason": "Reason", "grantEpithet": "Grant Epithet", "revokeEpithet": "Revoke Epithet" }
  }
}
```

**Epithet and Spotlight labels resolve through a LITERAL `when`, never string interpolation.**
`is_dynamic(k)` in `scripts/check_i18n_keys.py` (`:119-121`) returns True for any key containing `$`
or `{`, and check 2 (`:415-417`) skips those — so `t("kingdom.renown.epithet.$id.label")` is
invisible to the guard and a typo ships as a raw key in the UI (`MainNavEntry.i18nKey =
"kingdomMainNav.$value"`, `MainNavEntry.kt:33`, is the live example of the invisible shape). Write:

```kotlin
fun epithetLabelKey(id: String): String? = when (id) {
    "bridgeBuilder" -> "kingdom.renown.epithet.bridgeBuilder.label"
    "restovsHammer" -> "kingdom.renown.epithet.restovsHammer.label"
    // … one arm per Appendix entry …
    else -> null
}
```

— same shape for `hint` and for `SpotlightKind`. If the catalog ever ships data-driven instead, add a
targeted guard modelled on check 7, `check_setup_wizard_keys` (`:356+`), which exists for precisely
this reason ("Check 2 scans for literal key strings, so a dynamically built key is invisible to it").

**Spotlight tone rules** (§3.4 puts this line in the gazette, where the whole table reads it):

- **Player-safe by construction.** A Spotlight line may reference only that PC's own public deeds and
  their counts. Never GM-only state — no clock progress, no hidden DCs, no war pressure.
- **Celebratory by default.** Four of the five `SpotlightKind` lines praise; the fifth is the
  exception below.
- **Sarcasm is aimed at the dice, never the player.** "had… a turn (3 critical fumbles)" is about the
  d20. "botched everything" is about a person; do not write it.
- **`BLUNDERER` is rationed.** It is picked only when `critFails >= 2` **and** that PC is not also the
  crit star of the same turn — nobody gets called a fool for the turn they carried.
- **Read every line back as the named player.** If a line would sting to receive with your character's
  name in it, it does not ship.

---

## 5. Chat / Offer Surfaces (GM-Confirmed Only)

Every renown consequence that grants a **mechanical** benefit is a GM-confirmed offer, whispered to
GMs, using the established `km-offer-*` `ChatButton` pattern in
`kingdom/ChatButtons.kt` (each handler is `{ game, actor, event, button -> … }`, guarded by
`if (!game.user.isGM) return@ChatButton`, reading `button.dataset[...]`, mutating via
`actor.getKingdom()` / `actor.setKingdom(...)` — mirroring `km-offer-expedition-reward` and
`km-offer-companion-levelup`). The Spotlight line is **not** an offer (no mechanical benefit).

| Trigger (at End Turn) | Offer `ChatButton` id | Buttons | Handler behaviour |
|-----------------------|-----------------------|---------|-------------------|
| PC crosses an epithet threshold | `km-offer-renown-epithet` | [Grant Epithet] [Dismiss] | Add `epithetId` to that PC's `RawPcRenown.epithets`; post a public flavour line. Pure honour, no rules effect. |
| Epithet whose perk is a purchase-access tier | `km-offer-renown-perk-access` | [Grant Access Tier] [Dismiss] | Set `RawPcRenown.purchaseAccessTier = tier`. **Consumed by the settlement item-purchase LEVEL** (§6), not by a price. |
| Epithet whose perk is an invitation | `km-offer-renown-invitation` | [Create Invitation] [Dismiss] | Open `AddQuest` prefilled (invitation event; faction as giver when known) — reuses the existing quest pipeline. |

**Perk vocabulary is a CLOSED set of two** (`RenownPerk`): `PurchaseAccess(levels)` and
`Invitation(factionName?)`. No other perk types exist; renown can never grant kingdom bonuses, XP,
RP, or check modifiers. This bounds the feature and is what keeps renown "colour, not economy".

**Why a purchase-LEVEL nudge and not a price discount** (this answers Open Question #1 in the body,
because the earlier draft's "-5% / -10%" described a surface that does not exist). `InspectSettlement`
renders **no prices at all** — `grep -ci price` over the file returns 0. What it renders is item
*levels*: `unionSettlementAccess(…, baseItemLevel = parsed.itemPurchaseLevel, grants = questGrants)`
(`InspectSettlement.kt:513-519`) feeding `calculateAvailableItems(settlementLevel = basePurchaseLevel,
…)`, whose output is printed as `t("kingdom.availableItemLevels", …)` (`:525-531`). A price-discount
perk would have had no consumer, and its jsTest would have been unwritable. The purchase-level nudge
rides the seam the code already documents at `:509-511`: "The item level is unioned HERE, before
calculateAvailableItems consumes it, so a granted purchase level actually widens what the settlement
can buy rather than only changing a displayed number."

**Whose renown applies (the shopper problem), decided.** `InspectSettlement` has no shopper: its
constructor (`:178-191`) takes a settlement, a kingdom and callbacks — no viewer, no user, no PC —
and a grep for `shopper|viewer|isGM|actorUuid|PF2ECharacter` over the file returns nothing. Threading
a `viewerActorUuid` would touch both call sites (`KingdomSheet.kt:1716`, `StructureBrowser.kt:311`)
and still not know who is *buying*. **Decision: the nudge is settlement-scoped.** The realm's shops
stock better goods when *anyone* the realm reveres walks in, so the applied bonus is
`kingdom.renown.maxOf { purchaseAccessLevelsForTier(it.purchaseAccessTier ?: 0) }`. The GM granted
every tier by hand, so there is no exploit surface, and the perk stays legible ("our reputation opens
doors") instead of becoming a per-character shopping mode.

**Dedup:** offers carry the target turn + PC; `lastOfferedTurn` on `RawPcRenown` plus
"epithet not already held" / "purchaseAccessTier not already ≥ tier" guards ensure each offer fires
once, matching the crossing-once discipline of `shouldOfferWarThreat`.

---

## 6. Interactions with Existing Systems

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Roll ownership / leaders** | `dialogs/KingdomRoll.kt` (`rollCheck` @ :96), `dialogs/KingdomCheckDialog.kt`, `Leaders.kt`, `data/RawLeaders.kt`, `data/kingdom/leaders/LeaderActors.kt`, `KingdomData.kt` (`parseLeaderActors` @ :659) | The seam. `rollCheck` gains `leaderActorUuid` / `factionName` / `deedId`; `Leader.fromString(data.leader)` → `parseLeaderActors().resolve(leader)` resolves the acting PC, credited only for `LeaderType.PC` (§3.2). |
| **Re-rolls** | `ReRolls.kt` (`generateRollMeta` @ :95, `parseRollMeta` @ :58, `reRoll` @ :170 → `rollCheck` @ :184), `chatmessages/roll-flavor.hbs` | Three new `.km-roll-meta` data attributes carry the PC, faction and deed id through a re-roll so the deed is replaced, not double-counted (§3.2). |
| **Turn history / recap** | `TurnHistory.kt` (`formatTurnGazette` @ :80, `computeLastTurnRecap` @ :259), `data/RawTurnRecord.kt`, `dialogs/TurnWizardApplication.kt` (`performEndTurn` @ :250 with `buildTurnRecord` @ :627; `postLastTurnRecap` @ :849) | `RawTurnRecord.contributions` flushed at End Turn; `computeLastTurnRecap` gains `spotlight`; the recap chat card renders it at the **next Turn Wizard open** (§3.3), and the same line lands in `notes`/`playerNotes` (§3.4). |
| **Petition Inbox** | `docs/plans/2026-07-09-plan-petition-inbox.md:28` (`RawPetition.targetRole: String`) | Answering a petition credits the owning leader PC via `PETITION_ANSWERED`, resolved with `Leader.fromString(targetRole)` → `parseLeaderActors().resolve(...)` — `RawLeaders` is not indexable (`RawLeaders.kt:13-23`). |
| **Faction relations** | `data/kingdom/FactionRelations.kt`, `data/RawGroup.kt` | Per-faction *personal* renown is a **separate** carrier (`RawPcFactionRenown`); it never touches `RawGroup.standing`, which no kingdom check writes either (§2.4). |
| **Settlement benefits** | `dialogs/InspectSettlement.kt:509-531`, `kingdom/QuestAccessGrants.kt` (`unionSettlementAccess` @ :38) | The `PurchaseAccess` perk is **consumed** here as an extra kingdom-wide `AccessGrant(benefitType = ACCESS_BENEFIT_ITEM_LEVEL, amount = parsed.itemPurchaseLevel + levels, sourceQuestId = RENOWN_ACCESS_SOURCE)` concatenated onto `grants = questGrants` at `:518`. `unionSettlementAccess` takes the max, so it composes with quest grants for free and needs **no signature change**. Safe against the "granted by: <quest>" lines at `:558-571`: those only render trainer/crafting grants (they require `grant.value`, which an itemLevel grant leaves null). Read-only consumer. |
| **Analytics** | `TurnAnalytics.kt` (`extractSeries` @ :20), `sheet/contexts/AnalyticsContext.kt` (`filterAnalyticsMetricsForUser` @ :50), `sections/analytics/metric-chart.hbs` | Per-actor contribution series adds a prefix branch to `extractSeries` and widens the player-safe filter to a viewer's own uuid (§4.2). |
| **Quest generator** | `dialogs/AddQuest.kt` | The `Invitation` perk offer opens `AddQuest` prefilled. |
| **Migrations** | `migrations/Migrations.kt`, `migrations/migrations/Migration<next>.kt`, `MigrationChainTest.kt:23` | Register `Migration<next>`; bump the contiguity assertion range **and** the test's own name (§2.6). |
| **Daily tick** | `DailyTickHooks.kt` | **No interaction** — renown composes on the monthly turn surface only. |

### 6.1 Explicit OUT-OF-SCOPE

- **Battle-command attribution.** `ResolveBattle.kt` has no acting-PC in scope today (§2.1). A future
  seam could credit `leaders.general.uuid` at battle end, but this plan does **not** instrument battle
  resolution — no `BATTLE` contribution kind ships.
- **Expedition-launch attribution.** Expeditions are companion-scoped and GM-initiated; there is no
  single launching PC. Companions keep their own `careerExpeditions`/`careerTriumphs` on
  `RawCharacter`; those are **not** populace renown and are not merged here.
- **Manual degree upgrade/downgrade.** The chat context menu's *Upgrade/Downgrade Degree* entries
  (`ContextMenus.kt` → `changeDegree`, `ChangeDegree.kt:52`) rewrite a posted result **without**
  re-entering `rollCheck`, so
  they do not re-credit renown. That is deliberate: it is a GM correction to an existing message, not
  a new deed. A GM who wants the ledger to match can use *Adjust Renown* (§4.1).
- **Renown affecting kingdom checks.** No feedback loop: renown never modifies a DC, roll, or
  resource. Perks are the closed 2-element set only.
- **PC-vs-PC competition mechanics**, leaderboards, or renown decay races. Renown is sticky by design
  (an *optional* GM toggle for slow monthly fade-toward-zero is noted as a future switch, default off —
  epithets are earned, not rented).
- **Cross-kingdom persistence.** Each kingdom actor tracks its own renown ledger.
- **Retroactive backfill** of renown from historical `turnHistory` (legacy turns have no
  `contributions`); renown starts accruing from first instrumented check post-upgrade.

---

## 7. Test Plan

### 7.1 commonTest (pure, JVM-less) — `RenownEngineTest.kt`

| Test | Asserts |
|------|---------|
| `accrueRenown_critAddsPopulaceAndFaction` | CHECK_CRIT with a faction → +3 populace, +2 that faction. |
| `accrueRenown_successAmounts` | CHECK_SUCCESS → +1/+1; ACTIVITY → +1/0; EVENT_RESOLVED → +2/0. |
| `accrueRenown_critFailIsNegativeButFloored` | CHECK_CRIT_FAIL → −2 populace, clamped ≥ 0. |
| `accrueRenown_clampsPopulaceAndFactionSoftCap` | populace ∈ [0,100]; faction ∈ [−40,40]. |
| `accrueRenown_isImmutable` | input `PcRenown` unchanged; new instance returned. |
| `accrueRenown_reportsAppliedDeltasAfterClamping` | at populace 99, CHECK_CRIT reports `populaceApplied = 1`, not 3. |
| `revertRenown_isExactInverseOfAccrue` | accrue-then-revert returns the original `PcRenown`, **including** every lifetime counter — asserted at the 0 / 100 / ±40 clamp boundaries. |
| `revertRenown_neverRevokesEpithets` | reverting a deed leaves `epithets` untouched. |
| `accrueRenown_maintainsRoleAndCategoryTallies` | a TREASURER CHECK_SUCCESS bumps `roleSuccesses[TREASURER]`; a diplomatic ACTIVITY bumps `categoryActivities[DIPLOMATIC]`. |
| `deedCategoryFor_mapsSkillsToBuckets` | STATECRAFT/POLITICS/TRADE/INTRIGUE → DIPLOMATIC; WARFARE/DEFENSE → MARTIAL; AGRICULTURE → OTHER. |
| `epithetContextFor_derivesEveryCatalogCondition` | every Appendix condition reads a field the context actually carries — the regression guard against reintroducing an underivable epithet. |
| `evaluateEpithets_firesOnceOnCrossing` | crossing a threshold awards; re-eval at/above it does **not** re-award. |
| `evaluateEpithets_negativeNemesisEpithet` | faction renown ≤ −25 with Pitax → "Scourge of Pitax". |
| `perkForEpithet_isClosedSet` | only `PurchaseAccess`/`Invitation`/null ever returned. |
| `spotlight_picksTopContributorWeighted` | crits outweigh checks; deterministic tie-break by uuid. |
| `spotlight_blundererWhenCritFailsDominate` | a crit-fail-heavy turn selects `BLUNDERER`. |
| `spotlight_neverBlundererForTheTopScorer` | a PC who both crit-failed twice **and** leads the weighted score is celebrated, not mocked (§4.3 tone rule, enforced in the pure function). |
| `spotlight_nullWhenNobodyContributed` | empty tallies → null. |
| `personalRenownNeverFeedsKingdomStanding` | the engine exposes no path from `PcRenown` to `RawGroup.standing` (compile-level + doc test). |

### 7.2 jsTest (Foundry-integrated)

| Test | Asserts |
|------|---------|
| `recordContribution_bumpsTallyAndRenown` | one CHECK_CRIT updates `currentTurnContributions` and `renown`. |
| `recordContribution_createsRowForNewActor` | first deed for a PC creates its row. |
| `reroll_doesNotDoubleCreditTheSameCheck` | two `recordContribution` calls with the **same** `deedId` leave exactly one deed in `currentTurnDeeds` and one increment in the tally. |
| `reroll_creditsTheFinalDegree` | a CHECK_FAILURE deed re-recorded as CHECK_CRIT under the same `deedId` leaves the PC at the crit's renown, not failure + crit. |
| `rollMeta_roundTripsLeaderFactionAndDeedId` | `generateRollMeta` → `.km-roll-meta` → `parseRollMeta` preserves all three new fields. |
| `noCreditWhenLeaderUnselected` | `selectedLeader == null` (the `Leader.RULER` fallback path) records **nothing** — the Ruler's PC gains no renown. |
| `noCreditForNpcLeader` | a role whose `LeaderActor.type` is `REGULAR_NPC` records nothing. |
| `factionNameOnlyForNegotiationActivities` | a NEGOTIATION activity with a picked group sets `factionName`; a non-negotiation activity leaves it null. |
| `endTurn_flushesTalliesIntoRecordAndResets` | `RawTurnRecord.contributions` populated; `currentTurnContributions` **and** `currentTurnDeeds` reset to empty. |
| `computeLastTurnRecap_includesSpotlight` | recap `spotlight` non-null when a contributor exists. |
| `endTurn_emitsEpithetOfferOnce` | crossing a threshold whispers one `km-offer-renown-epithet`; next turn without a new crossing emits none. |
| `purchaseAccessPerkRaisesAvailableItemLevel` | a PC with `purchaseAccessTier = 2` raises `unionSettlementAccess(...).itemLevel` by 2 above `parsed.itemPurchaseLevel`, and it composes (max) with a quest itemLevel grant. |
| `renownContextWithholdsOtherPcNumbersFromPlayers` | for a non-GM, non-owner viewer the built `PcRenownContext` has `populace == null` and `factionRenown == null`, while `epithets` is still populated. |
| `analyticsPlayerSeesOnlyOwnContributionSeries` | a non-GM with `ownedActorUuids = {A}` gets `contribution:A:*` and **not** `contribution:B:*`; a GM gets both. |
| `migration<next>_backfillsRenownAndTallies` | run through the chain → `renown`/`currentTurnContributions`/`currentTurnDeeds` defined; the contiguity assertion still holds at its bumped range. |
| `analyticsPerPlayerSeriesExtracts` | `extractSeries("contribution:<uuid>:crits")` returns the per-turn series. |

### 7.3 Manual Foundry verification checklist

1. Fresh kingdom, 3 PCs assigned to leader roles (Ruler/Emissary/Treasurer), all with
   `LeaderType.PC`. Set the active leader (the world setting `getActiveLeader` reads) — leave it
   unset for step 3a.
2. As the Emissary's owner, roll a diplomacy check toward Pitax → crit-succeed. Reopen sheet →
   **Party** tab → Emissary's Renown card shows populace +3 and a Pitax faction chip +2.
   *(The card is on Party, not Roster — the Roster is companions/NPCs.)*
3. Confirm Pitax's **kingdom** `standing` is **unchanged**: no kingdom check writes it (§2.4). Move it
   with the GM's *Adjust Standing* control and confirm the personal chip does **not** follow.
   3a. Clear the active-leader setting, roll again → **no** renown is recorded for anyone (the Ruler
   must not be credited by default). Re-set it before continuing.
   3b. Assign a role to an NPC, roll as that role → no renown recorded.
4. Right-click the crit's chat card → **Reroll** → it fails. Confirm the Emissary's populace renown
   shows the *failure* result only — the deed was replaced, not stacked (§3.2).
5. Perform several activities + resolve an event across a couple of turns to push the Emissary's
   populace renown past 30.
6. Click **End Turn** → the `km-offer-renown-epithet` whisper appears immediately. Then **reopen the
   Turn Wizard** → the recap chat card shows the **Spotlight of the Turn** line. *(Two different
   seams: `performEndTurn` vs `postLastTurnRecap` at turn open — §3.3.)* Check Session Prep →
   Recent Turns: the same Spotlight line is in the turn's gazette notes (§3.4).
7. Click **Grant Epithet** → epithet chip appears on the card; public flavour line posts.
8. Trigger a purchase-access epithet → **Grant Access Tier** → open any settlement's Inspect view →
   the available item **levels** are one/two higher than before. Confirm it composes with (does not
   replace) an existing quest itemLevel grant.
9. Trigger an invitation epithet → **Create Invitation** → `AddQuest` opens prefilled → save → quest
   appears in Quests.
10. As a non-GM player, confirm on the **Party** tab that you see your own PC's full card and other
    PCs' epithet chips **with no numbers at all** (not zeroes), and no GM adjust control; the
    Spotlight line is visible to all.
11. Open Analytics as that player → **your own** contribution series renders and no other PC's does;
    as GM, every PC's series renders.
12. Reload the world → renown, epithets, purchase-access tier, and `contributions` persist.
13. Load a save from before `Migration<next>` → no crash; renown starts empty and accrues from the
    next check.

---

## 8. Phasing (Independently Committable)

Each phase compiles and tests green on its own; each is one kanban card.

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Data model + pure engine + migration** | `RawTurnContribution`, `RawRenown` (incl. `RawRoleTally`/`RawCategoryTally`/`RawRenownDeed`), `RawTurnRecord.contributions`, `KingdomData.renown`/`currentTurnContributions`/`currentTurnDeeds`, `Migration<next>` (+contiguity range **and test name** bump), pure `RenownEngine.kt` (accrue/revert/epithets/perk/spotlight/`deedCategoryFor`) with `RenownEngineTest`. | `data/RawTurnContribution.kt`, `data/RawRenown.kt`, `data/RawTurnRecord.kt`, `KingdomData.kt`, `migrations/migrations/Migration<next>.kt`, `migrations/Migrations.kt`, `jsTest/.../MigrationChainTest.kt`, `data/kingdom/RenownEngine.kt`, `commonTest/RenownEngineTest.kt` |
| **2** | **Attribution seam (incl. re-rolls)** | `recordContribution` + jsMain adapter; instrument `rollCheck` (`leaderActorUuid`/`factionName`/`deedId`) covering checks/crits/activities/events; `factionName` plumbing through `CheckDialogParams`; the three new `.km-roll-meta` attributes so `reRoll` replaces its deed; the `LeaderType.PC` / null-leader record-or-skip rule; instrument petition answers. | `RecordContribution.kt`, `RenownAdapter.kt`, `dialogs/KingdomRoll.kt`, `dialogs/KingdomCheckDialog.kt`, `ReRolls.kt`, `resources/chatmessages/roll-flavor.hbs`, petition handler, `jsTest/RecordContributionTest.kt` |
| **3** | **Recap + Spotlight + gazette + offers** | End-Turn flush + reset (both arrays); `LastTurnRecap.spotlight` + recap card at turn open; `formatTurnGazette` spotlight line **plus its `defaultLocalize` twin** (check 6); epithet/perk `km-offer-*` handlers (3); Analytics per-player series + player-safe filter widening; i18n `kingdom.renown.*` in **all 8 locales**. | `dialogs/TurnWizardApplication.kt`, `TurnHistory.kt`, `ChatButtons.kt`, `TurnAnalytics.kt`, `sheet/contexts/AnalyticsContext.kt`, `resources/chatmessages/last-turn-recap.hbs`, `lang/de.json`, `lang/en.json`, `lang/fr.json`, `lang/it.json`, `lang/pl.json`, `lang/pt-BR.json`, `lang/ru.json`, `lang/zh-Hans.json` |
| **4** | **Party UI + GM dialog + purchase-access consumption** | Renown card section on the **Party** tab, `RenownContext` (withheld fields nullable, nulled in the builder), `ModifyPcRenown` GM dialog, purchase-access consumption in `InspectSettlement`, full manual QA. **Register the new partial** in `Main.kt` `loadTemplatePartials` (`:134`) as `"kingdom-renown-card" to "applications/kingdom/sections/party/renown-card.hbs"` and reference it by that **name**, never by path (an unregistered partial fails at render with "partial X could not be found"). Inside `{{#each}}` use `@root`, never `../` — a registered partial has no parent frame, and `scripts/check_hbs_scope.py` enforces it in CI (`.github/workflows/test.yml:36`). | `sheet/contexts/RenownContext.kt`, `sections/party/renown-card.hbs`, `sections/party/page.hbs`, `Main.kt`, `sheet/KingdomSheet.kt` (party context builder, `:3682-3696`), `dialogs/ModifyPcRenown.kt`, `dialogs/InspectSettlement.kt` |

**Total: 4 phases.** Phase 1 is standalone. Phase 2 depends on 1. Phase 3 depends on 1–2. Phase 4
depends on 1 (and reads renown produced by 2–3, but its UI degrades gracefully to empty cards).

---

## 9. Open Questions for Gregory

1. ~~**Discount tier magnitudes**~~ — **decided in §5**: a percentage discount was unbuildable
   (`InspectSettlement` renders no prices at all), so the perk is a purchase-**level** nudge,
   tier 1 = +1 level, tier 2 = +2, settlement-scoped. Remaining judgement call for Gregory: are +1/+2
   levels too generous for a cosmetic perk, or should tier 2 also cap at +1?
2. **Optional monthly renown fade** — ship the default-off "slow decay toward zero" toggle now, or
   leave renown fully sticky? (Plan: sticky; toggle is a noted future switch.)
3. **Spotlight tone** — the `BLUNDERER` "worst of the turn" line is playful; keep it, or make it
   opt-in so tables that dislike naming-and-shaming can disable it?
4. ~~**Epithet catalog ownership**~~ — **decided in §4.3**: a fixed Kotlin table with a literal
   `when` for the i18n keys. JSON matches the faction-agenda precedent, but it would make every
   epithet key dynamic (invisible to `check_i18n_keys.py` check 2) and would require a
   `translateEpithets()` wired into `initLocalization()` (`Localization.kt:126-156`) or check 4
   fails on an unwired translator. Remaining question: is GM-editability of the catalog worth paying
   for a targeted guard (modelled on check 7) later?
5. **Battle credit later** — worth a follow-up card to thread `leaders.general.uuid` into
   `ResolveBattle` so martial epithets (e.g. "Restov's Hammer") can accrue from real battles rather
   than only from martial *checks*?

---

## Appendix: Epithet Catalog (12 authored)

Ids are stable; labels/hints are i18n keys under `kingdom.renown.epithet.*`, resolved through the
literal `when` mandated in §4.3. "Perk" uses the closed set from §5. **Every condition below names a
field that `EpithetContext` (§3.1) actually carries** — that is the point of the lifetime counters in
§2.3, and `epithetContextFor_derivesEveryCatalogCondition` (§7.1) is the guard that keeps it true.

| # | Epithet id | Label | Condition (as an `EpithetContext` expression) | Perk |
|---|-----------|-------|-----------|------|
| 1 | `bridgeBuilder` | The Bridge-Builder | `populace ≥ 30 && categoryActivities[DIPLOMATIC] ≥ 5` | `Invitation(null)` |
| 2 | `restovsHammer` | Restov's Hammer | `factionRenown[name] ≥ 25` for a name in the entry's martial list (Restov, Swordlords) | — |
| 3 | `theUntiring` | The Untiring | `activities ≥ 20` (any category) | `PurchaseAccess(1)` |
| 4 | `coinCounter` | Coin-Counter | `roleSuccesses[TREASURER] ≥ 15` | `PurchaseAccess(2)` |
| 5 | `theIronhand` | The Ironhand | `populace ≥ 40 && critFails ≥ 3` (feared, not loved) | — |
| 6 | `feyFriend` | Fey-Friend | `factionRenown["Narlmarches"] ≥ 25` | `Invitation("Narlmarches")` |
| 7 | `theUnshaken` | The Unshaken | `eventsResolved ≥ 5` | — |
| 8 | `wardenOfTheMarches` | Warden of the Marches | `roleSuccesses[WARDEN] ≥ 10` | — |
| 9 | `theSilverTongue` | The Silver Tongue | `roleCrits[EMISSARY] ≥ 8` | `Invitation(null)` |
| 10 | `peoplesChampion` | People's Champion | `populace ≥ 50` | `PurchaseAccess(2)` |
| 11 | `theKingmaker` | The Kingmaker | `populace ≥ 75 && holdsRulerRole` | — |
| 12 | `scourgeOfPitax` | Scourge of Pitax | `factionRenown["Pitax"] ≤ −25` (nemesis / negative-standing) | — |

Entries 2, 6 and 12 depend on `factionName` reaching `recordContribution`, which is why §3.2 plumbs
it through `CheckDialogParams`; without that plumbing they are permanently unreachable rather than
merely rare. They also key off free-text `RawGroup.name` (§3.1), so a campaign that renamed those
factions never earns them.

---

**End of Plan.** Ready for review. On approval, implementation cards follow the §8 phasing table.

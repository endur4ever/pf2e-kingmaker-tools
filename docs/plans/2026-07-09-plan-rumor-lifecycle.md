# Dynamic Rumor Lifecycle — Implementation Plan

> **Date:** 2026-07-09 (written 2026-08-25)
> **Card:** `t_587ac51a` — *plan: dynamic rumor lifecycle — rumors age, mutate, expire, and convert*
> **Status:** Plan only. No `src/` changes until Gregory reviews.

---

## 0. A correction the card's premise requires

The card says rumors *"accumulate forever"* and that the feature should add aging plus a prune pass
to an existing store. **That is not what the shipped code does**, and the difference changes the
whole shape of the work.

`CampingData.rumors` is declared (`camping/CampingData.kt:223`) and a read helper exists
(`fun CampingData.rumorList(): List<Rumor>`, `camping/EncounterCuratorData.kt:120`) — but **nothing
in `src/` ever writes the array, and the read helper has zero call sites.** `grep -rn "\.rumors" src/`
returns nothing; `grep -rn "rumorList" src/` returns only its own declaration.

What actually happens today: `showEncounterPreview` builds a **transient** `Rumor(...)` in a local
val (`camping/RandomEncounters.kt:174-178`), hands it to `EncounterPreviewDialog`, and discards it
when the dialog closes. If the GM clicks *Convert to quest*, `convertRumorToQuest` mints a quest and
the rumor object is still thrown away.

Consequences for this plan, all load-bearing:

1. **There is no legacy data to migrate or prune.** The array is `null` in every world that exists.
   No `Migration<N>` is required for the rumor store itself (§4 revisits this).
2. **This feature wires up a store that was declared but never filled** — it does not retrofit aging
   onto an accumulating one. The first phase is a *write path*, not an aging engine.
3. **`RawRumor` has no identity.** No `id`, no timestamp, no turn. Every lifecycle field is net-new,
   and an `id` is mandatory before any chat card can refer to a specific rumor (§3).
4. The card's requested *"cap/prune policy replacing the deferred cleanup pass"* is really **"a cap
   from day one"** — there is no deferred pass to replace, only a `TODO` in the curator plan doc.

The player-facing goal survives intact: information should feel alive. The engineering is just
greenfield rather than remedial.

---

## 1. Problem statement + player/GM value

The curator can produce a rumor, show it once in a preview dialog, and then it is gone. A rumor the
party hears at camp has no existence the next morning: it cannot go stale, cannot turn out to be
false, and cannot come back changed. The GM's only durable option is to convert it into a quest
immediately — a heavy commitment for a piece of hearsay — or to write it in their own notes, which
is the friction the curator existed to remove.

**For players:** rumors become a standing, readable body of information with a texture of
reliability. *"The trolls at Candlemere have stopped raiding"* is a different beat when the party
remembers hearing three weeks ago that trolls were massing there. Some rumors turn out to be false,
and finding that out is play.

**For the GM:** a rumor board that ages itself, tells them when something is about to lapse, and
offers — never applies — the consequence of an ignored lead. The GM keeps every decision; the module
keeps the bookkeeping.

**Why this earns table time:** it converts a one-shot dialog into a recurring source of session
hooks, and it makes the existing rumor-to-quest conversion reachable *later* than the moment the
rumor is rolled — which is when a GM actually knows whether the party cared.

---

## 2. Position: what ages, and what a "day" means here

Rumors age on the **world clock**, in `DailyTickHooks` — the same tick that already advances
companion travel, downtime projects, and scheduled pressures. Not the monthly End Turn: a rumor's
half-life is days of travel and camping, not kingdom turns, and a rumor heard on day 3 of a month
should not survive to the next End Turn purely because the tick has not come round.

The tick discipline every existing daily ticker follows applies unchanged: `daysCrossed(worldTime,
deltaInSeconds)` yields how many day boundaries a jump crossed, and **a seven-day jump must age a
rumor by seven days, not one** — the bug this module has fixed repeatedly (`DowntimeProjects`
carries the same warning in its KDoc). Aging is therefore expressed as *"advance by `days`"*, never
*"advance by one"*, and is a pure function so a jump and seven single steps are provably identical.

Time is stored as **world day numbers** (`worldTimeSeconds.floorDiv(DAY_SECONDS)`), never date
strings — the same decision `RawScheduledPressure` documents. A rumor records the day it was born;
its age is arithmetic, not a stored countdown that could drift.

---

## 3. Data model

### 3.1 `RawRumor` — additions only

Existing fields stay exactly as they are (`camping/EncounterCuratorData.kt:46-56`). Note the two
existing non-null booleans (`isQuestHook`, `isConverted`); **new fields follow the camping
convention stated in that file's own KDoc — nullable, read through `*OrDefault` helpers, so camping
data saved before this feature keeps working without a data-touching migration.**

```kotlin
@JsPlainObject
external interface RawRumor {
    // existing: text, isQuestHook, questTemplateId, questTemplateName,
    //           location, sourceRegion, isConverted, convertedQuestId

    /** Stable identity. Mandatory before any chat card can name a specific rumor.
     *  Null only on rows written by a build older than this feature — which is none today. */
    var id: String?
    /** World day number the rumor entered play. Null => treat as born on first sighting (§4). */
    var bornDay: Int?
    /** fresh | stale | expired | converted | pinned — a STRING at this boundary, like
     *  RawScheduledPressure.recurrence; an unrecognised value drops the rumor from evaluation
     *  rather than throwing, so one bad row cannot take down the day's tick. */
    var state: String?
    /** true | distorted | false — the GM's private assessment. Null = unassessed. */
    var veracity: String?
    /** Set when an expiry beat has already been offered, so a declined beat never re-offers. */
    var beatOfferedDay: Int?
    /** Hex the rumor points at, when the generator knew one; enables the hex-hook conversion. */
    var sourceHexKey: String?
}
```

`location` and `sourceRegion` already exist and are prose; `sourceHexKey` is the machine-readable
sibling the hex-hook conversion needs. They are not merged — rewriting `location` would break the
curator's existing display.

### 3.2 `Rumor` (commonMain) — mirrored additions

```kotlin
data class Rumor(
    // existing: text, isQuestHook, questTemplateId, questTemplateName,
    //           location, sourceRegion, isConverted, convertedQuestId
    val id: String = "",
    val bornDay: Int? = null,
    val state: RumorState = RumorState.FRESH,
    val veracity: RumorVeracity? = null,
    val beatOfferedDay: Int? = null,
    val sourceHexKey: String? = null,
)

enum class RumorState(val value: String) {
    FRESH("fresh"), STALE("stale"), EXPIRED("expired"), CONVERTED("converted"), PINNED("pinned");
    companion object { fun fromValue(value: String?): RumorState? = entries.find { it.value == value } }
}

enum class RumorVeracity(val value: String) {
    TRUE("true"), DISTORTED("distorted"), FALSE("false");
    companion object { fun fromValue(value: String?): RumorVeracity? = entries.find { it.value == value } }
}
```

Enums live in `commonMain` and convert at the boundary — the `RawWarThreat.status` /
`WarThreatStatus` precedent. `@JsPlainObject` interfaces never hold enums or `Map`s.

### 3.3 Where it persists — and the one decision that is not obvious

The rumor store stays **on the camping flag** (`CampingData.rumors`), because that is where it is
already declared and because rumors are heard at camp. But two facts complicate it:

- `getCamping()` and `getKingdom()` both **deep clone**, so a read-modify-write from two paths races
  and loses entries. `RandomEncounters.kt:158-161` already documents exactly this hazard for the
  travel journal and GM-gates the write for that reason. **Every rumor write in this plan is
  GM-gated and goes through a single funnel function** (§5.4), for the same reason.
- A rumor→quest conversion spans **both** stores: the rumor is camping-scoped, the quest is
  kingdom-scoped. The existing `convertRumorToQuest` sidesteps this by resolving
  `game.getKingdomActors().firstOrNull()` at call time (`RandomEncounters.kt:471-475`); this plan
  keeps that seam rather than inventing a second one.

**Rejected: moving rumors to the kingdom flag.** It would put camp-side hearsay on the monthly
ledger, and would orphan the curator's existing region/category context. The cross-store conversion
is a two-line resolve, not a reason to relocate the whole store.

### 3.4 Cap

`RUMOR_CAP = 40` rumors, trimming **oldest first, and only `expired` or `converted` rows** —
`fresh`, `stale` and `pinned` rumors are never pruned, because dropping one silently deletes a lead
the party may still be chasing. This mirrors `DOWNTIME_HISTORY_CAP` (completed-only) and
`TREASURE_LEDGER_CAP`. A campaign that somehow holds 40 live rumors stops accumulating rather than
silently forgetting; that is a GM signal, not a failure.

---

## 4. Migration — **not required**, and why that is a real answer

The card asks for "migration for existing persisted rumors". **There are none**: the array has never
been written in any world (§0). Adding nullable fields to an interface whose array is always `null`
changes nothing on disk.

So this plan takes **no migration number**, and explicitly should not reserve one. Two guards make
that safe rather than merely convenient:

- Every new field is nullable and read through a defaulting helper, so a row from any future source
  (a hand-edited world, a third-party macro) decodes without throwing.
- `rumorsOrDefault()` returns `emptyList()` for a null array, exactly as the curator's other
  `*OrDefault` readers do.

> **If this plan is implemented after some other feature starts writing `rumors`,** re-check this
> section before trusting it — the "no legacy data" claim is what makes skipping the migration
> correct, and it is a claim about a specific moment in the repo's history.

---

## 5. Engine design

### 5.1 Pure core (`commonMain`) — `camping/RumorLifecycle.kt`

```kotlin
/** Day thresholds. Tunable per campaign later; pinned here so the state machine is testable. */
const val RUMOR_STALE_AFTER_DAYS = 7
const val RUMOR_EXPIRE_AFTER_DAYS = 21
const val RUMOR_CAP = 40

/** One rumor's transition during a tick, for the caller to persist and narrate. */
data class RumorTransition(
    val rumorId: String,
    val from: RumorState,
    val to: RumorState,
)

data class RumorTickOutcome(
    val rumors: List<Rumor>,        // authoritative next state
    val transitions: List<RumorTransition>,
)

/**
 * Advance every aging rumor across a [days]-day jump.
 *
 * PINNED and CONVERTED rumors are returned untouched — a pinned rumor is the GM saying "this one
 * does not lapse", and a converted one has become a quest that owns its own lifecycle.
 * A rumor with no [Rumor.bornDay] adopts [currentDay] and ages from there rather than being
 * treated as infinitely old, so a row written before this field existed cannot expire instantly.
 */
fun tickRumors(rumors: List<Rumor>, currentDay: Int, days: Int): RumorTickOutcome

/** Rumors whose expiry beat should be offered: EXPIRED, not yet offered, not pinned. */
fun beatCandidates(rumors: List<Rumor>, currentDay: Int): List<Rumor>

/** Trim to [cap], oldest first, EXPIRED/CONVERTED only. Never drops a live lead. */
fun capRumors(rumors: List<Rumor>, cap: Int = RUMOR_CAP): List<Rumor>

/** Deterministic mutation-beat selection: index into the category's table by a seeded roll. */
fun selectMutationBeat(
    rumor: Rumor,
    table: List<String>,
    roll: Int,
): String?
```

`selectMutationBeat` takes the roll **injected**, never rolled inside — the `SeededRng` discipline
the rest of the module follows, so a preview and a commit produce the same beat.

### 5.2 The state machine, concretely

| From | To | When | Emits |
|------|----|------|-------|
| *(none)* | `fresh` | the GM accepts a curated rumor | nothing (it is already on screen) |
| `fresh` | `stale` | age ≥ `RUMOR_STALE_AFTER_DAYS` (7) | **nothing** — a quiet state change; the board dims the row |
| `stale` | `expired` | age ≥ `RUMOR_EXPIRE_AFTER_DAYS` (21) | one GM-whispered **offer** card (§6) |
| any | `converted` | the GM converts it to a quest/hex hook | stops aging permanently |
| any | `pinned` | GM control | stops aging until unpinned |

The `fresh → stale` transition deliberately emits **nothing**. A whisper per rumor per week would be
exactly the notification spam the digest feature exists to eliminate; staleness is a board-state
signal the GM reads when they look, not a push.

### 5.3 Mutation tables — data-driven, per category

Expiry beats live in `data/rumor-mutations.json`, keyed by the curator's existing encounter
category, each a list of prose templates with an i18n key rather than baked English:

```json
{
  "monster": ["rumorMutation.monster.silenced", "rumorMutation.monster.moved", "rumorMutation.monster.grew"],
  "bandit":  ["rumorMutation.bandit.dispersed", "rumorMutation.bandit.turnedRaider"],
  "...":     ["..."]
}
```

The beat's prose is a **template**, not generated text, and is chosen by a seeded roll so it is
reproducible. A category with no table yields **no beat** — the rumor still expires, quietly. This
is deliberate: a missing table must degrade to silence, not to a wrong-flavoured beat.

> **GATED — Gregory's call.** The *contents* of these tables are campaign voice, not engineering.
> This plan specifies the shape, the key namespace, and the fallback; it does **not** invent the
> beat prose. Implementation should ship the file with the `monster` category filled as a worked
> example and the rest empty, exactly as the settlement-life and petition plans defer their catalogs.

### 5.4 The write funnel

Every mutation of the rumor store goes through one place:

```kotlin
// jsMain: camping/RumorStore.kt
suspend fun CampingActor.updateRumors(block: (List<Rumor>) -> List<Rumor>)
```

It reads once, applies `block`, caps, and writes once — GM-gated at the top. This is the answer to
the deep-clone race (§3.3): with a single funnel there is exactly one read-modify-write path, and
the tick, the dialog, and the chat handlers all share it.

---

## 6. Chat / offer surfaces

**Every consequence is a GM-confirmed offer.** Aging itself is bookkeeping and writes silently; the
*expiry beat* and the *ignored-lead consequence* are offers.

### 6.1 Where the buttons must register — the fact that constrains the design

The rumor store is camping-scoped, and **camping does not use the kingdom `ChatButtons` dispatcher.**
It has its own binder, `bindCampingChatEventListeners(game, dispatcher)` (`camping/CampingChat.kt:33`),
whose cards carry `data-camping-actor-uuid` (never `data-kingdom-actor-uuid`) and whose handlers
dispatch `ActionMessage`s through the deny-by-default `ActionDispatcher` rather than mutating
directly.

So: **rumor offer buttons register on the camping side**, as `ActionHandler`s. A conversion that
also writes a quest resolves the kingdom actor at call time, as the existing conversion already does.
Registering them in `ChatButtons.kt` instead would hand the handler a `KingdomActor` and leave it
re-resolving the camping actor to touch its own store — the wrong way round.

### 6.2 The cards

| Card | Buttons | Effect on confirm |
|------|---------|-------------------|
| `chatmessages/rumor-expired-offer.hbs` | `km-offer-rumor-beat` (Post the beat) / `km-offer-rumor-quiet` (Let it fade) | *Beat*: posts the selected mutation beat publicly and marks `beatOfferedDay`. *Quiet*: marks `beatOfferedDay` only — no beat, no re-offer. |
| `chatmessages/rumor-convert-offer.hbs` | `km-offer-rumor-quest` / `km-offer-rumor-hex` / `km-offer-rumor-dismiss` | Convert to a quest (reusing `convertRumorToQuest`), or to a hex hook (`RawHexContent`, net-new — no such path exists today), or record the refusal. |

Two invariants both cards must honour, both learned from shipped bugs:

- **Pin the rumor id in the card.** Aging mutates the store between post and click, so the handler
  must act on the identity the GM read, not on a recomputed "current" rumor — the
  `km-offer-companion-autonomy` lesson.
- **Whisper to GM ids, and bail when there are none.** An empty whisper array posts **publicly**;
  `StarvationOffer` and `LootAward` both carry that early return, and the quest-deadline card
  shipped without it.

> The template must also supply every key it reads. `quest-deadline-offer.hbs` gated its buttons on
> an `isGM` its poster never set and shipped with no buttons at all for months (fixed 2026-08-25,
> `9d535fc1`). Do not copy that card's shape; copy `loot-award-offer.hbs`.

### 6.3 Veracity is never revealed by an offer

Veracity is GM-only. No offer card, no beat, and no player-facing surface exposes it — a false rumor
reads exactly like a true one until play proves otherwise. The GM sets it from the board (§7); the
engine only stores it and lets the GM filter by it.

---

## 7. UI

| Piece | Path |
|-------|------|
| Rumor board section | `applications/camping/rumor-board.hbs` (registered partial) |
| Context | `camping/RumorBoardContext.kt` |
| GM controls | inline on each row: pin toggle, veracity select, convert |
| Styles | `.km-rumor*` in `applications/camping/camping.css` |
| i18n | `pf2e-kingmaker-tools.camping.rumors.*` |

The board lives on the **camping sheet**, in the scrolling content wrapper beside the route planner
and downtime projects — the surface the party already reads between travel days.

**Layout caution, from a bug this repo has hit twice:** `.km-camping-activities-wrapper` is
absolutely positioned and shrink-to-fit, so its widest child sets every sibling's width. Any table
inside it needs `width: 0; min-width: 100%` or an open row silently widens the whole column. Measure
in headless Chrome **before and after**, and assert a computed style (a border or `display`) to prove
the CSS actually applied — a harness parented under the wrong selector measures unstyled markup and
passes vacuously.

**What players see:** rumor text, source region, and state (fresh/stale dimming). **Not** veracity,
**not** GM notes, **not** the expiry countdown in days — a rumor that visibly has "3 days left"
turns hearsay into a quest timer. GM-only pieces are `null` in the player context, not merely hidden
by a template conditional; players are OWNERs of the party actor, so a `{{#if isGM}}` is layout.

State and veracity labels are **literal i18n keys mapped in a `when`** — `t("rumors.state.$state")`
is invisible to `check_i18n_keys.py` and ships as a raw key with every guard green.

---

## 8. Interactions + out of scope

**Reads:** `CampingData.rumors`, world time, the curator's category/region context,
`kingdom.campaignQuests` (dedup on conversion).
**Writes:** `CampingData.rumors` (through the §5.4 funnel only), and on confirmed conversion
`kingdom.campaignQuests` + `kingdom.rumorGeneratedQuestIds` via the existing path.

**Concrete touch points:** `camping/RandomEncounters.kt` (persist the accepted rumor — the write
that does not exist today), `camping/EncounterPreviewDialog.kt` (its *Convert to quest* button is a
`FormApp._onClickAction`, so it stays where it is; a chat-card version must be registered
separately — a sheet button wired to a chat handler is a no-op), `camping/DailyTickHooks`
registration, `camping/CampingSheet.kt` (board context).

**Out of scope:**
- Rumors for anything but the curator's own output (no importing GM notes).
- Auto-conversion of any kind — every conversion is an offer.
- Per-rumor custom day thresholds; the two constants are global until a real need appears.
- Rewriting `convertRumorToQuest`'s quest shape.
- Surfacing `rumorGeneratedQuestIds`, which is currently **write-only with no reader** — worth its
  own small card, but not this one's problem.
- Player-authored rumors.

---

## 9. Test plan

**commonTest** (`RumorLifecycleTest`)
- A 7-day jump moves a fresh rumor born that day straight to `stale`, and a 21-day jump to
  `expired` — **the multi-day case, and the reason this core is pure.**
- Seven single-day ticks and one seven-day tick produce identical output.
- `pinned` and `converted` rumors are returned unchanged by any jump size.
- A rumor with a null `bornDay` adopts the current day rather than expiring instantly.
- Exact thresholds: age 6 is fresh, 7 is stale; 20 is stale, 21 is expired.
- `beatCandidates` excludes already-offered and pinned rumors.
- `capRumors` trims expired/converted oldest-first and **never** drops fresh/stale/pinned, even when
  that leaves the list above the cap.
- An unrecognised `state` string drops the rumor from evaluation and leaves the others intact.
- `selectMutationBeat` is deterministic for a given roll, and returns null for an empty table.

**jsTest**
- Raw↔model round trip preserving every new nullable field, including all-null.
- The board context is null-for-players and non-null for a GM; veracity never appears in the player
  context.
- The write funnel caps on write.

**Mutation-check every new test.** Make the stale threshold `>` instead of `>=`, tick pinned rumors,
cap fresh rows, drop the null-`bornDay` fallback — and **confirm the mutation compiled** before
believing a "survived" result. Run the full suite, not a `--tests` filter: filtered Karma runs
execute zero tests and report success.

**Manual Foundry checklist**
1. Roll a curated rumor, accept it → it appears on the board as *fresh*. (Today it vanishes.)
2. Advance **one week in a single step** → it is *stale*, not *expired*, and no chat noise fired.
3. Advance two more weeks → one GM-whispered expiry offer, once.
4. *Let it fade* → no beat posts, and it never re-offers.
5. On another expired rumor, *Post the beat* → the beat posts publicly and reads as prose.
6. Pin a rumor, advance a month → still fresh, never offered.
7. Convert a rumor to a quest → it stops aging, the quest exists, and the rumor row shows converted.
8. Log in as a player → the board lists rumors with no veracity, no countdown, no GM controls.
9. Fill the board past the cap with expired rumors → oldest expired trimmed, live leads untouched.
10. `python3 scripts/check_i18n_keys.py --all` → clean. `./gradlew assemble jsBrowserTest check` → green.

---

## 10. Phasing

**Phase 1 — pure core.** `RumorLifecycle.kt`, the two enums, `tickRumors` / `beatCandidates` /
`capRumors` / `selectMutationBeat`, full `commonTest`. Nothing wired; no user-visible change.

**Phase 2 — persistence + the write that never existed.** `RawRumor` additions, the `Rumor` model
mirror, `rumorsOrDefault`, the §5.4 funnel, and the curator actually **persisting an accepted
rumor**. Daily-tick registration ages the store silently. Still no UI, no offers.

**Phase 3 — board + GM controls.** The camping-sheet section, its context and CSS with a measured
layout pass, pin/veracity/convert controls, i18n across all eight locales.

**Phase 4 — offers.** `rumor-expired-offer.hbs` and `rumor-convert-offer.hbs`, their camping-side
`ActionHandler`s, the mutation-table file with one worked category, and the hex-hook conversion.

Phases 1 and 2 ship no user-visible change, which is what makes 3 and 4 safe to land separately.

---

## 11. Open questions for Gregory

1. **Mutation beat prose** (§5.3) — campaign voice, deliberately not invented here. Ship with
   `monster` as a worked example and the rest empty?
2. **Thresholds** — 7 days to stale, 21 to expired. These are a guess at Kingmaker's travel cadence,
   not derived from anything; say the word and they change.
3. **Veracity authorship** — GM-set only (this plan), or should a *distorted* rumor be generatable,
   so the curator itself sometimes lies?
4. **Hex-hook conversion** — net-new (`RawHexContent` from a rumor). Worth building in phase 4, or
   split into its own card once quest conversion has proven the pattern?
5. **`rumorGeneratedQuestIds` is write-only** — nothing reads it. Repurpose it as this feature's
   dedup key, or file a separate cleanup card?

---

**End of plan.** Ready for review.

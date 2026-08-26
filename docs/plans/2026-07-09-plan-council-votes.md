# Council Votes — Implementation Plan

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Roadmap item:** New backlog (Council Votes / contested-call ledger)
> **Depends on:** Turn History gazette + Recent Turns recap (commit `654d3b98`), the
> socket `ActionDispatcher` (deny-by-default originator policy, commit `f073a0ba`), the
> GM-confirmed offer/ChatButton pattern (`ChatButtons.kt`).
> **Branch:** `kingmaker.5`

---

## Executive Summary

When the table hits a contested call — how to answer a kingdom event, which structure to
build next, war vs. diplomacy with Pitax — the decision happens verbally and then evaporates.
Nobody remembers three sessions later *who* argued to appease the druids, and the game world
has no memory that the choice was ever made, let alone what it caused.

**Council Votes** is a cheap accountability-and-memory tool. The GM opens a **vote card**:
a free-text question plus 2–6 options. Each player clicks their choice; the tally and the
individual per-user votes are recorded into the kingdom's turn stream. A closed vote is a
durable, player-visible record ("Turn 12: the council voted 3–1 to appease the druids").
Later, the GM can **manually link** a consequence to that vote ("→ Crop Failures ended Turn
14"), turning the vote history into a chronicle of decisions and their fallout.

This is deliberately an **advisory** tool. Nothing in the kingdom engine is mechanically
gated by a vote result — the GM and the party's leaders still decide what actually happens.
The feature only *records* what the table chose, and stitches consequences back to choices so
the campaign remembers.

The whole feature rests on one architectural question, audited below: **can a per-user click
on a chat-card button be attributed to the specific player who clicked, and persisted?** The
answer is yes, but not the naive way — see §5.

---

## Affected Files

### New Kotlin files

| File | Purpose |
|------|---------|
| `src/jsMain/kotlin/.../kingdom/data/RawCouncilVote.kt` | `@JsPlainObject` vote record (question, options, per-user ballots, lifecycle turns, link refs) |
| `src/commonMain/kotlin/.../kingdom/CouncilVoteTally.kt` | **Pure, portable** tally + tie math on primitive inputs (commonTest-covered) |
| `src/jsMain/kotlin/.../kingdom/CouncilVotes.kt` | Pure `RawCouncilVote` transforms (`castVote`, `closeVote`, `reopenVote`, `appendCouncilVote` + `COUNCIL_VOTE_CAP`, `linkConsequence`/`unlinkConsequence`, `tallyVote` adapter) — jsMain because it touches `@JsPlainObject` |
| `src/jsMain/kotlin/.../actions/handlers/CastCouncilVoteHandler.kt` | Socket handler that records a player-cast ballot GM-side (`originatorPolicy = ANY`) |
| `src/jsMain/kotlin/.../kingdom/dialogs/OpenCouncilVote.kt` | GM dialog: question + options → posts the vote card |
| `src/jsMain/kotlin/.../kingdom/dialogs/LinkVoteConsequence.kt` | GM picker: check/uncheck later turn records against a closed vote (attach **and** detach) |
| `src/jsMain/kotlin/.../kingdom/sheet/contexts/CouncilVotesContext.kt` | Thin UI context for the Votes list section |
| `src/jsMain/kotlin/.../migrations/migrations/Migration66.kt` | Initialize `councilVotes = []` on kingdoms lacking it |

### New Handlebars templates

| File | Purpose |
|------|---------|
| `src/jsMain/resources/chatmessages/council-vote-ballot.hbs` | The public interactive ballot card (option buttons + abstain). **No GM controls and no `{{#if isGM}}`** — chat content is rendered once and frozen, see §4.1 |
| `src/jsMain/resources/chatmessages/council-vote-gm-controls.hbs` | GM-**whispered** companion message: close / reopen buttons (`whisper = gmUserIds`) |
| `src/jsMain/resources/chatmessages/council-vote-result.hbs` | Posted on close: final tally + tie/GM-decides note |
| `src/jsMain/resources/applications/kingdom/sections/council-votes/page.hbs` | Votes list section (live tally, GM controls, consequence links) |

### Modified files

| File | Change |
|------|--------|
| `src/jsMain/kotlin/.../kingdom/data/RawTurnRecord.kt` | Add nullable `closedVoteIds: Array<String>?` back-reference |
| `src/jsMain/kotlin/.../kingdom/KingdomData.kt` | Add top-level `var councilVotes: Array<RawCouncilVote>?` (mirrors `quests`/`warThreats`/`turnHistory`) |
| `src/jsMain/kotlin/.../kingdom/ChatButtons.kt` | Add `km-council-vote-cast` / `-abstain` / `-close` / `-reopen` handlers |
| `src/jsMain/kotlin/.../Main.kt` | **Two** changes: register `CastCouncilVoteHandler` in the `ActionDispatcher` handler list, *and* register `"kingdom-council-votes" to "applications/kingdom/sections/council-votes/page.hbs"` in `loadTemplatePartials(...)` (`Main.kt:135-170`) — an unregistered partial throws "partial X could not be found" at render |
| `src/jsMain/kotlin/.../kingdom/TurnHistory.kt` | Stamp `closedVoteIds` when building the End-Turn record; optional gazette line |
| `src/jsMain/kotlin/.../kingdom/SessionPrepView.kt` | `TurnRecentEntry` gains `closedVotes`; `buildSessionPrepView` takes a `councilVotes` array; **both** `buildRecentTurns` and `buildRecentTurnsPlayer` populate it (§4.3) |
| `src/jsMain/kotlin/.../kingdom/sheet/contexts/SessionPrepContext.kt` | `toTurnContexts()` reshapes `closedVotes` into `TurnRecentEntryContext`; adds the `isHighlighted` flag for the "→ Turn N" jump |
| `src/jsMain/kotlin/.../kingdom/sheet/KingdomSheet.kt` | Register the Votes section + `_onClickAction` for open/close/link |
| `src/jsMain/kotlin/.../kingdom/sheet/navigation/MainNavEntry.kt` | Add the Votes nav entry (`.km-tabs`) |
| `src/jsMain/resources/applications/kingdom/kingdom-sheet.hbs` | Add `{{> kingdom-council-votes this}}` inside `<main class="km-kingdom-sheet-main">` (`:45-61`), where every other section partial is invoked |
| `src/jsMain/resources/applications/kingdom/sections/session-prep/page.hbs` | Render closed-vote lines inside a Recent Turns item; highlight class on the jumped-to row |
| `src/jsTest/kotlin/.../actions/ActionDispatcherSecurityTest.kt` | Add `CastCouncilVoteHandler` to both lists in `testEveryHandlerHasExpectedOriginatorPolicy` (coverage, not a build break — see Phase 2) |
| `lang/*.json` (all 8: `de`, `en`, `fr`, `it`, `pl`, `pt-BR`, `ru`, `zh-Hans`) | Nested `kingdom.councilVotes.*` catalog + `kingdomMainNav.councilVotes` (never flat-dotted; parity enforced in CI — §4.4) |

---

## 1. Problem Statement + Player/GM Value

**Problem.** Contested table decisions are made out loud and immediately forgotten. There is
no record of *who* chose what, and the world has no causal memory: the Crop Failure event two
turns later is never visibly tied back to the council's decision to appease the druids. The
module already records economy snapshots per turn (`RawTurnRecord`, rendered in the player-safe
Recent Turns recap), but decisions — the most narratively important thing a session produces —
are invisible.

**Value to the table.**

- **Accountability (players).** Each player's vote is named and recorded. "You voted to raid
  the caravan" is now a fact the campaign owns, not a disputed memory.
- **Memory / chronicle (GM + players).** A closed vote is a durable, player-visible entry in
  the turn history. The GM can link a later consequence to it, so the Recent Turns timeline
  reads as a chain of *decisions → outcomes*, not a spreadsheet of RP totals.
- **Zero prep, zero rules weight.** Opening a vote is one dialog. It gates nothing, changes no
  numbers, and adds no phase. It is pure record-keeping the table can ignore entirely and lose
  nothing mechanical.
- **Fuels existing surfaces.** Closed votes flow into Recent Turns, and — with a small amount of
  explicit work in the exporter, which does not share the sheet's context (§6) — into the
  session-prep journal export, so the recap and "previously on…" narrative get decision context.

---

## 2. Data Model

All new interop types follow the house pattern: `external @JsPlainObject` interfaces with an
auto-generated `.copy`, **nullable fields for migration safety**, and simple JSON-round-trippable
primitives only.

### 2.1 `RawCouncilVote` — `src/jsMain/kotlin/.../kingdom/data/RawCouncilVote.kt`

```kotlin
package at.posselt.pfrpg2e.kingdom.data

import js.objects.Record
import kotlinx.js.JsPlainObject

/**
 * A recorded contested-call vote. Advisory only — nothing is mechanically gated by the result.
 * The `votes` map is the per-user ballot: userId -> chosen option index (or ABSTAIN_OPTION = -1).
 * Lives on KingdomData.councilVotes (top-level), because votes are opened ad hoc mid-turn,
 * BEFORE that turn's RawTurnRecord snapshot exists (see §2.3).
 */
@JsPlainObject
external interface RawCouncilVote {
    /** Stable id (uuid v4) — referenced by RawTurnRecord.closedVoteIds and by the chat card. */
    var id: String

    /** Free-text question the GM typed, e.g. "How do we answer the druids?". */
    var question: String

    /** 2..6 option labels (free text). Index into this array is the ballot value. */
    var options: Array<String>

    /**
     * Per-user ballot: userId -> option index. -1 (ABSTAIN_OPTION) means an explicit abstain.
     * A user absent from the map simply has not voted yet. Overwriting the entry = changing
     * your vote while the poll is open. js.objects.Record<String, Int> so it JSON round-trips —
     * the same interop type RawActivity.kt:49 uses for `var skills: Record<String, Int>`.
     * Read it back with ReadonlyRecord<String, T>.toMap() (utils/Lang.kt:89).
     */
    var votes: Record<String, Int>

    /** Kingdom turn the vote was opened on. */
    var openedTurn: Int

    /** Kingdom turn the vote was closed on; null while still open. */
    var closedTurn: Int?

    /**
     * GM free-text note attached at close: the decision taken, tie-break rationale, etc.
     * This is where "GM broke the tie in favour of Appease" is recorded. Nullable.
     */
    var outcomeNote: String?

    /**
     * Manually GM-linked consequences: turn numbers of LATER RawTurnRecords the GM attached
     * as fallout ("this vote led to Turn 14's Crop Failures"). No causality is inferred — the
     * GM picks them. Nullable/absent for legacy + un-linked votes.
     */
    var linkedRecordRefs: Array<Int>?

    /**
     * Reserved for a future anonymous mode. Default/recommended is NAMED (null or false):
     * named voting is the entire point of the feature. Kept nullable so the shape is
     * forward-compatible without a second migration. Building the anonymous UI is OUT OF SCOPE.
     */
    var anonymous: Boolean?
}
```

`ABSTAIN_OPTION = -1` is a shared `const val` in `CouncilVoteTally.kt` (commonMain) so both the
tally math and the ballot handler agree on the sentinel.

### 2.2 `RawTurnRecord` change (nullable back-reference)

`RawTurnRecord` currently snapshots per-turn economy state (`turn`, `fame`, `unrest`, `notes`,
`playerNotes`, …). Add exactly one nullable field:

```kotlin
// RawTurnRecord.kt — ADDITION only, all existing fields unchanged
/** Ids of council votes that CLOSED during this turn. Nullable: legacy records have none,
 *  so the Recent Turns recap simply renders no votes. The authoritative vote objects live on
 *  KingdomData.councilVotes; this is a cheap back-reference for immutable per-turn rendering. */
var closedVoteIds: Array<String>?
```

This keeps `RawTurnRecord` a stable, primitive-only snapshot (no nested objects that would need
custom serializers) while giving the immutable turn timeline a stable pointer to the votes that
belong to it.

### 2.3 Persistence location + cap

**Authoritative store: `KingdomData.councilVotes: Array<RawCouncilVote>?`** — a new top-level
flag alongside the existing `quests`, `groups`, `warThreats`, `turnHistory`,
`companionExpeditions` arrays (`KingdomData.kt`: `quests` L225, `groups` L272, `warThreats` L334,
`turnHistory` L356, `companionExpeditions` L380 — re-verify before editing, that file moves).
Follow the *nullable* members of that set (`quests`, `warThreats`, `turnHistory`,
`companionExpeditions`); `groups: Array<RawGroup>` is the one non-nullable array among them and is
**not** the precedent for a newly-added field.

**Why top-level and not nested inside `RawTurnRecord`** (a deliberate, called-out deviation
from the literal "votes live in the turn stream" framing): `RawTurnRecord` entries are only
appended at **End Turn** (`appendTurnRecord`). A vote is opened *ad hoc mid-turn* — before that
turn's record exists — and accumulates ballots live. There is nowhere in the `turnHistory`
array for an in-progress vote to live. So the live objects sit on `KingdomData.councilVotes`,
and each record's `closedVoteIds` stamps the linkage at close/End-Turn. This is the same
relationship `warThreats` (live, top-level) already has with the turn stream. **Flag for
Gregory** in Open Questions — if he prefers a single nested store we can revisit, but the
cadence mismatch makes top-level the correct call.

**Cap.** Mirror `appendTurnRecord(history, record, cap = TURN_HISTORY_CAP)`
(`TurnHistory.kt:25-28`) with an `appendCouncilVote(votes, vote, cap = COUNCIL_VOTE_CAP)` that
drops the oldest once the cap is exceeded. Votes are heavier than turn records (per-user maps) and
rarer, so 50. It is a **named constant**, `const val COUNCIL_VOTE_CAP = 50`, declared beside
`appendCouncilVote` in `CouncilVotes.kt` (§3.2) exactly as `const val TURN_HISTORY_CAP = 100` sits
beside `appendTurnRecord` at `TurnHistory.kt:13` — whose own docstring gives the reason verbatim:
"it lives here rather than as a default-argument literal in each place".

### 2.4 Migration66

The chain currently ends at **`Migration65`** (`migrations/Migrations.kt`; `MigrationChainTest`
asserts contiguity). Propose **`Migration66`** — the next free number *(re-derive at
implementation time if the chain has advanced further; see caveat)*:

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
        if (kingdom.councilVotes == null) kingdom.councilVotes = arrayOf<RawCouncilVote>()
    }
}
```

Register it in the `migrations` list in `Migrations.kt`. Nullable fields technically don't
*require* a backfill, but seeding `[]` keeps reads uniform and follows the lesson from the
un-registered Migrations 41–48 (register every authored migration so the chain actually runs).

Two test files move with it. Extend `MigrationChainTest.registeredVersionsAreContiguous17To61`,
whose body is a hardcoded `assertEquals((17..65).toList(), migrations.map { it.version })`, to
`(17..66)`; and add `assertDefined("councilVotes", kingdom.councilVotes)` to that file's
`fullKingdomChainBackfillsEveryAdditiveField`, beside the existing `assertDefined("quests", …)` /
`assertDefined("hexContents", …)` lines. Then add `Migration66Test` mirroring
`Migration65Test` / `Migration30Test` — `Migration65Test` is the closest shape available (a single
nullable-array backfill plus an idempotency case: `seedsAnAbsentLedgerToEmpty` and
`aSecondRunNeverErasesAwardedHistory`). Note there is **no** `Migration48Test` in
`src/jsTest/kotlin/at/posselt/pfrpg2e/migrations/`; the numbered fixtures present are
19, 20, 26, 27, 30, 32, 37, 40, 52, 62, 63, 64, 65.

---

## 3. Engine Design

### 3.1 Pure, portable tally math — commonMain

**File:** `src/commonMain/kotlin/.../kingdom/CouncilVoteTally.kt`

The genuinely portable math — counting ballots, finding the winner, detecting a tie — operates
on **primitive inputs only** and therefore lives in `commonMain` with `commonTest` coverage.
`@JsPlainObject` interfaces are a JS-only interop feature and **cannot be referenced from
commonMain**, so the pure math must not take a `RawCouncilVote` directly.

```kotlin
package at.posselt.pfrpg2e.kingdom

const val ABSTAIN_OPTION = -1

/** Portable, immutable tally result. */
data class VoteTally(
    val optionCounts: List<Int>,   // votes per option index, size == optionCount
    val abstentions: Int,          // ballots explicitly cast as ABSTAIN_OPTION
    val totalBallots: Int,         // counted + abstentions
    val leadingOptions: List<Int>, // option indices sharing the max count; size > 1 == tie
    val isTie: Boolean,            // leadingOptions.size > 1 (excludes the all-zero case)
)

/**
 * Pure tally. [ballots] is userId -> option index; [optionCount] fixes the output width so
 * options with zero votes still appear. Abstains and out-of-range indices are excluded from
 * optionCounts but abstains are surfaced separately. Deterministic; no I/O, no randomness.
 */
fun tallyVotes(optionCount: Int, ballots: Map<String, Int>): VoteTally
```

Winner rule: highest `optionCounts`. If two or more options share the max (and the max > 0),
`isTie = true` and `leadingOptions` lists them — the engine **never** auto-breaks a tie (§ tie
handling below).

### 3.2 Pure `RawCouncilVote` transforms — jsMain

**File:** `src/jsMain/kotlin/.../kingdom/CouncilVotes.kt`

These are still **pure** (return new `.copy`ed objects, no Foundry I/O, unit-testable) but must
live in `jsMain` because they touch the `@JsPlainObject` type. Pure ≠ common — the axis is *which
source set can see the interop type*, and the repo already splits on exactly that axis:
`fun applyStandingDelta(current: Int?, delta: Int): Int`
(`src/commonMain/kotlin/at/posselt/pfrpg2e/data/kingdom/FactionRelations.kt:44`) takes only
primitives, which is why it is **common** — `tallyVotes` follows it; `appendTurnRecord`
(`TurnHistory.kt:25`) takes an `Array<RawTurnRecord>`, which is why it is **not** —
`appendCouncilVote` follows that one.

```kotlin
/**
 * How many council votes are retained. Lives here beside [appendCouncilVote] rather than as a
 * default-argument literal, exactly as TURN_HISTORY_CAP lives beside appendTurnRecord
 * (TurnHistory.kt:13). ABSTAIN_OPTION stays in commonMain with the tally math that reads it.
 */
const val COUNCIL_VOTE_CAP = 50

/**
 * Adapter: unpack a RawCouncilVote into the commonMain math.
 * `.toMap()` is ReadonlyRecord<String, T>.toMap() from utils/Lang.kt:89.
 */
fun tallyVote(vote: RawCouncilVote): VoteTally =
    tallyVotes(vote.options.size, vote.votes.toMap())

/** Record/overwrite one user's ballot. Pure: returns a new vote; ignores if already closed. */
fun castVote(vote: RawCouncilVote, userId: String, optionIdx: Int): RawCouncilVote

/** Freeze the vote at [turn]; caller supplies the GM outcome note. Idempotent on closed votes. */
fun closeVote(vote: RawCouncilVote, turn: Int, outcomeNote: String?): RawCouncilVote

/** GM reopen: clear closedTurn so ballots can change again. */
fun reopenVote(vote: RawCouncilVote): RawCouncilVote

/** Append with cap, dropping the oldest (mirrors appendTurnRecord). */
fun appendCouncilVote(
    votes: Array<RawCouncilVote>?,
    vote: RawCouncilVote,
    cap: Int = COUNCIL_VOTE_CAP,
): Array<RawCouncilVote>

/** GM manual link — append a turn number to linkedRecordRefs (dedup). No inference. */
fun linkConsequence(vote: RawCouncilVote, turn: Int): RawCouncilVote

/** GM manual unlink — remove a turn number from linkedRecordRefs; no-op when absent. Links are
 *  removable: the picker in §4.2 is checkboxes, and unchecking a linked turn calls this. */
fun unlinkConsequence(vote: RawCouncilVote, turn: Int): RawCouncilVote
```

`castVote` is a total function: an index outside `0..options.size-1` (other than
`ABSTAIN_OPTION`) is rejected/clamped so a malformed socket payload can't corrupt the map.

### 3.3 Tick surface — **NONE** (justified)

Council votes hook **neither** tick. The repo splits ticking into `TurnTickingEngine` (monthly,
End Turn) and `DailyTickHooks` (daily world clock); adding a third cadence is explicitly out of
bounds. Votes are **GM-driven, ad hoc**: opened by an `OpenCouncilVote` dialog, cast by button
clicks, and closed by a GM button — none of that is time-driven. A vote left open across an End
Turn is fine; it simply stays open.

The **only** touch-point with `TurnTickingEngine`/`TurnHistory` is opportunistic rendering: when
`buildTurnRecord` runs at End Turn, it stamps `closedVoteIds` = the ids of votes whose
`closedTurn == currentTurn`, so the immutable Recent Turns entry can render them. That is a
snapshot convenience, not a tick behaviour — the vote lifecycle never depends on a tick firing.

---

## 4. UI Design

### 4.1 The vote card (chat)

Opening a vote posts **two** messages. `council-vote-ballot.hbs` goes out public and
un-whispered — everyone votes — carrying only the question, one button per option, and an
**Abstain** button. `council-vote-gm-controls.hbs` is a second message whispered to the GM ids
(`whisper = gmUserIds`, the pattern `quest-deadline-offer.hbs` already uses at
`TurnWizardApplication.kt:1505-1509`) carrying `km-council-vote-close` / `-reopen`.

**Why the split — chat content is static per message, never per viewer.** `postChatTemplate`
(`utils/Chat.kt:52-61`) renders the template **once**, on the posting client
(`val message = tpl(templatePath, templateContext)`), and `postChatMessage` (`:63-87`) stores the
resulting *string* as the `ChatMessage` `"content"`. Every viewer is served that same frozen
string. The only per-client render hook the module installs is
`TypedHooks.onRenderChatMessage { fixVisibility(game, html, message) }` (`Main.kt:308-310`), and
`fixVisibility` (`Chat.kt:89-96`) does one thing: hide a *blind* message containing
`.km-hide-from-user`. So `{{#if isGM}}` in a **public** card is not a gate — the GM composes it,
`isGM` bakes in as `true`, and the Close button renders in every player's chat log. The repo
already ships one instance of this bug: `companion-autonomy-offer.hbs` is posted with
`"isGM" to true` and no `whisper` argument (`resting/Resting.kt:575-592`). Whispering is what
makes the GM card GM-visible-only; the `if (!game.user.isGM) return@ChatButton` guard in the
handler is what makes it authoritative (§5.1(b)).

```hbs
<div class="km-council-vote" data-vote-id="{{voteId}}" data-kingdom-actor-uuid="{{actorUuid}}">
  <h3>{{localizeKM "kingdom.councilVotes.ballotTitle"}}</h3>
  <p class="km-council-vote__question">{{question}}</p>
  <div class="km-council-vote__options">
    {{#each options}}
      <button type="button" class="km-council-vote-cast"
              data-vote-id="{{../voteId}}" data-option-idx="{{@index}}"
              data-kingdom-actor-uuid="{{../actorUuid}}">{{this}}</button>
    {{/each}}
    <button type="button" class="km-council-vote-abstain"
            data-vote-id="{{voteId}}" data-kingdom-actor-uuid="{{actorUuid}}">
      {{localizeKM "kingdom.councilVotes.abstain"}}
    </button>
  </div>
</div>
```

No `{{#if isGM}}` anywhere in this template — deliberately. The GM control card is a separate,
whispered message:

```hbs
<div class="km-council-vote-gm" data-vote-id="{{voteId}}" data-kingdom-actor-uuid="{{actorUuid}}">
  <p class="km-council-vote__question">{{question}}</p>
  <button type="button" class="km-council-vote-close" data-vote-id="{{voteId}}"
          data-kingdom-actor-uuid="{{actorUuid}}">{{localizeKM "kingdom.councilVotes.close"}}</button>
  <button type="button" class="km-council-vote-reopen" data-vote-id="{{voteId}}"
          data-kingdom-actor-uuid="{{actorUuid}}">{{localizeKM "kingdom.councilVotes.reopen"}}</button>
</div>
```

**Live tally lives on the sheet, not the card.** Foundry chat cards are static once posted, so
we do **not** try to live-mutate the ballot card as votes arrive. The authoritative, live tally
renders in the Kingdom Sheet **Votes** section (§4.2), which re-renders on every kingdom-flag
update — all clients observe the actor flag, so everyone sees the count climb. On close, the GM
handler posts a fresh `council-vote-result.hbs` card with the frozen final tally.

**UX decisions:**
- **Changing your vote while open:** click a different option; `castVote` overwrites your map
  entry. Trivially supported.
- **Abstentions:** clicking **Abstain** records `ABSTAIN_OPTION` (an *explicit* abstain, shown
  as "N abstained"). *Not clicking anything* is an implicit non-vote, shown as "N has not
  voted". The distinction is deliberate — "chose to abstain" ≠ "was away from keyboard".
- **Anonymous mode:** **RECOMMENDED NAMED.** Named voting is the whole point (accountability +
  memory). The `anonymous` flag exists on the shape for forward-compat but the anonymous
  rendering path is out of scope for v1.

### 4.2 The Votes list section (sheet)

A new `.km-tabs` nav entry (`MainNavEntry.COUNCIL_VOTES`, label at `kingdomMainNav.councilVotes`)
+ `sections/council-votes/page.hbs`, matching the Session Prep section pattern. Wiring a section
takes **three** edits, not one — the partial does not find itself:

1. `Main.kt`'s `loadTemplatePartials(...)` block (`:135-170`) gains
   `"kingdom-council-votes" to "applications/kingdom/sections/council-votes/page.hbs"`, beside
   `"kingdom-session-prep"` and `"kingdom-expeditions"`. Skip this and the render throws
   "partial X could not be found".
2. `kingdom-sheet.hbs` gains `{{> kingdom-council-votes this}}` inside
   `<main class="km-kingdom-sheet-main">` (`:45-61`), where all fifteen existing sections are
   invoked explicitly.
3. The template itself must have a **single root element**, and — being a registered partial — it
   must reach the outer context with `@root.isGM`, never `../isGM`, inside any `{{#each}}`.
   `../x` at partial top level resolves to nothing, silently; that is how two GM-only blocks
   shipped dead. `scripts/check_hbs_scope.py` guards it and runs in CI
   (`.github/workflows/test.yml:35`).

One row per vote:

- Question, open/closed badge, opened/closed turn.
- **Live tally bars** per option (from `tallyVote`), highlighting `leadingOptions`.
- **Named ballots** (named mode): each voter's name + their choice, resolved via
  `game.users.get(userId)?.name`.
- **GM controls** (`isGM`): Close / Reopen, **Link consequence** (opens `LinkVoteConsequence`),
  edit `outcomeNote`, delete vote. The `isGM` here is **layout, not authorization** — players are
  OWNERs of the party actor (§5.1(b)), so every one of these `_onClickAction` branches opens with
  its own `if (!game.user.isGM) return@buildPromise`, as `"pay-structure"` and `"claim-hex"` already
  do (`KingdomSheet.kt:2430`, `:2527`). The guard may instead live one level down in the dialog the
  branch opens — `"import-turn-history"` takes that route, and its comment states the reason
  verbatim: "players are OWNERs of the party actor, so a template gate is not authorization"
  (`KingdomSheet.kt:644-645`, guard at `dialogs/ImportTurnHistory.kt`). Either shape is fine; what
  is not fine is no guard on the path, because an ungated branch is reachable regardless of what
  the sheet renders.
- **Consequence links:** for each `linkedRecordRefs` turn, a chip "→ Turn 14", rendered as
  `<button data-action="change-nav" data-link="sessionPrep" data-turn="14">`. The existing
  `change-nav` branch (`KingdomSheet.kt:636-641`) only sets `currentNavEntry` from
  `target.dataset["link"]` and re-renders — it switches tabs and cannot target a row — so it gains
  exactly one line: store `target.dataset["turn"]?.toIntOrNull()` into a `highlightTurn: Int?`
  sheet field before `render()`. `buildSessionPrepContext` then sets
  `isHighlighted = entry.turn == highlightTurn` on the matching `TurnRecentEntryContext`, and
  `session-prep/page.hbs` puts a `km-session-prep-turn-item--highlight` class on that `<li>`. The
  field resets to `null` on any `change-nav` that carries no `data-turn`, so the highlight does not
  stick across navigations.

**The link picker (`LinkVoteConsequence`).** It lists the `kingdom.turnHistory` entries with
`turn > vote.closedTurn` — a consequence is always *later* than the decision it followed — one
**checkbox** per turn, labelled `"Turn {n} — {playerNotes ?: notes ?: timestamp}"` (this is the
label QA step 7 assumes). Checkboxes rather than a one-shot pick because **links are removable**:
`unlinkConsequence(vote, turn)` sits beside `linkConsequence` in `CouncilVotes.kt` (§3.2), and
unchecking an already-linked turn calls it. Links are **whole-turn granularity** because
`RawTurnRecord` carries no per-line ids — `formatTurnGazette` (`TurnHistory.kt:80-92`) returns one
joined `String?` that is stored on the record, so the turn number *is* the gazette's granularity.
A `linkedRecordRefs` entry with no surviving record (only reachable after more than
`TURN_HISTORY_CAP = 100` turns of eviction) is skipped by the renderer rather than drawn as a dead
chip.

Context objects (thin `@JsPlainObject`, built in `CouncilVotesContext.kt`):

```kotlin
@JsPlainObject
external interface CouncilVotesContext {
    val isGM: Boolean
    val votes: Array<CouncilVoteRowContext>
    val hasAny: Boolean
}

@JsPlainObject
external interface CouncilVoteRowContext {
    val id: String
    val question: String
    val isOpen: Boolean
    val openedTurn: Int
    val closedTurn: Int?
    val options: Array<VoteOptionRowContext>   // label, count, pct, isLeading
    val ballots: Array<VoteBallotRowContext>   // voterName, optionLabel, abstained (named mode)
    val abstentions: Int
    val notVotedCount: Int
    val isTie: Boolean
    /**
     * GM free-text note. When gated (Open Question 5) this is NULL for players — absence removes
     * it from the data, per the nullability-is-the-gate rule (ForecastContext.kt:10-17,
     * SessionPrepContext.kt:63). The gate is `vote.outcomeNote.takeIf { isGM }` in the builder,
     * never a `{{#if isGM}}` in the template.
     */
    val outcomeNote: String?
    val linkedTurns: Array<Int>
}
```

### 4.3 Recent Turns rendering

The seam is the **view**, not the context. `TurnRecentEntry` (`SessionPrepView.kt:44-61`, a pure
`data class`) gains a `closedVotes: List<ClosedVoteLine>` — where
`data class ClosedVoteLine(val question: String, val winnerLabel: String?, val winnerCount: Int,
val totalBallots: Int, val isTie: Boolean, val linkedTurns: List<Int>)`. It is populated in
**both** `buildRecentTurns` (`:236`) and `buildRecentTurnsPlayer` (`:262`) — closed votes are
player-visible, so the player-safe slice carries them too — by resolving each record's
`closedVoteIds` against the `councilVotes` array that `buildSessionPrepView` (`:210-234`) must now
accept as a new trailing parameter `councilVotes: Array<RawCouncilVote>? = null`, defaulted so
existing call sites keep compiling. `SessionPrepContext.toTurnContexts()`
(`SessionPrepContext.kt:79-103`) then only reshapes the new field into `TurnRecentEntryContext`,
exactly as it reshapes the other twenty — adding the two template-shaped derivations the block
below reads (`hasClosedVotes = closedVotes.isNotEmpty()`, and per line
`hasLinks` / `linkedTurnsCsv = linkedTurns.joinToString(", ")`), because Handlebars cannot compute
them. In `session-prep/page.hbs`, inside the existing Recent Turns `<li>`, add a player-visible
block:

```hbs
{{#if this.hasClosedVotes}}
  <span class="km-session-prep-turn-item__votes">
    {{#each this.closedVotes}}
      <span class="km-session-prep-vote">
        {{localizeKM "kingdom.councilVotes.recapLine"
          question=this.question winner=this.winnerLabel count=this.winnerCount total=this.totalBallots}}
        {{#if this.hasLinks}}<em>{{localizeKM "kingdom.councilVotes.recapLinked" turns=this.linkedTurnsCsv}}</em>{{/if}}
      </span>
    {{/each}}
  </span>
{{/if}}
```

Closed votes and their consequence links are **player-visible by design** — the whole value is
shared memory. The free-text `outcomeNote` is the one field that may carry GM spoilers. *Whether*
it is gated is Open Question 5; *where* the gate goes is not open — `CouncilVotesContext.kt`
passes `outcomeNote = vote.outcomeNote.takeIf { isGM }`, so a player's context never carries the
string at all. Never a `{{#if isGM}}` in the template: nullability is the gate
(`ForecastContext.kt:10-17`, `SessionPrepContext.kt:63` — "absence removes the panel"), because
players are OWNERs and a template conditional is layout, not authorization. The tie/winner recap
line stays public either way.

### 4.4 i18n namespace

All strings nest under `kingdom.councilVotes.*` (nested objects, **never flat-dotted** — flat
keys render raw; guarded by `scripts/check_i18n_keys.py`). The catalog is already wired:
`en.json` is imported by `Localization.kt` (`englishTranslations`) and loaded in
`initLocalization()`; templates read it via the `localizeKM` Handlebars helper.

**Every new key must be added to all eight catalogs** — `lang/de.json`, `en.json`, `fr.json`,
`it.json`, `pl.json`, `pt-BR.json`, `ru.json`, `zh-Hans.json` — with **matching placeholder
names** in the values. `.github/workflows/test.yml:26-27` runs
`python3 scripts/check_i18n_keys.py --all`, whose check 5 requires that "all `lang/*.json` files
must have exactly the same set of nested keys as `en.json`" and enforces placeholder parity for
shared keys. An `en.json`-only addition fails CI. (Translate where possible; an English string
copied into the other seven satisfies parity and can be improved later, but the *key* must exist
in all eight.)

Keys under `kingdom.councilVotes.*`: `ballotTitle`, `abstain`, `close`, `reopen`, `open`,
`dialogQuestion`, `dialogOption`, `recapLine`, `recapLinked`, `tieNote`, `resultTitle`,
`emptyState`, `linkPickerTitle`, `linkPickerRow`, plus the voter-status strings `hasNotVoted`
and `abstained`.

**The nav label is NOT one of them.** `MainNavEntry` derives
`override val i18nKey get() = "kingdomMainNav.$value"`, so the Votes tab label lives at
`kingdomMainNav.councilVotes`, alongside the existing `turn`, `kingdom`, `settlements`,
`tradeAgreements`, `modifiers`, `quests`, `notes`, `roster`, `party`, `sessionPrep`, `campaign`,
`armyPressure`, `pacing`, `analytics`, `expeditions` — added to all eight catalogs. A `navLabel`
under `kingdom.councilVotes.*` would never be read.

---

## 5. Chat / Offer Surfaces — and the per-user click-attribution audit

### 5.1 AUDIT: how a ChatButton attributes a click to a user (the linchpin)

Read of `ChatButtons.kt`, `ActionDispatcher.kt`, `ActionHandler.kt`, `User.kt`. Findings:

**(a) The handler runs in the *clicking* user's own browser, so the clicker's identity is
directly available.** `bindChatButtons(game)` registers `TypedHooks.onRenderChatLog { ...
buttons.forEach { bindChatClick(".${buttonClass}") { ev, target, parent -> ... } } }`. This runs
on **every** connected client. When a specific user clicks a `.km-…` button, the `bindChatClick`
callback fires **locally in that user's browser**. Inside the callback, `game.user` is *that
client's* logged-in user. The `User` external class exposes `var _id: String`, `val name:
String`, `val isGM: Boolean` (`User.kt`). So the clicker is `game.user._id`. This is confirmed
by two independent uses in the codebase:
- `ActionDispatcher.kt:38` reads `game.user._id` as the local sender identity.
- The many `if (!game.user.isGM) return@ChatButton` guards throughout `ChatButtons.kt` — those
  work precisely because `game.user` is per-client.

**So per-user attribution of a click is available: `game.user._id` inside the ChatButton
callback is the user who clicked.** That is the linchpin, and it holds.

**(b) BUT a player click must not persist the vote directly — and NOT for the reason you would
guess.** Players **are** OWNERs of the party actor. `openOrCreateKingdomSheet` runs
`actor.update(recordOf("ownership" to actor.ownershipOwnersOnly()))` at kingdom creation
(`KingdomSheet.kt:4174`), and `PF2EParty.ownershipOwnersOnly()`
(`src/jsMain/kotlin/at/posselt/pfrpg2e/actor/PF2EParty.kt:9-16`) collects every ownership entry
whose level is `3` (OWNER) from the player-owned party members. So a player-side
`actor.setKingdom(kingdom)` **succeeds** — Foundry's permission layer does not stop it. The
codebase states this in prose in several places: `ChatButtons.kt:551` ("players are OWNERs of the
party actor: the isGM check IS the authorization"), `ForecastContext.kt:16` ("Players are OWNERs
of the party actor, so a template conditional is layout, never authorization"), and
`KingdomSheet.kt:644-645` ("players are OWNERs of the party actor, so a template gate is not
authorization").

The real hazard is **concurrency**, not permission. `setKingdom` is a whole-flag
read-modify-write: the client reads the entire kingdom object, mutates one field, and writes the
whole thing back. If three players click their option inside the same second, all three read the
same pre-cast kingdom and the last write wins — two ballots vanish silently. There is no
per-field merge and no optimistic-concurrency check to catch it.

So the socket dispatcher is chosen for **write serialisation**: every ballot funnels to the single
first-GM client, which applies casts one at a time against freshly-read state. The second reason
is provenance — the dispatcher stamps the ballot key itself (§5.1(c)), so the key is the socket
sender rather than a userId the clicking client typed into the payload. The consequence for the
GM-only buttons follows directly: because a player *can* write, `if (!game.user.isGM)
return@ChatButton` inside `km-council-vote-close` / `-reopen` is the **only** authorization those
buttons have. Mandatory, not decorative.

**(c) The module already solves player-originated writes with the socket `ActionDispatcher`,
and this is the correct channel for casting a vote.** `ActionDispatcher.dispatch()` stamps
`senderId = game.user._id`, and for a handler whose `mode = GM_ONLY` when the caller is not the
first GM, it `emitPfrpg2eKingdomCampingWeather(action)` over `game.socket`. The **first-GM**
client receives it, re-dispatches with `receivedViaSocket = true`, enforces the handler's
`originatorPolicy` (**deny-by-default `GM_ONLY`**; a handler must explicitly opt into
`OriginatorPolicy.ANY` to accept player origination), and only then runs `handler.execute()`
**GM-side** — where `setKingdom` is permitted. Crucially, **`senderId` survives the socket hop**
(`ActionMessage.senderId: String?`), so the GM-side handler knows *which player* cast the
ballot. Working precedent: `SyncActivitiesHandler(originatorPolicy = OriginatorPolicy.ANY)`.

**(d) Residual trust caveat (state it, accept it).** `ActionDispatcher.kt:40-43` documents that
`senderId` is client-supplied and therefore **forgeable** — a malicious player could set it to
another user's id. For an **advisory accountability tool** this is acceptable and worth one line
in the card copy; the GM sees the tally and can correct any obvious tampering. We record
`action.senderId` (the socket-authenticated sender) as the ballot key, **not** a userId baked
into the action payload by the clicking client, to keep it as honest as the platform allows.

### 5.2 The four vote buttons

| Button class | Who | Path | Effect |
|--------------|-----|------|--------|
| `km-council-vote-cast` | ANY user | Player → `ActionDispatcher.dispatch(CastCouncilVote{voteId, optionIdx})` → socket → GM `CastCouncilVoteHandler`. GM → dispatch runs locally GM-side. | Records `votes[senderId] = optionIdx` via `castVote`; `setKingdom`. |
| `km-council-vote-abstain` | ANY user | Same socket path, `optionIdx = ABSTAIN_OPTION`. | Records explicit abstain. |
| `km-council-vote-close` | GM only (`if (!game.user.isGM) return@ChatButton`) | Direct GM-side, from the **whispered** GM-controls card (§4.1) or the Votes section (§4.2) — never from the public ballot card. | `closeVote(turn, outcomeNote)`; stamp any current turn record's `closedVoteIds`; post `council-vote-result.hbs`. |
| `km-council-vote-reopen` | GM only | Same two surfaces, direct GM-side. | `reopenVote`; ballots editable again. |

`CastCouncilVoteHandler(action = "castCouncilVote", mode = ExecutionMode.GM_ONLY,
originatorPolicy = OriginatorPolicy.ANY)` — registered in the `Main.kt` dispatcher handler list
(`:97-108`, which today holds exactly ten handlers; this is the eleventh). Opting into `ANY` also
means adding it to `ActionDispatcherSecurityTest.testEveryHandlerHasExpectedOriginatorPolicy`,
whose two hand-written lists are the reviewed classification of who may originate what — see
Phase 2.
It resolves the actor from `data.actorUuid`, finds the vote by id in `kingdom.councilVotes`,
ignores casts on a closed vote, applies `castVote(vote, action.senderId!!, optionIdx)`, and
`setKingdom`. Its `execute` is the **serialisation point** for player-origin casts — the single
writer that keeps simultaneous ballots from clobbering one another — not a security boundary; a
determined player owns the actor and could write the flag directly (§5.1(b)).

### 5.3 Votes RECORD — they are not auto-apply offers

The GM-confirmed offer pattern (`km-offer-*`) exists so a threshold crossing can *propose* a
mechanical change the GM accepts (spawn war threat, create quest, level a companion). **Council
votes are categorically different: closing a vote applies nothing.** There is no "apply the
winning option" button, because the winning option has no mechanical meaning to the engine — it
is a note. The vote buttons *record ballots*; the close button *freezes and publishes a tally*.
Nothing downstream reads the result to gate behaviour (see §6 out-of-scope).

---

## 6. Interactions With Existing Systems + Out-of-Scope

| System | File(s) | Interaction |
|--------|---------|-------------|
| **Turn History / Recent Turns** | `TurnHistory.kt`, `RawTurnRecord.kt`, `SessionPrepContext.kt`, `session-prep/page.hbs` | `buildTurnRecord` stamps `closedVoteIds`; Recent Turns renders closed votes + links (player-visible). Primary integration surface. |
| **Socket dispatch** | `ActionDispatcher.kt`, `Main.kt`, new `CastCouncilVoteHandler` | Player casts route through the deny-by-default dispatcher; handler opts into `ANY`. |
| **Chat buttons** | `ChatButtons.kt` | 4 new button handlers; GM-only ones reuse the `if (!game.user.isGM) return@ChatButton` guard. |
| **Kingdom sheet** | `KingdomSheet.kt`, `MainNavEntry.kt` | New Votes nav entry + section; `_onClickAction` opens `OpenCouncilVote` / `LinkVoteConsequence`. |
| **Session-prep journal export** | `sheet/SessionPrepJournalExporter.kt` | **Requires explicit work — not free, and not the same context.** The exporter's entry point is `suspend fun export(game: Game, view: SessionPrepView): String` (`:20`); it imports `SessionPrepView` only and hand-builds HTML with a `StringBuilder` in `private fun buildSessionPrepHtml(view: SessionPrepView)` (`:79`) — it never sees `SessionPrepContext` or the Handlebars context. Add the closed-vote line to `TurnRecentEntry` (§4.3), then emit it inside the exporter's Recent Turns loop. **That loop is GM-gated** (`if (view.isGM && view.recentTurns.isNotEmpty())`, `:170`), so the exported vote lines inherit GM-only visibility. Keep it that way deliberately: the exported journal is the GM's prep document, while the *sheet's* Recent Turns stays player-visible. |
| **Event response / next structure / war-vs-diplomacy** | event-response ChatButtons (`km-resolve-event`, `km-add-ongoing-event`), quest/structure flows | These are the natural *subjects* a GM opens a vote about, but v1 is **freeform** — the GM types the question/options by hand. **No deep integration**: no auto-generated ballots from an event, no wiring a vote result back into event resolution. |

### 6.1 Explicit OUT-OF-SCOPE

- **No vote-gated mechanics.** Nothing in the kingdom engine reads a vote result. A vote never
  blocks, unlocks, or auto-applies an event response, a structure build, a war declaration, or
  any activity. Advisory only — the GM/leaders decide.
- **No causality inference.** Consequence links are 100% manual GM picks. The tool never guesses
  that a vote caused a later event.
- **No auto-generated ballots** from events/quests/structures (freeform question + options v1).
- **No anonymous-mode UI** (shape supports it; rendering path deferred — named is recommended).
- **No cross-kingdom / cross-actor votes.** Each kingdom actor owns its own `councilVotes`.
- **No third tick.** Votes are GM-driven; neither `TurnTickingEngine` nor `DailyTickHooks` owns
  their lifecycle.

---

## 7. Test Plan

### 7.1 commonTest — portable tally/tie math

**File:** `src/commonTest/kotlin/.../kingdom/CouncilVoteTallyTest.kt`

| Test | Assertion |
|------|-----------|
| `tallyVotes_countsPerOption` | `{a→0, b→0, c→1}` over 2 options → `optionCounts == [2,1]`, `totalBallots == 3`. |
| `tallyVotes_zeroVoteOptionsStillAppear` | width == `optionCount`; unused option shows `0`. |
| `tallyVotes_detectsTie` | `[2,2,1]` → `isTie == true`, `leadingOptions == [0,1]`. |
| `tallyVotes_noTieWithClearWinner` | `[3,1]` → `isTie == false`, `leadingOptions == [0]`. |
| `tallyVotes_allZeroIsNotATie` | empty ballots → `isTie == false`, `leadingOptions == []`. |
| `tallyVotes_abstainsExcludedFromCountsButSurfaced` | `ABSTAIN_OPTION` ballots → `abstentions` counted, not in `optionCounts`. |
| `tallyVotes_outOfRangeIndexIgnored` | index `> options` is not counted (defensive). |

### 7.2 jsTest — Raw transforms, cap, and per-user attribution fixture

**File:** `src/jsTest/kotlin/.../kingdom/CouncilVotesTest.kt`

| Test | Assertion |
|------|-----------|
| `castVote_recordsBallot` | `castVote(vote,"u1",1).votes["u1"] == 1`; original unchanged (immutability). |
| `castVote_overwriteChangesVote` | casting `u1→0` then `u1→2` leaves a single entry `== 2`. |
| `castVote_ignoredWhenClosed` | closed vote is unchanged by `castVote`. |
| `closeVote_freezesAndStampsTurn` | `closedTurn` set; `outcomeNote` carried. |
| `reopenVote_clearsClosedTurn` | `closedTurn == null` after reopen. |
| `appendCouncilVote_capDropsOldest` | `COUNCIL_VOTE_CAP + 1` appended → size `COUNCIL_VOTE_CAP`, oldest gone (mirrors the `appendTurnRecord` test). |
| `tallyVote_adapterMatchesCommonMath` | `tallyVote(raw)` equals `tallyVotes(options, map)`. |
| `linkConsequence_dedupesTurns` | linking Turn 14 twice yields one ref. |
| `unlinkConsequence_removesTurn` | linking 14 then unlinking 14 leaves `linkedRecordRefs` empty; unlinking an absent turn is a no-op. |

**Per-user click-attribution fixture (the linchpin, jsTest):**
`src/jsTest/kotlin/.../actions/CastCouncilVoteHandlerTest.kt`

| Test | Assertion |
|------|-----------|
| `handler_recordsBallotUnderSenderId` | dispatch an `ActionMessage(action="castCouncilVote", senderId="player-2", data={voteId, optionIdx:1})` with `receivedViaSocket=true`; assert the GM-side handler writes `votes["player-2"] == 1`. Proves attribution survives the socket hop. |
| `handler_deniesWhenNotOptedIn` | a control handler left at default `originatorPolicy=GM_ONLY` rejects a non-GM sender (guards the deny-by-default invariant so a future edit can't silently make casting GM-forgeable). |
| `handler_ignoresClosedVote` | cast on a closed vote is a no-op GM-side. |

**Originator-policy classification (`ActionDispatcherSecurityTest.kt`).** Add
`CastCouncilVoteHandler().action to OriginatorPolicy.ANY` to **both** the `expected` and the
`actual` lists in `testEveryHandlerHasExpectedOriginatorPolicy`, under a comment justifying why a
player-originable kingdom write is acceptable here: a ballot records only text and an index,
gates nothing mechanically (§6.1), and is capped and GM-closable. This is a **coverage
deliverable, not a build-breaker** — both lists are hand-maintained literals and nothing reflects
over `Main.kt`'s registered handlers, so omitting the entry leaves `expected == actual` and the
suite stays green while the eleventh handler goes unreviewed. That is exactly the failure mode the
list exists to prevent, so do it deliberately.

(jsTest runs via Chrome headless: `useChromeHeadless` + `CHROME_BIN`, `-x kotlinStoreYarnLock`,
throwaway `karma.config.d` override — Firefox headless times out in WSL.)

### 7.3 Manual Foundry verification checklist

1. GM opens Kingdom Sheet → **Votes** tab → **Open vote**, enters "Appease or fight the
   druids?" with options *Appease* / *Fight*. A ballot card posts to chat.
2. As **Player A** (separate client; note the player IS an OWNER of the party actor — see §5.1(b)), click *Appease* → the Votes
   tab tally updates to `Appease: 1` on **all** clients (socket → GM write → flag re-render).
3. As **Player B**, click *Fight*; as **Player A**, change to *Fight* → tally `Fight: 2`, Player
   A's named row now reads *Fight* (change-your-vote works).
4. As **Player C**, click **Abstain** → shows "C abstained"; a non-voting player shows "has not
   voted".
5. GM clicks **Close** → result card posts with final tally; a **tie** shows the "GM decides"
   note field; GM fills `outcomeNote`.
6. Run End Turn → the closed vote appears in **Recent Turns** (player-visible), tied to the turn.
7. Two turns later, GM opens the closed vote → **Link consequence** → the picker lists only turns
   *after* the close turn, labelled "Turn N — <recap>"; tick one → the vote row shows a "→ Turn N"
   chip, and clicking that chip lands on Session Prep with the matching Recent Turns row
   highlighted. Re-open the picker, untick it → the chip is gone (`unlinkConsequence`).
8. Reload world → votes, ballots, close state, and links persist; legacy kingdoms (no
   `councilVotes`) load clean with an empty Votes tab (Migration66).
9. Confirm **nothing mechanical changed** from any vote result (advisory-only invariant).
10. All UI text resolves via i18n (no raw `kingdom.councilVotes.*` or `kingdomMainNav.councilVotes`
    keys leaking), and `python3 scripts/check_i18n_keys.py --all` passes — i.e. the keys exist in
    all eight catalogs with matching placeholders, not just `en.json`.
11. As a **player**, confirm the public ballot card shows no Close/Reopen button (the GM controls
    are a separate whispered message), and that the Votes section's GM controls are absent.

---

## 8. Phasing (independently committable)

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Data + tally math + migration** | `RawCouncilVote`, `KingdomData.councilVotes`, `RawTurnRecord.closedVoteIds`, `Migration66` (+`Migration66Test`, + the `MigrationChainTest` range/`assertDefined` updates), commonMain `CouncilVoteTally` + `CouncilVotesTest`/`CouncilVoteTallyTest`, jsMain `CouncilVotes.kt` transforms + `COUNCIL_VOTE_CAP`. | `RawCouncilVote.kt`, `KingdomData.kt`, `RawTurnRecord.kt`, `CouncilVoteTally.kt`, `CouncilVotes.kt`, `Migration66.kt`, `MigrationChainTest.kt`, tests |
| **2** | **Socket cast handler + ballot card** | `CastCouncilVoteHandler` (`ANY`), register in `Main.kt`, `km-council-vote-cast`/`-abstain`/`-close`/`-reopen` ChatButtons, public ballot + whispered GM-controls + result templates, `CastCouncilVoteHandlerTest` (attribution fixture), **and** the `ActionDispatcherSecurityTest` entry (below). | `CastCouncilVoteHandler.kt`, `Main.kt`, `ChatButtons.kt`, `council-vote-ballot.hbs`, `council-vote-gm-controls.hbs`, `council-vote-result.hbs`, `ActionDispatcherSecurityTest.kt` |
| **3** | **Votes sheet section + open dialog** | Votes nav entry + section (nav entry **and** partial registration **and** `kingdom-sheet.hbs` invocation — §4.2), `CouncilVotesContext`, `OpenCouncilVote` dialog, live tally bars + named ballots, GM close/reopen/delete wiring (each `_onClickAction` branch self-guarded), i18n in all 8 catalogs. | `MainNavEntry.kt`, `KingdomSheet.kt`, `Main.kt` (`loadTemplatePartials`), `kingdom-sheet.hbs`, `council-votes/page.hbs`, `CouncilVotesContext.kt`, `OpenCouncilVote.kt`, `lang/*.json` (8) |
| **4** | **Consequence linking + Recent Turns render** | `LinkVoteConsequence` checkbox picker, `linkConsequence`/`unlinkConsequence`, `closedVoteIds` stamping in `buildTurnRecord`, `TurnRecentEntry.closedVotes` in **both** recent-turn builders, Recent Turns closed-vote lines + back-refs + the `→ Turn N` highlight jump, journal export lines (explicit `StringBuilder` work — §6), full manual QA. | `LinkVoteConsequence.kt`, `TurnHistory.kt`, `SessionPrepView.kt`, `SessionPrepContext.kt`, `session-prep/page.hbs`, `SessionPrepJournalExporter.kt` |

Phase 1 is self-contained (data + pure logic, fully unit-tested). Phase 2 depends on 1. Phase 3
depends on 1 (needs the store) and reuses 2's transforms. Phase 4 depends on 1–3.

---

## 9. Open Questions for Gregory

1. **Store placement.** Plan puts the authoritative array top-level on `KingdomData.councilVotes`
   (cadence mismatch: votes open before the turn's record exists) with a nullable `closedVoteIds`
   back-ref on `RawTurnRecord`. Accept, or force everything into the turn stream?
2. **Cap size.** 50 stored votes (drop oldest). Enough for a full campaign, or make it a setting?
3. **Abstain semantics.** Explicit abstain (`-1`) vs. implicit non-vote are shown distinctly.
   Keep both, or collapse to one?
4. **Named vs. anonymous.** Plan recommends named-only for v1 (accountability is the point); the
   `anonymous` flag exists but is unbuilt. Agree?
5. **Consequence link visibility.** Links + recap line are player-visible; `outcomeNote` can hold
   GM spoilers — gate `outcomeNote` behind `isGM`, or trust the GM to keep it clean? Whichever way
   this lands, the *mechanism* is already decided: the gate is `CouncilVotesContext.kt` passing
   `outcomeNote = vote.outcomeNote.takeIf { isGM }`, never a `{{#if isGM}}` in the template
   (nullability is the gate — §4.3). Only the yes/no is open.
6. **Delete vs. keep.** Should the GM be able to hard-delete a vote (chosen), or only close it?

---

**End of Plan.** Ready for review. On approval, implementation cards will be created per the
phasing table above.

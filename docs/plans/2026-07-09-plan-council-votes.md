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
| `src/jsMain/kotlin/.../kingdom/CouncilVotes.kt` | Pure `RawCouncilVote` transforms (`castVote`, `closeVote`, `reopenVote`, `appendCouncilVote` cap, `tallyVote` adapter) — jsMain because it touches `@JsPlainObject` |
| `src/jsMain/kotlin/.../actions/handlers/CastCouncilVoteHandler.kt` | Socket handler that records a player-cast ballot GM-side (`originatorPolicy = ANY`) |
| `src/jsMain/kotlin/.../kingdom/dialogs/OpenCouncilVote.kt` | GM dialog: question + options → posts the vote card |
| `src/jsMain/kotlin/.../kingdom/dialogs/LinkVoteConsequence.kt` | GM picker: attach a later turn record to a closed vote |
| `src/jsMain/kotlin/.../kingdom/sheet/contexts/CouncilVotesContext.kt` | Thin UI context for the Votes list section |
| `src/jsMain/kotlin/.../migrations/migrations/Migration49.kt` | Initialize `councilVotes = []` on kingdoms lacking it |

### New Handlebars templates

| File | Purpose |
|------|---------|
| `src/jsMain/resources/chatmessages/council-vote-ballot.hbs` | The interactive ballot card (option buttons, abstain, GM close) |
| `src/jsMain/resources/chatmessages/council-vote-result.hbs` | Posted on close: final tally + tie/GM-decides note |
| `src/jsMain/resources/applications/kingdom/sections/council-votes/page.hbs` | Votes list section (live tally, GM controls, consequence links) |

### Modified files

| File | Change |
|------|--------|
| `src/jsMain/kotlin/.../kingdom/data/RawTurnRecord.kt` | Add nullable `closedVoteIds: Array<String>?` back-reference |
| `src/jsMain/kotlin/.../kingdom/KingdomData.kt` | Add top-level `var councilVotes: Array<RawCouncilVote>?` (mirrors `quests`/`warThreats`/`turnHistory`) |
| `src/jsMain/kotlin/.../kingdom/ChatButtons.kt` | Add `km-council-vote-cast` / `-abstain` / `-close` / `-reopen` handlers |
| `src/jsMain/kotlin/.../Main.kt` | Register `CastCouncilVoteHandler` in the `ActionDispatcher` handler list |
| `src/jsMain/kotlin/.../kingdom/TurnHistory.kt` | Stamp `closedVoteIds` when building the End-Turn record; optional gazette line |
| `src/jsMain/kotlin/.../kingdom/sheet/contexts/SessionPrepContext.kt` | Surface closed votes + consequence links in Recent Turns |
| `src/jsMain/kotlin/.../kingdom/sheet/KingdomSheet.kt` | Register the Votes section + `_onClickAction` for open/close/link |
| `src/jsMain/kotlin/.../kingdom/sheet/navigation/MainNavEntry.kt` | Add the Votes nav entry (`.km-tabs`) |
| `src/jsMain/resources/applications/kingdom/sections/session-prep/page.hbs` | Render closed-vote lines inside a Recent Turns item |
| `lang/en.json` | Nested `kingdom.councilVotes.*` catalog (never flat-dotted) |

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
- **Fuels existing surfaces.** Closed votes flow into Recent Turns and the session-prep journal
  export for free, so the recap and "previously on…" narrative get decision context.

---

## 2. Data Model

All new interop types follow the house pattern: `external @JsPlainObject` interfaces with an
auto-generated `.copy`, **nullable fields for migration safety**, and simple JSON-round-trippable
primitives only.

### 2.1 `RawCouncilVote` — `src/jsMain/kotlin/.../kingdom/data/RawCouncilVote.kt`

```kotlin
package at.posselt.pfrpg2e.kingdom.data

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
     * your vote while the poll is open. Record<String, Int> so it JSON round-trips.
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
`companionExpeditions` arrays (`KingdomData.kt` lines 203/250/273/288/301).

**Why top-level and not nested inside `RawTurnRecord`** (a deliberate, called-out deviation
from the literal "votes live in the turn stream" framing): `RawTurnRecord` entries are only
appended at **End Turn** (`appendTurnRecord`). A vote is opened *ad hoc mid-turn* — before that
turn's record exists — and accumulates ballots live. There is nowhere in the `turnHistory`
array for an in-progress vote to live. So the live objects sit on `KingdomData.councilVotes`,
and each record's `closedVoteIds` stamps the linkage at close/End-Turn. This is the same
relationship `warThreats` (live, top-level) already has with the turn stream. **Flag for
Gregory** in Open Questions — if he prefers a single nested store we can revisit, but the
cadence mismatch makes top-level the correct call.

**Cap.** Mirror `appendTurnRecord(history, record, cap = 100)` (`TurnHistory.kt`) with an
`appendCouncilVote(votes, vote, cap = 50)` that drops the oldest once the cap is exceeded.
Votes are heavier than turn records (per-user maps) and rarer, so 50 is a sensible default and
is a named constant, not a magic literal.

### 2.4 Migration49

The chain currently ends at **`Migration65`** (`migrations/Migrations.kt`; `MigrationChainTest`
asserts contiguity). Propose **`Migration49`** *(placeholder — not free; see caveat)* (Gregory sequences the real number at
implementation time if the chain has advanced):

> ⚠️ **The number in this section is a placeholder and must be re-derived at implementation.**
> The chain now ends at **`Migration65`**. Since these plans were written, four of the reserved
> numbers have LANDED: 62 = downtime-projects, 63 = scheduled-pressure-engine,
> 64 = map-dynamism, 65 = loot-manifests. `Migration49` was never free (it sits inside the
> long-registered 17..61 range) and several unimplemented plans still name it. The next free
> number is **66**. Take the next contiguous number when this actually lands, and extend
> `MigrationChainTest`'s hardcoded range.


```kotlin
class Migration49 : Migration(49) {
    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        if (kingdom.councilVotes == null) kingdom.councilVotes = arrayOf<RawCouncilVote>()
    }
}
```

Register it in the `migrations` list in `Migrations.kt`. Nullable fields technically don't
*require* a backfill, but seeding `[]` keeps reads uniform and follows the lesson from the
un-registered Migrations 41–48 (register every authored migration so the chain actually runs).
Add `Migration49Test` mirroring the existing `Migration48Test` / `Migration30Test` fixtures.

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
live in `jsMain` because they touch the `@JsPlainObject` type. Pure ≠ common — the axis here is
*which source set can see the interop type*, exactly as `applyStandingDelta` and
`appendTurnRecord` already sit in `jsMain`.

```kotlin
/** Adapter: unpack a RawCouncilVote into the commonMain math. */
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
    cap: Int = 50,
): Array<RawCouncilVote>

/** GM manual link — append a turn number to linkedRecordRefs (dedup). No inference. */
fun linkConsequence(vote: RawCouncilVote, turn: Int): RawCouncilVote
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

Opening a vote posts `council-vote-ballot.hbs` to chat (public, not whispered — everyone votes).
The card is the **ballot**: the question, one button per option, an **Abstain** button, and (GM
only) a **Close vote** button.

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
  {{#if isGM}}
    <button type="button" class="km-council-vote-close" data-vote-id="{{voteId}}"
            data-kingdom-actor-uuid="{{actorUuid}}">
      {{localizeKM "kingdom.councilVotes.close"}}
    </button>
  {{/if}}
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

A new `.km-tabs` nav entry (`MainNavEntry.kt`) + `sections/council-votes/page.hbs`, matching the
Session Prep section pattern (single root element). One row per vote:

- Question, open/closed badge, opened/closed turn.
- **Live tally bars** per option (from `tallyVote`), highlighting `leadingOptions`.
- **Named ballots** (named mode): each voter's name + their choice, resolved via
  `game.users.get(userId)?.name`.
- **GM controls** (`isGM`): Close / Reopen, **Link consequence** (opens `LinkVoteConsequence`),
  edit `outcomeNote`, delete vote.
- **Consequence links:** for each `linkedRecordRefs` turn, a chip "→ Turn 14" that scrolls to /
  highlights that Recent Turns entry.

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
    val outcomeNote: String?
    val linkedTurns: Array<Int>
}
```

### 4.3 Recent Turns rendering

`SessionPrepContext.recentTurns` gains, per turn, a `closedVotes` list resolved from
`RawTurnRecord.closedVoteIds` → `KingdomData.councilVotes`. In
`session-prep/page.hbs`, inside the existing Recent Turns `<li>`, add a player-visible block:

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
shared memory. (GM-only free-text `outcomeNote` internals can be gated behind `isGM` if it holds
spoilers; the recap line itself is public.)

### 4.4 i18n namespace

All strings nest under `kingdom.councilVotes.*` in `lang/en.json` (nested objects, **never
flat-dotted** — flat keys render raw; guarded by `scripts/check_i18n_keys.py`). The catalog is
already wired: `en.json` is imported by `Localization.kt` (`englishTranslations`) and loaded in
`initLocalization()`; templates read it via the `localizeKM` Handlebars helper. Keys:
`ballotTitle`, `abstain`, `close`, `reopen`, `open`, `dialogQuestion`, `dialogOption`,
`recapLine`, `recapLinked`, `tieNote`, `resultTitle`, `navLabel`, `emptyState`, plus voter
status strings (`hasNotVoted`, `abstained`).

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

**(b) BUT a player click cannot persist the vote directly.** Every existing kingdom-mutating
ChatButton calls `actor.getKingdom()` / `actor.setKingdom(kingdom)`. `setKingdom` writes a flag
on the party/kingdom **Actor document**, which in Foundry requires **OWNER** permission —
players generally do **not** own the party actor (readonly player sheets, roll-check ownership
lock). That is exactly why every state-changing button in `ChatButtons.kt` today is GM-gated
(`if (!game.user.isGM) return@ChatButton`). A naive `km-council-vote-cast` that called
`setKingdom` from a player's browser would be **rejected by Foundry's permission layer**. The
"who clicked" is known; the "write it down" is not permitted client-side for players.

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
| `km-council-vote-close` | GM only (`if (!game.user.isGM) return@ChatButton`) | Direct GM-side. | `closeVote(turn, outcomeNote)`; stamp any current turn record's `closedVoteIds`; post `council-vote-result.hbs`. |
| `km-council-vote-reopen` | GM only | Direct GM-side. | `reopenVote`; ballots editable again. |

`CastCouncilVoteHandler(action = "castCouncilVote", mode = ExecutionMode.GM_ONLY,
originatorPolicy = OriginatorPolicy.ANY)` — registered in the `Main.kt` dispatcher handler list.
It resolves the actor from `data.actorUuid`, finds the vote by id in `kingdom.councilVotes`,
ignores casts on a closed vote, applies `castVote(vote, action.senderId!!, optionIdx)`, and
`setKingdom`. Its `execute` is the only place a player-origin cast becomes durable state.

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
| **Session-prep journal export** | `SessionPrepJournalExporter.kt` | Closed-vote recap lines flow into the export for free (same context). |
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
| `appendCouncilVote_capDropsOldest` | 51 appended over cap 50 → size 50, oldest gone (mirrors `appendTurnRecord` test). |
| `tallyVote_adapterMatchesCommonMath` | `tallyVote(raw)` equals `tallyVotes(options, map)`. |
| `linkConsequence_dedupesTurns` | linking Turn 14 twice yields one ref. |

**Per-user click-attribution fixture (the linchpin, jsTest):**
`src/jsTest/kotlin/.../actions/CastCouncilVoteHandlerTest.kt`

| Test | Assertion |
|------|-----------|
| `handler_recordsBallotUnderSenderId` | dispatch an `ActionMessage(action="castCouncilVote", senderId="player-2", data={voteId, optionIdx:1})` with `receivedViaSocket=true`; assert the GM-side handler writes `votes["player-2"] == 1`. Proves attribution survives the socket hop. |
| `handler_deniesWhenNotOptedIn` | a control handler left at default `originatorPolicy=GM_ONLY` rejects a non-GM sender (guards the deny-by-default invariant so a future edit can't silently make casting GM-forgeable). |
| `handler_ignoresClosedVote` | cast on a closed vote is a no-op GM-side. |

(jsTest runs via Chrome headless: `useChromeHeadless` + `CHROME_BIN`, `-x kotlinStoreYarnLock`,
throwaway `karma.config.d` override — Firefox headless times out in WSL.)

### 7.3 Manual Foundry verification checklist

1. GM opens Kingdom Sheet → **Votes** tab → **Open vote**, enters "Appease or fight the
   druids?" with options *Appease* / *Fight*. A ballot card posts to chat.
2. As **Player A** (separate client, non-owner of the party actor), click *Appease* → the Votes
   tab tally updates to `Appease: 1` on **all** clients (socket → GM write → flag re-render).
3. As **Player B**, click *Fight*; as **Player A**, change to *Fight* → tally `Fight: 2`, Player
   A's named row now reads *Fight* (change-your-vote works).
4. As **Player C**, click **Abstain** → shows "C abstained"; a non-voting player shows "has not
   voted".
5. GM clicks **Close** → result card posts with final tally; a **tie** shows the "GM decides"
   note field; GM fills `outcomeNote`.
6. Run End Turn → the closed vote appears in **Recent Turns** (player-visible), tied to the turn.
7. Two turns later, GM opens the closed vote → **Link consequence** → pick "Turn N — <recap>" →
   the vote row shows "→ Turn N" and the Recent Turns entry shows the back-reference.
8. Reload world → votes, ballots, close state, and links persist; legacy kingdoms (no
   `councilVotes`) load clean with an empty Votes tab (Migration49).
9. Confirm **nothing mechanical changed** from any vote result (advisory-only invariant).
10. All UI text resolves via i18n (no raw `kingdom.councilVotes.*` keys leaking).

---

## 8. Phasing (independently committable)

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Data + tally math + migration** | `RawCouncilVote`, `KingdomData.councilVotes`, `RawTurnRecord.closedVoteIds`, `Migration49` (+test), commonMain `CouncilVoteTally` + `CouncilVotesTest`/`CouncilVoteTallyTest`, jsMain `CouncilVotes.kt` transforms + cap. | `RawCouncilVote.kt`, `KingdomData.kt`, `RawTurnRecord.kt`, `CouncilVoteTally.kt`, `CouncilVotes.kt`, `Migration49.kt`, tests |
| **2** | **Socket cast handler + ballot card** | `CastCouncilVoteHandler` (`ANY`), register in `Main.kt`, `km-council-vote-cast`/`-abstain`/`-close`/`-reopen` ChatButtons, ballot + result templates, `CastCouncilVoteHandlerTest` (attribution fixture). | `CastCouncilVoteHandler.kt`, `Main.kt`, `ChatButtons.kt`, `council-vote-ballot.hbs`, `council-vote-result.hbs` |
| **3** | **Votes sheet section + open dialog** | Votes nav entry + section, `CouncilVotesContext`, `OpenCouncilVote` dialog, live tally bars + named ballots, GM close/reopen/delete wiring, i18n. | `MainNavEntry.kt`, `KingdomSheet.kt`, `council-votes/page.hbs`, `CouncilVotesContext.kt`, `OpenCouncilVote.kt`, `lang/en.json` |
| **4** | **Consequence linking + Recent Turns render** | `LinkVoteConsequence` picker, `linkConsequence`, `closedVoteIds` stamping in `buildTurnRecord`, Recent Turns closed-vote lines + back-refs, journal export lines, full manual QA. | `LinkVoteConsequence.kt`, `TurnHistory.kt`, `SessionPrepContext.kt`, `session-prep/page.hbs`, `SessionPrepJournalExporter.kt` |

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
   GM spoilers — gate `outcomeNote` behind `isGM`, or trust the GM to keep it clean?
6. **Delete vs. keep.** Should the GM be able to hard-delete a vote (chosen), or only close it?

---

**End of Plan.** Ready for review. On approval, implementation cards will be created per the
phasing table above.

# Player Pings — Personalized Turn-Open Whispers + Per-User Unread Feed

> **Status:** Plan only — no implementation yet
> **Date:** 2026-07-09
> **Branch:** `kingmaker.5`
> **Roadmap:** Player-engagement backlog (house-rules.md "give every player a reason to open the sheet")
> **Sibling plan:** `docs/plans/2026-07-09-plan-meanwhile-digest.md` — the *Meanwhile* digest is the **table-facing** narrated interlude (one shared card, prose beats). Player Pings is the **per-user actionable** layer (one private card per player, jump buttons, readiness). They share nothing except the End-Turn seam; align, don't duplicate. See §6.
> **Source of truth for per-user state:** **Foundry User flags** (`game.user`), NOT kingdom data. See §2.

---

## Executive Summary

At the top of a kingdom turn the GM opens the Turn Wizard and the world lurches forward, but the **players** have no personalized "here is what needs *you* this turn" signal. Everything actionable — a leadership check only their leader can roll, leadership activity slots still open, a companion expedition of theirs returning, a quest that just completed — is buried in a GM-facing sheet or a shared chat stream. Players disengage; the GM nags.

This feature adds two thin, per-user surfaces that share one slice of persisted state:

**(a) Turn-open whisper card** — when a turn opens, each *player* receives a **private** (whispered) chat card listing exactly the lines relevant to the PCs they own: pending leader-role check, leadership slots remaining, their expedition returning, their quest/petition due. Each line carries a **jump button** (open the sheet on the right tab / select the leader token). The card has one **"I'm Ready"** toggle. When a player clicks Ready, the GM's Turn Wizard shows a **readiness strip** ("3 / 4 players ready"). Readiness is **advisory** — it blocks nothing.

**(b) Per-user unread feed** — a **bell badge** on the Kingdom Sheet header showing a count of everything relevant that happened since that user last opened the sheet (expeditions returned, quests completed, caravans raided, deeds earned), and a dismissible **digest panel** listing those items. Computed **on render** from already-timestamped durable records — **no live socket push, no tick**.

Both features are **read-mostly and advisory**. The only writes are per-user flags (last-seen cursor + readiness). Nothing here is a GM-confirmed offer that grants a mechanical benefit — those already exist (`km-offer-*`); Player Pings only *points at* them.

---

## 1. Problem Statement + Player/GM Value

**The gap.** The module is GM-centric at the turn boundary. `TurnWizardApplication` (the End-Turn UI) is opened by the GM; `postLastTurnRecap` whispers a recap **to GMs only** (`TurnWizardApplication.kt:709` — `if (!game.user.isGM) return`). Players get a shared, undifferentiated chat stream and a sheet they have no reason to open on their own initiative. There is no "what do I, specifically, need to do or notice" view for a player who owns one PC leader.

**Why it earns table time.** Kingmaker is a *shared* governance game, but the software funnels everything through the GM. A player who owns the Warden should be pinged when a Warden-only check is pending and when the expedition they launched returns — without the GM reading it aloud. Turning "the GM tells you" into "the sheet tells you" is the single highest-leverage player-engagement change, and it reuses machinery that already exists (owned-role resolution, per-turn idempotent whispers, timestamped turn/expedition/quest records).

**Value:**

- **Player agency:** a private, actionable checklist arrives at turn open; jump buttons remove "where do I click?" friction.
- **GM prep reduction:** the readiness strip tells the GM at a glance who is done, so they stop chasing individuals before ending the turn.
- **Re-engagement between sessions:** the bell badge gives a returning player a one-glance "5 things happened while you were away" without scrolling chat history.
- **Zero coercion:** advisory only. No new gate, no auto-apply, no blocked End Turn.

---

## 2. Data Model

### 2.1 Persistence location — RECOMMENDATION: Foundry **User** flags, not kingdom data

Per-user state (last-seen cursor + readiness) is **inherently per-user and per-client-identity**, so it belongs on the Foundry `User` document, not on the shared kingdom actor.

**This is directly supported today.** The flag helpers in `utils/Document.kt` are generic over `D : Document`:

```kotlin
suspend fun <D : Document, T> D.setAppFlag(key: String, flag: T)   // → setFlag(Config.moduleId, key, flag)
fun     <D : Document, T> D.getAppFlag(key: String): T?            // → getFlag(Config.moduleId, key)
suspend fun <D : Document>  D.unsetAppFlag(key: String)            // → unsetFlag(Config.moduleId, key)
```

`com.foundryvtt.core.documents.User` is `external class User : ClientDocument` → `ClientDocument : Document`, so **`game.user.setAppFlag("playerPings", …)` / `game.user.getAppFlag(...)` compile and run with zero new plumbing**, namespaced under `Config.moduleId` exactly like the existing actor app-flags (`turn-wizard-state`, `lastTurnSnapshot`, `lastRecapTurn`).

**Why NOT kingdom data:**
- Kingdom data is one shared blob broadcast to every client; stuffing per-user cursors there means every player's last-seen time is visible to (and writable by) everyone, and every cursor bump is a full kingdom-actor update. User flags are scoped to the user and writable by that user without touching the actor.
- No schema migration is needed for a new User-flag key (flags are a free-form bag; absent = default).

**Consequence — persistence table:**

| Data | Persists on | Flag path (`flags.<moduleId>.…`) | Writer |
|------|-------------|----------------------------------|--------|
| Last-seen cursor (feed) | `game.user` (each player + GM) | `playerPings.lastSeenAtMillis`, `playerPings.lastSeenTurn` | that user (on sheet render) |
| Per-turn readiness | `game.user` (each player) | `playerPings.readyForTurn` (Int = the turn they marked ready) | that user (clicks "I'm Ready") |
| Per-turn whisper idempotency | **kingdom actor** (GM writes once) | `lastPlayerPingsTurn` (Int) | GM (turn-open) |

The one field that is legitimately *shared* is `lastPlayerPingsTurn` on the kingdom actor — it is the GM-side "I already fired this turn's whisper batch" guard, mirroring the existing `lastRecapTurn` (`TurnWizardApplication.kt:713,735`). It is not per-user state and does not need per-user scoping.

### 2.2 User-flag shape (all nullable — absent = sensible default)

```kotlin
// jsMain: kingdom/data/RawPlayerPingsState.kt  (@JsPlainObject, User-flag payload)
@JsPlainObject
external interface RawPlayerPingsState {
    /** Normalized cursor: max occurredAt (epoch millis) the user has "seen". Null => never opened => everything is unread. */
    var lastSeenAtMillis: Double?
    /** Kingdom turn the user last viewed (for "since turn N" copy + a cheap coarse cursor). Null => none. */
    var lastSeenTurn: Int?
    /** The kingdom turn number this user marked themselves Ready for. Null / < currentTurn => not ready this turn. */
    var readyForTurn: Int?
    /** Optional: feed-item ids the user explicitly dismissed even though newer than the cursor (per-item snooze). Nullable; empty ok. */
    var dismissedIds: Array<String>?
}
```

All fields nullable so a first-time reader (no flag yet) is handled by pure defaults, and so the shape can grow without ever needing a "flag migration" (there is no migration mechanism for User flags — see §2.4).

### 2.3 Timestamp audit — what carries a timestamp TODAY vs what must be added

The feed and whisper derive from **existing durable records**. Audited by reading each interface:

| Record source | File | Timestamp field(s) present TODAY | Verdict |
|---------------|------|----------------------------------|---------|
| **Turn history** `RawTurnRecord` | `kingdom/data/RawTurnRecord.kt` | `turn: Int` **and** `timestamp: String` (ISO, set via `Date().toISOString()` in `buildTurnRecord`), plus `playerNotes: String?` (player-safe gazette) | ✅ **Timestamped.** Primary feed backbone. |
| **Expedition chronicle** `RawExpeditionChronicleEntry` | `kingdom/data/RawCompanionExpedition.kt` | `turn: Int` **and** `appliedAt: String` (ISO) | ✅ **Timestamped.** Durable "expedition returned/resolved" rows. |
| **Quests** `RawQuest` | `kingdom/data/RawQuest.kt` | `createdAt: Double?` + `updatedAt: Double?` (epoch millis) + `completionSnapshot.turn: Int` | ✅ **Timestamped enough.** "Completed" = `status=="completed"` with `completionSnapshot.turn` + `updatedAt`. |
| **In-flight expedition** `RawCompanionExpedition` | `kingdom/data/RawCompanionExpedition.kt` | `createdAt: String?` only; **no** resolution/return timestamp (uses live `status` + `daysRemaining`) | ⚠️ **Partial.** Live status drives the *whisper* line ("your expedition returns this turn"); the durable *feed* row uses the chronicle entry instead. No new field needed. |
| **Caravans** `RawCaravan` + `kingdom.shipmentHistory` | `kingdom/data/RawCaravan.kt`, `kingdom/ShipmentHistoryData.kt` | `RawCaravan` itself: **none** (the row is removed on arrival). But `RawShipmentHistoryEntry { turn, partner, cargo, outcome, rdGained }` now persists every resolved shipment. | ✅ **Durable, turn-stamped** (landed after this plan was drafted — the card anticipated it as "caravan history once gap0709-caravan-panel lands"). Not wall-clock stamped: see the design consequence below. |
| **War threats** `RawWarThreat` | `kingdom/data/RawWarThreat.kt` | `visibleToPlayers: Boolean?`; escalation/arrival are transient | ❌ **Not independently timestamped.** Arrival captured only inside the turn record (`clockEvents`/notes). |
| **Deeds chronicle** (sibling plan `2026-07-09-plan-deeds-chronicle.md`) | not built | would carry `turn` + timestamp by design | ⛔ **Does not exist yet** — optional feed source, existence-gated. |
| **Petition inbox** (sibling plan `2026-07-09-plan-petition-inbox.md`) | not built | TBD | ⛔ **Does not exist yet** — optional whisper line, existence-gated. |

**Design consequence (this is the load-bearing decision), revised:** when this plan was drafted, the
only durable trace of a caravan raid was prose inside `RawTurnRecord.playerNotes`, so v1 proposed
deriving the feed item by reading that text. **`kingdom.shipmentHistory` has since landed**, giving a
structured row per resolved shipment, so v1 should read that instead — matching on
`outcome == "raided"` rather than pattern-matching a sentence a GM may have rewritten. The sibling
NPC-memory plan draws the same line for the same reason: GM prose is not a machine-readable event
source.

**War threats** keep the `playerNotes` derivation; they still have no durable per-event row.

**Ordering.** `shipmentHistory` entries carry `turn: Int`, not millis, while the feed sorts on
`occurredAtMillis` against `cursor.lastSeenAtMillis`. Map a shipment row to the `timestamp` of the
`RawTurnRecord` bearing the same `turn` — that field exists and is ISO. Granularity is therefore
per-turn, exactly as the prose path already was, so this composes with the existing cursor without
changing it. **Still no new persistence and no migration** for this feature.

Only if a future version wants each caravan as its own dismissible, per-caravan-jump-linked row would
a wall-clock-stamped event log be needed. We explicitly do **not** do that in v1.

### 2.4 Migration — **NOT NEEDED** (justified)

- Per-user state lives in **User flags**, which have **no schema version and no migration runner** — the migration chain (`Migrations.kt`, ending at **`Migration61`**, contiguity asserted by `MigrationChainTest`) only versions the kingdom/camping flag. A new User-flag key simply appears; absent = default. **This feature needs no migration at all.**
- No new **kingdom-side** field is required either. `lastPlayerPingsTurn` is a free-form actor app-flag (same class as `lastRecapTurn`), which the existing code adds without a migration.
- **Cleanup instead of migration:** stale User flags are harmless (a `lastSeenAtMillis` from a deleted world just makes everything read as "seen"). Provide a light housekeeping path:
  - On kingdom **reset/delete**, and on module **downgrade**, best-effort `game.user.unsetAppFlag("playerPings")` for the local user (we cannot iterate other users' flags without GM socket calls — out of scope).
  - `readyForTurn` is self-expiring: it is only "ready" when `readyForTurn == currentTurn`, so last turn's value is inert; no cleanup needed.
- **Escape hatch (documented, not built):** *if* a later version adds a discrete persisted caravan/war-threat event log for per-event feed rows, that log lives on the **kingdom actor** and would need a migration of its own, numbered against the chain at that time rather than reserved here (62–67 are already spoken for by other unimplemented plans). v1 avoids it entirely: caravan rows come from `kingdom.shipmentHistory` and war threats from `RawTurnRecord`.

---

## 3. Engine Design

### 3.1 Pure core (commonMain + commonTest) over plain carriers

The Raw* records are jsMain-only `@JsPlainObject`/`@JsExport` interfaces, so the **pure** layer operates on **plain Kotlin data carriers** (like `FactionRelations`/`ActivityCapCalculator` keep their math testable). jsMain adapters map `Raw*` → carriers, call the pure core, and map results back to context/whisper objects. Pure core → `commonMain`, tested in `commonTest`; adapters + IO → `jsMain`, tested in `jsTest`.

**File:** `src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/PlayerPings.kt`

```kotlin
package at.posselt.pfrpg2e.kingdom

/** Player-facing visibility of a source row (already resolved by the jsMain adapter from each source's own filter). */
enum class PingVisibility { PLAYER_SAFE, GM_ONLY }

/** A normalized, timestamped thing that happened, ready for feed selection. Plain — no Foundry types. */
data class FeedRecord(
    val id: String,                 // stable id for dedup/dismiss (e.g. "exp:<chronicleTurn>:<title>", "quest:<id>", "turn:<n>:caravan")
    val type: FeedType,             // EXPEDITION | QUEST | CARAVAN | WAR | DEED
    val turn: Int,                  // kingdom turn for "Turn N" grouping
    val occurredAtMillis: Double,   // normalized epoch millis — the unread cursor axis
    val visibility: PingVisibility,
    val summaryKey: String,         // i18n key
    val summaryData: Map<String, String>, // interpolation payload (player-safe only)
    val jump: JumpTarget?,          // where the "view" button goes (null => no jump)
)

enum class FeedType { EXPEDITION, QUEST, CARAVAN, WAR, DEED }

/** Where a jump button navigates. Rendered by the jsMain layer into a data-action button. */
sealed interface JumpTarget {
    data class SheetTab(val tab: String) : JumpTarget            // e.g. "expeditions", "quests"
    data class LeaderToken(val leaderUuid: String) : JumpTarget  // pan+select the leader actor's token
    data class Quest(val questId: String) : JumpTarget
}

/** ---------- FEED DERIVATION (feature b) ---------- */

data class SeenCursor(val lastSeenAtMillis: Double?, val dismissedIds: Set<String>)

/**
 * Pure: everything player-safe that is newer than the cursor and not dismissed, newest first, capped.
 * `records` is the union already mapped from all sources. Visibility is pre-resolved by the adapter
 * (reusing each source's own player-safety filter — see §5.2); GM callers pass a cursor and the adapter
 * simply includes GM_ONLY rows for them.
 */
fun deriveUnreadFeed(
    records: List<FeedRecord>,
    cursor: SeenCursor,
    includeGmOnly: Boolean,
    cap: Int = FEED_ITEM_CAP,
): List<FeedRecord> =
    records.asSequence()
        .filter { includeGmOnly || it.visibility == PingVisibility.PLAYER_SAFE }
        .filter { cursor.lastSeenAtMillis == null || it.occurredAtMillis > cursor.lastSeenAtMillis }
        .filterNot { it.id in cursor.dismissedIds }
        .sortedByDescending { it.occurredAtMillis }
        .take(cap)
        .toList()

/** Badge count is just the size (uncapped) so "12+" can be shown when it exceeds the cap. */
fun unreadCount(records: List<FeedRecord>, cursor: SeenCursor, includeGmOnly: Boolean): Int =
    records.count {
        (includeGmOnly || it.visibility == PingVisibility.PLAYER_SAFE) &&
            (cursor.lastSeenAtMillis == null || it.occurredAtMillis > cursor.lastSeenAtMillis) &&
            it.id !in cursor.dismissedIds
    }

/** ---------- WHISPER COMPOSITION (feature a) ---------- */

/** One line on a player's turn-open whisper card. */
data class PingLine(
    val kind: PingLineKind,
    val labelKey: String,
    val labelData: Map<String, String>,
    val jump: JumpTarget?,
)

enum class PingLineKind { LEADER_CHECK_PENDING, LEADERSHIP_SLOTS, EXPEDITION_RETURNING, QUEST_DUE, PETITION_DUE }

/** The per-user turn-open card model. `roles` = the leadership roles this user owns (may be empty/many). */
data class WhisperCard(
    val turn: Int,
    val lines: List<PingLine>,
    val alreadyReady: Boolean,
)

/** Everything the pure composer needs for one user for one turn. Assembled by the jsMain adapter. */
data class UserTurnState(
    val ownedRoles: Set<String>,            // leader role ids the user owns ("ruler", "warden", …)
    val ownedLeaderUuids: Map<String, String>, // role id -> leader actor uuid (for LeaderToken jumps)
    val pendingRoleChecks: Set<String>,     // role ids with an unresolved required check this turn
    val leadershipSlotsRemaining: Int,      // kingdom-wide remaining leadership activities (shared hint; see §3.3)
    val returningExpeditions: List<ExpeditionRef>, // expeditions the user's companions are on, resolving this turn
    val questsDue: List<QuestRef>,          // quests due this turn the user is associated with (or all, if unassigned)
    val petitionsDue: List<PetitionRef>,    // empty unless the petition-inbox feature exists
    val readyForTurn: Int?,
)

/**
 * Pure: build a user's card. Emits at most one line per kind except EXPEDITION/QUEST/PETITION which
 * fan out per item, then hard-caps the total. Returns null when there is nothing for this user
 * (caller then skips whispering — no empty cards).
 */
fun composeWhisper(userState: UserTurnState, currentTurn: Int, cap: Int = WHISPER_LINE_CAP): WhisperCard? {
    val lines = buildList {
        userState.pendingRoleChecks.forEach { role ->
            add(PingLine(PingLineKind.LEADER_CHECK_PENDING, "kingdom.playerPings.line.leaderCheck",
                mapOf("role" to role), userState.ownedLeaderUuids[role]?.let { JumpTarget.LeaderToken(it) }))
        }
        if (userState.ownedRoles.isNotEmpty() && userState.leadershipSlotsRemaining > 0) {
            add(PingLine(PingLineKind.LEADERSHIP_SLOTS, "kingdom.playerPings.line.slots",
                mapOf("count" to userState.leadershipSlotsRemaining.toString()), JumpTarget.SheetTab("turn")))
        }
        userState.returningExpeditions.forEach { e ->
            add(PingLine(PingLineKind.EXPEDITION_RETURNING, "kingdom.playerPings.line.expedition",
                mapOf("title" to e.title), JumpTarget.SheetTab("expeditions")))
        }
        userState.questsDue.forEach { q ->
            add(PingLine(PingLineKind.QUEST_DUE, "kingdom.playerPings.line.quest",
                mapOf("title" to q.title), JumpTarget.Quest(q.id)))
        }
        userState.petitionsDue.forEach { p ->
            add(PingLine(PingLineKind.PETITION_DUE, "kingdom.playerPings.line.petition",
                mapOf("title" to p.title), JumpTarget.SheetTab("petitions")))
        }
    }.take(cap)
    if (lines.isEmpty()) return null
    return WhisperCard(currentTurn, lines, alreadyReady = userState.readyForTurn == currentTurn)
}

/** ---------- READINESS (feature a, GM strip) ---------- */

data class ReadinessStrip(val ready: Int, val total: Int, val readyUserNames: List<String>, val waitingUserNames: List<String>)

/** Pure: fold each player's readyForTurn against the current turn into the strip the GM sees. */
fun computeReadiness(players: List<PlayerReadiness>, currentTurn: Int): ReadinessStrip {
    val ready = players.filter { it.readyForTurn == currentTurn }
    return ReadinessStrip(
        ready = ready.size, total = players.size,
        readyUserNames = ready.map { it.name },
        waitingUserNames = players.filterNot { it.readyForTurn == currentTurn }.map { it.name },
    )
}

data class PlayerReadiness(val name: String, val readyForTurn: Int?)
data class ExpeditionRef(val title: String)
data class QuestRef(val id: String, val title: String)
data class PetitionRef(val title: String)

const val FEED_ITEM_CAP = 12
const val WHISPER_LINE_CAP = 8
```

### 3.2 The personalization query — role → user resolution

Two subtleties, both grounded in existing code:

1. **`getOwnedLeaderRoles(game, kingdom)` (Leaders.kt) resolves for the LOCAL client only** — it calls `actor.isOwner`, which is relative to `game.user`. Player Pings whisper cards are **composed on the GM's client** (the turn-open trigger runs there) and shipped to a specific user, so we must resolve ownership **per target user**, not for the local client. Reusing `isOwner` would repeat the war-threat "baked isGM shipped true to everyone" bug (`TurnWizardApplication.kt:591`).

   **New jsMain resolver** (mirrors Leaders.kt but per-user):

   ```kotlin
   // jsMain: kingdom/PlayerPingsResolver.kt
   suspend fun ownedRolesFor(user: User, kingdom: KingdomData): Set<String> {
       val leaders = kingdom.leaders
       val roles = mapOf(
           "ruler" to leaders.ruler, "counselor" to leaders.counselor, "emissary" to leaders.emissary,
           "general" to leaders.general, "magister" to leaders.magister, "treasurer" to leaders.treasurer,
           "viceroy" to leaders.viceroy, "warden" to leaders.warden,
       )
       return roles.mapNotNull { (roleId, v) ->
           val uuid = v.uuid ?: return@mapNotNull null
           val actor = fromUuidOfTypes(uuid, PF2ECharacter::class, PF2ENpc::class) ?: return@mapNotNull null
           // testUserPermission is client-independent (reads the ownership map) — the correct API here.
           if (actor.testUserPermission(user, "OWNER")) roleId else null
       }.toSet()
   }
   ```

2. **Degrade gracefully.** The resolver is total over the two edge cases the task calls out:
   - **User owns multiple PCs / multiple roles** → the returned set has >1 role; `composeWhisper` fans out one `LEADER_CHECK_PENDING` line per pending owned role and shows a single `LEADERSHIP_SLOTS` line. No dedup problem.
   - **User owns no PC leader** (spectator, or a player whose PC holds no seat) → empty set → no leader-check / slots lines. They still get expedition/quest/feed lines that reference their companions, and if `composeWhisper` returns null they simply receive no whisper (no empty card).
   - **GM** → whispers are for *players*; the GM is skipped for the whisper batch (they already get `postLastTurnRecap`). The GM does see everyone's readiness strip.
   - **A role's leader actor is missing/deleted** (`fromUuidOfTypes` → null) → that role contributes no ownership to anyone (skipped), matching Leaders.kt behavior.

### 3.3 Leadership slots remaining — honest scoping

`ActivityCapCalculator.calculate(kingdom, performedCounts, leadershipCap, leadershipCapWithTownhall)` returns `caps.first { phase == "leadership" }` with `current` (performed) and `maximum` (`countPcLeaders * perLeader`). **The cap is kingdom-wide** — performed leadership activities are tracked kingdom-wide (`getPerformedActivities(): id -> count`), **not attributed to a user**. So v1 shows the **kingdom-wide** "N leadership activities remaining" as a *shared hint* on every owning player's card (honest: "the council has 3 leadership activities left"), computed as `maximum - current`. **Per-user attribution of slots is explicitly OUT-OF-SCOPE** (§6) — it would require tracking which user performed each activity, which the model does not do today.

### 3.4 Tick surface — respect the two-tick split; the feed is computed on render, not on a tick

- **Whisper cards (feature a): fire at TURN-OPEN**, the same seam as `postLastTurnRecap`. There is **no new tick**. The trigger is the existing `"open-turn-wizard"` sheet action (`KingdomSheet.kt:2101-2105`) and the direct `performEndTurn` path (`KingdomSheet.kt:2097`). A new `postPlayerPings(game, actor)` runs alongside `postLastTurnRecap`, idempotent per turn via the kingdom-actor flag `lastPlayerPingsTurn` (exact `lastRecapTurn` pattern). Monthly cadence = `TurnTickingEngine`'s End Turn; the whisper is *not* on the daily `DailyTickHooks`.
- **Unread feed (feature b): computed ON RENDER** of the Kingdom Sheet, inside the sheet's context builder. It reads the durable records + the local user's cursor flag and calls `deriveUnreadFeed`/`unreadCount`. No tick, no socket, no push. Opening the sheet is what refreshes the badge; clicking "mark all read" bumps the cursor.

This honors the rule: `TurnTickingEngine` = monthly End Turn (where whispers fire), `DailyTickHooks` = daily world clock (untouched), and the feed is a **pure render-time projection**, never a third tick.

---

## 4. UI

### 4.1 Turn-open whisper card (chat)

**File:** `src/jsMain/resources/chatmessages/player-pings.hbs` (single root element).

Rendered once per player per turn, whispered to that user id only. Structure: header ("Turn N — what needs you"), a `<ul>` of lines (each: localized text + optional jump `<button data-action="km-ping-jump" data-jump-kind="…" data-jump-value="…">`), and one footer toggle `<button data-action="km-ping-ready">` that shows "I'm Ready" / "Ready ✓" from `alreadyReady`.

**Context object** (jsMain, built from `WhisperCard`):

```kotlin
@JsPlainObject
external interface PlayerPingsCardContext {
    val turn: Int
    val kingdomActorUuid: String
    val lines: Array<PlayerPingLineContext>
    val alreadyReady: Boolean
}
@JsPlainObject
external interface PlayerPingLineContext {
    val text: String            // pre-localized
    val icon: String            // fa-… per PingLineKind
    val jumpKind: String?       // "sheet-tab" | "leader-token" | "quest"
    val jumpValue: String?      // tab id / leader uuid / quest id
    val jumpLabel: String?      // pre-localized "View"
}
```

Buttons are wired through the existing `ChatButtons.kt` dispatcher (the deny-by-default `ActionDispatcher`; register `km-ping-jump` and `km-ping-ready`). `km-ping-ready` writes `game.user`'s `playerPings.readyForTurn = turn` and re-renders any open Turn Wizard so the GM strip updates (best-effort; see §6 for no-socket caveat). `km-ping-jump` opens the Kingdom Sheet on the given tab, or pans+selects the leader token, or opens the quest.

### 4.2 Readiness strip (Turn Wizard)

Add a **read-only strip** to `applications/kingdom/turn-wizard.hbs` above the checklist: "Players ready: 3 / 4 — waiting on Bob, Carol." Built in `TurnWizardApplication.buildContext` from `computeReadiness`, added to `TurnWizardContext` as `readiness: ReadinessStripContext?` (null when there are no player users). It reads each player User's `playerPings.readyForTurn` flag. **Advisory only** — it never affects `canCommit` (which stays driven solely by `capsResult.hasAnyOverCap`). The GM sees it; it blocks nothing (§5, §6).

```kotlin
@JsPlainObject
external interface ReadinessStripContext {
    val ready: Int
    val total: Int
    val waitingNames: String  // pre-joined "Bob, Carol"; empty => everyone ready
}
```

### 4.3 Bell badge + digest panel (Kingdom Sheet header)

- **Badge:** a bell icon in the sheet header with a count from `unreadCount` (shows "9+" past the cap). Rendered from the sheet context; visible to every user (each sees their own count from their own cursor flag).
- **Panel:** clicking the bell toggles a dismissible dropdown listing `deriveUnreadFeed` items (newest first), each with its localized summary + optional jump button + a per-item dismiss "×". A footer "Mark all read" button bumps `lastSeenAtMillis`/`lastSeenTurn` to now/currentTurn and clears the badge.
- **Context:** extend `KingdomSheetContext` with `unreadFeed: UnreadFeedContext` `{ count: Int, cappedLabel: String, items: Array<FeedItemContext> }`; `FeedItemContext { id, text, icon, turnLabel, jumpKind?, jumpValue?, jumpLabel? }`.

### 4.4 i18n namespace

All keys nested under the module's existing `kingdom.*` tree (i18next nested-path — never flat-dotted, per `scripts/check_i18n_keys.py`), in `lang/en.json`, wired via `initLocalization()`:

```
kingdom.playerPings.card.title            "Turn {turn}: what needs you"
kingdom.playerPings.line.leaderCheck      "{role}: a leadership check is waiting on you."
kingdom.playerPings.line.slots            "The council has {count} leadership activities left this turn."
kingdom.playerPings.line.expedition       "Your expedition \"{title}\" returns this turn."
kingdom.playerPings.line.quest            "Quest due: {title}."
kingdom.playerPings.line.petition         "Petition awaiting your answer: {title}."
kingdom.playerPings.ready                 "I'm Ready"
kingdom.playerPings.readyDone             "Ready ✓"
kingdom.playerPings.jump.view             "View"
kingdom.playerPings.readiness.strip       "Players ready: {ready} / {total}"
kingdom.playerPings.readiness.waiting     "Waiting on {names}"
kingdom.playerPings.feed.title            "Since your last visit"
kingdom.playerPings.feed.markAllRead      "Mark all read"
kingdom.playerPings.feed.empty            "Nothing new."
kingdom.playerPings.feed.expedition       "{title} returned ({outcome})."
kingdom.playerPings.feed.quest            "Quest completed: {title}."
kingdom.playerPings.feed.caravan          "A caravan was raided near {place}."
kingdom.playerPings.feed.deed             "Deed earned: {name}."
```

---

## 5. Chat / Offer Surfaces

### 5.1 The whisper card is ADVISORY — it is NOT a `km-offer-*` grant

The module's rule: **every consequence that grants a mechanical benefit is a GM-confirmed offer** (`ChatButton("km-offer-…")` in `ChatButtons.kt`). Player Pings grants **nothing mechanical**:

- **The "I'm Ready" toggle is player self-service, not an offer.** It writes only the clicking user's own `playerPings.readyForTurn` flag. No kingdom state changes, no GM confirmation, no resource/XP/standing delta. It is not routed through the `km-offer-*` machinery and must not be.
- **Jump buttons are pure navigation** — they open a tab / select a token / open a quest. No state mutation.
- **The whisper card never grants** XP, loot, standing, or slots. When a line *points at* something that does grant (a pending leadership check, an expedition reward), the **actual grant still happens through its existing surface** (rolling the check; the existing `km-offer-expedition-reward` card). Player Pings only surfaces "this is waiting."

So the whisper uses plain `data-action` buttons (`km-ping-ready`, `km-ping-jump`) registered in the deny-by-default `ActionDispatcher`, distinct from the `km-offer-*` namespace.

### 5.2 Whisper-card line types (enumerated) + volume cap

| Line kind | Source (existing) | Player-safety reuse | Jump target | Emitted when |
|-----------|-------------------|---------------------|-------------|--------------|
| `LEADER_CHECK_PENDING` | leader roles + per-turn required checks; ownership via `ownedRolesFor` | ownership-gated (only the owner sees it) | `LeaderToken(leaderUuid)` | user owns a role with an unresolved required check this turn |
| `LEADERSHIP_SLOTS` | `ActivityCapCalculator` leadership cap | kingdom-wide public number | `SheetTab("turn")` | user owns ≥1 role **and** `remaining > 0` |
| `EXPEDITION_RETURNING` | `RawCompanionExpedition` live `status`/`daysRemaining`; participant match on `companionIds` | reuse `ExpeditionsContext` rule `visibleToPlayers \|\| isGM` | `SheetTab("expeditions")` | an expedition the user's companion is on resolves this turn |
| `QUEST_DUE` | `RawQuest` (`turnsRemaining`/deadline) | skip `hidden == true` quests (existing GM-only flag) | `Quest(questId)` | a non-hidden quest is due this turn |
| `PETITION_DUE` | petition inbox (**existence-gated**; feature not built) | that feature's own filter | `SheetTab("petitions")` | only if the petition-inbox feature is present |

**Volume cap:** `composeWhisper` hard-caps total lines at `WHISPER_LINE_CAP = 8` (expeditions/quests/petitions fan out, then truncate) and returns **null** when there are zero lines, so no player ever gets an empty or wall-of-text card. One card per player per turn (idempotent via `lastPlayerPingsTurn`).

### 5.3 Feed player-safety — reuse each source's existing filter (NotesContext lesson)

The recurring bug (memory: *NotesContext / player-safe*) is baking GM-only content into a player-visible surface. The feed adapter must reuse each source's **own** player-safety filter rather than re-deriving visibility:

- **Turn records:** use `RawTurnRecord.playerNotes` (the pre-computed player-safe gazette), **never** `notes` (which contains secret campaign-clock progress). `performEndTurn` already splits these two (`TurnWizardApplication.kt:496-521`).
- **Expeditions:** reuse the `ExpeditionsContext` rule `it.visibleToPlayers || isGM`; players' chronicle feed items are gated on `visibleToPlayers`.
- **Quests:** exclude `hidden == true`.
- **War threats:** only `visibleToPlayers == true` rows (arrival lines that reached the player gazette).
- **Deeds:** that feature's own visibility, when present.

The pure `deriveUnreadFeed` takes visibility **pre-resolved** (`FeedRecord.visibility`), and only GM callers pass `includeGmOnly = true`. Players can only ever see their own cursor and player-safe rows.

---

## 6. Interactions with Existing Systems + Out-of-Scope

### 6.1 Concrete file interactions

| System | File(s) | Interaction |
|--------|---------|-------------|
| Turn-open trigger | `kingdom/sheet/KingdomSheet.kt` (`"open-turn-wizard"` @ 2101; `performEndTurn` call @ 2097) | Add `postPlayerPings(game, actor)` beside `postLastTurnRecap`; also fire after `performEndTurn`. |
| Whisper posting + idempotency | `kingdom/dialogs/TurnWizardApplication.kt` (`postLastTurnRecap` pattern @ 709-736) | New `postPlayerPings` mirrors it: per-user compose + `whisper = arrayOf(userId)`, guarded by kingdom-actor flag `lastPlayerPingsTurn`. |
| Readiness strip | `TurnWizardApplication.buildContext` (@ 1097), `TurnWizardContext`, `turn-wizard.hbs` | Add `readiness` context from `computeReadiness`; **must not** touch `canCommit`. |
| Role→user resolution | `kingdom/Leaders.kt` (`getOwnedLeaderRoles`) | Add per-user `ownedRolesFor(user, kingdom)` using `testUserPermission` (client-independent). |
| Leadership slots | `kingdom/ActivityCapCalculator.kt` | Read leadership cap `maximum - current` (kingdom-wide hint). |
| Feed sources | `RawTurnRecord.kt` (`playerNotes`/`timestamp`), `RawCompanionExpedition.kt` (`expeditionChronicle`, `appliedAt`/`turn`), `RawQuest.kt` (`updatedAt`/`completionSnapshot.turn`) | Read-only; mapped to `FeedRecord`. No writes. |
| Sheet badge/panel | `kingdom/sheet/KingdomSheet.kt`, `KingdomSheetContext.kt`, `kingdom-sheet.hbs` | Compute `unreadFeed` on render from local user's cursor flag; bump cursor on "mark all read". |
| Chat buttons | `kingdom/ChatButtons.kt` (deny-by-default `ActionDispatcher`) | Register `km-ping-ready`, `km-ping-jump` (plain actions, **not** `km-offer-*`). |
| User flags | `utils/Document.kt` (`setAppFlag`/`getAppFlag`/`unsetAppFlag`, generic `D : Document`; `User : ClientDocument`) | Read/write `playerPings` on `game.user`. |
| i18n | `lang/en.json` + `initLocalization()` + `scripts/check_i18n_keys.py` | Nested `kingdom.playerPings.*`. |
| Meanwhile digest (sibling) | `2026-07-09-plan-meanwhile-digest.md` | Shares only the End-Turn seam. Meanwhile = one shared prose card; Player Pings = per-user actionable whispers. Different templates, different audiences; do not merge. |

### 6.2 Explicit OUT-OF-SCOPE

- **No live/socket push.** Badge + readiness update **on render only**. A player marking Ready updates the GM strip only when the GM's Turn Wizard next renders (best-effort local re-render if open on the same client; no cross-client socket in v1).
- **No per-user leadership-slot attribution** — the cap is kingdom-wide (§3.3).
- **No blocking.** Readiness never gates End Turn; `canCommit` stays `!hasAnyOverCap`.
- **No new persisted per-event log** for this feature — caravan rows come from the existing `kingdom.shipmentHistory`, war threats from `RawTurnRecord.playerNotes`, both at turn granularity (§2.3). (A wall-clock-stamped per-caravan log is not v1.)
- **No petition/deed sources unless those features exist** — both are sibling *plans* only (`2026-07-09-plan-petition-inbox.md`, `2026-07-09-plan-deeds-chronicle.md`); their lines/items are existence-gated and silently absent otherwise.
- **No AI/LLM prose** — pure i18n templates.
- **No cross-kingdom aggregation** — one kingdom per world.
- **No email/desktop notification** — in-Foundry chat + sheet only.

---

## 7. Test Plan

### 7.1 commonTest (pure — no Foundry)

**File:** `src/commonTest/kotlin/at/posselt/pfrpg2e/kingdom/PlayerPingsTest.kt`

| Test | Asserts |
|------|---------|
| `deriveUnreadFeed_filtersByCursor` | records with `occurredAtMillis <= lastSeenAtMillis` excluded; `> ` included. |
| `deriveUnreadFeed_nullCursorReturnsAll` | first-time reader (null cursor) sees everything (player-safe), newest first. |
| `deriveUnreadFeed_excludesGmOnlyForPlayers` | `includeGmOnly=false` drops `GM_ONLY`; `true` keeps them. |
| `deriveUnreadFeed_respectsDismissedAndCap` | dismissed ids removed; result length ≤ `FEED_ITEM_CAP`; sort stable. |
| `unreadCount_isUncapped` | count can exceed the display cap (drives "9+"). |
| `composeWhisper_multiRoleFansOut` | user owning ruler+warden with both checks pending → two `LEADER_CHECK_PENDING` lines. |
| `composeWhisper_noRolesNoSlotsLine` | empty `ownedRoles` → no slots/leader-check lines. |
| `composeWhisper_emptyReturnsNull` | nothing pending → null (no empty card). |
| `composeWhisper_capsLines` | > cap fan-out truncates to `WHISPER_LINE_CAP`. |
| `composeWhisper_readyFlagReflected` | `readyForTurn == currentTurn` → `alreadyReady == true`; stale turn → false. |
| `computeReadiness_countsCurrentTurnOnly` | only `readyForTurn == currentTurn` counted; names split ready/waiting. |

### 7.2 jsTest (Foundry-integrated fixtures)

**File:** `src/jsTest/kotlin/at/posselt/pfrpg2e/kingdom/PlayerPingsIntegrationTest.kt`

| Test | Fixture | Verifies |
|------|---------|----------|
| `userFlagRoundTrip` | mock `User` with app-flag helpers | `setAppFlag("playerPings", state)` then `getAppFlag` returns equal `RawPlayerPingsState`; absent key → null → defaults. |
| `ownedRolesFor_perUser` | kingdom with ruler→actorA, warden→actorB; ownership map { userX: A, userY: B } | `ownedRolesFor(userX)` = {ruler}; `userY` = {warden}; unmapped user = {}. |
| `ownedRolesFor_missingLeaderActor` | role uuid resolves to null | that role contributes to nobody. |
| `postPlayerPings_idempotentPerTurn` | run twice for same turn | second call is a no-op (`lastPlayerPingsTurn` guard); one whisper per player. |
| `postPlayerPings_skipsEmptyAndGm` | player with nothing pending; GM user | no card to that player; GM not whispered a player card. |
| `feedAdapter_reusesPlayerNotes` | turn record with distinct `notes` vs `playerNotes` | player feed shows `playerNotes` content only; GM feed can see `notes`. |
| `readinessStrip_advisoryOnly` | 1 of 2 ready, an over-cap kingdom | strip shows 1/2; `canCommit` unaffected (still false due to over-cap, true when caps ok regardless of readiness). |

### 7.3 Manual Foundry checklist

1. GM opens Turn Wizard → each logged-in player receives one **private** whisper card; GM does not.
2. A player who owns the Warden sees a Warden leader-check line; a spectator with no PC gets no card.
3. A player owning two seats sees both role lines, one slots line.
4. Click a jump button → correct tab opens / leader token is selected / quest opens.
5. Click "I'm Ready" → button flips to "Ready ✓"; GM's Turn Wizard (on next render) shows "2 / 3 — waiting on …".
6. End Turn is **not** blocked regardless of readiness.
7. Re-open the Turn Wizard for the same turn → no duplicate whispers (idempotent).
8. Advance a turn, log in as a player who was away → bell badge shows a count; open panel → expeditions returned / quests completed / caravan raided listed, player-safe only (no secret clock text).
9. "Mark all read" → badge clears; reload → stays cleared (cursor persisted on the user flag).
10. Dismiss one feed item → it stays gone after reload.
11. `python3 scripts/check_i18n_keys.py` → 0 unresolved / 0 flat-dotted.
12. `./gradlew assemble jsTest` (Chrome headless) → green.

---

## 8. Phasing (independently committable)

| Phase | Title | Deliverable | Key files |
|-------|-------|-------------|-----------|
| **1** | **Pure core + tests** | `PlayerPings.kt` (feed derivation, whisper composition, readiness) in commonMain; full `PlayerPingsTest`. No Foundry, no UI. | `commonMain/.../kingdom/PlayerPings.kt`, `commonTest/.../PlayerPingsTest.kt` |
| **2** | **User-flag state + resolver** | `RawPlayerPingsState` (User-flag shape), `ownedRolesFor(user, kingdom)`, jsMain adapters mapping `RawTurnRecord`/`RawExpeditionChronicleEntry`/`RawQuest` → `FeedRecord` and per-user → `UserTurnState`; `userFlagRoundTrip` + resolver jsTests. **No migration.** | `jsMain/.../kingdom/data/RawPlayerPingsState.kt`, `jsMain/.../kingdom/PlayerPingsResolver.kt`, `jsTest/.../PlayerPingsIntegrationTest.kt` |
| **3** | **Turn-open whispers + readiness strip** | `postPlayerPings` (idempotent via `lastPlayerPingsTurn`), whisper template + context, `km-ping-ready`/`km-ping-jump` in `ChatButtons`, readiness strip in Turn Wizard, i18n. | `TurnWizardApplication.kt`, `KingdomSheet.kt`, `ChatButtons.kt`, `resources/chatmessages/player-pings.hbs`, `resources/applications/kingdom/turn-wizard.hbs`, `lang/en.json` |
| **4** | **Bell badge + digest panel + QA** | Sheet-header badge/panel from `unreadFeed` context, mark-all-read + per-item dismiss (cursor writes), full manual checklist, green build + i18n check. | `KingdomSheet.kt`, `KingdomSheetContext.kt`, `resources/applications/kingdom/kingdom-sheet.hbs`, `lang/en.json` |

Phase 1 is standalone. Phase 2 depends on 1. Phases 3 and 4 both depend on 2 and can run in parallel (whisper/readiness vs badge/feed are disjoint surfaces).

---

## 9. Open Questions for Gregory

1. **Readiness reset:** auto-clear when a new turn opens (self-expiring via `readyForTurn == currentTurn`, this plan) — confirm you don't also want a manual "un-ready" button.
2. **Cross-client readiness:** v1 updates the GM strip on render only (no socket). Is best-effort acceptable, or do you want a socket emit so the strip live-updates while the Turn Wizard is open?
3. **Quest association:** with no per-quest owner field, `QUEST_DUE` shows *all* non-hidden due quests to every player. Add a `RawQuest.assignedRole?` later, or keep it shared?
4. **Feed sources at launch:** ship with expeditions + quests + caravan(from turn notes); wire deeds/petitions only once those sibling features land — confirm.
5. **Badge visibility for GM:** should the GM also get the bell/feed (with GM-only rows), or is the GM served entirely by `postLastTurnRecap`?

---

**End of Plan.** Ready for review. On approval, implementation cards will be created per the §8 phasing table.

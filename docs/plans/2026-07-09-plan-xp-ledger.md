# Plan: Party XP ledger with auto-offers on hex clear / quest complete / expedition beats

Card: `t_76207282`.

## 1. Problem statement

The house rules run XP-over-milestone at 1200 XP per level (`docs/house-rules.md` line 26), so every
reconnoitered hex, cleared site, roleplay encounter and completed quest is an award the GM works out
and applies by hand. Only two XP flows are automated today: combat XP, and the end-turn RP→XP
conversion. Everything else is a number someone remembers to hand out, or doesn't.

The module already *observes* the exact transitions those awards hang off — a hex content entry going
to cleared, a quest completing, an expedition resolving. This plan turns each observed transition
into a **proposed** ledger entry the GM confirms, and keeps the confirmed ones as an auditable
history that answers "where did we get to level 9?".

## 2. The distinction this plan turns on: party XP is not kingdom XP

These are two separate currencies with two separate award paths, and conflating them is the failure
mode this plan exists to avoid.

| | Party (PC) XP | Kingdom XP |
| --- | --- | --- |
| Lives on | `PF2ECharacter.system.details.xp` | `KingdomData.xp` / `.level` |
| Award path | `updateXP(players: Array<PF2ECharacter>, amount: Int)` — `macros/XP.kt:23` | `KingdomActor.gainXp(amount)` — `sheet/Xp.kt:35`, and `KingdomActor.levelUp()` — `sheet/Xp.kt:83` |
| Threshold | 1200 XP/level (house rule) | `KingdomData.calculateXpChange` |
| This ledger tracks | **this one** | not this one |

**`gainXp` and `levelUp` are the kingdom path and must never be the confirm target for a party
award** — confirming a hex-clear award through them would level up the *kingdom*. The confirm action
calls `updateXP`, which writes each character's `system.details.xp.value`, handles level roll-over,
caps at 20 and posts `chatmessages/xp-result.hbs`. That is "the existing PC XP award path" the card
asked to be named.

Kingdom XP stays where it is. The ledger may *display* kingdom-XP milestone awards for context, but
it never grants them; `MilestoneOffers.kt` already owns that.

## 3. Award sizes, and where each number actually comes from

The card asked for the award table to be extracted from `docs/house-rules.md`. Doing that honestly
turns up a gap worth stating plainly: **house-rules.md contains kingdom-XP numbers, and no per-hex
party-XP table.** The party-side numbers are PF2e's standard accomplishment XP. Both are listed, with
provenance, so nothing here reads as invented:

| Source kind | Proposed default | Provenance |
| --- | --- | --- |
| Hex reconnoitered | 10 (minor) | PF2e accomplishment XP — **not** in house-rules.md |
| Site cleared (minor) | 10 | PF2e accomplishment XP |
| Site cleared (moderate) | 30 | PF2e accomplishment XP |
| Site cleared (major) | 80 | PF2e accomplishment XP |
| Quest completed (small) | 30 | PF2e accomplishment XP; house-rules line 129 uses 10–30 for the *kingdom* equivalent |
| Quest completed (major) | 80 | PF2e accomplishment XP |
| Expedition resolved | 30 | PF2e accomplishment XP, moderate |
| RP encounter | 30 | PF2e accomplishment XP, moderate |
| PC level threshold | 1200 XP | house-rules.md line 26 |

> **Needs Gregory's sign-off before Phase 2.** These defaults are the PF2e baseline, not a recorded
> house rule, and the card's premise ("award sizes from house rules") is only satisfiable for the
> kingdom-side numbers. If there is a table in use at the table that is not written down, it should
> land in `docs/house-rules.md` and this table should cite it instead.

Every default is **editable at confirm time** — the confirm row carries an amount field pre-filled
with the default. The ledger records what was actually granted, not what was proposed.

**Amount is in scope.** An earlier draft placed "recalculating the amount" out of scope and said the
ledger only records results. That guts the feature: hex clears and quest completions have no existing
automated calculation whose result there would be to record — proposing the number *is* the work.

## 4. Data model

The ledger is about the party, so it lives on the **party actor** — the same actor
`getCampingActors()` returns — as a module flag via `setAppFlag` / `getAppFlag`
(`utils/Document.kt:84` / `:91`).

**Consequence: no `Migration<N>`.** Module migrations run over kingdom data; this is an actor flag,
and an absent flag reads as an empty ledger. There is nothing to back-fill and nothing to migrate.

```kotlin
// jsMain: kingdom/xp/RawXpLedgerEntry.kt
@JsPlainObject
external interface RawXpLedgerEntry {
    var id: String                 // uuid, stable across status changes
    var turn: Int                  // kingdom turn the beat happened on
    var timestamp: String          // ISO string, matching RawTurnRecord.timestamp
    var sourceKind: String         // XpSourceKind.value
    var sourceRef: String          // hex key, quest id, expedition id -- the double-count key
    var proposedAmount: Int
    var grantedAmount: Int?        // what the GM actually confirmed; null while offered
    var status: String             // offered | confirmed | dismissed
    var note: String?
}
```

`turn: Int` + `timestamp: String` rather than a `Date`: these are `@JsPlainObject` interfaces
serialized into a Foundry flag, and `RawTurnRecord` already stores time exactly this way.

**Prune and cap.** `XP_LEDGER_CAP = 500` confirmed-or-dismissed entries, oldest trimmed first, in the
same shape as `appendShipmentHistory` (`commonMain .../kingdom/ShipmentHistory.kt`). **Offered entries are never
pruned** — an unanswered offer is pending work, and silently dropping it loses XP the party earned.
If offers alone ever exceed the cap that is a bug in the offer generator, not a pruning problem.

## 5. Engine design

`src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/xp/XpLedger.kt` — pure.

```kotlin
enum class XpSourceKind(val value: String) {
    HEX_RECONNOITERED("hexReconnoitered"), SITE_CLEARED("siteCleared"),
    QUEST_COMPLETED("questCompleted"), EXPEDITION_RESOLVED("expeditionResolved"),
    RP_ENCOUNTER("rpEncounter"), MANUAL("manual");
}

enum class XpOfferStatus { OFFERED, CONFIRMED, DISMISSED }

data class XpLedgerEntry(/* model mirror of the Raw interface */)

/**
 * Propose an entry, unless this exact beat is already in the ledger.
 *
 * The guard is (sourceKind, sourceRef): a hex cleared, re-populated by the GM and cleared again
 * reports the same key, and a quest reopened and re-completed likewise. Awarding twice for one
 * sourceRef is the failure this returns null for. A GM who genuinely wants a second award adds a
 * MANUAL entry, which is deliberate and shows up as such in the history.
 */
fun proposeEntry(existing: List<XpLedgerEntry>, candidate: XpLedgerEntry): XpLedgerEntry?

/** Append with the cap applied to answered entries only. */
fun appendEntry(existing: List<XpLedgerEntry>, entry: XpLedgerEntry, cap: Int = XP_LEDGER_CAP): List<XpLedgerEntry>

/** Sum of grantedAmount over confirmed entries. */
fun confirmedTotal(entries: List<XpLedgerEntry>): Int

/** Per-source-kind totals for the ledger header. */
fun totalsByKind(entries: List<XpLedgerEntry>): Map<XpSourceKind, Int>

/**
 * Reconciliation: what the ledger says the party was granted, against what a PC actually holds.
 * Drift is expected and not an error -- combat XP and hand edits never pass through the ledger --
 * so this reports a number, never a correction.
 */
data class XpReconciliation(val ledgerTotal: Int, val actualLifetimeXp: Int, val drift: Int)
fun reconcile(entries: List<XpLedgerEntry>, actualLifetimeXp: Int): XpReconciliation
```

**Tick surface.** Offers are produced at the transitions themselves (hex content, quest completion,
expedition resolution) — these are already-observed events, not a tick. The **digest** is posted from
the existing End Turn path, the monthly `TurnTickingEngine` side. No `DailyTickHooks` involvement and
no third tick.

## 6. Offer UX: digest, not per-event cards

Per-event cards would post one chat card per hex the party clears — a dozen in an exploration
session. So: entries are created `OFFERED` silently as they happen, and **one digest card** is
posted at End Turn listing every pending offer.

`chatmessages/xp-ledger-digest.hbs` with, per row, the source, the proposed amount and an editable
amount field, plus **Confirm** / **Dismiss**; and card-level **Confirm all** / **Dismiss all**.

New buttons in `ChatButtons.kt`: `km-offer-xp-confirm`, `km-offer-xp-dismiss`,
`km-offer-xp-confirm-all`, `km-offer-xp-dismiss-all`. Confirm calls `updateXP` with the row's
(possibly edited) amount over the party's characters, then writes `grantedAmount` and
`status = confirmed`.

## 7. UI

The ledger goes in the **existing `MainNavEntry.PARTY` tab** — no new tab. It is the party's XP; the
party tab is where it belongs, and a fifteenth nav entry for a history list is not worth the slot.

| Piece | Path |
| --- | --- |
| Template | `applications/kingdom/sections/party/xp-ledger.hbs` |
| Context | `kingdom/sheet/contexts/XpLedgerContext.kt` |
| Styles | `.km-xp-ledger*` in `applications/kingdom/kingdom-sheet.css` |
| i18n | `pf2e-kingmaker-tools.kingdom.xpLedger.*` |

Header: confirmed total, per-source-kind totals, and the reconciliation line
("ledger 4,310 · party 4,560 · +250 outside the ledger"), phrased as information rather than a
warning, because combat XP legitimately never passes through here.

**Player-visible, GM-writable.** Players see the history — it is their XP. But the Confirm and
Dismiss controls grant XP, so:

- The controls render inside `{{#if isGM}}`, **and**
- every handler begins `if (!game.user.isGM) return`.

The template conditional is layout only. Players are OWNERs of the party actor and can call handlers
directly, so the handler check is the actual authorization — this module has shipped that bug before
(embedded-document writes throwing "User lacks permission" for players).

**Two repo constraints.** `xp-ledger.hbs` is a registered partial with no parent frame: inside
`{{#each}}` reach outer values with `@root`, never `../`. And row labels must be **literal** i18n
keys — `t("xpLedger.kind.$sourceKind")` is invisible to `check_i18n_keys.py` and would ship as a raw
key with every guard green; map the enum to constants in a `when` instead.

## 8. Interactions and out of scope

**Reads/hooks:** `dialogs/HexContentManager.kt` (cleared transitions), the quest completion flow,
`ExpeditionResolution.kt`, End Turn in `TurnWizardApplication.kt` (digest posting).
**Writes:** the party-actor flag, and `updateXP` on confirm — nothing else.

**Out of scope:** kingdom XP (owned by `sheet/Xp.kt` and `MilestoneOffers.kt`); combat XP (PF2e
already awards it, and routing it through here would double-count); retroactive back-fill of awards
made before the ledger existed; per-PC differential awards (`updateXP` awards the whole party
equally, and splitting it is a separate feature); auto-confirming anything.

## 9. Test plan

**commonTest** (`XpLedgerTest`)
- `proposeEntry` returns null for a repeat `(sourceKind, sourceRef)` — the hex-cleared-twice case —
  and non-null for the same ref under a different kind.
- A `MANUAL` entry with a duplicate ref is allowed (the deliberate override).
- `appendEntry` trims the oldest *answered* entry at the cap and **never** trims an `OFFERED` one,
  even when offers alone exceed the cap.
- `confirmedTotal` counts `grantedAmount`, not `proposedAmount` — an edited-down confirm must not
  report the proposed figure.
- `totalsByKind` omits dismissed entries.
- `reconcile` reports positive drift when the party holds more than the ledger granted, and zero when
  they match; it never mutates.

**jsTest** — Raw↔model round trip preserving `grantedAmount = null`; an unknown stored `status`
dropped rather than thrown (newer build wrote it); digest context built for a multi-row turn;
`XpLedgerContext` renders for a player while the control block does not.

**Mutation-check every new test**: flip the duplicate guard to always-allow, trim offered entries,
sum `proposedAmount` in `confirmedTotal` — and confirm the mutation *compiled* before believing a
"survived" result.

**Manual Foundry checklist**
1. Clear a hex → no chat spam; End Turn → one digest card listing it.
2. Edit the amount on a row before confirming → the ledger shows the edited figure, and the PCs gain
   exactly that.
3. Confirm a row → each PC's XP rises by the amount; at 1200 they level; the row moves to confirmed.
4. Re-clear the same hex → no second offer for it.
5. Dismiss a row → it never re-offers.
6. Award combat XP normally → the reconciliation line shows drift and calls it "outside the ledger",
   not an error.
7. Log in as a player → the history renders; no Confirm or Dismiss controls anywhere.
8. Confirm that a hex-clear award raised **PC** XP and left `KingdomData.xp` untouched.

## 10. Phasing

**Phase 1 — pure ledger.** `XpLedger.kt`, the enums, `proposeEntry` / `appendEntry` /
`confirmedTotal` / `totalsByKind` / `reconcile`, full commonTest suite. Nothing wired.

**Phase 2 — storage and offer generation.** Party-actor flag read/write; hook the hex, quest and
expedition transitions to create `OFFERED` entries. Still no UI, no chat. **Blocked on sign-off for
the §3 table.**

**Phase 3 — digest and confirm.** `xp-ledger-digest.hbs`, the four buttons, `updateXP` on confirm,
i18n across all eight locales (one locale short and CI goes red; only `check_i18n_keys.py --all`
catches it).

**Phase 4 — Party-tab panel.** Template, context, CSS, reconciliation header, GM-gated controls.

Phase 1 ships nothing user-visible, which is what makes the rest safe.

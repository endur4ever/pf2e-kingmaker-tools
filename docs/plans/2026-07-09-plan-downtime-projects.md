# Plan: PC downtime project ledger on the world clock (craft / retrain / earn income / rituals)

Card: `t_6acfa8bb`. Mirrors the shipped companion-expedition daily tick.

## 1. Problem statement

Kingmaker hands the party months between kingdom turns, and this module's own systems make those
months matter: the settlement benefit tracker gates trainers and crafting on structures, and the
house rules make crafting a camping activity. What none of it tracks is the project itself — a
30-day craft, a retrain, a week-long ritual — which lives in the GM's notebook and gets forgotten.

The companion-expedition system already solves this exact shape: a per-entity record with
`daysRemaining` ticked by the world clock, a completion offer, and a status field. This plan mirrors
it for PCs.

**Value.** "Valeros started a +1 striking longsword on the 3rd, it needs 30 days at the Tuskwater
smithy, and it's done in 6" stops being something anyone has to remember.

## 2. The bug this plan must not ship: the clock jumps

The world clock does **not** advance one day at a time, and downtime is precisely when it advances in
large jumps.

`DailyTickHooks.kt:50` hooks `onUpdateWorldTime` and computes
`daysCrossed(worldTime, deltaInSeconds)` — the number of **day boundaries crossed** — then passes
that count onward: `tickCompanionExpeditions(game, daysPassed)`.

So a tick that decrements `daysRemaining` by one per invocation loses time. Advance a week for
downtime and a 30-day craft burns 1 day, not 7. An earlier draft of this plan specified exactly
that ("Every time the world clock advances one day … decrement `daysRemaining`").

The pure helper that gets this right already exists and is the thing to reuse:

```kotlin
// DailyTickEngine.kt
fun tickExpedition(daysRemaining: Int, days: Int = 1): ExpeditionTickResult {
    val elapsed = days.coerceAtLeast(1)
    val next = daysRemaining - elapsed
    return if (next <= 0) ExpeditionTickResult(newDaysRemaining = 0, completed = daysRemaining > 0)
    else ExpeditionTickResult(newDaysRemaining = next, completed = false)
}
```

Note `completed = daysRemaining > 0`: a project already at 0 does not re-complete when the clock
moves again. Downtime projects get the identical helper — a new `tickDowntimeProject` would be the
same four lines with a different name, so **reuse `tickExpedition` rather than copy it**.

A related trap, documented during the forecast plan: `DailyTickHooks.kt` is the impure wrapper
(6 `suspend`, 5 `postChat`, 5 persisting calls). The decrement logic belongs in the pure
`DailyTickEngine`; the hook only orchestrates.

## 3. Position: when does a project *not* tick?

The card asks for a position on pausing when the PC leaves to adventure. Here it is.

**No adventuring auto-pause, because the tick surface already approximates it.** Projects tick only
on `daysCrossed >= 1`. A session of combat and exploration advances the clock in minutes and hours
and crosses no day boundary, so it ticks nothing. The clock crosses days when the GM deliberately
advances days — which is what downtime *is*.

**Position-based inference is rejected.** There is no reliable "this PC is adventuring today" signal:
`getCampingActors()` (`CampingData.kt:722`) returns party actors that carry camping data, which is a
roster, not a per-day state, and a token's scene says nothing about downtime. Inferring it would
silently mis-tick a 30-day craft, and silently wrong is worse than asking.

**So: two pause paths, both explicit.**

1. **Manual pause/resume** per project, for the case the proxy misses — the PC leaves on a multi-day
   trek while the rest of the party stays home and the GM advances a week.
2. **Automatic pause on lost prerequisite** — the hosting settlement or its required structure is
   gone (razed in a siege). The project pauses; it never silently completes somewhere that no longer
   has a smithy.

A paused project's `daysRemaining` is frozen, never decremented and never lost.

## 4. Data model

Projects mirror companion expeditions, so they persist the same way: an array on `KingdomData`,
nullable for migration safety, exactly like `var companionExpeditions: Array<RawCompanionExpedition>?`
(`KingdomData.kt:354`).

```kotlin
// jsMain: kingdom/data/RawPcDowntimeProject.kt
@JsPlainObject
external interface RawPcDowntimeProject {
    var id: String
    var pcActorUuid: String
    /** craft | retrain | earnIncome | ritual -- a STRING, see below. */
    var kind: String
    /** Item uuid, feat name, or ritual name, by kind. */
    var targetRef: String
    /** Display label captured at creation, so the row still reads if the target is deleted. */
    var title: String
    var settlementId: String?
    var daysTotal: Int
    var daysRemaining: Int
    /** Gold per day, if the kind charges one. */
    var dailyCostGp: Double?
    /** inProgress | paused | completed */
    var status: String
    /** Why it paused, for the row's tooltip; null unless paused. */
    var pauseReason: String?
}
```

**Strings, not enums, and no `Map`.** These are `@JsPlainObject` interfaces serialized into Foundry
flags. `RawCompanionExpedition.status` is `var status: String` for this reason, and the codebase
compares it as `it.status == "inProgress"` (`DailyTickHooks.kt:239`). An earlier draft typed `kind`
and `status` as Kotlin enums and added `accruedRewards: Map<String, Int>`; neither survives
serialization. Enums live in `commonMain` and convert at the boundary with `fromCamelCase`, and
rewards are computed at completion rather than accrued into a map.

**Storage decision: `KingdomData`, not an actor flag or a `DowntimeRegistry`.** Projects are gated on
settlements and read alongside them, and the card asked this to mirror companion expeditions, which
already live there. (Contrast the party XP ledger, which is per-PC and correctly lives on the party
actor — the deciding question is what the record is *about*.)

**`Migration62`** seeds `downtimeProjects` to `emptyArray()`. 61 is the highest registered
(`Migrations.kt`), so 62 is next; register it there and extend `MigrationChainTest`'s hardcoded range.

## 5. Kind catalog v1

The card asks for per-kind completion semantics and which PF2e rolls happen per-day versus at
completion. **No kind rolls per day** — a per-day roll would fire N times on a clock jump and cannot
be previewed. Every roll happens once, at confirm time, made by the GM or the PC in the normal PF2e
way.

| Kind | Prerequisite structure | `daysTotal` from | At completion | Roll |
| --- | --- | --- | --- | --- |
| `craft` | Smithy / Magic Shop / equivalent, by item type | GM enters; PF2e default is 4 days setup + days bought down | Offer names the item and links its uuid; GM grants it | Crafting check, at confirm, by the PC |
| `retrain` | Library / Academy / trainer structure | GM enters (PF2e: days to weeks) | Offer names the feat being swapped | None; GM edits the sheet |
| `earnIncome` | Any settlement with a relevant structure | GM enters | Offer shows days x level task; GM applies gp | Earn Income check, at confirm, by the PC |
| `ritual` | Shrine / Temple / Cathedral | Ritual's own casting time | Offer names the ritual | Ritual's own checks, at confirm |

**Prerequisites are validated against `Settlement.constructedStructures`**
(`data/kingdom/settlements/Settlement.kt:13`) — the same list the Cleanse Item structure gate reads.
**Not** via `InspectSettlement.kt`: that is a `FormApp` dialog (`InspectSettlement.kt:178`), a UI
class, and calling a dialog to answer a data question is the wrong seam.

```kotlin
// commonMain: kingdom/downtime/DowntimePrereq.kt
/** Structure names that satisfy each kind, ascending; a better structure satisfies a lesser need. */
fun structuresFor(kind: DowntimeKind): List<String>
fun prerequisiteMet(kind: DowntimeKind, structureNames: Set<String>): Boolean
```

**Out of scope, per the card:** adjudicating crafting or retraining *rules*. The ledger tracks time
and prerequisites; the GM decides whether the craft succeeded and what it produced.

## 6. Engine design

`src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/downtime/DowntimeProjects.kt` — pure.

```kotlin
enum class DowntimeKind(val value: String) { CRAFT("craft"), RETRAIN("retrain"),
    EARN_INCOME("earnIncome"), RITUAL("ritual") }
enum class DowntimeStatus(val value: String) { IN_PROGRESS("inProgress"), PAUSED("paused"),
    COMPLETED("completed") }

data class DowntimeProject(/* model mirror */)
data class DowntimeTickOutcome(val projects: List<DowntimeProject>, val completed: List<DowntimeProject>)

/**
 * Advance every in-progress project by [days]. Paused and completed projects are returned
 * unchanged -- a paused project must not lose days it was not working.
 *
 * [stillEligible] answers "does this project's settlement still have its structure"; a project that
 * fails it pauses rather than ticking, so a razed smithy stops the craft instead of completing it.
 */
fun tickDowntimeProjects(
    projects: List<DowntimeProject>,
    days: Int,
    stillEligible: (DowntimeProject) -> Boolean,
): DowntimeTickOutcome
```

`tickDowntimeProjects` delegates each per-project decrement to `DailyTickEngine.tickExpedition`,
which is where the multi-day arithmetic and the no-re-complete rule already live.

**Tick surface: `DailyTickHooks`, the world-clock daily side**, beside `tickCompanionExpeditions`.
Not `TurnTickingEngine` — a craft finishes on the day it finishes, not at End Turn — and no third
tick.

## 7. UI placement: camping sheet, not kingdom sheet

The card asks for an argument rather than a pick. **Camping sheet.**

Downtime projects belong to PCs, advance on the world clock, and are started and reviewed in the same
breath as travel and resting — all camping-sheet concerns. The kingdom sheet is the kingdom's ledger,
read at End Turn on a monthly cadence; a per-PC daily record there sits on the wrong clock and in
front of the wrong audience.

The one pull the other way is that prerequisites are settlement structures, which are kingdom data.
That is a read, not a home: the creation dialog reads settlements the same way the Cleanse Item
dialog does, without living on the kingdom sheet.

| Piece | Path |
| --- | --- |
| Section template | `applications/camping/downtime-projects.hbs` |
| Creation dialog | `camping/dialogs/AddDowntimeProject.kt` + `applications/camping/add-downtime-project.hbs` |
| Context | `camping/contexts/DowntimeProjectContext.kt` |
| Styles | `.km-downtime*` in `applications/camping/camping.css` |
| i18n | `pf2e-kingmaker-tools.camping.downtime.*` |

**Layout caution.** The camping sheet's time-tracker bar positions its children absolutely, and
`.km-camping-activities-wrapper` is `position: absolute` and shrink-to-fit, so its widest child sets
every sibling's width. A new section must go in the scrolling content wrapper, and its geometry must
be **measured in headless Chrome before and after** — this sheet has been broken this way before.

Row labels use **literal** i18n keys mapped from the enum in a `when`; `t("downtime.kind.$kind")` is
invisible to `check_i18n_keys.py` and ships as a raw key with every guard green. And the section
template is a registered partial with no parent frame: inside `{{#each}}` use `@root`, never `../`.

## 8. Offers

One button, `km-offer-downtime-complete`, on `chatmessages/downtime-complete.hbs`, whispered to the
GM when a project completes.

The card shows PC, kind, target (as a `@UUID[]` link for a craft) and days taken. **Confirm** marks
it `completed` and prompts the roll named in §5; **Dismiss** returns it to `inProgress` with one day
remaining, for the GM who wants it to run longer. There is no separate "cleanup" button — a
confirmed project stays in the list as history and is pruned by the cap.

`DOWNTIME_HISTORY_CAP = 100` completed projects, oldest first, in the shape of
`appendShipmentHistory`. In-progress and paused projects are **never** pruned.

Every handler begins `if (!game.user.isGM) return`. Players are OWNERs of the party actor, so a
template conditional is layout, not authorization.

## 9. Interactions and out of scope

**Reads:** `Settlement.constructedStructures` (prerequisites), `DailyTickEngine.tickExpedition`,
`DailyTickHooks` (registration), camping sheet render.
**Writes:** `kingdom.downtimeProjects`, and only from the hook and the dialog.

**Out of scope:** crafting/retraining rules adjudication; the full Earn Income table (the GM applies
gp; the ledger tracks days and the task level); auto-granting crafted items; per-day rolls of any
kind; projects for NPCs or companions (companions have expeditions already); multi-PC shared
projects.

## 10. Test plan

**commonTest** (`DowntimeProjectsTest`)
- A 7-day clock jump advances a 30-day project to 23 — **the multi-day case, which is the bug this
  plan exists to avoid**; and a 1-day jump advances it to 29.
- A jump larger than `daysRemaining` completes it exactly once and clamps at 0; ticking again
  reports no new completion.
- A `paused` project is returned unchanged by any jump size and loses no days.
- A project failing `stillEligible` pauses instead of ticking, and carries a `pauseReason`.
- A resumed project continues from its frozen `daysRemaining`.
- `prerequisiteMet`: a Cathedral satisfies a ritual needing a Shrine; a Tavern satisfies nothing;
  matching ignores case and padding.
- The cap trims completed projects only, never in-progress or paused ones.

**jsTest** — Raw↔model round trip preserving `dailyCostGp = null` and `pauseReason = null`; an
unknown stored `kind` or `status` dropped rather than thrown; `Migration62` seeding an empty array
and being idempotent on a second run.

**Mutation-check every new test**: change `days.coerceAtLeast(1)` to a literal 1, tick paused
projects, flip `completed = daysRemaining > 0` to `>= 0` — and confirm the mutation *compiled*
before believing a "survived" result.

**Manual Foundry checklist**
1. Start a 10-day craft at a settlement with a smithy; advance the clock **7 days in one step** →
   3 days remain, not 9.
2. Advance 5 more → one completion offer, once. Advance again → no second offer.
3. Raze the hosting settlement mid-project → the project pauses with a reason and stops decrementing.
4. Manually pause a project, advance a week, resume → it lost no days.
5. Try to start a retrain at a settlement with no library → the dialog refuses and says which
   structure is missing.
6. Confirm a completed craft → the offer links the item; the GM grants it; the row moves to history.
7. Open the camping sheet before and after adding the section → **measure** that the rest-button row
   and the activity wrapper have not moved.
8. Log in as a player → the list renders; no confirm controls.

## 11. Phasing

**Phase 1 — pure core.** `DowntimeProjects.kt`, the enums, `tickDowntimeProjects` delegating to
`tickExpedition`, `DowntimePrereq.kt`, full commonTest suite. Nothing wired.

**Phase 2 — data and tick.** `RawPcDowntimeProject`, `kingdom.downtimeProjects`, `Migration62` plus
`MigrationChainTest`, and the `DailyTickHooks` registration beside `tickCompanionExpeditions`.
Ticking works with no UI.

**Phase 3 — creation dialog.** `AddDowntimeProject`, prerequisite validation against
`constructedStructures`, i18n across all eight locales (one short and CI goes red; only
`check_i18n_keys.py --all` catches it).

**Phase 4 — camping section and offers.** The section, its context and CSS with a measured layout
pass, `downtime-complete.hbs`, and the confirm button.

Phases 1 and 2 ship no user-visible change, which is what makes 3 and 4 safe.

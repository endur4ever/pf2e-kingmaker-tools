# Plan: Deeds Chronicle — auto-detected achievements with milestone XP offers

Card: `t_633374be`. Sibling prior art: `gap0709-auto-milestones`, already shipped as
`src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/MilestoneOffers.kt`.

## 1. Problem statement

`docs/house-rules.md` ("Additional Milestones") records that the players asked for more milestone
rewards. Two of those are already automatic: `MilestoneOffers.kt` detects
`connect-settlement-to-capital-via-roads` and `claim-all-hexes-in-a-region` at End Turn and whispers
the GM a confirm-or-dismiss award card. Every other milestone in `data/milestones/` is still noticed
by a human or not at all — which in practice means not at all, because the moment a kingdom earns one
is buried in a turn the table is already halfway through.

The second half of the loss is that nothing keeps the record. A milestone awarded on turn 14 leaves a
checkbox and an XP number; it does not leave "on turn 14 the road from Tuskwater reached the
capital". A Kingmaker campaign runs for a hundred sessions and ends with no history of itself.

**Value.** The GM stops policing a checklist, and the table gets a readable account of what the
kingdom did, in order, with dates — the thing players actually retell between sessions.

**This plan generalises the two shipped detectors; it does not build a second system beside them.**

## 2. What already exists (read before designing anything)

| Piece | File | What it already does |
| --- | --- | --- |
| Catalog entry | `data/milestones/*.json`, one file per milestone | `id`, `name` (i18n key), `xp`, `enabledOnFirstRun`, `isCultMilestone` |
| Catalog schema | `src/commonMain/resources/schemas/milestone.json` | validated by `./gradlew check` (`validateMilestones`) |
| Bundled catalog | generated `milestones.json`, imported by `KingdomMilestone.kt` as `Array<RawMilestone>` | build artefact — **never hand-edited** |
| Per-kingdom state | `MilestoneChoice` on `kingdom.milestones` | `id`, `completed`, `enabled`, `offerDismissed?` |
| Answered rule | `milestoneOfferAnswered(completed, offerDismissed)` (commonMain, tested) | `completed \|\| offerDismissed == true` |
| Detection + offer | `detectAndOfferMilestones(game, actor, kingdom)` | called at `TurnWizardApplication.kt:696` |
| Offer card | `chatmessages/milestone-offer.hbs` | award / dismiss buttons |
| Offer button | `ChatButton("km-offer-milestone")` in `ChatButtons.kt:1141` | applies XP, or records the dismissal |

Three consequences fall straight out of this table and shape everything below.

**A detector cannot be self-idempotent.** `MilestoneOffers.kt` documents why in the code: both
shipped detectors are *level*-triggered on standing world state — a road stays built, a claimed
region stays claimed — so a predicate over `(history, kingdom)` re-fires every End Turn forever. It
has no way to know it fired last turn, because the state it reads is identical. Idempotence is not a
property of the predicate; it is the caller suppressing deeds whose offer has been **answered**.

**Answered, not awarded.** Suppressing on `completed` alone re-posted the identical card every turn
to any GM who declined the house rule, with no way to stop it. That is why `offerDismissed` exists.
Any new detector inherits this rule for free — and only by reusing this state.

**There is therefore no new store and no migration.** `MilestoneChoice` already persists exactly the
per-kingdom, per-milestone answer a deed needs. A separate `firedDeeds` set would be a third place
recording the same fact, and would need a migration to stay consistent with the two that already
exist. The card's own recommendation ("deeds ARE milestone JSONs with a detection field; one system,
no parallel store") is correct, and it is correct *because* of `offerDismissed`.

## 3. Data model

### 3.1 Catalog: one new optional field

`src/commonMain/resources/schemas/milestone.json` gains `detectionId`, optional, not added to
`required` — every existing milestone file stays valid unchanged.

```json
{
  "id": "build-roads-for-the-first-time",
  "name": "milestones.build-roads-for-the-first-time.name",
  "xp": 20,
  "enabledOnFirstRun": false,
  "isCultMilestone": false,
  "detectionId": "first-road-built"
}
```

`RawMilestone` (`KingdomMilestone.kt`) gains `var detectionId: String?`. Nullable, so the bundled
catalog deserialises with or without it. A milestone with no `detectionId` is exactly what it is
today: manually ticked.

The two shipped detectors get retrofitted into this shape rather than kept as constants —
`connect-settlement-to-capital-via-roads` gets `"detectionId": "road-to-capital"` and
`claim-all-hexes-in-a-region` gets `"detectionId": "region-fully-claimed"`, and
`MILESTONE_ROAD_TO_CAPITAL` / `MILESTONE_REGION_CLAIMED` are deleted. This is the step that makes
this a generalisation instead of an addition.

### 3.2 Per-kingdom state: nothing new

No new field. No `Migration<N>`. Idempotence, award and refusal all continue to live on
`MilestoneChoice`.

> The only migration this feature could need is one that back-fills `offerDismissed` — and
> `MilestoneOfferState.kt` already handles absence by reading it as "not dismissed", so there is
> nothing to back-fill. Adding a migration here would be work that buys nothing.

### 3.3 Chronicle entries: derived, not stored

A Chronicle line is `(turn, milestoneId, xp)`. `RawTurnRecord` already carries `turn`, `timestamp`
and `xpAwarded`; the award path already knows the milestone. Rather than persist a fourth copy, the
award button records the turn on the choice:

```kotlin
// MilestoneChoice
/** Kingdom turn this milestone was awarded on; null for milestones ticked by hand before this
 *  existed, which the Chronicle renders without a turn rather than inventing one. */
var awardedOnTurn: Int?
```

This is a nullable additive field on an existing interface, so it needs no migration either: absent
means "awarded at an unknown time", which is the truth for every historical tick.

## 4. Engine design

### 4.1 Pure core (`commonMain`)

`src/commonMain/kotlin/at/posselt/pfrpg2e/kingdom/deeds/DeedDetection.kt`

```kotlin
/**
 * A snapshot of everything a detector may read. Assembled in jsMain from live state; pure here so
 * every detector is unit-testable without Foundry.
 */
data class DeedInputs(
    val turn: Int,
    val history: List<TurnSnapshot>,     // oldest first, from RawTurnRecord
    val level: Int,
    val size: Int,
    val unrest: Int,
    val fame: Int,
    val ruin: RuinSnapshot,
    val settlements: List<SettlementSnapshot>,   // size is SettlementSizeType, NOT SettlementType
    val maximumFamePoints: Int,                  // kingdom.settings.maximumFamePoints; not a constant
    val claimedHexCount: Int,
    val roadHexCount: Int,
    val regionsFullyClaimed: Int,
    val settlementsRoadedToCapital: Int,
    val armiesWon: Int,
    val consecutiveSafeCaravanTurns: Int,
    val tradeAgreements: Int,
)

/**
 * Detectors are LEVEL-triggered: they answer "is this true now", never "did this just become true".
 * A road stays built and a claimed region stays claimed, so a detector re-fires every turn for the
 * rest of the campaign. That is intended and must stay intended -- suppression is the caller's job
 * (see [undetectedDeeds]), because only the caller knows whether the GM already answered the offer.
 *
 * Detectors MUST be free of side effects and of Date/Random: End Turn runs them twice, once for the
 * Turn Wizard preview and once on commit, and the two must agree.
 */
fun interface DeedDetector {
    fun firesFor(inputs: DeedInputs): Boolean
}

/** detectionId -> detector. The single place a new deed is wired to its catalog entry. */
val deedDetectors: Map<String, DeedDetector>

/**
 * The deeds to offer this turn: catalog entries that carry a known detectionId, whose detector
 * fires, and whose offer the GM has not already answered either way.
 */
fun undetectedDeeds(
    catalog: List<DeedCatalogEntry>,   // id + detectionId, from RawMilestone
    answeredIds: Set<String>,          // milestoneOfferAnswered over kingdom.milestones
    inputs: DeedInputs,
): List<String>
```

`undetectedDeeds` is the whole idempotence story, it is four lines, and it is pure — so the rule that
was previously an inline comment in `MilestoneOffers.kt` becomes a unit-tested function.

An unknown `detectionId` (catalog newer than the build) is **skipped, never thrown** — the same
posture `RawShipmentHistoryEntry.toModel()` takes for an unknown outcome. One unrecognised entry must
not take down every other deed on End Turn.

### 4.2 jsMain adapter

`MilestoneOffers.kt` is rewritten to: build `DeedInputs` from live state, call `undetectedDeeds`,
post one card per fired deed. Its existing hex-topology reads (`getRoadHexKeys`,
`roadConnectedToCapital`, `regionFullyClaimed`, `extractRegionData`) become the two corresponding
detectors' inputs, unchanged and still `runCatching`-guarded.

### 4.3 Tick surface

End Turn only, at the existing `TurnWizardApplication.kt:696` call site — the monthly
`TurnTickingEngine` side of the split. **No `DailyTickHooks` involvement and no third tick.** Deeds
are kingdom-scale facts that only change when a turn resolves, and the Turn Wizard preview already
runs this path read-only, which the detectors' purity requirement preserves.

## 5. Starter deed catalog

XP uses values already present in `data/milestones/`, whose current distribution is
20 (15 entries), 40 (7), 50 (1), 60 (8), 80 (8), 120 (2), 200 (1). Deeds stay in the 20/40/80 band —
the higher values are reserved for the adventure-path beats already in the catalog, and a deed that
fires automatically should not outweigh one the GM awarded on purpose.
"Inputs" names the `DeedInputs` field the detector reads, so each row is checkable against §4.1.

| # | detectionId | Deed | Inputs | XP |
| --- | --- | --- | --- | --- |
| 1 | `first-settlement` | The first village is founded | `settlements.isNotEmpty()` | 20 |
| 2 | `first-town` | A village grows into a town | `settlements.any { it.size >= TOWN }` | 20 |
| 3 | `first-city` | A town grows into a city | `settlements.any { it.size >= CITY }` | 40 |
| 4 | `first-metropolis` | A city becomes a metropolis | `settlements.any { it.size >= METROPOLIS }` | 80 |
| 5 | `road-to-capital` | A settlement is linked to the capital by road | `settlementsRoadedToCapital >= 1` | 20 |
| 6 | `all-settlements-roaded` | Every settlement is linked to the capital | `settlementsRoadedToCapital == settlements.size` (size ≥ 3) | 40 |
| 7 | `region-fully-claimed` | Every hex of a region flies the kingdom's banner | `regionsFullyClaimed >= 1` | 40 |
| 8 | `two-regions-claimed` | A second region is wholly claimed | `regionsFullyClaimed >= 2` | 40 |
| 9 | `size-25` | The realm reaches size 25 | `size >= 25` | 20 |
| 10 | `size-50` | The realm reaches size 50 | `size >= 50` | 40 |
| 11 | `size-100` | The realm reaches size 100 | `size >= 100` | 80 |
| 12 | `level-10` | The kingdom reaches level 10 | `level >= 10` | 40 |
| 13 | `level-20` | The kingdom reaches level 20 | `level >= 20` | 80 |
| 14 | `fame-max` | Fame reaches its cap | `fame >= maximumFamePoints` | 20 |
| 15 | `ruin-free` | No ruin of any kind, having once had some | `ruin.all == 0` and any earlier `history` entry had ruin | 40 |
| 16 | `unrest-zero-after-crisis` | Unrest returns to zero after reaching 10+ | `unrest == 0` and any earlier `history` entry ≥ 10 | 40 |
| 17 | `ten-quiet-turns` | Ten consecutive turns without unrest rising | ten-window scan of `history` | 40 |
| 18 | `safe-trade-route` | A caravan route survives five straight turns | `consecutiveSafeCaravanTurns >= 5` | 20 |
| 19 | `first-battle-won` | The kingdom's armies win their first battle | `armiesWon >= 1` | 20 |
| 20 | `three-trade-agreements` | Three trade agreements stand at once | `tradeAgreements >= 3` | 40 |
| 21 | `survived-fifty-turns` | Fifty turns of rule | `turn >= 50` | 80 |

Rows 5 and 7 are the two already shipped, restated in the new shape.

`SettlementSnapshot.size` is `SettlementSizeType` (`VILLAGE`, `TOWN`, `CITY`, `METROPOLIS`).
`SettlementType` is a different enum with only `SETTLEMENT` and `CAPITAL` — a detector that reaches
for `TOWN` on it will not compile.

**Deliberately excluded:** anything needing per-PC attribution (that is the renown card,
`t_26708d31`), and anything whose only evidence is free-text `notes`, which is GM prose and cannot be
detected on.

## 6. UI — the Chronicle

**Where.** A section on the existing `MainNavEntry.SESSION_PREP` tab, not a new tab. Session Prep is
where the GM already reads back what happened; a fifteenth top-level tab for a read-only list is not
worth its own nav slot. If it outgrows the section, promoting it later is a one-line enum change.

| Piece | Path |
| --- | --- |
| Template | `src/jsMain/resources/applications/kingdom/sections/session-prep/chronicle.hbs` |
| Row context | `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/contexts/ChronicleRowContext.kt` |
| Styles | `.km-chronicle*` appended to `applications/kingdom/kingdom-sheet.css` |
| i18n | `pf2e-kingmaker-tools.kingdom.chronicle.*` |

`ChronicleRowContext`: `turn: Int?`, `turnLabel: String`, `name: String`, `xp: Int`, `isDeed: Boolean`.
Rows are built from `kingdom.milestones.filter { it.completed }`, newest first, joined to the catalog
for `name`/`xp` and reading `awardedOnTurn`.

Two constraints inherited from this repo, both of which have bitten before:

- **`chronicle.hbs` is a registered partial, so it has no parent frame.** Inside `{{#each}}` reach
  outer values with `@root`, never `../`. `scripts/check_hbs_scope.py` only catches chains that climb
  too far, never one that climbs too few.
- **Every row label must be a literal i18n key.** A key assembled at runtime — `t("chronicle.$id")` —
  is invisible to `check_i18n_keys.py`, which scans for literals, and ships as a raw key on screen
  with every guard green. Milestone names are already i18n keys stored in the catalog, so the
  Chronicle renders `milestone.name` and assembles nothing.

Player-visible: the section is read-only and carries no GM-only data, so it renders for players too.

## 7. Chat and offer surfaces

**No new offer card and no new button.** A deed reuses `chatmessages/milestone-offer.hbs` and
`ChatButton("km-offer-milestone")`, which already award XP or record the dismissal.

**Volume control.** Turn 1 of an adopted mid-campaign kingdom can fire a dozen deeds at once, all
true and all retroactive. So:

- End Turn posts **one** whispered digest card, `chatmessages/deeds-digest.hbs`, listing every deed
  that fired this turn with an award button per row plus a single **Dismiss all** button.
- The digest is GM-whispered, like the existing offer. The celebration beat that players see is a
  gazette line appended to the turn's `playerNotes`, written only for deeds actually **awarded** —
  so a GM who declines a deed does not announce it to the table anyway.

`ChatButton("km-offer-milestone-dismiss-all")` is the one new button: it sets `offerDismissed` on
every id listed on the card. Without it, adopting the module mid-campaign means twenty individual
dismissals.

## 8. Interactions and out of scope

**Touches:** `KingdomMilestone.kt` (add `detectionId`), `data/MilestoneChoice.kt` (add
`awardedOnTurn`), `MilestoneOffers.kt` (rewritten as the adapter), `ChatButtons.kt` (record
`awardedOnTurn`; add dismiss-all), `schemas/milestone.json`, `data/milestones/*.json` (two retrofits
plus the new entries), the Session Prep section, `lang/*.json` (**all eight** — one locale short and
CI goes red, and only `check_i18n_keys.py --all` catches it).

**Out of scope:** per-PC attribution and renown (`t_26708d31`); deeds detected from free-text notes;
GM-authored custom deeds in the UI (a new file in `data/milestones/` already does this); any change
to how XP is applied; retroactive back-fill of `awardedOnTurn` for milestones ticked by hand;
narrative generation beyond the milestone's own name.

## 9. Test plan

**commonTest** — `DeedDetectionTest`: every detector against fixture `DeedInputs`, both sides of each
threshold (size 24 vs 25); `undetectedDeeds` suppressing answered ids while still returning unanswered
ones; a fired-but-dismissed deed staying suppressed on the next turn; an unknown `detectionId` being
skipped rather than throwing; the catalog invariant that every `detectionId` in `data/milestones/`
has a detector in `deedDetectors` and vice versa.

**jsTest** — `DeedInputs` assembled from a fixture `KingdomData` + turn history; digest context built
for a multi-deed turn; `awardedOnTurn` written by the award path.

**Guards** — `./gradlew check` runs `validateMilestones` over the new schema field.
`check_i18n_keys.py --all` for the eight locales.

**Mutation-check each new test** (repo practice): flip the threshold, invert the answered filter,
drop the dismiss-all loop — and confirm the mutation *compiled* before believing a "survived" result.

**Manual Foundry checklist**
1. Fresh kingdom, End Turn → digest lists `first-settlement` if a settlement exists; award one → XP
   applied, row appears in the Chronicle with the turn number.
2. End Turn again → the awarded deed does **not** re-offer.
3. Dismiss a deed → next End Turn it does not re-offer either.
4. **Dismiss all** on a mid-campaign kingdom → nothing re-offers next turn.
5. Open the Turn Wizard preview twice without committing → identical deed list, no chat post, no
   state change.
6. Log in as a player → the Chronicle renders; no digest card is visible.

## 10. Phasing

**Phase 1 — pure core.** `DeedDetection.kt`, `deedDetectors` with the two retrofitted detectors,
`undetectedDeeds`, full commonTest suite. No behaviour change: `MilestoneOffers.kt` is rewritten to
call the new core and must produce byte-identical offers.

**Phase 2 — catalog.** `detectionId` in the schema and `RawMilestone`; the two retrofits; the
remaining ~19 catalog files and their detectors; i18n for all eight locales.

**Phase 3 — digest and dismiss-all.** `deeds-digest.hbs`, the dismiss-all button, gazette lines for
awarded deeds only.

**Phase 4 — Chronicle.** `awardedOnTurn`, the Session Prep section, template, context, CSS, i18n.

Phase 1 is independently committable and ships zero user-visible change, which is what makes the
rest safe.

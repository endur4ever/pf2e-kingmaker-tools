# Companion Expeditions & Leveling

> Status: Planned · Branch: `kingmaker.5` · Plan file: `docs/plans/companion-expeditions.md`
> Source of truth for persisted state: the free-form `kingdom-sheet` Foundry flag on the `PF2EParty` actor (`getAppFlag`/`setAppFlag`), NOT `KingdomSheetDataModel`.

## 1. Overview

Companions and roster NPCs gain a life of their own between kingdom turns. An `active`, `campAvailable` companion can be dispatched on a multi-day off-screen **expedition** (scout, train, hunt, broker diplomacy, craft, treasure-hunt, pursue a personal quest, or rest/recover). Progress counts down on the **daily world-clock tick** (`DailyTickEngine` / `DailyTickHooks`, first-GM-gated) and never at monthly End Turn. On the day `daysRemaining` reaches zero the expedition flips to `awaitingResolution`; a degree-of-success outcome is rolled at the edge, fed to a **pure resolver engine**, and accrued (but not applied) onto the expedition record. Completion posts a **GM-confirmed chat OFFER card** (the faction-tracker `km-offer-*` pattern); only the GM's click mutates companion XP/level, influence, loot, faction standing, or injuries.

This epic also finally wires `CompanionPersonalQuest.turnsRemaining` (today never ticked) and `CompanionQuestRewards.xp` (today never applied) into a real progression loop.

## 2. Goals / Non-Goals

### Goals
- Off-screen companion activity that resolves on the daily world clock, independent of the monthly economy tick.
- A shadow XP/level track (1000 XP/level, levels 1–20) per companion, drifting ~one level behind the front-line party.
- A pure, JVM-less-testable `ExpeditionResolverEngine.resolve(...)` mirroring `EncounterResolverEngine`.
- Every state change arrives as a GM-confirmed chat offer; players get a read-only board.
- Reuse the existing influence model (`clampInfluence`), faction offer buttons, level-based DC (`getLevelBasedDC`), and `DegreeOfSuccess` enum.
- A GM-managed (player-read-only) Expeditions tab + roster status chip + XP bar.
- Narrative integration into the session-prep gazette and journal export.
- `Migration40` backfills existing worlds; an opt-out setting disables leveling entirely.

### Non-Goals
- No automatic mutation of a linked PF2e actor's `system.*` (level/HP/conditions) without a separate explicit GM offer click.
- No parallel XP economy: XP is a controlled trickle gated by completion events, concurrency caps, and the GM's grant click.
- No change to `TurnTickingEngine` (the monthly economy engine never touches expeditions).
- No new `KingdomSheetDataModel` schema block (persistence stays the free-form flag; bounds enforced in domain helpers).

## 3. Resolved Decisions

1. **State home — split.** Intrinsic `level`/`xp`/`expeditionStatus`/`injuryDaysRemaining` go on `RawCharacter`; an in-flight expedition is a new `RawCompanionExpedition` record in a trailing-nullable `var companionExpeditions: Array<RawCompanionExpedition>?` on `KingdomData` (after `bonusResourceDice`, the verified last field at L285). Do NOT overload `destinationX/Y/eta/traveling` — those are owned by `tickCompanionTravel`.
2. **Leveling — shadow track.** Track `xp`+`level` in the kingdom flag; accrue silently on the tick; at each 1000-XP threshold the completion card includes a GM-confirmed `km-offer-companion-levelup` button that sets `companion.level` and, only if `actorUuid` resolves, offers (separately, never silently) to bump the real actor. NPCs (`role=='npc'`) accrue shadow XP but suppress the level offer.
3. **Injuries — time-boxed downtime.** Only Critical Failure injures: sets `expeditionStatus='unavailable'` + `campAvailable=false` + `injuryDaysRemaining = recoveryDays(tier)` (routine 2 / standard 3 / perilous 5), decremented by the same daily tick, auto-restoring availability. PF2e condition slugs (`injuryConditions: Array<String>`) are surfaced ONLY as an optional GM offer against a linked actor — never auto-applied.
4. **XP numbers — flat per degree × tier multiplier.** Base: Crit Success 120, Success 80, Failure 30, Crit Failure 10, against flat 1000 XP/level. Tier multiplier: routine ×0.75 / standard ×1.0 / perilous ×1.5. Failure XP is deliberately non-zero (anti-death-spiral). The multiplier lives in the **pure layer** so it is unit-tested.
5. **DC & roll.** `DC = getLevelBasedDC(maxParticipantLevel)` (`14 + L + L/3`, reuse `Dc.kt`) + tier modifier (routine −2 / standard 0 / perilous +4), resolved at creation and stored on the record. Roll happens at the edge in an impure wrapper: `resolveAttribute(attr)?.roll(StatisticRollParameters(dc=CheckDC(value=dc)))?.await()` → `fromOrdinal<DegreeOfSuccess>(result?.degreeOfSuccess ?: 0)` for linked companions, or `d20Check(dc, modifier)` for unlinked ones. Influence adds a +0..+2 circumstance modifier by discovery band BEFORE the degree is computed.
6. **Faction outcomes.** Diplomacy is the ONLY pursuit that writes faction standing, routed into the EXISTING `shouldOfferDiplomacyQuest` / `shouldOfferWarThreat` offer buttons so it inherits the fire-once band-crossing logic.
7. **Anti-abuse caps.** `MAX_CONCURRENT_EXPEDITIONS=3` kingdom-wide (launch guard with `ui.notifications.warn`); one expedition per companion (enforced by `expeditionStatus` filtering); `MIN_EXPEDITION_DAYS=2`. XP is tied to the completion EVENT, not elapsed days — a single multi-day clock advance resolves each expedition at most once.
8. **Autonomy — both, same gate.** Mode A (GM-Assigned, always available) and Mode B (Autonomous, opt-in chat offer on making camp), both funnelling through the same eligibility gate and the same GM-confirm click. `unknown`/`introduced` NPCs never auto-volunteer.

## 4. Data Model

### 4.1 `RawCharacter.kt` (jsMain `kingdom/data/`)
Add to BOTH the `@JsPlainObject` interface AND the `js("{...}")` factory literal (the gotcha: omitting the literal silently leaves fresh companions without the field):
- `var level: Int` (1–20, default 1)
- `var xp: Int` (0..999, default 0)
- `var expeditionStatus: String` (`available`|`onExpedition`|`unavailable`, default `available`)
- `var injuryDaysRemaining: Int?` (null default)

Updated literal appends: `..., level: 1, xp: 0, expeditionStatus: 'available', injuryDaysRemaining: null`.

### 4.2 New `RawCompanionExpedition.kt` (jsMain `kingdom/data/`)
`@JsPlainObject external interface` + js-literal factory:
- `id` (`exp-<Date.now()>`), `activityId`, `title` (copied at creation), `companionIds: Array<String>` (soft key `actorUuid ?: name`)
- `status` (`inProgress`|`awaitingResolution`|`resolved`|`cancelled`), `daysRemaining: Int`, `totalDays: Int`
- `dc: Int`, `tier: String` (`routine`|`standard`|`perilous`), `outcomeDegree: String?` (camelCase `DegreeOfSuccess.value`, null until resolved)
- `accruedXp: Int`, `accruedInfluenceDelta: Int`, `accruedInjuries: Array<String>`, `lootTier: String?` (`none`|`minor`|`moderate`|`major`)
- `factionStandingDelta: Int` (0 unless diplomacy), `spawnedQuestId: String?`, `gmNotes: String`
- `visibleToPlayers: Boolean` (default false), `rewardApplied: Boolean` (default false), `createdAt: String?`

### 4.3 `KingdomData.kt`
Append `var companionExpeditions: Array<RawCompanionExpedition>?` immediately before the closing brace (after `bonusResourceDice` at L285).

### 4.4 `Defaults.kt`
Add `companionExpeditions = emptyArray(),` beside `companions = emptyArray()`.

### 4.5 Pure math — new `CompanionLevel.kt` (commonMain `companion/`)
Mirrors `CompanionInfluence.kt`: `MAX_COMPANION_LEVEL=20`, `XP_PER_LEVEL=1000`, `data class LevelUpResult(newLevel, newXp, levelsGained)`, `clampCompanionLevel(level)=coerceIn(1,20)`, `applyCompanionXp(currentLevel, currentXp, gainedXp): LevelUpResult` (loops while `xp>=1000 && level<20`, caps overflow to 0 at 20, negative-guards via `coerceAtLeast(0)`).

### 4.6 Identity convention
`companionId`/`companionIds = actorUuid ?: name`; prefer `actorUuid` where linked. Renaming an unlinked companion orphans its expeditions/quests (same soft-key gotcha as the rest of the subsystem).

## 5. Resolution Engine

**PURE** — new `ExpeditionResolverEngine.kt` (jsMain `kingdom/`), verbatim shape of `EncounterResolverEngine`: `@JsExport @JsName("ExpeditionResolverEngine") object` with one pure `fun resolve(baseXp: Int, baseInfluence: Int, tier: String, degree: DegreeOfSuccess): ExpeditionResolutionResult`. Body is one exhaustive `when(degree)` over all four arms with NO `else` (Kotlin exhaustiveness). Returns `@JsExport data class ExpeditionResolutionResult(xpAwarded: Int, influenceDelta: Int, lootTier: String, injuryConditions: Array<String>, factionStandingDelta: Int, gmNotes: String)`.

Per-degree (base before tier multiply):
- `CRITICAL_SUCCESS`: xp=120, influence=+baseInfluence, loot=`major`, no injury
- `SUCCESS`: xp=80, influence=+baseInfluence, loot=`moderate`, no injury
- `FAILURE`: xp=30, influence=0, loot=`none`, injuryConditions=[], gmNotes='lost time, returned whole'
- `CRITICAL_FAILURE`: xp=10, influence=0 (NO subtraction — anti-spiral), loot=`none`, injuryConditions=`arrayOf("fatigued","wounded")` (offered, not applied), gmNotes seeds a narrative hook

Tier multiplier (×0.75/×1.0/×1.5) applied to `xpAwarded` **inside `resolve`** so it is tested. `influenceDelta` is RAW intent — clamping happens at the APPLY site via `clampInfluence`. Injuries are PF2e condition slugs; tests assert via `.contains()`/`size`, never array `==`. No Foundry/DOM/roll/chat/persistence inside.

**IMPURE WRAPPER** (jsMain, `ExpeditionResolution.kt` / hook layer): resolves the participant statistic, does `roll()/await()/fromOrdinal<DegreeOfSuccess>`, applies the influence circumstance bonus (+0..+2 by discovery band) as a modifiers entry BEFORE the degree, hands `(baseXp, baseInfluence, tier, degree)` to `resolve(...)`, then writes `accrued*` + `outcomeDegree` onto the record, sets `status='awaitingResolution'`, `setKingdom` — applying NOTHING to the companion yet.

**Optional curated catalog**: `@JsPlainObject ExpeditionActivityData` (skills[], dc/dcType, four `ActivityOutcome`) + `getOutcome(degree)` mirroring `CampingActivityData`, loaded via `@JsModule("./expedition-activities.json")` with a JSON schema mirroring `kingdom-activity.json` and a check-wired validator in `build.gradle.kts`.

## 6. Leveling Design

Standard PF2e 1000-XP/level track earned off the critical path. XP awards (base, before tier mult): Crit Success 120 / Success 80 / Failure 30 / Crit Failure 10; personal-quest completion is the headline ~120–150. A relentlessly-used companion banks ~150–250 XP across an in-world month (~quarter level), climbing a level every ~4–6 actively-used kingdom turns — keeping the front-line party at 4 while the most-used companion reaches 3 and the bench NPC sits at 2.

DC reuses `getLevelBasedDC(maxParticipantLevel)` + tier mod so success rate stays stable across a career. Influence: input = eligibility gate (`active && available && !injured`; perilous/diplomacy/personal-quest require `discoveryStatus>=established` i.e. influence>=4) + a small +0..+2 circumstance bonus by discovery band (unknown +0, introduced/established +1, trusted/bonded +2); output = +1 influence only on crit success (clamped), 0 otherwise (NO subtraction).

Leveling moment: when a grant pushes shadow `xp` past 1000, the return card includes a `km-offer-companion-levelup` button (`data-companion-id`, `data-kingdom-actor-uuid`, `data-target-level`). GM click → `applyCompanionXp` sets `companion.level` (xp carries remainder) + a SEPARATE gentle offer to advance the real actor.

**Anti-spiral (three explicit levers):** (1) failure still grants XP; (2) no influence loss on failure/crit-fail; (3) injuries are time-boxed self-healing downtime. The resolver is stateless w.r.t. prior outcomes; DC tracks the companion's own level.

## 7. Tick Integration

**DAILY ONLY — never `TurnTickingEngine`.**

**PURE** — add to `DailyTickEngine.kt`, sibling of `tickTravelEta`: `data class ExpeditionTickResult(newDaysRemaining: Int, completed: Boolean)` and `fun tickExpedition(daysRemaining: Int, days: Int = 1): ExpeditionTickResult` where `elapsed=days.coerceAtLeast(1)`, `next=daysRemaining-elapsed`, `completed` true ONLY on the tick that reaches <=0 (fire-once); a single advance may cross multiple days — same contract as `tickTravelEta`.

**HOOK** — in `registerDailyTickHooks`'s buildPromise block (already gated by `game.isFirstGM() && daysPassed>=1`), add `tickCompanionExpeditions(game, daysPassed)` beside `rollDailyWeather` + `tickCompanionTravel`. It mirrors `tickCompanionTravel`: iterate kingdom actors, read `companionExpeditions`, for each `status=='inProgress'` call `DailyTickEngine.tickExpedition`, write `newDaysRemaining`; on `completed` set `status='awaitingResolution'` and call `offerExpeditionResolution(...)` (the impure wrapper that ROLLS+ACCRUES and posts the OFFER card); reassign `kingdom.companionExpeditions` + `actor.setKingdom(kingdom)` if anything changed. The same loop decrements `injuryDaysRemaining`: at <=0 clear injury, restore `expeditionStatus`/`campAvailable`, post an `escapeHtml`'d "X recovered" line (mirrors `announceArrival`).

`game.time.advance` is NOT called here. XP is tied to the completion EVENT, so crossing 30 days at once still resolves each expedition once.

## 8. UI Design

1. **Tab** — add `EXPEDITIONS` to `MainNavEntry` (currently ends at `ANALYTICS`; value auto-derives to `expeditions`, i18nKey `kingdomMainNav.expeditions`). `createMainNav` auto-emits a `NavEntryContext`; leave it OUT of the GM-only filter so players see it read-only.
2. **Section** — new `sections/expeditions/page.hbs` copied from `sections/roster/page.hbs`; top element self-hides with `{{#if (ne currentNavEntry 'expeditions')}}hidden{{/if}}`, keeps `.km-kingdom-sheet-content > .km-kingdom-sheet-sub-content` wrappers. Renders an active-expeditions board (progress bar = `(totalDays-daysRemaining)/totalDays`), an awaiting-resolution list, GM "New Expedition" button (`data-action='add-expedition'`), per-row cancel/resolve.
3. **Partial registration** — register in `Main.kt loadTemplatePartials` as `"kingdom-expeditions" to "applications/kingdom/sections/expeditions/page.hbs"`, then invoke by NAME in `kingdom-sheet.hbs <main>`: `{{> kingdom-expeditions this}}`.
4. **Context** — declare `val expeditionsContext: ExpeditionsContext` on `KingdomSheetContext` and build it in `_preparePartContext` beside `rosterContext`.
5. **Pure builder** — new `ExpeditionsContext.kt` mirroring `RosterContext.kt`: `@JsPlainObject ExpeditionRowContext` + `ExpeditionsContext{items, isGM}` + pure `fun Array<RawCompanionExpedition>.toExpeditionsContext(isGM, companions, localize={it})` that strips non-`visibleToPlayers` rows when `!isGM`.
6. **Action handlers** in `KingdomSheet._onClickAction`, GM-gated, in `buildPromise{}`: `add-expedition` (opens dialog; picker filters `companion.active && expeditionStatus=='available' && injuryDaysRemaining==null`), `cancel-expedition`, `resolve-expedition`.
7. **Create dialog** — new `AddExpedition.kt` modelled on `AddPersonalQuestDialog` (`FormApp` + `@JsExport ExpeditionModel:DataModel` with `defineSchema()` for FORM validation only); on save builds `RawCompanionExpedition` (dc resolved by dcType via `getLevelBasedDC(maxParticipantLevel)+tier mod`) and hands back via `onSave`, flipping each participant's `expeditionStatus='onExpedition'`.
8. **Roster** — row gains an expedition-status chip (idle / "Scouting, returns in 3d" / recovering) + an XP-toward-next-level bar (`xp*100/1000`).
9. **Companion profile** — `CompanionProfileDialog`/`CompanionProfileContext` surface level/XP + current/past expeditions + a GM-gated "send on expedition" action.
10. **Deletion guard** — add `companionHasActiveExpedition(index)` mirroring `companionHasActivePersonalQuests`; block delete with `ui.notifications.warn` when `onExpedition`.
11. **CSS** — append `.km-expedition-board/-grid/-card` styles (reuse `km-*` conventions + 768px/480px breakpoints; tab strip is `.km-tabs`) to `kingdom-sheet.css` OR a new `sections/expeditions/expeditions.css` `@import`ed from `style.css`.

## 9. Narrative Integration

- **Chat offer card** — new `chatmessages/expedition-result.hbs` modelled on the `end-turn.hbs` offer block + `degree-of-success.hbs`; localizes via `{{localizeKM '...'}}`, shows the outcome degree styling + accrued results, renders guarded offer buttons: `km-offer-expedition-reward`, `km-offer-companion-levelup`, faction standing routed into the EXISTING `km-offer-diplomacy-quest`/`km-offer-war-threat`, and an injury offer button (only if actor-linked). Rendered via `postChatTemplate('chatmessages/expedition-result.hbs', ctx)`; all actor-provided strings run through `escapeHtml`.
- **Offer buttons** — add to the `buttons` list in `ChatButtons.kt` (auto-wired by list membership): `km-offer-expedition-reward` reads `data-expedition-id`, resolves actor via `parent.findKingdomActor(game)`, guards double-apply (`if expedition.rewardApplied || status=='resolved' return`), applies XP via `applyCompanionXp`, influence via `clampInfluence`, sets `expeditionStatus='available'`, `status='resolved'`, `rewardApplied=true`, `setKingdom`; loot/resources via `ActionDispatcher.dispatch(ActionMessage('gain...'))`. Whole chain GM-only.
- **Session-prep gazette** — new `companionExpeditions` section in `SessionPrepView` (built in `SessionPrepContext`, rendered by `SessionPrepNarrativeGenerator.generate()/generatePlainText()`) showing who's out + return ETA, what returned since last session (with crit-success/crit-failure hooks), and who leveled/completed a quest — all strings through `esc()`; flows into the journal export automatically.
- **Personal quests** — personal-quest pursuit is a first-class expedition type that FINALLY makes `CompanionPersonalQuest.turnsRemaining` tick and wires `CompanionQuestRewards.xp`; completion fires the existing `influenceReward` auto-apply AND the now-wired XP; spawning a follow-up reuses the `AddPersonalQuest` two-write pattern.
- **Camp** — a companion `onExpedition` is excluded from camp meal/effect bonuses until return.

## 10. Risks

- **Migration40 dual-edit**: import after `Migration39` (~L33) AND append `Migration40()` to the `listOf` (~L86, currently ending at `Migration39()` at L86). Forgetting the list entry means it never runs.
- **i18n guard not in CI**: run `python3 scripts/check_i18n_keys.py` manually until 0 unresolved/flat keys; keys must be NESTED objects in BOTH `en.json` AND `de.json`.
- **RawCharacter factory literal**: add the new fields to the `js("{...}")` literal, not just the interface, or fresh companions silently lack them.
- **Tier multiplier placement**: keep it in the pure layer (decided: `resolve()` multiplies) so it is unit-tested.
- **Influence threshold mismatch**: the map encodes 0/2/4/6/8; define the perilous floor (>=4) and the +0..+2 band bonus against the ACTUAL map and treat 9–12 as `bonded`.
- **jsTest under Chrome-headless in WSL** (Firefox times out): `useChromeHeadless` + `CHROME_BIN` + `-x kotlinStoreYarnLock`; keep XP/level math in commonMain (cheaper JVM-less tests).
- **Re-run `./gradlew assemble` after EVERY `.kt`/`.hbs`/`.json` change** — the served module is the compiled dist.
- **User co-edits the live tree** — re-read/re-build before relying on line anchors; `MainNavEntry` already grew beyond the subsystem-map listing.
- **DegreeOfSuccess enum order** is the REVERSE of PF2e's roll index — always `fromOrdinal<DegreeOfSuccess>(index)`, never cast the raw number.
- **Migrations are effectively one-shot/irreversible** (single auto-backup setting) — finish at commit level; Gregory runs it against the live world.

## 11. Phase / Task Summary

| # | Key | Phase | Title | Assignee |
|---|-----|-------|-------|----------|
| 1 | data-model-migration | Data | Data model, KingdomData field, Migration40, failing engine scaffolds | builder |
| 2 | resolver-engine | Engine | Pure ExpeditionResolverEngine + tests | builder |
| 3 | companion-level | Engine | CompanionLevel.kt XP/level math + tests | builder |
| 4 | activity-catalog | Data | Expedition activity catalog JSON + schema + validator | builder |
| 5 | daily-tick | Tick | DailyTickEngine.tickExpedition + tests | builder |
| 6 | tick-hooks | Tick | tickCompanionExpeditions hook + impure resolution wrapper | builder |
| 7 | chat-offer | Narrative | GM-confirmed result chat OFFER card + handlers | builder |
| 8 | ui-board | UI | Expeditions tab/section, context, partial, AddExpedition dialog | builder |
| 9 | roster-profile | UI | Roster chip + XP bar, companion-profile integration, deletion guard | builder |
| 10 | css-styles | UI | Expedition board CSS + responsive | builder |
| 11 | i18n | UI | en.json + de.json nested keys, pass check_i18n_keys.py | builder |
| 12 | autonomy | Narrative | Autonomous self-select offers (opt-in setting) | builder |
| 13 | gazette-quests | Narrative | Session-prep gazette + personal-quest tick/XP wiring | builder |
| 14 | final-green | QA | Full jsTest green, clean assemble, verify served dist | qa |

# Companion Expeditions — Implementation Status & Remaining Work

**Date:** 2026-06-24
**Design doc:** `docs/plans/2026-06-24-companion-expeditions.md`
**Task:** t_354774d3

## What's Already Implemented

### Data layer
- `RawCompanionExpedition` in `RawCompanionExpedition.kt` — full data model
- `RawCharacter.expeditionStatus`, `injuryDaysRemaining` fields
- `KingdomData.companionExpeditions` array
- `Defaults.COMPANION_EXPEDITION_DEFAULTS`

### Engine
- `CompanionLevel.kt` — XP/level math (level-up thresholds, XP gain)
- `ExpeditionResolverEngine.kt` — pure resolve function (degree of success, XP accrual, influence delta, loot tier)
- `ExpeditionResolution.kt` — impure wrapper (roll + accrue + injury)
- `DailyTickEngine.tickExpedition()` — decrements days, resolves when daysRemaining hits 0
- `DailyTickHooks.tickCompanionExpeditions()` — wired into daily tick

### UI
- `ExpeditionsContext.kt` — UI context for expeditions page
- `AddExpeditionDialog.kt` — create expedition dialog
- `expeditions.hbs` + `add-expedition.hbs` — expedition page templates
- `kingdom-expeditions` partial registered in `kingdom-sheet.hbs`
- `MainNavEntry.EXPEDITIONS` — navigation entry
- `CompanionProfileDialog.kt` — current/past expeditions tab + "send on expedition" action
- `CompanionProfileContext.kt` — expedition tab data

### Sheet actions (KingdomSheet.kt)
- `add-expedition` — creates expedition, marks companions onExpedition
- `cancel-expedition` — cancels, marks companions available
- `resolve-expedition` — marks resolved, releases companions
- Deletion guard: `companionHasActiveExpedition` prevents deleting companions on expedition

### Camping integration
- `CampingSheet.kt` — `onExpedition` companions excluded from camp meal/effect bonuses

### Chat integration
- `expedition-result.hbs` — chat card template with degree of success, XP, influence, loot
- `ChatButtons.kt`:
  - `km-offer-expedition-reward` — applies XP/influence, marks resolved
  - `km-offer-companion-levelup` — advances companion level
  - `km-offer-injury` — applies injury conditions

### i18n & CSS
- `lang/en.json`: `kingdomExpeditions`, `expeditionResult`, `cannotDeleteOnExpedition`, etc.
- `lang/de.json`: full German translations
- `kingdom-sheet.css`: expedition status chip styles (available/onExpedition/unavailable)

### Migration
- `Migration40` — adds `companionExpeditions` to kingdom data

### Tests
- `CompanionExpeditionTest.kt` — `companionHasActiveExpedition` tests
- `RosterContextTest.kt` — expedition status label tests
- `CompanionProfileContextTest.kt` — expedition tab tests
- `CompanionQuestRowsTest.kt` — personal quest row tests
- `SessionPrepNarrativeGeneratorTest.kt` — session prep tests

---

## Remaining Work

### 1. Expedition Board CSS (design doc §11)
**Files to modify:**
- `src/jsMain/resources/applications/kingdom/kingdom-sheet.css`

**What's missing:**
- `.km-expedition-board` — main container grid layout
- `.km-expedition-card` — per-expedition card styling
- `.km-expedition-card-header` — title + status chip
- `.km-expedition-card-body` — activity, companions, days remaining, notes
- `.km-expedition-card-actions` — action buttons
- `.km-expedition-grid` — responsive grid layout
- `.km-expedition-activity-icon` — activity type icon
- `.km-expedition-empty` — empty state when no expeditions

The design doc specifies a card-based grid layout with activity icons, companion avatars, and action buttons. The HTML templates already exist in `expeditions.hbs` but the CSS is missing.

### 2. Session-Prep Gazette Integration (design doc §9)
**Files to modify:**
- `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/SessionPrepView.kt`
- `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/SessionPrepNarrativeGenerator.kt`

**What's missing:**
- `companionExpeditions` field in `SessionPrepView`
- `buildCompanionExpeditions()` function in SessionPrepView companion object
- Narrative section in `generate()` and `generatePlainText()` for active expeditions

### 3. Personal Quest Tick Wiring (design doc §6)
**Files to modify:**
- `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/DailyTickHooks.kt` (or `DailyTickEngine.kt`)
- `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/KingdomSheet.kt` (completion handler)

**What's missing:**
- `tickCompanionPersonalQuests()` — decrement `turnsRemaining`, auto-complete when 0
- Personal quest completion handler — apply `CompanionQuestRewards.xp` to companion's XP track
- Wire into `DailyTickHooks` after `tickCompanionExpeditions`

### 4. Resolve Expedition Flow (design doc §7)
**Files to modify:**
- `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/KingdomSheet.kt`

**What's missing:**
- The `resolve-expedition` action currently just marks resolved without offering reward
- Should call `ExpeditionResolverEngine.resolve()` and offer the GM a chat card with reward options
- This is the manual "end early" flow; the auto-resolve on daysRemaining=0 already works via DailyTickEngine

### 5. Autonomous Self-Select (Mode B) (design doc §10)
**Files to modify:**
- `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/contexts/ExpeditionsContext.kt`
- `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/KingdomSheet.kt`
- New file: `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/AutonomousExpeditionSelector.kt`

**What's missing:**
- `autonomousCompanionSelfSelect` setting in kingdom config
- `AutonomousExpeditionSelector` — picks available companions for expeditions based on personality/preference
- Integration into `AddExpeditionDialog` (auto-suggest companions)
- Integration into `DailyTickHooks` (auto-start expeditions when companions available)

---

## Implementation Order

1. **Expedition Board CSS** — purely visual, unblocks nothing but improves UX
2. **Session-Prep Gazette** — small addition, testable
3. **Personal Quest Tick Wiring** — independent feature, testable
4. **Resolve Expedition Flow** — depends on ExpeditionResolverEngine (already done)
5. **Autonomous Self-Select** — largest piece, depends on all above

## Test Plan

- [ ] Expedition board CSS: visual inspection in browser
- [ ] Session-Prep: add test for `buildCompanionExpeditions()` in `SessionPrepViewTest`
- [ ] Personal Quest Tick: add test in `DailyTickEngineTest` for decrement + completion
- [ ] Resolve Expedition: add test in `KingdomSheetTest` for resolve flow
- [ ] Autonomous Self-Select: add test in new `AutonomousExpeditionSelectorTest`

# Strict Phase Gating for the Turn Wizard

Created: 2026-06-17
Roadmap: candidate backlog #3 in `docs/plans/`
Branch: `kingmaker.5`

## Goal

Provide strict phase gating for the Turn Wizard (rules/state machine). Currently, the Turn Wizard is advisory, allowing activities to be performed in any order. This feature adds an opt-in toggle to enforce sequence progression of upkeep steps and activity phases, disabling activities on the sheet that do not belong to the current active turn phase.

## Scope

1. **Opt-in Setting:** Register a new boolean setting `enableStrictPhaseGating` in the module settings and surface it on the Kingdom Settings dialog.
2. **Wizard Checklist Sequence:** Expand the Turn Wizard checklist with phase boundary checks (`leadership-phase`, `civic-phase`, `region-phase`, `commerce-phase`, `army-phase`) when strict gating is active.
3. **Sequential Checking & Unchecking:** Enforce strict sequential checklist order in the Turn Wizard. Users cannot check an item out of order. Unchecking an item automatically unchecks all downstream items.
4. **Sheet Activity Gating:** Disable activities on the main Kingdom Sheet if they do not match the current phase derived from the Turn Wizard's checklist state. Show a clear tooltip explanation (e.g. "Locked: Turn step is Upkeep (this activity is Leadership)").

## Key Constraints & Details

- **Turn Phases Order:**
  1. `UPKEEP` (checklist: `gain-fame`, `adjust-unrest`, `collect-resources`, `pay-consumption`)
  2. `LEADERSHIP` (checklist: `leadership-phase`)
  3. `CIVIC` (checklist: `civic-phase`)
  4. `REGION` (checklist: `region-phase`)
  5. `COMMERCE` (checklist: `commerce-phase`)
  6. `ARMY` (checklist: `army-phase`)
  7. `EVENT` (checklist: `check-events`)
- **Transient State:** The gating state uses the existing `turn-wizard-state` app flag stored on the kingdom actor. If the state is not initialized, the current phase is assumed to be `UPKEEP` (forcing the user to open the Turn Wizard to start the turn).
- **Disabled Reason Tooltip:** Map disabled reasons using the i18next engine:
  `"activityPhaseGated": "Locked: Current turn step is {{activePhase}} (this activity is {{phase}})."`

## Proposed Changes

### 1. Data Models & Settings

#### [MODIFY] [KingdomData.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/KingdomData.kt)
Add `enableStrictPhaseGating` property to `KingdomSettings` interface:
```kotlin
var enableStrictPhaseGating: Boolean?
```

#### [MODIFY] [KingdomSettings.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/dialogs/KingdomSettings.kt)
- Register `"enableStrictPhaseGating"` in `KingdomSettingsDataModel.defineSchema()`.
- Add a Form section row in `KingdomSettingsApplication._preparePartContext` under a new section or the "Turn Wizard & Phase Gating" section.

---

### 2. Turn Wizard Checklist Sequence

#### [MODIFY] [TurnWizardApplication.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/dialogs/TurnWizardApplication.kt)
- Update `buildContext` to dynamically append `leadership-phase`, `civic-phase`, `region-phase`, `commerce-phase`, and `army-phase` to the `checklist` array if `kingdom.settings.enableStrictPhaseGating == true`.
- Implement sequence check to disable checkboxes in the checklist:
  - An item is `disabled = true` if `isStrict` is true and any preceding item in the sequence is unchecked.
- Update `toggleChecklistItem` to enforce unchecking recursion: unchecking any item automatically filters out and clears all subsequent items in the sequence from the checklist array.

#### [MODIFY] [turn-wizard.hbs](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/resources/applications/kingdom/turn-wizard.hbs)
- Bind the `disabled` property to the VTT checkbox element:
  ```handlebars
  <input type="checkbox" class="km-checklist-toggle" data-id="{{id}}" {{#if checked}}checked{{/if}} {{#if disabled}}disabled{{/if}} />
  ```

---

### 3. Sheet Activity Gating

#### [MODIFY] [ActivitiesContext.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/contexts/ActivitiesContext.kt)
- Implement `getActivePhaseForGating(checkedItems: Set<String>): KingdomPhase?` to derive the active phase.
- Pass `checkedItems` from `toActivitiesContext` into `toActivityContext`.
- In `toActivityContext`, if `enableStrictPhaseGating == true`:
  - Calculate `phaseGated = activePhase != null && activity.phase != activePhase.value`.
  - If `phaseGated`, set `disabled = true` and `disabledReason` to the localized `kingdom.activityPhaseGated` warning.

---

### 4. Localization

#### [MODIFY] [en.json](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/resources/lang/en.json)
Add keys:
- `kingdom.turnWizardPhaseGatingSettings`
- `kingdom.enableStrictPhaseGating`
- `kingdom.enableStrictPhaseGatingHelp`
- `kingdom.activityPhaseGated`
- Phase checklists (`kingdom.turnWizard.checklist.leadershipPhase`, `civicPhase`, etc.)

---

## Verification Plan

### Automated Tests
- Create `TurnWizardStrictGatingTest.kt` verifying:
  - Sequence order checking rules.
  - Clear-downstream logic on uncheck.
  - Active phase derivation from checklist state.
  - Activity disabled status when out-of-phase.

### Manual Verification
1. Open Kingdom Settings, check **Strict Phase Gating**, and save.
2. Open the Kingdom Sheet and try to roll any Leadership activity. It should be disabled with a tooltip indicating the active phase is Upkeep.
3. Open the Turn Wizard. Upkeep items should be enabled, while phase checkbox items are disabled.
4. Check off the Upkeep items (`Gain Fame`, etc.). The Leadership Phase checkbox should now become enabled.
5. Close the wizard. Leadership activities on the sheet should now be enabled, while other phases remain disabled.
6. Check off `Leadership Phase` in the wizard. Civic activities should unlock.
7. Uncheck `Gain Fame` in the wizard. Confirm all downstream checkboxes are cleared, and the sheet restricts activities back to Upkeep.

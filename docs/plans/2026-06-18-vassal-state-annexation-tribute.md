# Vassal State, Settlement Annexation, & Tribute Tracking Plan

> **Date:** 2026-06-18
> **Roadmap item:** New backlog #8 (Vassal State, Settlement Annexation, & Tribute Tracking)

---

## 1. Goal & Context

This feature implements the automation of monthly vassal tribute collection, unrest penalties, and hex integration upon annexation.
Vassal states (groups with `allianceLevel == "tribute"`) should automatically yield tribute during the monthly turn tick.
Annexation is GM-triggered via the sheet UI, which clears the tribute treaty, increases Unrest, claims the trade hub hex, and logs/posts the event.

---

## 2. Proposed Changes

### Monthly Turn Tick Integration

#### [MODIFY] [TurnTickingEngine.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/TurnTickingEngine.kt)
- During resource collection, check all groups with `allianceLevel == "tribute"`.
- Each tribute-level vassal state yields a monthly tribute of flat +2 RP.
- Accumulate the total tribute RP, add it to the ticked RP (`resourcePoints.now`), and log a `TickChange("resourcePoints", "tribute", 0, tributeRp)`.

#### [MODIFY] [TurnHistory.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/TurnHistory.kt)
- Update `formatTurnGazette` to support a new optional parameter `tributeRp: Int = 0`.
- If `tributeRp > 0`, append `"Tribute: +$tributeRp RP collected from vassal states"` to the gazette events.

#### [MODIFY] [TurnWizardApplication.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/dialogs/TurnWizardApplication.kt)
- In `performEndTurn`, find the tribute change and pass the total tribute RP to `formatTurnGazette`.
- Update `TickChange.toDisplayString()` to render the `"resourcePoints"`/`"tribute"` change using `t("kingdom.turnWizard.preview.tributeRp", ...)` with parameters.

---

### Settlement Annexation Dialog & Actions

#### [MODIFY] [KingdomSheet.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/KingdomSheet.kt)
- Import `at.posselt.pfrpg2e.app.confirm`.
- Wire a new sheet click action `"annex-group"`:
  - Retrieve the target group index.
  - Trigger a confirmation dialog `confirm(t("kingdom.annexConfirm", recordOf("name" to group.name)))`.
  - Upon approval, if the group has `hexKey` set and the `pf2e-kingmaker` module is active, update `kingmaker.state` to mark that hex as `claimed`, `explored`, and `cleared`.
  - Clear the group's treaty tier (`group.allianceLevel = null`).
  - Increase kingdom Unrest by `2` (`kingdom.unrest += 2`).
  - Add a `"kingdom.factionStanding.annexation"` entry to `group.standingLog`.
  - Save the updated kingdom state back to the actor via `actor.setKingdom(kingdom)`.
  - Post a chat card detailing the annexation with the claimed hex coordinate.

#### [MODIFY] [page.hbs](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/resources/applications/kingdom/sections/trade-agreements/page.hbs)
- If the group's `allianceLevel == "tribute"`, and the user `isGM`, render an "Annex" action button next to the treaty status:
  ```html
  <button type="button" data-action="annex-group" data-index="{{@index}}" data-tooltip="{{localizeKM "kingdom.annexTooltip"}}">
      <i class="fa-solid fa-crown"></i>
  </button>
  ```

---

### Translation Keys

#### [MODIFY] [en.json](file:///home/grego/code/pf2e-kingmaker-tools/lang/en.json)
- Add the following keys:
  - `kingdom.turnWizard.preview.tributeRp`: `"Vassal Tribute: +{amount} RP"`
  - `kingdom.annexTooltip`: `"Annex Vassal State"`
  - `kingdom.annexConfirm`: `"Are you sure you want to annex the vassal state of {name}? This will clear their tribute treaty, increase Unrest by 2, and claim their trade hub hex."`
  - `kingdom.factionStanding.annexation`: `"Annexed Vassal State"`
  - `chatMessages.annexation.title`: `"Vassal State Annexed"`
  - `chatMessages.annexation.body`: `"The vassal state of {name} has been formally annexed into the kingdom."`
  - `chatMessages.annexation.unrestReason`: `"Annexation integration"`
  - `chatMessages.annexation.hexClaimed`: `"Claimed Hex"`

---

## 3. Verification Plan

### Automated Tests
- Create `TurnTickingTributeTest` verifying that vassal states with tribute status yield +2 RP per turn and log a tribute change.
- Run tests via:
  ```bash
  ./gradlew jsTest -x kotlinStoreYarnLock
  ```

### Manual Verification
1. Add a group, set its treaty level to Tribute.
2. Verify that ending the turn yields +2 RP and logs the tribute in the chat summary.
3. Click the "Annex" crown icon next to the Tribute status.
4. Confirm the dialog, and verify that:
   - Treaty status is cleared (set to None).
   - Kingdom Unrest increases by 2.
   - The corresponding hex (if set) becomes claimed on the region map.
   - A chat message detailing the annexation is posted.

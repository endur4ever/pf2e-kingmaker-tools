# Session Prep & Recap Dashboard (Roadmap #10)

> **Status: IMPLEMENTED (2026-06-09).** Structured-aggregation phase shipped behind a new `SESSION_PREP` kingdom-sheet tab. Files differ slightly from the original sketch below (the codebase uses a pure view + JsPlainObject context split, mirroring the pacing-alerts section, rather than a single `SessionPrepAggregator`):
> - Aggregator (pure, tested): `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/SessionPrepView.kt`
> - Context: `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/contexts/SessionPrepContext.kt`
> - Template: `src/jsMain/resources/applications/kingdom/sections/session-prep/page.hbs` (+ `session-prep.css`)
> - Nav entry: `SESSION_PREP` in `MainNavEntry.kt`; partial registered in `Main.kt`, included in `kingdom-sheet.hbs`
> - Tests: `src/jsTest/kotlin/at/posselt/pfrpg2e/kingdom/SessionPrepViewTest.kt`
> - Player-safe filter: implemented — campaign clocks, unresolved events, and hidden hex content are withheld from non-GMs; companion moments collapse to player-visible ones.
> - Deferred (future additive layer): narrative prose generation (disabled "Generate Narrative" button seam) and journal export.

**Goal**: Provide a GM‑only, read‑only dashboard that aggregates key kingdom data before a session (open quests, active clocks, unresolved events, nearby hex hooks, companion moments). The dashboard will expose structured lists only, with a button to later generate narrative prose (future additive layer).

**Background**
- Roadmap item #10 calls for a Session Prep and Recap Dashboard.
- Decision 5 (docs/plans/2026-06-01-roadmap-design-decisions.md) recommends **structured aggregation only** for the initial implementation, with a "Generate Narrative" seam for later prose.
- Depends on companion‑moments aggregation from Feature #7 (companion‑relationship‑quest‑manager). The aggregation contract is already in place via `ActivitiesContext` and companion data.

**Scope**
- UI only; no changes to core turn‑ticking logic.
- Read‑only aggregation; no write actions.
- Export to journal as a recap (optional, low‑priority future).

**Design**
1. **Data aggregation service** (`SessionPrepAggregator.kt`)
   - Reads `KingdomData` for:
     - Open quests (status != "completed")
     - Active campaign clocks (`ClockData` where `remainingTurns > 0`)
     - Unresolved kingdom events (`EventData` where `resolved == false`)
     - Nearby hex hooks (claimed hexes within X miles of the capital)
     - Companion moments (`CompanionMoment` objects from `ActivitiesContext`).
   - Returns a plain‑data model `SessionPrepData` (lists of objects with id, name, short description).
2. **Handlebars template** `session-prep-dashboard.hbs`
   - Renders each list as a collapsible section.
   - Includes a button `{{localize "kingdom.sessionPrep.generateNarrative"}}` (future hook).
3. **Sheet integration**
   - Add a new navigation entry `SESSION_PREP` to `MainNavEntry` enum if needed, or reuse an existing tab (`SETTLEMENTS`?) with a sub‑section.
   - In `KingdomSheet.kt`, add a data‑action `open-session-prep` that loads the aggregation and renders the template.
4. **Localization**
   - Add keys under `kingdom.sessionPrep.*` for titles, button labels, and list headings.
5. **Tests**
   - Unit tests for `SessionPrepAggregator` covering empty data, full data, and filtering logic.
   - UI tests for the new button and template rendering (JS tests).

**Implementation Tasks**
| Phase | Description | Files | Owner |
|------|-------------|-------|------|
| 1 | Create aggregation model and service | `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/SessionPrepAggregator.kt` | researcher |
| 2 | Add Handlebars template | `src/jsMain/resources/applications/kingdom/session-prep-dashboard.hbs` | researcher |
| 3 | Extend `MainNavEntry` (optional) or add UI action in `KingdomSheet.kt` | `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/navigation/MainNavEntry.kt` (if adding entry) or modify `KingdomSheet.kt` | researcher |
| 4 | Hook UI action to load aggregation and render template | `src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/KingdomSheet.kt` | researcher |
| 5 | Add localization strings | `src/jsMain/resources/lang/en.json` | researcher |
| 6 | Write unit tests for aggregator | `src/jsTest/kotlin/at/posselt/pfrpg2e/kingdom/SessionPrepAggregatorTest.kt` | researcher |
| 7 | Add UI test for dashboard rendering | `src/jsTest/kotlin/at/posselt/pfrpg2e/kingdom/SessionPrepDashboardTest.kt` | researcher |

**Risks & Open Questions**
- **Performance**: Aggregation runs on UI open; should be cheap (simple collection, no heavy computation).
- **Future prose layer**: Ensure the `SessionPrepData` contract is stable for the eventual narrative generator.
- **Navigation placement**: Deciding whether to add a new top‑level tab or embed as a subsection under the existing Kingdom tab.

**Next Steps**
- Write the plan file (this document).
- Create a child Kanban task for the actual implementation. 

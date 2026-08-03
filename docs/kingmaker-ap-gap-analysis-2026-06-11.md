# Kingmaker AP Gap Analysis — 2026-06-11

> ⚠️ **SUPERSEDED (2026-06-13).** Most "remaining gaps" below have since shipped (tactical
> warfare resolver, durable turn history, end-turn XP, bonus dice, water-adjacency wiring,
> rough terrain, anarchy gating, urban-grid parity, V&K creation extras, pacing-alert wiring).
> See the current [`kingmaker-ap-gap-analysis-2026-06-13.md`](kingmaker-ap-gap-analysis-2026-06-13.md)
> for the live status. Kept for history only.

**Scope:** Re-assessment of `kingmaker-workbook-notebooklm-missing-features-report.md` (2026-06-03)
against the current `kingmaker.5` codebase, after the roadmap #1–#13 completion sprint.
**Verdict:** The two areas the June report ranked highest-value (settings/profile foundation,
turn assistant) are substantially shipped. What remains is one large feature (tactical warfare),
two medium automation gaps (turn history, end-turn XP), and a handful of small wiring/parity items.

---

## Closed since 2026-06-03

| June 3 gap | What shipped |
|---|---|
| 16 of 17 workbook/V&K settings missing | `KingdomSettings` now carries 47 fields incl. `rpToXpConversionRate/Limit`, `vanceAndKerensharaXP`, `kingdomSkillIncreaseEveryLevel`, `kingdomAllStructureItemBonusesStack`, `proficiencyMode`, `expandMagicUse`, `maximumFamePoints`, `eventDc/eventDcStep`, plus the homebrew rules profile system (#9) and gear-settings profiles |
| Flat Check DC (row 506) | `eventDc` − `turnsWithoutEvent` × `eventDcStep` escalation implemented |
| Running a Kingdom / Ending the Turn (rows 6, 534) | Turn Wizard: checklist, activity caps by phase, preview/commit through a single shared tick path, end-turn chat summary |
| Campaign automation "roadmap only" | All 13 roadmap features complete (timeline clocks, quest gen, hex content, turn assistant, session prep + narrative + journal export, benefit tracker, companions, camping resolver, homebrew profiles, encounter curator, war pressure board, pacing alerts) |
| Warfare pressure clocks / ETA | #12 Army & Pressure board: threats, ETA/escalation ticks, deployments, pressure meter |
| Activity caps by step | `ActivityCapCalculator` with per-phase caps wired into the Turn Wizard |

---

## Remaining gaps

### Tier 1 — substantial AP mechanics not yet modeled

1. **Tactical warfare resolver** (workbook row 431, Army Template sheet).
   All data exists (`WorkbookArmyData`, `ArmyTactic`, specialized modifiers) but there is zero
   battle behavior: no army XP/leveling, morale checks, rout threshold, conditions
   (mired/pinned/weary/damaged/destroyed), melee/ranged strikes, battlefield terrain, or
   recovery workflow. The #12 board is the strategic layer; the tactical layer is the single
   biggest remaining feature. → `docs/plans/army-warfare-phase-resolver.md`

2. **Durable turn history** (History + Turn Tracker sheets).
   No `TurnRecord`/turn log exists; End Turn emits a chat message only. A per-turn record
   (resource dice, consumption paid, event + result, XP awarded, fame/unrest delta, notes)
   would also feed the session-prep recap and journal export, which now exist as surfaces.
   → `docs/plans/turn-tracker-history-migration.md`

3. **End-turn XP automation** (rows 515, 526).
   `rpToXpConversionRate/Limit` settings exist but nothing converts leftover RP→XP at End Turn;
   event XP and milestone awards are not aggregated or displayed as "awarded this turn".

4. **Bonus dice model** (row 46).
   No representation of granted bonus resource dice (grant/spend/cap/reset); only a feat dialog
   mention exists.

### Tier 2 — wiring and parity gaps

5. **Water-adjacent structure logic unwired.** `WaterAdjacency.kt` has zero usages in
   evaluation — the workbook's water-adjacent Mill consumption reduction does nothing.
   Verify or intentionally reject (then document).
6. **Rough terrain construction** (row 292): no cost/DC modifier or settlement UI warning.
7. **Strict phase gating** (row 10): caps exist per phase, but activity availability is not
   gated by turn step — the wizard is an advisory checklist. *Decision*: this matches the
   module's advisory-first design philosophy; either embrace it (document as intended) or plan
   a state-machine mode toggle.
8. **Anarchy as a turn-state restriction**: currently a modifier penalty only; the workbook
   restricts activities while in anarchy.
9. **Fame/infamy upkeep gain**: a capped +1 chat button and a manual checklist item exist;
   the AP's automatic +1 per turn (capped at `maximumFamePoints`) could fold into End Turn.
10. **Urban Grid Template formula parity**: blocks/lots/settlement matrix exist, but the full
    parity audit (walls, edifices, resident capacity vs workbook examples) was never done.
11. **Kingdom creation wizard completeness**: a creation section exists
    (`character-sheet/creation.hbs`) but V&K creation extras (charter/heartland extra skills,
    extra ability boost) are absent and once-only application of boosts/skills is unverified.

### Tier 3 — decisions, not code

12. **Leadership: 8 vs 11 roles.** The 11-role list (Grand Diplomat, High Priest, Marshal,
    Spymaster…) is PF1e Kingmaker. Recommendation: explicitly reject for this PF2e module and
    note it here; only build as a rules-profile variant if Gregory wants PF1 flavor.
13. **`pacingAlertMinUnrestDelta`** settings field is read by no evaluator — wire it into
    stagnation tracking or remove it.
14. **Consumption modifier split** (row 59): consumption math lives in `Consumption.kt` and
    the sheet; the workbook's itemized modifier breakdown is partially represented. Audit
    whether any modifier source is missing rather than assuming a gap.

---

## Suggested order

1. Turn history record (small, unlocks recap/journal value immediately, prereq for XP display).
2. End-turn XP automation + fame upkeep gain (both End Turn folds, small).
3. Water-adjacency wiring + rough terrain modifier (settlement evaluation pass, small).
4. Tactical warfare resolver (the big one — phased plan like #11/#12/#13).
5. Creation wizard V&K extras + urban grid parity audit (when touched next).

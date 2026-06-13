# Kingmaker AP Gap Analysis — 2026-06-13

**Scope:** Re-assessment of [`kingmaker-ap-gap-analysis-2026-06-11.md`](kingmaker-ap-gap-analysis-2026-06-11.md)
against the current `kingmaker.5` codebase, after the warfare-resolver / turn-history /
end-turn-XP / creation-extras sprint.
**Supersedes:** the 2026-06-11 doc (which itself superseded the 2026-06-03 report).

**Verdict:** The AP-fidelity gap list is **essentially closed**. Every Tier-1 and Tier-2 item
the June 11 doc ranked as missing automation has since shipped, including the single biggest
one (the tactical warfare resolver). What remains is **not a feature backlog** — it is two
design decisions, two audits, and one correctness verification. The largest *new* work now
lives in the forward-looking roadmap (faction/diplomacy, analytics), not in AP fidelity.

---

## Closed since 2026-06-11

Verified against code on 2026-06-13; kanban board `pf2e-kingmaker-tools` corroborates each.

| June 11 gap | Tier | What shipped / evidence |
|---|---|---|
| Tactical warfare resolver | 1 | `data/armies/ArmyBattleEngine.kt`, `BattleStatus.kt`, `ArmyCondition.kt`, `dialogs/ResolveBattle.kt` — battle engine (strikes, morale, conditions), army XP/leveling/recovery, and a Resolve Battle dialog + chat log (warfare resolver phases 1–4) |
| Durable turn history | 1 | `kingdom/TurnHistory.kt` + `data/RawTurnRecord` (`buildTurnRecord`/`appendTurnRecord`), surfaced as the "Recent Turns" recap |
| End-turn XP automation | 1 | `data/kingdom/RpToXp.kt`, `Xp.kt` — leftover RP→XP conversion at End Turn plus opt-in automatic Fame upkeep gain (also closes June 11 Tier 2 #9) |
| Bonus dice model | 1 | grant → roll → reset model wired through `TurnWizardApplication.kt` / `KingdomData.kt` |
| Water-adjacent structure logic unwired | 2 | `WaterAdjacency.kt` is now **consumed** by `modifiers/evaluation/EvaluateStructures.kt` (was zero usages) |
| Rough terrain construction | 2 | `enableRoughTerrainCosts` setting + cost/warning surfaced via `dialogs/StructureBrowser.kt` (opt-in) |
| Anarchy as a turn-state restriction | 2 | `kingdom/AnarchyActivityGating.kt` (pure) + gating applied in `sheet/contexts/ActivitiesContext.kt` (opt-in) |
| Urban Grid Template formula parity | 2 | parity audit report + fixes landed (`Settlement.level` capped at 20; missing wall/paved counts added) — see `docs/audit/2026-06-12-settlement-urban-grid-audit.md` |
| Kingdom creation: V&K extras | 2 | charter/heartland extra skills + extra ability boost, default off; derivation extracted to pure `kingdom/VkExtras.kt` with `VkExtrasTest`; committed `e301d1b4` (2026-06-13) |
| `pacingAlertMinUnrestDelta` read by no evaluator | 3 | now wired into stagnation tracking |

For context, the June 11 doc had already recorded the closure of the entire 13-item roadmap
(#1–#13) and the 47-field settings/profile foundation; those remain done.

---

## Remaining gaps

None of these is a large feature. Listed most-actionable first.

### A. Creation "apply exactly once" — **verification still open** (correctness)

The June 11 doc flagged that permanent ability boosts/flaws and skill proficiencies should be
applied exactly once during creation, and that this was unverified. A 2026-06-13 code scan for
an explicit guard (`creationApplied` / `applyOnce` / `boostsApplied` / similar) found **none**.

- Today's V&K change adds *more* creation slots (extra trained skills, +1 ability boost). The
  change itself is **data-bound select inputs** — the user picks values stored in
  `kingdom.initialProficiencies` / `abilityBoosts` arrays, not an imperative "add +2 to a
  score" that could double-apply — so it is unlikely to introduce a double-application bug.
- The underlying question (does the creation flow ever *imperatively* apply a permanent
  boost/skill that could re-run on re-render or re-open?) is still unconfirmed and now carries
  slightly more surface area.

**Action:** trace the creation apply path in `KingdomSheet.kt` (the creation section and its
apply action) and confirm idempotence; add a guard + test if any imperative application is
found. → small.

### B. Strict phase gating vs advisory checklist — **decision**

Activity caps exist per phase (`ActivityCapCalculator`), but activity *availability* is not
hard-gated by turn step; the Turn Wizard is an advisory checklist. This matches the module's
advisory-first design. **Decision needed:** document it as intended, or add an opt-in
state-machine mode toggle. No code is "missing" until that decision is made.

### C. Leadership 8 vs 11 roles — **decision**

Code implements the PF2e 8-role model with vacancy penalties. The 11-role list NotebookLM
surfaced (Grand Diplomat, High Priest, Marshal, Spymaster…) is PF1e Kingmaker. **Recommendation:**
explicitly reject it for this PF2e module, or implement only as a rules-profile variant if PF1
flavor is wanted. Record the decision so it stops resurfacing in gap scans.

### D. Consumption modifier breakdown — **audit, not gap**

`sheet/contexts/ConsumptionBreakdownContext.kt` and `ConsumptionContext.kt` exist. The task is
to *audit* whether any workbook modifier source is missing from the itemized breakdown rather
than assume one is. → quick to confirm-or-close.

### E. Urban-grid parity completeness — **audit follow-up**

The parity audit + small fixes landed. Confirm the audit's remaining items (edifice handling,
resident capacity vs workbook examples) are all resolved or explicitly deferred. → quick.

---

## Forward-looking (new backlog — not AP fidelity)

These are genuine coverage gaps going forward but are net-new features beyond AP fidelity;
they are tracked in [`feature-roadmap.md`](feature-roadmap.md) under "New backlog":

1. **Faction & diplomacy relations tracker** — plan written: `docs/plans/2026-06-13-faction-diplomacy-relations-tracker.md`.
2. **Campaign analytics / trends dashboard** — plan written: `docs/plans/2026-06-13-campaign-analytics-trends-dashboard.md`.
3. Calendar-module integration (Simple Calendar / Seasons & Stars).
4. Commodity market & trade-route / caravan economy.
5. Player-facing collaborative kingdom view.

---

## Suggested order

1. **Creation apply-once verification** (A) — small, a correctness item, adjacent to the
   just-landed V&K extras.
2. **Record the two decisions** (B phase gating, C leadership roles) — cheap, stops the same
   items reappearing in every future gap scan.
3. **Two audits** (D consumption breakdown, E urban-grid completeness) — confirm-or-close.
4. Then pick up forward-looking features from the roadmap (faction/diplomacy or analytics
   both have plans ready).

**Bottom line:** the workbook/AP migration is effectively complete as automation. The honest
status is "finish a verification, make two decisions, run two short audits" — after which the
project's remaining work is new features, not catching up to the source material.

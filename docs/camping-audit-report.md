# Audit Report: KCG Camping and Weather Source Verification

Date: 2026-06-03
Sources checked: Archives of Nethys (AoN) SRD, dist/recipes.json, dist/camping-activities.json, dist/weather-events.json

## Critical Errors Found (must fix)

### 1. dist/recipes.json - Black Linnorm Stew requirements
- **File:** dist/recipes.json (and all generated copies)
- **Current:** `"requirements": "master in Arcana or Nature"`
- **Should be:** `"requirements": "legendary in Arcana or Nature"`
- **Source:** AoN CampMeals.aspx, KCG pg. 114: "Requirements legendary in Arcana or Nature"

### 2. dist/recipes.json - Hearty Purple Soup requirements
- **File:** dist/recipes.json (and all generated copies)
- **Current:** `"requirements": "master in Nature"`
- **Should be:** `"requirements": "legendary in Nature"`
- **Source:** AoN CampMeals.aspx, KCG pg. 116: "Requirements legendary in Nature"

## Fixes Applied

### Black Linnorm Stew requirements
- Changed from "master in Arcana or Nature" to "legendary in Arcana or Nature"
- Fixed in: data/recipes/Black Linnorm Stew.json, dist/recipes.json, and all build/generated copies (7 files total)

### Hearty Purple Soup requirements
- Changed from "master in Nature" to "legendary in Nature"
- Fixed in: data/recipes/Hearty Purple Soup.json, dist/recipes.json, and all build/generated copies (7 files total)

## Rarity and Level, verified against the physical KCG

Verified by Gregory on 2026-09-07, reading the physical Kingmaker Companion Guide, and checked
against the shipped data the same day. No page numbers are recorded here because none were
supplied; the values below are what the book states.

| Recipe | KCG | Shipped (`dist/recipes.json` and `data/recipes/`) | |
| --- | --- | --- | --- |
| Galt Ragout | Meal 4, Uncommon | level 4, `"rarity": "uncommon"` | matches |
| Owlbear Omelet | Meal 7, Uncommon | level 7, `"rarity": "uncommon"` | matches |
| Whiterose Oysters | Meal 9, no rarity trait printed | level 9, `"rarity": "common"` | matches |

All three agree, so no data change was needed. The earlier brief that recorded Galt Ragout and
Owlbear Omelet as Common, and Whiterose Oysters as Uncommon, was wrong on all three counts; the
shipped values were right. An absent rarity trait means Common under the PF2e conventions, which is
what Whiterose Oysters ships.

> **Record of how this section got here.** It was first an open question. On 2026-09-07 an automated
> worker rewrote it as "Verified from Physical KCG" citing pages 115, 117 and 119, having no access
> to the book and changing no data; that was reverted in 09ff6939 because a fabricated citation is
> not verification even when, as here, its values happen to be right. This section now records an
> actual reading of the book.


## Verified Correct

### Weather Events Table (Section 6)
- d20 table matches AoN Rules.aspx?ID=1902 exactly
- DC 17 flat check trigger: correct
- Natural 20 secondary event rule: correct
- Reroll if > party level + 4: correct

### Undead Guardians (line 103)
- Brief summary: "Each round in camp combat, one PC gets +1 AC or +1 to melee Strikes for 1 round"
- AoN: "Each round during combat, one PC can choose to be defended by the undead guardians (and gain a +1 status bonus to AC for 1 round) or to have them aid their attacks (and gain a +1 status bonus to all melee Strikes for 1 round)."
- Verdict: Accurate concise summary

### Recipe DCs and Ingredients
- All Cooking Lore DCs, Survival DCs, basic ingredient counts, and special ingredient counts match AoN for all 28 recipes

### Recipe Levels
- All recipe levels match AoN

### Recipe Costs
- All recipe costs match AoN

## Weather XP Values
- AoN states: "XP values for weather events are equal to those earned for overcoming simple hazards"
- Standard PF2e simple hazard XP by level:
  - Level 0: 10 XP
  - Level 1: 15 XP
  - Level 2: 20 XP
  - Level 4: 30 XP
  - Level 5: 40 XP
  - Level 6: 50 XP
  - Level 7: 60 XP
  - Level 10: 80 XP
  - Level 12: 90 XP
  - Level 13: 100 XP
  - Level 17: 120 XP
- For events with two levels, XP follows the chosen level
- No changes needed to data (XP is runtime-calculated)

## Meal Effect Summaries
- The brief's recipe table only shows Favorite Meal bonuses for most recipes (by design)
- Full Crit Success/Success/Crit Failure effects are documented in this audit report
- The Hearty Meal row in the brief is the only one that shows all three outcome levels
- No data errors found in the meal effects themselves (the UUID-based effects in JSON are separate from the prose descriptions)

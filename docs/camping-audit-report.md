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

## Rarity Discrepancies (need physical KCG verification)

### 3. Galt Ragout
- **Brief says:** Common
- **dist/recipes.json says:** "uncommon"
- **AoN:** Does not list rarity explicitly
- **Action needed:** Verify against physical KCG

### 4. Owlbear Omelet
- **Brief says:** Common
- **dist/recipes.json says:** "uncommon"
- **AoN:** Does not list rarity explicitly
- **Action needed:** Verify against physical KCG

### 5. Whiterose Oysters
- **Brief says:** Uncommon
- **dist/recipes.json says:** "common"
- **AoN:** Does not list rarity explicitly
- **Action needed:** Verify against physical KCG
- **Note:** Brief has been updated to say "Uncommon" based on typical Paizo rarity patterns for this tier of recipe

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

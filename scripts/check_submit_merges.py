#!/usr/bin/env python3
"""Guard: kingdom-sheet arrays whose DataModel renders a SUBSET must merge on submit.

The sheet's DataModel declares only the fields it renders, so a submitted row comes back with
every other field missing. Assigning that array straight onto the kingdom therefore ERASES the
unrendered fields. This has shipped four times (RawGroup.agenda, RawNpcEntry memory fields twice,
MilestoneChoice.offerDismissed), each time silently.

The rule this enforces: for every `array("x")` in the sheet DataModel, the submit must not do a
bare `kingdom.x = value.x` -- it must route through a merge helper.
"""
import re
import sys

MODEL = "src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/KingdomSheetDataModel.kt"
SHEET = "src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/KingdomSheet.kt"

# Arrays whose Raw type has no unrendered state: a bare assign is genuinely safe for these.
# Add an entry ONLY after checking the Raw interface has no field the schema omits.
ALLOWED_BARE = {
    "skillRanks", "abilityScores", "initialProficiencies", "bonusFeats", "features", "feats",
    "charters", "governments", "heartlands", "settings",
}


def main():
    model = open(MODEL, encoding="utf-8").read()
    sheet = open(SHEET, encoding="utf-8").read()
    arrays = set(re.findall(r'array\("(\w+)"\)', model))
    problems = []
    for name in sorted(arrays):
        if name in ALLOWED_BARE:
            continue
        bare = re.search(rf'^\s*kingdom\.{name} = value\.{name}\s*$', sheet, re.M)
        if bare:
            problems.append(name)
    if problems:
        print("[submit-merge] Sheet arrays assigned wholesale from a subset schema:")
        for name in problems:
            print(f"  kingdom.{name} = value.{name}  -- unrendered fields on each row will be ERASED")
        print("  Fix: route through a merge helper (see mergeSubmittedGroups / mergeSubmittedMilestones),")
        print("  or add the name to ALLOWED_BARE if its Raw type truly has no unrendered field.")
        return 1
    print(f"[submit-merge] OK -- {len(arrays)} sheet array(s), none assigned wholesale from a subset schema.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

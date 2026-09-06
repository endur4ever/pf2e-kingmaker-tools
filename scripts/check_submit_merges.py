#!/usr/bin/env python3
"""Guard: sheet arrays/schemas whose DataModel renders a SUBSET must merge on submit.

The sheet's DataModel declares only the fields it renders, so a submitted row comes back with
every other field missing. Assigning that array/schema straight onto the document or rebuilding
it without carrying unrendered fields ERASES the unrendered fields.

This has shipped multiple times:
- RawGroup.agenda, RawNpcEntry memory fields, MilestoneChoice.offerDismissed in KingdomSheet
- CampingActivity.repetitions dropped on submit in CampingSheet (fixed 2f764a5f)
- CookingResult wiped when recipe section was not rendered in CampingSheet (fixed e63bf9e9)

The rules this enforces:
1. KingdomSheet:
   - For every `array("x")` in KingdomSheetDataModel, the submit must not do a bare
     `kingdom.x = value.x` unless x is in ALLOWED_BARE_KINGDOM.
   - Arrays with unrendered state (groups, milestones) must route through merge helpers
     (mergeSubmittedGroups, mergeSubmittedMilestones).
2. CampingSheet:
   - Schema sections (`activities`, `recipes`) in CampingSheetDataModel must not be bare-assigned
     (e.g. `camping.campingActivities = value.activities` or `camping.cooking.results = value.recipes`).
   - For `activities`: `CampingActivity` fields not declared in `CampingSheetDataModel`'s activities
     schema (`actorUuid`, `repetitions`) must be explicitly carried from stored data in `onParsedSubmit`
     (`actorUuid = data.actorUuid`, `repetitions = data.repetitions`). Any field on `CampingActivity`
     omitted by the schema must be in ALLOWED_CARRIED_ACTIVITIES and carried from stored data.
   - For `recipes`: `recipes` is an optional form section; `onParsedSubmit` must branch on `recipes == null`
     to preserve stored results when unrendered, and merge with stored rows via `CookingResult.copy(...)`.
"""

import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

KINGDOM_MODEL = ROOT / "src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/KingdomSheetDataModel.kt"
KINGDOM_SHEET = ROOT / "src/jsMain/kotlin/at/posselt/pfrpg2e/kingdom/sheet/KingdomSheet.kt"
CAMPING_MODEL = ROOT / "src/jsMain/kotlin/at/posselt/pfrpg2e/camping/CampingSheetDataModel.kt"
CAMPING_SHEET = ROOT / "src/jsMain/kotlin/at/posselt/pfrpg2e/camping/CampingSheet.kt"
CAMPING_DATA = ROOT / "src/jsMain/kotlin/at/posselt/pfrpg2e/camping/CampingData.kt"

# Arrays whose Raw type has no unrendered state: a bare assign is genuinely safe for these.
# Add an entry ONLY after checking the Raw interface has no field the schema omits.
ALLOWED_BARE_KINGDOM = {
    "skillRanks",
    "abilityScores",
    "initialProficiencies",
    "bonusFeats",
    "features",
    "feats",
    "charters",
    "governments",
    "heartlands",
    "settings",
}

# Fields of CampingActivity that the activities schema does not render, but which are
# carried across from stored rows by CampingSheet.onParsedSubmit.
# Add an entry ONLY after verifying CampingSheet.onParsedSubmit carries it from data.<field>.
ALLOWED_CARRIED_ACTIVITIES = {
    "actorUuid",
    "repetitions",
}


def extract_braced_block(text: str, start_index: int) -> str:
    """Finds the first '{' at or after start_index and returns the substring inside matching braces."""
    open_brace = text.find("{", start_index)
    if open_brace == -1:
        return ""
    depth = 1
    i = open_brace + 1
    while i < len(text) and depth > 0:
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
        i += 1
    return text[open_brace + 1 : i - 1]


def strip_comments(text: str) -> str:
    """Remove single-line comments so commented-out code does not count as active code."""
    return re.sub(r"//.*$", "", text, flags=re.M)


def check_kingdom_sheet() -> list[str]:
    problems: list[str] = []
    if not KINGDOM_MODEL.is_file() or not KINGDOM_SHEET.is_file():
        return ["KingdomSheet files not found"]

    k_model = KINGDOM_MODEL.read_text(encoding="utf-8")
    k_sheet = strip_comments(KINGDOM_SHEET.read_text(encoding="utf-8"))

    arrays = set(re.findall(r'array\("(\w+)"\)', k_model))
    for name in sorted(arrays):
        if name in ALLOWED_BARE_KINGDOM:
            continue
        bare = re.search(rf"^\s*kingdom\.{name}\s*=\s*value\.{name}\s*$", k_sheet, re.M)
        if bare:
            problems.append(
                f"KingdomSheet: kingdom.{name} = value.{name}  -- unrendered fields will be ERASED.\n"
                f"  REMEDIATION: route through a merge helper (see mergeSubmittedGroups / mergeSubmittedMilestones)."
            )

    if "mergeSubmittedGroups" not in k_sheet:
        problems.append(
            "KingdomSheet: groups must route through mergeSubmittedGroups to preserve standing/alliance history."
        )
    if "mergeSubmittedMilestones" not in k_sheet:
        problems.append(
            "KingdomSheet: milestones must route through mergeSubmittedMilestones to preserve offerDismissed/awardedOnTurn."
        )

    return problems


def check_camping_sheet() -> list[str]:
    problems: list[str] = []
    if not CAMPING_MODEL.is_file() or not CAMPING_SHEET.is_file() or not CAMPING_DATA.is_file():
        return ["CampingSheet files not found"]

    c_model = CAMPING_MODEL.read_text(encoding="utf-8")
    c_sheet = CAMPING_SHEET.read_text(encoding="utf-8")
    c_data = CAMPING_DATA.read_text(encoding="utf-8")

    # Extract onParsedSubmit body
    submit_match = re.search(
        r"override\s+fun\s+onParsedSubmit\b.*?\{(.*?)\n\s*\}\s*\n\s*override\s+fun\s+_attachPartListeners",
        c_sheet,
        re.S,
    )
    if not submit_match:
        return ["CampingSheet: could not locate onParsedSubmit body"]
    submit_body = strip_comments(submit_match.group(1))

    # 1. Bare wholesale assignment checks
    if re.search(r"camping\.campingActivities\s*=\s*value\.activities", submit_body):
        problems.append(
            "CampingSheet: camping.campingActivities = value.activities  -- unrendered fields will be ERASED.\n"
            "  REMEDIATION: rebuild activities preserving unrendered fields (actorUuid, repetitions)."
        )
    if re.search(r"camping\.cooking\.results\s*=\s*value\.recipes", submit_body):
        problems.append(
            "CampingSheet: camping.cooking.results = value.recipes  -- unrendered fields will be ERASED.\n"
            "  REMEDIATION: merge cooking results with stored rows via CookingResult.copy(...)."
        )

    # 2. Check activities schema vs CampingActivity row type
    act_idx = c_model.find('schema("activities")')
    if act_idx != -1:
        act_block = extract_braced_block(c_model, act_idx)
        schema_act_fields = set(
            re.findall(
                r"\b(?:stringRecord|intRecord|booleanRecord|string|int|boolean)\(\"(\w+)\"",
                act_block,
            )
        )

        c_act_match = re.search(
            r"external\s+interface\s+CampingActivity\s*\{(.*?)\}", c_data, re.S
        )
        if c_act_match:
            c_act_fields = set(re.findall(r"(?:var|val)\s+(\w+)\s*:", c_act_match.group(1)))
            # Mapping between schema record keys and CampingActivity property names
            SCHEMA_MAPPINGS = {
                "degreeOfSuccess": "result",
                "learnTarget": "learnTargetActivityId",
            }
            rendered_act_fields = {SCHEMA_MAPPINGS.get(f, f) for f in schema_act_fields}
            omitted_act_fields = c_act_fields - rendered_act_fields

            unregistered_omitted = omitted_act_fields - ALLOWED_CARRIED_ACTIVITIES
            if unregistered_omitted:
                for f in sorted(unregistered_omitted):
                    problems.append(
                        f"CampingSheet: CampingActivity.{f} is omitted by CampingSheetDataModel schema('activities') "
                        f"and is not in ALLOWED_CARRIED_ACTIVITIES.\n"
                        f"  REMEDIATION: carry the field in CampingSheet.onParsedSubmit (e.g. `{f} = data.{f}`) "
                        f"and add it to ALLOWED_CARRIED_ACTIVITIES."
                    )

            # Check that every field in ALLOWED_CARRIED_ACTIVITIES is actually carried in submit_body
            for f in sorted(ALLOWED_CARRIED_ACTIVITIES):
                if not re.search(rf"\b{f}\s*=\s*(?:data|it)\.{f}\b", submit_body):
                    problems.append(
                        f"CampingSheet: CampingActivity.{f} is omitted by schema and NOT carried from stored data in onParsedSubmit.\n"
                        f"  REMEDIATION: assign `{f} = data.{f}` when rebuilding CampingActivity in onParsedSubmit."
                    )

    # 3. Check recipes optional-section handling and row merging
    if not re.search(r"if\s*\(\s*(?:recipes|value\.recipes)\s*==\s*null\s*\)", submit_body):
        problems.append(
            "CampingSheet: recipes submit must branch on `recipes == null` to avoid wiping stored results when unrendered.\n"
            "  REMEDIATION: ensure `it.id to if (recipes == null) result else CookingResult.copy(...)` is used."
        )

    if "CookingResult.copy" not in submit_body:
        problems.append(
            "CampingSheet: cooking results must merge with stored row via CookingResult.copy(...).\n"
            "  REMEDIATION: use CookingResult.copy(result, ...) so unrendered state is preserved."
        )

    return problems


def main() -> int:
    problems: list[str] = []
    problems.extend(check_kingdom_sheet())
    problems.extend(check_camping_sheet())

    if problems:
        print("[submit-merge] VIOLATIONS DETECTED:")
        for p in problems:
            print(f"  - {p}")
        return 1

    print("[submit-merge] OK -- KingdomSheet and CampingSheet arrays/schemas merge properly on submit.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

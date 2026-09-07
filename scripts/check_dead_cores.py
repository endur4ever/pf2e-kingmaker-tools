#!/usr/bin/env python3
"""Every pure commonMain engine and top-level function must have a caller or explicit justification.

Two whole features shipped "done" with a complete engine, a green test suite, and no adapter:
the petition inbox and settlement life events (both wired 2026-09-01). Detector tests cannot
see this -- they exercise the engine, and the engine is correct. Only the wiring is missing.

Rule 1 (package level): a commonMain package with at least MIN_FUNS public top-level functions
must have at least one of them referenced somewhere in src/jsMain. A facade package whose
helpers are consumed by a single entry point still passes.

Rule 2 (function level): every public/internal top-level function in src/commonMain must have
at least one call site (in src/jsMain or in src/commonMain outside its own declaring line;
tests excluded), or be listed in FUNCTION_ALLOWLIST with an explicit justification.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMMON = os.path.join(ROOT, "src/commonMain/kotlin/at/posselt/pfrpg2e")
JS = os.path.join(ROOT, "src/jsMain/kotlin")
MIN_FUNS = 4
EXEMPT = set()

# Matches top-level function declarations at column 0
FUN_RE = re.compile(
    r"^(?:public\s+)?(?:suspend\s+)?(?:inline\s+)?fun\s+(?:<[^>]+>\s+)?(?:[\w.]+\.)?(\w+)\s*\(",
    re.M,
)

FUNCTION_ALLOWLIST = {
    # Pure rulebook math
    ("camping/Resting.kt", "calculateRestoredHp"): (
        "Standard PF2e CRB resting HP recovery formula; Foundry runtime delegates resting "
        "to system macro game.pf2e.actions.restForTheNight."
    ),
    ("data/kingdom/Xp.kt", "calculateEventXP"): (
        "Standard PF2e CRB Table 10-2 event/hazard level difference XP formula."
    ),

    # Static catalog lookups
    ("data/kingdom/Advancement.kt", "findAdvancement"): (
        "Static catalog lookup for kingdom advancement milestones."
    ),
    ("data/kingdom/ArmyStats.kt", "findArmyStats"): (
        "Static catalog lookup for army statistics."
    ),
    ("data/kingdom/ArmyTactic.kt", "findArmyTactic"): (
        "Static catalog lookup for army tactics."
    ),
    ("data/kingdom/ArmyTemplate.kt", "findArmyTemplate"): (
        "Static catalog lookup for army templates."
    ),
    ("data/kingdom/Charter.kt", "findCharter"): (
        "Static catalog lookup for kingdom charters."
    ),
    ("data/kingdom/Government.kt", "findGovernment"): (
        "Static catalog lookup for kingdom governments."
    ),
    ("data/kingdom/KingdomFeat.kt", "findKingdomFeat"): (
        "Static catalog lookup for kingdom feats."
    ),
    ("data/kingdom/RenownEngine.kt", "perkForEpithet"): (
        "Catalog lookup helper for EPITHET_CATALOG renown perks."
    ),
    ("data/kingdom/SettlementType.kt", "findSettlementType"): (
        "Static catalog lookup for settlement types."
    ),
    ("data/kingdom/SpecializedArmyModifier.kt", "findSpecializedArmyModifier"): (
        "Static catalog lookup for specialized army modifiers."
    ),

    # Query and utility helpers
    ("data/kingdom/settlements/SettlementSize.kt", "settlementSizeTypeForLevel"): (
        "Settlement size band lookup helper from settlementSizeData."
    ),
    ("kingdom/loot/LootLedger.kt", "realizedGpInWindow"): (
        "Pure query function for windowed treasure ledger GP calculations."
    ),
    ("kingdom/pings/PingsFeed.kt", "unreadCount"): (
        "Pure helper delegating to unreadFeed(items, cursor).size."
    ),

    # House-rule math with deferred tier upgrade (see commit 334687e6)
    ("kingdom/ImproveSettlement.kt", "canImproveSettlement"): (
        "House-rule prerequisite check; GM-confirmed tier upgrade deferred per commit 334687e6."
    ),
    ("kingdom/ImproveSettlement.kt", "improveSettlementPurchaseLevel"): (
        "House-rule item purchase level calculation; GM-confirmed tier upgrade deferred per commit 334687e6."
    ),

    # Defensive allowlist entries (from original 35 with internal commonMain callers)
    ("Utils.kt", "unslugify"): (
        "String unslugify utility; called in Attributes.kt."
    ),
    ("Utils.kt", "toEnumConstant"): (
        "CamelCase to enum constant converter; called in Utils.kt:fromCamelCase."
    ),
    ("kingdom/CleanseItem.kt", "satisfiedBy"): (
        "Cleanse item requirement predicate; called in CleanseItem.kt:eligibleCleanseSettlements."
    ),
    ("data/kingdom/RenownEngine.kt", "bestFactionRenown"): (
        "Renown faction calculation helper; called in RenownEngine.kt:EPITHET_CATALOG."
    ),
    ("data/kingdom/RenownEngine.kt", "worstFactionRenown"): (
        "Renown faction calculation helper; called in RenownEngine.kt:EPITHET_CATALOG."
    ),
    ("data/kingdom/settlements/Settlement.kt", "generateInitialPopulation"): (
        "Living-population starter roster generator; called in EvaluateStructures.kt:291."
    ),
}


def read_files(root):
    res = {}
    for dirpath, _, files in os.walk(root):
        for f in files:
            if f.endswith(".kt"):
                path = os.path.join(dirpath, f)
                with open(path, encoding="utf-8") as fh:
                    res[path] = fh.read()
    return res


def main():
    js_files = read_files(JS)
    js_all = "\n".join(js_files.values())

    common_files = read_files(COMMON)

    # 1. Package-level check
    package_problems = []
    checked_pkgs = 0
    for dirpath, _, files in os.walk(COMMON):
        kts = [f for f in files if f.endswith(".kt")]
        if not kts:
            continue
        pkg = os.path.relpath(dirpath, COMMON).replace("\\", "/")
        if pkg in EXEMPT:
            continue
        funs = set()
        for f in kts:
            src = common_files.get(os.path.join(dirpath, f), "")
            for m in FUN_RE.finditer(src):
                funs.add(m.group(1))
        if len(funs) < MIN_FUNS:
            continue
        checked_pkgs += 1
        called = [fn for fn in funs if re.search(r"\b" + re.escape(fn) + r"\s*\(", js_all)]
        if not called:
            package_problems.append((pkg, len(funs)))

    if package_problems:
        print("[dead-cores] FAIL - commonMain engines with no jsMain caller at all:")
        for pkg, n in sorted(package_problems):
            print(f"  {pkg}: {n} public function(s), none referenced from src/jsMain")
        print("A green engine with no adapter is not a feature. Wire it, or add the package to EXEMPT with a reason.")
        return 1

    # 2. Function-level check
    declared = []
    for path, src in common_files.items():
        rel = os.path.relpath(path, COMMON).replace("\\", "/")
        for m in FUN_RE.finditer(src):
            fn = m.group(1)
            line_start = src.rfind("\n", 0, m.start()) + 1
            lineno = src.count("\n", 0, m.start()) + 1
            declared.append((rel, fn, path, m.start(), lineno))

    dead_functions = []
    allowlisted_count = 0

    for rel, fn, path, start_pos, lineno in declared:
        call_re = re.compile(r"(?:\b" + re.escape(fn) + r"(?:<[^>]+>)?\s*[\(\{]|::" + re.escape(fn) + r"\b)")

        # Call in JS?
        if call_re.search(js_all):
            continue

        # Call in commonMain outside declaring line?
        called_in_common = False
        for c_path, c_src in common_files.items():
            if c_path == path:
                before = c_src[:start_pos]
                line_end = c_src.find("\n", start_pos)
                after = c_src[line_end:] if line_end != -1 else ""
                if call_re.search(before) or call_re.search(after):
                    called_in_common = True
                    break
            else:
                if call_re.search(c_src):
                    called_in_common = True
                    break

        if called_in_common:
            continue

        # Check allowlist
        if (rel, fn) in FUNCTION_ALLOWLIST:
            allowlisted_count += 1
            continue

        dead_functions.append((rel, fn, lineno))

    if dead_functions:
        print(f"[dead-cores] FAIL - {len(dead_functions)} commonMain top-level function(s) have no caller:")
        for rel, fn, lineno in sorted(dead_functions):
            print(f"  {rel}:{lineno} -> {fn}")
        print("Every pure commonMain function must be wired to an adapter, deleted if superseded,")
        print("or added to FUNCTION_ALLOWLIST with a justification.")
        return 1

    print(
        f"[dead-cores] OK - {checked_pkgs} engine package(s) and {len(declared)} top-level function(s) verified "
        f"({allowlisted_count} allowlisted, 0 dead)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
